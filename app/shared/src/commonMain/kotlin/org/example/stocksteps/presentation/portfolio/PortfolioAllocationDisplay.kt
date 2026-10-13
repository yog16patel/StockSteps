package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioUiState

/**
 * One allocation legend entry: [label] ("AAPL · USD"), [percent] as reported ("72.97%" or "—"), [amount] in the reporting currency when known,
 * [fraction] 0…1 for drawing only (bar length / donut sweep — never shown as a number), [colorIndex] into `StockChartSeriesStyle`, and one
 * [spoken] label ("AAPL, USD, 72.97 percent of account value, CAD 1,350.00").
 */
data class PortfolioAllocationEntry(
    val label: String,
    val percent: String,
    val amount: String?,
    val fraction: Float,
    val colorIndex: Int,
    val spoken: String
)

/** Allocation card content: holdings ([donut] true only when every slice is valid) and the collapsed by-currency group. */
data class PortfolioAllocationView(
    val donut: Boolean,
    val holdings: List<PortfolioAllocationEntry>,
    val currencies: List<PortfolioAllocationEntry>,
    val donutDescription: String?,
    /** D3.1: why the holding percentages don't add up to 100% (they're of account value, which includes cash); null without cash. */
    val cashNote: String? = null
)

/**
 * Display rules for the Allocation section (Portfolio Phase C). Percentages and amounts are the server's (`PortfolioReport.allocations`,
 * `currencyAllocations`); nothing is normalised or recomputed. The donut is drawn only when `PortfolioDisplayRules.allocationDonutAllowed`
 * accepts the holding percentages (every one present, none negative, total ≤ 100 %); otherwise the labelled bars stay — borrowed or negative
 * cash can't be drawn honestly as a ring.
 */
object PortfolioAllocationDisplay {
    const val TITLE = "Allocation"
    const val BY_HOLDING = "By holding"
    const val BY_CURRENCY = "By currency"
    const val EMPTY = "Allocation appears once you have holdings."
    const val SHOW_CURRENCY = "Show by currency"
    const val HIDE_CURRENCY = "Hide by currency"

    fun of(state: PortfolioUiState): PortfolioAllocationView {
        val currency = state.currency
        val holdings = state.allocations.mapIndexed { i, a ->
            entry("${a.symbol} · ${a.currency.name}", a.percent, a.reportingValue?.let { "$currency ${PortfolioFormat.amount(it)}" }, i,
                "${a.symbol}, ${a.currency.name}")
        }
        val currencies = state.currencyAllocations.mapIndexed { i, c ->
            entry(c.currency.name, c.percent, c.reportingValue?.let { "$currency ${PortfolioFormat.amount(it)}" }, i, c.currency.name)
        }
        val donut = PortfolioDisplayRules.allocationDonutAllowed(state.allocations.map { it.percent })
        val description = if (donut) "Allocation by holding: " + holdings.joinToString("; ") { it.spoken } else null
        return PortfolioAllocationView(donut, holdings, currencies, description, cashNote(state))
    }

    const val CASH_NEGATIVE_NOTE = "Cash is below zero (unfunded), so holdings add up to more than 100%."
    const val CASH_POSITIVE_NOTE = "Percentages are of account value, which includes cash."

    /**
     * D3.1: the engine's percentages divide by holdings + cash (documented: "Allocation includes cash"), but the list shows holdings only. Decided
     * from the sign of the presenter's cash values alone; nothing is computed. No note without holdings or without cash.
     */
    fun cashNote(state: PortfolioUiState): String? {
        if (state.allocations.isEmpty()) return null
        val lines = state.cash.map(PortfolioPresentation::moneyLine)
        val values = state.cash.mapNotNull { runCatching { org.example.stocksteps.portfolio.Decimal.parse(it.substringAfter(' ').trim()) }.getOrNull() }
        return when {
            lines.any { it.negative } -> CASH_NEGATIVE_NOTE
            values.any { it > org.example.stocksteps.portfolio.Decimal.ZERO } -> CASH_POSITIVE_NOTE
            else -> null
        }
    }

    private fun entry(label: String, percent: String?, amount: String?, index: Int, spokenName: String): PortfolioAllocationEntry {
        val text = PortfolioFormat.amount(percent).takeIf { it != "—" }?.let { "$it%" } ?: "—"
        val spokenPercent = if (text == "—") "percentage unavailable" else "${text.removeSuffix("%")} percent of account value"
        val spoken = listOfNotNull(spokenName, spokenPercent, amount).joinToString(", ")
        return PortfolioAllocationEntry(label, text, amount, PortfolioPresentation.allocationFraction(percent), index, spoken)
    }
}
