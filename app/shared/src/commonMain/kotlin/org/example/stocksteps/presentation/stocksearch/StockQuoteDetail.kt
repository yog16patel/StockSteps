package org.example.stocksteps.presentation.stocksearch

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import org.example.stocksteps.presentation.companydetail.CompanyDetailScreen

@Composable
internal fun StockQuoteDetail(
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
    if (state.selected == null) {
        Column(modifier = modifier) { Text("Select a company to understand its business and stock.") }
        return
    }
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
