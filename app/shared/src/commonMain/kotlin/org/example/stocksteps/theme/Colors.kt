package org.example.stocksteps.theme

// StockSteps design-system palette. RGB values are independent of Compose and
// SwiftUI so both platforms render the same brand.
data class ThemePalette(
    val appBackground: Int,
    val backgroundAlt: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val surfaceSecondary: Int,
    val surfaceSelected: Int,
    val border: Int,
    val borderSubtle: Int,
    val primary: Int,
    val primaryBright: Int,
    val primaryDark: Int,
    val primaryContainer: Int,
    val primaryTint: Int,
    val onPrimary: Int,
    val positive: Int,
    val positiveBright: Int,
    val positiveContainer: Int,
    val positiveBorder: Int,
    val warning: Int,
    val warningBright: Int,
    val warningContainer: Int,
    val warningBorder: Int,
    val caution: Int,
    val cautionContainer: Int,
    val cautionBorder: Int,
    val negative: Int,
    val negativeBright: Int,
    val negativeContainer: Int,
    val negativeBorder: Int,
    val textPrimary: Int,
    val textBody: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val textDisabled: Int,
    val iconPrimary: Int,
    val iconSecondary: Int,
    // Text-role variants: the brand fills are below WCAG AA (4.5:1) for small
    // text on light surfaces, so small labels use these instead.
    val primaryText: Int,
    val positiveText: Int,
    val negativeText: Int,
    // Warm surface for learning content; deliberately separate from Warning semantics.
    val educationContainer: Int,
    val educationAccent: Int,
    // Brand logos are drawn for light backgrounds, so their tile stays light in dark mode.
    val logoContainer: Int
) {
    // Legacy names used by screens that have not migrated to the design system yet.
    val background: Int get() = appBackground
    val surfaceVariant: Int get() = surfaceSecondary
    val outline: Int get() = border
    val error: Int get() = negative
}

object ThemeColors {
    val light = ThemePalette(
        appBackground = 0xF7FAFD, backgroundAlt = 0xF3F6FA,
        surface = 0xFFFFFF, surfaceElevated = 0xFFFFFF, surfaceSecondary = 0xF3F6FA, surfaceSelected = 0xEBF3FF,
        border = 0xD6E0EA, borderSubtle = 0xE9EEF4,
        primary = 0x1683FF, primaryBright = 0x2997FF, primaryDark = 0x0866C6,
        primaryContainer = 0xEBF3FF, primaryTint = 0x62AEFF, onPrimary = 0xFFFFFF,
        positive = 0x16A86B, positiveBright = 0x20D98B, positiveContainer = 0xE9F9F2, positiveBorder = 0xBCEBD8,
        warning = 0xD99516, warningBright = 0xF5B942, warningContainer = 0xFFF6E5, warningBorder = 0xF3DBA5,
        caution = 0xE87924, cautionContainer = 0xFFF0E4, cautionBorder = 0xF5C9A5,
        negative = 0xE5484D, negativeBright = 0xFF5B57, negativeContainer = 0xFDEEEE, negativeBorder = 0xF4C1C1,
        textPrimary = 0x0F172A, textBody = 0x334155, textSecondary = 0x475569,
        textTertiary = 0x64748B, textDisabled = 0x94A3B8,
        iconPrimary = 0x1E293B, iconSecondary = 0x64748B,
        primaryText = 0x0866C6, positiveText = 0x0E7F54, negativeText = 0xD13338,
        educationContainer = 0xFFF4E6, educationAccent = 0xE07B1F, logoContainer = 0xFFFFFF
    )

    // Dark is not an inverted light theme: navy grounds, lighter surfaces, subtle borders.
    // The dark spec has no SurfaceSecondary; SurfaceElevated serves that role.
    val dark = ThemePalette(
        appBackground = 0x07111C, backgroundAlt = 0x091522,
        surface = 0x0D1B2A, surfaceElevated = 0x122334, surfaceSecondary = 0x122334, surfaceSelected = 0x17314A,
        border = 0x243647, borderSubtle = 0x192A39,
        primary = 0x1683FF, primaryBright = 0x2997FF, primaryDark = 0x0866C6,
        primaryContainer = 0x123A5D, primaryTint = 0x62AEFF, onPrimary = 0xFFFFFF,
        positive = 0x20D98B, positiveBright = 0x3EE6A0, positiveContainer = 0x103A31, positiveBorder = 0x17664E,
        warning = 0xF5B942, warningBright = 0xFFC857, warningContainer = 0x40341B, warningBorder = 0x705B25,
        caution = 0xFF9D42, cautionContainer = 0x432D19, cautionBorder = 0x75491F,
        negative = 0xFF5B57, negativeBright = 0xFF665F, negativeContainer = 0x442020, negativeBorder = 0x75302E,
        textPrimary = 0xF5F7FA, textBody = 0xD4DCE4, textSecondary = 0xAAB7C4,
        textTertiary = 0x718091, textDisabled = 0x526171,
        iconPrimary = 0xEAF0F6, iconSecondary = 0x8FA0B2,
        primaryText = 0x62AEFF, positiveText = 0x20D98B, negativeText = 0xFF5B57,
        educationContainer = 0x2A2116, educationAccent = 0xF5B942, logoContainer = 0xE8EEF4
    )
}
