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
    ACCOUNT(false), TRANSACTION_TYPE(false), TRADE_CURRENCY(false), SYMBOL(false), COMPANY_NAME(true), EXCHANGE(true), SHARES(false), PRICE(false), AMOUNT(false),
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

    /**
     * Phase D1 on-screen order on both platforms: type, date, account and security; amounts; then the optional (advanced) fields. E.2: the trade
     * currency opens "Amounts" (it was under the collapsed "More options", so a typed symbol's USD default went unnoticed in a CAD account).
     */
    val redesignOrder: List<PortfolioFormField> = listOf(PortfolioFormField.TRANSACTION_TYPE, PortfolioFormField.DATE, PortfolioFormField.ACCOUNT,
        PortfolioFormField.SYMBOL, PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT,
        PortfolioFormField.FEES, PortfolioFormField.CASH_CURRENCY, PortfolioFormField.FX_RATE, PortfolioFormField.SETTLEMENT,
        PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE, PortfolioFormField.NOTES)
    private val AMOUNT_FIELDS = setOf(PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT, PortfolioFormField.FEES)

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
     * Field-level check, shared by both forms (D1, refined in D1.1). Number fields are checked with the same parser the ledger uses
     * (`Decimal.parse`), dates with `PortfolioEngine.validDate`; then `PortfolioEngine.validate` runs **unchanged**. It stops at its first failed
     * rule, so to show every problem at once the check re-runs it: each failure is placed under its field, that field is given a stand-in
     * value that satisfies the rule just reported, and `validate` runs again (at most once per field). The stand-ins exist only inside this
     * check — what is submitted is always exactly what was typed. Messages that name two fields are split per field ([guidance]); an
     * unknown message stays a form-level message, unaltered. No new rules.
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
        var probe = transaction
        repeat(PortfolioFormField.entries.size) {
            try {
                org.example.stocksteps.portfolio.PortfolioEngine.validate(probe)
                return PortfolioFormCheck(errors, null)
            } catch (cause: IllegalArgumentException) {
                val message = cause.message ?: PortfolioDisplay.FORM_CHECK_FAILED
                val found = guidance(message, probe).filterKeys { it !in errors }
                if (found.isEmpty()) return PortfolioFormCheck(errors, if (errors.isEmpty()) message else null)
                errors.putAll(found)
                probe = standIn(probe, found.keys)
            }
        }
        return PortfolioFormCheck(errors, null)
    }

    /**
     * Field guidance for one `PortfolioEngine.validate` message (empty for unknown messages). A message about two fields ("Choose a security and
     * a positive share quantity.") becomes one sentence per field that actually fails it, using the same values the engine checked.
     */
    internal fun guidance(message: String, tx: PortfolioTransaction): Map<PortfolioFormField, String> {
        fun value(text: String) = runCatching { org.example.stocksteps.portfolio.Decimal.parse(text) }.getOrNull()
        fun negative(text: String) = value(text)?.let { it < org.example.stocksteps.portfolio.Decimal.ZERO } == true
        return when (message) {
            "Settlement cannot precede the trade." -> mapOf(PortfolioFormField.SETTLEMENT to message)
            "Shares, price and fees cannot be negative." -> buildMap {
                if (negative(tx.quantity)) put(PortfolioFormField.SHARES, PortfolioDisplay.FORM_NOT_NEGATIVE)
                if (negative(tx.unitPrice)) put(PortfolioFormField.PRICE, PortfolioDisplay.FORM_NOT_NEGATIVE)
                if (negative(tx.fees)) put(PortfolioFormField.FEES, PortfolioDisplay.FORM_NOT_NEGATIVE)
            }
            "Amount cannot be negative.", "Amount must be positive." -> mapOf(PortfolioFormField.AMOUNT to message)
            "Choose a security and a positive share quantity." -> buildMap {
                if (tx.instrument == null) put(PortfolioFormField.SYMBOL, PortfolioDisplay.FORM_CHOOSE_SECURITY)
                if ((value(tx.quantity) ?: org.example.stocksteps.portfolio.Decimal.ZERO) <= org.example.stocksteps.portfolio.Decimal.ZERO)
                    put(PortfolioFormField.SHARES, PortfolioDisplay.FORM_POSITIVE_SHARES)
            }
            "Trade price must be positive." -> mapOf(PortfolioFormField.PRICE to message)
            "Record split-related fees as a separate fee transaction." -> mapOf(PortfolioFormField.FEES to message)
            "Invalid security symbol." -> mapOf(PortfolioFormField.SYMBOL to PortfolioDisplay.FORM_SYMBOL_INVALID)
            "FX rate must be positive." -> mapOf(PortfolioFormField.FX_RATE to message)
            "Notes are too long." -> mapOf(PortfolioFormField.NOTES to message)
            else -> emptyMap()
        }
    }

    /** Check-only stand-ins that satisfy the rule just reported for [fields], so the next `validate` run reveals the next problem. */
    private fun standIn(tx: PortfolioTransaction, fields: Set<PortfolioFormField>): PortfolioTransaction {
        var t = tx
        fields.forEach { field ->
            t = when (field) {
                PortfolioFormField.SYMBOL -> t.copy(instrument = InstrumentRef("CHECK", null, null, t.currency.name))
                PortfolioFormField.SHARES -> t.copy(quantity = "1")
                PortfolioFormField.PRICE -> t.copy(unitPrice = "1")
                PortfolioFormField.AMOUNT -> t.copy(grossAmount = "1")
                PortfolioFormField.FEES -> t.copy(fees = "0")
                PortfolioFormField.FX_RATE -> t.copy(fxRate = null)
                PortfolioFormField.SETTLEMENT -> t.copy(settlementDate = null)
                PortfolioFormField.NOTES -> t.copy(notes = null)
                else -> t
            }
        }
        return t
    }

    /**
     * D1.1: errors kept after the transaction type (or another visibility input) changes. Errors from `PortfolioEngine.validate` depended on
     * the old type, so they are dropped; number/date format errors describe the typed text and stay while their field is still visible.
     */
    fun retainedErrors(errors: Map<PortfolioFormField, String>, type: TransactionType, symbol: String, cashDiffers: Boolean): Map<PortfolioFormField, String> {
        val visible = visibleFields(type, symbol, cashDiffers).toSet()
        return errors.filter { (field, message) -> field in visible && (message == PortfolioDisplay.FORM_NUMBER_INVALID || message == PortfolioDisplay.FORM_DATE_INVALID) }
    }

    /** D1.1: "Apple Inc. · NASDAQ · USD" under the symbol when the chosen security's details are known (null otherwise; never looked up). */
    fun selectedSecurity(symbol: String, name: String, exchange: String, currency: String?): String? {
        if (symbol.isBlank() || (name.isBlank() && exchange.isBlank())) return null
        return listOf(name, exchange, currency.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /**
     * E.2: under "Trade currency" when it differs from the chosen account's reporting currency (null when they match or no account is chosen).
     * Says which currency the amounts are in and that the app converts them; it never changes or converts what was typed. The trade currency
     * isn't looked up for a typed symbol, so the note is also the prompt to check it.
     */
    fun currencyNote(tradeCurrency: String, account: PortfolioAccount?): String? =
        account?.reportingCurrency?.name?.takeIf { it != tradeCurrency }?.let { PortfolioDisplay.formCurrencyNote(tradeCurrency, it) }

    /** D1.1: the "earlier returns" note only where it applies (an opening position's effective date), instead of a paragraph above every form. */
    fun dateNote(type: TransactionType): String? = if (type == TransactionType.OPENING_POSITION) PortfolioDisplay.FORM_INTRO else null
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
