package org.example.stocksteps.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.earnings.EarningsService
import org.example.stocksteps.earnings.FinnhubEarningsDataSource
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.*
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 2 (shared financial data cache): the cache core with a fake clock, single flight under concurrency,
 * failure and cancellation handling, cross-feature reuse through the REAL adapters (Ktor MockEngine, no
 * network), key separation, metering without double counting, entitlement isolation and the narration budget.
 */
class FinancialCacheTest {
    private val time = AtomicLong(1_000)
    private fun cache(capacity: Int = 16) = CompanyFinancialCache(capacity, now = { time.get() })

    // ---------- Cache core ----------

    @Test fun missLoadsHitReusesExpiryRefreshesAndInvalidateReloads() = runBlocking {
        val c = cache(); var loads = 0
        suspend fun get() = c.getOrLoad("quote:AAPL", 30_000) { ++loads }
        assertEquals(1, get()); assertEquals(1, get()); assertEquals(1, loads)
        time.addAndGet(29_999); assertEquals(1, get())
        time.addAndGet(1); assertEquals(2, get())                                     // expired → one reload
        c.invalidate("quote:AAPL"); assertEquals(3, get())
        assertEquals(mapOf("miss" to 3L, "hit" to 2L, "expired" to 1L), c.stats().filterKeys { it != "join" })
    }

    @Test fun resultTtlAndDistinctKeys() = runBlocking {
        val c = cache()
        assertEquals("annual", c.getOrLoad("income-statement:AAPL:annual:6", 1_000) { "annual" })
        assertEquals("quarter", c.getOrLoad("income-statement:AAPL:quarter:24", 1_000) { "quarter" })
        assertEquals("TD.TO", c.getOrLoad("profile:TD.TO", 1_000) { "TD.TO" })
        assertEquals("TD", c.getOrLoad("profile:TD", 1_000) { "TD" })
        var n = 0
        repeat(2) { c.getOrLoad("short", 10_000, resultTtl = { _: Int -> 5 }) { ++n } }
        assertEquals(1, n); time.addAndGet(5); c.getOrLoad("short", 10_000, resultTtl = { _: Int -> 5 }) { ++n }; assertEquals(2, n)
    }

    @Test fun boundedPurgesExpiredFirstThenLeastRecentlyUsed() = runBlocking {
        val c = cache(capacity = 3)
        c.getOrLoad("a", 10) { 1 }; c.getOrLoad("b", 10_000) { 2 }; c.getOrLoad("c", 10_000) { 3 }
        time.addAndGet(20)                                                               // "a" expired
        c.getOrLoad("d", 10_000) { 4 }
        assertEquals(3, c.size); assertEquals(0L, c.stats()["evict"] ?: 0L)              // expired one purged, no live eviction
        c.getOrLoad("b", 10_000) { 99 }                                                  // touch b (now most recent)
        c.getOrLoad("e", 10_000) { 5 }
        assertEquals(3, c.size); assertEquals(1L, c.stats()["evict"])
        var reloaded = false; c.getOrLoad("c", 10_000) { reloaded = true; 3 }; assertTrue(reloaded)  // LRU "c" was evicted
        assertEquals(2, c.getOrLoad("b", 10_000) { 99 })
    }

    @Test fun hundredConcurrentIdenticalRequestsLoadOnceAndOtherKeysDontWait() = runBlocking {
        val c = cache(); val loads = AtomicInteger(); val gate = CompletableDeferred<Unit>()
        val slow = (1..100).map { async(Dispatchers.Default) { c.getOrLoad("fundamentals:AAPL", 60_000) { loads.incrementAndGet(); gate.await(); "AAPL" } } }
        // A different symbol completes while AAPL is still loading (no global lock).
        assertEquals("MSFT", withTimeout(2_000) { c.getOrLoad("fundamentals:MSFT", 60_000) { "MSFT" } })
        gate.complete(Unit)
        assertTrue(slow.awaitAll().all { it == "AAPL" }); assertEquals(1, loads.get())
        // Callers that started before the load finished joined it; any that started after got a cache hit.
        assertEquals(99L, (c.stats()["join"] ?: 0) + (c.stats()["hit"] ?: 0))
    }

