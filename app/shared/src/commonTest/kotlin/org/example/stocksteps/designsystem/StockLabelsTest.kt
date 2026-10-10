package org.example.stocksteps.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class StockLabelsTest {
    @Test
    fun codeIdentifiersBecomeSentenceCaseLabels() {
        assertEquals("Opening position", StockLabels.humanize("OPENING_POSITION"))
        assertEquals("Personal", StockLabels.humanize("PERSONAL"))
        assertEquals("Opening position", StockLabels.humanize("openingPosition"))
        assertEquals("Buy order", StockLabels.humanize("buy-order"))
        assertEquals("", StockLabels.humanize("  "))
    }

    @Test
    fun accountAndMarketAcronymsStayUpperCase() {
        assertEquals("TFSA", StockLabels.humanize("TFSA"))
        assertEquals("Self directed RRSP", StockLabels.humanize("SELF_DIRECTED_RRSP"))
        assertEquals("USD cash", StockLabels.humanize("USD_CASH"))
    }
}
