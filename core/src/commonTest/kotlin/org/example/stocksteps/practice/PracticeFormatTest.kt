package org.example.stocksteps.practice

import kotlin.test.*

/** Phase 5C: the holding row's screen-reader sentence (shared by the Android and iOS Practice screens). */
class PracticeFormatTest {
    private fun holding(name: String?, stale: Boolean = false) = PracticeHoldingView(
        instrument = PracticeInstrument("AAPL", "AAPL", name, "NASDAQ", "USD", InstrumentKind.STOCK),
        quantity = "2", averageCost = "454.5", costBasis = "909.01", currentPrice = "336.67", priceCurrency = "USD", fxRate = "1.35",
        marketValue = "909.01", unrealizedGain = "0", stale = stale)

    @Test fun nameEndingWithAPeriodIsNotDoubled() {
        assertEquals("AAPL, Apple Inc. 2 shares. Value \$909.01. No change \$0.00.", PracticeFormat.holdingDescription(holding("Apple Inc."), "CAD"))
    }

    @Test fun shareCountUsesTheSingularOnlyForExactlyOne() {
        assertEquals("1 share", PracticeFormat.shareCount("1"))
        assertEquals("1 share", PracticeFormat.shareCount("1.0000"))
        assertEquals("2 shares", PracticeFormat.shareCount("2"))
        assertEquals("0.5 shares", PracticeFormat.shareCount("0.5"))
        assertEquals("1.5 shares", PracticeFormat.shareCount("1.5"))
    }

    @Test fun missingNameIsLeftOutAndStalePricesAreAnnounced() {
        val text = PracticeFormat.holdingDescription(holding(null, stale = true), "CAD")
        assertTrue(text.startsWith("AAPL. 2 shares."), text)
        assertFalse(", ." in text)
        assertTrue(text.endsWith("Price from an earlier session."), text)
    }
}
