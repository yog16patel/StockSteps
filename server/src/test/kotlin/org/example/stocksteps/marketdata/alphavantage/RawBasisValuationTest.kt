package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.service.QuarterlyEarnings
import org.example.stocksteps.service.ValuationCalculations
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 9B.7: P/E history on as-traded closes (Alpha Vantage TIME_SERIES_DAILY). A 4-for-1 split after Q4 2025 shows as a share-count jump.
 * With split-adjusted closes the original method divides earlier EPS by 4; with raw closes it must not, and months whose window spans the
 * split are left out (never mixed bases).
 */
class RawBasisValuationTest {
    private val quarters = listOf(QuarterlyEarnings("2024-12-31", "2025-01-30", 1.0, 100.0, "USD"),
        QuarterlyEarnings("2025-03-31", "2025-04-30", 1.0, 100.0, "USD"), QuarterlyEarnings("2025-06-30", "2025-07-30", 1.0, 100.0, "USD"),
        QuarterlyEarnings("2025-09-30", "2025-10-30", 1.0, 100.0, "USD"), QuarterlyEarnings("2025-12-31", "2026-01-30", 1.0, 100.0, "USD"),
        // Split in February 2026: EPS on the new basis, four times the shares.
        QuarterlyEarnings("2026-03-31", "2026-04-30", 0.25, 400.0, "USD"), QuarterlyEarnings("2026-06-30", "2026-07-30", 0.25, 400.0, "USD"),
        QuarterlyEarnings("2026-09-30", "2026-10-15", 0.25, 400.0, "USD"))
    /** As traded: 80 before the split, 20 after. */
    private val raw = generateSequence(LocalDate.parse("2025-01-02")) { it.plusDays(1) }.takeWhile { it <= LocalDate.parse("2026-10-16") }
        .filter { it.dayOfWeek.value <= 5 }.map { PricePoint(it.toString(), if (it < LocalDate.parse("2026-02-15")) 80.0 else 20.0) }.toList()

    @Test fun rawClosesNeverGetSplitFactorsAndSpanningMonthsAreOmitted() {
        val s = ValuationCalculations.monthlyPe(raw, quarters, LocalDate.parse("2026-10-16"), closesSplitAdjusted = false)
        assertFalse(s.splitAdjusted)
        val byMonth = s.observations.associate { it.date.take(7) to it.pe }
        // Dec 2025: four pre-split quarters (EPS 4.0) at 80 → 20, no split inside the window.
        assertEquals(20.0, byMonth["2025-12"])
        // The share counts only date the split to (Dec 31, Mar 31]: from January on the window or price may span it → omitted, never mixed.
        for (m in listOf("2026-01", "2026-02", "2026-04", "2026-07", "2026-09")) assertNull(byMonth[m], "month $m spans the split")
        // Oct 2026: still spans (Q4 2025 pre-split is inside the window) → current P/E unavailable.
        assertNull(s.currentPe)
    }

    @Test fun theOriginalMethodIsUnchangedForAdjustedCloses() {
        val adjusted = raw.map { PricePoint(it.time, if (it.time < "2026-02-15") it.close / 4 else it.close) }
        val s = ValuationCalculations.monthlyPe(adjusted, quarters, LocalDate.parse("2026-10-16"))
        assertTrue(s.splitAdjusted)
        assertEquals(20.0, s.currentPe, "20 / (0.25 × 3 + 1.0 / 4)")
    }

    @Test fun withoutASplitRawAndAdjustedAgree() {
        val noSplit = quarters.take(5)
        val closes = raw.filter { it.time < "2026-02-01" }
        assertEquals(ValuationCalculations.monthlyPe(closes, noSplit, LocalDate.parse("2026-01-31")).observations,
            ValuationCalculations.monthlyPe(closes, noSplit, LocalDate.parse("2026-01-31"), closesSplitAdjusted = false).observations)
    }
}
