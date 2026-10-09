package org.example.stocksteps.portfolio

import kotlin.test.Test
import kotlin.test.assertEquals

/** Phase 5C.1: My Portfolio totals read "CAD 2145.53" while every other money figure was grouped. */
class PortfolioFormatTest {
    @Test fun amountsAreGroupedWithTwoDecimals() {
        assertEquals("2,145.53", PortfolioFormat.amount("2145.53"))
        assertEquals("1,234,567.00", PortfolioFormat.amount("1234567"))
        assertEquals("999.99", PortfolioFormat.amount("999.99"))
        assertEquals("0.00", PortfolioFormat.amount("0"))
    }

    @Test fun negativesKeepTheirSignOutsideTheGroups() {
        assertEquals("-1,200.50", PortfolioFormat.amount("-1200.5"))
        assertEquals("-12.00", PortfolioFormat.amount("-12"))
    }

    @Test fun missingOrInvalidValuesAreUnavailableNotZero() {
        assertEquals("—", PortfolioFormat.amount(null))
        assertEquals("—", PortfolioFormat.amount("not a number"))
    }
}
