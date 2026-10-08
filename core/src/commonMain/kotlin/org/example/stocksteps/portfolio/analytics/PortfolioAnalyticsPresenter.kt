package org.example.stocksteps.portfolio.analytics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioRepository

data class InsightsUiState(
    val signedIn: Boolean = false,
    val accounts: List<PortfolioAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val accountName: String = "",
    val period: AnalyticsPeriod = AnalyticsPeriod.ONE_YEAR,
    val benchmark: BenchmarkId = BenchmarkId.TSX,
    val benchmarks: List<BenchmarkInfo> = BenchmarkCatalog.all,
    val loading: Boolean = false,
    val error: String? = null,
    val entitlements: Entitlements? = null,
    val view: InsightsView? = null,
    /** MOCK backend only: deterministic scenarios and plan simulation. */
    val mockAvailable: Boolean = false,
    val scenario: String? = null,
    val scenarios: List<String> = AnalyticsFixtureCatalog.ids
) {
    val plus: Boolean get() = (view?.tier ?: entitlements?.tier) == SubscriptionTier.PLUS
}

/**
 * Portfolio Intelligence presenter shared by Android and iOS. The account selection is the Portfolio
 * tab's (one source of truth); analytics come from the server, which enforces the plan. Inputs are
 * keyed so recompositions never recompute anything.
 */
