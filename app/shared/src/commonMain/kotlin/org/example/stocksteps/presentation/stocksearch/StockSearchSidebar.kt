package org.example.stocksteps.presentation.stocksearch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun StockSearchSidebar(
    state: StockSearchState,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onSelect: (StockSearchResult) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(ThemeSpacing.large.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
        ) {
            Text(
                text = "StockSteps",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Explore stocks",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Company or ticker") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "USD · CAD",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
            ) {
                if (state.query.isBlank()) item {
                    Text(
                        text = "Find your first stock",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Search for a company or ticker to explore its quote.")
                }
                if (state.query.isNotBlank() && !state.searching && state.searchError == null && state.results.isEmpty()) {
                    item { Text("No matches found.") }
                }
                items(state.results, key = { it.symbol }) { stock ->
                    val selected = state.selected?.symbol == stock.symbol
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onSelect(stock) }
                                )
                                .padding(ThemeSpacing.medium.dp),
                            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)
                        ) {
                            Text(
                                text = stock.symbol,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stock.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = listOfNotNull(stock.exchange, stock.currency).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
