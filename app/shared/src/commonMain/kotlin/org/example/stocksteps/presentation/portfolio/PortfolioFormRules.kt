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

/** Phase D1 form sections: what and when, the amounts, then optional details collapsed under "More options". */
enum class PortfolioFormSection { DETAILS, AMOUNTS, MORE }

/** Result of the form check: [errors] sit next to their field; [message] is a form-level message no field explains. */
data class PortfolioFormCheck(val errors: Map<PortfolioFormField, String>, val message: String?) {
    val valid: Boolean get() = errors.isEmpty() && message == null
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

    /** Phase D1 on-screen order on both platforms: type, date, account and security; amounts; then the optional (advanced) fields. */
    val redesignOrder: List<PortfolioFormField> = listOf(PortfolioFormField.TRANSACTION_TYPE, PortfolioFormField.DATE, PortfolioFormField.ACCOUNT,
        PortfolioFormField.SYMBOL, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT, PortfolioFormField.FEES,
        PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.CASH_CURRENCY, PortfolioFormField.FX_RATE, PortfolioFormField.SETTLEMENT,
        PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE, PortfolioFormField.NOTES)
    private val AMOUNT_FIELDS = setOf(PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT, PortfolioFormField.FEES)

    fun section(field: PortfolioFormField): PortfolioFormSection = when {
        field.advanced -> PortfolioFormSection.MORE
        field in AMOUNT_FIELDS -> PortfolioFormSection.AMOUNTS
        else -> PortfolioFormSection.DETAILS
    }

    fun sectionTitle(section: PortfolioFormSection): String = when (section) {
        PortfolioFormSection.DETAILS -> PortfolioDisplay.FORM_SECTION_DETAILS
        PortfolioFormSection.AMOUNTS -> PortfolioDisplay.FORM_SECTION_AMOUNTS
        PortfolioFormSection.MORE -> PortfolioDisplay.FORM_SECTION_MORE
    }

    /** The visible fields of one section, in [redesignOrder]. */
    fun sectionFields(section: PortfolioFormSection, type: TransactionType, symbol: String, cashDiffers: Boolean): List<PortfolioFormField> =
        redesignOrder.filter { section(it) == section && shows(it, type, symbol, cashDiffers) }

    /** "More options" starts open when one of its fields holds a value (editing) or has an error, so nothing entered is hidden. */
    fun moreOptionsOpen(values: Map<PortfolioFormField, String>, errors: Set<PortfolioFormField>, tradeCurrency: String, cashCurrency: String): Boolean =
        cashCurrency != tradeCurrency || errors.any { section(it) == PortfolioFormSection.MORE } ||
            listOf(PortfolioFormField.FX_RATE, PortfolioFormField.SETTLEMENT, PortfolioFormField.NOTES).any { values[it].orEmpty().isNotBlank() }

    /** Sample scenarios are read-only: the form explains that instead of offering a save that would do nothing. */
    fun readOnly(state: org.example.stocksteps.portfolio.PortfolioUiState?): Boolean = state?.mockScenario != null

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
        // Phase D1: money fields name their currency; dates are chosen with a picker (the stored value stays YYYY-MM-DD).
        PortfolioFormField.ACCOUNT -> "Account"
        PortfolioFormField.TRANSACTION_TYPE -> "Transaction type"
        PortfolioFormField.TRADE_CURRENCY -> "Trade currency"
        PortfolioFormField.SYMBOL -> if (type in SHARE_TYPES) "Security symbol" else "Security symbol (optional for cash)"
        PortfolioFormField.COMPANY_NAME -> "Company name (optional)"
        PortfolioFormField.EXCHANGE -> "Exchange (optional)"
        PortfolioFormField.SHARES -> if (type == TransactionType.STOCK_SPLIT) "Split ratio: new / old" else "Shares (fractional supported)"
        PortfolioFormField.PRICE -> (if (type == TransactionType.OPENING_POSITION) "Average cost per share" else "Price per share") + " ($currency)"
        PortfolioFormField.AMOUNT -> "Amount ($currency)"
        PortfolioFormField.FEES -> "Fees ($currency, optional)"
        PortfolioFormField.DATE -> if (type == TransactionType.OPENING_POSITION) "Effective date" else "Trade date"
        PortfolioFormField.CASH_CURRENCY -> "Cash currency"
        PortfolioFormField.FX_RATE -> "Trade-date FX: one $currency in $cashCurrency"
        PortfolioFormField.SETTLEMENT -> "Settlement date (optional)"
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
     * The transaction both forms submit (blank numbers → "0", as the iOS bridge always did; typed numbers are passed exactly as entered, never
     * rounded or normalized); [accountId] is passed through untouched. Use [visibleTransaction] from a form so hidden fields send nothing.
     */
    fun transaction(
        id: String, accountId: String, type: TransactionType, date: String, currency: PortfolioCurrency,
        symbol: String, name: String, exchange: String, instrumentCurrency: String,
        quantity: String, price: String, amount: String, fees: String,
        cashCurrency: PortfolioCurrency, fxRate: String, settlement: String, notes: String
    ): PortfolioTransaction = PortfolioTransaction(id, accountId, type, date, currency,
        instrument = symbol.takeIf { it.isNotBlank() }?.let { InstrumentRef(it, name.ifBlank { null }, exchange.ifBlank { null }, instrumentCurrency) },
        quantity = quantity.ifBlank { "0" }, unitPrice = price.ifBlank { "0" }, grossAmount = amount.ifBlank { "0" }, fees = fees.ifBlank { "0" },
        notes = notes.ifBlank { null }, cashCurrency = cashCurrency, fxRate = fxRate.ifBlank { null }, settlementDate = settlement.ifBlank { null })