class PortfolioAnalyticsPresenter(
    private val repository: PortfolioRepository,
    private val entitlements: EntitlementsRepository,
    private val scope: CoroutineScope,
    private val computation: CoroutineDispatcher = Dispatchers.Default
) {
    /** Choices belong to one owner ("uid|environment"); another owner's choices are never applied. */
    private data class Choices(
        val owner: String?,
        val period: AnalyticsPeriod = AnalyticsPeriod.ONE_YEAR,
        val benchmark: BenchmarkId? = null,
        val scenario: String? = null,
        /** False until the owner's saved benchmark has been read, so the first request uses it. */
        val ready: Boolean = false
    )
    private val choices = MutableStateFlow(Choices(null))
    private val refreshes = MutableStateFlow(0)
    private val mutable = MutableStateFlow(InsightsUiState())
    /** Who the current view belongs to; a different owner never sees it, even briefly. */
    private var viewOwner: String? = null
    val state: StateFlow<InsightsUiState> = mutable.asStateFlow()

    private data class Key(val uid: String?, val environment: String?, val account: String?, val revision: Long?, val period: AnalyticsPeriod,
                           val benchmark: BenchmarkId?, val scenario: String?, val tier: SubscriptionTier?, val refresh: Int, val ready: Boolean)

    private fun ownerOf(uid: String?, environment: String?) = uid?.let { "$it|$environment" }
    private fun currentOwner() = repository.state.value.let { ownerOf(it.uid, it.environment) }

    init {
        // A new owner or environment starts from defaults plus that owner's saved benchmark.
        scope.launch {
            repository.state.map { ownerOf(it.uid, it.environment) }.distinctUntilChanged().collectLatest { owner ->
                choices.value = Choices(owner, ready = owner == null)
                if (owner != null) {
                    val saved = repository.benchmarkChoice()
                    choices.update { if (it.owner == owner) it.copy(benchmark = it.benchmark ?: saved, ready = true) else it }
                }
            }
        }
        scope.launch {
            combine(repository.snapshot, repository.state, entitlements.state, choices, refreshes) { snapshot, resource, plan, stored, refresh ->
                val owner = ownerOf(resource.uid, resource.environment)
                // The ledger snapshot can trail a sign-out by a moment: never show it without an owner.
                val ledger = if (owner == null) org.example.stocksteps.portfolio.PortfolioLedger() else snapshot.ledger
                val selected = snapshot.selectedAccountId?.takeIf { owner != null }
                val account = ledger.accounts.find { it.id == selected }
                val chosen = stored.takeIf { it.owner == owner } ?: Choices(owner)
                mutable.update { current ->
                    current.copy(
                        view = current.view?.takeIf { owner != null && owner == viewOwner },
                        error = current.error.takeIf { owner != null && owner == viewOwner },
                        signedIn = resource.uid != null,
                        accounts = ledger.accounts.filterNot { it.archived },
                        selectedAccountId = selected,
                        accountName = account?.name.orEmpty(),
                        period = chosen.period,
                        benchmark = chosen.benchmark ?: BenchmarkCatalog.default(account?.reportingCurrency ?: org.example.stocksteps.portfolio.PortfolioCurrency.CAD),
                        entitlements = plan,
                        mockAvailable = resource.environment == "mock",
                        scenario = chosen.scenario
                    )
                }
                Key(resource.uid, resource.environment, selected, resource.value?.revision, chosen.period,
                    chosen.benchmark, chosen.scenario, plan?.tier, refresh, chosen.ready)
            }.distinctUntilChanged().collectLatest(::load)
        }
    }

    private suspend fun load(key: Key) {
        val owner = key.uid?.let { "$it|${key.environment}" }
        val fixture = key.scenario?.takeIf { key.environment == "mock" }
        if (fixture != null) {
            mutable.update { it.copy(loading = true, error = null) }
            val analytics = withContext(computation) { AnalyticsFixtureCatalog.get(fixture).analytics(key.period, key.benchmark) }
            viewOwner = owner
            mutable.update { it.copy(loading = false, view = InsightsFormatter.view(analytics), entitlements = AnalyticsFixtureCatalog.get(fixture).entitlements,
                accountName = "Sample · ${AnalyticsFixtureCatalog.get(fixture).title}") }
            return
        }
        if (!key.ready) {
            mutable.update { it.copy(loading = true) }
            return
        }
        if (key.uid == null || key.account == null) {
            viewOwner = null
            mutable.update { it.copy(loading = false, view = null, error = null) }
            return
        }
        mutable.update { current -> current.copy(loading = true, error = null, view = current.view?.takeIf { it.accountId == key.account && viewOwner == owner }) }
        try {
            val analytics = repository.analytics(key.account, key.period, key.benchmark, key.tier?.name)
            val view = withContext(computation) { InsightsFormatter.view(analytics) }
            viewOwner = owner
            mutable.update { it.copy(loading = false, view = view) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(loading = false, error = "Insights couldn't be loaded. Your holdings and transactions are unaffected.") }
        }
    }

    private fun choose(change: (Choices) -> Choices) {
        val owner = currentOwner()
        choices.update { current -> change(if (current.owner == owner) current else Choices(owner, ready = true)) }
    }

    fun selectAccount(id: String) { choose { it.copy(scenario = null) }; repository.selectAccount(id) }
    fun selectPeriod(value: AnalyticsPeriod) = choose { it.copy(period = value) }
    fun selectBenchmark(id: BenchmarkId) {
        choose { it.copy(benchmark = id) }
        if (choices.value.scenario == null) scope.launch { repository.saveBenchmarkChoice(id) }
    }
    fun refresh() {
        scope.launch {
            repository.invalidateMarketCache()
            entitlements.refresh()
            refreshes.value++
        }
    }

    /** MOCK only: show a read-only scenario (null returns to your own accounts). */
    fun showScenario(id: String?) {
        if (repository.state.value.environment != "mock") return
        choose { it.copy(scenario = id?.takeIf { value -> value in AnalyticsFixtureCatalog.ids }) }
    }

    /** MOCK only: simulate a StockSteps+ plan on the mock backend (Settings → Development). */
    fun simulatePlan(tier: SubscriptionTier, expired: Boolean = false) {
        if (repository.state.value.environment != "mock") return
        scope.launch {
            try {
                entitlements.simulate(tier, expired)
                repository.invalidateMarketCache()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(error = "Couldn't change the simulated plan: ${cause.message ?: "the mock server didn't respond"}.") }
            }
        }
    }
}
