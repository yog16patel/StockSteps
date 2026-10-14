package org.example.stocksteps.earnings

import org.example.stocksteps.model.EarningsDateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 5D.1B (owner decisions 5D1A-1 and 5D1A-2): Insights use the result's own currency, and a currency the source didn't provide is never
 * shown as "$": the number stands alone and one caption per card / section says the currency is missing. Values are only formatted.
 */
class EarningsPhase5D1BTest {
    private fun est(eps: Double?, currency: String?) = EarningsEstimate(eps, EpsBasis.GAAP_DILUTED, 2.0e9, currency, source = "test")
    private fun act(eps: Double?, currency: String?) = EarningsActual(eps, EpsBasis.GAAP_DILUTED, 2.1e9, currency, source = "test")
    private fun event(eps: Double?, estimate: Double?, currency: String?) = EarningsEvent("T:2026-Q2", "T", "Test", fiscalYear = 2026, fiscalQuarter = 2,
        date = "2026-07-22", dateStatus = EarningsDateStatus.CONFIRMED, estimate = est(estimate, currency), actual = act(eps, currency), source = "test",
        updatedAt = "2026-10-07T00:00:00Z")
    private fun surpriseText(currency: String?, eps: Double = 1.94, estimate: Double = 2.00) =
        EarningsInsightEngine.generate(event(eps, estimate, currency), emptyList(), "now").single { it.id.startsWith("eps-surprise") }.explanation

    @Test fun formatterKeepsKnownCurrenciesAndNeverInventsOne() {
        assertEquals("$1.94", EarningsFormatter.eps(1.94, "USD"))
        assertEquals("C$1.94", EarningsFormatter.eps(1.94, "CAD"))
        assertEquals("EUR 1.94", EarningsFormatter.eps(1.94, "EUR"))
        assertEquals("1.94", EarningsFormatter.eps(1.94, null))
        assertEquals("−1.94", EarningsFormatter.eps(-1.94, null))
        assertEquals("0.00", EarningsFormatter.eps(0.0, null))
        assertEquals("4.45B", EarningsFormatter.revenue(4.45e9, null))
        assertEquals("C$4.45B", EarningsFormatter.revenue(4.45e9, "CAD"))
        assertEquals("—", EarningsFormatter.eps(null, "CAD")) // a missing value stays missing
    }

    @Test fun currencyNoteOnlyWhenACurrencyIsMissing() {
        assertNull(EarningsFormatter.currencyNote("USD", "CAD"))
        assertEquals(EarningsFormatter.UNKNOWN_CURRENCY, EarningsFormatter.currencyNote("CAD", null))
        assertNull(EarningsFormatter.currencyNote())
    }

    @Test fun insightsUseTheResultsOwnCurrency() {
        assertEquals("EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted).", surpriseText("CAD"))
        assertEquals("EPS was $1.94 against an estimate of $2.00 (GAAP diluted).", surpriseText("USD"))
        assertEquals("EPS was 1.94 against an estimate of 2.00 (GAAP diluted). Currency not provided by the source.", surpriseText(null))
        assertEquals("EPS was −C$0.05 against an estimate of −C$0.10 (GAAP diluted).", surpriseText("CAD", -0.05, -0.10))
    }

    @Test fun noDoubledOrSubstitutedSymbols() {
        for (currency in listOf("USD", "CAD", null)) {
            val text = surpriseText(currency) + EarningsFormatter.historyPoint("Q2 26", 1.94, 2.0, currency) + EarningsFormatter.estimateSentence(est(1.25, currency))
            for (bad in listOf("\$\$", "C\$C\$", "C\$\$", "\$C\$", "—")) assertFalse(bad in text, "$currency: $bad in $text")
            if (currency == null) assertFalse("$" in text, text) else assertTrue((if (currency == "CAD") "C$" else "$") in text)
        }
    }

    @Test fun insightClassificationAndTitlesUnchanged() {
        val insight = EarningsInsightEngine.generate(event(1.94, 2.00, "CAD"), emptyList(), "now").single { it.id.startsWith("eps-surprise") }
        assertEquals("Reported EPS fell short of the consensus estimate by 3.0%.", insight.title)
        assertEquals(Classification.MISS, EarningsCalculator.eps(est(2.00, "CAD"), act(1.94, "CAD")).classification)
    }

    @Test fun missingActualOrEstimateHasNoSurpriseInsight() {
        assertTrue(EarningsInsightEngine.generate(event(null, 2.0, "CAD"), emptyList(), "now").none { it.id.startsWith("eps-surprise") })
        assertTrue(EarningsInsightEngine.generate(event(1.94, null, "CAD"), emptyList(), "now").none { it.id.startsWith("eps-surprise") })
    }
}
