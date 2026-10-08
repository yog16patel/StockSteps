package org.example.stocksteps.portfolio

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.WatchDataRepository
import org.example.stocksteps.data.userdata.userCacheOwner
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.WatchQuote
import kotlin.random.Random
import kotlin.time.Clock

data class PortfolioHoldingRow(
    val symbol: String, val name: String, val exchange: String?, val currency: String,
    val quantity: String, val price: String?, val value: String?, val basis: String,
    val gain: String?, val gainPercent: String?, val realized: String, val dividends: String,
    val logoUrl: String?, val stale: Boolean
)
data class PortfolioUiState(
    val accounts: List<PortfolioAccount> = emptyList(), val selectedAccountId: String? = null,
    val accountName: String = "Your portfolio", val currency: String = "CAD",
    val total: String? = null, val basis: String? = null, val unrealized: String? = null,
    val realized: String? = null, val cash: List<String> = emptyList(),
    val holdings: List<PortfolioHoldingRow> = emptyList(), val transactions: List<PortfolioTransaction> = emptyList(),
    val loading: Boolean = false, val offline: Boolean = false, val error: String? = null,
    val notice: String? = null, val signedIn: Boolean = false, val busy: Boolean = false,
    val history: List<PortfolioValuation> = emptyList(), val historyLoading: Boolean = false,
    val historyRange: String = "1M",
    val dailyGain: String? = null, val dailyPercent: String? = null, val dailyNotice: String = "Daily return needs dated prices and cash flows.",
    val mockAvailable: Boolean = false, val mockScenario: String? = null,
    val allocations: List<PortfolioAllocation> = emptyList(),
    val combinedTotal: String? = null,
    val dividends: List<String> = emptyList(),
    val currencyAllocations: List<PortfolioCurrencyAllocation> = emptyList(),
    val quoteAsOf: String? = null, val fxAsOf: String? = null,
    val today: String = "", val historyNotice: String = "Historical values require dated prices and FX; no estimated history is shown."
)

