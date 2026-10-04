package org.example.stocksteps.presentation.stocksearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*

internal data class StockWatchlistState(
    val symbols: Set<String> = emptySet(),
    val enabled: Boolean = false,
    val error: String? = null
)

internal class StockWatchlistViewModel(
    repository: WatchlistRepository,
    auth: AuthRepository,
    private val add: AddToWatchlist,
    private val remove: RemoveFromWatchlist
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    val state = combine(repository.snapshot, auth.session, error, busy) { snapshot, session, error, busy ->
        StockWatchlistState(
            symbols = if (snapshot.userId == session.user?.id) snapshot.items.map { it.symbol }.toSet() else emptySet(),
            enabled = !session.initializing && !busy,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockWatchlistState())

    fun toggle(symbol: String) {
        if (busy.value || !state.value.enabled) return
        busy.value = true
        viewModelScope.launch {
            try {
                if (symbol in state.value.symbols) remove(symbol) else add(symbol)
                error.value = null
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                error.value = "Could not update your watchlist. Try again."
            } finally { busy.value = false }
        }
    }
}
