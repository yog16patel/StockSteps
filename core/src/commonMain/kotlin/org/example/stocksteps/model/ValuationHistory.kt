package org.example.stocksteps.model

import kotlinx.serialization.Serializable

/** How the historical P/E series was built; shown to users so averages are never overstated. */
@Serializable
enum class ValuationMethod {
    /**
     * Month-end split-adjusted close ÷ trailing-twelve-month diluted EPS, using only quarters whose
     * filings were public by that month (no look-ahead). Months with zero/negative TTM EPS are gaps.
     */
    MONTHLY_TTM,
    /** Provider-reported fiscal-year P/E ratios (one observation per fiscal year). */
    ANNUAL_REPORTED,
    /** No reliable historical series; only the current P/E (if any) is available. */
    UNAVAILABLE
}

/** One valid historical P/E observation (always positive; invalid periods are omitted). */
@Serializable
data class ValuationObservation(val date: String, val pe: Double)

/**
 * Normalized P/E history for the Valuation screen, identical in MOCK and REAL. Observations are
 * oldest first; ranges (1Y/3Y/5Y/10Y), averages and percentiles are computed by the apps from
 * these observations, so switching ranges never needs another request.
 */
@Serializable
data class ValuationHistory(
    val symbol: String,
    val method: ValuationMethod,
    val observations: List<ValuationObservation> = emptyList(),
    /** Current P/E on the same definition as [observations] (or the provider's TTM P/E). */
    val currentPe: Double? = null,
    /** AVAILABLE, NON_POSITIVE_DENOMINATOR (zero/negative earnings) or MISSING. */
    val currentPeAvailability: FinancialAvailability = FinancialAvailability.MISSING,
    /** Trailing-twelve-month diluted EPS behind [currentPe], in [epsCurrency]. */
    val ttmEps: Double? = null,
    val epsCurrency: String? = null,
    /** True when earlier quarters' EPS were restated for stock splits detected in share counts. */
    val splitAdjusted: Boolean = false,
    val asOf: String? = null
)
