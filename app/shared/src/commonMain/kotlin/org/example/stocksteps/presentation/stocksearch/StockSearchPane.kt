package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import org.example.stocksteps.theme.ThemeSpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StockSearchPane(
    state: StockSearchState,
    modifier: Modifier = Modifier,
    inlineQuote: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSelect: (org.example.stocksteps.model.StockSearchResult) -> Unit,
    onRetryQuote: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
    ) {
        item { Text("StockSteps", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Search company or ticker") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.searchError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.query.isNotBlank() && !state.searching && state.searchError == null && state.results.isEmpty()) item { Text("No matches found.") }
        if (inlineQuote && state.selected != null) item { StockQuotePane(state, Modifier.fillMaxWidth().heightIn(max = 240.dp), onRetryQuote) }
        items(state.results, key = { it.symbol }) { stock ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(stock) }
                    .padding(vertical = ThemeSpacing.small.dp)
            ) {
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(listOfNotNull(stock.symbol, stock.exchange, stock.currency).joinToString(" · "))
            }
            HorizontalDivider()
        }
    }
}
