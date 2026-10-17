package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
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
 * Phase 7B Markets request benchmark (MockEngine, fake clocks, no network). "Before" = the Phase 7A quote path (sectors, index
 * proxies and most-active volumes from quotes, refused quotes cooled down for 1 h); "After" = the EOD path. Both use the same mock:
 * every index, proxy and sector-fund quote refused (402) as observed on the current plan; daily closes served for every symbol through
 * Friday 2026-10-16. Writes `server/build/phase7b-markets-benchmark.txt`.
 */
class MarketsEodBenchmarkTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun minutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    }

    private class World(eodFirst: Boolean, start: String, eodStatus: Int? = null, missing: Set<String> = emptySet()) {
        val clock = MutableClock(Instant.parse(start))
        val up = FmpMock(latency = 1)
        init {
            val symbols = MarketsService.SECTORS.map { it.second } + MarketsService.INDICES.flatMap { listOf(it.symbol, it.proxySymbol) }
            symbols.forEach { s ->
                up.symbolStatus["quote|$s"] = 402
                up.bodies["historical-price-eod/light|$s"] = if (s in missing) "[]"
                    else """[{"date":"2026-10-14","price":99.0},{"date":"2026-10-15","price":100.0},{"date":"2026-10-16","price":101.0}]"""
                eodStatus?.let { up.symbolStatus["historical-price-eod/light|$s"] = it }
            }
            // A most-active list without volumes (the case that triggered quote enrichment).
            up.bodies["most-actives"] = (1..10).joinToString(",", "[", "]") { """{"symbol":"A$it","name":"A$it","price":5.0,"changesPercentage":1.0,"exchange":"NYSE"}""" }
        }
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 128, now = { clock.millis() }), eod = EodSessions(clock))
        val markets = MarketsService(FmpMarketDataProvider(up.client, "k"), fmp, object : IndexDataSource {
            override suspend fun indexQuote(symbol: String) = fmp.getQuote(symbol)
            override suspend fun indexHistory(symbol: String) = charts.getDailyCloses(symbol)
        }, NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() }),
            MarketsSourceLabels("u", "q", false, "Delayed"), clock = clock, cache = CompanyFinancialCache(capacity = 128, now = { clock.millis() }),
            dailyCloses = if (eodFirst) charts::getDailyCloses else null, eod = EodSessions(clock))
        suspend fun open() = runCatching { markets.overview() }
        fun counts() = Triple(up.n("quote"), up.n("historical-price-eod/light"),
            up.n("biggest-gainers") + up.n("biggest-losers") + up.n("most-actives"))
    }

    private data class Row(val name: String, val before: Triple<Int, Int, Int>, val after: Triple<Int, Int, Int>)

    private suspend fun scenario(name: String, start: String, eodStatus: Int? = null, missing: Set<String> = emptySet(), run: suspend World.() -> Unit): Row {
        val b = World(false, start, eodStatus, missing).apply { run() }.counts()
        val a = World(true, start, eodStatus, missing).apply { run() }.counts()
        return Row(name, b, a)
    }

    @Test fun benchmark() = runBlocking {
        val friday = "2026-10-16T22:00:00Z"   // Fri 18:00 ET (closed)
        val mondayPre = "2026-10-19T08:00:00Z"   // Mon 04:00 ET (pre-market starts)
        val rows = listOf(
            scenario("One cold overview (Fri 18:00 ET, after-hours)", friday) { open() },
            scenario("Cold + one warm overview 1 min later", friday) { open(); clock.minutes(1); open() },
            scenario("10 opens, 6 min apart (Fri after-hours, 1 h)", friday) { repeat(10) { open(); clock.minutes(6) } },
            scenario("Monday 04:00–20:00 ET, an open every minute (960 builds)", mondayPre) { repeat(960) { open(); clock.minutes(1) } },
            scenario("Weekend: 48 opens, one every 30 min", "2026-10-17T12:00:00Z") { repeat(48) { open(); clock.minutes(30) } },
            scenario("20 concurrent cold overviews", friday) { (1..20).map { async(Dispatchers.Default) { open() } }.awaitAll() },
            scenario("Restricted EOD (402 for every close), 10 opens over 1 h", friday, eodStatus = 402) { repeat(10) { open(); clock.minutes(6) } },
            scenario("Missing data for 3 sector funds, 10 opens over 1 h", friday, missing = setOf("XLRE", "XLU", "XLB")) { repeat(10) { open(); clock.minutes(6) } },
        )
        val text = buildString {
            appendLine("Phase 7B Markets benchmark (MockEngine; upstream FMP requests; before = Phase 7A quote path, after = EOD path)")
            appendLine("| Scenario | Before quote | Before EOD | Before movers | Before total | After quote | After EOD | After movers | After total |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
            rows.forEach { r -> appendLine("| ${r.name} | ${r.before.first} | ${r.before.second} | ${r.before.third} | ${r.before.toList().sum()} | ${r.after.first} | ${r.after.second} | ${r.after.third} | ${r.after.toList().sum()} |") }
        }
        File("build/phase7b-markets-benchmark.txt").writeText(text)
        println(text)
        val day = rows.single { it.name.startsWith("Monday") }
        assertTrue(day.after.first < day.before.first, "fewer quote requests over a trading day")
        assertEquals(3, rows.single { it.name.startsWith("20 concurrent") }.after.first, "Fri 18:00 ET is US after-hours: only the three US index quotes (shared by all 20)")
        assertEquals(0, rows.single { it.name.startsWith("Weekend") }.after.first)
    }
}
