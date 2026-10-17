package org.example.stocksteps.screener

import kotlinx.coroutines.*
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 7E Discover request benchmark (MockEngine, fake clocks, no network). Upstream FMP requests by endpoint for: the FMP screener path
 * on the current plan (screener refused, 402), the FMP screener path on a plan that includes it, and the curated universe. The curated
 * column includes its background enrichment pass. Writes `server/build/phase7e-discover-benchmark.txt`.
 */
class CuratedDiscoverBenchmarkTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun minutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    }

    private enum class Mode { FMP_RESTRICTED, FMP_LICENSED, CURATED }

    private class World(val mode: Mode, scope: CoroutineScope, providerStatus: Int? = null) {
        val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
        val up = FmpMock(universeSize = 50, latency = 1)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
        val stocks = StockService(fmp, fmp, CompanyFinancialCache(capacity = 512, now = { clock.millis() }))
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 512, now = { clock.millis() }), eod = EodSessions(clock))
        val curated = if (mode == Mode.CURATED) CuratedScreenerUniverse(stocks::getProfile, charts::getDailyCloses, EodSessions(clock), clock, scope, perHour = 500, perDay = null, meter = ProviderUsageMeter()) else null
        val universe: ScreenerUniverseSource = curated ?: FmpScreenerUniverse(up.client, "k", listOf("NASDAQ", "NYSE", "TSX"), 50, 2_000_000_000L,
            cache = CompanyFinancialCache(capacity = 4, now = { clock.millis() }))
        val financials = CompanyFinancialService(fmp)
        val service = ScreenerService(universe, stocks, { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock, sampleData = false,
            fundamentalsPerHour = 0, fullRecords = false, scope = scope, meter = ProviderUsageMeter(), pageQuotes = mode != Mode.CURATED,
            pageCache = CompanyFinancialCache(capacity = 64, now = { clock.millis() }))
        init {
            if (mode == Mode.FMP_RESTRICTED) up.status["company-screener"] = 402
            CuratedUniverseFile.load().companies.forEach { c ->
                up.bodies["historical-price-eod/light|${c.symbol}"] = """[{"date":"2026-10-15","price":100.0},{"date":"2026-10-16","price":101.0}]"""
            }
            providerStatus?.let { s -> up.status["profile"] = s; up.status["historical-price-eod/light"] = s }
        }
        suspend fun open() = runCatching { service.catalog() }
        suspend fun page(q: ScreenerQuery = ScreenerQuery()) = runCatching { service.search(q) }
        suspend fun settle() { curated?.pending?.join() }
        fun counts() = listOf(up.n("company-screener"), up.n("profile"), up.n("historical-price-eod/light"), up.n("quote"),
            listOf("income-statement", "balance-sheet-statement", "cash-flow-statement", "ratios-ttm", "key-metrics-ttm").sumOf { up.n(it) }, up.total)
    }

    private suspend fun scenario(name: String, providerStatus: Int? = null, run: suspend World.() -> Unit): Pair<String, List<List<Int>>> = coroutineScope {
        name to Mode.entries.map { mode -> val w = World(mode, this, providerStatus); w.run(); w.settle(); w.counts().also { coroutineContext.cancelChildren() } }
    }

    @Test fun benchmark() = runBlocking<Unit> {
        val rows = listOf(
            scenario("One cold open (catalog)") { open() },
            scenario("Cold open, then a warm open") { open(); settle(); open() },
            scenario("Ten repeated opens") { repeat(10) { open(); settle(); clock.minutes(1) } },
            scenario("First page of 20") { open(); settle(); page() },
            scenario("Three pages") { open(); settle(); var p = page().getOrNull(); repeat(2) { p = p?.nextCursor?.let { c -> page(ScreenerQuery(cursor = c)).getOrNull() } } },
            scenario("Filter by sector") { open(); settle(); page(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("Technology"))))) },
            scenario("Sort by market cap") { open(); settle(); page(ScreenerQuery(sort = ScreenerSort(SortField.MARKET_CAP, descending = true))) },
            scenario("Ten concurrent users (open + first page)") { open(); settle(); (1..10).map { async { page() } }.awaitAll() },
            scenario("Provider 402 for profiles and closes, 10 opens over 1 h", providerStatus = 402) { repeat(10) { open(); settle(); clock.minutes(6) } },
            scenario("Provider 429 for profiles and closes, 10 opens over 1 h", providerStatus = 429) { repeat(10) { open(); settle(); clock.minutes(6) } },
        )
        val text = buildString {
            appendLine("Phase 7E Discover benchmark (MockEngine; upstream FMP requests: screener / profile / EOD / quote / fundamentals / total)")
            appendLine("| Scenario | FMP screener path, current plan (402) | FMP screener path, plan with screener | Curated universe |")
            appendLine("|---|---|---|---|")
            rows.forEach { (n, c) -> appendLine("| $n | ${c.joinToString(" | ") { it.joinToString(" / ") }} |") }
        }
        File("build/phase7e-discover-benchmark.txt").writeText(text)
        println(text)
        rows.forEach { (_, c) -> assertEquals(0, c[2][0], "curated: no screener request"); assertEquals(0, c[2][3], "curated: no quote request") }
        val tenOpens = rows.single { it.first == "Ten repeated opens" }.second[2]
        assertEquals(155 * 2, tenOpens[5], "one profile and one close series per company, once")
        assertTrue(rows.single { it.first.startsWith("Ten concurrent") }.second[2][5] == 155 * 2)
    }
}
