package org.example.stocksteps.theme

// Logical units: dp on Android, points on iOS. 4-point grid.
object ThemeSpacing {
    val none = 0
    val xxs = 2
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val xxl = 24
    val xxxl = 32

    // Semantic roles. Prefer these in new code; they are the only spacing names refined components should use.
    /** Horizontal screen padding. */
    val screen = lg
    /** Standard card padding for refined components. */
    val cardPaddingStandard = lg
    /**
     * Default card padding (`StockCard`, `.stockCard`, `StockRow` horizontal inset). Phase 1 kept the compact 12; Phase 1A moved it to
     * [cardPaddingStandard] (16) together with wrapping metric values.
     */
    val cardPadding = cardPaddingStandard
    /** The pre-Phase 1A compact card padding, for dense containers that must keep it. */
    val cardPaddingCompact = md
    /** Spacious cards: hero summaries, promotional and educational containers. */
    val cardPaddingSpacious = xl
    val educationalCardPadding = lg
    /** Gap between cards in a screen list. */
    val contentGap = md
    /**
     * Today: the card gap on Home, Portfolio, Portfolio Insights, Practice and Daily Brief (same value as [contentGap]).
     * Phase 3 moves those lists to [contentGap]; then this becomes the gap between major sections ([xxl] = 24).
     */
    val sectionGap = md
    /** Gap between items inside a card (rows, chips, stacked lines). */
    val itemGap = sm
    /** Label above its value. */
    val labelValueGap = xs
    /** Vertical gap between form fields. */
    val formFieldGap = md
    /** Heading to its description. */
    val related = sm
    /** Compact icon-to-label gap. */
    val iconText = sm
    /** Title to subtitle inside a compact row. */
    val titleSubtitle = xxs

    // Legacy names used by screens that have not migrated to the design system yet (tiny/small/medium/large/extraLarge are
    // still used; migrate them to the scale names when their screens are refined). The off-grid values have no users left.
    val tiny = xs
    @Deprecated("Off the 4-pt grid and unused; use xs (4) or sm (8).") val space6 = 6
    val small = sm
    @Deprecated("Off the 4-pt grid and unused; use sm (8) or md (12).") val space10 = 10
    val medium = md
    @Deprecated("Off the 4-pt grid and unused; use md (12) or lg (16).") val space14 = 14
    val large = lg
    val extraLarge = xxl
    @Deprecated("Off the 4-pt grid and unused; use xxl (24) or xxxl (32).") val space30 = 30
    @Deprecated("Unused; use xxxl (32).") val space40 = 40
}

/**
 * Corner radii (Phase 1): chip 8 · button 12 · card 16 · large container 20. Compose `StockStepsTheme.shapes` and SwiftUI read the
 * same values. Phase 1 raised button 10 → 12, card 12 → 16 and cardLarge 16 → 20 (radius only; no layout change).
 */
object ThemeCorners {
    val chip = 8
    val button = 12
    val card = 16
    /** Large promotional / hero containers. */
    val cardLarge = 20

    // Legacy names.
    val small = chip
    val medium = card
    val large = cardLarge
}

object ThemeDimensions {
    val touchTarget = 48
    val iconSmall = 16
    val icon = 20
    val iconLarge = 24
    val navIcon = 22
    val logo = 32
    val topBarMaxHeight = 56
    val bottomBarHeight = 56
    val rowMinHeight = 56
    val chipHeight = 34
    val buttonHeight = 44
    val searchHeight = 44
    val border = 1
    val contentMaxWidth = 720
    val skeletonLine = 12
    val newsThumbnail = 56
    val newsThumbnailCompact = 48
    val logoCompact = 28
    val brandMark = 26
    val rowCompactMinHeight = 52
    val sparklineWidth = 64
    val sparklineHeight = 28
    val sparklineStroke = 1.5f
    val statusDot = 8
    // Read-only market-status switch: track and thumb.
    val statusSwitchWidth = 32
    val statusSwitchHeight = 18
    val statusSwitchThumb = 14
    val avatar = 44
    val chartHeight = 160
    val educationIcon = 44
    val learnIllustration = 64
    val learnAction = 36
    val iconTile = 36
    val rangeBar = 6
    val rangeMarker = 12
    val changeColumn = 64
    /** Markets index card width: two cards and part of a third fit on common phones. */
    val indexCardWidth = 164
    // Below this width, or above this font scale, side-by-side values stack.
    val multiColumnMinWidth = 300
    val largeFontScale = 1.3f
    /** Narrowest column a metric (label over value) may get before a metric grid drops a column. */
    val metricMinColumnWidth = 96
    /** Icon tile on full empty/error states. */
    val stateIcon = 48
}

/** Layout decisions shared by Compose and SwiftUI so both platforms reflow the same way. */
object StockLayout {
    /**
     * Columns for a metric grid of [count] items in [availableWidth] dp/pt: as many as fit at [ThemeDimensions.metricMinColumnWidth]
     * (scaled by [fontScale]), at most [maxColumns]; one column above [ThemeDimensions.largeFontScale].
     */
    fun metricColumns(count: Int, availableWidth: Float, fontScale: Float, maxColumns: Int = 3, widestContent: Float = 0f, gap: Float = 12f): Int {
        if (count <= 1) return 1
        if (fontScale > ThemeDimensions.largeFontScale) return 1
        val fit = (availableWidth / (ThemeDimensions.metricMinColumnWidth * fontScale.coerceAtLeast(1f))).toInt()
        // Phase 3: also respect the widest measured label/value, so "↑ +2,129.33" never breaks inside a too-narrow column.
        val byContent = if (widestContent > 0f) ((availableWidth + gap) / (widestContent + gap)).toInt() else Int.MAX_VALUE
        return minOf(fit, byContent).coerceIn(1, minOf(count, maxColumns))
    }
}
