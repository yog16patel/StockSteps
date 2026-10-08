package org.example.stocksteps.data.userdata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.stocksteps.data.watchlist.WatchlistLocalStore
import org.example.stocksteps.data.watchlist.watchlistOwner
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.WatchlistRepository
import org.example.stocksteps.domain.normalizedWatchlistSymbol
import org.example.stocksteps.model.*

/** The platform's push token (FCM on Android, FCM/APNs on iOS); null until permission and registration. */
interface PushTokenSource {
    val token: StateFlow<String?>
    val platform: String
}

/**
 * Links this install's push token to the signed-in account on the current backend. Re-registers on
 * token rotation, account switch and Mock/Real switch; [unregister] runs before sign-out so a
 * previous user never receives the next user's alerts on a shared device.
 */
class DeviceRegistrar(
    private val auth: AuthRepository,
    private val api: UserApi,
    private val tokens: PushTokenSource,
    private val environment: StateFlow<String>,
    private val deviceId: suspend () -> String,
    private val scope: CoroutineScope
) {
    /** Last registration attempt result, for Settings ("notifications on this device"). */
    val registered = MutableStateFlow(false)

    fun start() {
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, tokens.token, environment) { uid, token, env -> Triple(uid, token, env) }
                .distinctUntilChanged()
                .collectLatest { (uid, token, _) ->
                    registered.value = false
                    if (uid == null || token == null) return@collectLatest
                    var wait = 2_000L
                    repeat(4) {
                        try {
                            api.registerDevice(RegisterDeviceRequest(deviceId(), token, tokens.platform))
                            registered.value = true
                            return@collectLatest
                        } catch (cause: Exception) {
                            if (cause is CancellationException) throw cause
                            delay(wait); wait *= 3
                        }
                    }
                }
        }
    }

    /** Best effort: the server also drops tokens FCM reports as invalid. */
    suspend fun unregister() {
        if (auth.session.value.user == null) return
        try { api.unregisterDevice(deviceId()) } catch (cause: Exception) { if (cause is CancellationException) throw cause }
        registered.value = false
    }
}

/**
 * The existing single-list contract (Home, Company Details, Search) on top of the new data:
 * signed out → the on-device guest list; signed in → the union of the user's server watchlists.
 * "Add" saves to the default list; "remove" removes the stock from every list. On sign-in, stocks
 * saved before (guest list merged into the account's local copy) are imported once into "My Stocks".
 */
class AccountWatchlistRepository(
    private val auth: AuthRepository,
    private val guest: WatchlistRepository,
    private val lists: UserWatchlistsRepository,
    private val local: WatchlistLocalStore,
    scope: CoroutineScope
) : WatchlistRepository {
    override val snapshot: Flow<WatchlistSnapshot> = combine(auth.session, guest.snapshot, lists.state) { session, guestSnapshot, state ->
        val uid = session.user?.id
        if (uid == null) guestSnapshot.copy(userId = null)
        else WatchlistSnapshot(uid, if (state.uid == uid) union(state.value) else emptyList())
    }.distinctUntilChanged()

    override val items: Flow<List<WatchlistItem>> = snapshot.map { it.items }

    override val syncState: StateFlow<WatchlistSyncState> = lists.state
        .map { WatchlistSyncState(syncing = it.loading, error = it.error?.takeIf { _ -> it.uid != null }) }
        .stateIn(scope, SharingStarted.Eagerly, WatchlistSyncState())

    init {
        scope.launch {
            lists.state.filter { it.uid != null && it.value != null && !it.offline }.map { it.uid!! }.distinctUntilChanged().collectLatest { uid ->
                val owner = watchlistOwner(uid)
                val saved = local.observe(owner).first()
                if (saved.isEmpty()) return@collectLatest
                try {
                    lists.import(saved.map { InstrumentRef(it.symbol, it.name, it.exchange, it.currency) })
                    local.clearOwner(owner)
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    // Kept on the device; retried the next time the lists load.
                }
            }
        }
    }

    override suspend fun add(symbol: String) = add(StockSearchResult(symbol, symbol))

    override suspend fun add(stock: StockSearchResult) {
        if (auth.session.value.user == null) return guest.add(stock)
        lists.addToDefault(InstrumentRef(normalizedWatchlistSymbol(stock.symbol), stock.name.takeIf { it != stock.symbol }, stock.exchange, stock.currency))
    }

    override suspend fun remove(symbol: String) {
        if (auth.session.value.user == null) return guest.remove(symbol)
        lists.removeEverywhere(normalizedWatchlistSymbol(symbol))
    }

    override fun retrySync() {
        guest.retrySync()
    }

    private fun union(response: WatchlistsResponse?): List<WatchlistItem> = response?.watchlists.orEmpty()
        .flatMap { it.entries }.distinctBy { it.instrument.symbol }
        .map { WatchlistItem(it.instrument.symbol, it.addedAt, it.addedAt, it.instrument.name, it.instrument.exchange, it.instrument.currency) }
}
