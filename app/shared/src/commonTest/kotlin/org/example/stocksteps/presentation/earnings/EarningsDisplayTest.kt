package org.example.stocksteps.presentation.earnings

import org.example.stocksteps.earnings.Classification
import org.example.stocksteps.theme.StockBadgeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 5D.1 (E-01): one classification presentation for Earnings Results and Details on both platforms. */
class EarningsDisplayTest {
    @Test fun eachClassificationHasWordsAndASemanticStyle() {
        assertEquals(EarningsClassificationDisplay("Beat estimate", StockBadgeKind.POSITIVE, "Result: beat the estimate"), EarningsDisplay.classification(Classification.BEAT))
        assertEquals(EarningsClassificationDisplay("Met estimate", StockBadgeKind.NEUTRAL, "Result: met the estimate"), EarningsDisplay.classification(Classification.MET))
        assertEquals(EarningsClassificationDisplay("Missed estimate", StockBadgeKind.NEGATIVE, "Result: missed the estimate"), EarningsDisplay.classification(Classification.MISS))
        assertEquals(StockBadgeKind.NEUTRAL, EarningsDisplay.classification(Classification.UNAVAILABLE).kind)
        assertEquals("Comparison unavailable", EarningsDisplay.classification(Classification.UNAVAILABLE).label)
    }

    @Test fun labelsDescribeTheEstimateNotTheInvestment() {
        for (c in Classification.entries) {
            val shown = EarningsDisplay.classification(c)
            assertTrue("estimate" in shown.label.lowercase() || c == Classification.UNAVAILABLE, "$c")
            for (word in listOf("good", "bad", "buy", "sell", "strong", "weak", "opportunity")) assertFalse(word in shown.label.lowercase() || word in shown.spoken.lowercase(), "$c: $word")
        }
    }

    /** The mapping only reads the classification it is given; it never recomputes one (same input → same output, all four distinct). */
    @Test fun sameDisplayForTheSameClassification() {
        assertEquals(4, Classification.entries.map { EarningsDisplay.classification(it).label }.toSet().size)
        Classification.entries.forEach { assertEquals(EarningsDisplay.classification(it), EarningsDisplay.classification(it)) }
    }
}
