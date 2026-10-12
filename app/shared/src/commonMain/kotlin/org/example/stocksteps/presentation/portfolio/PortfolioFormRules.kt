package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.TransactionType

/**
 * Transaction form fields in today's on-screen order (declaration order). [advanced] marks the fields the Phase D layout will group under
 * "Advanced"; Phase A doesn't use it for layout yet.
 */
enum class PortfolioFormField(val advanced: Boolean) {
    ACCOUNT(false), TRANSACTION_TYPE(false), TRADE_CURRENCY(true), SYMBOL(false), COMPANY_NAME(true), EXCHANGE(true), SHARES(false), PRICE(false), AMOUNT(false),
    FEES(false), DATE(false), CASH_CURRENCY(true), FX_RATE(true), SETTLEMENT(true), NOTES(true)
}

/** One account in a form picker: [id] is what is submitted; [label] is unique within its list (display only). */
data class PortfolioAccountOption(val id: String, val label: String, val detail: String)

/**
 * Display rules for the Portfolio transaction form, shared by Compose and SwiftUI (Portfolio UI Refinement Phase A,
 * `docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md`). Accounts are always chosen and submitted by id — names aren't unique (finding A-F1).
 * Nothing here validates or calculates: `PortfolioEngine.validate` (client and server) stays the only rule set.
 */
