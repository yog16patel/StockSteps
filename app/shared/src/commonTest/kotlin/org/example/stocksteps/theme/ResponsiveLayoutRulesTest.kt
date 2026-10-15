package org.example.stocksteps.theme

import org.example.stocksteps.companydetail.AssessmentRules
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun discoverCardsUseTwoColumnsOnlyWhenEveryTitleWordFits() {
        // 360 dp at 1.3×: content 328 dp, gap 8, card padding 12 → 136 dp of text per column; "Companies" ≈ 98 dp fits, so two columns
        // and the title wraps between words (the old ⓘ button beside the title left ≈ 88 dp, which split "Compan/ies").
        assertEquals(2, StockCardGridLayout.columns(widestTitleWord = 98f, availableWidth = 328f, gap = 8f, cardPadding = 12f, accessibilityText = false))
        // A word wider than one column's text width: one column instead of breaking it.
        assertEquals(1, StockCardGridLayout.columns(widestTitleWord = 140f, availableWidth = 328f, gap = 8f, cardPadding = 12f, accessibilityText = false))
        // Boundary: exactly the text width still fits.
        assertEquals(2, StockCardGridLayout.columns(widestTitleWord = 136f, availableWidth = 328f, gap = 8f, cardPadding = 12f, accessibilityText = false))
        // Accessibility text sizes and an unmeasured width are always one column.
        assertEquals(1, StockCardGridLayout.columns(widestTitleWord = 10f, availableWidth = 1000f, gap = 8f, cardPadding = 12f, accessibilityText = true))
        assertEquals(1, StockCardGridLayout.columns(widestTitleWord = 10f, availableWidth = 0f, gap = 8f, cardPadding = 12f, accessibilityText = false))
        assertEquals(listOf("Growing", "Companies"), StockCardGridLayout.words("Growing Companies"))
        assertEquals(listOf("Price", "sales"), StockCardGridLayout.words("Price / sales"))
    }

    @Test
    fun comparisonColumnsOnlyWhenEveryUnbreakableTokenFits() {
        // 360 dp content (328 dp), gap 8: two companies → 160 dp columns; three → ≈ 104; four → 76.
        assertTrue(StockComparisonLayout.sideBySide(widestToken = 110f, companies = 2, availableWidth = 328f, gap = 8f, accessibilityText = false))
        assertTrue(StockComparisonLayout.sideBySide(widestToken = 100f, companies = 3, availableWidth = 328f, gap = 8f, accessibilityText = false))
        // "Semiconductors" (≈ 110 dp) can't fit a four-company column: stacked instead of "Semiconduct/ors".
        assertFalse(StockComparisonLayout.sideBySide(widestToken = 110f, companies = 4, availableWidth = 328f, gap = 8f, accessibilityText = false))
        // Short values fit four columns at the default size.
        assertTrue(StockComparisonLayout.sideBySide(widestToken = 60f, companies = 4, availableWidth = 328f, gap = 8f, accessibilityText = false))
        // Exact fit at the threshold stays side by side; one point more stacks.
        assertTrue(StockComparisonLayout.sideBySide(widestToken = 76f, companies = 4, availableWidth = 328f, gap = 8f, accessibilityText = false))
        assertFalse(StockComparisonLayout.sideBySide(widestToken = 77f, companies = 4, availableWidth = 328f, gap = 8f, accessibilityText = false))
        // Large accessibility text, unmeasured width or no companies: stacked.
        assertFalse(StockComparisonLayout.sideBySide(widestToken = 10f, companies = 2, availableWidth = 1000f, gap = 8f, accessibilityText = true))
        assertFalse(StockComparisonLayout.sideBySide(widestToken = 10f, companies = 2, availableWidth = 0f, gap = 8f, accessibilityText = false))
        assertFalse(StockComparisonLayout.sideBySide(widestToken = 10f, companies = 0, availableWidth = 328f, gap = 8f, accessibilityText = false))
        // Values never split; text splits only at spaces.
        assertEquals(listOf("US$1.23T"), StockComparisonLayout.tokens("US$1.23T"))
        assertEquals(listOf("Auto", "-", "Manufacturers"), StockComparisonLayout.tokens("Auto - Manufacturers"))
        assertEquals(listOf("FY", "ended", "Sep", "2025"), StockComparisonLayout.tokens("FY ended Sep 2025"))
    }

    @Test
    fun chartSeriesDifferByColourAndDashAndNeverMatchTheReference() {
        for (palette in listOf(org.example.stocksteps.theme.ThemeColors.light, org.example.stocksteps.theme.ThemeColors.dark)) {
            val lines = (0..3).map { StockChartSeriesStyle.series(it, palette) }
            assertEquals(4, lines.map { it.color }.distinct().size, "four companies get four colours")
            assertEquals(4, lines.map { it.dash }.distinct().size, "and four line patterns (colour is never the only cue)")
            assertTrue(lines[0].dash.isEmpty(), "main series is solid")
            val reference = StockChartSeriesStyle.reference(palette)
            assertTrue(lines.none { it.color == reference.color && it.dash == reference.dash }, "Start (100) never looks like a company")
            assertEquals(lines[1], StockChartSeriesStyle.series(5, palette), "repeats after four")
            val strip = StockSemanticStyles.sampleStrip(palette)
            assertEquals(palette.warningContainer, strip.container, "the MOCK strip keeps the SAMPLE fill")
        }
    }

    @Test
    fun tabLabelsGrowWithTextUntilTheyStopFittingButNeverEllipsizeOrShrinkBelowStandard() {
        // 360 dp / 5 tabs ≈ 72 dp, minus padding ≈ 64 dp for a label; "Watchlist" ≈ 56 dp at 12 sp, 1.0×.
        assertEquals(1f, StockNavigationLayout.labelScale(widestAtStandard = 56f, labelWidth = 64f, fontScale = 1f))
        assertEquals(1.1f, StockNavigationLayout.labelScale(56f, 64f, 1.1f), "fits: the user's scale")
        val capped = StockNavigationLayout.labelScale(56f, 64f, 2.0f)!!
        assertTrue(capped in 1f..2f && capped * 56f <= 64f, "2.0×: stops growing where every label still fits")
        assertEquals(null, StockNavigationLayout.labelScale(80f, 64f, 1.3f), "can't fit even at standard size: icons only")
        assertEquals(1.5f, StockNavigationLayout.labelScale(0f, 64f, 1.5f), "nothing measured yet: no change")
    }

    @Test
    fun headingsShrinkOnlyWhenOneWordCannotFitItsLine() {
        assertEquals(1f, StockHeadingFit.scale(longestWord = 180f, available = 260f), "fits: user's size")
        val s = StockHeadingFit.scale(longestWord = 320f, available = 260f)
        assertTrue(s < 1f && s * 320f <= 260f * 0.92f, "the long word fits after scaling, with the safety margin")
        assertEquals(1f, StockHeadingFit.scale(longestWord = 230f, available = 260f), "within the margin: unchanged")
        assertEquals(0.6f, StockHeadingFit.scale(longestWord = 1000f, available = 100f), "never below the floor")
        assertEquals(1f, StockHeadingFit.scale(0f, 260f))
    }

    @Test
    fun earningsLabelValueRowsNeverSqueezeALabelIntoLetters() {
        // Android 360 dp, 1.0×: card text width ≈ 296 dp. "Window" / "Source" (≈ 48 dp) beside wrapping values ("StockSteps" ≈ 75 dp).
        val column = StockLayout.labelColumn(widestLabel = 48f, longestLabelWord = 48f, longestValueWord = 75f, availableWidth = 296f)
        assertEquals(48f, column, "side by side: the label keeps its full one-line width; values wrap in the rest")
        // A long market label ("S&P/TSX Composite" ≈ 120 dp) still fits beside the values at 1.0×.
        assertEquals(120f, StockLayout.labelColumn(120f, 64f, 75f, 296f))
        // 2.0×: widths double. With a long market label the capped label column (≥ its longest word "Composite" ≈ 140) leaves the values
        // 144 dp, less than the longest value word ("StockSteps" ≈ 150), so the whole group stacks.
        assertEquals(null, StockLayout.labelColumn(240f, 140f, 150f, 296f))
        // 2.0× with short labels ("Window" ≈ 96): side by side is still readable, values wrap between words.
        assertEquals(96f, StockLayout.labelColumn(96f, 96f, 150f, 296f))
        // A very long label is capped at 45 % of the row (it wraps between words) but never narrower than its longest word.
        assertEquals(296f * StockLayout.LABEL_COLUMN_MAX_SHARE, StockLayout.labelColumn(200f, 60f, 60f, 296f))
        assertEquals(null, StockLayout.labelColumn(200f, 190f, 60f, 296f), "an unbreakable label word leaves too little for values: stack")
        // A single value word wider than the value column stacks instead of breaking the word.
        assertEquals(null, StockLayout.labelColumn(48f, 48f, 250f, 296f))
        assertEquals(null, StockLayout.labelColumn(48f, 48f, 75f, 0f), "unmeasured: stacked (never squeezed)")
    }

    @Test fun briefIndexTilesOnlyWhileEveryTokenFits() {
        // 360 dp phone: 328 dp content, 8 dp gaps, 12 dp tile padding → 80 dp of text per tile.
        assertTrue(StockIndexTilesLayout.tiles(76f, 3, 328f, 8f, 12f, accessibilityText = false))
        assertFalse(StockIndexTilesLayout.tiles(99f, 3, 328f, 8f, 12f, accessibilityText = false)) // 1.3× text
        assertFalse(StockIndexTilesLayout.tiles(40f, 3, 328f, 8f, 12f, accessibilityText = true))
        assertFalse(StockIndexTilesLayout.tiles(40f, 3, 0f, 8f, 12f, accessibilityText = false)) // width unknown yet
        assertFalse(StockIndexTilesLayout.tiles(40f, 1, 328f, 8f, 12f, accessibilityText = false)) // one index: a row
        assertTrue(StockIndexTilesLayout.tiles(88f, 3, 361f, 8f, 12f, accessibilityText = false)) // iPhone 16 Default
    }
}
