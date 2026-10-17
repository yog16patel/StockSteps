package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.marketdata.alphavantage.*
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.screener.CuratedScreenerUniverse
import org.example.stocksteps.screener.CuratedUniverseFile
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 9B.7 Alpha Vantage request benchmark (MockEngine, fake clocks, no network). Upstream Alpha Vantage requests for: a cold Company
 * Details open, 1,000 concurrent readers over 20 symbols, a typing storm, rapid navigation, and Discover enrichment against the free and a
 * premium allowance. Writes `server/build/phase9b7-alpha-vantage-benchmark.txt`.
 */
class AlphaVantageBenchmarkTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private class World(premium: Boolean, val clock: MutableClock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))) {
        val av = AlphaVantageClientTest.Av { clock.millis() }
        val sessions = EodSessions(clock)
        val config = AlphaVantageConfig("k", true, if (premium) 75 else null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet())
        val client = AlphaVantageClient(av.http, config, sessions, clock, CompanyFinancialCache(capacity = 4_096, now = { clock.millis() }))
        val fmpCalls = java.util.concurrent.atomic.AtomicInteger()
        val delegate = object : org.example.stocksteps.repository.StockProviderRepository {
            override suspend fun getQuote(symbol: String) = null
            override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getProfile(symbol: String) = run { fmpCalls.incrementAndGet(); org.example.stocksteps.model.CompanyProfile(symbol, symbol) }
            override suspend fun searchStocks(query: String) = emptyList<org.example.stocksteps.model.StockSearchResult>()
            override suspend fun getFundamentals(symbol: String, period: String) = run { fmpCalls.incrementAndGet(); org.example.stocksteps.model.CompanyFundamentals(symbol) }
        }
        val stocks = AlphaVantageStockProvider(client, delegate, sessions, clock)
        val service = StockService(stocks, stocks, CompanyFinancialCache(capacity = 1_024, now = { clock.millis() }))
        val charts = PriceChartService(AlphaVantagePriceHistoryProvider(client, clock), CompanyFinancialCache(capacity = 1_024, now = { clock.millis() }), eod = sessions)
        init {
            av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("X", listOf("2026-10-15" to "10", "2026-10-16" to "11"))
            av.bodies["GLOBAL_QUOTE"] = AvFixtures.quote("X", "11", "2026-10-16", "10")
            av.bodies["OVERVIEW"] = AvFixtures.OVERVIEW; av.bodies["SYMBOL_SEARCH"] = AvFixtures.SEARCH
            av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME; av.bodies["BALANCE_SHEET"] = AvFixtures.BALANCE; av.bodies["CASH_FLOW"] = AvFixtures.CASH_FLOW
        }
        /** What Company Details asks for: profile, quote, chart, statements (the fundamentals bundle stays with FMP by configuration). */
        suspend fun details(symbol: String) = coroutineScope {
            listOf(async { runCatching { service.getProfile(symbol) } }, async { runCatching { service.getStock(symbol) } },
                async { runCatching { charts.getChart(symbol, ChartRange.ONE_YEAR) } },
                async { runCatching { stocks.statementHistory(symbol, "annual", Statement.entries.toSet()) } }).awaitAll()
        }
    }

    @Test fun benchmark() = runBlocking<Unit> {
        val rows = mutableListOf<Pair<String, String>>()
        // 1. Cold Company Details open.
        for (premium in listOf(false, true)) {
            val w = World(premium); w.details("AAPL")
            rows += "Company Details, cold (${if (premium) "premium" else "free"} plan)" to "${w.av.total} AV (${w.av.calls.keys.sorted().joinToString { "$it ${w.av.n(it)}" }}) + ${w.fmpCalls.get()} FMP"
        }
        // 2. 1,000 concurrent readers over 20 symbols, then again (warm).
        val w2 = World(true)
        (1..1000).map { i -> async(Dispatchers.Default) { w2.details("S${i % 20}") } }.awaitAll()
        val cold = w2.av.total
        (1..1000).map { i -> async(Dispatchers.Default) { w2.details("S${i % 20}") } }.awaitAll()
        rows += "1,000 concurrent Details readers × 20 symbols (premium)" to "cold $cold AV (20 × 6), warm ${w2.av.total - cold}"
        assertEquals(120, cold); assertEquals(cold, w2.av.total)
        // 3. Typing storm: 100 users type "microsoft" one letter at a time (prefixes of 2+ letters reach the server).
        val w3 = World(false)
        (1..100).map { async(Dispatchers.Default) { for (n in 2.."microsoft".length) runCatching { w3.service.searchStocks("microsoft".take(n)) } } }.awaitAll()
        rows += "100 users typing \"microsoft\" (8 prefixes each)" to "${w3.av.n("SYMBOL_SEARCH")} AV searches (one per distinct prefix)"
        assertTrue(w3.av.n("SYMBOL_SEARCH") <= 8)
        // 4. Rapid navigation: 50 back-and-forth opens of one company within a minute.
        val w4 = World(true); repeat(50) { w4.details("AAPL"); w4.clock.instant = w4.clock.instant.plusSeconds(1) }
        rows += "50 rapid re-opens of one company" to "${w4.av.total} AV (the cold 6 only)"
        assertEquals(6, w4.av.total)
        // 5. Discover enrichment (155 companies) on the free allowance (25/day → 20 on this instance) vs a 75/min plan.
        for (premium in listOf(false, true)) {
            val w = World(premium)
            val guard = ProviderGuard(ProviderBudgetConfig.fromEnvironment { if (premium) mapOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75")[it] else null },
                now = { w.clock.millis() }, meter = ProviderUsageMeter(), sleep = { w.clock.instant = w.clock.instant.plusMillis(it) })
            val priced = withContext(guard) {
                coroutineScope {
                    val u = CuratedScreenerUniverse(w.service::getProfile, w.charts::getDailyCloses, w.sessions, w.clock, this, perHour = 1_000, perDay = 400, meter = ProviderUsageMeter())
                    repeat(30) { u.universe(); u.pending?.join(); w.clock.instant = w.clock.instant.plusSeconds(120) }
                    u.universe().entries.count { it.price != null }.also { coroutineContext.cancelChildren() }
                }
            }
            rows += "Discover enrichment, 1 h (${if (premium) "75/min plan" else "free 25/day"})" to "${w.av.total} AV requests, $priced of ${CuratedUniverseFile.load().companies.size} priced, ${w.fmpCalls.get()} FMP profiles"
        }
        val text = buildString {
            appendLine("Phase 9B.7 Alpha Vantage benchmark (MockEngine, fake clock; upstream requests)")
            appendLine("| Scenario | Requests |"); appendLine("|---|---|")
            rows.forEach { (k, v) -> appendLine("| $k | $v |") }
        }
        File("build/phase9b7-alpha-vantage-benchmark.txt").writeText(text)
        println(text)
    }
}
