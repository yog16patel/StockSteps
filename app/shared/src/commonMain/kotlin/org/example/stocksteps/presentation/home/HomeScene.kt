package org.example.stocksteps.presentation.home

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies

@Composable
internal fun HomeScene(
    backend: org.example.stocksteps.settings.BackendRouter,
    environment: org.example.stocksteps.settings.BackendEnvironment,
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    onLearn: () -> Unit,
    onViewAllMovers: (() -> Unit)? = null,
    onViewAllNews: (() -> Unit)? = null,
    onExplore: (org.example.stocksteps.model.StockSearchResult) -> Unit
) {
    val model = viewModel(key = "home:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        HomeViewModel(
            quote = data.getStockQuote(),
            watchlist = accounts.watchlist,
            auth = accounts.auth,
            snapshot = data.getMarketSnapshot(),
            news = data.marketNews(),
            sparkline = data.getSparkline(),
            profile = data.getCompanyProfile(),
            closeResources = data::close
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val saved by accounts.watchlist.snapshot.collectAsStateWithLifecycle(
        org.example.stocksteps.model.WatchlistSnapshot(null, emptyList())
    )
    val session by accounts.auth.session.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    HomeScreen(
        state,
        signedIn = session.user != null,
        hinge = hinge,
        viewAllMovers = onViewAllMovers != null,
        viewAllNews = onViewAllNews != null,
        onAction = { action ->
            when (action) {
                HomeAction.Learn -> onLearn()
                HomeAction.ViewAllMovers -> onViewAllMovers?.invoke()
                HomeAction.ViewAllNews -> onViewAllNews?.invoke()
                HomeAction.RetryMarket -> model.refresh()
                HomeAction.RetryNews -> model.refreshNews()
                is HomeAction.SelectMovers -> model.selectMovers(action.category)
                is HomeAction.OpenArticle -> runCatching { uriHandler.openUri(action.url) }
                is HomeAction.OpenStock -> {
                    val item = saved.items.takeIf { saved.userId == session.user?.id }?.firstOrNull { it.symbol == action.symbol }
                    onExplore(item?.listing() ?: org.example.stocksteps.model.StockSearchResult(action.symbol, action.symbol))
                }
            }
        }
    )
}