    @Test fun failuresAreSharedNotStoredAndTheNextRequestRetries() = runBlocking {
        val c = cache(); val attempts = AtomicInteger(); val gate = CompletableDeferred<Unit>()
        val callers = (1..30).map { async(Dispatchers.Default) {
            runCatching { c.getOrLoad("quote:AAPL", 30_000) { attempts.incrementAndGet(); gate.await(); throw StockProviderException(StockProviderException.Failure.UNAVAILABLE, 503) } }
        } }
        delay(50); gate.complete(Unit)
        val results = callers.awaitAll()
        assertEquals(1, attempts.get()); assertTrue(results.all { (it.exceptionOrNull() as? StockProviderException)?.upstreamStatus == 503 })
        assertEquals("ok", c.getOrLoad("quote:AAPL", 30_000) { attempts.incrementAndGet(); "ok" })   // not poisoned: next request loads
        assertEquals(2, attempts.get())
    }

    @Test fun cancelledLoaderHandsOverAndTimeoutsLeaveNothingInFlight() = runBlocking {
        val c = cache(); val loads = AtomicInteger(); val started = CompletableDeferred<Unit>()
        val owner = launch(Dispatchers.Default) { c.getOrLoad("k", 60_000) { loads.incrementAndGet(); started.complete(Unit); delay(10_000); "never" } }
        started.await()
        val waiter = async(Dispatchers.Default) { c.getOrLoad("k", 60_000) { loads.incrementAndGet(); "taken over" } }
        delay(50); owner.cancelAndJoin()
        assertEquals("taken over", withTimeout(2_000) { waiter.await() }); assertEquals(2, loads.get())
        // A caller's own timeout releases the in-flight entry: the next caller loads normally.
        assertFailsWith<TimeoutCancellationException> { withTimeout(50) { c.getOrLoad("t", 60_000) { delay(10_000); "slow" } } }
        assertEquals("fast", withTimeout(1_000) { c.getOrLoad("t", 60_000) { "fast" } })
    }

    // ---------- Cross-feature reuse through the REAL adapters ----------

