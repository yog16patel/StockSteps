package org.example.stocksteps.presentation.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.localBackendUrl
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies

@Composable
internal fun HomeScene(baseUrl: String?, accounts: AccountDependencies, hinge: WindowHinge?, onSearch: () -> Unit, onExplore: (org.example.stocksteps.model.StockSearchResult) -> Unit) {
    val model = viewModel(key = "home:${baseUrl.orEmpty()}") {
        val data = StockStepsDependencies(baseUrl ?: localBackendUrl())
        HomeViewModel(
            quote = data.getStockQuote(),
            watchlist = accounts.watchlist,
            auth = accounts.auth,
            snapshot = data.getMarketSnapshot(),
            news = data.marketNews(),
            closeResources = data::close
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val saved by accounts.watchlist.snapshot.collectAsStateWithLifecycle(
        org.example.stocksteps.model.WatchlistSnapshot(null, emptyList())
    )
    val session by accounts.auth.session.collectAsStateWithLifecycle()
    HomeScreen(state, hinge, model::refresh, onSearch, { symbol ->
        val item = saved.items.takeIf { saved.userId == session.user?.id }?.firstOrNull { it.symbol == symbol }
        onExplore(item?.listing() ?: org.example.stocksteps.model.StockSearchResult(symbol, symbol))
    }, model::refreshNews)
}
