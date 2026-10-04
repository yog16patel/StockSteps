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
    onRetryProfile: () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
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
        if (detail == null) {
            StockSearchPane(
                state = state,
                modifier = layout.search.modifier(),
                inlineQuote = true,
                onQueryChange = onQueryChange,
                onSelect = onSelect,
                onRetryQuote = onRetryQuote,
                onRetryProfile = onRetryProfile
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
                onRetryProfile = onRetryProfile
            )
        }
    }
}