    private val clock = Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC)

    private class Upstream(val fail: (String) -> Boolean = { false }) {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val queries = ConcurrentHashMap.newKeySet<String>()
        fun n(key: String) = counts[key]?.get() ?: 0
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath.substringAfter("/stable/").substringAfter("/api/v1/")
            val period = request.url.parameters["period"]?.let { ":$it" } ?: ""
            val limit = request.url.parameters["limit"]?.let { ":$it" } ?: ""
            if (period.isNotEmpty() || limit.isNotEmpty()) counts.getOrPut("$path$period$limit") { AtomicInteger() }.incrementAndGet()
            counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            queries += request.url.encodedQuery
            val symbol = request.url.parameters["symbol"] ?: "AAPL"
            if (fail(path)) return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
            val body = when (path) {
                "quote" -> """[{"symbol":"$symbol","price":100.0,"previousClose":99.0,"timestamp":1791396000}]"""
                "profile" -> """[{"symbol":"$symbol","companyName":"$symbol Corp","currency":"${if (symbol.endsWith(".TO")) "CAD" else "USD"}","exchange":"NASDAQ"}]"""
                "income-statement" -> (0 until (request.url.parameters["limit"]?.toInt() ?: 6)).joinToString(",", "[", "]") { i ->
                    val p = request.url.parameters["period"]
                    """{"symbol":"$symbol","date":"${2026 - (if (p == "quarter") i / 4 else i)}-0${if (p == "quarter") 9 - (i % 4) * 2 else 9}-27","period":"${if (p == "quarter") "Q${4 - i % 4}" else "FY"}","revenue":${1000 - i}.0,"epsDiluted":1.${i},"reportedCurrency":"USD"}""" }
                "historical-chart/5min" -> """[{"date":"2026-10-07 09:30:00","close":100.0},{"date":"2026-10-07 09:35:00","close":101.0}]"""
                "historical-price-eod/light" -> """[{"date":"2026-10-05","price":99.0},{"date":"2026-10-06","price":100.0}]"""
                "search-symbol", "search-name" -> """[{"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ"}]"""
                "calendar/earnings" -> """{"earningsCalendar":[]}"""
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) } }
    }

    private class World(val up: Upstream, clock: Clock) {
        val fmp = FmpStockProviderRepositoryImpl(up.client, "secret-test-key") { java.time.LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        private val prices = FmpPriceHistoryProvider(up.client, "secret-test-key")
        val charts = PriceChartService(prices)
        val sparklines = SparklineService(prices)
        val financials = CompanyFinancialService(fmp)
        val valuation = ValuationService(fmp, charts, fmp)
        val details = CompanyDetailsService(stocks, financials, FmpMarketDataProvider(up.client, "secret-test-key"), valuation = valuation,
            marketStatus = UsMarketCalendar().let { c -> { c.marketStatusAt(clock.instant()) } })
        val store = InMemoryUserDataStore()
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val earnings = EarningsService(FinnhubEarningsDataSource(up.client, "secret-test-key"), stocks, charts, store, entitlements, clock, sampleData = false, research = null)
        val screener = ScreenerService(FixtureScreenerUniverse(), stocks, { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock,
            sampleData = false, fundamentalsPerHour = 0, fullRecords = false)
        val history = ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements, clock, sampleData = false, source = "test")
    }

    @Test fun detailsComparisonAndGuidedResearchShareOneCopyOfEachDataset() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        w.details.getDetails("AAPL")                                                     // Company Details
        val afterDetails = up.counts.mapValues { it.value.get() }
        w.screener.compare("AAPL,MSFT")                                                  // Comparison (AAPL reused, MSFT new)
        w.details.getDetails("AAPL")                                                     // Guided Research reads Company Details
        w.financials.getFundamentals("AAPL", "quarter")                                  // Financials (quarter)
        // AAPL's annual statements, profile and quarterly EPS were fetched once across all four features.
        assertEquals(2, up.n("income-statement:annual:6"))                              // AAPL + MSFT, once each
        assertEquals(2, up.n("profile")); assertEquals(afterDetails["ratios"], up.n("ratios") - 1)
        // Financials (quarter ×8) reuses Valuation's 24-quarter request: no separate ×8 request exists.
        assertEquals(0, up.n("income-statement:quarter:8")); assertEquals(1, up.n("income-statement:quarter:24"))
        // Market status comes from the exchange calendar.
        assertEquals(0, up.n("exchange-market-hours"))
    }

    @Test fun annualAndQuarterlyStayDistinctAndQuarterlyIsTheNewestEight() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        val annual = w.financials.getFundamentals("AAPL", "annual")
        val quarter = w.financials.getFundamentals("AAPL", "quarter")
        assertTrue(annual.history.isNotEmpty() && quarter.history.isNotEmpty())
        assertTrue(quarter.history.size <= 8, "quarterly history shows at most 8 quarters")
        assertNotEquals(annual.history.map { it.period }, quarter.history.map { it.period })
        assertEquals(1, up.n("income-statement:annual:6")); assertEquals(1, up.n("income-statement:quarter:24"))
    }

    @Test fun usAndCanadianListingsNeverShareEntries() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        assertEquals("USD", w.stocks.getProfile("TD")?.currency)
        assertEquals("CAD", w.stocks.getProfile("TD.TO")?.currency)
        assertEquals(2, up.n("profile"))
    }

    @Test fun chartAndSparklineShareOneIntradayRequestAndSearchIsNormalized() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        w.charts.getChart("AAPL", ChartRange.ONE_DAY); w.sparklines.getSparkline("AAPL")
        assertEquals(1, up.n("historical-chart/5min"))
        assertEquals(listOf(100.0, 101.0), w.sparklines.getSparkline("AAPL")?.closes)
        w.stocks.searchStocks("Apple"); w.stocks.searchStocks("  apple "); w.stocks.searchStocks("APPLE")
        assertEquals(1, up.n("search-symbol")); assertEquals(1, up.n("search-name"))
        w.stocks.searchStocks("microsoft"); assertEquals(2, up.n("search-symbol"))
    }

    @Test fun earningsDetailsAndResultsShareOneHistoryRequest() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        runCatching { w.earnings.details("AAPL", null) }; runCatching { w.earnings.latestResults("AAPL") }; runCatching { w.earnings.next("AAPL") }
        assertEquals(1, up.n("calendar/earnings"))
    }

    @Test fun rateLimitedEarningsHistoryIsNotRetried() = runBlocking {
        val up = Upstream(fail = { it == "calendar/earnings" })
        val w = World(up, clock)
        runCatching { w.earnings.latestResults("AAPL") }
        assertEquals(2, up.n("calendar/earnings"))                                      // 503: one retry (transient)
        val limited = HttpClient(MockEngine { respond("{}", HttpStatusCode.TooManyRequests) }) { install(ContentNegotiation) { json() } }
        val calls = AtomicInteger()
        val source = object : org.example.stocksteps.earnings.EarningsDataSource by FinnhubEarningsDataSource(limited, "k") {
            override suspend fun history(symbol: String): List<org.example.stocksteps.earnings.EarningsEvent> { calls.incrementAndGet(); return FinnhubEarningsDataSource(limited, "k").history(symbol) }
        }
        val e = EarningsService(source, w.stocks, w.charts, w.store, w.entitlements, clock, sampleData = false, research = null)
        runCatching { e.latestResults("AAPL") }
        assertEquals(1, calls.get())                                                     // 429: never retried
    }

    @Test fun premiumHistoryStaysPremiumAlthoughStatementsAreShared() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
        w.entitlements.simulate("free", DebugEntitlementRequest(SubscriptionTier.FREE))
        val plus = w.history.userHistory("plus", "AAPL,MSFT", "5Y")
        assertTrue(plus.access.plus)
        val denied = assertFailsWith<ScreenerRequestException> { w.history.userHistory("free", "AAPL,MSFT", "5Y") }
        assertEquals("PLUS_REQUIRED", denied.code)
        val free = w.history.userHistory("free", "AAPL,MSFT", "1Y")
        assertFalse(free.access.plus); assertTrue(free.metrics.none { it.metric.premium })
    }

    // ---------- Metering ----------

    @Test fun eachUpstreamRequestIsCountedOnceWithoutTheKey() = runBlocking {
        val up = Upstream(); val w = World(up, clock)
        val meter = ProviderUsageMeter.shared
        val feature = "cache-test-${System.nanoTime()}"
        withContext(ProviderFeature(feature)) { w.financials.getFundamentals("AAPL", "annual"); w.financials.getFundamentals("AAPL", "annual") }
        val upstream = meter.count("fmp", "upstream", feature)
        val sent = up.counts.filterKeys { ':' !in it }.values.sumOf { it.get() }        // every FMP request this world sent (bare-path keys)
        assertEquals(sent.toLong(), upstream, "one upstream count per request (loader no longer double counts)")
        assertTrue(meter.count("fmp", "cacheHit", feature) >= 11)                        // the second load hit every dataset
        assertTrue(meter.report().counters.none { "secret-test-key" in it.endpoint || "secret-test-key" in it.feature })
        assertTrue(up.queries.any { "apikey=secret-test-key" in it })                   // sanity: the key really was a query parameter
    }

    // ---------- Market status, narration budget, MOCK isolation ----------

    @Test fun marketStatusFromTheCalendar() {
        val c = UsMarketCalendar()
        assertEquals(MarketStatus.OPEN, c.marketStatusAt(Instant.parse("2026-10-07T15:00:00Z")))          // 11:00 ET Wednesday
        assertEquals(MarketStatus.PRE_MARKET, c.marketStatusAt(Instant.parse("2026-10-07T12:00:00Z")))
        assertEquals(MarketStatus.AFTER_HOURS, c.marketStatusAt(Instant.parse("2026-10-07T21:15:00Z")))
        assertEquals(MarketStatus.CLOSED, c.marketStatusAt(Instant.parse("2026-10-10T15:00:00Z")))        // Saturday
        assertEquals(MarketStatus.CLOSED, c.marketStatusAt(Instant.parse("2026-12-25T15:00:00Z")))        // holiday
    }

    @Test fun narrationHasAnHourlyBudgetAndFallsBackToTheTemplate() = runBlocking {
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val stocks = StockService(fixture, fixture)
        val calls = AtomicInteger()
        val movement = MovementService(stocks, PriceChartService(fixture), NewsService(fixture), narrator = { facts -> calls.incrementAndGet(); "${facts.toJson()}" },
            version = "test", narrationsPerHour = 2)
        val results = listOf("AAPL", "MSFT", "NVDA", "AMZN").map { movement.explain(it, org.example.stocksteps.model.MovementPeriod.ONE_MONTH) }
        assertTrue(calls.get() <= 2, "at most two AI narrations per hour (was ${calls.get()})")
        assertTrue(results.filterNotNull().count { !it.aiGenerated } >= 2)              // the rest use the deterministic summary
    }

    @Test fun mockSourcesUseNoNetworkAndKeepTheirOwnCaches() {
        val sources = org.example.stocksteps.mockDataSources()
        assertTrue(sources.stockProvider is FixtureMarketDataSource && sources.priceHistory is FixtureMarketDataSource)
        assertTrue(sources.earningsData is org.example.stocksteps.earnings.FixtureEarningsDataSource)
        // Each process builds REAL or MOCK sources, never both, so caches can't mix environments.
        assertNull(sources.insights?.takeIf { it is org.example.stocksteps.news.GeminiArticleInsightGenerator })
    }
}
