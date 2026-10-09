package org.example.stocksteps.service

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.earnings.EarningsActual
import org.example.stocksteps.earnings.EarningsDataSource
import org.example.stocksteps.earnings.EarningsEvent
import org.example.stocksteps.earnings.EarningsService
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 3D: a reported quarter (earnings evidence already loaded by the earnings service) shortens that company's
 * statement lifetime to 2 h until the provider publishes the new period; nothing changes without evidence, nothing is
 * claimed before the rows exist, and failures keep their cooldowns. REAL FMP adapter on a MockEngine, fake clocks.
 */
class EarningsAwareStatementsTest {
    private val hour = 3_600_000L

    private class World(withSignals: Boolean = true) {
        val up = FmpMock()
        val ms = AtomicLong(0)
        var wall: Instant = Instant.parse("2026-10-29T22:00:00Z")
        val meter = ProviderUsageMeter()
        val signals = if (withSignals) EarningsStatementSignals({ wall }, meter = meter) else null
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }, statementSignals = signals) { LocalDate.parse("2026-11-05") }
        suspend fun newest(period: String = "quarter") = fmp.getFundamentals("AAPL", period).history.maxByOrNull { it.date.orEmpty() }?.date
        fun advance(millis: Long) { ms.addAndGet(millis); wall = wall.plusMillis(millis) }
        val quarterly get() = up.counts["income-statement?period=quarter"]?.get() ?: 0
        val annual get() = up.counts["income-statement?period=annual"]?.get() ?: 0
    }

    private fun event(quarter: Int = 4, date: String = "2026-10-29", periodEnd: String? = null, reported: Boolean = true) = EarningsEvent(
        id = "AAPL:2026-Q$quarter", symbol = "AAPL", name = "Apple", fiscalYear = 2026, fiscalQuarter = quarter, periodEnd = periodEnd, date = date,
        actual = if (reported) EarningsActual(eps = 1.6, source = "test") else null, source = "test", updatedAt = "2026-10-29T21:00:00Z")

    @Test fun aNewQuarterAppearsWithinTwoHoursOfPublication() = runBlocking {
        val w = World()
        assertEquals("2026-06-27", w.newest())
        w.signals!!.observe(listOf(event()))                                   // the report is announced (after the close)
        w.advance(hour)
        assertEquals("2026-06-27", w.newest(), "announced but not yet published: nothing is claimed")
        assertEquals(2, w.quarterly, "the report invalidated the cached statements once")
        w.up.newQuarter = "2026-09-27" to "2026-10-30 06:00:00"                // the provider publishes 1 h later
        w.advance(hour)
        assertEquals("2026-06-27", w.newest(), "still within the 2 h accelerated lifetime")
        w.advance(hour + 1_000)
        assertEquals("2026-09-27", w.newest(), "visible ≤ 2 h after publication (was up to 24 h)")
        w.advance(5 * hour); w.newest()
        assertEquals(3, w.quarterly, "the normal 24 h lifetime resumes once the period is present")
        assertTrue(w.meter.eventCount("fmp.statements.earningsRefresh") >= 1)
        assertEquals(1L, w.meter.eventCount("statements.earningsSignal"))
        // A provider correction has no reliable signal: it waits for the normal 24 h lifetime.
        w.up.correction = 5.0; w.advance(hour); w.newest()
        assertEquals(3, w.quarterly)
    }

    @Test fun withoutEvidenceStatementsKeepTheNormalLifetime() = runBlocking {
        for (w in listOf(World(withSignals = false), World().also { it.signals!!.observe(listOf(event(reported = false))) })) {
            w.newest(); w.up.newQuarter = "2026-09-27" to "2026-10-30 06:00:00"
            w.advance(3 * hour); assertEquals("2026-06-27", w.newest())
            assertEquals(1, w.quarterly, "a scheduled (unreported) event changes nothing")
            w.advance(21 * hour + 1_000); assertEquals("2026-09-27", w.newest())
        }
    }

    @Test fun delayedPublicationIsPolledAtMostEveryTwoHoursAndOnlyWithinTheWindow() = runBlocking {
        val w = World()
        w.newest(); w.signals!!.observe(listOf(event()))
        repeat(12) { w.advance(10 * 60_000L); w.newest() }                         // 2 h of busy traffic
        assertEquals(2, w.quarterly, "one refresh per 2 h however many requests arrive (no storm)")
        w.signals.observe(listOf(event()))                                     // the same report observed again
        assertEquals(2, w.quarterly, "repeat observations of the same report don't invalidate again")
        w.advance(11 * 60_000L); w.newest()
        assertEquals(3, w.quarterly, "the next poll happens when the 2 h lifetime runs out")
        w.wall = w.wall.plusSeconds(11 * 86_400L); w.advance(2 * hour + 1_000); w.newest()
        val settled = w.quarterly
        w.advance(3 * hour); w.newest()
        assertEquals(settled, w.quarterly, "after the 10-day window the normal 24 h lifetime returns")
    }

    @Test fun annualStatementsAreOnlyAcceleratedForFourthQuarterReports() = runBlocking {
        val q2 = World(); q2.newest("annual")
        q2.signals!!.observe(listOf(event(quarter = 2)))
        q2.advance(3 * hour); q2.newest("annual")
        assertEquals(1, q2.annual, "a Q2 report doesn't touch annual statements")
        val q4 = World(); q4.newest("annual")
        q4.signals!!.observe(listOf(event(quarter = 4)))
        q4.advance(hour); q4.newest("annual")
        assertEquals(2, q4.annual)
    }

    @Test fun anExactPeriodEndIsUsedWhenTheSourceGivesOne() {
        val signals = EarningsStatementSignals({ Instant.parse("2026-10-30T12:00:00Z") })
        signals.observe(listOf(event(periodEnd = "2026-09-27")))
        val report = signals.awaiting("AAPL", "quarter")!!
        assertFalse(signals.covers(report, "2026-06-27")); assertTrue(signals.covers(report, "2026-09-27"))
        assertFalse(signals.covers(report, null), "an empty response is never the new period")
        assertEquals(2 * hour, signals.statementTtl("AAPL", "quarter", "2026-06-27", 24 * hour))
        assertEquals(24 * hour, signals.statementTtl("AAPL", "quarter", "2026-09-27", 24 * hour))
        assertEquals(24 * hour, signals.statementTtl("MSFT", "quarter", "2026-06-27", 24 * hour), "other companies aren't polled")
        // Without a period end (Finnhub calendars): a row ending within 105 days before the announcement counts.
        val approx = EarningsStatementSignals({ Instant.parse("2026-10-30T12:00:00Z") }).also { it.observe(listOf(event())) }
        assertEquals(2 * hour, approx.statementTtl("AAPL", "quarter", "2026-06-27", 24 * hour))
        assertEquals(24 * hour, approx.statementTtl("AAPL", "quarter", "2026-09-27", 24 * hour))
    }

    @Test fun providerFailuresDuringAcceleratedRefreshKeepTheirCooldowns() = runBlocking {
        for (status in listOf(429, 503, 403)) {
            val w = World(); w.newest(); w.signals!!.observe(listOf(event()))
            w.up.status["income-statement"] = status
            w.advance(hour); w.newest(); w.advance(10_000); w.newest()
            assertEquals(2, w.quarterly, "$status: the failure is shared and cooled down, not retried per request")
            w.up.status.clear(); w.advance(31_000); w.newest()
            assertEquals(if (status == 403) 2 else 3, w.quarterly, "$status: 402/403 keep the 1 h cooldown; others retry after 30 s")
        }
    }

    @Test fun theEarningsServiceFeedsEvidenceFromEventsItAlreadyLoads() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
        val signals = EarningsStatementSignals({ clock.instant() })
        val up = FmpMock()
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k")
        val source = object : EarningsDataSource {
            var calls = 0
            override suspend fun calendar(from: LocalDate, to: LocalDate) = emptyList<EarningsEvent>()
            override suspend fun history(symbol: String): List<EarningsEvent> { calls++; return listOf(event()) }
            override val label = "test"
        }
        val store = InMemoryUserDataStore()
        val earnings = EarningsService(source, StockService(fmp, fmp), PriceChartService(FmpPriceHistoryProvider(up.client, "k")), store,
            EntitlementService(store, clock::millis, true), clock, sampleData = false, research = null, statementSignals = signals)
        earnings.next("AAPL"); earnings.next("AAPL")
        assertNotNull(signals.awaiting("AAPL", "quarter"))
        assertEquals(1, source.calls, "no extra earnings requests: evidence comes from the cached history")
    }
}
