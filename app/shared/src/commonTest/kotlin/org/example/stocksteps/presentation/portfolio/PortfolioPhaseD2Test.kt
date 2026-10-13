package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase D2: transaction history filters, holding scope, month grouping and copy — display only, presenter order kept. */
class PortfolioPhaseD2Test {
    private fun tx(id: String, type: TransactionType, date: String, symbol: String? = "AAPL", createdAt: Long = 0) =
        PortfolioTransaction(id, "acc-1", type, date, PortfolioCurrency.USD, symbol?.let { InstrumentRef(it, null, null, "USD") },
            quantity = "1", unitPrice = "10", netAmount = "-10", createdAt = createdAt)

    // Presenter order: newest trade date first, then newest createdAt (PortfolioPresenter sorts this way; the display never re-sorts).
    private val ledger = listOf(
        tx("t1", TransactionType.BUY, "2026-10-13", createdAt = 9),
        tx("t2", TransactionType.CASH_DEPOSIT, "2026-10-10", symbol = null),
        tx("t3", TransactionType.BUY, "2026-10-10", symbol = "TD.TO", createdAt = 7),
        tx("t4", TransactionType.DIVIDEND, "2026-09-15"),
        tx("t5", TransactionType.SELL, "2026-09-01", symbol = "TD.TO"),
        tx("t6", TransactionType.OPENING_POSITION, "2026-01-02", symbol = "GOOGL"),
        tx("t7", TransactionType.DIVIDEND_REINVESTMENT, "2025-12-31"),
        tx("t8", TransactionType.FEE, "2025-12-01", symbol = null),
    )
    private fun ids(list: List<PortfolioTransaction>) = list.map { it.id }

