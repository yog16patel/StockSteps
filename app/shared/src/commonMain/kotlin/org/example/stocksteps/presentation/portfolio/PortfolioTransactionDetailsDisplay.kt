package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioActionKind
import org.example.stocksteps.portfolio.PortfolioActionResult
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.TransactionType

/**
 * The leading visual of a transaction (Portfolio Phase D3): rows with a security show the holding's avatar (logo or ticker); cash rows show a
 * type icon. Keys map to `StockIcons` on Android and SF Symbols on iOS, like `PortfolioInsightIcons`.
 */
enum class PortfolioTransactionIcon { SECURITY, CASH, DIVIDEND, FEE, TRANSFER }

/**
 * One transaction row as text groups (Phase D3, shared by the dashboard preview and Transaction history on both platforms). [amount] is the
 * **recorded** amount — the server-normalized `grossAmount` (shares × price for trades, the entered amount otherwise) — or null when none is
 * recorded (sample fixtures and zero-value rows are never shown as "0.00"). Nothing is computed here.
 */
data class PortfolioTransactionRowText(
    val icon: PortfolioTransactionIcon,
    val title: String,
    val security: String?,
    val date: String,
    /** "USD 100.00 per share" for priced trades; null otherwise (the amount says it). */
    val unitPrice: String?,
    val amount: String?,
    val spoken: String
)

/** One labelled value in Transaction details. */
data class PortfolioDetailRow(val label: String, val value: String)

/** Transaction details (Phase D3): summary + the rows this type uses and actually records. */
data class PortfolioTransactionDetails(
    val icon: PortfolioTransactionIcon,
    val title: String,
    /** "AAPL · Apple Inc." when a security is recorded. */
    val security: String?,
    val date: String,
    /** Recorded amount with its currency; null when none is recorded (never estimated). */
    val amount: String?,
    val rows: List<PortfolioDetailRow>,
    val notes: String?,
    val spokenSummary: String
)

object PortfolioTransactionDetailsDisplay {
    const val TITLE = "Transaction details"
    const val DETAILS = "Details"
    const val NOTES = "Notes"
    const val EDIT = "Edit transaction"
    const val DELETE = "Delete transaction"
    const val OPEN = "Open transaction details"
    const val GONE_TITLE = "Transaction not available"
    const val GONE_MESSAGE = "This transaction is no longer in this account. It may have been deleted, moved to another account, or the account changed."
    const val BACK = "Back"
    const val DELETE_MESSAGE = "This will permanently remove this transaction from your portfolio. Balances will be recalculated. " +
        "Deletion is rejected if a later sale would exceed your shares."

