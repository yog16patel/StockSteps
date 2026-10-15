package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.StockDivider
import org.example.stocksteps.designsystem.components.StockEmptyState
import org.example.stocksteps.designsystem.components.StockErrorState
import org.example.stocksteps.designsystem.components.StockSearchField
import org.example.stocksteps.designsystem.components.StockSearchResultRow
import org.example.stocksteps.designsystem.components.StockSectionHeader
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.search.SearchDisplay
import org.example.stocksteps.search.SearchDisplay.Phase

/**
 * Company search (5D.5.5B/C): a pinned, borderless search field (the screen's top bar already says "Search stocks"), then exactly one state
 * from [SearchDisplay.phase] — recent searches, a one-line hint, "Searching…", ranked results (Best match / Other results), no results, or
 * an error with Retry. Results only ever belong to the current query (the ViewModel drops stale responses and clears on every change).
 */
@Composable
internal fun StockSearchPane(
    state: StockSearchState,
    modifier: Modifier = Modifier,
    inlineQuote: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSelect: (StockSearchResult) -> Unit,
    onRetryQuote: () -> Unit,
    onRetryProfile: () -> Unit,
    saved: Boolean = false,
    watchlistEnabled: Boolean = false,
    watchlistError: String? = null,
    onDetailAction: (org.example.stocksteps.presentation.companydetail.CompanyDetailAction) -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    recent: List<StockSearchResult> = emptyList(),
    onClearRecent: () -> Unit = {},
    onRetrySearch: () -> Unit = {}
) {
    val spacing = StockStepsTheme.spacing
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Opens ready to type (as iOS); later query changes never dismiss the keyboard.
    LaunchedEffect(Unit) { if (state.query.isEmpty() && state.selected == null) runCatching { focus.requestFocus() } }
    val phase = SearchDisplay.phase(state.query, state.searching, state.searchError != null, state.results, recent)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        StockSearchField(state.query, onQueryChange, SearchDisplay.PLACEHOLDER, Modifier.fillMaxWidth(), focusRequester = focus,
            onSearch = { focusManager.clearFocus() }, bordered = false)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = spacing.xl)) {
            if (inlineQuote && state.selected != null) {
                item(key = "quote") {
                    StockQuotePane(state = state, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), onRetry = onRetryQuote,
                        onRetryProfile = onRetryProfile, saved = saved, watchlistEnabled = watchlistEnabled, watchlistError = watchlistError,
                        onDetailAction = onDetailAction, onToggleWatchlist = onToggleWatchlist)
                }
            }
            when (phase) {
                Phase.RECENT -> {
                    item(key = "recent:header") {
                        StockSectionHeader(SearchDisplay.RECENT_TITLE, actionText = SearchDisplay.CLEAR_RECENT, onActionClick = onClearRecent)
                    }
                    resultRows("recent", recent, best = false, onSelect)
                }
                Phase.HINT -> item(key = "hint") {
                    StockEmptyState(SearchDisplay.HINT, Modifier.padding(top = spacing.sm))
                }
                Phase.SEARCHING -> item(key = "searching") { SearchingRow() }
                Phase.RESULTS -> SearchDisplay.sections(state.query, state.results).forEach { section ->
                    item(key = "header:${section.title}") { StockSectionHeader(section.title, Modifier.padding(top = spacing.sm)) }
                    resultRows("result", section.results, section.best, onSelect)
                }
                Phase.NO_RESULTS -> item(key = "none") {
                    StockEmptyState(SearchDisplay.NO_RESULTS_MESSAGE, Modifier.padding(top = spacing.xl).semantics { liveRegion = LiveRegionMode.Polite },
                        actionText = SearchDisplay.TRY_DIFFERENT, onAction = { onQueryChange(""); runCatching { focus.requestFocus() } },
                        title = SearchDisplay.NO_RESULTS_TITLE, icon = StockIcons.Search)
                }
                Phase.ERROR -> item(key = "error") {
                    StockErrorState(SearchDisplay.ERROR_MESSAGE, onRetrySearch, Modifier.padding(top = spacing.xl).semantics { liveRegion = LiveRegionMode.Polite },
                        title = SearchDisplay.ERROR_TITLE, icon = StockIcons.ErrorOutline, primaryRetry = true, retryText = SearchDisplay.RETRY)
                }
            }
        }
    }
}

/** Result rows with dividers inset to the text (the logo stays on the left at every size). */
internal fun LazyListScope.resultRows(prefix: String, rows: List<StockSearchResult>, best: Boolean, onSelect: (StockSearchResult) -> Unit,
                                      enabled: (StockSearchResult) -> Boolean = { true }, note: (StockSearchResult) -> String? = { null }) {
    itemsIndexed(rows, key = { _, stock -> "$prefix:${stock.symbol}" }) { index, stock ->
        if (index > 0) StockDivider(startIndent = StockStepsTheme.dimensions.logo + StockStepsTheme.spacing.md)
        StockSearchResultRow(stock, best = best, onClick = { onSelect(stock) }, enabled = enabled(stock), note = note(stock))
    }
}

/** Compact "Searching…" with a small spinner; announced politely, never a full-screen flash. */
@Composable
internal fun SearchingRow(modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.xl).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(StockStepsTheme.dimensions.iconSmall), color = colors.primary,
            strokeWidth = StockStepsTheme.dimensions.border * 2)
        androidx.compose.material3.Text(SearchDisplay.SEARCHING, style = StockStepsTheme.typography.small, color = colors.textSecondary)
    }
}
