package org.example.stocksteps.theme

/**
 * Sign-in / create-account layout (shared dp/point values for Android and iOS). Colours come from the
 * StockSteps theme (`ThemeColors.light` / `ThemeColors.dark`), so the screens follow Light, Dark and System;
 * only the Google button keeps Google's own white treatment.
 */
object AuthTokens {
    val contentWidth = 460
    val screenPadding = 18
    val controlHeight = 54
    val controlRadius = 14
    val cardRadius = 24
    val cardPadding = 20
    val logoSize = 44
    val logoRadius = 12
    val googleSize = 22
    val iconSize = 20
    val borderWidth = 1
    /** Hero illustration height; shorter on compact heights (< 700dp) so the form stays reachable. */
    val heroHeight = 170
    val heroHeightCompact = 120
    val headerGap = 22
    val heroGap = 20
    val fieldGap = 7
    val groupGap = 14
    val actionGap = 16
    val heroTitle = ThemeTextStyle(34, 40, 800)
    val brand = ThemeTextStyle(22, 26, 700)
    val tagline = ThemeTextStyle(13, 17, 500)
    val logo = ThemeTextStyle(22, 26, 800)
    val heroBody = ThemeTextStyle(16, 22, 400)
    val body = ThemeTextStyle(15, 20, 400)
    val button = ThemeTextStyle(16, 20, 600)
    val label = ThemeTextStyle(13, 17, 500)
    val linkText = ThemeTextStyle(14, 18, 600)
    val caption = ThemeTextStyle(12, 16, 400)
}
