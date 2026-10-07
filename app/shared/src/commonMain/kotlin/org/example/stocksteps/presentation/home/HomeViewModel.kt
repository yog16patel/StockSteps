package org.example.stocksteps.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.home.MarketSnapshotUiModel
import org.example.stocksteps.home.MoverCategory
import org.example.stocksteps.home.MoversUiModel
import org.example.stocksteps.companydetail.SectionStatus
import org.example.stocksteps.model.*

internal data class HomeStock(val symbol: String, val name: String? = null, val change: Double? = null, val loading: Boolean = false, val error: Boolean = false, val price: Double? = null, val currency: String? = null)
internal data class HomeState(
    val snapshot: MarketSnapshot? = null,
    val snapshotLoading: Boolean = false,
    val snapshotError: String? = null,
    val stocks: List<HomeStock> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
    val newsLoading: Boolean = false,
    val newsError: String? = null,
    val selectedMovers: MoverCategory = MoverCategory.GAINERS,
    val sparklines: Map<String, List<Double>> = emptyMap(),
    val logos: Map<String, String> = emptyMap()
) {
    val market: MarketSnapshotUiModel get() = HomePresentation.market(snapshot, snapshotLoading, snapshotError != null, HomePresentation.HOME_MARKET_CARDS, sparklines)
    val movers: MoversUiModel get() = HomePresentation.movers(snapshot, snapshotLoading, snapshotError != null, selectedMovers, sparklines, logos)
    val newsStatus: SectionStatus get() = HomePresentation.newsStatus(news.size, newsLoading, newsError != null)

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
    private val sparkline: GetSparkline? = null,
    private val profile: GetCompanyProfile? = null,
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
                if (snapshot.userId == session.user?.id) snapshot.items else emptyList()
            }.collectLatest { items ->
                mutableState.update { it.copy(stocks = items.map { item -> HomeStock(item.symbol, item.name, loading = true, currency = item.currency) }) }
                // Bound simultaneous quote calls; completed rows render independently.
                items.chunked(3).forEach { batch ->
                    coroutineScope {
                        batch.map { item -> async {
                            val row = fetch(HomeStock(item.symbol, item.name, currency = item.currency))
                            mutableState.update { state -> state.copy(stocks = state.stocks.map { if (it.symbol == item.symbol) row else it }) }
                        } }.awaitAll()
                    }
                }
            }
        }
    }
    fun refresh() {
        requestedSparklines.clear()
        refreshRequests.update { it + 1 }
        refreshIndices()
    }
    /** Movers for every category arrive in one snapshot, so switching is local and makes no request. */
    fun selectMovers(category: MoverCategory) {
        mutableState.update { it.copy(selectedMovers = category) }
        loadMoverExtras()
    }

    private fun loadMoverExtras() {
        loadSparklines()
        loadLogos()
    }

    // Profile logos fill in for snapshot movers without one; requested once per symbol.
    private val requestedLogos = mutableSetOf<String>()
    private fun loadLogos() {
        val getProfile = profile ?: return
        val current = state.value
        HomePresentation.moversMissingLogos(current.snapshot, current.selectedMovers)
            .filter(requestedLogos::add)
            .forEach { symbol ->
                viewModelScope.launch {
                    val logo = try {
                        getProfile(symbol).logoUrl
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        null
                    }
                    if (logo != null) mutableState.update { it.copy(logos = it.logos + (symbol to logo)) }
                }
            }
    }

    // Requested once per symbol until the next refresh; failures simply leave the row without a line.
    private val requestedSparklines = mutableSetOf<String>()
    private fun loadSparklines() {
        val getSparkline = sparkline ?: return
        val current = state.value
        (HomePresentation.visibleIndexSymbols(current.snapshot) + HomePresentation.visibleMoverSymbols(current.snapshot, current.selectedMovers))
            .filter(requestedSparklines::add)
            .forEach { symbol ->
                viewModelScope.launch {
                    val closes = try {
                        getSparkline(symbol).closes
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        null
                    }
                    if (closes != null) mutableState.update { it.copy(sparklines = it.sparklines + (symbol to closes)) }
                }
            }
    }
    private fun refreshIndices() {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            mutableState.update { it.copy(snapshotLoading = true, snapshotError = null) }
            try {
                val result = snapshot()
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(snapshot = result, snapshotLoading = false) }
                loadMoverExtras()
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
