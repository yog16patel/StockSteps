package org.example.stocksteps.theme

/** Meaning of a status label (Phase 2). Every kind is paired with text, so colour is never the only signal. */
enum class StockBadgeKind { NEUTRAL, POSITIVE, NEGATIVE, WARNING, INFO, EDUCATION, PREMIUM, SAMPLE }

/** Meaning of a banner / inline status message (Phase 2). `SAMPLE` is the MOCK / simulated-data disclosure. */
enum class StockBannerKind { INFO, SUCCESS, WARNING, ERROR, SAMPLE }

/** RGB roles for a tinted element: fill, text, outline and icon. */
data class StockToneColors(val container: Int, val content: Int, val border: Int, val icon: Int)

/**
 * The one mapping from badge/banner meaning to the existing semantic palette, shared by Compose and SwiftUI (no second colour system).
 * Text roles are AA (≥ 4.5:1) on their container in light and dark — see `DesignTokensTest`.
 */
object StockSemanticStyles {
    fun badge(kind: StockBadgeKind, p: ThemePalette): StockToneColors = when (kind) {
        StockBadgeKind.NEUTRAL -> StockToneColors(p.surfaceSecondary, p.textSecondary, p.borderSubtle, p.iconSecondary)
        StockBadgeKind.POSITIVE -> StockToneColors(p.positiveContainer, p.positiveText, p.positiveBorder, p.positiveText)
        StockBadgeKind.NEGATIVE -> StockToneColors(p.negativeContainer, p.negativeText, p.negativeBorder, p.negativeText)
        StockBadgeKind.WARNING -> StockToneColors(p.warningContainer, p.cautionText, p.warningBorder, p.cautionText)
        StockBadgeKind.INFO -> StockToneColors(p.primaryContainer, p.primaryText, p.primaryContainer, p.primaryText)
        // Learning uses the existing learn (indigo) roles; the education colour family was deliberately not changed (Phase 1A).
        StockBadgeKind.EDUCATION -> StockToneColors(p.learnContainerStart, p.learnAccent, p.learnContainerStart, p.learnAccent)
        StockBadgeKind.PREMIUM -> StockToneColors(p.primaryAction, p.onPrimary, p.primaryAction, p.onPrimary)
        // Same warm family as the sample-data banner, with strong text and an outline so it reads as a disclosure, not a warning.
        StockBadgeKind.SAMPLE -> StockToneColors(p.warningContainer, p.textPrimary, p.warningBorder, p.cautionText)
    }

    /** Banner fill/outline/icon; banner titles use `textPrimary` and messages `textBody` on [StockToneColors.container]. */
    fun banner(kind: StockBannerKind, p: ThemePalette): StockToneColors = when (kind) {
        StockBannerKind.INFO -> StockToneColors(p.primaryContainer, p.textBody, p.primaryContainer, p.primaryText)
        StockBannerKind.SUCCESS -> StockToneColors(p.positiveContainer, p.textBody, p.positiveBorder, p.positiveText)
        StockBannerKind.WARNING -> StockToneColors(p.warningContainer, p.textBody, p.warningBorder, p.cautionText)
        StockBannerKind.ERROR -> StockToneColors(p.negativeContainer, p.textBody, p.negativeBorder, p.negativeText)
        StockBannerKind.SAMPLE -> StockToneColors(p.warningContainer, p.textBody, p.warningBorder, p.cautionText)
    }

    /** Fill/text of a selected chip, pill or range option: the accessible action blue with white text (as primary buttons). */
    fun selectedFill(p: ThemePalette): Pair<Int, Int> = p.primaryAction to p.onPrimary

    /**
     * The app-wide MOCK strip ("Sample data · mock backend"): SAMPLE fill with a hairline [StockToneColors.border] on top and quieter
     * `textSecondary` text (≥ 7:1 on the fill in both themes), so it stays visible without competing with the screen (Comparison final polish).
     */
    fun sampleStrip(p: ThemePalette): StockToneColors = StockToneColors(p.warningContainer, p.textSecondary, p.warningBorder, p.cautionText)

    /** Text on an education container (eyebrows, labels): `educationAccent` is 2.75:1 there in light mode, so text uses `cautionText`. */
    fun educationText(p: ThemePalette): Int = p.cautionText
}

/** Pill/segment layout rule shared by both platforms. */
object StockPillLayout {
    /**
     * Whether a scrolling selector should fade an edge (Phase 5A, closing 4C.3): only while more than half a touch target of content is hidden
     * beyond it ([hiddenBeyondEdge] in the same unit as [touchTarget]). A few pixels of trailing padding past the edge is not "more options", so
     * a fully visible last option is never dimmed (iPhone 16: "ALL" was faded although fully visible).
     */
    fun fadesEdge(hiddenBeyondEdge: Float, touchTarget: Float): Boolean = hiddenBeyondEdge > touchTarget / 2

    /** Equal-width pills fit when every label (measured) plus padding fits its share of [availableWidth]; otherwise scroll horizontally. */
    fun fitsEqualWidth(labelWidths: List<Float>, availableWidth: Float, horizontalPadding: Float): Boolean {
        if (labelWidths.isEmpty()) return true
        val share = availableWidth / labelWidths.size
        return labelWidths.all { it + 2 * horizontalPadding <= share }
    }
}

