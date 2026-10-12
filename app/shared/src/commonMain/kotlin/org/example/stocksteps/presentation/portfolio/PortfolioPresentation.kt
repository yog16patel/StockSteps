package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.format.ReadableDates
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioHoldingRow
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType

/**
 * Display text for the Portfolio screen (Global UI Refinement Phase 3), shared by Compose and SwiftUI so both show the same labels,
 * signs and freshness. Formatting only: every number comes from [PortfolioUiState] (exact decimal strings) — nothing is calculated,
 * estimated or defaulted to zero here; a missing value stays missing (`null` / "—").
 */
object PortfolioPresentation {
    /** "TFSA", "Non-registered", "Personal" — never the raw enum name. */
    fun categoryLabel(category: PortfolioCategory): String = when (category) {
        PortfolioCategory.TFSA -> "TFSA"
        PortfolioCategory.RRSP -> "RRSP"
        PortfolioCategory.FHSA -> "FHSA"
        PortfolioCategory.NON_REGISTERED -> "Non-registered"
        PortfolioCategory.PERSONAL -> "Personal"
        PortfolioCategory.OTHER -> "Other"
    }

    /** "Opening position", "Dividend reinvestment", … */
    fun transactionLabel(type: TransactionType): String = when (type) {
        TransactionType.BUY -> "Buy"
        TransactionType.SELL -> "Sell"
        TransactionType.CASH_DEPOSIT -> "Cash deposit"
        TransactionType.CASH_WITHDRAWAL -> "Cash withdrawal"
        TransactionType.DIVIDEND -> "Dividend"
        TransactionType.DIVIDEND_REINVESTMENT -> "Dividend reinvestment"
        TransactionType.FEE -> "Fee"
        TransactionType.OPENING_POSITION -> "Opening position"
        TransactionType.CASH_ADJUSTMENT -> "Cash adjustment"
        TransactionType.TRANSFER_IN -> "Transfer in"
        TransactionType.TRANSFER_OUT -> "Transfer out"
        TransactionType.STOCK_SPLIT -> "Stock split"
    }

    /**
     * Account selector text: "Personal · CAD". When the name already says the reporting currency ("TFSA CAD") it isn't repeated ("TFSA CAD",
     * not "TFSA CAD · CAD"); a name naming another currency keeps both ("US savings USD · CAD").
     */
    fun accountLabel(account: PortfolioAccount): String {
        val code = account.reportingCurrency.name
        val named = account.name.split(' ', '-', '(', ')', '·', '/', ',').any { it.equals(code, ignoreCase = true) }
        return if (named) account.name else "${account.name} · $code"
    }

    /** Menu / settings detail: "TFSA · reports in CAD". */
    fun accountDetail(account: PortfolioAccount): String = "${categoryLabel(account.category)} · reports in ${account.reportingCurrency.name}"

    /** Sign of an exact decimal string; UNAVAILABLE when missing or unparsable (never treated as zero). */
    fun direction(amount: String?): PriceDirection {
        val value = amount?.let { runCatching { Decimal.parse(it) }.getOrNull() } ?: return PriceDirection.UNAVAILABLE
        val zero = Decimal.parse("0")
        return when {
            value > zero -> PriceDirection.UP
            value < zero -> PriceDirection.DOWN
            else -> PriceDirection.UNCHANGED
        }
    }

    /** "+1,577.28", "-12.50", "0.00"; null when missing. */
    fun signedAmount(amount: String?): String? {
        amount ?: return null
        val text = PortfolioFormat.amount(amount)
        if (text == "—") return null
        return if (direction(amount) == PriceDirection.UP) "+$text" else text
    }

    /** Holding row change in two lines (reference layout): "+13,144.00%" over "(+1,577.28)"; amount alone when the percentage is missing. */
    fun holdingChangeLines(row: PortfolioHoldingRow): Pair<String, String?>? {
        val amount = signedAmount(row.gain) ?: return null
        val percent = signedPercent(row.gainPercent) ?: return amount to null
        return percent to "($amount)"
    }

    /** "+13,144.00%"; null when missing. */
    fun signedPercent(percent: String?): String? = signedAmount(percent)?.let { "$it%" }

