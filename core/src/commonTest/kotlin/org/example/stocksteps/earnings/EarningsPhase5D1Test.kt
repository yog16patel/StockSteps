package org.example.stocksteps.earnings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Global UI Refinement Phase 5D.1: the "not reported yet" estimate sentence names only the estimates that exist (E-02), and history-chart
 * tooltips use the same currency and formatter as the history rows (E-03). Formatting only: values are never changed.
 */
class EarningsPhase5D1Test {
    private fun est(eps: Double? = null, revenue: Double? = null, currency: String? = null) =
        EarningsEstimate(eps = eps, revenue = revenue, currency = currency, source = "test")
    @Test fun bothEstimatesAreNamedWithTheirCurrencyAndScale() {
        assertEquals("Analysts expect EPS of $1.25 and revenue of $850.00M.",
            EarningsFormatter.estimateSentence(est(eps = 1.25, revenue = 850_000_000.0, currency = "USD")))
        assertEquals("Analysts expect EPS of C$1.58 and revenue of C$20.70B.",
            EarningsFormatter.estimateSentence(est(eps = 1.58, revenue = 20_700_000_000.0, currency = "CAD")))
    }

    @Test fun onlyTheAvailableEstimateIsNamed() {
        assertEquals("Analysts expect EPS of $1.25.", EarningsFormatter.estimateSentence(est(eps = 1.25, currency = "USD")))
        assertEquals("Analysts expect revenue of $850.00M.", EarningsFormatter.estimateSentence(est(revenue = 850_000_000.0, currency = "USD")))
    }

    @Test fun neitherEstimateSaysSoWithoutPlaceholders() {
        val expected = "Analyst estimates aren't available yet."
        assertEquals(expected, EarningsFormatter.estimateSentence(est(currency = "USD")))
        assertEquals(expected, EarningsFormatter.estimateSentence(null))
        assertEquals(expected, EarningsFormatter.estimateSentence(est(eps = Double.NaN, revenue = Double.POSITIVE_INFINITY)))
        for (estimate in listOf(est(eps = 1.0), est(revenue = 1e9), null)) assertFalse("—" in EarningsFormatter.estimateSentence(estimate))
    }

    @Test fun zeroAndNegativeEstimatesAreRealValues() {
        assertEquals("Analysts expect EPS of $0.00.", EarningsFormatter.estimateSentence(est(eps = 0.0, currency = "USD")))
        assertEquals("Analysts expect EPS of −$0.25 and revenue of $12.00M.",
            EarningsFormatter.estimateSentence(est(eps = -0.25, revenue = 12_000_000.0, currency = "USD")))
    }

    @Test fun revenueKeepsItsOwnScaleInsteadOfAlwaysBillions() {
        assertTrue("$850.00M" in EarningsFormatter.estimateSentence(est(revenue = 850_000_000.0, currency = "USD"))) // not "0.85B"
        assertTrue("$124.30B" in EarningsFormatter.estimateSentence(est(revenue = 124_300_000_000.0, currency = "USD")))
    }

    /** 5D.1B: a missing currency is never shown as "$"; the sentence says once that the source didn't provide one. */
    @Test fun missingCurrencyIsNotShownAsDollars() {
        assertEquals("Analysts expect EPS of 1.25. Currency not provided by the source.", EarningsFormatter.estimateSentence(est(eps = 1.25)))
        assertEquals("Analysts expect EPS of −0.25 and revenue of 850.00M. Currency not provided by the source.",
            EarningsFormatter.estimateSentence(est(eps = -0.25, revenue = 850_000_000.0)))
        assertEquals("Analyst estimates aren't available yet.", EarningsFormatter.estimateSentence(est()))
    }

    @Test fun historyTooltipsUseTheRowsCurrencyAndFormatter() {
        for (currency in listOf("USD", "CAD", null)) {
            val point = EarningsFormatter.historyPoint("Q1 25", 1.58, 1.42, currency)
            // The history row shows these exact strings for the same quarter.
            assertEquals("Q1 25 · reported ${EarningsFormatter.eps(1.58, currency)} · est. ${EarningsFormatter.eps(1.42, currency)}", point)
        }
        assertEquals("Q1 25 · reported C$1.58 · est. C$1.42", EarningsFormatter.historyPoint("Q1 25", 1.58, 1.42, "CAD"))
        assertEquals("Q1 25 · reported $1.58 · est. $1.42", EarningsFormatter.historyPoint("Q1 25", 1.58, 1.42, "USD"))
    }

    @Test fun historyTooltipsSayWhatIsMissingAndKeepValues() {
        assertEquals("Q2 25 · not reported · est. C$1.10", EarningsFormatter.historyPoint("Q2 25", null, 1.10, "CAD"))
        assertEquals("Q2 25 · reported −C$0.05 · no estimate", EarningsFormatter.historyPoint("Q2 25", -0.05, null, "CAD"))
        assertEquals("Q2 25 · reported $1.23 · est. $1.23", EarningsFormatter.historyPoint("Q2 25", 1.234, 1.226, "USD")) // display rounding only
    }
}