    /**
     * Phase D1: [transaction] with every field the selected type hides left empty, so switching Buy → Cash deposit never submits a stale share
     * count or price (and the FX rate only while the cash currency differs). Visible values are passed exactly as typed.
     */
    fun visibleTransaction(
        id: String, accountId: String, type: TransactionType, date: String, currency: PortfolioCurrency,
        symbol: String, name: String, exchange: String, instrumentCurrency: String,
        quantity: String, price: String, amount: String, fees: String,
        cashCurrency: PortfolioCurrency, fxRate: String, settlement: String, notes: String
    ): PortfolioTransaction {
        val visible = visibleFields(type, symbol, cashCurrency != currency).toSet()
        fun keep(field: PortfolioFormField, value: String) = if (field in visible) value else ""
        return transaction(id, accountId, type, date, currency, symbol, keep(PortfolioFormField.COMPANY_NAME, name), keep(PortfolioFormField.EXCHANGE, exchange),
            instrumentCurrency, keep(PortfolioFormField.SHARES, quantity), keep(PortfolioFormField.PRICE, price), keep(PortfolioFormField.AMOUNT, amount),
            fees, cashCurrency, keep(PortfolioFormField.FX_RATE, fxRate), settlement, notes)
    }

    /**
     * Phase D1 field-level check, shared by both forms. Number fields are checked with the same parser the ledger uses (`Decimal.parse`), dates
     * with `PortfolioEngine.validDate`; then `PortfolioEngine.validate` runs unchanged and its message is placed next to the field it is about.
     * No new rules: every message comes from those existing checks (number and date texts are explained in plain words).
     */
    fun check(transaction: PortfolioTransaction, inputs: Map<PortfolioFormField, String>): PortfolioFormCheck {
        val visible = visibleFields(transaction.type, transaction.instrument?.symbol.orEmpty(), transaction.cashCurrency != transaction.currency)
        val errors = linkedMapOf<PortfolioFormField, String>()
        visible.filter(::decimal).forEach { field ->
            val text = inputs[field].orEmpty()
            if (text.isNotBlank() && runCatching { org.example.stocksteps.portfolio.Decimal.parse(text) }.isFailure) errors[field] = PortfolioDisplay.FORM_NUMBER_INVALID
        }
        if (!org.example.stocksteps.portfolio.PortfolioEngine.validDate(transaction.tradeDate)) errors[PortfolioFormField.DATE] = PortfolioDisplay.FORM_DATE_INVALID
        transaction.settlementDate?.let { if (!org.example.stocksteps.portfolio.PortfolioEngine.validDate(it)) errors[PortfolioFormField.SETTLEMENT] = PortfolioDisplay.FORM_DATE_INVALID }
        if (errors.isNotEmpty()) return PortfolioFormCheck(errors, null)
        return try {
            org.example.stocksteps.portfolio.PortfolioEngine.validate(transaction)
            PortfolioFormCheck(emptyMap(), null)
        } catch (cause: IllegalArgumentException) {
            val message = cause.message ?: PortfolioDisplay.FORM_CHECK_FAILED
            fieldFor(message, transaction)?.let { PortfolioFormCheck(mapOf(it to message), null) } ?: PortfolioFormCheck(emptyMap(), message)
        }
    }

    /** The field a `PortfolioEngine.validate` message is about; null keeps it as a form-level message (unknown texts are never reworded). */
    internal fun fieldFor(message: String, tx: PortfolioTransaction): PortfolioFormField? {
        fun negative(value: String) = runCatching { org.example.stocksteps.portfolio.Decimal.parse(value) < org.example.stocksteps.portfolio.Decimal.ZERO }.getOrDefault(false)
        return when (message) {
            "Settlement cannot precede the trade." -> PortfolioFormField.SETTLEMENT
            "Shares, price and fees cannot be negative." -> when {
                negative(tx.quantity) -> PortfolioFormField.SHARES
                negative(tx.unitPrice) -> PortfolioFormField.PRICE
                else -> PortfolioFormField.FEES
            }
            "Amount cannot be negative.", "Amount must be positive." -> PortfolioFormField.AMOUNT
            "Choose a security and a positive share quantity." -> if (tx.instrument == null) PortfolioFormField.SYMBOL else PortfolioFormField.SHARES
            "Trade price must be positive." -> PortfolioFormField.PRICE
            "Record split-related fees as a separate fee transaction." -> PortfolioFormField.FEES
            "Invalid security symbol." -> PortfolioFormField.SYMBOL
            "FX rate must be positive." -> PortfolioFormField.FX_RATE
            "Notes are too long." -> PortfolioFormField.NOTES
            else -> null
        }
    }
}

/** Phase D1 date pickers: the stored value stays a plain YYYY-MM-DD day; pickers exchange whole days since 1970-01-01 (no time zone involved). */
object PortfolioFormDates {
    fun epochDay(date: String): Long? =
        if (org.example.stocksteps.portfolio.PortfolioEngine.validDate(date)) org.example.stocksteps.portfolio.analytics.AnalyticsDates.day(date).toLong() else null
    fun date(epochDay: Long): String = org.example.stocksteps.portfolio.analytics.AnalyticsDates.fromDay(epochDay.toInt())
    /** "Oct 8, 2026" for the field; the raw text when it isn't a valid day. */
    fun display(date: String): String = if (org.example.stocksteps.portfolio.PortfolioEngine.validDate(date)) PortfolioDates.date(date) else date
    /** The earliest day `PortfolioEngine.validDate` accepts (pickers start there; no other limit is added). */
    const val EARLIEST = "1900-01-01"
}
