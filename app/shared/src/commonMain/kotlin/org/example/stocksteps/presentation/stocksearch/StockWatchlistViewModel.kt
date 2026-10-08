package org.example.stocksteps.presentation.stocksearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.UserWatchlistsRepository
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.model.Watchlist
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.stringResource

internal data class StockWatchlistState(
    val symbols: Set<String> = emptySet(),
    val enabled: Boolean = false,
    val error: String? = null,
    /** Signed-in user's lists (for choosing where to save when there is more than one). */
    val lists: List<Watchlist> = emptyList(),
    /** Set while the user picks a destination list for this stock. */
    val choosing: StockSearchResult? = null
)

/**
 * "Add to Watchlist" on Company Details and Search. Saved = in any list. With several lists the
 * user chooses the destination; "Saved" removes the stock from every list that holds it.
 */
internal class StockWatchlistViewModel(
    repository: WatchlistRepository,
    auth: AuthRepository,
    private val add: AddToWatchlist,
    private val remove: RemoveFromWatchlist,
    private val lists: UserWatchlistsRepository? = null
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val choosing = MutableStateFlow<StockSearchResult?>(null)
    private val userLists = lists?.state?.map { it.value?.watchlists.orEmpty() } ?: flowOf(emptyList())
    val state = combine(repository.snapshot, auth.session, combine(error, busy, choosing, ::Triple), userLists) { snapshot, session, (error, busy, choosing), userLists ->
        StockWatchlistState(
            symbols = if (snapshot.userId == session.user?.id) snapshot.items.map { it.symbol }.toSet() else emptySet(),
            enabled = !session.initializing && !busy,
            error = error,
            lists = if (session.user != null) userLists else emptyList(),
            choosing = choosing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockWatchlistState())

    fun toggle(stock: StockSearchResult) {
        if (busy.value || !state.value.enabled) return
        if (stock.symbol !in state.value.symbols && state.value.lists.size > 1) { choosing.value = stock; return }
        run { if (stock.symbol in state.value.symbols) remove(stock.symbol) else add(stock) }
    }

    fun choose(listId: String) {
        val stock = choosing.value ?: return
        choosing.value = null
        val repository = lists ?: return
        run { repository.add(listId, InstrumentRef(stock.symbol, stock.name.takeIf { it != stock.symbol }, stock.exchange, stock.currency)) }
    }

    fun cancelChoice() { choosing.value = null }

    private fun run(block: suspend () -> Unit) {
        busy.value = true
        viewModelScope.launch {
            try { block(); error.value = null }
            catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                error.value = "Could not update your watchlist. Try again."
            } finally { busy.value = false }
        }
    }
}

/** Destination picker shown when the user has more than one watchlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchlistChooserSheet(stock: StockSearchResult, lists: List<Watchlist>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = StockStepsTheme.spacing.xxl)) {
            Text(stringResource(Res.string.watchlist_choose_title, stock.symbol),
                Modifier.padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.sm).semantics { heading() },
                style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            lists.forEach { list ->
                Text("${list.name} (${list.entries.size})",
                    Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button) { onChoose(list.id) }
                        .padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.md),
                    style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textPrimary)
            }
        }
    }
}
