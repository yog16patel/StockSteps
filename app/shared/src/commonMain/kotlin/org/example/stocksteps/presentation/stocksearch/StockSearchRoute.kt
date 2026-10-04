package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.model.StockSearchResult

@Composable
internal fun StockSearchRoute(baseUrl: String?, hinge: WindowHinge?) {
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedSelection by rememberSaveable(stateSaver = listSaver<StockSearchResult?, String>(
        save = { stock -> stock?.let { listOf(it.symbol, it.name, it.currency.orEmpty(), it.exchange.orEmpty(), it.exchangeFullName.orEmpty()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else StockSearchResult(it[0], it[1], it[2].ifEmpty { null }, it[3].ifEmpty { null }, it[4].ifEmpty { null }) }
    )) { mutableStateOf<StockSearchResult?>(null) }
    val model = viewModel(key = "stock-search:${baseUrl.orEmpty()}") {
        val dependencies = org.example.stocksteps.di.StockStepsDependencies(baseUrl ?: localBackendUrl())
        dependencies.searchViewModel(savedQuery, savedSelection)
    }
    val state by model.state.collectAsStateWithLifecycle()
    SideEffect { savedQuery = state.query; savedSelection = state.selected }
    StockSearchScreen(state, hinge, model::changeQuery, model::selectStock, model::retryQuote)
}
