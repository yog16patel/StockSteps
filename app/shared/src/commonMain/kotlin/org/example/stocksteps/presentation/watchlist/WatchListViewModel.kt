package org.example.stocksteps.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.WatchlistRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.watchlist.WatchlistModel
import org.example.stocksteps.watchlist.WatchlistPresenter

internal data class WatchListState(
    val session: AuthSession = AuthSession(),
    /** Signed out: the on-device guest list. */
    val guestItems: List<WatchlistItem> = emptyList(),
    val lists: ServerBackedRepository.State<WatchlistsResponse> = ServerBackedRepository.State(),
    val alerts: List<AlertRule> = emptyList(),
    val selectedId: String? = null,
    val data: WatchDataRepository.Result? = null,
    val dataLoading: Boolean = false,
    val busy: Boolean = false,
    /** One-off feedback ("Couldn't save the note…"); cleared by [WatchListViewModel.dismissMessage]. */
    val message: String? = null
) {
    val signedIn: Boolean get() = session.user != null
    val watchlists: List<Watchlist> get() = lists.value?.watchlists.orEmpty()
    val model: WatchlistModel? get() = if (!signedIn || lists.value == null) null else
        WatchlistPresenter.build(watchlists, selectedId, data?.data, alerts, data?.takeIf { it.fromCache }?.savedAt ?: lists.savedAt.takeIf { lists.offline })
    val guestModel: WatchlistModel get() = WatchlistPresenter.build(
        listOf(Watchlist("guest", "My Stocks", 0, 0, 0, true, guestItems.mapIndexed { index, item ->
            WatchlistEntry(item.symbol, InstrumentRef(item.symbol, item.name, item.exchange, item.currency), index, item.addedAt)
        })), "guest", data?.data, emptyList(), data?.takeIf { it.fromCache }?.savedAt
    )
    val selected: Watchlist? get() = watchlists.firstOrNull { it.id == model?.selectedId }
}

/**
 * The Watchlist tab: signed-in lists come from the backend (cached for offline reading), quotes and
 * earnings from one batched market-data request per selected list. Nothing is shown as saved
 * before the server confirms; failures leave the list unchanged with a message.
 */
