package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import org.example.stocksteps.theme.ThemeSpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    onRetryQuote: () -> Unit,
    onRetryProfile: () -> Unit,
    saved: Boolean = false,
    watchlistEnabled: Boolean = false,
    watchlistError: String? = null,
    onDetailAction: (org.example.stocksteps.presentation.companydetail.CompanyDetailAction) -> Unit = {},
    onToggleWatchlist: () -> Unit = {}
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
        if (inlineQuote && state.selected != null) {
            item {
                StockQuotePane(
                    state = state,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    onRetry = onRetryQuote,
                    onRetryProfile = onRetryProfile,
                    saved = saved,
                    watchlistEnabled = watchlistEnabled,
                    watchlistError = watchlistError,
                    onDetailAction = onDetailAction,
                onToggleWatchlist = onToggleWatchlist
                )
            }
        }
        // 5D.5.5B: server-ranked results in "Best match" / "Other results" sections (or one "Results" section) with the shared borderless
        // row; the order and contents are the server's.
        org.example.stocksteps.search.SearchDisplay.sections(state.query, state.results).forEach { section ->
            item(key = "header:${section.title}") {
                org.example.stocksteps.designsystem.components.StockSectionHeader(section.title, Modifier.padding(top = ThemeSpacing.small.dp))
            }
            itemsIndexed(section.results, key = { _, stock -> "result:${stock.symbol}" }) { index, stock ->
                // The divider starts under the text; at very large text the avatar sits above the text, so it spans the row.
                val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= org.example.stocksteps.designsystem.components.SEARCH_ROW_STACK_SCALE
                if (index > 0) org.example.stocksteps.designsystem.components.StockDivider(
                    startIndent = if (stacked) 0.dp else org.example.stocksteps.designsystem.theme.StockStepsTheme.dimensions.logo + org.example.stocksteps.designsystem.theme.StockStepsTheme.spacing.md)
                org.example.stocksteps.designsystem.components.StockSearchResultRow(stock, best = section.best, onClick = { onSelect(stock) })
            }
        }
    }
}
