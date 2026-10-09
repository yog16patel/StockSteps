package org.example.stocksteps.screener

import kotlinx.coroutines.*
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.ProviderUsageMeter
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import kotlin.test.*

/**
 * Phase 3B-0: screener fundamentals are re-warmed after they expire (the audit's bug: expired entries were never
 * reloaded, so financial filters evaluated 15 → 0 companies after 7 h), within the hourly budget, oldest first,
 * without duplicate loads or retry storms. Fake clock and loader; no network.
 */
class ScreenerWarmupTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun advance(seconds: Long) { instant = instant.plusSeconds(seconds) }
    }

    private val start = Instant.parse("2026-10-07T14:00:00Z")
    private val fixture = FixtureMarketDataSource(sampleFallback = false)
    private val hour = 3_600L

    private inner class World(size: Int, val perHour: Int, val loader: suspend (String) -> CompanyFundamentals = { ok(it) }) {
        val clock = MutableClock(start)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val meter = ProviderUsageMeter()
        val entries = (0 until size).map { UniverseEntry("S$it", "Co $it", "NYSE", "US", "Technology", null, 50.0, 1e10, 1e6) }
        val service = ScreenerService({ UniverseDefinition("test universe", entries) }, StockService(fixture, fixture),
            { s -> calls += s; loader(s) }, PriceChartService(fixture), { 0.73 }, clock, sampleData = false,
            fundamentalsPerHour = perHour, fullRecords = false, scope = scope, meter = meter)
        suspend fun settle() { while (true) { val running = scope.coroutineContext.job.children.toList(); if (running.isEmpty()) return; running.joinAll() } }
        suspend fun evaluated(): Int { service.catalog(); settle(); return service.catalog().universe.evaluated.also { settle() } }
        fun loads(symbol: String) = calls.count { it == symbol }
    }

    private fun ok(symbol: String, vararg extra: Pair<String, FinancialAvailability>) =
        CompanyFundamentals(symbol, datasets = mapOf("income" to FinancialAvailability.AVAILABLE) + extra)

    @Test fun expiredFundamentalsAreReloadedAndCoverageRecovers() = runBlocking {
        val w = World(15, perHour = 25)
        assertEquals(0, w.service.catalog().universe.evaluated, "nothing is loaded before the first warm-up")
        w.settle()
        assertEquals(15, w.service.catalog().universe.evaluated)
        assertEquals(15, w.calls.size)
        w.clock.advance(5 * hour)
        assertEquals(15, w.evaluated(), "still fresh before the 6 h record TTL")
        assertEquals(15, w.calls.size, "no reload before expiry")
        w.clock.advance(hour + 60)
        // Expired data isn't used for screening (not evaluated), and every expired company becomes due again.
        assertEquals(0, w.service.catalog().universe.evaluated)
        w.settle()
        assertEquals(15, w.service.catalog().universe.evaluated, "coverage recovers after the re-warm")
        assertEquals(30, w.calls.size, "exactly one reload per expired company")
        assertEquals(15L, w.meter.eventCount("screener.warm.initial"))
        assertEquals(15L, w.meter.eventCount("screener.warm.refresh"))
        w.clock.advance(6 * hour); w.evaluated(); w.clock.advance(6 * hour); w.evaluated(); w.clock.advance(6 * hour)
        assertEquals(15, w.evaluated(), "coverage doesn't collapse over a day")
        assertEquals(75, w.calls.size, "one load per company per 6 h")
        w.scope.cancel()
    }

    @Test fun budgetIsRespectedAndOldestLoadsAreRefreshedFirst() = runBlocking {
        val w = World(10, perHour = 4)
        w.evaluated()
        // Loads run concurrently (4 permits), so each step is compared as a set.
        assertEquals(setOf("S0", "S1", "S2", "S3"), w.calls.toSet(), "never-loaded companies first, in universe order")
        w.clock.advance(30 * 60); w.evaluated()
        assertEquals(4, w.calls.size, "budget exhausted: no extra upstream loads within the hour")
        assertTrue(w.meter.eventCount("screener.warm.budgetReached") > 0)
        w.clock.advance(30 * 60); w.evaluated()          // t + 1 h
        assertEquals(setOf("S4", "S5", "S6", "S7"), w.calls.drop(4).toSet())
        w.clock.advance(hour); w.evaluated()             // t + 2 h
        assertEquals(setOf("S8", "S9"), w.calls.drop(8).toSet(), "only due companies are loaded, even with budget left")
        w.clock.advance(4 * hour + 1); w.evaluated()     // t + 6 h: S0–S3 expired
        assertEquals(setOf("S0", "S1", "S2", "S3"), w.calls.drop(10).toSet())
        w.clock.advance(2 * hour); w.evaluated()         // t + 8 h: S4–S7 (t + 1 h) and S8–S9 (t + 2 h) expired, room 4
        assertEquals(setOf("S4", "S5", "S6", "S7"), w.calls.drop(14).toSet(), "the oldest expired loads go first")
        w.clock.advance(hour); w.evaluated()
        assertEquals(setOf("S8", "S9"), w.calls.drop(18).toSet())
        assertEquals(20, w.calls.size, "no load beyond the ones above; never more than 4 in any rolling hour")
        w.scope.cancel()
    }

    @Test fun failedLoadsAreRetriedOnlyAfterTheBackoff() = runBlocking {
        val w = World(3, perHour = 25) { s -> if (s == "S1") throw IllegalStateException("provider down") else ok(s) }
        repeat(10) { w.evaluated(); w.clock.advance(60) }
        assertEquals(1, w.loads("S1"), "no retry storm while searches continue")
        assertEquals(2, w.service.catalog().universe.evaluated)
        assertEquals(1L, w.meter.eventCount("screener.warm.failed"))
        w.clock.advance(30 * 60); w.evaluated()
        assertEquals(2, w.loads("S1"), "retried once after the 30-minute backoff")
        w.scope.cancel()
    }

    @Test fun aLoadWhereEveryDatasetFailedIsNotHeldAsData() = runBlocking {
        val down = CompanyFundamentals("S0", datasets = mapOf("income" to FinancialAvailability.TEMPORARILY_UNAVAILABLE, "ratiosTtm" to FinancialAvailability.TEMPORARILY_UNAVAILABLE))
        var failing = true
        val w = World(1, perHour = 25) { s -> if (failing) down else ok(s) }
        assertEquals(0, w.evaluated(), "a provider outage isn't presented as an evaluated company with nothing to match")
        failing = false
        w.clock.advance(60); w.evaluated()
        assertEquals(1, w.calls.size, "backoff applies to failed loads too")
        w.clock.advance(30 * 60)
        assertEquals(1, w.evaluated())
        assertEquals(2, w.calls.size)
        w.scope.cancel()
    }

    @Test fun partialLoadsAreRefreshedSoonerButStillScreened() = runBlocking {
        val w = World(1, perHour = 25) { s -> ok(s, "estimates" to FinancialAvailability.TEMPORARILY_UNAVAILABLE) }
        assertEquals(1, w.evaluated())
        w.clock.advance(hour + 1)
        assertEquals(1, w.evaluated(), "partial data stays usable until the record TTL")
        assertEquals(2, w.calls.size, "and is reloaded after an hour instead of six")
        w.scope.cancel()
    }

    @Test fun concurrentSearchesDoNotDuplicateWarmUps() = runBlocking {
        val w = World(8, perHour = 25) { s -> delay(100); ok(s) }
        coroutineScope { repeat(20) { launch(Dispatchers.Default) { w.service.catalog() } } }
        w.settle()
        assertEquals(8, w.calls.size)
        assertEquals(8, w.calls.distinct().size)
        w.scope.cancel()
    }

    @Test fun aCancelledLoadDoesNotLeaveTheSymbolMarkedAsLoading() = runBlocking {
        var first = true
        val w = World(1, perHour = 25) { s -> if (first) { first = false; throw CancellationException("client went away") } else ok(s) }
        w.evaluated()
        w.clock.advance(60)
        assertEquals(1, w.evaluated(), "the next search schedules the company again")
        assertEquals(2, w.calls.size)
        w.scope.cancel()
    }
}
