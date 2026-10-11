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
}