object PortfolioFormRules {
    /** Types that move shares; mirrors `PortfolioEngine.validate` (transfers move shares only when they name a security). */
    private val SHARE_TYPES = setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION,
        TransactionType.DIVIDEND_REINVESTMENT, TransactionType.STOCK_SPLIT)
    private val TRANSFERS = setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
    private val DECIMAL_FIELDS = setOf(PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT,
        PortfolioFormField.FEES, PortfolioFormField.FX_RATE)

    /** The three everyday choices, offered first. */
    val primaryTypes: List<TransactionType> = listOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION)
    /** Every other type, in ledger order ("More types" in the Phase D layout). */
    val otherTypes: List<TransactionType> = TransactionType.entries.filterNot { it in primaryTypes }
    /** Picker order today: primary types, then the rest. */
    val typeOptions: List<TransactionType> = primaryTypes + otherTypes
    val categoryOptions: List<PortfolioCategory> = PortfolioCategory.entries
    val currencyOptions: List<PortfolioCurrency> = PortfolioCurrency.entries

    /** Planned Phase D order (security first, then type, date and amounts, advanced last). Not used by any screen in Phase A. */
    val redesignOrder: List<PortfolioFormField> = listOf(PortfolioFormField.ACCOUNT, PortfolioFormField.SYMBOL, PortfolioFormField.TRANSACTION_TYPE,
        PortfolioFormField.DATE, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT, PortfolioFormField.FEES,
        PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.CASH_CURRENCY, PortfolioFormField.FX_RATE, PortfolioFormField.SETTLEMENT,
        PortfolioFormField.NOTES, PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE)

    /** A ledger type by its enum name (SwiftUI pickers store names); null for anything else. */
    fun type(name: String): TransactionType? = TransactionType.entries.firstOrNull { it.name == name }

    /** True when shares and price are asked instead of an amount — the same condition `PortfolioEngine.validate` uses. */
    fun movesShares(type: TransactionType, symbol: String): Boolean = type in SHARE_TYPES || type in TRANSFERS && symbol.isNotBlank()

    /**
     * Whether [field] is shown for [type]. Shares/price vs amount follow [movesShares]; company name and exchange appear once the type
     * needs a security or one was entered; the FX rate only when the cash currency differs from the trade currency.
     */
    fun shows(field: PortfolioFormField, type: TransactionType, symbol: String, cashDiffers: Boolean): Boolean = when (field) {
        PortfolioFormField.SHARES, PortfolioFormField.PRICE -> movesShares(type, symbol)
        PortfolioFormField.AMOUNT -> !movesShares(type, symbol)
        PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE -> type in SHARE_TYPES || symbol.isNotBlank()
        PortfolioFormField.FX_RATE -> cashDiffers
        else -> true
    }

    /** The visible fields in on-screen order. */
    fun visibleFields(type: TransactionType, symbol: String, cashDiffers: Boolean): List<PortfolioFormField> =
        PortfolioFormField.entries.filter { shows(it, type, symbol, cashDiffers) }

    /** Fields that take a decimal number (decimal keyboard on both platforms; the text itself is never rounded or normalized). */
    fun decimal(field: PortfolioFormField): Boolean = field in DECIMAL_FIELDS

    /** Field label, the same on both platforms. */
    fun label(field: PortfolioFormField, type: TransactionType, currency: String, cashCurrency: String): String = when (field) {
        PortfolioFormField.ACCOUNT -> "Account"
        PortfolioFormField.TRANSACTION_TYPE -> "Transaction type"
        PortfolioFormField.TRADE_CURRENCY -> "Trade currency"
        PortfolioFormField.SYMBOL -> if (type in SHARE_TYPES) "Security symbol" else "Security symbol (optional for cash)"
        PortfolioFormField.COMPANY_NAME -> "Company name (optional)"
        PortfolioFormField.EXCHANGE -> "Exchange (optional)"
        PortfolioFormField.SHARES -> if (type == TransactionType.STOCK_SPLIT) "Split ratio: new / old" else "Shares (fractional supported)"
        PortfolioFormField.PRICE -> if (type == TransactionType.OPENING_POSITION) "Average cost per share" else "Price per share"
        PortfolioFormField.AMOUNT -> "Amount"
        PortfolioFormField.FEES -> "Fees"
        PortfolioFormField.DATE -> "Effective / trade date · YYYY-MM-DD"
        PortfolioFormField.CASH_CURRENCY -> "Cash currency"
        PortfolioFormField.FX_RATE -> "Trade-date FX: one $currency in $cashCurrency"
        PortfolioFormField.SETTLEMENT -> "Settlement date (optional) · YYYY-MM-DD"
        PortfolioFormField.NOTES -> "Notes (optional)"
    }

    /**
     * Picker options: active accounts, plus the [currentId] account when it is archived (editing one of its transactions must keep showing
     * it). Accounts with the same "Name · CUR" get their category and then their position appended so every option reads differently.
     */
    fun accountOptions(accounts: List<PortfolioAccount>, currentId: String? = null): List<PortfolioAccountOption> {
        val shown = accounts.filter { !it.archived || it.id == currentId }
        val base = shown.map { PortfolioPresentation.accountLabel(it) }
        val withCategory = shown.mapIndexed { index, account ->
            if (base.count { it == base[index] } > 1) "${base[index]} · ${PortfolioPresentation.categoryLabel(account.category)}" else base[index]
        }
        return shown.mapIndexed { index, account ->
            val label = withCategory[index]
            val same = withCategory.indices.filter { withCategory[it] == label }
            val unique = if (same.size > 1) "$label (${same.indexOf(index) + 1} of ${same.size})" else label
            val detail = PortfolioPresentation.accountDetail(account) + if (account.archived) " · archived" else ""
            PortfolioAccountOption(account.id, unique, detail)
        }
    }

    /**
     * The account id the form submits. [current] is the form's saved choice: `null` = not chosen yet → the dashboard's [default] account when
     * it is active (or still `null`); an id → kept while that account exists, `""` once it was deleted (the user must choose again — a
     * transaction is never moved silently to another account); `""` stays `""`.
     */
    fun resolveAccountId(current: String?, accounts: List<PortfolioAccount>, default: String?): String? = when {
        current == null -> default?.takeIf { id -> accounts.any { it.id == id && !it.archived } }
        current.isEmpty() -> ""
        accounts.any { it.id == current } -> current
        else -> ""
    }

    /**
     * The transaction the Android form submits, built exactly as before Phase A (blank numbers → "0"; fees as entered) so validation and
     * persistence are unchanged; [accountId] is passed through untouched.
     */
    fun transaction(
        id: String, accountId: String, type: TransactionType, date: String, currency: PortfolioCurrency,
        symbol: String, name: String, exchange: String, instrumentCurrency: String,
        quantity: String, price: String, amount: String, fees: String,
        cashCurrency: PortfolioCurrency, fxRate: String, settlement: String, notes: String
    ): PortfolioTransaction = PortfolioTransaction(id, accountId, type, date, currency,
        instrument = symbol.takeIf { it.isNotBlank() }?.let { InstrumentRef(it, name.ifBlank { null }, exchange.ifBlank { null }, instrumentCurrency) },
        quantity = quantity.ifBlank { "0" }, unitPrice = price.ifBlank { "0" }, grossAmount = amount.ifBlank { "0" }, fees = fees,
        notes = notes.ifBlank { null }, cashCurrency = cashCurrency, fxRate = fxRate.ifBlank { null }, settlementDate = settlement.ifBlank { null })
}
