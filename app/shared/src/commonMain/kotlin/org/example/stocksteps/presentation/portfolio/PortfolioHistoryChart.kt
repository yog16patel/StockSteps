package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.stocksteps.designsystem.components.StockTrendChart
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.PortfolioUiState

@Composable
internal fun PortfolioHistoryChart(state: PortfolioUiState, onRange: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        listOf("1D", "1W", "1M", "3M", "1Y", "ALL").forEach { range ->
            FilterChip(selected = state.historyRange == range, onClick = { onRange(range) }, label = { Text(range) })
        }
    }
    if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.history.isEmpty()) {
        Text("Choose a period to load dated history. No chart is invented when prices or investment history are missing.", style = StockStepsTheme.typography.small)
    } else {
        val values = state.history.map { it.totalValue?.toDoubleOrNull() }
        val details = state.history.map { "${it.date} · ${it.currency.name} ${it.totalValue ?: "Unavailable"}" }
        StockTrendChart(values, details, listOf(state.history.first().date, state.history.last().date), emptyList(),
            description = "Portfolio value in ${state.currency}. ${details.first()}. ${details.last()}. Missing observations are gaps.")
    }
    Text(state.historyNotice, style = StockStepsTheme.typography.caption)
}
