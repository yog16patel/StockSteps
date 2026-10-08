package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.model.StockSearchResult

@Composable
internal fun StockSearchScene(
    route: StockSearchRoute,
    backend: org.example.stocksteps.settings.BackendRouter,
    environment: org.example.stocksteps.settings.BackendEnvironment,
    hinge: WindowHinge?,
    accounts: org.example.stocksteps.di.AccountDependencies? = null,
    onOpenStock: ((StockSearchResult) -> Unit)? = null
) {
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedSelection by rememberSaveable(stateSaver = listSaver<StockSearchResult?, String>(
        save = { stock -> stock?.let { listOf(it.symbol, it.name, it.currency.orEmpty(), it.exchange.orEmpty(), it.exchangeFullName.orEmpty()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else StockSearchResult(it[0], it[1], it[2].ifEmpty { null }, it[3].ifEmpty { null }, it[4].ifEmpty { null }) }
    )) { mutableStateOf<StockSearchResult?>(null) }
    val model = viewModel(key = "stock-search:$environment") {
        val dependencies = org.example.stocksteps.di.StockStepsDependencies(backend::currentUrl)
        dependencies.searchViewModel(savedQuery, savedSelection)
    }
    var initialSymbolHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(route.initialSymbol) {
        if (!initialSymbolHandled) {
            route.initialSymbol?.let { symbol ->
                if (route.name != null || route.exchange != null) {
                    model.selectStock(StockSearchResult(symbol, route.name ?: symbol, route.currency, route.exchange, route.exchangeFullName))
                } else model.selectStock(StockSearchResult(symbol, symbol))
            }
            initialSymbolHandled = true
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    SideEffect { savedQuery = state.query; savedSelection = state.selected }
    val watchlistModel = accounts?.let { owner ->
        viewModel(key = "stock-watchlist") { owner.stockWatchlistViewModel() }
    }
    val watchlistState by (watchlistModel?.state ?: kotlinx.coroutines.flow.MutableStateFlow(StockWatchlistState()))
        .collectAsStateWithLifecycle()
    StockSearchScreen(
        state = state,
        hinge = hinge,
        onQueryChange = model::changeQuery,
        // Results open the shared Company Details page; inline selection remains only as a fallback.
        onSelect = { stock -> onOpenStock?.invoke(stock) ?: model.selectStock(stock) },
        onRetryQuote = model::retryQuote,
        onRetryProfile = model::retryProfile,
        saved = state.selected?.symbol in watchlistState.symbols,
        watchlistEnabled = watchlistState.enabled,
        watchlistError = watchlistState.error,
        onDetailAction = model::detailAction,
        onToggleWatchlist = { state.selected?.let { watchlistModel?.toggle(it) } }
    )
    watchlistState.choosing?.let { stock ->
        WatchlistChooserSheet(stock, watchlistState.lists, onChoose = { watchlistModel?.choose(it) }, onDismiss = { watchlistModel?.cancelChoice() })
    }
}
