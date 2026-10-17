package org.example.stocksteps.presentation.discovery

import org.example.stocksteps.model.MarketMover
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Phase 8B: the legacy movers line never prints "Unavailable%", a raw double or $0.00 for missing data. */
class MarketMoverDisplayTest {
    private val d = MarketMoverDisplay

    @Test
    fun missingChangeAndPriceAreSaidInWords() {
        assertEquals("Change unavailable · Price unavailable", d.line(MarketMover("X")))
        assertEquals("Change unavailable", d.change(Double.NaN))
        assertFalse(d.line(MarketMover("X")).contains("%"))
    }

    @Test
    fun validValuesKeepSignTwoDecimalsAndCurrency() {
        assertEquals("+1.23% · $12.34", d.line(MarketMover("X", price = 12.3412, changePercent = 1.2345)))
        assertEquals("-0.40% · $7.00", d.line(MarketMover("X", price = 7.0, changePercent = -0.4)))
        assertEquals("0.00% · Price unavailable", d.line(MarketMover("X", changePercent = 0.0)))
        assertEquals("Change unavailable · $3.10", d.line(MarketMover("X", price = 3.1)))
    }
}
