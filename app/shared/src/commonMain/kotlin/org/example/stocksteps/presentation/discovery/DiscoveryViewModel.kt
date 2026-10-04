package org.example.stocksteps.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*

internal data class FeedState<T>(val items: List<T> = emptyList(), val loading: Boolean = false, val error: String? = null)
internal data class DiscoveryState(
    val gainers: FeedState<MarketMover> = FeedState(),
    val losers: FeedState<MarketMover> = FeedState(),
    val news: FeedState<NewsArticle> = FeedState()
)
internal enum class DiscoverySection { GAINERS, LOSERS, NEWS }

internal class DiscoveryViewModel(
    private val gainers: GetMarketGainers,
    private val losers: GetMarketLosers,
    private val news: GetMarketNews,
    private val closeResources: () -> Unit = {}
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiscoveryState())
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<DiscoverySection, Job>()
    init { refresh() }

    fun refresh() { DiscoverySection.entries.forEach(::retry) }

    fun retry(section: DiscoverySection) {
        jobs[section]?.cancel()
        jobs[section] = viewModelScope.launch {
            when (section) {
                DiscoverySection.GAINERS -> load({ gainers() }, { it.gainers }) { feed -> mutableState.update { it.copy(gainers = feed) } }
                DiscoverySection.LOSERS -> load({ losers() }, { it.losers }) { feed -> mutableState.update { it.copy(losers = feed) } }
                DiscoverySection.NEWS -> load({ news() }, { it.news }) { feed -> mutableState.update { it.copy(news = feed) } }
            }
        }
    }

    private suspend fun <T> load(fetch: suspend () -> List<T>, current: (DiscoveryState) -> FeedState<T>, update: (FeedState<T>) -> Unit) {
        update(current(state.value).copy(loading = true, error = null))
        try {
            val items = fetch()
            currentCoroutineContext().ensureActive()
            update(FeedState(items = items))
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            update(current(state.value).copy(loading = false, error = cause.message.takeIf { cause is StockDataException } ?: "Could not load market data. Try again."))
        }
    }

    override fun onCleared() { closeResources() }
}
