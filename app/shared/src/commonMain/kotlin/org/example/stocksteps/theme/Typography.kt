package org.example.stocksteps.theme

// Font sizes use sp on Android and Dynamic Type-scaled points on iOS.
// Each platform keeps its native system font. `tabular` requests fixed-width
// digits for financial numbers (Compose "tnum", SwiftUI monospacedDigit).
data class ThemeTextStyle(val size: Int, val lineHeight: Int, val weight: Int, val tabular: Boolean = false)

/**
 * Shared type scale (Global UI Refinement Phase 1). Hierarchy: values and titles dominate, body text reads at 15, supporting text
 * steps down to 14/13/12 and is coloured with `textSecondary`/`textTertiary` (colour is chosen per use, not baked into the style).
 * Body line heights are ~1.4×; nothing here sets a fixed height, so text grows with Android font scale and iOS Dynamic Type.
 */
object ThemeTypography {
    val display = ThemeTextStyle(36, 44, 700)
    // 32 rather than the 34 target: hero values (portfolio total, price, P/E, practice total) share a line with the currency
    // code on 360-dp phones; 32 keeps them dominant over screenTitle (28) without new wrapping at default font scale.
    val largeNumber = ThemeTextStyle(32, 40, 700, tabular = true)
    val screenTitle = ThemeTextStyle(28, 34, 700)
    val sectionTitle = ThemeTextStyle(20, 26, 600)
    val cardTitle = ThemeTextStyle(17, 22, 600)
    val body = ThemeTextStyle(15, 21, 400)
    val bodyMedium = ThemeTextStyle(15, 21, 500)
    val bodySemiBold = ThemeTextStyle(15, 21, 600)
    val small = ThemeTextStyle(14, 20, 400)
    val label = ThemeTextStyle(13, 18, 500)
    val caption = ThemeTextStyle(12, 16, 400)
    // Medium (not the Regular target): tiny is used for badges and pills, where 11 sp regular is hard to read.
    val tiny = ThemeTextStyle(11, 14, 500)

    // Financial values: same scale family with tabular digits.
    // numberEmphasis 18 rather than the 22 target: it sits in single-line metric grids (At a Glance, Markets index cards,
    // valuation) that already truncate long values; the wrapping metric component exists since Phase 1A, and the value moves to 22
    // when those screens adopt it.
    val numberEmphasis = ThemeTextStyle(18, 24, 600, tabular = true)
    val numberMedium = ThemeTextStyle(17, 22, 600, tabular = true)
    val numberLabel = ThemeTextStyle(13, 18, 400, tabular = true)
    val numberLabelStrong = ThemeTextStyle(13, 18, 500, tabular = true)

    // Legacy names used by screens that have not migrated to the design system yet.
    val headline = screenTitle
    val title = sectionTitle
    val subtitle = cardTitle
}
