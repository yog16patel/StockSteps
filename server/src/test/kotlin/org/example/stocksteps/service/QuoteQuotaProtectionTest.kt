package org.example.stocksteps.service

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.stockRoutes
import org.example.stocksteps.userdata.WatchMarketData
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 7A: FMP quote quota protection on the REAL adapters over a MockEngine (no network, no keys), with fake clocks.
 * - A quote *outcome* is cached: 402/403 for 1 h, 429 for 10 min, other failures 30 s; successes keep their lifetime.
 * - The failure keeps its classification (restricted / rate limited / daily allowance) and never becomes a price or "not found".
 * - `ProviderGuard` enforces a configured daily allowance separately from the per-minute rate; a provider 429 never switches it on.
 */
class QuoteQuotaProtectionTest {
    private val ms = AtomicLong(1_000_000)
    private val up = FmpMock(latency = 2)
    private val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }) { LocalDate.parse("2026-10-07") }
    private fun advance(minutes: Long) = ms.addAndGet(minutes * 60_000)
    private fun quotes() = up.n("quote")
    private suspend fun failure(symbol: String): StockProviderException = assertFailsWith { fmp.getQuote(symbol) }

    @Test fun successfulQuotesKeepTheirLifetime() = runBlocking {
        assertEquals(100.0, fmp.getQuote("AAPL")?.price)
        repeat(9) { fmp.getQuote("AAPL") }
        assertEquals(1, quotes(), "ten reads within 30 s share one request")
        ms.addAndGet(31_000)
        fmp.getQuote("AAPL")
        assertEquals(2, quotes(), "refreshed after the 30 s quote lifetime")
    }

    @Test fun planRestrictedQuotesCoolDownForAnHourKeepTheirTypeAndRecover() = runBlocking {
        for ((symbol, status) in listOf("XLK" to 402, "XLV" to 403)) {
            up.symbolStatus["quote|$symbol"] = status
            val before = quotes()
            repeat(10) {
                val f = failure(symbol)
                assertTrue(f.restricted); assertEquals(status, f.upstreamStatus); assertFalse(f.dailyLimitReached)
                assertEquals(ProviderErrors.RESTRICTED, ProviderErrors.of(f).second.code)
            }
            advance(59)
            failure(symbol)
            assertEquals(before + 1, quotes(), "one request per hour for a refused symbol ($status)")
            up.symbolStatus.remove("quote|$symbol")   // e.g. the plan now includes it
            advance(2)
            assertEquals(100.0, fmp.getQuote(symbol)?.price, "recovers after the cooldown")
            assertEquals(before + 2, quotes())
        }
    }

    @Test fun providerRateLimitCoolsDownForTenMinutesAndIsNotADailyLimit() = runBlocking {
        up.symbolStatus["quote|MSFT"] = 429
        repeat(5) {
            val f = failure("MSFT")
            assertEquals(StockProviderException.Failure.RATE_LIMITED, f.failure)
            assertFalse(f.dailyLimitReached, "a provider 429 doesn't say which limit was hit")
            assertEquals("PROVIDER_RATE_LIMITED", ProviderErrors.of(f).second.code)
        }
        assertEquals(1, quotes())
        advance(9); failure("MSFT"); assertEquals(1, quotes(), "still cooling down")
        up.symbolStatus.clear(); advance(2)
        assertNotNull(fmp.getQuote("MSFT")); assertEquals(2, quotes())
    }

    @Test fun transientFailuresUseTheShortCooldown() = runBlocking {
        up.symbolStatus["quote|NVDA"] = 503
        repeat(3) { assertEquals(StockProviderException.Failure.UNAVAILABLE, failure("NVDA").failure) }
        assertEquals(1, quotes())
        ms.addAndGet(31_000); up.symbolStatus.clear()
        assertNotNull(fmp.getQuote("NVDA")); assertEquals(2, quotes())
    }

    @Test fun concurrentReadsShareOneRequestAndSymbolsStayIsolated() = runBlocking {
        up.symbolStatus["quote|XLK"] = 402
        val results = (1..20).map { async(Dispatchers.Default) { runCatching { fmp.getQuote("XLK") } } }.awaitAll()
        assertTrue(results.all { (it.exceptionOrNull() as? StockProviderException)?.restricted == true })
        assertEquals(1, quotes(), "20 concurrent reads, one upstream request")
        (1..20).map { async(Dispatchers.Default) { fmp.getQuote("AAPL") } }.awaitAll().forEach { assertEquals(100.0, it?.price) }
        assertEquals(2, quotes(), "a refused symbol doesn't affect another symbol")
        repeat(10) { i -> up.symbolStatus["quote|R$i"] = 402; failure("R$i"); failure("R$i") }
        assertEquals(12, quotes(), "ten different refused symbols: one request each")
    }

    @Test fun aRestrictedQuoteIsNeverAPriceOrNotFound() = runBlocking {
        up.symbolStatus["quote|XLK"] = 402
        // The repository throws: no StockQuote object (and so no 0.00) exists for a refused symbol.
        assertNull(runCatching { fmp.getQuote("XLK") }.getOrNull())
        // Watch data / Practice / Portfolio read through WatchMarketData: missing (null), flagged as restricted, never a value.
        val watch = WatchMarketData(StockService(fmp, fmp), null, emptyNews, CompanyFinancialCache(capacity = 64, now = { ms.get() }))
        assertNull(watch.quote("XLK"))
        assertTrue(watch.quoteRestricted("XLK"))
        assertFalse(watch.quoteRestricted("AAPL")); assertEquals(100.0, watch.quote("AAPL")?.price)
        up.symbolStatus["quote|TMP"] = 503
        assertNull(watch.quote("TMP")); assertFalse(watch.quoteRestricted("TMP"), "a temporary failure isn't a plan restriction")
    }

    @Test fun quoteRouteSaysRestrictedNotNotFoundAndSuccessKeepsItsSchema() = testApplication {
        up.symbolStatus["quote|XLK"] = 402
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureApiErrors(); routing { stockRoutes(StockService(fmp, fmp)) }
        }
        val refused = client.get("/api/v1/stocks/XLK/quote")
        assertEquals(HttpStatusCode.BadGateway, refused.status, "status unchanged from before Phase 7A")
        val error = Json.decodeFromString(ApiError.serializer(), refused.bodyAsText())
        assertEquals(ProviderErrors.RESTRICTED, error.code)
        assertEquals(ProviderErrors.RESTRICTED_MESSAGE, error.message)
        assertFalse("402" in refused.bodyAsText() || "financialmodelingprep" in refused.bodyAsText() || "apikey" in refused.bodyAsText())
        val ok = client.get("/api/v1/stocks/AAPL/quote")
        assertEquals(HttpStatusCode.OK, ok.status)
        val keys = Json.parseToJsonElement(ok.bodyAsText()).jsonObject.keys
        assertEquals(setOf("symbol", "companyName", "price", "change", "changePercent", "dayHigh", "dayLow", "previousClose", "volume",
            "timestamp", "marketCap", "open", "yearHigh", "yearLow").filter { it in keys }.toSet(), keys, "no new fields in the success body")
        assertEquals(100.0, Json { ignoreUnknownKeys = true }.decodeFromString(StockQuote.serializer(), ok.bodyAsText()).price)
        up.knownSymbols = setOf("AAPL")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/NOPE/quote").status, "a genuinely missing symbol is still 404")
    }

    // --- Markets: repeated sector refreshes reuse the refused outcomes ---

    private val emptyNews = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })

    @Test fun marketsSectorRefreshesDoNotRepeatRefusedQuotes() = runBlocking {
        MarketsService.SECTORS.forEach { (_, symbol, _) -> up.symbolStatus["quote|$symbol"] = 402 }
        val markets = marketsService()
        val overview = markets.overview()
        assertEquals(ProviderErrors.RESTRICTED, overview.errors.single { it.section == "sectors" }.error.code,
            "every fund refused by the plan: the section says so, not \"temporarily unavailable\"")
        assertTrue(overview.sectors.items.isEmpty(), "no sector value is invented")
        val first = quotes()
        assertTrue(first >= 11, "the first build asks for each sector fund once")
        advance(31); markets.overview()   // closed market: the sector section refreshes after 30 min
        assertEquals(11, up.log.count { it.startsWith("quote|XL") }, "within the hour the 11 refused funds aren't asked again")
        advance(31); markets.overview()
        assertEquals(22, up.log.count { it.startsWith("quote|XL") }, "asked again once the hour is over")
        repeat(4) { advance(31); markets.overview() }   // 124 more minutes: two more hourly attempts, not four
        assertEquals(44, up.log.count { it.startsWith("quote|XL") })
    }

    private fun marketsService(): MarketsService {
        val index = object : IndexDataSource {
            override suspend fun indexQuote(symbol: String) = fmp.getQuote(symbol)
            override suspend fun indexHistory(symbol: String) = emptyList<PricePoint>()
        }
        // Wednesday 2026-10-07 21:30 ET (01:30 UTC), closed: sections live 15 min (sectors 30 min), the overview 5 min.
        return MarketsService(FmpMarketDataProvider(up.client, "k"), fmp, index, emptyNews,
            MarketsSourceLabels("u", "q", sampleData = false, notice = "Delayed"),
            clock = Clock.fixed(Instant.parse("2026-10-08T01:30:00Z"), ZoneOffset.UTC),
            cache = CompanyFinancialCache(capacity = 128, now = { ms.get() }))
    }

    // --- Daily allowance (ProviderGuard) ---

    private val wall = AtomicLong(Instant.parse("2026-10-16T23:00:00Z").toEpochMilli())
    private fun guard(daily: Long?, meter: ProviderUsageMeter = ProviderUsageMeter()) = ProviderGuard(
        ProviderBudgetConfig(mapOf("fmp" to ProviderLimits(perMinute = 600, burst = 600, maxConcurrent = 50, dailyLimit = daily)), safetyMargin = 1.0),
        { wall.get() }, meter, sleep = { wall.addAndGet(it) })

    @Test fun aConfiguredDailyAllowanceStopsUpstreamCallsUntilTheNextBudgetDay() = runBlocking {
        val meter = ProviderUsageMeter()
        val g = guard(daily = 5, meter = meter)
        withContext(g) {
            repeat(5) { i -> assertNotNull(fmp.getQuote("D$i")) }
            assertEquals(5, quotes())
            val f = failure("D5")
            assertTrue(f.dailyLimitReached); assertEquals(StockProviderException.Failure.RATE_LIMITED, f.failure); assertNull(f.upstreamStatus)
            assertEquals(ProviderErrors.DAILY_LIMIT, ProviderErrors.of(f).second.code)
            assertEquals(503, ProviderErrors.of(f).first)
            assertEquals(5, quotes(), "no upstream request once the allowance is used")
            // Concurrent callers on other symbols can't overshoot either.
            (6..25).map { async(Dispatchers.Default) { runCatching { fmp.getQuote("D$it") } } }.awaitAll()
            assertEquals(5, quotes())
            assertEquals(5L to 5L, g.dailyUsage("fmp"))
            assertEquals(1L, meter.snapshot().second["provider.fmp.dailyLimitReached"], "one alert event per day")
            // Next budget day (UTC midnight) — the denial was cached only 30 s per symbol.
            wall.set(Instant.parse("2026-10-17T00:00:01Z").toEpochMilli()); ms.addAndGet(31_000)
            assertNotNull(fmp.getQuote("D5"))
            assertEquals(6, quotes(), "recovers after the daily reset")
            assertEquals(1L to 5L, g.dailyUsage("fmp"))
        }
    }

    @Test fun concurrentCallersNeverExceedTheDailyAllowance() = runBlocking {
        val g = guard(daily = 10)
        withContext(g) { (1..50).map { async(Dispatchers.Default) { runCatching { fmp.getQuote("C$it") } } }.awaitAll() }
        assertEquals(10, quotes())
    }

    @Test fun providerRateLimitsNeverSwitchOnTheDailyState() = runBlocking {
        val g = guard(daily = 100)
        up.status["quote"] = 429
        withContext(g) { val f = failure("X1"); assertFalse(f.dailyLimitReached) }
        assertEquals(1L to 100L, g.dailyUsage("fmp"), "the 429 is counted as one admitted attempt, not as exhaustion")
        assertTrue(g.open("fmp"), "the existing circuit breaker handles the 429")
    }

    @Test fun withoutAConfiguredAllowanceNothingChanges() = runBlocking {
        val g = guard(daily = null)
        withContext(g) { repeat(30) { i -> fmp.getQuote("N$i") } }
        assertEquals(30, quotes())
        assertEquals(30L to null, g.dailyUsage("fmp"))
        val env = ProviderBudgetConfig.fromEnvironment(mapOf("PROVIDER_FMP_PER_MINUTE" to "250")::get)
        assertNull(env.limits.getValue("fmp").dailyLimit, "never inferred from the per-minute value")
        val configured = ProviderBudgetConfig.fromEnvironment(mapOf("PROVIDER_FMP_DAILY_LIMIT" to "1000", "PROVIDER_SAFETY_MARGIN" to "0.5", "CLOUD_RUN_MAX_INSTANCES" to "2")::get)
        assertEquals(250L, configured.local(configured.limits.getValue("fmp")).dailyLimit, "1000 × 0.5 ÷ 2 instances")
    }
}
