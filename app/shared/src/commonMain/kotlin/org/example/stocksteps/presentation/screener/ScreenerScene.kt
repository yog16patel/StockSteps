package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.stocksearch.WatchlistChooserSheet
import org.example.stocksteps.screener.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

internal class ScreenerViewModel(data: ScreenerDataSource, saved: SavedScreensRepository?, private val close: () -> Unit) : ViewModel() {
    val presenter = ScreenerPresenter(data, SharedComparisonSelection.instance, viewModelScope, saved)
    override fun onCleared() = close()
}

internal class ComparisonViewModel(data: ScreenerDataSource, private val close: () -> Unit) : ViewModel() {
    val presenter = ComparisonPresenter(data, SharedComparisonSelection.instance, viewModelScope)
    override fun onCleared() = close()
}

/** Owns the Screener presenter (keyed by Mock/Real) and wires Company Details, Watchlist, Portfolio and Compare. */
@Composable
internal fun ScreenerScene(
    route: ScreenerRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenStock: (String) -> Unit,
    onCompare: () -> Unit,
    onAddPortfolio: (InstrumentRef) -> Unit,
    onSignIn: () -> Unit
) {
    val model = viewModel(key = "screener:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        ScreenerViewModel(data.screenerData(), accounts?.savedScreens, data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<StockSearchResult?>(null) }
    var watchlistError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { model.presenter.start() }
    LaunchedEffect(route.preset, state.presets.isNotEmpty()) {
        if (route.preset != null && state.presets.isNotEmpty() && state.applied == null) model.presenter.selectPreset(route.preset)
    }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ScreenerScreen(
                state = state,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                actions = ScreenerActions(
                    onPreset = model.presenter::selectPreset,
                    onRange = model.presenter::setRange,
                    onChoice = model.presenter::setChoice,
                    onClearFilter = { model.presenter.clearFilter(it); model.presenter.apply() },
                    onReset = model.presenter::resetAll,
                    onApply = model.presenter::apply,
                    onSort = model.presenter::sort,
                    onLoadMore = model.presenter::loadMore,
                    onRetry = model.presenter::retry,
                    onOpen = onOpenStock,
                    onToggleCompare = model.presenter::toggleCompare,
                    onCompare = onCompare,
                    onWatchlist = { row -> choosing = StockSearchResult(row.symbol, row.name, row.currency, row.exchange) },
                    onPortfolio = { row -> onAddPortfolio(InstrumentRef(row.symbol, row.name, row.exchange, row.currency)) },
                    onSave = model.presenter::saveScreen,
                    onApplySaved = model.presenter::applySaved,
                    onRenameSaved = model.presenter::renameScreen,
                    onDeleteSaved = model.presenter::deleteScreen,
                    onSignIn = onSignIn,
                    onDismissMessage = model.presenter::dismissMessage
                ),
                watchlistAvailable = accounts != null
            )
        }
    }
    val lists = accounts?.watchlists?.state?.collectAsStateWithLifecycle()
    choosing?.let { stock ->
        if (accounts != null) WatchlistChooserSheet(
            stock = stock,
            lists = lists?.value?.value?.watchlists.orEmpty(),
            onChoose = { list ->
                scope.launch {
                    try {
                        // Independent of Portfolio: adding to a watchlist never changes holdings, and vice versa.
                        accounts.watchlists.add(list, InstrumentRef(stock.symbol, stock.name, stock.exchange, stock.currency))
                        choosing = null
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        watchlistError = cause.message ?: "Try again when the server is available."
                    }
                }
            },
            onDismiss = { choosing = null }
        )
    }
    watchlistError?.let { message ->
        AlertDialog(onDismissRequest = { watchlistError = null }, title = { Text("Could not save to watchlist") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { watchlistError = null }) { Text("OK") } })
    }
}

/** Owns the Comparison presenter; the selection is shared with Screener and Company Details. */
@Composable
internal fun ComparisonScene(
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    onOpenStock: (String) -> Unit,
    onDiscover: () -> Unit
) {
    val model = viewModel(key = "comparison:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        ComparisonViewModel(data.screenerData(), data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    val search = remember(environment) { StockStepsDependencies(backend::currentUrl) }
    DisposableEffect(search) { onDispose { search.close() } }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ComparisonScreen(
                state = state,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onRemove = model.presenter::remove,
                onAdd = model.presenter::add,
                onReplace = model.presenter::replace,
                onExample = model.presenter::useExample,
                onSearch = { query -> search.searchStocks()(query) },
                onPeriod = model.presenter::selectPeriod,
                onOpen = onOpenStock,
                onRetry = model.presenter::retry,
                onDiscover = onDiscover,
                onDismissMessage = model.presenter::dismissMessage
            )
        }
    }
}