    private val PRICED = setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT,
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)

    fun icon(tx: PortfolioTransaction): PortfolioTransactionIcon = when {
        tx.instrument != null -> PortfolioTransactionIcon.SECURITY
        tx.type == TransactionType.DIVIDEND -> PortfolioTransactionIcon.DIVIDEND
        tx.type == TransactionType.FEE -> PortfolioTransactionIcon.FEE
        tx.type == TransactionType.TRANSFER_IN || tx.type == TransactionType.TRANSFER_OUT -> PortfolioTransactionIcon.TRANSFER
        else -> PortfolioTransactionIcon.CASH
    }

    private fun decimal(text: String?) = text?.let { runCatching { Decimal.parse(it) }.getOrNull() }
    private fun nonZero(text: String?) = decimal(text)?.takeIf { it != Decimal.ZERO }
    private fun money(currency: String, value: String) = "$currency ${PortfolioFormat.amount(value)}"

    /** The recorded amount ("USD 1,000.00"), or null when the ledger holds none (0 / unparseable). */
    fun amount(tx: PortfolioTransaction): String? = nonZero(tx.grossAmount)?.let { money(tx.currency.name, tx.grossAmount) }

    fun row(tx: PortfolioTransaction): PortfolioTransactionRowText {
        val title = PortfolioPresentation.transactionLabel(tx.type)
        val security = PortfolioPresentation.transactionDetail(tx)
        val date = PortfolioPresentation.dateLabel(tx.tradeDate)
        val unitPrice = nonZero(tx.unitPrice)?.takeIf { tx.type in PRICED }?.let { "${money(tx.currency.name, tx.unitPrice)} per share" }
        val amount = amount(tx)
        val spoken = listOfNotNull(title, security, date, unitPrice, amount?.let { "amount $it" }).joinToString(", ").replace(' ', ' ')
        return PortfolioTransactionRowText(icon(tx), title, security, date, unitPrice, amount, spoken)
    }

    /**
     * Details rows, in reading order, for the fields this type uses (`PortfolioFormRules.visibleFields`, the form's own rule — a cash deposit has
     * no shares or price) and only when recorded. Amounts carry their currency. Missing values have no row; nothing is derived.
     */
    fun of(tx: PortfolioTransaction, accounts: List<PortfolioAccount>): PortfolioTransactionDetails {
        val currency = tx.currency.name
        val symbol = tx.instrument?.symbol.orEmpty()
        val visible = PortfolioFormRules.visibleFields(tx.type, symbol, tx.cashCurrency != tx.currency).toSet()
        val opening = tx.type == TransactionType.OPENING_POSITION
        val rows = buildList {
            add(PortfolioDetailRow("Transaction type", PortfolioPresentation.transactionLabel(tx.type)))
            add(PortfolioDetailRow(if (opening) "Effective date" else "Trade date", PortfolioPresentation.dateLabel(tx.tradeDate)))
            tx.settlementDate?.takeIf { it.isNotBlank() }?.let { add(PortfolioDetailRow("Settlement date", PortfolioPresentation.dateLabel(it))) }
            accounts.firstOrNull { it.id == tx.accountId }?.let {
                add(PortfolioDetailRow("Account", "${PortfolioPresentation.accountLabel(it)}\n${PortfolioPresentation.accountDetail(it)}"))
            }
            tx.instrument?.let { instrument ->
                add(PortfolioDetailRow("Security symbol", instrument.symbol))
                instrument.name?.takeIf { it.isNotBlank() }?.let { add(PortfolioDetailRow("Company name", it)) }
                instrument.exchange?.takeIf { it.isNotBlank() }?.let { add(PortfolioDetailRow("Exchange", it)) }
            }
            if (PortfolioFormField.SHARES in visible) nonZero(tx.quantity)?.let { add(PortfolioDetailRow("Shares", tx.quantity)) }
            if (PortfolioFormField.PRICE in visible) nonZero(tx.unitPrice)?.let {
                add(PortfolioDetailRow(if (opening) "Average cost per share" else "Price per share", money(currency, tx.unitPrice)))
            }
            nonZero(tx.grossAmount)?.let { add(PortfolioDetailRow("Amount before fees", money(currency, tx.grossAmount))) }
            if (PortfolioFormField.FEES in visible) decimal(tx.fees)?.let { add(PortfolioDetailRow("Fees", money(currency, tx.fees))) }
            nonZero(tx.netAmount)?.let { add(PortfolioDetailRow("Net amount", money(currency, tx.netAmount))) }
            add(PortfolioDetailRow("Currency", currency))
            if (tx.cashCurrency != tx.currency) {
                add(PortfolioDetailRow("Cash currency", tx.cashCurrency.name))
                tx.fxRate?.takeIf { it.isNotBlank() }?.let { add(PortfolioDetailRow("FX rate", it)) }
            }
        }
        val title = PortfolioPresentation.transactionLabel(tx.type)
        val security = tx.instrument?.let { i -> listOfNotNull(i.symbol, i.name?.takeIf { it.isNotBlank() }).joinToString(" · ") }
        val date = PortfolioPresentation.dateLabel(tx.tradeDate)
        val amount = amount(tx)
        return PortfolioTransactionDetails(icon(tx), title, security, date, amount, rows, tx.notes?.takeIf { it.isNotBlank() },
            listOfNotNull(title, security, date, amount).joinToString(", ").replace(' ', ' '))
    }
}

/**
 * Phase D3 success feedback from the presenter's confirmed [PortfolioActionResult]. A screen keeps the last id it handled (Android
 * `rememberSaveable`, iOS `@State`) and starts from the id current when it appeared, so a result shows once — never again after rotation,
 * recomposition or navigating back.
 */
object PortfolioActionFeedback {
    fun message(kind: PortfolioActionKind): String = when (kind) {
        PortfolioActionKind.ADDED -> "Transaction added"
        PortfolioActionKind.UPDATED -> "Transaction updated"
        PortfolioActionKind.DELETED -> "Transaction deleted"
    }

    /** The id a screen starts from: everything confirmed before it appeared counts as seen. */
    fun baseline(result: PortfolioActionResult?): Long = result?.id ?: 0

    /** True when [result] is newer than the last one this screen handled. */
    fun isNew(result: PortfolioActionResult?, seen: Long): Boolean = result != null && result.id > seen

    /** How long the banner stays before it hides itself (it can also be dismissed). */
    const val VISIBLE_MILLIS = 4_000L
}

/**
 * Which confirmed result has already been announced (Phase D3). One instance lives next to the presenter in `AccountDependencies`, so it is
 * shared by every Portfolio screen on both platforms and survives Android rotation. The **visible** screen takes a result once; a screen that
 * leaves for the result (Transaction details after its own delete, the Add screen) doesn't take it, so the screen it returns to announces it.
 * Ids come from one presenter instance (same lifetime as this tracker), so "different from the last one shown" means "not shown yet".
 */
class PortfolioFeedbackTracker {
    private var shown: Long = 0

    /** The message for [result] if no screen has shown it yet (marking it shown), else null. */
    fun take(result: PortfolioActionResult?): String? {
        if (result == null || result.id == shown) return null
        shown = result.id
        return PortfolioActionFeedback.message(result.kind)
    }

    /** Marks [result] as handled without showing it (an action finished where Portfolio isn't on screen). */
    fun skip(result: PortfolioActionResult?) { result?.let { shown = it.id } }
}
