package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.components.StockButton
import org.example.stocksteps.designsystem.components.StockButtonVariant
import org.example.stocksteps.designsystem.components.StockPillSelector
import org.example.stocksteps.designsystem.components.StockTrendChart
import org.example.stocksteps.designsystem.theme.StockStepsTheme
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
 * Account value history inside the overview card (Portfolio Phase B): heading + one-line note, period pills, then exactly one state from
 * [PortfolioOverviewDisplay.historyState] — "Show 1M" before any request (history stays on demand), loading, the chart (missing values are
 * gaps, high/low labels from exact decimals), a flat or single-value summary instead of a misleading line, empty, or an error with Retry.
 * It plots account value including cash and deposits — never labelled as investment performance.
 */
@Composable
internal fun PortfolioHistorySection(state: PortfolioUiState, requested: String?, onPeriod: (String) -> Unit, onRetry: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val history = PortfolioOverviewDisplay.historyState(state, requested)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(PortfolioDisplay.CHART_TITLE, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
            Text(PortfolioDisplay.CHART_NOTE, style = typography.caption, color = colors.textSupporting)
        }
        StockPillSelector(PortfolioOverviewDisplay.PERIODS, selected = PortfolioOverviewDisplay.selectedPeriod(state, requested), label = { it }, onSelect = onPeriod)
        when (history) {
            // Text above the action (not beside it): at 2.0× font scale a side-by-side row would squeeze the sentence into a narrow column.
            PortfolioHistoryState.NOT_REQUESTED -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(PortfolioOverviewDisplay.NOT_REQUESTED_MESSAGE, style = typography.small, color = colors.textSupporting)
                StockButton(PortfolioOverviewDisplay.SHOW_ACTION, { onPeriod(PortfolioOverviewDisplay.SHOW_PERIOD) }, variant = StockButtonVariant.SECONDARY)
            }
            PortfolioHistoryState.LOADING -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = PortfolioOverviewDisplay.LOADING_MESSAGE })
                Text(PortfolioOverviewDisplay.LOADING_MESSAGE, style = typography.caption, color = colors.textMeta)
            }
            PortfolioHistoryState.LOADED -> {
                val values = state.history.map { it.totalValue?.toDoubleOrNull() }
                StockTrendChart(values, state.history.map(PortfolioOverviewDisplay::pointDetail), PortfolioOverviewDisplay.xLabels(state.history),
                    PortfolioOverviewDisplay.yLabels(state.history), description = PortfolioOverviewDisplay.chartDescription(state))
                if (PortfolioOverviewDisplay.hasGaps(state.history)) Text(PortfolioOverviewDisplay.GAPS_NOTE, style = typography.caption, color = colors.textMeta)
            }
            PortfolioHistoryState.FLAT -> Text(PortfolioOverviewDisplay.flatSummary(state).orEmpty(), style = typography.small, color = colors.textBody)
            PortfolioHistoryState.INSUFFICIENT -> Text(PortfolioOverviewDisplay.insufficientSummary(state), style = typography.small, color = colors.textSupporting)
            PortfolioHistoryState.EMPTY -> Text(PortfolioOverviewDisplay.EMPTY_MESSAGE, style = typography.small, color = colors.textSupporting)
            PortfolioHistoryState.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(PortfolioOverviewDisplay.ERROR_MESSAGE, style = typography.small, color = colors.negativeText)
                StockButton(PortfolioOverviewDisplay.RETRY, onRetry, variant = StockButtonVariant.SECONDARY)
            }
        }
    }
}