    /** "+1,577.28 (+13,144.00%)", or just the amount when the percentage is missing; null when the amount is missing. */
    fun change(amount: String?, percent: String?): String? {
        val signed = signedAmount(amount) ?: return null
        return signedPercent(percent)?.let { "$signed ($it)" } ?: signed
    }

    /** Hero "Today" line; null when the daily change is unavailable (the screen then says so — no invented zero). */
    fun todayChange(state: PortfolioUiState): String? = change(state.dailyGain, state.dailyPercent)

    const val TODAY_UNAVAILABLE = "Today's change unavailable"

    /** "MSFT · NASDAQ · 3 shares" (exchange only when provided). */
    fun holdingSubtitle(row: PortfolioHoldingRow): String =
        // Non-breaking space (Phase C): "10 shares" never splits as "10 / shares" at large text.
        listOfNotNull(row.symbol, row.exchange?.takeIf { it.isNotBlank() }, "${row.quantity}\u00A0${if (row.quantity == "1") "share" else "shares"}").joinToString(" · ")

    /** "USD 1,589.28" or "USD —" when the market value is unavailable. */
    fun holdingValue(row: PortfolioHoldingRow): String = "${row.currency} ${PortfolioFormat.amount(row.value)}"

    /**
     * Signed amount with its currency, the sign before the code: "+USD 3.00", "-USD 12.50", "USD 0.00"; "USD —" when missing or unparsable
     * (never 0.00). Formatting only — the ledger string is the value.
     */
    fun signedMoney(currency: String, amount: String?): String {
        val text = PortfolioFormat.amount(amount)
        if (text == "—") return "$currency —"
        val sign = when (direction(amount)) { PriceDirection.UP -> "+"; PriceDirection.DOWN -> "-"; else -> "" }
        return "$sign$currency ${text.removePrefix("-")}"
    }

    /** Holding details "Realized": the holding's realized gain/loss in its trading currency (finding A-F3 — was the raw ledger string). */
    fun holdingRealized(row: PortfolioHoldingRow): String = signedMoney(row.currency, row.realized)

    /** Holding details "Dividends": dividends received for the holding, in its trading currency ("USD 12.50"). */
    fun holdingDividends(row: PortfolioHoldingRow): String = "${row.currency} ${PortfolioFormat.amount(row.dividends)}"

    /** One spoken description for a holding row (TalkBack / VoiceOver). */
    fun holdingDescription(row: PortfolioHoldingRow): String {
        val gain = when (direction(row.gain)) {
            PriceDirection.UP -> "unrealized gain ${change(row.gain, row.gainPercent)?.removePrefix("+")}"
            PriceDirection.DOWN -> "unrealized loss ${change(row.gain, row.gainPercent)?.removePrefix("-")}"
            PriceDirection.UNCHANGED -> "no unrealized gain or loss"
            PriceDirection.UNAVAILABLE -> "unrealized gain unavailable"
        }
        return listOfNotNull(row.name, holdingSubtitle(row), "value ${holdingValue(row)}", gain, if (row.stale) "last available quote, delayed or stale" else null)
            .joinToString(", ").replace('\u00A0', ' ')
    }

    /** "MSFT · 3 shares" (or just the ticker / nothing for cash transactions). */
    fun transactionDetail(transaction: PortfolioTransaction): String? {
        val symbol = transaction.instrument?.symbol ?: return null
        val quantity = transaction.quantity.takeIf { runCatching { Decimal.parse(it) != Decimal.parse("0") }.getOrDefault(false) }
        return listOfNotNull(symbol, quantity?.let { "$it\u00A0${if (it == "1") "share" else "shares"}" }).joinToString(" · ")
    }

    /**
     * Second line of a transaction: trades show the entered price per share ("USD 4.00 per share") — their net cash amount is often 0 for
     * opening positions and read as "CAD 0.00"; cash movements show their net amount ("CAD 500.00"). Ledger fields only, nothing derived.
     */
    fun transactionAmount(transaction: PortfolioTransaction): String {
        val price = transaction.unitPrice.takeIf { transaction.type in PRICED_TRADES && runCatching { Decimal.parse(it) != Decimal.parse("0") }.getOrDefault(false) }
        // Non-breaking space (Phase C): the currency code stays with its amount ("CAD 75.00", not "CAD / 75.00") at large text.
        return if (price != null) "${transaction.currency.name}\u00A0${PortfolioFormat.amount(price)} per share"
        else "${transaction.currency.name}\u00A0${PortfolioFormat.amount(transaction.netAmount)}"
    }