    @Test
    fun everyTypeBelongsToExactlyOneRealFilterAndAllContainsEverything() {
        TransactionType.entries.forEach { type ->
            val filter = PortfolioTransactionsDisplay.filterOf(type)
            assertTrue(filter != PortfolioTransactionFilter.ALL, "$type must have a specific filter")
            val row = listOf(tx("x", type, "2026-10-01"))
            val matching = PortfolioTransactionsDisplay.filters.filter { PortfolioTransactionsDisplay.filter(row, it).isNotEmpty() }
            assertEquals(listOf(PortfolioTransactionFilter.ALL, filter), matching, "$type")
        }
        assertEquals(ids(ledger), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.ALL)))
    }

    @Test
    fun openingPositionsAreNotBuys() {
        assertEquals(PortfolioTransactionFilter.OTHER, PortfolioTransactionsDisplay.filterOf(TransactionType.OPENING_POSITION))
        assertEquals(listOf("t1", "t3"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.BUYS)))
        assertEquals(listOf("t6", "t8"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.OTHER)))
        assertTrue(PortfolioTransactionsDisplay.note(PortfolioTransactionFilter.OTHER)!!.startsWith("Opening positions"))
    }

    @Test
    fun filtersMatchTheirTypesAndKeepPresenterOrder() {
        assertEquals(listOf("t5"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.SELLS)))
        assertEquals(listOf("t4", "t7"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.DIVIDENDS)))
        assertEquals(listOf("t2"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.CASH)))
        // Same trade date, older createdAt first as given: the display keeps that order instead of re-sorting it.
        val sameDay = listOf(tx("b", TransactionType.BUY, "2026-10-10", createdAt = 1), tx("a", TransactionType.BUY, "2026-10-10", createdAt = 5))
        assertEquals(listOf("b", "a"), ids(PortfolioTransactionsDisplay.filter(sameDay, PortfolioTransactionFilter.BUYS)))
    }

    @Test
    fun holdingScopeCombinesWithEveryFilterAndExcludesCashRows() {
        assertEquals(listOf("t1", "t4", "t7"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.ALL, "AAPL")))
        assertEquals(listOf("t1"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.BUYS, "AAPL")))
        assertEquals(listOf("t5"), ids(PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.SELLS, "TD.TO")))
        assertEquals(emptyList(), PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.CASH, "AAPL"))
    }

    @Test
    fun holdingScopeIsDroppedWhenAnotherAccountIsSelected() {
        assertEquals("AAPL", PortfolioTransactionsDisplay.activeSymbol("AAPL", "acc-1", "acc-1"))
        assertNull(PortfolioTransactionsDisplay.activeSymbol("AAPL", "acc-1", "acc-2"))
        assertNull(PortfolioTransactionsDisplay.activeSymbol("AAPL", "acc-1", null))
        assertNull(PortfolioTransactionsDisplay.activeSymbol("", "acc-1", "acc-1"))
        assertNull(PortfolioTransactionsDisplay.activeSymbol(null, "acc-1", "acc-1"))
        assertEquals("AAPL", PortfolioTransactionsDisplay.activeSymbol("AAPL", null, "acc-9")) // no account given: scope by symbol only
    }

    @Test
    fun monthsAreGroupedNewestFirstAcrossAYearBoundary() {
        val groups = PortfolioTransactionsDisplay.groups(ledger)
        assertEquals(listOf("October 2026", "September 2026", "January 2026", "December 2025"), groups.map { it.title })
        assertEquals(listOf(listOf("t1", "t2", "t3"), listOf("t4", "t5"), listOf("t6"), listOf("t7", "t8")), groups.map { ids(it.transactions) })
        assertEquals(ids(ledger), groups.flatMap { ids(it.transactions) }) // grouping never drops or reorders a row
    }

    @Test
    fun singleMonthAndEmptyLists() {
        assertEquals(listOf("October 2026"), PortfolioTransactionsDisplay.groups(ledger.take(3)).map { it.title })
        assertEquals(emptyList(), PortfolioTransactionsDisplay.groups(emptyList()))
    }

    @Test
    fun undatedRowsAreNeverGuessedIntoAMonth() {
        val rows = listOf(tx("bad", TransactionType.BUY, "2026-13-40"), tx("ok", TransactionType.BUY, "2026-10-01"), tx("blank", TransactionType.SELL, ""))
        val groups = PortfolioTransactionsDisplay.groups(rows)
        assertEquals(listOf("October 2026", PortfolioTransactionsDisplay.UNDATED), groups.map { it.title })
        assertEquals(listOf("bad", "blank"), ids(groups.last().transactions))
        assertEquals("", groups.last().key)
        assertEquals(PortfolioTransactionsDisplay.UNDATED, PortfolioTransactionsDisplay.monthTitle("2026-xx"))
    }

    @Test
    fun copyIsCountsAndPlainWordsNeverAmounts() {
        assertEquals("1 transaction", PortfolioTransactionsDisplay.summary(1, PortfolioTransactionFilter.ALL))
        assertEquals("12 transactions", PortfolioTransactionsDisplay.summary(12, PortfolioTransactionFilter.ALL))
        assertEquals("3 buys", PortfolioTransactionsDisplay.summary(3, PortfolioTransactionFilter.BUYS))
        assertEquals("1 cash movement", PortfolioTransactionsDisplay.summary(1, PortfolioTransactionFilter.CASH))
        assertEquals("No sells in this account yet.", PortfolioTransactionsDisplay.emptyMessage(PortfolioTransactionFilter.SELLS))
        assertEquals("No dividends for AAPL in this account yet.", PortfolioTransactionsDisplay.emptyMessage(PortfolioTransactionFilter.DIVIDENDS, "AAPL"))
        assertEquals("Sells: 0 sells", PortfolioTransactionsDisplay.spokenFilter(PortfolioTransactionFilter.SELLS, 0))
        assertEquals("See all transactions (5)", PortfolioTransactionsDisplay.seeAll(5))
        assertEquals("AAPL transactions only", PortfolioTransactionsDisplay.scopeLine("AAPL"))
        val all = PortfolioTransactionsDisplay.filters.flatMap {
            listOf(PortfolioTransactionsDisplay.label(it), PortfolioTransactionsDisplay.summary(2, it), PortfolioTransactionsDisplay.emptyMessage(it, "AAPL"),
                PortfolioTransactionsDisplay.note(it).orEmpty())
        }
        all.forEach { text ->
            assertFalse(Regex("[A-Z]{2,}_[A-Z]").containsMatchIn(text), "raw enum name in \"$text\"")
            assertFalse(Regex("(USD|CAD)|\\d+\\.\\d{2}").containsMatchIn(text), "amount in \"$text\"")
        }
        assertEquals(listOf("All", "Buys", "Sells", "Dividends", "Cash", "Other"), PortfolioTransactionsDisplay.filters.map(PortfolioTransactionsDisplay::label))
    }

    @Test
    fun filteringAndGroupingLeaveRowsUntouched() {
        val before = ledger.map { it.copy() }
        PortfolioTransactionFilter.entries.forEach { PortfolioTransactionsDisplay.filter(ledger, it, "AAPL") }
        PortfolioTransactionsDisplay.groups(ledger)
        assertEquals(before, ledger)
        // A filtered row is the same object, so its row text (amounts included) is identical to the dashboard's.
        val row = PortfolioTransactionsDisplay.filter(ledger, PortfolioTransactionFilter.BUYS).first()
        assertTrue(row === ledger.first())
        assertEquals(PortfolioPresentation.transactionText(ledger.first()), PortfolioPresentation.transactionText(row))
    }

    @Test
    fun samplePortfoliosAreReadOnlySoDeleteIsHidden() {
        assertTrue(PortfolioFormRules.readOnly(PortfolioUiState(mockScenario = "multiple-buys")))
        assertFalse(PortfolioFormRules.readOnly(PortfolioUiState()))
    }
}
