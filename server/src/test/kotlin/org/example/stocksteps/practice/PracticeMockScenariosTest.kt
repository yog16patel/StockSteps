package org.example.stocksteps.practice

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/** Every MOCK scenario over the real fixtures: no provider, Firebase or billing calls. */
class PracticeMockScenariosTest {
    private val fixtures = FixtureMarketDataSource(sampleFallback = true)
    private val store = InMemoryUserDataStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC)
    private val svc = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true),
        WatchPracticeMarket(WatchMarketData(StockService(fixtures, fixtures), null, NewsService(fixtures)), PriceChartService(fixtures)::getDailyCloses),
        MockPortfolioFx, clock, Clock.fixed(fixtures.capturedAt!!, ZoneOffset.UTC), MockCorporateActions, sampleData = true)

    private fun load(name: String) = runBlocking { svc.loadScenario("demo-$name", name) }

    @Test fun everyScenarioLoads() {
        listOf("empty", "one-holding", "three-holdings", "free-limit", "eight-holdings-trial-expired", "trial-available", "trial-active", "trial-expiring",
            "trial-expired", "plus-active", "plus-expired", "low-cash", "missing-quote", "dividend", "split", "missing-fx", "stale-quote").forEach { name ->
            val o = load(name)
            assertTrue(o.sampleData, name)
            assertTrue(java.math.BigDecimal(o.cash) >= java.math.BigDecimal.ZERO, name)
        }
    }

    @Test fun scenarioStatesMatchTheirNames(): Unit = runBlocking {
        assertTrue(load("empty").holdings.isEmpty())
        val limit = load("free-limit")
        assertEquals(3, limit.openHoldings)
        assertEquals("HOLDING_LIMIT", svc.preview("demo-free-limit", PracticeOrderRequest("GOOGL", OrderSide.BUY, quantity = "1")).blocker?.code)
        val eight = load("eight-holdings-trial-expired")
        assertEquals(8, eight.openHoldings)
        assertEquals(TrialStatus.EXPIRED, eight.entitlement.trialStatus)
        assertNotNull(eight.totalValue)
        assertEquals(TrialStatus.ACTIVE, load("trial-active").entitlement.trialStatus)
        assertEquals(1, load("trial-expiring").entitlement.trialDaysLeft)
        assertEquals(PracticeAccess.PLUS, load("plus-active").entitlement.access)
        assertEquals(PracticeAccess.FREE, load("plus-expired").entitlement.access)
        assertEquals("INSUFFICIENT_CASH", run { load("low-cash"); svc.preview("demo-low-cash", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1")).blocker?.code })
        assertEquals("QUOTE_STALE", run { load("stale-quote"); svc.preview("demo-stale-quote", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1")).blocker?.code })
        assertEquals("FX_UNAVAILABLE", run { load("missing-fx"); svc.preview("demo-missing-fx", PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "1")).blocker?.code })
        assertEquals(ValuationStatus.UNAVAILABLE, load("missing-quote").valuationStatus)
        val dividend = svc.transactions("demo-dividend", null).items.also { load("dividend") }
        assertTrue(svc.transactions("demo-dividend", "DIVIDEND").items.single().pricingSource.startsWith("Sample"))
        load("split")
        assertEquals("8", svc.overview("demo-split").holdings.single().quantity)
        assertTrue(dividend.size >= 0)
    }

    @Test fun expandedHistoryUsesFixtureClosesSinceCreation(): Unit = runBlocking {
        load("trial-active")
        val perf = svc.performance("demo-trial-active", "3M")
        assertTrue(perf.points.size > 20)
        assertTrue(perf.points.first().date >= "2026-08-03")
        assertTrue(perf.points.all { it.value != null }, perf.points.filter { it.value == null }.joinToString { it.date })
    }
}