    private val PRICED_TRADES = setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION,
        TransactionType.DIVIDEND_REINVESTMENT, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)

    /** "Oct 9, 2026" for a trade date. */
    fun dateLabel(date: String): String = PortfolioDates.date(date)

    /** "Prices as of Oct 8, 2026, 3:00 PM UTC" and "Exchange rate as of Oct 8, 2026" as separate lines (market vs FX dates). */
    fun freshnessLines(state: PortfolioUiState): List<String> = listOfNotNull(
        state.quoteAsOf?.let { "Prices as of ${PortfolioDates.dateTime(it)}" },
        state.fxAsOf?.let { "Exchange rate as of ${PortfolioDates.dateTime(it)}" }
    )

    /** The freshness lines joined with " · "; null when neither is known. */
    fun freshness(state: PortfolioUiState): String? = freshnessLines(state).joinToString(" · ").ifEmpty { null }

    /** Bar length for an allocation percentage, clamped to 0…1 (borrowed cash can push a holding above 100 %; the label shows the real value). */
    fun allocationFraction(percent: String?): Float {
        val value = percent?.toDoubleOrNull() ?: return 0f
        return (value / 100.0).coerceIn(0.0, 1.0).toFloat()
    }
}

/** Portfolio's name for [ReadableDates] (moved to core in Phase 4A so Home's core-built labels use the same wording). */
object PortfolioDates {
    fun date(value: String): String = ReadableDates.date(value)
    fun shortDate(value: String): String = ReadableDates.shortDate(value)
    fun dateTime(value: String): String = ReadableDates.dateTime(value)
}

/** Layout thresholds shared by the Compose and SwiftUI Portfolio screens. */
object PortfolioLayout {
    /** Holding rows narrower than this (dp/pt, row width incl. padding) put market value and change under the company name. */
    const val stackedRowWidth = 320
}

/** Holdings display order (Portfolio P-A rule, used from P-C): presentation only — rows and their values are unchanged. */
enum class PortfolioHoldingSort(val label: String) { VALUE("Value"), NAME("Name"), CHANGE("Change %") }

/**
 * Display decisions shared by the Compose and SwiftUI Portfolio screens (Portfolio UI Refinement P-A, `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`).
 * They only compare or check exact decimal strings from `PortfolioUiState`; nothing here produces a number that is shown.
 */
object PortfolioDisplayRules {
    /**
     * A donut may replace the allocation bars only when every slice has a valid percentage, none is negative and together they don't exceed
     * 100 %. Borrowed or negative cash can push a holding above 100 % — a ring can't show that honestly, so the labelled bars stay.
     */
    fun allocationDonutAllowed(percents: List<String?>): Boolean {
        if (percents.isEmpty()) return false
        val values = percents.map { p -> p?.let { runCatching { Decimal.parse(it) }.getOrNull() } ?: return false }
        val zero = Decimal.parse("0")
        if (values.any { it < zero }) return false
        return values.fold(zero) { sum, v -> sum + v } <= Decimal.parse("100")
    }

    /**
     * Holdings in [sort] order. VALUE uses the reporting-currency value from the allocation list ([reportingValues] by symbol) so USD and CAD
     * holdings are never ranked by their native amounts; CHANGE uses the unrealized gain %. Rows whose sort value is missing keep their original
     * order after the others (missing never counts as zero). NAME is alphabetical, case-insensitive. Stable for ties.
     */
    fun sortHoldings(rows: List<PortfolioHoldingRow>, sort: PortfolioHoldingSort, reportingValues: Map<String, String?> = emptyMap()): List<PortfolioHoldingRow> {
        fun decimal(text: String?) = text?.let { runCatching { Decimal.parse(it) }.getOrNull() }
        return when (sort) {
            PortfolioHoldingSort.NAME -> rows.sortedBy { it.name.lowercase() }
            PortfolioHoldingSort.VALUE, PortfolioHoldingSort.CHANGE -> {
                val keyed = rows.map { it to decimal(if (sort == PortfolioHoldingSort.VALUE) reportingValues[it.symbol] else it.gainPercent) }
                keyed.filter { it.second != null }.sortedByDescending { it.second!! }.map { it.first } + keyed.filter { it.second == null }.map { it.first }
            }
        }
    }
}
