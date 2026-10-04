package org.example.stocksteps.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.model.*

internal data class WatchListState(
    val items: List<WatchlistItem> = emptyList(),
    val session: AuthSession = AuthSession(),
    val sync: WatchlistSyncState = WatchlistSyncState(),
    val error: String? = null
)

internal class WatchListViewModel(
    private val repository: WatchlistRepository,
    auth: AuthRepository,
    private val remove: RemoveFromWatchlist
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val state = combine(repository.snapshot, auth.session, repository.syncState, error) { snapshot, session, sync, error ->
        WatchListState(if (snapshot.userId == session.user?.id) snapshot.items else emptyList(), session, sync, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchListState())

    fun remove(symbol: String) {
        viewModelScope.launch {
            try { remove(symbol); error.value = null }
            catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                error.value = if (cause is AccountException) cause.message else "Could not update your watchlist. Try again."
            }
        }
    }
    fun retry() { repository.retrySync() }
}
