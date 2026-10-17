package org.example.stocksteps.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.example.stocksteps.httpclient.ProviderCalls
import org.example.stocksteps.marketdata.sec.SecEdgarClient
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.screener.CuratedScreenerUniverse
import org.example.stocksteps.screener.CuratedUniverseFile
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 9B request benchmarks (MockEngine, fake clocks, no network):
 * 1. Discover background enrichment per UTC day over two weeks, before (Phase 7E: 60/h, no daily cap) and after (60/h + 100 upstream
 *    requests/day), with coverage at 20:45 UTC each day;
 * 2. a Discover open storm (300 concurrent opens);
 * 3. SEC EDGAR fundamentals for 1,000 concurrent readers over 20 companies (cold, then warm).
 * Writes `server/build/phase9b-market-data-benchmark.txt`.
 */
class MarketDataPhase9bBenchmarkTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private class Discover(scope: CoroutineScope, perDay: Int?, start: Instant) {
        val clock = MutableClock(start)
        val up = FmpMock(latency = 0)
        val eod = EodSessions(clock)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.now(clock) }
        val stocks = StockService(fmp, fmp, CompanyFinancialCache(capacity = 1024, now = { clock.millis() }))
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 1024, now = { clock.millis() }), eod = eod)
        val universe = CuratedScreenerUniverse(stocks::getProfile, charts::getDailyCloses, eod, clock, scope, perHour = CuratedScreenerUniverse.DEFAULT_PER_HOUR,
            perDay = perDay, meter = ProviderUsageMeter())
        private var published: LocalDate? = null
        /** The provider publishes the expected session's close (two trading days per series). */
        fun publish() {
            val expected = eod.expectedSession(EodSessions.Market.US, clock.instant())
            if (expected == published) return
            published = expected
            val previous = eod.previousTradingDay(EodSessions.Market.US, expected)
            CuratedUniverseFile.load().companies.forEach { c ->
                up.bodies["historical-price-eod/light|${c.symbol}"] = """[{"date":"$previous","price":100.0},{"date":"$expected","price":101.0}]"""
            }
        }
        fun requests() = up.n("profile") + up.n("historical-price-eod/light")
        fun profiles() = up.n("profile")
        suspend fun open() { universe.universe(); universe.pending?.join() }
    }

    private data class Day(val date: LocalDate, val profiles: Int, val closes: Int, val priced: Int, val latest: Int)

    private suspend fun twoWeeks(perDay: Int?): List<Day> = coroutineScope {
        val start = Instant.parse("2026-10-15T00:00:00Z")
        val d = Discover(this, perDay, start)
        val days = mutableListOf<Day>()
        var dayStart = d.profiles() to d.requests() - d.profiles()
        var coverage = 0 to 0
        var date = start.atZone(ZoneOffset.UTC).toLocalDate()
        while (d.clock.instant < start.plusSeconds(14 * 86_400L)) {
            d.publish(); d.open()
            val t = d.clock.instant.atZone(ZoneOffset.UTC)
            if (t.hour == 20 && t.minute == 45) {
                val u = d.universe.universe()
                val expected = d.eod.expectedSession(EodSessions.Market.US, d.clock.instant())
                coverage = u.entries.count { it.price != null } to u.entries.count { it.priceDate == expected.toString() }
            }
            d.clock.instant = d.clock.instant.plusSeconds(15 * 60)
            val next = d.clock.instant.atZone(ZoneOffset.UTC).toLocalDate()
            if (next != date) {
                d.universe.pending?.join()
                val now = d.profiles() to d.requests() - d.profiles()
                days += Day(date, now.first - dayStart.first, now.second - dayStart.second, coverage.first, coverage.second)
                dayStart = now; date = next
            }
        }
        coroutineContext.cancelChildren()
        days
    }

    @Test fun benchmark() = runBlocking<Unit> {
        val before = twoWeeks(null)
        val after = twoWeeks(CuratedScreenerUniverse.DEFAULT_PER_DAY)
        // The cap holds every day (it counts real upstream requests, reserved before each company starts).
        after.forEach { assertTrue(it.profiles + it.closes <= CuratedScreenerUniverse.DEFAULT_PER_DAY, "$it") }
        assertTrue(before.maxOf { it.profiles + it.closes } > CuratedScreenerUniverse.DEFAULT_PER_DAY)
        assertEquals(155, after.last().priced, "every company priced within the fortnight")

        // Storm: 300 concurrent opens on a cold instance cost at most the day's cap.
        val storm = coroutineScope {
            val d = Discover(this, CuratedScreenerUniverse.DEFAULT_PER_DAY, Instant.parse("2026-10-16T22:00:00Z"))
            d.publish()
            (1..300).map { async { d.universe.universe() } }.awaitAll(); d.universe.pending?.join()
            d.requests().also { coroutineContext.cancelChildren() }
        }
        assertTrue(storm <= CuratedScreenerUniverse.DEFAULT_PER_DAY, "storm $storm")

        // SEC: 1,000 concurrent readers over 20 companies.
        val secCounts = ConcurrentHashMap<String, AtomicInteger>()
        val facts = org.example.stocksteps.marketdata.SecFixtures.json(1001, "X", org.example.stocksteps.marketdata.SecFixtures.growthCo)
        val tickers = org.example.stocksteps.marketdata.SecFixtures.tickersJson(*(1..20).map { Triple(1000L + it, "Co $it", "T$it") }.toTypedArray())
        val secClient = HttpClient(MockEngine { req ->
            delay(3)
            val key = ProviderCalls.endpoint(req.url.toString()); secCounts.getOrPut(key) { AtomicInteger() }.incrementAndGet()
            if (key == "company_tickers_exchange.json") respond(tickers, HttpStatusCode.OK) else respond(facts, HttpStatusCode.OK)
        })
        val sec = SecEdgarClient(secClient, "StockSteps bench ops@example.com", CompanyFinancialCache(capacity = 64))
        (1..1000).map { i -> async(Dispatchers.Default) { sec.history("T${i % 20 + 1}") } }.awaitAll()
        val cold = secCounts.values.sumOf { it.get() }
        (1..1000).map { i -> async(Dispatchers.Default) { sec.history("T${i % 20 + 1}") } }.awaitAll()
        val warm = secCounts.values.sumOf { it.get() } - cold
        assertEquals(21, cold, "1 ticker file + 1 companyfacts per company"); assertEquals(0, warm)

        val text = buildString {
            appendLine("Phase 9B market-data benchmark (MockEngine, fake clock; upstream requests)")
            appendLine()
            appendLine("1. Discover enrichment per UTC day (profile / EOD / total · priced, latest close at 20:45 UTC)")
            appendLine("| UTC day | Before (7E: 60/h, no daily cap) | After (9B: 60/h + 100/day) |")
            appendLine("|---|---|---|")
            before.zip(after).forEach { (b, a) ->
                appendLine("| ${b.date} ${b.date.dayOfWeek.name.take(3).lowercase()} | ${b.profiles} / ${b.closes} / ${b.profiles + b.closes} · ${b.priced}, ${b.latest} | ${a.profiles} / ${a.closes} / ${a.profiles + a.closes} · ${a.priced}, ${a.latest} |")
            }
            appendLine("| Two weeks | ${before.sumOf { it.profiles + it.closes }} | ${after.sumOf { it.profiles + it.closes }} |")
            appendLine("| Highest day | ${before.maxOf { it.profiles + it.closes }} | ${after.maxOf { it.profiles + it.closes }} |")
            appendLine()
            appendLine("2. 300 concurrent Discover opens on a cold instance (after): $storm upstream requests")
            appendLine()
            appendLine("3. SEC EDGAR, 1,000 concurrent readers over 20 companies: cold $cold requests (1 ticker file + 20 companyfacts), warm $warm")
        }
        File("build/phase9b-market-data-benchmark.txt").writeText(text)
        println(text)
    }
}
