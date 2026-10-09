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
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.FmpFundamentalsLoader
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.screener.*
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Phase 3 audit (read-only): upstream request counts for scenarios A–G with the REAL adapters on a Ktor
 * MockEngine (no network, no keys). Measures *current* behaviour only; proposed improvements are modeled
 * in `docs/FINANCIAL_API_PHASE3_COST_MODEL.md`. Writes `server/build/phase3-audit-benchmark.txt`.
 */
class Phase3AuditBenchmarkTest {
    private val wednesday = Instant.parse("2026-10-07T18:00:00Z")

    /** Mutable clock for code that accepts a java.time.Clock. */
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private class Upstream(private val universeSize: Int = 30) {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val total get() = counts.values.sumOf { it.get() }
        fun n(path: String) = counts[path]?.get() ?: 0
        @Volatile var published = false   // Scenario G: the new quarter's statement becomes available
        @Volatile var corrected = false    // Scenario G: the provider revises it
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath.substringAfter("/stable/").substringAfter("/api/v1/")
            counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            delay(2)
            val symbol = request.url.parameters["symbol"] ?: "AAPL"
            val body = when (path) {
                "quote" -> """[{"symbol":"$symbol","price":100.0,"previousClose":99.0,"timestamp":1791396000}]"""
                "profile" -> """[{"symbol":"$symbol","companyName":"$symbol Corp","currency":"USD","exchange":"NASDAQ","sector":"Technology","industry":"Software"}]"""
                "company-screener" -> (0 until universeSize).joinToString(",", "[", "]") { i ->
                    """{"symbol":"${request.url.parameters["exchange"]}$i","companyName":"Co $i","marketCap":${(1000 - i)}000000000,"sector":"Technology","industry":"Software","price":50.0,"volume":1000000,"exchangeShortName":"${request.url.parameters["exchange"]}","country":"US"}""" }
                "income-statement" -> {
                    val q = request.url.parameters["period"] == "quarter"
                    val newest = if (q && published) """{"symbol":"$symbol","date":"2026-09-27","period":"Q4","fiscalYear":"2026","revenue":${if (corrected) 1105 else 1100}.0,"reportedCurrency":"USD","acceptedDate":"2026-10-30"},""" else ""
                    "[$newest" + (0 until 6).joinToString(",") { i -> """{"symbol":"$symbol","date":"${2026 - (i + 1)}-06-27","period":"${if (q) "Q3" else "FY"}","fiscalYear":"${2026 - i}","revenue":${1000 - i}.0,"reportedCurrency":"USD"}""" } + "]"
                }
                "historical-chart/5min" -> """[{"date":"2026-10-07 09:30:00","close":100.0},{"date":"2026-10-07 09:35:00","close":101.0}]"""
                "historical-price-eod/light" -> """[{"date":"2026-10-05","price":99.0},{"date":"2026-10-06","price":100.0}]"""
                "calendar/earnings" -> """{"earningsCalendar":[]}"""
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) } }
    }

    private class World(val up: Upstream, val clock: Clock, val scope: CoroutineScope, perHour: Int = 0) {
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k") { java.time.LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        private val prices = FmpPriceHistoryProvider(up.client, "k")
        val charts = PriceChartService(prices)
        val financials = CompanyFinancialService(fmp)
        val valuation = ValuationService(fmp, charts, fmp)
        val details = CompanyDetailsService(stocks, financials, FmpMarketDataProvider(up.client, "k"), valuation = valuation,
            marketStatus = UsMarketCalendar().let { c -> { c.marketStatusAt(clock.instant()) } })
        val store = InMemoryUserDataStore()
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val earnings = EarningsService(FinnhubEarningsDataSource(up.client, "k"), stocks, charts, store, entitlements, clock, sampleData = false, research = null)
        val screener = ScreenerService(FmpScreenerUniverse(up.client, "k", listOf("NASDAQ", "NYSE", "TSX"), 100, 2_000_000_000L), stocks,
            { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock, sampleData = false, fundamentalsPerHour = perHour, fullRecords = false,
            scope = scope, quarterlyRevenueGrowth = { s -> try { quarterRevenueGrowth(earnings.latestResults(s)) } catch (_: Exception) { MetricValue(note = "none") } })
        val history = ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements, clock, sampleData = false, source = "test")
        val research = ComparisonResearchService(store, entitlements, { s -> screener.compare(s.joinToString(",")) }, history, clock)
        val ai = ComparisonAiService({ s -> screener.compare(s.joinToString(",")) }, history, store, entitlements, TemplateComparisonAi(),
            ComparisonAiQuota(store, clock), clock, sampleData = false, source = "test")
    }

    private suspend fun <T> quiet(block: suspend () -> T): T? = try { block() } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }
    private suspend fun settle(up: Upstream) { var last = -1; while (last != up.total) { last = up.total; delay(150) } }
    private fun line(name: String, up: Upstream, extra: String = "") = "## $name — upstream ${up.total}$extra\n" +
        up.counts.toSortedMap().entries.joinToString("") { "  ${it.key} = ${it.value.get()}\n" }

    @Test fun scenarios() = runBlocking {
        val out = StringBuilder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // A — Company Details: Overview, Financials (annual and quarterly), Valuation.
            Upstream().let { up -> val w = World(up, Clock.fixed(wednesday, ZoneOffset.UTC), scope)
                quiet { w.details.getDetails("AAPL") }; quiet { w.financials.getFundamentals("AAPL", "annual") }
                quiet { w.financials.getFundamentals("AAPL", "quarter") }; quiet { w.valuation.history("AAPL") }
                out.append(line("A Company Details (overview, financials annual+quarter, valuation)", up)) }

            // B — Comparison of four, metric switches (client-only), 1Y history, repeat.
            Upstream().let { up -> val w = World(up, Clock.fixed(wednesday, ZoneOffset.UTC), scope)
                val s = listOf("AAPL", "MSFT", "KO", "RIVN")
                quiet { w.screener.compare(s.joinToString(",")) }
                listOf("1Y", "3Y").forEach { quiet { w.screener.performance(s.joinToString(","), it) } }
                val before1y = up.total
                quiet { w.history.publicHistory(s.joinToString(","), "1Y") }
                val oneYear = up.total - before1y
                quiet { w.screener.compare(s.joinToString(",")) }
                out.append(line("B Comparison of 4 (+performance, 1Y history, repeat)", up, " (1Y history alone: $oneYear)")) }

            // C — Premium research: 3Y/5Y history, detailed summary, one AI question (template provider, no Gemini).
            Upstream().let { up -> val w = World(up, Clock.fixed(wednesday, ZoneOffset.UTC), scope)
                w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
                val s = listOf("AAPL", "MSFT", "KO", "RIVN")
                quiet { w.screener.compare(s.joinToString(",")) }
                val t0 = up.total
                quiet { w.history.userHistory("plus", s.joinToString(","), "3Y") }; quiet { w.history.userHistory("plus", s.joinToString(","), "5Y") }
                val t1 = up.total
                val session = w.research.create("plus", CreateResearchRequest(s))
                quiet { w.research.summary("plus", session.session.id, true) }
                val t2 = up.total
                quiet { w.ai.ask("plus", ComparisonAiRequest(s, ComparisonAiType.QUESTION, "How has revenue changed over the years?", idempotencyKey = "phase3-audit-key"), null) }
                out.append(line("C Premium research (after a 4-company comparison)", up, " (3Y+5Y: ${t1 - t0}, detailed summary: ${t2 - t1}, AI question: ${up.total - t2})")) }

            // D — Screener: open, change filters and sort, then five Company Details; warm-up budget 25/h.
            Upstream().let { up -> val w = World(up, Clock.fixed(wednesday, ZoneOffset.UTC), scope, perHour = 25)
                quiet { w.screener.catalog() }; settle(up)
                val afterWarm = up.total
                val q = ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 30.0)), sort = ScreenerSort(SortField.METRIC, "pe", false))
                quiet { w.screener.search(q) }; quiet { w.screener.search(q.copy(sort = ScreenerSort(SortField.MARKET_CAP))) }; settle(up)
                val afterSearch = up.total
                listOf("NASDAQ0", "NASDAQ1", "NYSE5", "TSX3", "NASDAQ29").forEach { quiet { w.details.getDetails(it) } }
                out.append(line("D Screener (universe 3×30, warm-up 25/h, 2 searches, 5 details)", up,
                    " (catalog+warm-up: $afterWarm, searches: ${afterSearch - afterWarm}, 5 details: ${up.total - afterSearch})")) }

            // E — 50 users, overlapping symbols (10 distinct), all at once.
            Upstream().let { up -> val w = World(up, Clock.fixed(wednesday, ZoneOffset.UTC), scope)
                val symbols = listOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "KO", "JPM", "TD.TO", "RY.TO")
                coroutineScope { (0 until 50).map { i -> async(Dispatchers.Default) { quiet { w.details.getDetails(symbols[i % symbols.size]) } } }.awaitAll() }
                out.append(line("E 50 users, 10 overlapping symbols (Company Details)", up)) }

            // F — Market closed (Saturday): the same fundamentals 10 minutes apart; loader with a fake cache clock.
            Upstream().let { up ->
                val ms = AtomicLong(0)
                val loader = FmpFundamentalsLoader(up.client, "k", CompanyFinancialCache(now = { ms.get() }), { java.time.LocalDate.parse("2026-10-10") })
                val quote: suspend () -> Pair<StockQuote?, CompanyProfile?> = { null to null }
                loader.load("AAPL", "annual", quote = quote); val first = up.total
                ms.addAndGet(10 * 60_000); loader.load("AAPL", "annual", quote = quote)
                ms.addAndGet(10 * 60_000); loader.load("AAPL", "annual", quote = quote)
                out.append(line("F Market closed: fundamentals at t, t+10 min, t+20 min", up, " (first load $first; repeats ${up.total - first})")) }

            // G — Earnings: statement published 2 h after a first load, then corrected; 24 h statement TTL.
            Upstream().let { up ->
                val ms = AtomicLong(0)
                val loader = FmpFundamentalsLoader(up.client, "k", CompanyFinancialCache(now = { ms.get() }), { java.time.LocalDate.parse("2026-10-31") })
                val quote: suspend () -> Pair<StockQuote?, CompanyProfile?> = { null to null }
                fun latest(f: org.example.stocksteps.model.CompanyFundamentals) = f.history.maxByOrNull { it.date.orEmpty() }?.let { "${it.date} revenue ${it.revenue}" }
                val r0 = latest(loader.load("AAPL", "quarter", quote = quote))
                up.published = true; ms.addAndGet(2 * 3_600_000)
                val r1 = latest(loader.load("AAPL", "quarter", quote = quote))
                ms.addAndGet(23 * 3_600_000)
                val r2 = latest(loader.load("AAPL", "quarter", quote = quote))
                up.corrected = true; ms.addAndGet(3_600_000)
                val r3 = latest(loader.load("AAPL", "quarter", quote = quote))
                out.append(line("G Earnings publication and correction (quarterly)", up,
                    "\n  before: $r0 | +2 h after publication: $r1 | +25 h: $r2 | +1 h after correction: $r3"))
            }

            // Re-warm check: after the 6 h record TTL, are expired screener fundamentals loaded again?
            // Universe 3 × 5 = 15 symbols < hourly budget 25: a working re-warm restores full coverage after expiry.
            Upstream(universeSize = 5).let { up -> val clock = MutableClock(wednesday); val w = World(up, clock, scope, perHour = 25)
                quiet { w.screener.catalog() }; settle(up)
                val warmed = w.screener.catalog().universe.evaluated
                clock.instant = wednesday.plusSeconds(7 * 3600)
                quiet { w.screener.catalog() }; settle(up)
                val later = w.screener.catalog().universe.evaluated
                out.append("## Screener re-warm after 7 h — evaluated $warmed → $later of ${w.screener.catalog().universe.size} (fundamentals loads: ${up.n("ratios-ttm")})\n") }
        } finally { scope.cancel() }
        val text = out.toString()
        println(text)
        File("build/phase3-audit-benchmark.txt").writeText(text)
    }
}
