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

    /** Phase 7A: a blocked preview's "0" placeholder (no quote, plan-restricted quote) is never shown as "$0.00". */
    @Test fun aPreviewWithoutAUsablePriceSaysUnavailable() {
        fun preview(price: String, blocker: PracticeBlocker? = null) = PracticeOrderPreview(
            PracticeInstrument("XLK", "XLK", null, "NYSEARCA", "USD", InstrumentKind.ETF), OrderSide.BUY, "1", price, "USD", "0", null, "CAD",
            "0", "10000", "10000", null, "Unavailable", "0", true, 0, blocker = blocker)
        assertEquals("Unavailable", PracticeFormat.previewPrice(preview("0", PracticeBlocker("QUOTE_RESTRICTED", "x"))))
        assertEquals("Unavailable", PracticeFormat.previewPrice(preview("not a number")))
        assertEquals("US\$123.45", PracticeFormat.previewPrice(preview("123.45")))
    }
}
