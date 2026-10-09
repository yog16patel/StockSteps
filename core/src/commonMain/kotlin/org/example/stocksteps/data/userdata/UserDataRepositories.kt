package org.example.stocksteps.data.userdata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Last server copy of a signed-in user's data, for reading while offline. Cleared on sign-out. */
interface UserDataCache {
    suspend fun read(owner: String, key: String): Pair<String, Long>?
    suspend fun write(owner: String, key: String, json: String, savedAt: Long)

    /** Removes every environment's copy for this account. */
    suspend fun clearAccount(uid: String)
}

class InMemoryUserDataCache : UserDataCache {
    private val lock = Mutex()
    private val values = HashMap<Pair<String, String>, Pair<String, Long>>()
    override suspend fun read(owner: String, key: String) = lock.withLock { values[owner to key] }
    override suspend fun write(owner: String, key: String, json: String, savedAt: Long) { lock.withLock { values[owner to key] = json to savedAt } }
    override suspend fun clearAccount(uid: String) { lock.withLock { values.keys.removeAll { it.first.endsWith("|user:$uid") } } }
}

/** Cache namespace: environment and account, so Mock/Real and different users never share data. */
fun userCacheOwner(environment: String, uid: String) = "$environment|user:$uid"

/** A user-facing message for a failed request (no provider or server details). */
fun userDataError(cause: Throwable): String = when (cause) {
    is StockStepsApiException -> cause.error.message
    is org.example.stocksteps.domain.AccountException -> cause.message ?: "Sign in again to continue."
    else -> "You're offline or the server can't be reached. Try again."
}

/**
 * Generic signed-in resource (watchlists, alerts): the server is the source of truth; the last
 * response is cached per environment+account and shown (marked offline) when the server can't be
 * reached. Mutations are sent one at a time; nothing is shown as saved until the server confirms.
 */
@OptIn(ExperimentalTime::class)
abstract class ServerBackedRepository<T : Any>(
    private val auth: AuthRepository,
    private val cache: UserDataCache,
    private val environment: StateFlow<String>,
    private val scope: CoroutineScope,
    private val serializer: KSerializer<T>,
    private val cacheKey: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    data class State<T>(
        val uid: String? = null,
        val environment: String? = null,
        val value: T? = null,
        val loading: Boolean = false,
        /** Showing the cached copy because the latest request failed (or hasn't finished). */
        val offline: Boolean = false,
        val savedAt: Long? = null,
        val error: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(State<T>())
    val state: StateFlow<State<T>> = mutable.asStateFlow()
    private val writes = Mutex()

    protected abstract suspend fun fetch(): T

    fun start() {
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, environment) { uid, env -> uid to env }
                .distinctUntilChanged()
                .collectLatest { (uid, env) ->
                    // Loading from the moment the account is known: callers that skip a refresh while one is running (Home's onVisible) must not
                    // see an idle repository during the cache read and fetch the same data a second time (Phase 5C.1: 2× watchlists/alerts per launch).
                    mutable.value = State(uid = uid, environment = env, loading = uid != null)
                    if (uid == null) return@collectLatest
                    cache.read(userCacheOwner(env, uid), cacheKey)?.let { (text, savedAt) ->
                        runCatching { json.decodeFromString(serializer, text) }.getOrNull()?.let { cached ->
                            mutable.update { it.copy(value = cached, offline = true, savedAt = savedAt) }
                        }
                    }
                    refresh()
                }
        }
    }

    suspend fun refresh() {
        val (uid, env) = mutable.value.uid to mutable.value.environment
        if (uid == null || env == null || auth.session.value.user?.id != uid || environment.value != env) return
        mutable.update { it.copy(loading = true, error = null) }
        try {
            apply(uid, env, fetch())
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { if (it.uid == uid && it.environment == env) it.copy(loading = false, offline = it.value != null, error = userDataError(cause)) else it }
        }
    }

    /** Runs a server mutation; the response replaces the state. Throws (with a user message) on failure. */
    protected suspend fun mutate(request: suspend () -> T): T = writes.withLock {
        val uid = mutable.value.uid ?: throw IllegalStateException("Sign in to make changes.")
        val env = mutable.value.environment ?: throw IllegalStateException("Sign in to make changes.")
        if (auth.session.value.user?.id != uid || environment.value != env) throw CancellationException("Account or environment changed")
        val response = try { request() } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            throw UserDataRequestException(userDataError(cause), cause)
        }
        apply(uid, env, response)
        response
    }

    private suspend fun apply(uid: String, env: String, value: T) {
        // A response for a previous account or environment is never shown.
        if (mutable.value.uid != uid || mutable.value.environment != env || auth.session.value.user?.id != uid || environment.value != env) return
        val time = now()
        mutable.update { it.copy(value = value, loading = false, offline = false, savedAt = time, error = null) }
        cache.write(userCacheOwner(env, uid), cacheKey, json.encodeToString(serializer, value), time)
        if (auth.session.value.user?.id != uid) cache.clearAccount(uid)
    }
}

class UserDataRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UserWatchlistsRepository(
    auth: AuthRepository, private val api: UserApi, cache: UserDataCache, environment: StateFlow<String>, scope: CoroutineScope
) : ServerBackedRepository<WatchlistsResponse>(auth, cache, environment, scope, WatchlistsResponse.serializer(), "watchlists") {
    override suspend fun fetch() = api.watchlists()
    suspend fun create(name: String) = mutate { api.createWatchlist(name) }
    suspend fun rename(id: String, name: String) = mutate { api.renameWatchlist(id, name) }
    suspend fun delete(id: String) = mutate { api.deleteWatchlist(id) }
    suspend fun add(listId: String, instrument: InstrumentRef) = mutate { api.addEntry(listId, instrument) }
    suspend fun remove(listId: String, entryId: String) = mutate { api.removeEntry(listId, entryId) }
    suspend fun note(listId: String, entryId: String, note: String?) = mutate { api.updateNote(listId, entryId, note) }
    suspend fun move(listId: String, entryId: String, targetId: String, copy: Boolean) = mutate { api.moveEntry(listId, entryId, targetId, copy) }
    suspend fun reorder(listId: String, ids: List<String>) = mutate { api.reorderEntries(listId, ids) }
    suspend fun import(instruments: List<InstrumentRef>) = mutate { api.importEntries(instruments) }

    /** Adds to the default list (Company Details / Search "Add to Watchlist" with one list). */
    suspend fun addToDefault(instrument: InstrumentRef) {
        val default = state.value.value?.watchlists?.let { lists -> lists.firstOrNull { it.isDefault } ?: lists.firstOrNull() }
            ?: refresh().let { state.value.value?.watchlists?.firstOrNull() }
            ?: throw UserDataRequestException(state.value.error ?: "Your watchlists couldn't be loaded.")
        add(default.id, instrument)
    }

    /** Removes the stock from every list that contains it (the Company Details "Saved" toggle). */
    suspend fun removeEverywhere(symbol: String) {
        state.value.value?.watchlists.orEmpty().forEach { list ->
            list.entries.firstOrNull { it.instrument.symbol == symbol }?.let { remove(list.id, it.id) }
        }
    }
}

class AlertsRepository(
    auth: AuthRepository, private val api: UserApi, cache: UserDataCache, environment: StateFlow<String>, scope: CoroutineScope
) : ServerBackedRepository<AlertsResponse>(auth, cache, environment, scope, AlertsResponse.serializer(), "alerts") {
    override suspend fun fetch() = api.alerts()
    suspend fun create(request: CreateAlertRequest) = mutate { api.createAlert(request) }
    suspend fun update(id: String, request: UpdateAlertRequest) = mutate { api.updateAlert(id, request) }
    suspend fun delete(id: String) = mutate { api.deleteAlert(id) }
}

/** Quotes and earnings for watched symbols (public data); the last result is cached per account for offline use. */
@OptIn(ExperimentalTime::class)
class WatchDataRepository(
    private val api: StockStepsApi,
    private val cache: UserDataCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    data class Result(val data: WatchDataResponse, val fromCache: Boolean, val savedAt: Long)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val loads = Mutex()
    private val memory = LinkedHashMap<String, Result>()

    /** Fresh data when reachable; otherwise the cached copy (fromCache) if it covers the request; otherwise throws. */
    suspend fun load(owner: String, symbols: List<String>, force: Boolean = false): Result = loads.withLock {
        if (symbols.isEmpty()) throw IllegalArgumentException("No symbols")
        val cached = memory[owner]
        if (!force && cached != null && now() - cached.savedAt < 60_000 && symbols.all { symbol -> cached.data.quotes.any { it.symbol == symbol } }) {
            return@withLock cached.copy(data = cached.data.copy(quotes = cached.data.quotes.filter { it.symbol in symbols }))
        }
        try {
            val data = api.getWatchData(symbols)
            val time = now()
            cache.write(owner, KEY, json.encodeToString(WatchDataResponse.serializer(), data), time)
            Result(data, fromCache = false, savedAt = time).also {
                memory[owner] = it
                if (memory.size > 8) memory.remove(memory.keys.first())
            }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            val (text, savedAt) = cache.read(owner, KEY) ?: throw cause
            val cached = runCatching { json.decodeFromString(WatchDataResponse.serializer(), text) }.getOrNull() ?: throw cause
            if (symbols.any { symbol -> cached.quotes.none { it.symbol == symbol } }) throw cause
            Result(cached.copy(quotes = cached.quotes.filter { it.symbol in symbols }), fromCache = true, savedAt = savedAt)
        }
    }

    private companion object { const val KEY = "watchdata" }
}
