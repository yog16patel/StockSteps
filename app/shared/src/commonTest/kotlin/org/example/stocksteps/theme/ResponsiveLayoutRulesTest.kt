package org.example.stocksteps.theme

import org.example.stocksteps.companydetail.AssessmentRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 4C.3: the shared responsive rules both Company Details implementations use. */
class ResponsiveLayoutRulesTest {
    @Test
    fun headerActionsPairOnlyWhenBothLabelsFitTheirEqualShare() {
        // iPhone 16 content width (≈ 361 pt): "Add to portfolio" (≈ 120 incl. icon) + 2 × 24 padding = 168 fits half of 361 − 8 (176.5).
        assertTrue(StockActionLayout.sideBySide(listOf(120f, 90f), 361f, 24f, 8f, accessibilityText = false))
        // iPhone SE content width (≈ 343 pt): 168 > 167.5, so the actions stack instead of wrapping "Add to / portfolio".
        assertFalse(StockActionLayout.sideBySide(listOf(120f, 90f), 343f, 24f, 8f, accessibilityText = false))
        // The longer label decides, not the sum of both.
        assertFalse(StockActionLayout.sideBySide(listOf(170f, 40f), 400f, 24f, 8f, accessibilityText = false))
        // Accessibility text sizes always stack; an unmeasured width never claims a fit.
        assertFalse(StockActionLayout.sideBySide(listOf(60f, 60f), 1000f, 24f, 8f, accessibilityText = true))
        assertFalse(StockActionLayout.sideBySide(listOf(60f, 60f), 0f, 24f, 8f, accessibilityText = false))
    }

    @Test
    fun longStatusBadgesNeverShareTheTitleLine() {
        val short = listOf("Growing", "Growing fast", "Shrinking", "High margin", "Profitable", "Thin margin", "Losing money",
            "Near History", "Above History", "Below History")
        short.forEach { assertTrue(StockBadgeLayout.mayShareTitleLine(it), it) }
        // Both Financial Health labels are long and always go below "Financial Health".
        listOf(AssessmentRules.health(2.0, 1.0).first, AssessmentRules.health(1.0, 2.0).first).forEach {
            assertFalse(StockBadgeLayout.mayShareTitleLine(it), it)
        }
    }

    @Test
    fun selectorFadesOnlyWhenMoreThanHalfAPillIsHidden() {
        // iPhone 16 default: the 7 ranges overflow by a few points of padding and ALL is fully visible, so there is no fade.
        assertFalse(StockPillLayout.fadesEdge(hiddenBeyondEdge = 6f, touchTarget = 44f))
        assertFalse(StockPillLayout.fadesEdge(hiddenBeyondEdge = 22f, touchTarget = 44f))
        // Android 360 dp: a whole 48 dp pill (ALL) is beyond the edge, so the edge fades as a "more options" cue.
        assertTrue(StockPillLayout.fadesEdge(hiddenBeyondEdge = 50f, touchTarget = 48f))
        // At the start of the row nothing is hidden before it.
        assertFalse(StockPillLayout.fadesEdge(hiddenBeyondEdge = 0f, touchTarget = 48f))
    }

    @Test
    fun quoteRowsStackValuesOnlyWhenTheyWouldCrowdTheName() {
        // 360 dp at the default size: "+$3.04 (+0.91%)" ≈ 105 dp of ≈ 236 dp beside the logo, so values stay on the right.
        assertFalse(StockQuoteRowLayout.stacksValues(valuesWidth = 105f, availableWidth = 236f, accessibilityText = false))
        // Larger text or a narrower row: values would take more than half, so they move under the company.
        assertTrue(StockQuoteRowLayout.stacksValues(valuesWidth = 140f, availableWidth = 236f, accessibilityText = false))
        assertTrue(StockQuoteRowLayout.stacksValues(valuesWidth = 40f, availableWidth = 400f, accessibilityText = true))
        // Unmeasured width: keep the default side-by-side layout.
        assertFalse(StockQuoteRowLayout.stacksValues(valuesWidth = 140f, availableWidth = 0f, accessibilityText = false))
    }
}
