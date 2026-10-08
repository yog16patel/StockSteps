package org.example.stocksteps.portfolio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.AuthRepository

data class PortfolioSnapshot(
    val ledger: PortfolioLedger = PortfolioLedger(),
    val selectedAccountId: String? = null,
    val position: PortfolioPositionState? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false
)

/** Separate account resource and cache key. Neither watchlist membership nor alerts affect it. */
class PortfolioRepository(
    auth: AuthRepository,
    private val api: UserApi,
    private val cache: UserDataCache,
    environment: StateFlow<String>,
    private val scope: CoroutineScope
) : ServerBackedRepository<PortfolioLedger>(auth, cache, environment, scope, PortfolioLedger.serializer(), "portfolio.ledger.v1") {
    private val selected = MutableStateFlow<String?>(null)
    private val mutableSnapshot = MutableStateFlow(PortfolioSnapshot())
    val snapshot: StateFlow<PortfolioSnapshot> = mutableSnapshot.asStateFlow()
    private val marketLock = Mutex()
    private val marketCache = LinkedHashMap<String, Pair<Long, PortfolioReport>>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    init {
        scope.launch {
            var previousOwner: Pair<String?, String?>? = null
            var cachedLedger: PortfolioLedger? = null
            var cachedAccount: String? = null
            var cachedPosition: PortfolioPositionState? = null
            combine(state, selected) { resource, selection ->
                val owner = resource.uid to resource.environment
                val safeSelection = selection.takeIf { owner == previousOwner }
                if (owner != previousOwner) selected.value = null
                previousOwner = owner
                val ledger = resource.value ?: PortfolioLedger()
                val id = safeSelection?.takeIf { requested -> ledger.accounts.any { it.id == requested && !it.archived } }
                    ?: ledger.accounts.firstOrNull { !it.archived }?.id
                if (ledger != cachedLedger || id != cachedAccount) {
                    cachedPosition = id?.let { PortfolioEngine.replay(ledger, it) }
                    cachedLedger = ledger
                    cachedAccount = id
                }
                PortfolioSnapshot(ledger, id, cachedPosition, resource.loading, resource.offline, resource.error, resource.uid != null)
            }.collect { mutableSnapshot.value = it }
        }
    }
    suspend fun report(accountId: String, range: String? = null): PortfolioReport = marketLock.withLock {
        val resource = state.value
        val uid = resource.uid ?: throw IllegalStateException("Sign in to view your portfolio.")
        val environment = resource.environment ?: throw IllegalStateException("Choose a backend.")
        val owner = userCacheOwner(environment, uid)
        val key = "portfolio.market.$accountId.${range ?: "summary"}"
        val memoryKey = "$owner:$key:${resource.value?.revision}"
        val time = Clock.System.now().toEpochMilliseconds()
        marketCache[memoryKey]?.takeIf { time - it.first < 60_000 }?.let { return@withLock it.second }
        try {
            val result = api.portfolioReport(accountId, range)
            if (state.value.uid != uid || state.value.environment != environment) throw CancellationException("Account changed")
            marketCache[memoryKey] = time to result
            if (marketCache.size > 32) marketCache.remove(marketCache.keys.first())
            cache.write(owner, key, json.encodeToString(PortfolioReport.serializer(), result), time)
            result
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            val cached = cache.read(owner, key)?.first?.let { runCatching { json.decodeFromString(PortfolioReport.serializer(), it) }.getOrNull() }
            cached?.takeIf { it.revision == resource.value?.revision }?.copy(notice = "Cached market observations. ${cached.notice}") ?: throw cause
        }
    }
    suspend fun invalidateMarketCache() = marketLock.withLock { marketCache.clear() }
    override suspend fun fetch() = api.portfolio()
    fun selectAccount(id: String) { selected.value = id }
    suspend fun saveAccount(account: PortfolioAccount) = mutate { api.savePortfolioAccount(account) }
    suspend fun deleteAccount(id: String) = mutate { api.deletePortfolioAccount(id) }
    suspend fun saveTransaction(transaction: PortfolioTransaction, edit: Boolean = false) = mutate { api.savePortfolioTransaction(transaction, edit) }
    suspend fun deleteTransaction(id: String) = mutate { api.deletePortfolioTransaction(id) }
}
