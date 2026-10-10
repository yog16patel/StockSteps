package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.components.StockCard
import org.example.stocksteps.designsystem.components.StockIconTile
import org.example.stocksteps.designsystem.components.StockPillSelector
import org.example.stocksteps.designsystem.components.StockTrendChart
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.PortfolioValuation

/** Shared with SwiftUI: when the history chart is worth drawing. */
object PortfolioChartRules {
    /** At least two dated (non-missing) values; otherwise the card shows a one-line summary instead of an empty chart area. */
    fun drawable(values: List<String?>): Boolean = values.count { it?.toDoubleOrNull() != null } >= 2
    /** Same rule for a valuation list (SwiftUI cannot take a `List<String?>` directly). */
    fun drawableHistory(history: List<PortfolioValuation>): Boolean = drawable(history.map { it.totalValue })
}

internal val PORTFOLIO_RANGES = listOf("1D", "1W", "1M", "3M", "1Y", "ALL")

/**
 * Portfolio performance card (Phase 3): period pills, the dated account-value chart (cash included, missing observations drawn as
 * gaps — never interpolated) or a compact empty state. History loads only when a period is chosen, so no pill looks selected until
 * dated values are loading or shown; the disclosure stays one line.
 */
@Composable
internal fun PortfolioPerformanceCard(state: PortfolioUiState, onRange: (String) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val active = state.history.isNotEmpty() || state.historyLoading
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text("Portfolio performance", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
        StockPillSelector(PORTFOLIO_RANGES, selected = if (active) state.historyRange else "", label = { it }, onSelect = onRange)
        if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.history.isEmpty()) {
            if (!state.historyLoading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                StockIconTile(StockIcons.TrendingUp, colors.surfaceSecondary, colors.iconSecondary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text("No performance history shown", style = typography.bodySemiBold, color = colors.textTitle)
                    Text("Choose a period to load dated values.", style = typography.small, color = colors.textSupporting)
                }
            }
        } else if (PortfolioChartRules.drawable(state.history.map { it.totalValue })) {
            val values = state.history.map { it.totalValue?.toDoubleOrNull() }
            val details = state.history.map { "${PortfolioDates.date(it.date)} · ${it.currency.name} ${it.totalValue?.let(PortfolioFormat::amount) ?: "Unavailable"}" }
            StockTrendChart(values, details, listOf(PortfolioDates.shortDate(state.history.first().date), PortfolioDates.shortDate(state.history.last().date)), emptyList(),
                description = "Portfolio value in ${state.currency}. ${details.first()}. ${details.last()}. Missing observations are gaps.")
        } else {
            // Fewer than two dated values: a line chart would be an almost empty box, so state the one value instead.
            val latest = state.history.lastOrNull { it.totalValue != null }
            Text(latest?.let { "Only one dated value so far: ${PortfolioDates.date(it.date)} · ${it.currency.name} ${PortfolioFormat.amount(it.totalValue)}" }
                ?: "No dated values in this period.", style = typography.small, color = colors.textSupporting)
        }
        Text(state.historyNotice, style = typography.caption, color = colors.textMeta)
    }
}
