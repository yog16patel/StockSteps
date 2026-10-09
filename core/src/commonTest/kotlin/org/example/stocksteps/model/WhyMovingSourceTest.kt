package org.example.stocksteps.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Phase 5C.1: three "Yahoo" links on Company Details were indistinguishable to screen readers. */
class WhyMovingSourceTest {
    @Test fun linksFromOnePublisherReadDifferently() {
        val a = WhyMovingSource("Microsoft unveils new AI laptop", "https://example.test/a", "Yahoo")
        val b = WhyMovingSource("Microsoft debuts Surface Laptop Ultra", "https://example.test/b", "Yahoo")
        assertEquals("Source: Yahoo, Microsoft unveils new AI laptop", a.accessibilityLabel)
        assertNotEquals(a.accessibilityLabel, b.accessibilityLabel)
    }

    @Test fun missingOrRepeatedPublisherFallsBackToTheTitle() {
        assertEquals("Source: Market wrap", WhyMovingSource("Market wrap", "https://example.test/c").accessibilityLabel)
        assertEquals("Source: Market wrap", WhyMovingSource("Market wrap", "https://example.test/c", " ").accessibilityLabel)
        assertEquals("Source: Market wrap", WhyMovingSource("Market wrap", "https://example.test/c", "Market wrap").accessibilityLabel)
    }
}
