package org.example.stocksteps.theme

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Global UI Refinement Phase 1: the shared tokens Compose and SwiftUI both read. These guard the token *values*
 * (contrast, hierarchy, grid); they do not prove a screen is accessible — that still needs TalkBack/VoiceOver and large-text checks.
 */
class DesignTokensTest {
    private val palettes = mapOf("light" to ThemeColors.light, "dark" to ThemeColors.dark)

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun readableTextRolesMeetWcagAaOnEverySurface() {
        palettes.forEach { (name, p) ->
            val surfaces = mapOf("appBackground" to p.appBackground, "surface" to p.surface, "surfaceSecondary" to p.surfaceSecondary,
                "surfaceElevated" to p.surfaceElevated)
            val text = mapOf("textPrimary" to p.textPrimary, "textBody" to p.textBody, "textSecondary" to p.textSecondary,
                "textTertiary" to p.textTertiary, "primaryText" to p.primaryText, "positiveText" to p.positiveText,
                "negativeText" to p.negativeText, "cautionText" to p.cautionText)
            surfaces.forEach { (s, bg) -> text.forEach { (t, fg) ->
                assertTrue(contrast(fg, bg) >= 4.5, "$name $t on $s: ${contrast(fg, bg)} < 4.5")
            } }
        }
    }

    @Test
    fun textRampStepsDownFromTitleToDisabled() {
        palettes.forEach { (name, p) ->
            val ramp = listOf(p.textPrimary, p.textBody, p.textSecondary, p.textTertiary, p.textDisabled).map { contrast(it, p.surface) }
            assertEquals(ramp.sortedDescending(), ramp, "$name text ramp must lose contrast step by step: $ramp")
            // Disabled must look disabled (not a fifth readable grey).
            assertTrue(contrast(p.textDisabled, p.surface) < 4.5, "$name textDisabled reads like body text")
        }
    }

    @Test
    fun textRoleAliasesMapOntoTheExistingRamp() {
        palettes.values.forEach { p ->
            assertEquals(p.textPrimary, p.textTitle)
            assertEquals(p.textPrimary, p.textValue)
            assertEquals(p.textSecondary, p.textSupporting)
            assertEquals(p.textTertiary, p.textMeta)
        }
    }

    @Test
    fun darkCardsStandApartFromTheBackground() {
        val p = ThemeColors.dark
        assertTrue(luminance(p.surface) > luminance(p.appBackground))
        assertTrue(luminance(p.surfaceElevated) > luminance(p.surface))
        assertTrue(luminance(p.border) > luminance(p.borderSubtle) && luminance(p.borderSubtle) > luminance(p.surface))
    }

    @Test
    fun authGradientStopsArePaletteTokensWithTheirOriginalColours() {
        // Moved out of AuthComponents (Compose + SwiftUI) in Phase 1 without changing how sign-in looks.
        palettes.values.forEach { p ->
            assertEquals(0x2997FF, p.primaryBright)
            assertEquals(0x6CB8FF, p.brandGlow)
            assertEquals(0x2F8FFF, p.primaryGradientEnd)
            assertEquals(0xFFFFFF, p.onPrimary)
        }
    }

    @Test
    fun typographyIsAStrictHierarchy() {
        val t = ThemeTypography
        val sizes = listOf(t.display, t.largeNumber, t.screenTitle, t.sectionTitle, t.cardTitle, t.body, t.small, t.label, t.caption, t.tiny)
            .map { it.size }
        assertEquals(sizes.sortedDescending(), sizes)
        assertEquals(sizes.size, sizes.toSet().size, "two hierarchy levels share a size: $sizes")
        assertTrue(t.largeNumber.size > t.screenTitle.size, "hero values must outrank screen titles")
        assertTrue(t.numberEmphasis.size > t.cardTitle.size, "emphasised values must outrank card titles")
        assertTrue(sizes.min() >= 11, "nothing below 11 sp/pt")
        assertEquals(listOf(t.body.size, t.body.size), listOf(t.bodyMedium.size, t.bodySemiBold.size))
    }

    @Test
    fun typographyLineHeightsAndWeightsAreConsistent() {
        val t = ThemeTypography
        val all = listOf(t.display, t.largeNumber, t.screenTitle, t.sectionTitle, t.cardTitle, t.body, t.bodyMedium, t.bodySemiBold,
            t.small, t.label, t.caption, t.tiny, t.numberEmphasis, t.numberMedium, t.numberLabel, t.numberLabelStrong)
        all.forEach { s ->
            assertTrue(s.weight in setOf(400, 500, 600, 700), "weight ${s.weight}")
            assertTrue(s.lineHeight.toDouble() / s.size in 1.2..1.5, "line height ${s.lineHeight} for ${s.size}")
        }
        listOf(t.body, t.bodyMedium, t.bodySemiBold, t.small).forEach { s ->
            assertTrue(s.lineHeight.toDouble() / s.size in 1.3..1.45, "readable text line height ${s.lineHeight}/${s.size}")
        }
        listOf(t.largeNumber, t.numberEmphasis, t.numberMedium, t.numberLabel, t.numberLabelStrong).forEach {
            assertTrue(it.tabular, "financial values use tabular digits")
        }
        assertEquals(listOf(t.screenTitle, t.sectionTitle, t.cardTitle), listOf(t.headline, t.title, t.subtitle))
    }

    @Test
    fun spacingSemanticTokensSitOnTheGrid() {
        val s = ThemeSpacing
        listOf(s.xs, s.sm, s.md, s.lg, s.xl, s.xxl, s.xxxl).forEach { assertEquals(0, it % 4, "$it is off the 4-pt grid") }
        listOf(s.screen, s.cardPadding, s.cardPaddingStandard, s.cardPaddingSpacious, s.educationalCardPadding, s.contentGap,
            s.sectionGap, s.itemGap, s.labelValueGap, s.formFieldGap, s.related, s.iconText).forEach { assertEquals(0, it % 4) }
        assertEquals(16, s.screen)
        assertEquals(16, s.cardPaddingStandard)
        assertEquals(20, s.cardPaddingSpacious)
        assertEquals(4, s.labelValueGap)
        assertTrue(s.contentGap in 12..16 && s.formFieldGap in 12..16)
        // Phase 1 keeps the values existing screens inherit; see docs/GLOBAL_UI_REFINEMENT_PHASE1.md for the migration path.
        assertEquals(12, s.cardPadding)
        assertEquals(s.contentGap, s.sectionGap)
    }

    @Test
    @Suppress("DEPRECATION")
    fun legacySpacingAliasesKeepTheirValues() {
        val s = ThemeSpacing
        assertEquals(listOf(s.xs, s.sm, s.md, s.lg, s.xxl), listOf(s.tiny, s.small, s.medium, s.large, s.extraLarge))
        assertEquals(listOf(6, 10, 14, 30, 40), listOf(s.space6, s.space10, s.space14, s.space30, s.space40))
    }

    @Test
    fun cornersAndDimensionsAreConsistent() {
        val c = ThemeCorners
        assertEquals(listOf(8, 12, 16, 20), listOf(c.chip, c.button, c.card, c.cardLarge))
        assertEquals(listOf(c.chip, c.card, c.cardLarge), listOf(c.small, c.medium, c.large))
        assertTrue(ThemeDimensions.touchTarget >= 48)
        assertEquals(ThemeDimensions.multiColumnMinWidth, FinancialsTokens.twoColumnMinWidth)
        assertEquals(ThemeDimensions.largeFontScale, FinancialsTokens.largeTextScale)
    }
}
