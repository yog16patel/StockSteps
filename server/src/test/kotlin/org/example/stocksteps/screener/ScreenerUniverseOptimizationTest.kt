package org.example.stocksteps.screener

import kotlinx.coroutines.*
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

/**
 * Phase 3B-1: the default universe is at most 50 companies per exchange (NASDAQ + NYSE + TSX = 150), warm-up uses the
 * screener dataset set (10 datasets + quote, no historical ratios or profile) with unchanged metric semantics, the
 * budget bounds upstream work and the FMP dataset cache stays within its capacity. REAL adapters on a MockEngine.
 */
class ScreenerUniverseOptimizationTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private class World(universeSize: Int, perHour: Int, capacity: Int = FmpStockProviderRepositoryImpl.DEFAULT_DATASET_CACHE_ENTRIES) {
        val up = FmpMock(universeSize)
        val clock = MutableClock(Instant.parse("2026-10-07T14:00:00Z"))          // Wednesday, US and TSX regular session
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", capacity, cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        val financials = CompanyFinancialService(fmp)
        val universe = FmpScreenerUniverse(up.client, "k", listOf("NASDAQ", "NYSE", "TSX"), 50, 2_000_000_000L)
        val service = ScreenerService(universe, stocks, { financials.getFundamentals(it, "annual") }, PriceChartService(FmpPriceHistoryProvider(up.client, "k")),
            { 0.73 }, clock, sampleData = false, fundamentalsPerHour = perHour, fullRecords = false, scope = scope,
            screenerFundamentalsOf = { s, c -> financials.screenerFundamentals(s, c) }, meter = ProviderUsageMeter())
        suspend fun settle() { while (true) { val running = scope.coroutineContext.job.children.toList(); if (running.isEmpty()) return; running.joinAll() } }
        suspend fun coverage(): Int { service.catalog(); settle(); return service.catalog().universe.evaluated }
    }

    @Test fun defaultUniverseIsAtMostFiftyPerExchange() = runBlocking {
        val w = World(universeSize = 80, perHour = 0)
        val definition = w.universe.universe()
        assertEquals(150, definition.entries.size)
        assertEquals(mapOf("NASDAQ" to 50, "NYSE" to 50, "TSX" to 50), definition.entries.groupingBy { it.exchange!! }.eachCount())
        assertTrue(definition.entries.filter { it.exchange == "TSX" }.all { it.symbol.endsWith(".TO") }, "TSX listings stay exchange-qualified")
        assertEquals(150, definition.entries.map { it.symbol }.distinct().size)
        assertTrue("Up to 50" in definition.description && "largest" !in definition.description && "Not every listed company" in definition.description)
        assertEquals(3, w.up.total, "one universe request per exchange")
        w.universe.universe()
        assertEquals(3, w.up.total, "cached for 24 h")
        w.scope.cancel()
    }

    @Test fun aColdScreenerCompanyCostsElevenRequestsNotThirteen() = runBlocking {
        val w = World(universeSize = 2, perHour = 25)
        assertEquals(6, w.coverage())
        val warm = w.up.total - w.up.n("company-screener")
        assertEquals(6 * 11, warm, "10 datasets + quote per company")
        assertEquals(0, w.up.n("profile"), "listing currency comes from the universe")
        assertEquals(0, w.up.n("ratios"), "historical annual ratios aren't loaded for screening")
        assertEquals(6, w.up.n("quote"))
        w.scope.cancel()
    }

    @Test fun screenerSetGivesTheSameMetricsAsTheFullBundle() = runBlocking {
        val w = World(universeSize = 1, perHour = 0)
        for ((symbol, currency) in listOf("NASDAQ0" to "USD", "T0.TO" to "CAD")) {
            val full = w.financials.getFundamentals(symbol, "annual")
            val lean = w.financials.screenerFundamentals(symbol, currency)
            assertEquals(full.financials, lean.financials, symbol)
            assertEquals(full.valuation.metrics, lean.valuation.metrics, symbol)
            assertEquals(full.history, lean.history, symbol)
            val today = "2026-10-07"
            val a = CompanyRecordBuilder.build(symbol, null, null, full, 1.0, today); val b = CompanyRecordBuilder.build(symbol, null, null, lean, 1.0, today)
            assertEquals(a.metrics, b.metrics, "$symbol screener metrics")
        }
        // The currency check is exercised: USD estimates with a USD listing give a forward P/E; a CAD listing doesn't mix them.
        assertEquals(FinancialAvailability.AVAILABLE, w.financials.screenerFundamentals("NASDAQ0", "USD").valuation.metrics["forwardPe"]?.availability)
        assertNotEquals(FinancialAvailability.AVAILABLE, w.financials.screenerFundamentals("T0.TO", "CAD").valuation.metrics["forwardPe"]?.availability)
        assertNull(w.financials.screenerFundamentals("NASDAQ0", "USD").valuation.metrics["forwardPe"]?.value?.takeIf { it == 0.0 }, "never a false zero")
        w.scope.cancel()
    }

    @Test fun fullUniverseCoverageOverADayStaysWithinTheBudget() = runBlocking {
        val w = World(universeSize = 50, perHour = 25)
        val perHour = mutableListOf<Int>(); val coverage = mutableListOf<Int>()
        for (hour in 0 until 24) {
            val before = w.up.total
            coverage += w.coverage()
            perHour += w.up.total - before
            w.clock.instant = w.clock.instant.plusSeconds(3_600)
        }
        assertEquals(listOf(25, 50, 75, 100, 125, 150), coverage.take(6), "25 companies per hour until the universe is covered")
        assertTrue(coverage.drop(6).all { it >= 125 }, "never collapses after expiry: $coverage")
        assertTrue(perHour.drop(1).all { it <= 25 * 11 + 3 }, "≤ budget × requests per company (+ universe refresh): $perHour")
        w.scope.cancel()
    }

    @Test fun datasetCacheStaysWithinItsCapacity() = runBlocking {
        val w = World(universeSize = 2, perHour = 25, capacity = 64)
        w.coverage()
        assertTrue(w.fmp.datasetCacheSize <= 64, "bounded: ${w.fmp.datasetCacheSize}")
        val roomy = World(universeSize = 2, perHour = 25)
        roomy.coverage()
        assertTrue(roomy.fmp.datasetCacheSize in 66..140, "6 companies × (10 datasets + cooldown entries + quote): ${roomy.fmp.datasetCacheSize}")
        w.scope.cancel(); roomy.scope.cancel()
    }

    @Test fun concurrentSearchesShareWarmUpWork() = runBlocking {
        val w = World(universeSize = 3, perHour = 25)
        coroutineScope { repeat(12) { launch(Dispatchers.Default) { w.service.catalog() } } }
        w.settle()
        assertEquals(9, w.up.n("quote"))
        assertEquals(9, w.up.n("income-statement"))
        w.scope.cancel()
    }

    @Test fun providerOutageDoesNotCauseRunawayRetries() = runBlocking {
        val w = World(universeSize = 2, perHour = 25)
        listOf("income-statement", "balance-sheet-statement", "cash-flow-statement", "ratios-ttm", "key-metrics-ttm", "income-statement-ttm",
            "cash-flow-statement-ttm", "analyst-estimates", "dividends", "shares-float").forEach { w.up.status[it] = 503 }
        assertEquals(0, w.coverage(), "an outage isn't presented as companies with nothing to match")
        val afterFirst = w.up.total
        repeat(10) { w.clock.instant = w.clock.instant.plusSeconds(60); w.coverage() }
        assertEquals(afterFirst, w.up.total, "no retries during the 30-minute backoff")
        w.up.status.clear(); w.clock.instant = w.clock.instant.plusSeconds(30 * 60)
        assertEquals(6, w.coverage(), "recovers after the backoff")
        w.scope.cancel()
    }
}
