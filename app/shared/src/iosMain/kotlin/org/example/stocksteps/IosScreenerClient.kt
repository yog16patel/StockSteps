package org.example.stocksteps

import kotlinx.cinterop.addressOf
import platform.Foundation.create
import kotlinx.cinterop.usePinned
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
    /** Company Comparison Phase 4 research checklist (null when there's no account graph). */
    val research: ComparisonResearchPresenter? get() = accountGraph?.comparisonResearch
    fun observeResearch(onChange: (ResearchUiState) -> Unit): AccountSubscription {
        val presenter = research ?: return object : AccountSubscription { override fun cancel() {} }
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun researchStatuses(): List<ResearchStatus> = ResearchStatus.entries
    fun researchCategories(state: ResearchUiState, comparison: ComparisonUiState?): List<ResearchCategoryView> = state.categories { ResearchContext.lines(it, comparison) }
    fun createResearchForSelection() { research?.create(SharedComparisonSelection.instance.selected.value.map { it.symbol }) }
    /** Shows an opened session's companies so the checklist context matches it. */
    fun selectResearchCompanies(symbols: List<String>) {
        if (SharedComparisonSelection.instance.selected.value.map { it.symbol } != symbols) SharedComparisonSelection.instance.set(symbols.map { SelectedCompany(it, it) })
    }
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    fun exportData(export: ResearchExport): platform.Foundation.NSData = kotlinx.cinterop.memScoped {
        if (export.bytes.isEmpty()) platform.Foundation.NSData()
        else export.bytes.usePinned { pinned -> platform.Foundation.NSData.create(bytes = pinned.addressOf(0), length = export.bytes.size.toULong()) }
    }

    /** Company Comparison Phase 5 AI assistant (null when there's no account graph). The server checks StockSteps+ and quotas. */
    val ai: ComparisonAiPresenter? get() = accountGraph?.comparisonAi
    fun observeAi(onChange: (ComparisonAiUiState) -> Unit): AccountSubscription {
        val presenter = ai ?: return object : AccountSubscription { override fun cancel() {} }
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun aiSuggestions(comparison: ComparisonUiState?): List<SuggestedAiQuestion> = ComparisonAiSuggestions.forComparison(comparison)
    fun aiSummary(type: String) = ComparisonAiType.entries.firstOrNull { it.name == type }?.let { ai?.summarize(it) }
    fun aiEvidence(response: ComparisonAiResponse, ids: List<String>): List<FinancialEvidence> = ids.mapNotNull(response::evidence)
    /** "View data": opens the metric (or its history) the evidence comes from on Compare; the selection stays. */
    fun openEvidence(evidence: FinancialEvidence) {
        val metric = evidence.metricId ?: return
        HistoryMetric.entries.firstOrNull { it.name == metric }?.let { comparison.selectHistoryMetric(it); return }
        comparison.openRelated(metric)
    }
    /** "Copy to my note draft": a draft in the open research session only (the user saves it); false when no matching session is open. */
    fun addAiAnswerToNote(turn: AiTurn): Boolean {
        val research = research ?: return false
        val open = research.state.value.open ?: return false
        val q = turn.request.researchQuestionId ?: return false
        val answer = turn.response ?: return false
        if (open.session.symbols.sorted() != turn.request.symbols.sorted()) return false
        val existing = research.state.value.drafts[q] ?: open.session.responses[q]?.note.orEmpty()
        research.editNote(q, (if (existing.isBlank()) "" else existing.trimEnd() + "\n\n") + answer.noteText())
        return true
    }
    fun canAddAiAnswerToNote(turn: AiTurn): Boolean = turn.request.researchQuestionId != null &&
        research?.state?.value?.open?.session?.symbols?.sorted() == turn.request.symbols.sorted()

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
