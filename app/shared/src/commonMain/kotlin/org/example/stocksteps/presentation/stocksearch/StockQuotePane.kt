package org.example.stocksteps.presentation.stocksearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.stocksteps.presentation.companydetail.CompanyDetailScreen

@Composable
internal fun StockQuotePane(
    state: StockSearchState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onRetryProfile: () -> Unit,
    saved: Boolean = false,
    watchlistEnabled: Boolean = false,
    watchlistError: String? = null,
    onDetailAction: (org.example.stocksteps.presentation.companydetail.CompanyDetailAction) -> Unit = {},
    onToggleWatchlist: () -> Unit = {}
) {
    CompanyDetailScreen(
        state = state,
        modifier = modifier,
        saved = saved,
        watchlistEnabled = watchlistEnabled,
        watchlistError = watchlistError,
        onToggleWatchlist = onToggleWatchlist,
        onRetryQuote = onRetry,
        onRetryProfile = onRetryProfile,
        onAction = onDetailAction
    )
}
