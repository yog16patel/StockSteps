package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioEngine
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.TransactionType

/** Transaction history filters (Portfolio Phase D2), in display order. Display only: they never change what the ledger holds. */
enum class PortfolioTransactionFilter { ALL, BUYS, SELLS, DIVIDENDS, CASH, OTHER }

/** One month of the history: "October 2026" and its rows in the presenter's order. [key] is "YYYY-MM" ("" for rows without a valid date). */
data class PortfolioTransactionMonth(val key: String, val title: String, val transactions: List<PortfolioTransaction>)

/**
 * Transaction history rules shared by Compose and SwiftUI (Portfolio Phase D2). Everything here is a display filter or grouping of
 * `PortfolioUiState.transactions`, which the presenter already sorts newest first: rows are never re-sorted, and nothing is summed, netted or
 * computed — counts are row counts, amounts stay the existing row text (`PortfolioPresentation.transactionText`).
 *
 * Opening positions are deliberately **not** "Buys" (owner decision, 2026-10-13): they record holdings that existed before tracking started,
 * not a purchase made here, so they sit under Other, which says so in its note.
 */
object PortfolioTransactionsDisplay {
    const val TITLE = "Transactions"
    const val SEE_ALL = "See all transactions"
    const val SHOW_ALL_FILTER = "Show all transactions"
    const val SHOW_ALL_HOLDINGS = "Show all holdings"
    const val FILTERS_LABEL = "Transaction type"
    const val UNDATED = "Date not recorded"

    val filters: List<PortfolioTransactionFilter> = PortfolioTransactionFilter.entries

    fun label(filter: PortfolioTransactionFilter): String = when (filter) {
        PortfolioTransactionFilter.ALL -> "All"
        PortfolioTransactionFilter.BUYS -> "Buys"
        PortfolioTransactionFilter.SELLS -> "Sells"
        PortfolioTransactionFilter.DIVIDENDS -> "Dividends"
        PortfolioTransactionFilter.CASH -> "Cash"
        PortfolioTransactionFilter.OTHER -> "Other"
    }

    /** The one filter a ledger type belongs to. Exhaustive on purpose: a new [TransactionType] must be placed here before it compiles. */
    fun filterOf(type: TransactionType): PortfolioTransactionFilter = when (type) {
        TransactionType.BUY -> PortfolioTransactionFilter.BUYS
        TransactionType.SELL -> PortfolioTransactionFilter.SELLS
        TransactionType.DIVIDEND, TransactionType.DIVIDEND_REINVESTMENT -> PortfolioTransactionFilter.DIVIDENDS
        TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.CASH_ADJUSTMENT -> PortfolioTransactionFilter.CASH
        TransactionType.OPENING_POSITION, TransactionType.FEE, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT,
        TransactionType.STOCK_SPLIT -> PortfolioTransactionFilter.OTHER
    }

    /** What a filter contains, in plain words (shown under the pills; null for All, Buys and Sells, which need no explanation). */
    fun note(filter: PortfolioTransactionFilter): String? = when (filter) {
        PortfolioTransactionFilter.DIVIDENDS -> "Cash dividends and reinvested dividends."
        PortfolioTransactionFilter.CASH -> "Deposits, withdrawals and cash adjustments."
        PortfolioTransactionFilter.OTHER -> "Opening positions, fees, transfers and stock splits."
        else -> null
    }

    /**
     * The holding scope that still applies: [symbol] only while the account it was opened for ([scopeAccountId]) is the selected one. Switching
     * account drops it, so the history never shows an empty list for a holding that belongs to another account.
     */
    fun activeSymbol(symbol: String?, scopeAccountId: String?, selectedAccountId: String?): String? =
        symbol?.takeIf { it.isNotBlank() && (scopeAccountId == null || scopeAccountId == selectedAccountId) }

    /** Rows matching [filter] (and the holding [symbol], when given) in the presenter's order. Cash rows have no symbol, so a holding excludes them. */
    fun filter(transactions: List<PortfolioTransaction>, filter: PortfolioTransactionFilter, symbol: String? = null): List<PortfolioTransaction> =
        transactions.filter { tx ->
            (symbol == null || tx.instrument?.symbol == symbol) && (filter == PortfolioTransactionFilter.ALL || filterOf(tx.type) == filter)
        }

    /**
     * Month groups from the stored `YYYY-MM-DD` text (no time zone, like `PortfolioFormDates`). Groups appear in the order their first row
     * appears — newest first, since the presenter sorts that way — and rows keep their order inside a group. A row whose date isn't valid is
     * never guessed into a month: it goes to a final [UNDATED] group.
     */
    fun groups(transactions: List<PortfolioTransaction>): List<PortfolioTransactionMonth> {
        val dated = LinkedHashMap<String, MutableList<PortfolioTransaction>>()
        val undated = mutableListOf<PortfolioTransaction>()
        transactions.forEach { tx ->
            if (PortfolioEngine.validDate(tx.tradeDate)) dated.getOrPut(tx.tradeDate.take(7)) { mutableListOf() } += tx else undated += tx
        }
        return dated.map { (key, rows) -> PortfolioTransactionMonth(key, monthTitle(key), rows) } +
            listOfNotNull(undated.takeIf { it.isNotEmpty() }?.let { PortfolioTransactionMonth("", UNDATED, it) })
    }

    /** "2026-10" → "October 2026". */
    fun monthTitle(key: String): String {
        val month = key.drop(5).take(2).toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return UNDATED
        return "$month ${key.take(4)}"
    }

    /** "12 transactions", "1 buy", "3 cash movements": a row count, never an amount. */
    fun summary(count: Int, filter: PortfolioTransactionFilter): String {
        val (one, many) = nouns(filter)
        return "$count ${if (count == 1) one else many}"
    }

    /** Empty filter: "No sells in this account yet." / "No dividends for AAPL in this account yet." */
    fun emptyMessage(filter: PortfolioTransactionFilter, symbol: String? = null): String =
        "No ${nouns(filter).second}${symbol?.let { " for $it" } ?: ""} in this account yet."

    /** Announced when the filter changes: "Sells: 3 sells". */
    fun spokenFilter(filter: PortfolioTransactionFilter, count: Int): String = "${label(filter)}: ${summary(count, filter)}"

    /** Holding scope line: "AAPL transactions only". */
    fun scopeLine(symbol: String): String = "$symbol transactions only"

    /** Dashboard / Holding details link: "See all transactions (12)". */
    fun seeAll(count: Int): String = "$SEE_ALL ($count)"

    private fun nouns(filter: PortfolioTransactionFilter): Pair<String, String> = when (filter) {
        PortfolioTransactionFilter.ALL -> "transaction" to "transactions"
        PortfolioTransactionFilter.BUYS -> "buy" to "buys"
        PortfolioTransactionFilter.SELLS -> "sell" to "sells"
        PortfolioTransactionFilter.DIVIDENDS -> "dividend" to "dividends"
        PortfolioTransactionFilter.CASH -> "cash movement" to "cash movements"
        PortfolioTransactionFilter.OTHER -> "other transaction" to "other transactions"
    }

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
}
