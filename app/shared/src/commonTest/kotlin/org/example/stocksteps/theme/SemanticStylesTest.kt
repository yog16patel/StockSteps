package org.example.stocksteps.theme

import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.designsystem.components.badgeKind
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Global UI Refinement Phase 2: badge/banner semantics, selection fills and pill layout shared by Compose and SwiftUI. */
class SemanticStylesTest {
    private val palettes = mapOf("light" to ThemeColors.light, "dark" to ThemeColors.dark)

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun everyBadgeKindHasReadableTextOnItsContainer() {
        palettes.forEach { (name, p) ->
            StockBadgeKind.entries.forEach { kind ->
                val t = StockSemanticStyles.badge(kind, p)
                assertTrue(contrast(t.content, t.container) >= 4.5, "$name $kind badge text ${contrast(t.content, t.container)}")
            }
        }
    }

    @Test
    fun badgeKindsAreVisuallyDistinct() {
        palettes.values.forEach { p ->
            val looks = StockBadgeKind.entries.map { StockSemanticStyles.badge(it, p).let { t -> Triple(t.container, t.content, t.border) } }
            assertEquals(looks.size, looks.toSet().size, "two badge kinds look identical")
            // The sample/MOCK disclosure is outlined so it never reads as an ordinary warning.
            val sample = StockSemanticStyles.badge(StockBadgeKind.SAMPLE, p)
            assertTrue(sample.border != sample.container)
        }
    }

    @Test
    fun everyBannerKindIsReadableAndIconsAreVisible() {
        palettes.forEach { (name, p) ->
            StockBannerKind.entries.forEach { kind ->
                val t = StockSemanticStyles.banner(kind, p)
                assertTrue(contrast(t.content, t.container) >= 4.5, "$name $kind banner message")
                assertTrue(contrast(p.textPrimary, t.container) >= 4.5, "$name $kind banner title")
                assertTrue(contrast(t.icon, t.container) >= 3.0, "$name $kind banner icon ${contrast(t.icon, t.container)}")
            }
            // The sample-data banner keeps the same fill as the app-wide MOCK strip it replaced in content.
            assertEquals(p.warningContainer, StockSemanticStyles.banner(StockBannerKind.SAMPLE, p).container)
        }
    }

    @Test
    fun selectionAndEducationTextMeetAa() {
        palettes.forEach { (name, p) ->
            val (fill, text) = StockSemanticStyles.selectedFill(p)
            assertTrue(contrast(text, fill) >= 4.5, "$name selected chip/pill")
            assertTrue(contrast(StockSemanticStyles.educationText(p), p.educationContainer) >= 4.5, "$name education eyebrow")
            assertTrue(contrast(p.negativeText, p.negativeContainer) >= 4.5, "$name negative text on its container")
        }
    }

    @Test
    fun pillsShareWidthOnlyWhileEveryLabelFits() {
        assertTrue(StockPillLayout.fitsEqualWidth(List(7) { 20f }, availableWidth = 329f, horizontalPadding = 12f))
        // "3M" at large text: one label wider than its share → scroll instead of "…".
        assertFalse(StockPillLayout.fitsEqualWidth(List(6) { 20f } + 26f, availableWidth = 329f, horizontalPadding = 12f))
        assertFalse(StockPillLayout.fitsEqualWidth(List(7) { 30f }, availableWidth = 329f, horizontalPadding = 12f))
        assertTrue(StockPillLayout.fitsEqualWidth(emptyList(), availableWidth = 100f, horizontalPadding = 12f))
    }

    @Test
    fun existingBadgeTonesKeepTheirMeaning() {
        // StockBadge(text, tone) API is unchanged; it now renders through the shared kinds.
        assertEquals(StockBadgeKind.POSITIVE, FactTone.POSITIVE.badgeKind())
        assertEquals(StockBadgeKind.NEGATIVE, FactTone.NEGATIVE.badgeKind())
        assertEquals(StockBadgeKind.WARNING, FactTone.CAUTION.badgeKind())
        assertEquals(StockBadgeKind.NEUTRAL, FactTone.NEUTRAL.badgeKind())
    }
}
