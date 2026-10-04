package org.example.stocksteps.theme

// Font sizes use sp on Android and Dynamic Type-scaled points on iOS.
// Each platform keeps its native system font.
data class ThemeTextStyle(val size: Int, val lineHeight: Int, val weight: Int)

object ThemeTypography {
    val headline = ThemeTextStyle(28, 36, 700)
    val title = ThemeTextStyle(22, 28, 600)
    val subtitle = ThemeTextStyle(16, 24, 600)
    val body = ThemeTextStyle(16, 24, 400)
    val caption = ThemeTextStyle(12, 16, 600)
}
