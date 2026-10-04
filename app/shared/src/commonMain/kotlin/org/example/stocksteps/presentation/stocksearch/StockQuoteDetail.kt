package org.example.stocksteps.presentation.stocksearch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun StockQuoteDetail(
    state: StockSearchState,
    modifier: Modifier,
    onRetry: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        val stock = state.selected
        if (stock == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ThemeSpacing.extraLarge.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your stock at a glance",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(ThemeSpacing.medium.dp))
                Text(
                    text = "Choose a stock from the search results to view its quote.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ThemeSpacing.extraLarge.dp),
                verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)
            ) {
                Text(
                    text = stock.symbol,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(listOfNotNull(stock.exchange, stock.currency).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                if (state.quoteLoading) {
                    CircularProgressIndicator()
                    Text("Loading quote…")
                }
                state.quoteError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("Retry quote") }
                }
                state.quote?.let { quote ->
                    Text(
                        text = "Current price",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${quote.price ?: "Unavailable"} ${stock.currency.orEmpty()}",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    QuoteMetric("Change", quote.change?.toString() ?: "Unavailable")
                    QuoteMetric("Change (%)", quote.changePercent?.let { "$it%" } ?: "Unavailable")
                    QuoteMetric("Day high", quote.dayHigh?.toString() ?: "Unavailable")
                    QuoteMetric("Day low", quote.dayLow?.toString() ?: "Unavailable")
                }
            }
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
    }
}
