package org.example.stocksteps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.screener.*

/**
 * Discover Stocks and Compare Stocks for SwiftUI: the same shared presenters, selection and backend
 * contract as Android. `baseUrl` is read per request so the Mock/Real setting applies immediately.
 */
class IosScreenerClient(baseUrl: () -> String, account: IosAccountClient?) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val data = dependencies.screenerData()
    private val search = dependencies.searchStocks()
    val screener = ScreenerPresenter(data, SharedComparisonSelection.instance, scope, account?.savedScreens)
    private val accountGraph = account?.dependenciesForEarnings
    /** Phase 3 history: public free view for guests; the signed-in route (server-checked StockSteps+) otherwise. */
    val comparison = ComparisonPresenter(data, SharedComparisonSelection.instance, scope,
        dependencies.comparisonHistory(accountGraph?.userApi) { accountGraph?.auth?.session?.value?.user?.id },
        accountGraph?.comparisonAccount ?: kotlinx.coroutines.flow.flowOf(null))

    fun observeScreener(onChange: (ScreenerUiState) -> Unit): AccountSubscription {
        val job = scope.launch { screener.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun observeComparison(onChange: (ComparisonUiState) -> Unit): AccountSubscription {
        val job = scope.launch { comparison.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    // Swift-friendly wrappers (nullable doubles and enum names).
    fun setRange(metric: String, min: Double, hasMin: Boolean, max: Double, hasMax: Boolean) = screener.setRange(metric, min.takeIf { hasMin }, max.takeIf { hasMax })
    fun setChoice(field: String, values: List<String>) = ChoiceField.entries.firstOrNull { it.name == field }?.let { screener.setChoice(it, values) }
    fun sort(field: String, metric: String?, descending: Boolean) = SortField.entries.firstOrNull { it.name == field }?.let { screener.sort(ScreenerSort(it, metric, descending)) }
    fun selectPeriod(label: String) = PerformancePeriod.parse(label)?.let(comparison::selectPeriod)
    fun addToComparison(symbol: String, name: String) = comparison.add(symbol, name)
    fun replaceInComparison(old: String, symbol: String, name: String) = comparison.replace(old, symbol, name)
    fun useExample(index: Int) = comparison.useExample(index)
    /** Watchlist entry point: compares the first three companies of a list. */
    fun compareCompanies(symbols: List<String>, names: List<String>) =
        SharedComparisonSelection.instance.set(symbols.zip(names).map { (s, n) -> SelectedCompany(s, n) }.take(3))
    fun maxCompanies(): Int = MAX_COMPARED_COMPANIES
    fun historyRanges(): List<HistoryRange> = HistoryRange.entries
    fun historyMetrics(): List<HistoryMetric> = HistoryMetric.entries
    fun education(id: String): MetricInfo = MetricFormatter.education(id, ScreenerDefinitions.metric(id))
    fun rangeText(range: RangeFilter): String = when {
        range.min != null && range.max != null -> "${format(range.metric, range.min!!)}–${format(range.metric, range.max!!)}"
        range.min != null -> "≥ ${format(range.metric, range.min!!)}"
        range.max != null -> "≤ ${format(range.metric, range.max!!)}"
        else -> "any"
    }
    private fun format(metric: String, value: Double): String = when (ScreenerDefinitions.metric(metric)?.unit) {
        MetricUnit.PERCENT -> "${MetricFormatter.decimals(value, 1)}%"
        MetricUnit.MULTIPLE -> "${MetricFormatter.decimals(value, 1)}×"
        MetricUnit.MONEY -> MetricFormatter.money(value, if (metric == "marketCap") "USD" else null)
        else -> MetricFormatter.decimals(value, 2)
    }

    @Throws(Exception::class)
    suspend fun searchStocks(query: String): List<StockSearchResult> = search(query).take(8)

    fun close() {
        scope.cancel()
        dependencies.close()
    }
}