/** One app-owned presenter, used by Portfolio and Home on both platforms. */
class PortfolioPresenter(
    private val repository: PortfolioRepository,
    private val quotes: WatchDataRepository,
    private val scope: CoroutineScope
) {
    private val quoteState = MutableStateFlow<List<WatchQuote>>(emptyList())
    private val failure = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val report = MutableStateFlow<PortfolioReport?>(null)
    private val range = MutableStateFlow<String?>(null)
    private val historyLoading = MutableStateFlow(false)
    private val scenario = MutableStateFlow<String?>(null)
    private val scenarioAccount = MutableStateFlow<String?>(null)
    private val marketRefresh = MutableStateFlow(0L)
    private val allReports = MutableStateFlow<List<PortfolioReport>>(emptyList())
    private val mutable = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = mutable.asStateFlow()
    init {
        scope.launch {
            combine(repository.state, repository.snapshot, range, marketRefresh) { resource, snapshot, range, refresh ->
                listOf(resource.uid, resource.environment, snapshot.selectedAccountId, resource.value?.revision?.toString(), range, refresh.toString())
            }.distinctUntilChanged().collectLatest { key ->
                report.value = null
                if (scenario.value != null && repository.state.value.environment == "mock") return@collectLatest
                val account = key[2] ?: return@collectLatest
                if (key[0] == null) return@collectLatest
                historyLoading.value = key[4] != null
                try { report.value = repository.report(account, key[4]) }
                catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    failure.value = "Market valuation unavailable. Your ledger remains available."
                } finally { historyLoading.value = false }
            }
        }
        scope.launch {
            combine(repository.state, marketRefresh) { resource, refresh ->
                listOf(resource.uid, resource.environment, resource.value?.revision?.toString(), resource.value?.accounts?.joinToString { it.id }, refresh.toString())
            }.distinctUntilChanged().collectLatest {
                allReports.value = emptyList()
                val accounts = repository.state.value.value?.accounts.orEmpty().filterNot { it.archived }
                if (accounts.size > 1) {
                    allReports.value = accounts.mapNotNull { account ->
                        try { repository.report(account.id) } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }
                    }
                }
            }
        }
        scope.launch {
            repository.state.map { resource -> Triple(resource.uid, resource.environment, resource.value?.transactions?.mapNotNull { it.instrument?.symbol }?.distinct()?.sorted().orEmpty()) }
                .distinctUntilChanged().collectLatest { (uid, environment, symbols) ->
                    quoteState.value = emptyList()
                    if (uid != null && environment != null && symbols.isNotEmpty()) {
                        try { quoteState.value = symbols.chunked(100).flatMap { quotes.load(userCacheOwner(environment, uid), it).data.quotes } }
                        catch (cause: Exception) { if (cause is CancellationException) throw cause; failure.value = "Quotes unavailable. Your transactions are saved." }
                    }
                }
        }
        scope.launch {
            val base = combine(repository.snapshot, quoteState, failure, busy) { snapshot, prices, error, working ->
                present(snapshot, prices).copy(error = error ?: snapshot.error, busy = working)
            }
            val marketState = combine(base, report, historyLoading, allReports) { base, market, loading, reports ->
                val valid = market?.takeIf { it.accountId == base.selectedAccountId && it.revision == repository.snapshot.value.ledger.revision }
                base.copy(total = if (valid != null) valid.summary.totalValue else base.total,
                    basis = if (valid != null) valid.summary.investedCost else base.basis,
                    unrealized = if (valid != null) valid.summary.unrealizedGain else base.unrealized,
                    realized = if (valid != null) valid.summary.realizedGain else base.realized,
                    notice = valid?.let { it.summary.missing.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Unavailable: ") },
                    history = valid?.history.orEmpty(), historyLoading = loading, historyRange = range.value ?: "1M",
                    historyNotice = valid?.notice ?: base.historyNotice,
                    dailyGain = valid?.daily?.gain, dailyPercent = valid?.daily?.percent, dailyNotice = valid?.daily?.notice ?: base.dailyNotice,
                    allocations = valid?.allocations.orEmpty(), combinedTotal = combined(base, valid, reports),
                    currencyAllocations = valid?.currencyAllocations.orEmpty(), quoteAsOf = valid?.quoteAsOf, fxAsOf = valid?.fxAsOf)
            }
            combine(marketState, scenario, scenarioAccount, range) { base, sample, selected, period ->
                if (sample == null || repository.state.value.environment != "mock") base.copy(mockAvailable = repository.state.value.environment == "mock")
                else {
                    val fixture = PortfolioFixtureCatalog.get(sample)
                    val account = selected?.takeIf { id -> fixture.ledger.accounts.any { it.id == id } } ?: fixture.selectedAccount
                    val snapshot = PortfolioSnapshot(fixture.ledger, account, account?.let { PortfolioEngine.replay(fixture.ledger, it) })
                    val values = present(snapshot, fixture.quotes)
                    val report = account?.let { fixture.report(it) }
                    values.copy(total = report?.summary?.totalValue, basis = report?.summary?.investedCost,
                        unrealized = report?.summary?.unrealizedGain, realized = report?.summary?.realizedGain,
                        history = report?.history.orEmpty().filter { it.date >= when (period) {
                            "1D" -> "2026-10-08"; "1W" -> "2026-10-01"; "1M" -> "2026-09-08"; "3M" -> "2026-07-08"; "1Y" -> "2025-10-08"; else -> "1900-01-01"
                        } }, historyRange = period ?: "ALL", historyNotice = report?.notice ?: "Read-only sample; no saved data is changed.",
                        dailyGain = report?.daily?.gain, dailyPercent = report?.daily?.percent, dailyNotice = report?.daily?.notice.orEmpty(),
                        notice = report?.summary?.missing?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Unavailable: "),
                        allocations = report?.allocations.orEmpty(), currencyAllocations = report?.currencyAllocations.orEmpty(), quoteAsOf = report?.quoteAsOf, fxAsOf = report?.fxAsOf, combinedTotal = combined(values, report, fixture.ledger.accounts.map { fixture.report(it.id) }),
                        mockAvailable = true, mockScenario = sample, busy = true, signedIn = base.signedIn)
                }
            }.collect { mutable.value = it }
        }
    }
    fun selectAccount(id: String) {
        if (scenario.value != null && repository.state.value.environment == "mock") scenarioAccount.value = id else repository.selectAccount(id)
    }
    fun showScenario(id: String?) {
        require(id == null || repository.state.value.environment == "mock" && id in PortfolioFixtureCatalog.ids)
        scenarioAccount.value = null
        scenario.value = id
        if (id == null) marketRefresh.value++
    }
    fun loadHistory(period: String) {
        require(period in setOf("1D", "1W", "1M", "3M", "1Y", "ALL"))
        range.value = period
    }
    fun refresh() = command { repository.refresh(); refreshQuotes(); repository.invalidateMarketCache(); marketRefresh.value++ }
    fun saveAccount(account: PortfolioAccount) = command { repository.saveAccount(account); repository.selectAccount(account.id) }
    fun deleteAccount(id: String) = command { repository.deleteAccount(id) }
    fun saveTransaction(transaction: PortfolioTransaction, edit: Boolean = false) = command { repository.saveTransaction(transaction, edit) }
    fun deleteTransaction(id: String) = command { repository.deleteTransaction(id) }
    fun clearError() { failure.value = null }
    private fun command(action: suspend () -> Unit) {
        if (busy.value || scenario.value != null && repository.state.value.environment == "mock") return
        busy.value = true
        scope.launch {
            failure.value = null
            try { action() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                failure.value = cause.message ?: "The portfolio could not be updated."
            } finally { busy.value = false }
        }
    }
    private suspend fun refreshQuotes() {
        val resource = repository.state.value
        val uid = resource.uid ?: return
        val environment = resource.environment ?: return
        val symbols = resource.value?.transactions.orEmpty().mapNotNull { it.instrument?.symbol }.distinct()
        if (symbols.isNotEmpty()) quoteState.value = symbols.chunked(100).flatMap { quotes.load(userCacheOwner(environment, uid), it, force = true).data.quotes }
    }
    companion object {
        private fun combined(base: PortfolioUiState, selected: PortfolioReport?, reports: List<PortfolioReport>): String? {
            if (base.accounts.count { !it.archived } < 2 || selected == null) return null
            val active = base.accounts.filterNot { it.archived }
            var total = Decimal.ZERO
            for (account in active) {
                val report = reports.find { it.accountId == account.id && it.revision == selected.revision } ?: return null
                val amount = report.summary.totalValue?.let(Decimal::parse) ?: return null
                val rate = if (report.summary.currency.name == base.currency) Decimal.ONE else selected.currentFx[report.summary.currency]?.let(Decimal::parse) ?: return null
                total += amount * rate
            }
            return total.toString()
        }
        fun newId(): String {
            val bytes = Random.nextBytes(16)
            bytes[6] = ((bytes[6].toInt() and 15) or 64).toByte()
            bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
            val hex = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        }
        fun today(): String = Clock.System.now().toString().take(10)
        /** Rendering uses decimal arithmetic too. Provider Doubles are converted at the boundary. */
        fun present(snapshot: PortfolioSnapshot, quotes: List<WatchQuote>): PortfolioUiState {
            val account = snapshot.ledger.accounts.find { it.id == snapshot.selectedAccountId }
            val quoteMap = quotes.associateBy { it.symbol }
            val rows = snapshot.position?.holdings.orEmpty().filter { Decimal.parse(it.quantity) > Decimal.ZERO }.map { holding ->
                val quote = quoteMap[holding.instrument.symbol]
                val price = quote?.price?.takeIf { it.isFinite() && it > 0 }?.let(::providerDecimal)
                    ?.takeIf { quote.currency == null || quote.currency == holding.currency.name }
                val value = price?.let { Decimal.parse(it) * Decimal.parse(holding.quantity) }
                val gain = value?.minus(Decimal.parse(holding.costBasis))
                val percent = gain?.takeIf { Decimal.parse(holding.costBasis) > Decimal.ZERO }?.let { it / Decimal.parse(holding.costBasis) * Decimal.parse("100") }
                PortfolioHoldingRow(holding.instrument.symbol, holding.instrument.name ?: quote?.name ?: holding.instrument.symbol,
                    holding.instrument.exchange, holding.currency.name, holding.quantity, price, value?.toString(), holding.costBasis,
                    gain?.toString(), percent?.toString(), holding.realizedGain, holding.dividends, quote?.logoUrl, quote?.stale ?: false)
            }
            val valuation = account?.let {
                PortfolioEngine.value(snapshot.ledger, it.id, PortfolioPrices(today(), rows.mapNotNull { row -> row.price?.let { row.symbol to it } }.toMap(), emptyMap()))
            }
            return PortfolioUiState(snapshot.ledger.accounts, account?.id, account?.name ?: "Your portfolio", account?.reportingCurrency?.name ?: "CAD",
                valuation?.totalValue, valuation?.investedCost, valuation?.unrealizedGain, valuation?.realizedGain,
                snapshot.position?.cash.orEmpty().map { "${it.key.name} ${it.value}" }, rows,
                snapshot.ledger.transactions.filter { it.accountId == account?.id }.sortedWith(compareByDescending<PortfolioTransaction> { it.tradeDate }.thenByDescending { it.createdAt }),
                snapshot.loading, snapshot.offline, snapshot.error,
                valuation?.missing?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Unavailable: "), snapshot.signedIn, today = today(), dividends = snapshot.position?.dividends.orEmpty().map { "${it.key.name} ${Decimal.parse(it.value).display()}" })
        }
        internal fun providerDecimal(value: Double): String? {
            if (!value.isFinite() || value <= 0 || value >= 1e18) return null
            // Use the provider's shortest decimal representation, not binary-fraction truncation.
            val parts = value.toString().lowercase().split('e')
            val mantissa = parts[0]
            val digits = mantissa.replace(".", "")
            val point = mantissa.substringBefore('.').length + parts.getOrElse(1) { "0" }.toInt()
            val plain = when {
                point <= 0 -> "0." + "0".repeat(-point) + digits
                point >= digits.length -> digits + "0".repeat(point - digits.length)
                else -> digits.take(point) + "." + digits.drop(point)
            }
            val whole = plain.substringBefore('.').trimStart('0').ifEmpty { "0" }
            val fraction = plain.substringAfter('.', "")
            val amount = Decimal.parse("$whole.${fraction.take(8).padEnd(8, '0')}")
            return (if (fraction.getOrNull(8)?.let { it >= '5' } == true) amount + Decimal.parse("0.00000001") else amount).toString()
        }
    }
}
