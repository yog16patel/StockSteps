package org.example.stocksteps.data.watchlist

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.account.PlatformWatchlistGateway
import org.example.stocksteps.data.account.awaitAccountOperation
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.domain.WatchlistRepository
import org.example.stocksteps.domain.normalizedWatchlistSymbol
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.model.WatchlistSnapshot
import org.example.stocksteps.model.WatchlistItem
import org.example.stocksteps.model.WatchlistSyncState
import kotlin.time.Clock

/** One app-owned coordinator. SQL is the sole source observed by UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineWatchlistRepository(
    private val auth: AuthRepository,
    private val local: WatchlistLocalStore,
    private val cloud: PlatformWatchlistGateway,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val retryIntervalMillis: Long = 30_000
) : WatchlistRepository {
    private val mutableSync = MutableStateFlow(WatchlistSyncState())
    override val syncState = mutableSync.asStateFlow()
    private val retry = Channel<Unit>(Channel.CONFLATED)
    override val snapshot: Flow<WatchlistSnapshot> = auth.session.filter { !it.initializing }
        .map { it.user?.id }.distinctUntilChanged().flatMapLatest { uid ->
            local.observe(watchlistOwner(uid)).map { WatchlistSnapshot(uid, it) }
                .onStart { emit(WatchlistSnapshot(uid, emptyList())) }
        }
    override val items: Flow<List<WatchlistItem>> = snapshot.map { it.items }

    init {
        scope.launch {
            auth.session.filter { !it.initializing }.map { it.user?.id }.distinctUntilChanged().collectLatest { uid ->
                mutableSync.value = WatchlistSyncState()
                if (uid != null) {
                    while (currentCoroutineContext().isActive) {
                        try { synchronize(uid) }
                        catch (cause: Exception) {
                            if (cause is CancellationException) throw cause
                            mutableSync.update { it.copy(syncing = false, error = safeSyncError(cause)) }
                        }
                        delay(retryIntervalMillis)
                    }
                }
            }
        }
    }

    override suspend fun add(symbol: String) {
        val session = readySession()
        local.add(watchlistOwner(session.user?.id), normalizedWatchlistSymbol(symbol), now(), session.user != null)
    }
    override suspend fun remove(symbol: String) {
        val session = readySession()
        local.remove(watchlistOwner(session.user?.id), normalizedWatchlistSymbol(symbol), session.user != null)
    }
    private fun readySession(): AuthSession {
        val session = auth.session.value
        if (session.initializing) throw AccountException("Your account is still loading. Try again.")
        return session
    }
    override fun retrySync() { retry.trySend(Unit) }

    private suspend fun synchronize(uid: String) = coroutineScope {
        val owner = watchlistOwner(uid)
        local.mergeGuest(owner)
        val pending = local.observePending(owner).stateIn(this, SharingStarted.Eagerly, emptyList())
        // Snapshot collection is separate from writes: offline writes cannot block local UI.
        launch {
            while (isActive) {
                try {
                    snapshots(uid).collect { snapshot ->
                        // Cached and locally pending snapshots are never authoritative deletions.
                        if (snapshot.authoritative) {
                            local.applySnapshot(owner, snapshot.items)
                            mutableSync.update { it.copy(error = null) }
                        }
                    }
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    mutableSync.update { it.copy(error = safeSyncError(cause)) }
                }
                delay(retryIntervalMillis)
            }
        }
        launch {
            pending.collect { operations ->
                mutableSync.update { it.copy(pendingCount = operations.size) }
                retry.trySend(Unit)
            }
        }
        while (isActive) {
            withTimeoutOrNull(retryIntervalMillis) { retry.receive() }
            var failed = false
            for (operation in pending.value) {
                ensureActive()
                if (auth.session.value.user?.id != uid) return@coroutineScope
                mutableSync.update { it.copy(syncing = true, error = null) }
                try {
                    // SDK transactions have their own deadline. Do not start a second write
                    // while a first write may still commit after a coroutine timeout.
                    if (operation.item == null) awaitAccountOperation { cloud.delete(uid, operation.symbol, it) }
                    else awaitAccountOperation { cloud.put(uid, operation.item, it) }
                    ensureActive()
                    local.acknowledge(owner, operation)
                } catch (cause: Exception) {
                    if (cause is CancellationException && cause !is TimeoutCancellationException) throw cause
                    mutableSync.update { it.copy(error = safeSyncError(cause)) }
                    failed = true
                    break
                } finally {
                    mutableSync.update { it.copy(syncing = false) }
                }
            }
            // Drain self-generated retry events after failure, preventing a hot retry loop.
            if (failed) {
                retry.tryReceive()
                withTimeoutOrNull(retryIntervalMillis) { retry.receive() }
                retry.trySend(Unit)
            }
        }
    }

    private data class Snapshot(val items: List<WatchlistItem>, val authoritative: Boolean)
    private fun snapshots(uid: String): Flow<Snapshot> = callbackFlow {
        val subscription = cloud.observe(uid) { items, authoritative, error ->
            if (error != null) close(AccountException(error))
            else if (items != null) trySend(Snapshot(items, authoritative))
        }
        awaitClose { subscription.cancel() }
    }
    private fun safeSyncError(cause: Exception): String = if (cause is AccountException) cause.message
        ?: "Could not sync your watchlist." else "Sync is unavailable. Your changes are saved on this device."
}
