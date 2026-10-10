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

    /** Text on an education container (eyebrows, labels): `educationAccent` is 2.75:1 there in light mode, so text uses `cautionText`. */
    fun educationText(p: ThemePalette): Int = p.cautionText
}

/** Pill/segment layout rule shared by both platforms. */
object StockPillLayout {
    /** Equal-width pills fit when every label (measured) plus padding fits its share of [availableWidth]; otherwise scroll horizontally. */
    fun fitsEqualWidth(labelWidths: List<Float>, availableWidth: Float, horizontalPadding: Float): Boolean {
        if (labelWidths.isEmpty()) return true
        val share = availableWidth / labelWidths.size
        return labelWidths.all { it + 2 * horizontalPadding <= share }
    }
}
