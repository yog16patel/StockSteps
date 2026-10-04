package org.example.stocksteps.theme

// RGB values are independent of Compose and SwiftUI.
data class ThemePalette(
    val primary: Int,
    val onPrimary: Int,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val outline: Int,
    val error: Int,
    val positive: Int,
    val negative: Int
)

object ThemeColors {
    val light = ThemePalette(
        primary = 0x146B56, onPrimary = 0xFFFFFF,
        background = 0xF7FAF8, surface = 0xFFFFFF, surfaceVariant = 0xE8F0EC,
        textPrimary = 0x18251F, textSecondary = 0x52635A, outline = 0x718078,
        error = 0xB3261E, positive = 0x146B56, negative = 0xB3261E
    )
    val dark = ThemePalette(
        primary = 0x8CD5B8, onPrimary = 0x003829,
        background = 0x101914, surface = 0x18251F, surfaceVariant = 0x2C3A32,
        textPrimary = 0xE2ECE5, textSecondary = 0xB3C3B8, outline = 0x85988B,
        error = 0xFFB4AB, positive = 0x8CD5B8, negative = 0xFFB4AB
    )
}
