package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.theme.ThemeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
internal fun StockQuotePane(state: StockSearchState, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
        val stock = state.selected
        if (stock == null) Text("Select a stock to view its quote.", style = MaterialTheme.typography.titleMedium)
        else Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(ThemeSpacing.large.dp), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
                Text(stock.name, style = MaterialTheme.typography.titleLarge)
                Text(listOfNotNull(stock.symbol, stock.exchange, stock.currency).joinToString(" · "))
                if (state.quoteLoading) CircularProgressIndicator()
                state.quote?.let {
                    Text("Price: ${it.price ?: "Unavailable"} ${stock.currency.orEmpty()}")
                    Text("Change: ${it.change ?: "Unavailable"} (${it.changePercent ?: "Unavailable"}%)")
                }
                state.quoteError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry quote") }
                }
            }
        }
    }
}
