package org.example.stocksteps.marketdata

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.service.EodSessions
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B: the replaceable daily-price boundary — EOD labelling, quote freshness and adjustment-basis rules (fixed clocks, fakes). */
class PriceBoundaryTest {
    // Friday 2026-10-16 22:00 UTC = 18:00 ET (after the close + 60 min publication delay).
    private val now = Instant.parse("2026-10-16T22:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessions = EodSessions(clock)
    private class Fake(var points: List<PricePoint>) : PriceHistoryProvider {
        var calls = 0
        override suspend fun getIntradaySparkline(symbol: String) = Sparkline(symbol, emptyList())
        override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
        override suspend fun getDailyCloses(symbol: String): List<PricePoint> { calls++; return points }
    }

    @Test fun fmpSeriesCarriesBasisCurrencyZoneAndSourceAndDropsInvalidRows() = runBlocking {
        val fake = Fake(listOf(PricePoint("2026-10-14", 10.0), PricePoint("2026-10-15", 0.0), PricePoint("2026-10-15", 11.0),
            PricePoint("2026-10-16", Double.NaN), PricePoint("bad", 5.0), PricePoint("2026-10-16", 12.5)))
        val s = assertNotNull(FmpDailyPriceSource(fake, { "USD" }, clock).daily("AAPL")).value
        assertEquals(AdjustmentBasis.UNVERIFIED, s.basis); assertEquals("USD", s.currency); assertEquals("America/New_York", s.timeZone!!.id)
        assertEquals("fmp", s.provenance.provider); assertEquals("2026-10-16", s.provenance.asOf)
        assertEquals(listOf("2026-10-14" to "10.0", "2026-10-15" to "11.0", "2026-10-16" to "12.5"), s.closes.map { it.date.toString() to it.close.toPlainString() })
        assertNull(FmpDailyPriceSource(Fake(listOf(PricePoint("2026-10-16", -1.0))), { "USD" }, clock).daily("AAPL"), "no valid close → no series, never 0")
    }

    @Test fun closesAreEndOfDayOrStaleNeverLive() = runBlocking {
        val series = assertNotNull(FmpDailyPriceSource(Fake(listOf(PricePoint("2026-10-15", 11.0), PricePoint("2026-10-16", 12.0))), { "USD" }, clock).daily("AAPL"))
        val latest = assertNotNull(PriceSeriesRules.latest(series, sessions, now))
        assertEquals(PriceDelay.END_OF_DAY, latest.delay); assertEquals(LocalDate.parse("2026-10-16"), latest.date)
        assertEquals(PriceDelay.STALE, PriceSeriesRules.closeDelay("AAPL", LocalDate.parse("2026-10-15"), sessions, now))
        // Saturday: Friday's close is still the expected session (weekend), Thursday's is stale.
        val saturday = Instant.parse("2026-10-17T15:00:00Z")
        assertEquals(PriceDelay.END_OF_DAY, PriceSeriesRules.closeDelay("AAPL", LocalDate.parse("2026-10-16"), sessions, saturday))
        assertEquals(PriceDelay.UNKNOWN, PriceSeriesRules.closeDelay("VOD.L", LocalDate.parse("2026-10-16"), sessions, now))
        assertTrue(PriceDelay.entries.none { PriceSeriesRules.closeDelay("AAPL", LocalDate.parse("2026-10-16"), sessions, now) == PriceDelay.REAL_TIME })
    }

    @Test fun quoteFreshnessIsDelayedStaleOrEndOfDay() {
        val open = Instant.parse("2026-10-16T17:00:00Z")   // 13:00 ET, session open
        assertEquals(PriceDelay.DELAYED, PriceSeriesRules.quoteDelay("AAPL", open.minusSeconds(15 * 60), sessions, open))
        assertEquals(PriceDelay.STALE, PriceSeriesRules.quoteDelay("AAPL", open.minusSeconds(3 * 3600), sessions, open))
        assertEquals(PriceDelay.END_OF_DAY, PriceSeriesRules.quoteDelay("AAPL", Instant.parse("2026-10-16T20:00:00Z"), sessions, Instant.parse("2026-10-17T02:00:00Z")))
        assertEquals(PriceDelay.STALE, PriceSeriesRules.quoteDelay("AAPL", Instant.parse("2026-10-15T20:00:00Z"), sessions, Instant.parse("2026-10-17T02:00:00Z")))
        assertEquals(PriceDelay.UNKNOWN, PriceSeriesRules.quoteDelay("AAPL", null, sessions, open))
        // Phase 9B.8: an end-of-day quote stamped at the close is END_OF_DAY during after-hours and the next session too.
        assertEquals(PriceDelay.END_OF_DAY, PriceSeriesRules.quoteDelay("AAPL", Instant.parse("2026-10-16T20:00:00Z"), sessions, Instant.parse("2026-10-16T22:00:00Z")))
        assertEquals(PriceDelay.END_OF_DAY, PriceSeriesRules.quoteDelay("AAPL", Instant.parse("2026-10-15T20:00:00Z"), sessions, open), "Thursday's close during Friday's session")
        assertEquals(PriceDelay.UNKNOWN, PriceSeriesRules.quoteDelay("AAPL", open.plusSeconds(3600), sessions, open), "a future timestamp isn't trusted")
    }

    @Test fun adjustedHistoryIsNeverMixedWithAnotherBasis() {
        val prov = Provenance("fmp", Dataset.DAILY_PRICES, now, "2026-10-16")
        val history = PriceSeries("AAPL", "US", null, "USD", AdjustmentBasis.UNVERIFIED, listOf(DailyClose(LocalDate.parse("2026-10-16"), BigDecimal("12"))), prov)
        fun current(basis: AdjustmentBasis, provider: String = "fmp", currency: String? = "USD") =
            PriceObservation("AAPL", BigDecimal("12"), currency, LocalDate.parse("2026-10-16"), basis, PriceDelay.END_OF_DAY, prov.copy(provider = provider))
        assertTrue(PriceSeriesRules.compatible(history, current(AdjustmentBasis.UNVERIFIED)), "one consistent series")
        assertFalse(PriceSeriesRules.compatible(history, current(AdjustmentBasis.UNVERIFIED, provider = "alphavantage")), "two unknown bases from different vendors")
        assertFalse(PriceSeriesRules.compatible(history, current(AdjustmentBasis.SPLIT_ADJUSTED)))
        assertFalse(PriceSeriesRules.compatible(history.copy(basis = AdjustmentBasis.SPLIT_AND_DIVIDEND_ADJUSTED), current(AdjustmentBasis.UNADJUSTED)))
        assertFalse(PriceSeriesRules.compatible(history, current(AdjustmentBasis.UNVERIFIED, currency = "CAD")))
        assertFalse(PriceSeriesRules.compatible(history.copy(currency = null), current(AdjustmentBasis.UNVERIFIED, currency = null)), "unknown currency never combines")
        assertFalse(current(AdjustmentBasis.UNVERIFIED).copy(delay = PriceDelay.STALE).usableForRatios)
        assertFalse(current(AdjustmentBasis.UNVERIFIED).copy(value = BigDecimal.ZERO).usableForRatios)
    }
}
