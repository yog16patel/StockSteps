package org.example.stocksteps.earnings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 5D.1B (owner decision 5D1A-2): AI grounding never presents an amount without a source currency as US dollars. The fact has no symbol,
 * and the context says explicitly that the currency wasn't provided. Known currencies are unchanged and add no note.
 */
class EarningsGroundingCurrencyTest {
    private fun context(currency: String?, estimateCurrency: String? = currency): EarningsGroundingContext {
        val report = EarningsReport("X:2026-Q3", "X", "X", "TSX", "CA", "Example Company", null, 2026, 3, "2026-09-30", PeriodType.QUARTER, "2026-10-06",
            "2026-10-06T21:00:00Z", currency, "1.94", EpsBasis.GAAP_DILUTED, "2.00", EpsBasis.GAAP_DILUTED, "4450000000", "4400000000", estimateCurrency,
            PeriodType.QUARTER, 6, null, null)
        return EarningsGrounding.build(EarningsResultsResponse(report, EarningsResultsCalculator.insights(report), "now"), null, null)
    }
    private fun fact(c: EarningsGroundingContext, id: String) = c.facts.single { it.id == id }.value

    @Test fun cadAndUsdKeepTheirSymbolsWithoutANote() {
        val cad = context("CAD")
        assertEquals("C$1.94", fact(cad, "eps-actual"))
        assertEquals("C$4.45B", fact(cad, "revenue-actual"))
        assertFalse(EarningsGrounding.UNKNOWN_CURRENCY_FACT in cad.unavailable)
        val usd = context("USD")
        assertEquals("$1.94", fact(usd, "eps-actual"))
        assertFalse(EarningsGrounding.UNKNOWN_CURRENCY_FACT in usd.unavailable)
    }

    @Test fun missingCurrencyIsStatedAndNeverDollars() {
        val unknown = context(null)
        assertEquals("1.94", fact(unknown, "eps-actual"))
        assertEquals("2.00", fact(unknown, "eps-estimate"))
        assertTrue(unknown.facts.none { "$" in it.value }, unknown.facts.toString())
        assertTrue(EarningsGrounding.UNKNOWN_CURRENCY_FACT in unknown.unavailable)
        // An estimate without its own currency falls back to the reporting currency (existing rule), so no note is needed then.
        assertFalse(EarningsGrounding.UNKNOWN_CURRENCY_FACT in context("CAD", null).unavailable)
    }
}
