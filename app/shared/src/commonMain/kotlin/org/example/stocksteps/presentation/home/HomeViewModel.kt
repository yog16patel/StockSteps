package org.example.stocksteps.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*

internal data class HomeStock(val symbol: String, val name: String? = null, val change: Double? = null, val loading: Boolean = false, val error: Boolean = false, val price: Double? = null)
internal data class HomeState(
    val snapshot: MarketSnapshot? = null,
    val snapshotLoading: Boolean = false,
    val snapshotError: String? = null,
    val stocks: List<HomeStock> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
    val newsLoading: Boolean = false,
    val newsError: String? = null
) {
    val indices: List<HomeStock> get() = snapshot?.indices?.takeIf { it.isNotEmpty() }?.map {
        HomeStock(it.symbol, it.name, it.changePercent, error = it.error != null || it.price == null, price = it.price)
    } ?: listOf("SPY" to "S&P 500", "QQQ" to "Nasdaq-100", "DIA" to "Dow 30").map {
        HomeStock(it.first, it.second, loading = snapshotLoading, error = snapshotError != null || snapshot?.errors?.any { it.section == "indices" } == true)
    }
}

internal class HomeViewModel(
    private val quote: GetStockQuote,
    watchlist: WatchlistRepository,
    auth: AuthRepository,
    private val snapshot: GetMarketSnapshot,
    private val news: GetMarketNews? = null,
    private val closeResources: () -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private val refreshRequests = MutableStateFlow(0)
    private var indexJob: Job? = null
    private var newsJob: Job? = null
    fun refreshNews() {
        val getNews = news ?: return
        newsJob?.cancel()
        newsJob = viewModelScope.launch {
            mutableState.update { it.copy(newsLoading = true, newsError = null) }
            try {
                val articles = getNews()
                mutableState.update { it.copy(news = articles, newsLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(newsLoading = false, newsError = "Could not load news. Try again.") }
            }
        }
    }
    init {
        refreshIndices()
        refreshNews()
        viewModelScope.launch {
            combine(watchlist.snapshot, auth.session, refreshRequests) { snapshot, session, _ ->
                if (snapshot.userId == session.user?.id) snapshot.items.map { it.symbol } else emptyList()
            }.collectLatest { symbols ->
                mutableState.update { it.copy(stocks = symbols.map { symbol -> HomeStock(symbol, loading = true) }) }
                // Bound simultaneous quote calls; completed rows render independently.
                symbols.chunked(3).forEach { batch ->
                    coroutineScope {
                        batch.map { symbol -> async {
                            val row = fetch(HomeStock(symbol))
                            mutableState.update { state -> state.copy(stocks = state.stocks.map { if (it.symbol == symbol) row else it }) }
                        } }.awaitAll()
                    }
                }
            }
        }
    }
    fun refresh() { refreshRequests.update { it + 1 }; refreshIndices() }
    private fun refreshIndices() {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            mutableState.update { it.copy(snapshotLoading = true, snapshotError = null) }
            try {
                val result = snapshot()
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(snapshot = result, snapshotLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(snapshotLoading = false, snapshotError = cause.message.takeIf { cause is StockDataException } ?: "Could not load market snapshot. Try again.") }
            }
        }
    }
    private suspend fun fetch(row: HomeStock): HomeStock = try {
        val result: StockQuote = quote(row.symbol)
        currentCoroutineContext().ensureActive()
        row.copy(name = row.name ?: result.companyName, change = result.changePercent, error = result.changePercent == null, price = result.price)
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        row.copy(error = true)
    }
    override fun onCleared() { closeResources() }
}
