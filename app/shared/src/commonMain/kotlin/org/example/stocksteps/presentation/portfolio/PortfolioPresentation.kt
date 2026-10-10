package org.example.stocksteps.presentation.portfolio

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

    /** Account selector text: "Personal · CAD". */
    fun accountLabel(account: PortfolioAccount): String = "${account.name} · ${account.reportingCurrency.name}"

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
        listOfNotNull(row.symbol, row.exchange?.takeIf { it.isNotBlank() }, "${row.quantity} ${if (row.quantity == "1") "share" else "shares"}").joinToString(" · ")

    /** "USD 1,589.28" or "USD —" when the market value is unavailable. */
    fun holdingValue(row: PortfolioHoldingRow): String = "${row.currency} ${PortfolioFormat.amount(row.value)}"

    /** One spoken description for a holding row (TalkBack / VoiceOver). */
    fun holdingDescription(row: PortfolioHoldingRow): String {
        val gain = when (direction(row.gain)) {
            PriceDirection.UP -> "unrealized gain ${change(row.gain, row.gainPercent)?.removePrefix("+")}"
            PriceDirection.DOWN -> "unrealized loss ${change(row.gain, row.gainPercent)?.removePrefix("-")}"
            PriceDirection.UNCHANGED -> "no unrealized gain or loss"
            PriceDirection.UNAVAILABLE -> "unrealized gain unavailable"
        }
        return listOfNotNull(row.name, holdingSubtitle(row), "value ${holdingValue(row)}", gain, if (row.stale) "last available quote, delayed or stale" else null)
            .joinToString(", ")
    }

    /** "MSFT · 3 shares" (or just the ticker / nothing for cash transactions). */
    fun transactionDetail(transaction: PortfolioTransaction): String? {
        val symbol = transaction.instrument?.symbol ?: return null
        val quantity = transaction.quantity.takeIf { runCatching { Decimal.parse(it) != Decimal.parse("0") }.getOrDefault(false) }
        return listOfNotNull(symbol, quantity?.let { "$it ${if (it == "1") "share" else "shares"}" }).joinToString(" · ")
    }

    /**
     * Second line of a transaction: trades show the entered price per share ("USD 4.00 per share") — their net cash amount is often 0 for
     * opening positions and read as "CAD 0.00"; cash movements show their net amount ("CAD 500.00"). Ledger fields only, nothing derived.
     */
    fun transactionAmount(transaction: PortfolioTransaction): String {
        val price = transaction.unitPrice.takeIf { transaction.type in PRICED_TRADES && runCatching { Decimal.parse(it) != Decimal.parse("0") }.getOrDefault(false) }
        return if (price != null) "${transaction.currency.name} ${PortfolioFormat.amount(price)} per share"
        else "${transaction.currency.name} ${PortfolioFormat.amount(transaction.netAmount)}"
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

/**
 * Readable dates for portfolio "as of" stamps (Phase 3.1), without a time-zone database: dates stay calendar dates and UTC instants are
 * shown in UTC with the zone named ("Oct 8, 2026, 3:00 PM UTC") — exact rather than guessed local time. Anything else is shown unchanged.
 */
object PortfolioDates {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val UTC_INSTANT = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?Z$""")

    /** "2026-10-09" → "Oct 9, 2026". */
    fun date(value: String): String {
        val m = DATE.matchEntire(value.trim()) ?: return value
        return label(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: value
    }

    /** "2026-09-01" → "Sep 1" — compact chart-axis label (the full date is in the selection line and the spoken description). */
    fun shortDate(value: String): String {
        val m = DATE.matchEntire(value.trim()) ?: return value
        val month = m.groupValues[2].toInt(); val day = m.groupValues[3].toInt()
        if (month !in 1..12 || day !in 1..31) return value
        return "${MONTHS[month - 1]} $day"
    }

    /** "2026-10-07T20:00:00Z" → "Oct 7, 2026, 8:00 PM UTC"; a plain date → "Oct 9, 2026". */
    fun dateTime(value: String): String {
        val trimmed = value.trim()
        DATE.matchEntire(trimmed)?.let { return date(trimmed) }
        val m = UTC_INSTANT.matchEntire(trimmed) ?: return value
        val day = label(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return value
        val hour = m.groupValues[4].toInt(); val minute = m.groupValues[5]
        if (hour > 23 || minute.toInt() > 59) return value
        return "$day, ${if (hour % 12 == 0) 12 else hour % 12}:$minute ${if (hour < 12) "AM" else "PM"} UTC"
    }

    private fun label(year: String, month: String, day: String): String? {
        val m = month.toInt(); val d = day.toInt()
        if (m !in 1..12 || d !in 1..31) return null
        return "${MONTHS[m - 1]} $d, $year"
    }
}

/** Layout thresholds shared by the Compose and SwiftUI Portfolio screens. */
object PortfolioLayout {
    /** Holding rows narrower than this (dp/pt, row width incl. padding) put market value and change under the company name. */
    const val stackedRowWidth = 320
}
