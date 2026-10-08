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
    private val auth: AuthRepository,
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
    suspend fun report(accountId: String, range: String? = null, expectedUid: String? = null): PortfolioReport = marketLock.withLock {
        val resource = state.value
        val uid = resource.uid ?: throw IllegalStateException("Sign in to view your portfolio.")
        val environment = resource.environment ?: throw IllegalStateException("Choose a backend.")
        if (expectedUid != null && expectedUid != uid) throw CancellationException("Account changed")
        val owner = userCacheOwner(environment, uid)
        val key = "portfolio.market.$accountId.${range ?: "summary"}"
        val memoryKey = "$owner:$key:${resource.value?.revision}"
        val time = Clock.System.now().toEpochMilliseconds()
        marketCache[memoryKey]?.takeIf { time - it.first < 60_000 }?.let { return@withLock it.second }
        try {
            ensureOwner(uid, environment)
            val result = api.portfolioReport(accountId, range, expectedOwner = uid)
            ensureOwner(uid, environment)
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
    suspend fun invalidateMarketCache() {
        marketLock.withLock { marketCache.clear() }
        analyticsLock.withLock { analyticsCache.clear() }
    }

    private val analyticsLock = Mutex()
    private val analyticsCache = LinkedHashMap<String, Pair<Long, org.example.stocksteps.portfolio.analytics.PortfolioAnalytics>>()

    /**
     * Server-computed Insights for one account (the server enforces the tier). Memory-cached for a
     * minute per owner, ledger revision, period and benchmark; the last result for the same revision
     * is shown offline. Never mixes accounts, users or Mock/Real.
     */
    suspend fun analytics(accountId: String, period: org.example.stocksteps.portfolio.analytics.AnalyticsPeriod, benchmark: org.example.stocksteps.portfolio.analytics.BenchmarkId?, tier: String?, expectedUid: String? = null, expectedEnvironment: String? = null): org.example.stocksteps.portfolio.analytics.PortfolioAnalytics = analyticsLock.withLock {
        val serializer = org.example.stocksteps.portfolio.analytics.PortfolioAnalytics.serializer()
        val resource = state.value
        val uid = resource.uid ?: throw IllegalStateException("Sign in to view your portfolio.")
        val environment = resource.environment ?: throw IllegalStateException("Choose a backend.")
        // The caller's choices (account, benchmark, period) belong to one user: never apply them to another.
        if (expectedUid != null && expectedUid != uid || expectedEnvironment != null && expectedEnvironment != environment) throw CancellationException("Account changed")
        val owner = userCacheOwner(environment, uid)
        val key = "portfolio.analytics.$accountId.${period.label}.${benchmark?.name}"
        val memoryKey = "$owner:$key:${resource.value?.revision}:$tier"
        val time = Clock.System.now().toEpochMilliseconds()
        analyticsCache[memoryKey]?.takeIf { time - it.first < 60_000 }?.let { return@withLock it.second }
        try {
            ensureOwner(uid, environment)
            val result = api.portfolioAnalytics(accountId, period, benchmark, expectedOwner = uid)
            ensureOwner(uid, environment)
            analyticsCache[memoryKey] = time to result
            if (analyticsCache.size > 24) analyticsCache.remove(analyticsCache.keys.first())
            cache.write(owner, key, json.encodeToString(serializer, result), time)
            result
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            val cached = cache.read(owner, key)?.first?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            cached?.takeIf { it.revision == resource.value?.revision && it.tier.name == tier }
                ?.let { it.copy(notes = listOf("Showing your last saved Insights; they couldn't be refreshed.") + it.notes) } ?: throw cause
        }
    }

    /**
     * The signed-in identity can change before this repository's state catches up; a request (or a
     * cache write) for [uid] must never run under another user's token.
     */
    private fun ensureOwner(uid: String, environment: String) {
        if (auth.session.value.user?.id != uid || state.value.uid != uid || state.value.environment != environment) throw CancellationException("Account changed")
    }

    /** The benchmark the user last chose, per account owner and environment (not synced; a display preference). */
    suspend fun benchmarkChoice(): org.example.stocksteps.portfolio.analytics.BenchmarkId? {
        val resource = state.value
        val owner = userCacheOwner(resource.environment ?: return null, resource.uid ?: return null)
        return org.example.stocksteps.portfolio.analytics.BenchmarkCatalog.parse(cache.read(owner, "portfolio.benchmark")?.first)
    }
    suspend fun saveBenchmarkChoice(id: org.example.stocksteps.portfolio.analytics.BenchmarkId) {
        val resource = state.value
        val owner = userCacheOwner(resource.environment ?: return, resource.uid ?: return)
        cache.write(owner, "portfolio.benchmark", id.name, Clock.System.now().toEpochMilliseconds())
    }
    override suspend fun fetch() = api.portfolio()
    fun selectAccount(id: String) { selected.value = id }
    suspend fun saveAccount(account: PortfolioAccount) = mutate { api.savePortfolioAccount(account) }
    suspend fun deleteAccount(id: String) = mutate { api.deletePortfolioAccount(id) }
    suspend fun saveTransaction(transaction: PortfolioTransaction, edit: Boolean = false) = mutate { api.savePortfolioTransaction(transaction, edit) }
    suspend fun deleteTransaction(id: String) = mutate { api.deletePortfolioTransaction(id) }
}
