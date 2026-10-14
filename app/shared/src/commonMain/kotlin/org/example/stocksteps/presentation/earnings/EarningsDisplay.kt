package org.example.stocksteps.presentation.earnings

import org.example.stocksteps.earnings.Classification
import org.example.stocksteps.theme.StockBadgeKind

/** How one estimate comparison is shown: the words, the badge style and the spoken text. */
data class EarningsClassificationDisplay(val label: String, val kind: StockBadgeKind, val spoken: String)

/**
 * Phase 5D.1 (E-01): the one presentation of an EPS / revenue [Classification], used by Earnings Results and Earnings Details on Android and
 * iOS, so the same result never looks "good" on one screen and neutral on another. Display only: the classification comes from the server's
 * exact comparison and is never recomputed here. The label always carries the meaning (colour is secondary) and only describes the result
 * against the estimate, never whether the stock is a good investment.
 */
object EarningsDisplay {
    const val BEAT = "Beat estimate"
    const val MET = "Met estimate"
    const val MISSED = "Missed estimate"
    const val UNAVAILABLE = "Comparison unavailable"

    fun classification(classification: Classification): EarningsClassificationDisplay = when (classification) {
        Classification.BEAT -> EarningsClassificationDisplay(BEAT, StockBadgeKind.POSITIVE, "Result: beat the estimate")
        Classification.MET -> EarningsClassificationDisplay(MET, StockBadgeKind.NEUTRAL, "Result: met the estimate")
        Classification.MISS -> EarningsClassificationDisplay(MISSED, StockBadgeKind.NEGATIVE, "Result: missed the estimate")
        Classification.UNAVAILABLE -> EarningsClassificationDisplay(UNAVAILABLE, StockBadgeKind.NEUTRAL, "Comparison with the estimate unavailable")
    }
}
