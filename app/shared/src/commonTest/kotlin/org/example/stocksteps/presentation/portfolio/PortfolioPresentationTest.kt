package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioHoldingRow
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioPresentationTest {
    private val msft = PortfolioHoldingRow("MSFT", "Microsoft Corporation", "NASDAQ", "USD", "3", "529.76", "1589.28", "12",
        "1577.28", "13144", "0", "0", null, stale = false)

    @Test
    fun enumsAreNeverShownRaw() {
        PortfolioCategory.entries.forEach { assertFalse(PortfolioPresentation.categoryLabel(it).contains('_'), "$it") }
        TransactionType.entries.forEach { type ->
            val label = PortfolioPresentation.transactionLabel(type)
            assertFalse(label.contains('_') || label == type.name, "$type")
        }
        assertEquals("Non-registered", PortfolioPresentation.categoryLabel(PortfolioCategory.NON_REGISTERED))
        assertEquals("Opening position", PortfolioPresentation.transactionLabel(TransactionType.OPENING_POSITION))
        val account = PortfolioAccount("a", "Personal", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
        assertEquals("Personal · CAD", PortfolioPresentation.accountLabel(account))
        assertEquals("TFSA · reports in CAD", PortfolioPresentation.accountDetail(account))
    }

    @Test
    fun changesCarryExplicitSignsAndMissingValuesStayMissing() {
        assertEquals("+1,577.28 (+13,144.00%)", PortfolioPresentation.change("1577.28", "13144"))
        assertEquals("-12.50 (-1.20%)", PortfolioPresentation.change("-12.5", "-1.2"))
        assertEquals("0.00 (0.00%)", PortfolioPresentation.change("0", "0"))
        assertEquals("+5.00", PortfolioPresentation.change("5", null))
        assertNull(PortfolioPresentation.change(null, "1.5"))
        assertNull(PortfolioPresentation.todayChange(PortfolioUiState(dailyGain = null, dailyPercent = null)))
        assertEquals(PriceDirection.UNAVAILABLE, PortfolioPresentation.direction(null))
        assertEquals(PriceDirection.UNAVAILABLE, PortfolioPresentation.direction("n/a"))
        assertEquals(PriceDirection.DOWN, PortfolioPresentation.direction("-0.01"))
        assertEquals(PriceDirection.UNCHANGED, PortfolioPresentation.direction("0.00"))
    }

    @Test
    fun holdingRowsDescribeEverythingOnce() {
        assertEquals("MSFT · NASDAQ · 3 shares", PortfolioPresentation.holdingSubtitle(msft))
        assertEquals("USD 1,589.28", PortfolioPresentation.holdingValue(msft))
        val spoken = PortfolioPresentation.holdingDescription(msft)
        assertTrue(spoken.startsWith("Microsoft Corporation, MSFT · NASDAQ · 3 shares, value USD 1,589.28, unrealized gain 1,577.28 (+13,144.00%)"), spoken)
        val missing = msft.copy(value = null, gain = null, gainPercent = null, stale = true, exchange = null, quantity = "1")
        assertEquals("+13,144.00%" to "(+1,577.28)", PortfolioPresentation.holdingChangeLines(msft))
        assertEquals("+1,577.28" to null, PortfolioPresentation.holdingChangeLines(msft.copy(gainPercent = null)))
        assertNull(PortfolioPresentation.holdingChangeLines(missing))
        assertEquals("USD —", PortfolioPresentation.holdingValue(missing))
        assertEquals("MSFT · 1 share", PortfolioPresentation.holdingSubtitle(missing))
        assertTrue(PortfolioPresentation.holdingDescription(missing).contains("unrealized gain unavailable"))
        assertTrue(PortfolioPresentation.holdingDescription(missing).contains("stale"))
    }

    @Test
    fun transactionsAndFreshness() {
        val buy = PortfolioTransaction("t", "a", TransactionType.OPENING_POSITION, "2026-10-09", PortfolioCurrency.USD,
            InstrumentRef("MSFT", "Microsoft Corporation", "NASDAQ", "USD"), quantity = "3", netAmount = "12")
        assertEquals("MSFT · 3 shares", PortfolioPresentation.transactionDetail(buy))
        assertEquals("USD 4.00 per share", PortfolioPresentation.transactionAmount(buy.copy(unitPrice = "4")))
        assertEquals("USD 12.00", PortfolioPresentation.transactionAmount(buy))
        assertEquals("CAD 500.00", PortfolioPresentation.transactionAmount(buy.copy(type = TransactionType.CASH_DEPOSIT, currency = PortfolioCurrency.CAD,
            instrument = null, unitPrice = "1", netAmount = "500")))
        assertNull(PortfolioPresentation.transactionDetail(buy.copy(instrument = null)))
        assertEquals("MSFT", PortfolioPresentation.transactionDetail(buy.copy(quantity = "0")))
        assertNull(PortfolioPresentation.freshness(PortfolioUiState()))
        assertEquals("Prices as of Oct 7, 2026 · Exchange rate as of Oct 9, 2026",
            PortfolioPresentation.freshness(PortfolioUiState(quoteAsOf = "2026-10-07", fxAsOf = "2026-10-09")))
        assertEquals(listOf("Prices as of Oct 7, 2026, 8:00 PM UTC", "Exchange rate as of Oct 9, 2026"),
            PortfolioPresentation.freshnessLines(PortfolioUiState(quoteAsOf = "2026-10-07T20:00:00Z", fxAsOf = "2026-10-09")))
        assertEquals(1f, PortfolioPresentation.allocationFraction("135.5"))
        assertEquals(0f, PortfolioPresentation.allocationFraction(null))
        assertEquals(0.25f, PortfolioPresentation.allocationFraction("25"))
    }

    @Test
    fun chartIsDrawnOnlyWithTwoDatedValues() {
        assertFalse(PortfolioChartRules.drawable(emptyList()))
        assertFalse(PortfolioChartRules.drawable(listOf(null, "1850", null)))
        assertTrue(PortfolioChartRules.drawable(listOf("1800", null, "1850")))
    }

    @Test
    fun datesAreReadableAndNeverGuessed() {
        assertEquals("Oct 9, 2026", PortfolioDates.date("2026-10-09"))
        assertEquals("Sep 1", PortfolioDates.shortDate("2026-09-01"))
        assertEquals("Oct 8, 2026, 3:00 PM UTC", PortfolioDates.dateTime("2026-10-08T15:00:00Z"))
        assertEquals("Jan 1, 2026, 12:05 AM UTC", PortfolioDates.dateTime("2026-01-01T00:05Z"))
        assertEquals("Dec 31, 2026, 12:00 PM UTC", PortfolioDates.dateTime("2026-12-31T12:00:00.123Z"))
        // Offsets other than Z and unknown text are shown unchanged rather than converted by guesswork.
        assertEquals("2026-10-08T11:00:00-04:00", PortfolioDates.dateTime("2026-10-08T11:00:00-04:00"))
        assertEquals("yesterday", PortfolioDates.dateTime("yesterday"))
        assertEquals("2026-13-01", PortfolioDates.date("2026-13-01"))
    }

    @Test
    fun donutOnlyForCompleteNonNegativeAllocations() {
        assertTrue(PortfolioDisplayRules.allocationDonutAllowed(listOf("72.97", "27.03")))
        assertTrue(PortfolioDisplayRules.allocationDonutAllowed(listOf("60", "30")), "cash or unallocated may leave a gap")
        assertFalse(PortfolioDisplayRules.allocationDonutAllowed(emptyList()))
        assertFalse(PortfolioDisplayRules.allocationDonutAllowed(listOf("72.97", null)), "missing slice never becomes zero")
        assertFalse(PortfolioDisplayRules.allocationDonutAllowed(listOf("135.5", "-35.5")), "borrowed cash: keep bars")
        assertFalse(PortfolioDisplayRules.allocationDonutAllowed(listOf("60", "40.01")), "over 100 %")
        assertFalse(PortfolioDisplayRules.allocationDonutAllowed(listOf("n/a")))
    }

    @Test
    fun holdingsSortIsDisplayOnlyAndKeepsMissingLast() {
        val td = msft.copy(symbol = "TD.TO", name = "Toronto-Dominion Bank", currency = "CAD", value = "500", gainPercent = "33.33")
        val aapl = msft.copy(symbol = "AAPL", name = "apple Inc.", value = "1000", gainPercent = null)
        val rows = listOf(msft, td, aapl)
        // Value ranks by the reporting-currency amount (USD 1,000 = CAD 1,350 beats CAD 500), not by native amounts; MSFT has none → last.
        val reporting = mapOf("AAPL" to "1350", "TD.TO" to "500", "MSFT" to null)
        assertEquals(listOf("AAPL", "TD.TO", "MSFT"), PortfolioDisplayRules.sortHoldings(rows, PortfolioHoldingSort.VALUE, reporting).map { it.symbol })
        assertEquals(listOf("MSFT", "TD.TO", "AAPL"), PortfolioDisplayRules.sortHoldings(rows, PortfolioHoldingSort.CHANGE).map { it.symbol })
        assertEquals(listOf("AAPL", "MSFT", "TD.TO"), PortfolioDisplayRules.sortHoldings(rows, PortfolioHoldingSort.NAME).map { it.symbol })
        assertEquals(rows.toSet(), PortfolioDisplayRules.sortHoldings(rows, PortfolioHoldingSort.VALUE).toSet(), "no row is dropped or changed")
        assertEquals(rows, PortfolioDisplayRules.sortHoldings(rows, PortfolioHoldingSort.VALUE), "no reporting values: original order")
    }

    @Test
    fun holdingDetailsAmountsAreFormattedWithCurrency() {
        // Finding A-F3: Realized and Dividends were raw ledger strings ("3.5", "12.50000000").
        val row = msft.copy(realized = "3.5", dividends = "12.50000000")
        assertEquals("+USD 3.50", PortfolioPresentation.holdingRealized(row))
        assertEquals("USD 12.50", PortfolioPresentation.holdingDividends(row))
        assertEquals("-USD 1,200.25", PortfolioPresentation.holdingRealized(row.copy(realized = "-1200.25")))
        assertEquals("USD 0.00", PortfolioPresentation.holdingRealized(row.copy(realized = "0")))
        assertEquals("CAD 0.00", PortfolioPresentation.holdingDividends(row.copy(currency = "CAD", dividends = "0")))
        // Unparsable never becomes zero.
        assertEquals("USD —", PortfolioPresentation.holdingRealized(row.copy(realized = "n/a")))
        assertEquals("USD —", PortfolioPresentation.signedMoney("USD", null))
    }

    @Test
    fun sharedCopyKeepsDisclosuresAndAvoidsAdvice() {
        // Sample copy says it's read-only and that saved data is untouched; the chart copy says it isn't a return (A-F6, A-F18).
        assertTrue("Read-only" in PortfolioDisplay.SAMPLE_SHORT && "stay unchanged" in PortfolioDisplay.SAMPLE_BANNER)
        assertTrue("deposits" in PortfolioDisplay.CHART_NOTE && "StockSteps+" in PortfolioDisplay.CHART_NOTE)
        assertEquals("Account value", PortfolioDisplay.CHART_TITLE)
        assertEquals("Portfolio insights", PortfolioDisplay.INSIGHTS_TITLE)
        assertEquals("Delete TFSA?", PortfolioDisplay.deleteAccountTitle("TFSA"))
        val all = listOf(PortfolioDisplay.SAMPLE_BANNER, PortfolioDisplay.EMPTY_MESSAGE, PortfolioDisplay.HOLDINGS_EMPTY, PortfolioDisplay.CHART_NOTE,
            PortfolioDisplay.FORM_INTRO, PortfolioDisplay.ACCOUNT_NOTE, PortfolioDisplay.ABOUT_CASH, PortfolioDisplay.ABOUT_ALLOCATION)
        all.forEach { text -> listOf("should", "consider", "recommend", "guideline").forEach { assertFalse(it in text.lowercase(), "$it in $text") } }
        assertEquals("Account value in CAD, including cash. Sep 1, 2026 · CAD 1,850.00. Oct 8, 2026 · CAD 1,850.00. Missing observations are gaps.",
            PortfolioDisplay.chartDescription("CAD", "Sep 1, 2026 · CAD 1,850.00", "Oct 8, 2026 · CAD 1,850.00"))
    }

    @Test
    fun insightsAsOfIsReadableAndStaysUtc() {
        // Finding A-F14: was "As of 2026-10-08 21:15 UTC" from a string cut.
        assertEquals("As of Oct 8, 2026, 9:15 PM UTC", PortfolioDisplay.asOf("2026-10-08T21:15:00Z"))
        assertEquals("As of Oct 8, 2026, 12:00 AM UTC", PortfolioDisplay.asOf("2026-10-08T00:00:00Z"))
        assertEquals("As of 2026-10-08 21:15", PortfolioDisplay.asOf("2026-10-08 21:15"))
    }
}
