package org.example.stocksteps.market

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.*
import org.example.stocksteps.practice.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.service.UsMarketCalendar
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/**
 * Ubuntu verification 2026-10-13 §3.1: the MOCK market clock advances from the fixture capture, so captured quotes must follow it —
 * otherwise every practice trade failed with QUOTE_STALE and price alerts stopped ~16 h after the server started.
 */
class MockClockFreshnessTest {
    private val capture = FixtureMarketDataSource().capturedAt!!   // 2026-10-07T21:15Z, Wed 17:15 New York (after hours)
    private val rules = AlertRules()
    private val symbols = listOf("AAPL", "MSFT", "KO", "COST")       // captured, captured, captured + keepMissing, catalog sample

    /** Simulated server uptimes: each moment is the mock market clock after that long. */
    private val uptimes = mapOf(
        "start" to Duration.ZERO,
        "next open (Thu 10:00 ET)" to Duration.ofMinutes(16 * 60 + 45),
        "24 h" to Duration.ofHours(24),
        "weekend (Sat)" to Duration.ofDays(3),
        "7 days" to Duration.ofDays(7),
        "30 days" to Duration.ofDays(30),
        "Thanksgiving (holiday)" to Duration.between(capture, Instant.parse("2026-11-26T17:00:00Z")),
        "Black Friday 15:00 ET (after early close)" to Duration.between(capture, Instant.parse("2026-11-27T20:00:00Z"))
    )

    private fun fixturesAt(now: Instant) = FixtureMarketDataSource(sampleFallback = true, marketNow = { now })

    @Test fun capturedQuotesWentStaleWithoutTheClockFollowingThem(): Unit = runBlocking {
        // The regression: the stored capture-day timestamp against a mock clock one day later.
        val now = capture + Duration.ofHours(24)
        assertNull(rules.freshQuote(FixtureMarketDataSource(sampleFallback = true).getQuote("AAPL"), now))
        assertNotNull(rules.freshQuote(fixturesAt(now).getQuote("AAPL"), now))
    }

    @Test fun quotesStayFreshAndDeterministicAtEveryUptime(): Unit = runBlocking {
        val calendar = UsMarketCalendar()
        val unshifted = FixtureMarketDataSource(sampleFallback = true)
        for ((label, uptime) in uptimes) {
            val now = capture + uptime
            val fixtures = fixturesAt(now)
            for (symbol in symbols) {
                val quote = assertNotNull(fixtures.getQuote(symbol), "$label $symbol")
                assertNotNull(rules.freshQuote(quote, now), "$label $symbol is fresh")
                assertEquals(unshifted.getQuote(symbol)?.price, quote.price, "$label $symbol keeps its deterministic price")
                assertEquals(quote, fixtures.getQuote(symbol), "$label $symbol is stable at one instant")
                assertTrue(quote.timestamp!! <= now.epochSecond, "$label $symbol is never stamped in the future")
                if (calendar.session(now).status == MarketSessionStatus.OPEN) assertEquals(now.epochSecond, quote.timestamp, "$label open session")
            }
        }
        // Closed: the latest session's close; never earlier than the capture.
        val saturday = fixturesAt(capture + Duration.ofDays(3)).getQuote("AAPL")!!
        assertEquals(Instant.parse("2026-10-09T20:00:00Z").epochSecond, saturday.timestamp, "Friday 16:00 ET")
        assertEquals(Instant.parse("2026-11-27T18:00:00Z").epochSecond, fixturesAt(capture + uptimes.getValue("Black Friday 15:00 ET (after early close)")).getQuote("AAPL")!!.timestamp, "13:00 ET early close")
        assertEquals(Instant.parse("2026-10-07T20:00:00Z").epochSecond, fixturesAt(capture).getQuote("AAPL")!!.timestamp, "capture day unchanged")
    }

    @Test fun generatedChartsEndOnTheSimulatedSession(): Unit = runBlocking {
        val now = capture + Duration.ofDays(7)  // Wed Oct 14, after hours
        val fixtures = fixturesAt(now)
        assertEquals("2026-10-14", fixtures.getDailyCloses("COST").last().time)
        assertEquals(fixtures.getQuote("COST")!!.price, fixtures.getDailyCloses("COST").last().close)
    }

    private fun practice(fixtures: FixtureMarketDataSource, now: Instant, store: InMemoryUserDataStore = InMemoryUserDataStore()): PracticeService {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        return PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true),
            WatchPracticeMarket(WatchMarketData(StockService(fixtures, fixtures), null, NewsService(fixtures)), PriceChartService(fixtures)::getDailyCloses),
            MockPortfolioFx, clock, clock, MockCorporateActions, sampleData = true)
    }

    @Test fun practiceTradesExecuteAtEveryUptime(): Unit = runBlocking {
        for ((label, uptime) in uptimes) {
            val now = capture + uptime
            val svc = practice(fixturesAt(now), now)
            for ((i, symbol) in listOf("AAPL", "COST").withIndex()) {
                val preview = svc.preview("u1", PracticeOrderRequest(symbol, OrderSide.BUY, quantity = "1"))
                assertNull(preview.blocker, "$label $symbol: ${preview.blocker}")
                svc.execute("u1", PracticeOrderRequest(symbol, OrderSide.BUY, quantity = "1", idempotencyKey = "order-$i-${uptime.toMinutes()}", reviewedPrice = preview.price))
            }
            assertEquals(2, svc.overview("u1").holdings.size, label)
        }
    }

    @Test fun staleQuoteScenarioStillBlocksUnderTheRollingClock(): Unit = runBlocking {
        val now = capture + Duration.ofDays(7)
        val svc = practice(fixturesAt(now), now)
        svc.loadScenario("demo", "stale-quote")
        assertEquals("QUOTE_STALE", svc.preview("demo", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1")).blocker?.code)
    }

    @Test fun priceAlertsEvaluateAtEveryUptime(): Unit = runBlocking {
        for ((label, uptime) in uptimes) {
            val now = capture + uptime
            val fixtures = fixturesAt(now)
            val price = fixtures.getQuote("AAPL")!!.price!!
            val store = InMemoryUserDataStore()
            store.updateAlerts("u1") {
                listOf(AlertRule("r1", InstrumentRef("AAPL", "Apple Inc.", currency = "USD"), AlertType.PRICE_ABOVE, price - 1, "USD", null, null,
                    RepeatPolicy.ONCE, AlertStatus.ACTIVE, true, 0, 0)) to Unit
            }
            val report = AlertEvaluator(store, WatchMarketData(StockService(fixtures, fixtures), null, NewsService(fixtures)), SimulatedPushSender(),
                clock = Clock.fixed(now, ZoneOffset.UTC)).run()
            assertEquals(0, report.stale, label)
            assertEquals(1, report.triggered, label)
        }
    }

    @Test fun mockServerWiresQuotesToItsMarketClock(): Unit = runBlocking {
        val sources = org.example.stocksteps.mockDataSources()
        val now = sources.marketClock.instant()
        assertNotNull(rules.freshQuote(sources.stockProvider.getQuote("AAPL"), now))
        assertNotNull(rules.freshQuote(sources.quoteProvider.getQuote("COST"), now))
    }
}
