package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.compose.foundation.background
import org.example.stocksteps.theme.ThemeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun StockSearchScreen(
    state: StockSearchState,
    hinge: WindowHinge?,
    onQueryChange: (String) -> Unit,
    onSelect: (org.example.stocksteps.model.StockSearchResult) -> Unit,
    onRetryQuote: () -> Unit,
    onRetryProfile: () -> Unit,
    saved: Boolean = false,
    watchlistEnabled: Boolean = false,
    watchlistError: String? = null,
    onDetailAction: (org.example.stocksteps.presentation.companydetail.CompanyDetailAction) -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    recent: List<org.example.stocksteps.model.StockSearchResult> = emptyList(),
    onClearRecent: () -> Unit = {},
    onRetrySearch: () -> Unit = {}
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Phase 5: safe *drawing* insets (bars, cutout, keyboard). safeContent also adds the edge-swipe gesture zones (~30 dp per side
            // with gesture navigation), which pushed the field and results ~46 dp from the edges instead of the 16 dp screen margin.
            .safeDrawingPadding()
            .padding(ThemeSpacing.screen.dp)
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val layout = paneLayout(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            density.density,
            origin.x,
            origin.y,
            hinge
        )

        fun PaneRect.modifier(): Modifier = with(density) {
            Modifier
                .absoluteOffset(x.toDp(), y.toDp())
                .size(width.toDp(), height.toDp())
        }

        val detail = layout.detail
        if (detail == null && state.selected != null) {
            StockQuoteDetail(
                state = state, modifier = layout.search.modifier(), onRetry = onRetryQuote,
                onRetryProfile = onRetryProfile, saved = saved, watchlistEnabled = watchlistEnabled,
                watchlistError = watchlistError, onDetailAction = onDetailAction, onToggleWatchlist = onToggleWatchlist
            )
        } else if (detail == null) {
            StockSearchPane(
                state = state,
                modifier = layout.search.modifier(),
                inlineQuote = true,
                recent = recent,
                onClearRecent = onClearRecent,
                onRetrySearch = onRetrySearch,
                onQueryChange = onQueryChange,
                onSelect = onSelect,
                onRetryQuote = onRetryQuote,
                onRetryProfile = onRetryProfile,
                saved = saved,
                watchlistEnabled = watchlistEnabled,
                watchlistError = watchlistError,
                onDetailAction = onDetailAction,
                onToggleWatchlist = onToggleWatchlist
            )
        } else {
            StockSearchSidebar(
                state = state,
                modifier = layout.search.modifier(),
                onQueryChange = onQueryChange,
                onSelect = onSelect
            )
            StockQuoteDetail(
                state = state,
                modifier = detail.modifier(),
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
}