internal class WatchListViewModel(
    auth: AuthRepository,
    private val lists: UserWatchlistsRepository,
    private val alerts: AlertsRepository,
    private val watchData: WatchDataRepository,
    private val guest: WatchlistRepository,
    private val environment: StateFlow<String>
) : ViewModel() {
    private val local = MutableStateFlow(WatchListState())
    private var dataJob: Job? = null

    val state: StateFlow<WatchListState> = combine(local, auth.session, guest.snapshot, lists.state, alerts.state) { local, session, guestSnapshot, lists, alerts ->
        local.copy(
            session = session,
            guestItems = if (session.user == null && guestSnapshot.userId == null) guestSnapshot.items else emptyList(),
            lists = if (lists.uid == session.user?.id) lists else ServerBackedRepository.State(),
            alerts = if (alerts.uid == session.user?.id) alerts.value?.alerts.orEmpty() else emptyList()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WatchListState())

    init {
        // Reload quotes whenever the visible symbols, account or backend change.
        viewModelScope.launch {
            state.map { Triple(it.session.user?.id, symbolsOf(it), environment.value) }.distinctUntilChanged().collectLatest { (uid, symbols, env) ->
                loadData(uid, symbols, env)
            }
        }
    }

    private fun symbolsOf(state: WatchListState): List<String> =
        if (state.signedIn) state.selected?.entries?.map { it.instrument.symbol }.orEmpty() else state.guestItems.map { it.symbol }

    private fun loadData(uid: String?, symbols: List<String>, env: String) {
        dataJob?.cancel()
        if (symbols.isEmpty()) { local.update { it.copy(data = null, dataLoading = false) }; return }
        dataJob = viewModelScope.launch {
            local.update { it.copy(dataLoading = true) }
            val owner = uid?.let { userCacheOwner(env, it) } ?: "$env|guest"
            val result = try { watchData.load(owner, symbols) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                null
            }
            local.update { it.copy(data = result ?: it.data, dataLoading = false) }
        }
    }

    fun select(id: String) = local.update { it.copy(selectedId = id) }

    fun refresh() {
        viewModelScope.launch {
            if (state.value.signedIn) {
                launch { lists.refresh() }
                launch { alerts.refresh() }
            }
            loadData(state.value.session.user?.id, symbolsOf(state.value), environment.value)
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    fun createList(name: String, onCreated: () -> Unit = {}) = mutate {
        val before = state.value.watchlists.map { it.id }.toSet()
        val response = lists.create(name)
        response.watchlists.firstOrNull { it.id !in before }?.let { created -> local.update { it.copy(selectedId = created.id) } }
        onCreated()
    }

    fun renameList(id: String, name: String) = mutate { lists.rename(id, name) }
    fun deleteList(id: String) = mutate {
        lists.delete(id)
        local.update { if (it.selectedId == id) it.copy(selectedId = null) else it }
    }
    fun remove(entryId: String) = state.value.selected?.let { list -> mutate { lists.remove(list.id, entryId) } }
    fun saveNote(entryId: String, note: String?) = state.value.selected?.let { list -> mutate { lists.note(list.id, entryId, note) } }
    fun move(entryId: String, targetId: String, copy: Boolean) = state.value.selected?.let { list -> mutate { lists.move(list.id, entryId, targetId, copy) } }

    /** Moves an entry one place up or down (accessible alternative to drag and drop). */
    fun shift(entryId: String, by: Int) {
        val list = state.value.selected ?: return
        val ids = list.entries.map { it.id }.toMutableList()
        val from = ids.indexOf(entryId)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        mutate { lists.reorder(list.id, ids) }
    }

    fun removeGuest(symbol: String) = mutate { guest.remove(symbol) }

    /** One mutation at a time; failures keep the screen unchanged and show [WatchListState.message]. */
    private fun mutate(block: suspend () -> Unit) {
        if (state.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                local.update { it.copy(message = cause.message ?: "Something went wrong. Try again.") }
            } finally { local.update { it.copy(busy = false) } }
        }
    }
}

/** Result of submitting the alert form. */
internal sealed interface AlertSubmitResult {
    data object Created : AlertSubmitResult
    /** The price condition already holds: ask whether to notify now or wait for a cross. */
    data class AlreadyMet(val message: String) : AlertSubmitResult
    data class Failed(val message: String) : AlertSubmitResult
}

internal data class AlertsState(
    val signedIn: Boolean = false,
    val alerts: ServerBackedRepository.State<AlertsResponse> = ServerBackedRepository.State(),
    val filter: org.example.stocksteps.watchlist.AlertFilter = org.example.stocksteps.watchlist.AlertFilter.ACTIVE,
    /** Only this stock's alerts (opened from a watchlist row or a notification). */
    val symbol: String? = null,
    val busy: Boolean = false,
    val message: String? = null
) {
    val rules: List<AlertRule> get() = alerts.value?.alerts.orEmpty().filter { symbol == null || it.instrument.symbol == symbol }
    val history: List<AlertEvent> get() = alerts.value?.history.orEmpty().filter { symbol == null || it.symbol == symbol }
}

/** Alerts list and editor actions (create / pause / resume / edit / delete). */
internal class AlertsViewModel(
    auth: AuthRepository,
    private val repository: AlertsRepository,
    symbol: String?
) : ViewModel() {
    private val local = MutableStateFlow(AlertsState(symbol = symbol))
    val state: StateFlow<AlertsState> = combine(local, auth.session, repository.state) { local, session, alerts ->
        local.copy(signedIn = session.user != null, alerts = if (alerts.uid == session.user?.id) alerts else ServerBackedRepository.State())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlertsState(symbol = symbol))

    fun setFilter(filter: org.example.stocksteps.watchlist.AlertFilter) = local.update { it.copy(filter = filter) }
    fun refresh() { viewModelScope.launch { repository.refresh() } }
    fun dismissMessage() = local.update { it.copy(message = null) }
    fun pause(id: String) = update(id, UpdateAlertRequest(status = AlertStatus.PAUSED))
    fun resume(id: String) = update(id, UpdateAlertRequest(status = AlertStatus.ACTIVE))
    fun delete(id: String) = mutate { repository.delete(id) }
    fun update(id: String, request: UpdateAlertRequest) = mutate { repository.update(id, request) }

    suspend fun submit(request: CreateAlertRequest): AlertSubmitResult {
        if (local.value.busy) return AlertSubmitResult.Failed("Please wait…")
        local.update { it.copy(busy = true) }
        return try {
            repository.create(request)
            AlertSubmitResult.Created
        } catch (cause: UserDataRequestException) {
            val code = (cause.cause as? org.example.stocksteps.network.StockStepsApiException)?.error?.code
            if (code == "CONDITION_ALREADY_MET") AlertSubmitResult.AlreadyMet(cause.message ?: "") else AlertSubmitResult.Failed(cause.message ?: "Couldn't create the alert.")
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            AlertSubmitResult.Failed(cause.message ?: "Couldn't create the alert.")
        } finally { local.update { it.copy(busy = false) } }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                local.update { it.copy(message = cause.message ?: "Couldn't update the alert.") }
            } finally { local.update { it.copy(busy = false) } }
        }
    }
}
