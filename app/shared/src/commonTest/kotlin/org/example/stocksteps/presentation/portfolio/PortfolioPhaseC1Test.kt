package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.TransactionType
import org.example.stocksteps.theme.StockLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase C.1: transaction text groups and the shared row-stacking rule used by legend and value lines. */
class PortfolioPhaseC1Test {
    private val opening = PortfolioTransaction("t", "a", TransactionType.OPENING_POSITION, "2026-09-01", PortfolioCurrency.CAD,
        instrument = InstrumentRef("TD.TO"), quantity = "5", unitPrice = "75")

    @Test
    fun transactionTextIsSplitIntoGroups() {
        val text = PortfolioPresentation.transactionText(opening)
        assertEquals("Opening position", text.title)
        assertEquals("TD.TO · 5 shares", text.security)
        assertEquals("Sep 1, 2026", text.date)
        assertEquals("CAD 75.00 per share", text.amount)
        assertEquals("Opening position, TD.TO · 5 shares, Sep 1, 2026, CAD 75.00 per share", text.spoken)
        assertFalse(' ' in text.spoken)
    }

    @Test
    fun cashTransactionHasNoSecurityGroupAndKeepsItsAmount() {
        val deposit = opening.copy(type = TransactionType.CASH_DEPOSIT, instrument = null, quantity = "0", unitPrice = "0", netAmount = "500")
        val text = PortfolioPresentation.transactionText(deposit)
        assertNull(text.security)
        assertEquals("Cash deposit", text.title)
        assertEquals("CAD 500.00", text.amount)
        assertEquals("Cash deposit, Sep 1, 2026, CAD 500.00", text.spoken)
    }

    @Test
    fun rowsStackOnlyWhenLabelAndValueCannotShareTheLine() {
        // Legend "AAPL · USD" + "72.97%" at 1.0× on a 296 dp legend column: side by side; at 2.0× on a 280 dp column: stacked.
        assertFalse(StockLayout.metricRowStacked(longestLabelWord = 100f, valueWidth = 70f, availableWidth = 296f, gap = 8f))
        assertTrue(StockLayout.metricRowStacked(longestLabelWord = 200f, valueWidth = 140f, availableWidth = 280f, gap = 8f))
    }
}