/**
 * Side-by-side header actions (Company Details "Add to portfolio" + "Compare", Phase 4C.3), shared by both platforms: the pair shares a row
 * only while every measured label (icon included) plus padding fits its equal share after the [gap]; never at accessibility text sizes. Otherwise
 * each action takes its own full-width row — labels and fonts are never shrunk to force one row.
 */
object StockActionLayout {
    fun sideBySide(labelWidths: List<Float>, availableWidth: Float, horizontalPadding: Float, gap: Float, accessibilityText: Boolean): Boolean =
        !accessibilityText && labelWidths.isNotEmpty() && availableWidth > 0 &&
            StockPillLayout.fitsEqualWidth(labelWidths, availableWidth - gap * (labelWidths.size - 1), horizontalPadding)
}

/**
 * Where a status badge sits next to a row title (Phase 4C.3): short badges ("High margin", "Near History") may share the title's line when it
 * fits; long ones ("More cash than debt") always go below the title so they never squeeze it. Character count, not pixels, so both platforms
 * decide the same way at every text size; the fit check stays with each layout.
 */
object StockBadgeLayout {
    const val INLINE_MAX_CHARACTERS = 14

    fun mayShareTitleLine(badge: String): Boolean = badge.trim().length <= INLINE_MAX_CHARACTERS
}

/**
 * Watchlist quote rows (Phase 5A), shared by both platforms: price and change sit to the right of the company while the wider of the two
 * measured values takes at most [MAX_VALUE_SHARE] of the space beside the logo; otherwise — narrow phones, large text — they move onto their
 * own line under the company, so the name keeps room and values never wrap into fragments. Always stacked at accessibility text sizes.
 */
object StockQuoteRowLayout {
    const val MAX_VALUE_SHARE = 0.5f

    fun stacksValues(valuesWidth: Float, availableWidth: Float, accessibilityText: Boolean): Boolean =
        accessibilityText || (availableWidth > 0 && valuesWidth > availableWidth * MAX_VALUE_SHARE)
}

/**
 * Card grids such as the Discover Stocks preset cards (Phase 5B), shared by both platforms: two columns only while the longest single word
 * of every card title fits the text width of one column, so titles wrap between words ("Growing / Companies") and never inside one
 * ("Compan/ies" at 360 dp, 1.3×). Otherwise — narrow screens, accessibility text sizes or an unmeasured width — one full-width column.
 */
object StockCardGridLayout {
    fun columns(widestTitleWord: Float, availableWidth: Float, gap: Float, cardPadding: Float, accessibilityText: Boolean): Int {
        if (accessibilityText || availableWidth <= 0f) return 1
        val textWidth = (availableWidth - gap) / 2 - cardPadding * 2
        return if (widestTitleWord <= textWidth) 2 else 1
    }

    /** The words a title can break between (spaces and slashes, as text layout does). */
    fun words(title: String): List<String> = title.split(' ', '/', ' ').filter { it.isNotBlank() }
}

/**
 * Company Comparison values (Phase 5C), shared by both platforms: companies sit side by side in equal columns only while the widest piece
 * of text that must stay on one line — a whole value ("US$1.23T", "−12.3%") or one word of a text value or period ("Semiconductors") —
 * fits one column. Otherwise (four companies on a narrow phone, large text, long industry names) every metric is stacked: one line per
 * company, in selection order, with the company beside its value. Always stacked at accessibility text sizes; an unmeasured width stacks
 * too, so nothing is ever cut or broken mid-word while the layout is still unknown.
 */
object StockComparisonLayout {
    fun sideBySide(widestToken: Float, companies: Int, availableWidth: Float, gap: Float, accessibilityText: Boolean): Boolean {
        if (accessibilityText || availableWidth <= 0f || companies <= 0) return false
        val column = (availableWidth - gap * (companies - 1)) / companies
        return widestToken <= column
    }

    /** The pieces that may not break: text splits only at spaces, so each whitespace-separated token must fit. */
    fun tokens(text: String): List<String> = text.split(' ', ' ', '\n').filter { it.isNotBlank() }
}

/**
 * Line styles for multi-series charts (Comparison final polish), shared by Compose `StockTrendChart` and Swift Charts so a company keeps the same
 * colour **and** dash pattern on both platforms and in the legend. Colour is never the only cue: each series also differs in dash pattern and the
 * legend names it. Dash lengths are dp / pt (on, off, …); empty = solid.
 */
object StockChartSeriesStyle {
    data class Line(val color: Int, val dash: List<Float>, val width: Float = 2f)

    /** 0 = the main series (brand blue, solid); 1–3 = slate dashed, orange dotted, indigo dash-dot; repeats after 4. */
    fun series(index: Int, p: ThemePalette): Line = when (index.mod(4)) {
        0 -> Line(p.primary, emptyList())
        1 -> Line(p.textSecondary, listOf(6f, 4f))
        2 -> Line(p.caution, listOf(1f, 4f))
        else -> Line(p.learnAccent, listOf(8f, 3f, 2f, 3f))
    }

    /** Reference line (e.g. "Start (100)", an average or zero): thin, muted, long dashes — never one of the series styles. */
    fun reference(p: ThemePalette): Line = Line(p.textTertiary, listOf(10f, 5f), width = 1f)
}
