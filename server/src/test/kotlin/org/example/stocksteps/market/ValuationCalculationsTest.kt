package org.example.stocksteps.market

import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.service.QuarterlyEarnings
import org.example.stocksteps.service.ValuationCalculations
import java.time.LocalDate
import kotlin.test.*

class ValuationCalculationsTest {
    private fun q(end: String, filed: String, eps: Double?, shares: Double = 100.0, currency: String = "USD") =
        QuarterlyEarnings(end, filed, eps, shares, currency)

    /** Four quarters of 2025 (EPS 1.0 each → TTM 4.0), filed a month after each quarter. */
    private val year = listOf(
        q("2025-03-31", "2025-04-30", 1.0), q("2025-06-30", "2025-07-31", 1.0),
        q("2025-09-30", "2025-10-31", 1.0), q("2025-12-31", "2026-02-28", 1.0)
    )

    private fun closes(vararg points: Pair<String, Double>) = points.map { PricePoint(it.first, it.second) }

    @Test fun monthlyObservationsUseMonthEndCloseOverTtmEps() {
        val series = ValuationCalculations.monthlyPe(
            closes("2026-03-02" to 90.0, "2026-03-31" to 100.0, "2026-04-15" to 110.0, "2026-04-30" to 120.0),
            year, LocalDate.parse("2026-05-01")
        )
        assertEquals(listOf("2026-03-31" to 25.0, "2026-04-30" to 30.0), series.observations.map { it.date to it.pe }, "one per month, last trading close")
        assertEquals(30.0, series.currentPe)
        assertEquals(4.0, series.ttmEps)
    }

    @Test fun noLookAheadBeforeTheFourthQuarterIsFiled() {
        // Q4 2025 is only public on 2026-02-28: January 2026 has just three filed quarters.
        val series = ValuationCalculations.monthlyPe(closes("2026-01-30" to 100.0, "2026-02-27" to 100.0, "2026-03-31" to 100.0), year, LocalDate.parse("2026-04-01"))
        assertEquals(listOf("2026-03-31"), series.observations.map { it.date }, "months before the filing date have no TTM EPS")
        assertNull(ValuationCalculations.ttmEpsOn("2026-02-27", year, ValuationCalculations.splitFactors(year)))
    }

    @Test fun splitsRestateEarlierEpsToTheCurrentShareBasis() {
        // 10:1 split between Q2 and Q3: earlier quarters reported 10x EPS on 1/10 the shares.
        val quarters = listOf(
            q("2025-03-31", "2025-04-30", 10.0, shares = 10.0), q("2025-06-30", "2025-07-31", 10.0, shares = 10.0),
            q("2025-09-30", "2025-10-31", 1.0, shares = 100.0), q("2025-12-31", "2026-01-31", 1.0, shares = 100.0)
        )
        val factors = ValuationCalculations.splitFactors(quarters)
        assertEquals(0.1, factors.getValue("2025-03-31"), 1e-9)
        assertEquals(1.0, factors.getValue("2025-12-31"), 1e-9)
        assertEquals(4.0, ValuationCalculations.ttmEpsOn("2026-02-15", quarters, factors)!!, 1e-9, "a split is not an economic change")
        // Ordinary buybacks (a few % fewer shares) are not mistaken for splits.
        assertTrue(ValuationCalculations.splitFactors(year.map { it.copy(shares = 100.0 - it.periodEnd.substring(5, 7).toDouble()) }).values.all { it == 1.0 })
    }

    @Test fun zeroOrNegativeEarningsLeaveGapsNotNegativeMultiples() {
        val losses = year.mapIndexed { i, quarter -> quarter.copy(epsDiluted = if (i == 0) -4.0 else 1.0) } // TTM = -1
        val negative = ValuationCalculations.monthlyPe(closes("2026-03-31" to 100.0), losses, LocalDate.parse("2026-04-01"))
        assertTrue(negative.observations.isEmpty())
        assertNull(negative.currentPe)
        assertEquals(-1.0, negative.ttmEps, "kept so the app can explain negative earnings")
        val zero = ValuationCalculations.monthlyPe(closes("2026-03-31" to 100.0), year.mapIndexed { i, q -> q.copy(epsDiluted = if (i == 0) -3.0 else 1.0) }, LocalDate.parse("2026-04-01"))
        assertTrue(zero.observations.isEmpty())
        assertNull(zero.currentPe)
    }

    @Test fun missingQuartersAndMixedCurrenciesAreNotBridged() {
        val gap = year.filterIndexed { i, _ -> i != 1 } + q("2024-12-31", "2025-01-31", 1.0)
        assertNull(ValuationCalculations.ttmEpsOn("2026-03-31", gap, ValuationCalculations.splitFactors(gap)), "Q2 missing: no 4 consecutive quarters")
        val mixed = year.mapIndexed { i, quarter -> if (i == 0) quarter.copy(currency = "CAD") else quarter }
        assertNull(ValuationCalculations.ttmEpsOn("2026-03-31", mixed, ValuationCalculations.splitFactors(mixed)))
    }

    @Test fun futureClosesAreIgnored() {
        val series = ValuationCalculations.monthlyPe(closes("2026-03-31" to 100.0, "2026-05-29" to 200.0), year, LocalDate.parse("2026-04-15"))
        assertEquals(listOf("2026-03-31"), series.observations.map { it.date })
    }
}
