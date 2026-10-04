package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.stocksteps.domain.SearchStocks
import org.example.stocksteps.domain.GetStockQuote
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.model.StockSearchResult

internal class StockSearchViewModel(
    private val searchStocks: SearchStocks,
    private val getQuote: GetStockQuote,
    initialQuery: String = "",
    initialSelection: StockSearchResult? = null,
    private val closeResources: () -> Unit = {}
) : ViewModel() {
    private val mutableState = MutableStateFlow(StockSearchState(query = initialQuery, selected = initialSelection))
    val state = mutableState.asStateFlow()
    private val queries = MutableStateFlow(initialQuery.trim())
    private var searchJob: Job? = null
    private var quoteJob: Job? = null

    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            queries.debounce { if (it.isBlank()) 0L else 300L }.collectLatest { text ->
                if (text.isNotBlank()) {
                    searchJob = launch { search(text) }
                    searchJob?.join()
                }
            }
        }
    }

    init {
        mutableState.update { it.copy(searching = initialQuery.isNotBlank()) }
        observeQueries()
        initialSelection?.let { selectStock(it) }
    }

    fun changeQuery(query: String) {
        quoteJob?.cancel()
        mutableState.update { it.copy(query = query, selected = null, quote = null, quoteError = null, quoteLoading = false) }
        val text = query.trim()
        if (queries.value == text) return
        searchJob?.cancel()
        mutableState.update { it.copy(results = emptyList(), searchError = null, searching = text.isNotBlank()) }
        queries.value = text
    }

    private suspend fun search(query: String) {
        try {
            val results = searchStocks(query)
            currentCoroutineContext().ensureActive()
            if (queries.value == query) mutableState.update { it.copy(results = results, searching = false) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            if (queries.value == query) mutableState.update { it.copy(searchError = cause.userMessage(), searching = false) }
        }
    }

    fun selectStock(stock: StockSearchResult) {
        quoteJob?.cancel()
        mutableState.update { it.copy(selected = stock, quote = null, quoteError = null, quoteLoading = true) }
        quoteJob = viewModelScope.launch {
            try {
                val quote = getQuote(stock.symbol)
                ensureActive()
                mutableState.update { it.copy(quote = quote, quoteLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(quoteError = cause.userMessage(), quoteLoading = false) }
            }
        }
    }

    fun retryQuote() { state.value.selected?.let(::selectStock) }
    override fun onCleared() { closeResources() }
}

private fun Exception.userMessage() = if (this is StockDataException) message
    ?: "Could not load stock data." else "Could not load stock data. Try again."
