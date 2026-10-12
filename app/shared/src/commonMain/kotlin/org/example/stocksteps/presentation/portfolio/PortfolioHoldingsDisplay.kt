package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.PortfolioAllocation
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioHoldingRow
import org.example.stocksteps.portfolio.PortfolioUiState

/**
 * One holding row as both platforms draw it (Portfolio Phase C): left = name + "AAPL · NASDAQ · 10 shares", right = market value (strong) +
 * signed change (percentage when known, else the amount). The signed amount and everything else stay in [spoken] and in Holding Details.
 * [weight] is the server's allocation percentage for this symbol ("26.60% of account value"), shown only when exactly one allocation matches.
 */
data class PortfolioHoldingItem(
    val symbol: String,
    val name: String,
    val subtitle: String,
    val value: String,
    val change: String?,
    val direction: PriceDirection,
    val weight: String?,
    val stale: Boolean,
    val spoken: String,
    val logoUrl: String?
)

object PortfolioHoldingsDisplay {
    const val SORT_TITLE = "Sort holdings"
    const val DEFAULT_ORDER = "Default order"
    const val GAIN_UNAVAILABLE = "Gain unavailable"
    const val STALE = "Last available quote · delayed or stale"

    /** Short label for the sort menu button ("Sort: Value"); null → the presenter's own order. */
    fun sortLabel(sort: PortfolioHoldingSort?): String = "Sort: ${sort?.label ?: "Default"}"

    /**
     * Rows in display order. `null` keeps the presenter's order; otherwise `PortfolioDisplayRules.sortHoldings` (stable, missing values last).
     * VALUE ranks by the reporting-currency value from the allocation list, so USD and CAD holdings aren't compared by native amounts.
     */
    fun items(state: PortfolioUiState, sort: PortfolioHoldingSort?): List<PortfolioHoldingItem> {
        val rows = sort?.let { PortfolioDisplayRules.sortHoldings(state.holdings, it, state.allocations.associate { a -> a.symbol to a.reportingValue }) }
            ?: state.holdings
        return rows.map { item(it, state.allocations) }
    }

    fun item(row: PortfolioHoldingRow, allocations: List<PortfolioAllocation>): PortfolioHoldingItem {
        val lines = PortfolioPresentation.holdingChangeLines(row)
        val weight = weight(row.symbol, allocations)
        val spoken = listOfNotNull(PortfolioPresentation.holdingDescription(row), weight).joinToString(", ")
        return PortfolioHoldingItem(row.symbol, row.name, PortfolioPresentation.holdingSubtitle(row), PortfolioPresentation.holdingValue(row),
            lines?.first, PortfolioPresentation.direction(row.gain), weight, row.stale, spoken, row.logoUrl)
    }

    /**
     * "26.60% of account value" from the server's allocation for [symbol]; null when there is no allocation, more than one (ambiguous — e.g. the
     * same ticker in two currencies) or its percentage is missing/unparsable. Never computed from market values.
     */
    fun weight(symbol: String, allocations: List<PortfolioAllocation>): String? {
        val match = allocations.filter { it.symbol == symbol }.singleOrNull() ?: return null
        val percent = PortfolioFormat.amount(match.percent).takeIf { it != "—" } ?: return null
        return "$percent% of account value"
    }

    // ---- Holding details (Phase C) ----

    const val DETAILS_ACTIONS = "More for this holding"
    const val COMPANY_DETAILS = "Company details"
    const val PRICE_ALERTS = "Price alerts"
    const val ADD_TO_WATCHLIST = "Add to watchlist"

    /**
     * Holding details metrics, each with its currency and sign, from the row's existing strings only: Shares, Price, Market value, Cost,
     * Unrealized P/L (amount and %, as on the row), Realized P/L, Dividends. Missing values read "USD —".
     */
    fun detailMetrics(row: PortfolioHoldingRow): List<PortfolioOverviewMetric> {
        val c = row.currency
        val unrealized = PortfolioPresentation.signedMoney(c, row.gain).let { money ->
            PortfolioPresentation.signedPercent(row.gainPercent)?.takeIf { row.gain != null }?.let { "$money ($it)" } ?: money
        }
        fun plain(label: String, value: String) = PortfolioOverviewMetric(label, value, null, "$label $value")
        fun signed(label: String, value: String, amount: String?) = PortfolioOverviewMetric(label, value, PortfolioPresentation.direction(amount),
            when (PortfolioPresentation.direction(amount)) {
                PriceDirection.UP -> "$label gain of ${value.removePrefix("+")}"
                PriceDirection.DOWN -> "$label loss of ${value.removePrefix("-")}"
                PriceDirection.UNCHANGED -> "$label $value, no gain or loss"
                PriceDirection.UNAVAILABLE -> "$label unavailable"
            })
        return listOf(
            plain("Shares", row.quantity),
            plain("Price", "$c ${PortfolioFormat.amount(row.price)}"),
            plain("Market value", PortfolioPresentation.holdingValue(row)),
            plain("Cost", "$c ${PortfolioFormat.amount(row.basis)}"),
            signed("Unrealized P/L", unrealized, row.gain),
            signed("Realized P/L", PortfolioPresentation.holdingRealized(row), row.realized),
            plain("Dividends", PortfolioPresentation.holdingDividends(row))
        )
    }
}
