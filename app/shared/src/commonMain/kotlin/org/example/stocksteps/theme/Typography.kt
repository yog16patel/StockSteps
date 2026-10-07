package org.example.stocksteps.theme

// Font sizes use sp on Android and Dynamic Type-scaled points on iOS.
// Each platform keeps its native system font. `tabular` requests fixed-width
// digits for financial numbers (Compose "tnum", SwiftUI monospacedDigit).
data class ThemeTextStyle(val size: Int, val lineHeight: Int, val weight: Int, val tabular: Boolean = false)

object ThemeTypography {
    val display = ThemeTextStyle(32, 38, 700)
    val largeNumber = ThemeTextStyle(26, 32, 700, tabular = true)
    val screenTitle = ThemeTextStyle(24, 30, 700)
    val sectionTitle = ThemeTextStyle(18, 24, 600)
    val cardTitle = ThemeTextStyle(16, 21, 600)
    val body = ThemeTextStyle(14, 20, 400)
    val bodyMedium = ThemeTextStyle(14, 20, 500)
    val bodySemiBold = ThemeTextStyle(14, 20, 600)
    val small = ThemeTextStyle(13, 18, 400)
    val label = ThemeTextStyle(12, 16, 500)
    val caption = ThemeTextStyle(11, 15, 400)
    val tiny = ThemeTextStyle(10, 13, 500)

    // Financial values use the same scale with tabular digits.
    val numberEmphasis = cardTitle.copy(tabular = true)
    val numberMedium = bodyMedium.copy(tabular = true)
    val numberLabel = label.copy(tabular = true)
    val numberLabelStrong = ThemeTextStyle(12, 16, 600, tabular = true)

    // Legacy names used by screens that have not migrated to the design system yet.
    val headline = screenTitle
    val title = sectionTitle
    val subtitle = cardTitle
}
