package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.StockLabels
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.PortfolioValuation

/** One overview metric (Invested cost, Unrealized P/L, Realized P/L): [value] carries its currency; [spoken] says gain/loss in words. */
data class PortfolioOverviewMetric(val label: String, val value: String, val direction: PriceDirection?, val spoken: String)

/**
 * What the Portfolio overview card shows (Portfolio UI Refinement Phase B), built once from [PortfolioUiState] for Compose and SwiftUI.
 * Formatting only: every amount is an existing exact-decimal presenter field; missing stays "—"; nothing is added, converted or returned as a
 * new percentage. [today] is null when the server's daily change is unavailable (the card then shows the "Why?" line).
 */
data class PortfolioOverview(
    val total: String,
    val totalAvailable: Boolean,
    val today: String?,
    val todayDirection: PriceDirection,
    val allAccounts: String?,
    val metrics: List<PortfolioOverviewMetric>,
    val freshness: String?,
    val notice: String?,
    val sampleBadge: String?,
    val spoken: String
)

/** History area of the overview card; each state has its own copy (never an invented line). */
enum class PortfolioHistoryState { NOT_REQUESTED, LOADING, LOADED, FLAT, INSUFFICIENT, EMPTY, ERROR }

object PortfolioOverviewDisplay {
    const val TOTAL_LABEL = "Total value"
    const val TODAY_LABEL = "Today's change"
    /** The period the compact "Show 1M" action loads (history stays on demand, rule R2). */
    const val SHOW_PERIOD = "1M"
    const val SHOW_ACTION = "Show 1M"
    const val NOT_REQUESTED_MESSAGE = "History loads when you choose a period."
    const val LOADING_MESSAGE = "Loading dated values…"
    const val EMPTY_MESSAGE = "No dated values in this period."
    const val ERROR_MESSAGE = "Account value history couldn't load."
    const val RETRY = "Retry"
    const val GAPS_NOTE = "Some dates have no value and are left as gaps."
    val PERIODS: List<String> = PORTFOLIO_RANGES

    fun of(state: PortfolioUiState, activeAccounts: Int): PortfolioOverview {
        val currency = state.currency
        val total = "$currency ${PortfolioFormat.amount(state.total)}"
        val totalAvailable = PortfolioFormat.amount(state.total) != "—"
        val todayAmount = state.dailyGain?.takeIf { PortfolioFormat.amount(it) != "—" }
        val today = todayAmount?.let { gain ->
            val money = PortfolioPresentation.signedMoney(currency, gain)
            PortfolioPresentation.signedPercent(state.dailyPercent)?.let { "$money ($it)" } ?: money
        }
        val todayDirection = if (today == null) PriceDirection.UNAVAILABLE else PortfolioPresentation.direction(state.dailyGain)
        // Non-breaking space: at large text the line wraps after "All accounts ·", never between the currency code and its amount.
        val allAccounts = if (activeAccounts > 1) "All accounts · $currency\u00A0${PortfolioFormat.amount(state.combinedTotal)}" else null
        val metrics = listOf(
            PortfolioOverviewMetric("Invested cost", "$currency ${PortfolioFormat.amount(state.basis)}", null,
                spokenAmount("Invested cost", currency, state.basis, signed = false)),
            PortfolioOverviewMetric("Unrealized P/L", PortfolioPresentation.signedMoney(currency, state.unrealized),
                PortfolioPresentation.direction(state.unrealized), spokenAmount("Unrealized P/L", currency, state.unrealized, signed = true)),
            PortfolioOverviewMetric("Realized P/L", PortfolioPresentation.signedMoney(currency, state.realized),
                PortfolioPresentation.direction(state.realized), spokenAmount("Realized P/L", currency, state.realized, signed = true))
        )
        val sample = state.mockScenario?.let { "Sample · ${StockLabels.humanize(it)}" }
        val spoken = listOfNotNull(
            sample?.let { "Read-only sample" },
            if (totalAvailable) "$TOTAL_LABEL $total" else "$TOTAL_LABEL unavailable",
            today?.let { "$TODAY_LABEL ${spokenChange(todayDirection, it)}" } ?: PortfolioPresentation.TODAY_UNAVAILABLE,
            allAccounts?.let { if (PortfolioFormat.amount(state.combinedTotal) == "—") "All accounts total unavailable" else it.replace(" · ", " ").replace('\u00A0', ' ') }
        ).joinToString(". ") + "."
        return PortfolioOverview(total, totalAvailable, today, todayDirection, allAccounts, metrics, PortfolioPresentation.freshness(state),
            state.notice, sample, spoken)
    }

