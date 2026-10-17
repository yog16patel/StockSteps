package org.example.stocksteps.screener

import kotlinx.coroutines.*
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 9B: the Discover enrichment daily cap counts real upstream requests (REAL adapters on a MockEngine, fake clock). */
class DiscoverDailyBudgetTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }
    private class World(scope: CoroutineScope, perDay: Int?, status: Int? = null) {
        val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
        val up = FmpMock(latency = 0)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
        val stocks = StockService(fmp, fmp, CompanyFinancialCache(capacity = 512, now = { clock.millis() }))
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 512, now = { clock.millis() }), eod = EodSessions(clock))
        val u = CuratedScreenerUniverse(stocks::getProfile, charts::getDailyCloses, EodSessions(clock), clock, scope, perHour = 1_000, perDay = perDay, meter = ProviderUsageMeter())
        init {
            CuratedUniverseFile.load().companies.forEach { c -> up.bodies["historical-price-eod/light|${c.symbol}"] = """[{"date":"2026-10-15","price":10.0},{"date":"2026-10-16","price":11.0}]""" }
            status?.let { up.status["profile"] = it; up.status["historical-price-eod/light"] = it }
        }
        fun upstream() = up.n("profile") + up.n("historical-price-eod/light")
        suspend fun open() { u.universe(); u.pending?.join() }
    }

    @Test fun zeroMeansNoBackgroundRequests() = runBlocking<Unit> {
        val w = World(this, perDay = 0)
        repeat(5) { w.open() }
        assertEquals(0, w.upstream())
        assertTrue(w.u.universe().entries.all { it.price == null }, "nothing invented")
        coroutineContext.cancelChildren()
    }

    @Test fun capHoldsUnderConcurrencyAndResetsAtUtcMidnight() = runBlocking<Unit> {
        val w = World(this, perDay = 40)
        (1..50).map { async { w.u.universe() } }.awaitAll(); w.u.pending?.join()
        repeat(5) { w.open() }
        assertEquals(40, w.upstream(), "exactly the day's allowance: 20 companies × (profile + close)")
        assertEquals(40, w.u.usedTodayForTests)
        w.clock.instant = Instant.parse("2026-10-17T00:30:00Z")
        repeat(3) { w.open() }
        assertEquals(80, w.upstream(), "a new UTC day has a new allowance")
        coroutineContext.cancelChildren()
    }

    @Test fun refusedRequestsCountAndAreNotRepeatedPastTheCap() = runBlocking<Unit> {
        val w = World(this, perDay = 30, status = 402)
        w.clock.instant = Instant.parse("2026-10-16T01:00:00Z")   // 20 × 31 min stays inside one UTC day
        repeat(20) { w.open(); w.clock.instant = w.clock.instant.plusSeconds(31 * 60) }
        assertTrue(w.upstream() <= 30, "402s are upstream requests too: ${w.upstream()}")
        coroutineContext.cancelChildren()
    }

    @Test fun cacheHitsCostNothing() = runBlocking<Unit> {
        val w = World(this, perDay = 100)
        // Warm the shared caches as user traffic would (Company Details reading profiles and charts).
        CuratedUniverseFile.load().companies.take(30).forEach { c -> w.stocks.getProfile(c.symbol); w.charts.getDailyCloses(c.symbol) }
        val warmed = w.upstream()
        repeat(3) { w.open() }
        // Pass 1 reserves 50 companies: the 30 warmed ones cost nothing and are refunded, 20 cost 40; pass 2 uses the remaining 60.
        assertEquals(warmed + 100, w.upstream(), "the 30 warmed companies were enriched for free; 50 more used the allowance")
        assertEquals(80, w.u.universe().entries.count { it.price != null })
        coroutineContext.cancelChildren()
    }
}