    /**
     * [requested] is the period the user chose on this screen (null = none yet). History already in the state (an earlier choice kept by the
     * shared presenter, or a sample scenario) counts as loaded. A requested period with no values is EMPTY, or ERROR when the presenter
     * reports a failure.
     */
    fun historyState(state: PortfolioUiState, requested: String?): PortfolioHistoryState = when {
        state.historyLoading -> PortfolioHistoryState.LOADING
        state.history.isNotEmpty() -> when {
            !PortfolioChartRules.drawableHistory(state.history) -> PortfolioHistoryState.INSUFFICIENT
            flat(state.history) -> PortfolioHistoryState.FLAT
            else -> PortfolioHistoryState.LOADED
        }
        requested != null && state.error != null -> PortfolioHistoryState.ERROR
        requested != null -> PortfolioHistoryState.EMPTY
        else -> PortfolioHistoryState.NOT_REQUESTED
    }

    /** The pill shown as selected: the loaded/loading period, else the one just requested, else none (""). */
    fun selectedPeriod(state: PortfolioUiState, requested: String?): String =
        if (state.history.isNotEmpty() || state.historyLoading) state.historyRange else requested.orEmpty()

    /** Every dated value is the same exact decimal (at least two of them): drawing it would suggest movement through axis ticks. */
    fun flat(history: List<PortfolioValuation>): Boolean {
        val values = history.mapNotNull { v -> v.totalValue?.let { runCatching { Decimal.parse(it) }.getOrNull() } }
        return values.size >= 2 && values.all { it == values.first() }
    }

    /** True when some dates in the series have no value (drawn as gaps, never interpolated). */
    fun hasGaps(history: List<PortfolioValuation>): Boolean = history.any { it.totalValue == null } && history.any { it.totalValue != null }

    /** "Unchanged at CAD 1,850.00 · Sep 1 – Oct 8, 2026" for a FLAT series. */
    fun flatSummary(state: PortfolioUiState): String? {
        val dated = state.history.filter { it.totalValue != null }
        val first = dated.firstOrNull() ?: return null
        val last = dated.last()
        // Non-breaking spaces inside each date: the line may wrap at " · " or " – ", never inside "Oct 8, 2026" (seen at 360 dp).
        fun date(value: String) = PortfolioDates.date(value).replace(' ', '\u00A0')
        return "Unchanged at ${first.currency.name}\u00A0${PortfolioFormat.amount(first.totalValue)} · ${date(first.date)} – ${date(last.date)}"
    }

    /** INSUFFICIENT: the one dated value, or that there is none. */
    fun insufficientSummary(state: PortfolioUiState): String =
        state.history.lastOrNull { it.totalValue != null }
            ?.let { "Only one dated value so far: ${pointDetail(it)}" } ?: EMPTY_MESSAGE

    /** "Oct 8, 2026 · CAD 1,850.00" (or "· Unavailable" for a gap) — the selection line above the chart on both platforms. */
    fun pointDetail(value: PortfolioValuation): String =
        "${PortfolioDates.date(value.date)} · ${value.currency.name} ${value.totalValue?.let(PortfolioFormat::amount) ?: "Unavailable"}"

    /** Short first / last date labels under the chart. */
    fun xLabels(history: List<PortfolioValuation>): List<String> =
        if (history.isEmpty()) emptyList() else listOf(PortfolioDates.shortDate(history.first().date), PortfolioDates.shortDate(history.last().date))

    /** Highest and lowest dated value (top, bottom), exact decimals; empty when the series isn't drawable. */
    fun yLabels(history: List<PortfolioValuation>): List<String> {
        val values = history.mapNotNull { v -> v.totalValue?.let { runCatching { Decimal.parse(it) }.getOrNull() } }
        if (values.size < 2) return emptyList()
        return listOf(PortfolioFormat.amount(values.maxOf { it }.toString()), PortfolioFormat.amount(values.minOf { it }.toString()))
    }

    /** Spoken chart summary shared by both platforms (first and last dated values, gaps mentioned). */
    fun chartDescription(state: PortfolioUiState): String {
        val history = state.history
        if (history.isEmpty()) return PortfolioDisplay.CHART_EMPTY_TITLE
        return PortfolioDisplay.chartDescription(state.currency, pointDetail(history.first()), pointDetail(history.last()))
    }

    private fun spokenChange(direction: PriceDirection, text: String): String = when (direction) {
        PriceDirection.UP -> "up ${text.removePrefix("+")}"
        PriceDirection.DOWN -> "down ${text.removePrefix("-")}"
        PriceDirection.UNCHANGED -> "unchanged, $text"
        PriceDirection.UNAVAILABLE -> "unavailable"
    }

    private fun spokenAmount(label: String, currency: String, amount: String?, signed: Boolean): String {
        val text = PortfolioFormat.amount(amount)
        if (text == "—") return "$label unavailable"
        if (!signed) return "$label $currency $text"
        return when (PortfolioPresentation.direction(amount)) {
            PriceDirection.UP -> "$label gain of $currency $text"
            PriceDirection.DOWN -> "$label loss of $currency ${text.removePrefix("-")}"
            else -> "$label $currency $text, no gain or loss"
        }
    }
}
