package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.companydetail.MetricEducation
import org.example.stocksteps.data.userdata.ServerBackedRepository
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.data.userdata.UserDataCache
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException
import kotlin.math.abs
import kotlin.math.round

// ---------- Data access ----------

/** Public screener/compare endpoints (fakeable in tests). */
interface ScreenerDataSource {
    suspend fun catalog(): ScreenerCatalog
    suspend fun search(query: ScreenerQuery): ScreenerPage
    suspend fun compare(symbols: List<String>): ComparisonResponse
    suspend fun performance(symbols: List<String>, period: PerformancePeriod): PerformanceComparison
}

class RemoteScreenerDataSource(private val api: StockStepsApi) : ScreenerDataSource {
    override suspend fun catalog() = api.getScreenerCatalog()
    override suspend fun search(query: ScreenerQuery) = api.searchScreener(query)
    override suspend fun compare(symbols: List<String>) = api.compare(symbols)
    override suspend fun performance(symbols: List<String>, period: PerformancePeriod) = api.comparePerformance(symbols, period)
}

/** Signed-in saved screens; cleared on sign-out and per Mock/Real environment like other user data. */
class SavedScreensRepository(
    auth: AuthRepository,
    private val api: UserApi,
    cache: UserDataCache,
    environment: StateFlow<String>,
    scope: CoroutineScope
) : ServerBackedRepository<SavedScreensResponse>(auth, cache, environment, scope, SavedScreensResponse.serializer(), "screener.saved.v1") {
    override suspend fun fetch() = api.savedScreens()
    suspend fun save(name: String, query: ScreenerQuery) = mutate { api.saveScreen(SaveScreenRequest(name, query.definition())) }
    suspend fun rename(id: String, name: String) = mutate { api.updateScreen(id, UpdateScreenRequest(name = name)) }
    suspend fun replace(id: String, query: ScreenerQuery) = mutate { api.updateScreen(id, UpdateScreenRequest(query = query.definition())) }
    suspend fun delete(id: String) = mutate { api.deleteScreen(id) }
}

// ---------- Comparison selection (shared by Screener, Company Details and Compare) ----------

data class SelectedCompany(val symbol: String, val name: String)
enum class SelectionResult { ADDED, ALREADY_SELECTED, FULL }

/** The companies chosen for comparison: 2–4, no duplicates, kept while moving between screens. */
class ComparisonSelection {
    private val mutable = MutableStateFlow<List<SelectedCompany>>(emptyList())
    val selected: StateFlow<List<SelectedCompany>> = mutable.asStateFlow()

    fun add(symbol: String, name: String): SelectionResult {
        var result = SelectionResult.ADDED
        mutable.update { current ->
            when {
                current.any { it.symbol.equals(symbol, ignoreCase = true) } -> { result = SelectionResult.ALREADY_SELECTED; current }
                current.size >= MAX_COMPARED_COMPANIES -> { result = SelectionResult.FULL; current }
                else -> current + SelectedCompany(symbol.uppercase(), name)
            }
        }
        return result
    }
    fun remove(symbol: String) = mutable.update { list -> list.filterNot { it.symbol.equals(symbol, ignoreCase = true) } }
    fun toggle(symbol: String, name: String): SelectionResult =
        if (selected.value.any { it.symbol.equals(symbol, ignoreCase = true) }) { remove(symbol); SelectionResult.ADDED } else add(symbol, name)
    /** Swaps [old] for another company in the same position (no restart). */
    fun replace(old: String, symbol: String, name: String): SelectionResult {
        var result = SelectionResult.ADDED
        mutable.update { current ->
            val index = current.indexOfFirst { it.symbol.equals(old, ignoreCase = true) }
            when {
                symbol.equals(old, ignoreCase = true) -> current
                current.any { it.symbol.equals(symbol, ignoreCase = true) } -> { result = SelectionResult.ALREADY_SELECTED; current }
                index < 0 -> { if (current.size >= MAX_COMPARED_COMPANIES) { result = SelectionResult.FULL; current } else current + SelectedCompany(symbol.uppercase(), name) }
                else -> current.toMutableList().also { it[index] = SelectedCompany(symbol.uppercase(), name) }
            }
        }
        return result
    }
    /** Replaces the whole selection (examples, a watchlist): duplicates dropped, at most [MAX_COMPARED_COMPANIES]. */
    fun set(companies: List<SelectedCompany>) {
        mutable.value = companies.distinctBy { it.symbol.uppercase() }.take(MAX_COMPARED_COMPANIES).map { it.copy(symbol = it.symbol.uppercase()) }
    }
    fun clear() { mutable.value = emptyList() }
    fun contains(symbol: String) = selected.value.any { it.symbol.equals(symbol, ignoreCase = true) }
}

/** One selection for the app process (public data, not tied to an account). */
object SharedComparisonSelection {
    val instance = ComparisonSelection()
}

// ---------- Formatting (no calculations) ----------

data class MetricCell(
    val text: String,
    val tone: Tone = Tone.NEUTRAL,
    /** Why the value isn't shown (tap target); null when it is available. */
    val explanation: String? = null,
    /** Small second line: the company's own reporting period or a converted amount (e.g. "FY ended Sep 2025"). */
    val detail: String? = null
) {
    val available: Boolean get() = explanation == null
    val accessibility: String get() = text + (detail?.let { ", $it" } ?: "")
}
enum class Tone { NEUTRAL, POSITIVE, NEGATIVE }

object MetricFormatter {
    fun cell(definition: MetricDefinition?, value: MetricValue?, currency: String? = null): MetricCell {
        val v = value?.value?.takeIf { it.isFinite() }
        if (value?.availability == FinancialAvailability.NO_DIVIDEND) return MetricCell("None", explanation = "No dividend in the trailing year.")
        if (v == null || value.availability != FinancialAvailability.AVAILABLE) return MetricCell("N/A", explanation = explanation(definition?.id, value))
        val unit = definition?.unit ?: MetricUnit.RATIO
        val growth = definition?.group == MetricGroup.GROWTH
        val text = when (unit) {
            MetricUnit.PERCENT -> (if (growth && v > 0) "+" else if (v < 0) "−" else "") + "${decimals(abs(v), 1)}%"
            MetricUnit.MULTIPLE -> "${decimals(v, 1)}×"
            MetricUnit.RATIO -> decimals(v, 2)
            MetricUnit.MONEY -> money(v, value.basis?.currency ?: currency)
            MetricUnit.PRICE -> price(v, currency)
            MetricUnit.COUNT -> compact(v)
        }
        val tone = if (growth) (if (v > 0) Tone.POSITIVE else if (v < 0) Tone.NEGATIVE else Tone.NEUTRAL) else Tone.NEUTRAL
        return MetricCell(text, tone)
    }

    /**
     * Why a value is missing, specific to the metric: the company's own source note first, then a
     * metric-aware reason ("not meaningful" is different from "not reported").
     */
    fun explanation(metricId: String?, value: MetricValue?): String {
        value?.note?.takeIf { it.isNotBlank() && value.availability != FinancialAvailability.AVAILABLE }?.let { return it }
        val a = value?.availability
        if (a == FinancialAvailability.NON_POSITIVE_DENOMINATOR) when (metricId) {
            "pe" -> return "Not meaningful: the company had zero or negative earnings (a loss) over the trailing twelve months."
            "debtEquity", "priceBook" -> return "Not meaningful: shareholders' equity is zero or negative."
            "payoutRatio" -> return "Not meaningful: earnings were zero or negative."
            "evEbitda" -> return "Not meaningful: EBITDA was zero or negative."
            "interestCoverage" -> return "Not shown: the company reported no interest expense."
        }
        if (a == null || a == FinancialAvailability.MISSING) when (metricId) {
            "quarterRevenueGrowth" -> return "No comparable quarterly results are available for this company."
            "dividendYield" -> return "Dividend history isn't available, so the yield isn't shown (this isn't the same as no dividend)."
        }
        return reason(a)
    }

    fun reason(availability: FinancialAvailability?): String = when (availability) {
        FinancialAvailability.NON_POSITIVE_DENOMINATOR -> "Not meaningful: the denominator (e.g. earnings) is zero or negative."
        FinancialAvailability.UNRELIABLE_COMPARISON -> "Not meaningful: the prior period was zero or a loss."
        FinancialAvailability.INSUFFICIENT_HISTORY -> "Not enough reported history."
        FinancialAvailability.PERIOD_MISMATCH -> "Periods or currencies don't match."
        FinancialAvailability.INVALID_VALUE -> "The reported value wasn't valid."
        FinancialAvailability.TEMPORARILY_UNAVAILABLE -> "Temporarily unavailable."
        else -> "Not available for this company."
    }

    fun decimals(value: Double, places: Int): String {
        val factor = if (places == 1) 10.0 else 100.0
        val scaled = round(abs(value) * factor).toLong()
        val whole = scaled / factor.toLong()
        val fraction = (scaled % factor.toLong()).toString().padStart(places, '0')
        return (if (value < 0 && scaled != 0L) "−" else "") + "$whole.$fraction"
    }

    private fun symbol(currency: String?) = when (currency) { null, "USD" -> "$"; "CAD" -> "C$"; else -> "$currency " }

    fun price(value: Double, currency: String?) = symbol(currency) + grouped(decimals(abs(value), 2)).let { if (value < 0) "−$it" else it }

    fun money(value: Double, currency: String?): String = (if (value < 0) "−" else "") + symbol(currency) + compact(abs(value))

    /** Money with an unambiguous currency marker ("US$", "C$"), used when compared amounts are in different currencies. */
    fun money(value: Double, currency: String?, explicit: Boolean): String =
        if (explicit && (currency == null || currency == "USD")) (if (value < 0) "−" else "") + (if (currency == null) "" else "US$") + compact(abs(value)) + (if (currency == null) " (currency not reported)" else "")
        else money(value, currency)

    fun compact(value: Double): String = when {
        value >= 1e12 -> "${decimals(value / 1e12, 2)}T"
        value >= 1e9 -> "${decimals(value / 1e9, 1)}B"
        value >= 1e6 -> "${decimals(value / 1e6, 1)}M"
        value >= 1e3 -> "${decimals(value / 1e3, 1)}K"
        else -> decimals(value, 1).removeSuffix(".0")
    }

    private fun grouped(plain: String): String {
        val whole = plain.substringBefore('.'); val fraction = plain.substringAfter('.', "")
        val g = whole.reversed().chunked(3).joinToString(",").reversed()
        return if (fraction.isEmpty()) g else "$g.$fraction"
    }

    fun change(percent: Double?): MetricCell = percent?.takeIf { it.isFinite() }?.let {
        MetricCell((if (it > 0) "+" else if (it < 0) "−" else "") + "${decimals(abs(it), 2)}%", if (it > 0) Tone.POSITIVE else if (it < 0) Tone.NEGATIVE else Tone.NEUTRAL)
    } ?: MetricCell("—", explanation = "Daily change unavailable.")

    /** "What it means / how it's calculated / why / limitations" for an info sheet, from MetricEducation. */
    fun education(id: String, definition: MetricDefinition?): MetricInfo {
        val education = MetricEducation.find(id)
        return MetricInfo(
            title = definition?.label ?: education?.label ?: id,
            meaning = education?.explanation ?: definition?.missing.orEmpty(),
            calculation = education?.calculation,
            why = education?.why,
            limitations = listOfNotNull(definition?.missing, definition?.currencyNote).distinct(),
            period = definition?.period
        )
    }
}

data class MetricInfo(val title: String, val meaning: String, val calculation: String?, val why: String?, val limitations: List<String>, val period: String?)

// ---------- Screener ----------

data class ScreenerRowView(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val currency: String?,
    val logoUrl: String?,
    val price: String,
    val change: MetricCell,
    val metrics: List<Pair<String, MetricCell>>,
    val notes: List<String>,
    val stale: Boolean,
    val selected: Boolean
) {
    val accessibility: String get() = buildString {
        append("$name, $symbol${exchange?.let { " on $it" } ?: ""}. Price $price, ${change.text} today.")
        metrics.forEach { (label, cell) -> append(" $label ${cell.text}.") }
        if (stale) append(" Financial data may be out of date.")
        if (selected) append(" Selected for comparison.")
    }
}

data class ScreenerUiState(
    val catalogLoading: Boolean = true,
    val catalogError: String? = null,
    val presets: List<ScreenerPreset> = emptyList(),
    val metrics: List<MetricDefinition> = emptyList(),
    val choices: Map<ChoiceField, List<String>> = emptyMap(),
    /** Filters being edited; nothing is requested until [ScreenerPresenter.apply]. */
    val draft: ScreenerQuery = ScreenerQuery(),
    val applied: ScreenerQuery? = null,
    val rows: List<ScreenerRowView> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val universe: UniverseInfo? = null,
    val warnings: List<String> = emptyList(),
    val excludedForMissingData: Int = 0,
    val displayMetrics: List<MetricDefinition> = emptyList(),
    val sampleData: Boolean = false,
    val asOf: String? = null,
    val saved: SavedScreensResponse? = null,
    val signedIn: Boolean = false,
    val selected: List<SelectedCompany> = emptyList(),
    /** One-off message (e.g. "Compare up to 4 companies"); cleared by [ScreenerPresenter.dismissMessage]. */
    val message: String? = null
) {
    val draftChanged: Boolean get() = applied?.definition()?.copy(pageSize = 0) != draft.definition().copy(pageSize = 0)
    val activeFilterCount: Int get() = (applied ?: draft).activeFilterCount
    val canCompare: Boolean get() = selected.size >= 2
    val activePreset: ScreenerPreset? get() = applied?.presetId?.let { id -> presets.firstOrNull { it.id == id } }
    fun definition(id: String) = metrics.firstOrNull { it.id == id }
}

/**
 * Screener presenter shared by Android and iOS. Filter edits change only the draft; [apply] runs
 * one server search (obsolete searches are cancelled and identical ones aren't repeated). Results
 * are paged by the server's cursor, so sorting covers the whole evaluated universe.
 */
class ScreenerPresenter(
    private val data: ScreenerDataSource,
    private val selection: ComparisonSelection,
    private val scope: CoroutineScope,
    private val savedScreens: SavedScreensRepository? = null,
    private val pageSize: Int = 20
) {
    private val mutable = MutableStateFlow(ScreenerUiState())
    val state: StateFlow<ScreenerUiState> = mutable.asStateFlow()
    private val requests = MutableStateFlow<Pair<ScreenerQuery, Int>?>(null)
    private var page: ScreenerPage? = null
    private var moreJob: Job? = null

    init {
        scope.launch { selection.selected.collect { selected -> mutable.update { it.copy(selected = selected, rows = it.rows.map { row -> row.copy(selected = selected.any { s -> s.symbol == row.symbol }) }) } } }
        savedScreens?.let { repository ->
            scope.launch { repository.state.collect { s -> mutable.update { it.copy(saved = s.value, signedIn = s.uid != null) } } }
        }
        scope.launch {
            requests.filterNotNull().distinctUntilChanged().collectLatest { (query, _) ->
                moreJob?.cancel()
                mutable.update { it.copy(loading = true, error = null) }
                try {
                    val result = data.search(query)
                    page = result
                    mutable.update { it.copy(loading = false, rows = result.rows.map(::row), total = result.total, hasMore = result.nextCursor != null,
                        universe = result.universe, warnings = result.warnings, excludedForMissingData = result.excludedForMissingData,
                        displayMetrics = result.displayMetrics.mapNotNull(it::definition), sampleData = result.sampleData, asOf = result.asOf) }
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    mutable.update { it.copy(loading = false, error = message(cause, "Results couldn't be loaded. Check your connection and try again.")) }
                }
            }
        }
    }

    private fun message(cause: Exception, fallback: String) = (cause as? StockStepsApiException)?.error?.message ?: fallback

    private fun row(result: ScreenerResultRow): ScreenerRowView {
        val c = result.company
        val shown = (page?.displayMetrics ?: mutable.value.applied?.let(ScreenerDefinitions::displayMetricsFor)).orEmpty()
        return ScreenerRowView(
            c.symbol, c.name, c.exchange, c.currency, c.logoUrl,
            c.price?.let { MetricFormatter.price(it, c.currency) } ?: "—",
            MetricFormatter.change(c.changePercent),
            shown.map { id -> val d = ScreenerDefinitions.metric(id); (d?.label ?: id) to MetricFormatter.cell(d, c.metrics[id], c.currency) },
            result.notApplied, c.stale, selection.contains(c.symbol)
        )
    }

    fun start() {
        if (mutable.value.presets.isNotEmpty()) return
        scope.launch {
            mutable.update { it.copy(catalogLoading = true, catalogError = null) }
            try {
                val catalog = data.catalog()
                mutable.update { it.copy(catalogLoading = false, presets = catalog.presets, metrics = catalog.metrics, choices = catalog.choices, universe = catalog.universe) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(catalogLoading = false, catalogError = message(cause, "Screening isn't available right now.")) }
            }
        }
    }

    fun selectPreset(id: String) {
        val preset = mutable.value.presets.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(draft = preset.query(pageSize)) }
        apply()
    }

    fun setRange(metric: String, min: Double?, max: Double?) = mutable.update { state ->
        val others = state.draft.ranges.filterNot { it.metric == metric }
        val next = if (min == null && max == null) others else others + RangeFilter(metric, min, max)
        state.copy(draft = state.draft.copy(ranges = next, presetId = state.draft.presetId?.takeIf { id -> ScreenerDefinitions.preset(id)?.ranges == next }))
    }

    fun clearFilter(metric: String) = setRange(metric, null, null)

    fun setChoice(field: ChoiceField, values: List<String>) = mutable.update { state ->
        val others = state.draft.choices.filterNot { it.field == field }
        state.copy(draft = state.draft.copy(choices = if (values.isEmpty()) others else others + ChoiceFilter(field, values)))
    }

    fun resetAll() {
        mutable.update { it.copy(draft = ScreenerQuery(pageSize = pageSize)) }
        apply()
    }

    fun apply() {
        val query = mutable.value.draft.copy(pageSize = pageSize, cursor = null)
        mutable.update { it.copy(applied = query, draft = query) }
        requests.value = query to (requests.value?.second ?: 0)
    }

    fun retry() { requests.value = (mutable.value.applied ?: return) to ((requests.value?.second ?: 0) + 1) }

    fun sort(sort: ScreenerSort) {
        mutable.update { it.copy(draft = it.draft.copy(sort = sort)) }
        apply()
    }

    fun loadMore() {
        val current = page ?: return
        val cursor = current.nextCursor ?: return
        if (moreJob?.isActive == true) return
        moreJob = scope.launch {
            mutable.update { it.copy(loadingMore = true) }
            try {
                val next = data.search(current.query.copy(cursor = cursor))
                page = next
                mutable.update { s -> s.copy(loadingMore = false, rows = (s.rows + next.rows.map(::row)).distinctBy { it.symbol }, hasMore = next.nextCursor != null, total = next.total) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(loadingMore = false, message = message(cause, "More results couldn't be loaded.")) }
            }
        }
    }

    fun toggleCompare(symbol: String, name: String) {
        val result = selection.toggle(symbol, name)
        if (result == SelectionResult.FULL) mutable.update { it.copy(message = "You can compare up to $MAX_COMPARED_COMPANIES companies. Remove one first.") }
    }

    fun dismissMessage() = mutable.update { it.copy(message = null) }

    // Saved screens (signed in only; the server enforces the plan's limit and ownership).
    private fun savedAction(block: suspend (SavedScreensRepository) -> Unit) {
        val repository = savedScreens ?: return
        scope.launch {
            try { block(repository) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(message = message(cause, "Couldn't update saved screens.")) }
            }
        }
    }
    fun saveScreen(name: String) = savedAction { it.save(name, mutable.value.applied ?: mutable.value.draft); mutable.update { s -> s.copy(message = "Saved “${name.trim()}”.") } }
    fun renameScreen(id: String, name: String) = savedAction { it.rename(id, name) }
    fun deleteScreen(id: String) = savedAction { it.delete(id) }
    fun applySaved(id: String) {
        val screen = mutable.value.saved?.screens?.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(draft = screen.query.copy(pageSize = pageSize)) }
        apply()
    }
}

// ---------- Comparison ----------

data class CompanyColumn(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val currency: String?,
    val logoUrl: String?,
    val price: String,
    val change: MetricCell,
    val error: String?,
    /** "NASDAQ · US · USD": tells same-named or cross-listed companies apart. */
    val listing: String = listOfNotNull(exchange, currency).joinToString(" · ")
)

data class ComparisonRow(val id: String, val label: String, val period: String?, val cells: List<MetricCell>) {
    fun accessibility(columns: List<CompanyColumn>) = "$label" + (period?.let { ", $it" } ?: "") + ": " +
        columns.zip(cells).joinToString("; ") { (c, cell) -> "${c.symbol} " + if (cell.available) cell.accessibility else "not available, ${cell.explanation}" }
}
/** [advanced] sections hold extra metrics shown under "More metrics" (collapsed by default for beginners). */
data class ComparisonSection(val title: String, val rows: List<ComparisonRow>, val note: String? = null, val advanced: Boolean = false)

/** Ready-made pairs for a first comparison (no selection yet). */
data class ComparisonExample(val title: String, val description: String, val companies: List<SelectedCompany>)

data class ComparisonUiState(
    val selected: List<SelectedCompany> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val columns: List<CompanyColumn> = emptyList(),
    val sections: List<ComparisonSection> = emptyList(),
    val observations: List<ComparisonObservation> = emptyList(),
    val notes: List<String> = emptyList(),
    val period: PerformancePeriod = PerformancePeriod.ONE_YEAR,
    val chart: PerformanceComparison? = null,
    val chartLoading: Boolean = false,
    val chartError: String? = null,
    val sampleData: Boolean = false,
    val message: String? = null,
    /** When the comparison data was produced (server time). */
    val asOf: String? = null
) {
    val needsMore: Boolean get() = selected.size < 2
    val canAdd: Boolean get() = selected.size < MAX_COMPARED_COMPANIES
    val examples: List<ComparisonExample> get() = ComparisonPresenter.EXAMPLES
    /** Gentle guidance: four columns are tight on a phone. */
    val crowdedHint: String? get() = if (selected.size >= MAX_COMPARED_COMPANIES) "Four companies fit, but two or three are easier to read on a phone." else null
    val advancedCount: Int get() = sections.filter { it.advanced }.sumOf { it.rows.size }
}

/**
 * Compare 2–4 companies (2–3 recommended on phones): one request for metrics, one per chart period
 * (shared between the chart and the price-performance rows, never fetched twice). Beginner metrics
 * come first; the rest are under "More metrics". Never ranks companies.
 */
class ComparisonPresenter(
    private val data: ScreenerDataSource,
    private val selection: ComparisonSelection,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(ComparisonUiState())
    val state: StateFlow<ComparisonUiState> = mutable.asStateFlow()
    private val period = MutableStateFlow(PerformancePeriod.ONE_YEAR)
    private val refreshes = MutableStateFlow(0)
    /** In-flight or finished performance requests by "SYMBOLS|period" (failures are dropped so a retry refetches). */
    private val performanceRequests = HashMap<String, Deferred<PerformanceComparison>>()
    private val requestScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    companion object {
        val EXAMPLES = listOf(
            ComparisonExample("Apple and Microsoft", "Two large technology companies whose fiscal years end in different months.",
                listOf(SelectedCompany("AAPL", "Apple Inc."), SelectedCompany("MSFT", "Microsoft Corporation"))),
            ComparisonExample("Royal Bank and TD", "Two Canadian banks: one listed in Toronto (CAD), one in New York (USD).",
                listOf(SelectedCompany("RY.TO", "Royal Bank of Canada"), SelectedCompany("TD", "Toronto-Dominion Bank"))),
            ComparisonExample("Coca-Cola and Rivian", "A long-time dividend payer and a company that reports losses and pays no dividend.",
                listOf(SelectedCompany("KO", "Coca-Cola Co"), SelectedCompany("RIVN", "Rivian Automotive Inc")))
        )
        /** Beginner rows, in order, under the five core groups. */
        val CORE_SECTIONS = listOf(
            "Growth" to listOf("quarterRevenueGrowth", "revenueGrowth"),
            "Profitability" to listOf("netMargin"),
            "Financial Health" to listOf("debtEquity"),
            "Valuation" to listOf("pe", "priceSales"),
            "Shareholder Returns" to listOf("dividendYield")
        )
    }

    private fun key(symbols: List<String>, p: PerformancePeriod) = symbols.joinToString(",") + "|" + p.name

    /** One request per selection and period, shared by the chart and the price-change rows. */
    private fun performance(symbols: List<String>, p: PerformancePeriod): Deferred<PerformanceComparison> {
        performanceRequests[key(symbols, p)]?.let { return it }
        // Only the current selection's requests are kept.
        performanceRequests.keys.filterNot { it.startsWith(symbols.joinToString(",") + "|") }.forEach { performanceRequests.remove(it) }
        return requestScope.async { data.performance(symbols, p) }.also { performanceRequests[key(symbols, p)] = it }
    }

    private suspend fun performanceOrNull(symbols: List<String>, p: PerformancePeriod): PerformanceComparison? {
        val request = performance(symbols, p)
        return try { request.await() } catch (cause: Exception) {
            if (cause is CancellationException && !currentCoroutineContext().isActive) throw cause
            // A failed request is forgotten so the next attempt fetches again.
            if (performanceRequests[key(symbols, p)] === request) performanceRequests.remove(key(symbols, p))
            null
        }
    }

    init {
        scope.launch {
            combine(selection.selected.map { list -> list.map { it.symbol } }.distinctUntilChanged(), refreshes) { s, r -> s to r }.collectLatest { (symbols, _) ->
                mutable.update { it.copy(selected = selection.selected.value) }
                if (symbols.size < 2) { mutable.update { it.copy(loading = false, columns = emptyList(), sections = emptyList(), observations = emptyList(), notes = emptyList(), error = null) }; return@collectLatest }
                mutable.update { it.copy(loading = true, error = null) }
                try {
                    val response = data.compare(symbols)
                    val (oneYear, threeYear) = coroutineScope {
                        val y1 = async { performanceOrNull(symbols, PerformancePeriod.ONE_YEAR) }
                        val y3 = async { performanceOrNull(symbols, PerformancePeriod.THREE_YEARS) }
                        y1.await() to y3.await()
                    }
                    mutable.update { build(it.copy(loading = false), response, oneYear, threeYear) }
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    mutable.update { it.copy(loading = false, error = (cause as? StockStepsApiException)?.error?.message ?: "Comparison couldn't be loaded. Try again.") }
                }
            }
        }
        scope.launch {
            combine(selection.selected.map { list -> list.map { it.symbol } }.distinctUntilChanged(), period, refreshes) { s, p, r -> Triple(s, p, r) }.collectLatest { (symbols, p, _) ->
                if (symbols.size < 2) { mutable.update { it.copy(chart = null, chartError = null, chartLoading = false) }; return@collectLatest }
                mutable.update { it.copy(chartLoading = true, chartError = null, period = p) }
                val chart = performanceOrNull(symbols, p)
                mutable.update { it.copy(chartLoading = false, chart = chart, chartError = if (chart == null) "Price history couldn't be loaded. Try again." else null) }
            }
        }
    }

    private fun periodLabel(b: FinancialBasis?): String? {
        b ?: return null
        val month = b.date?.let { d -> MONTHS.getOrNull((d.drop(5).take(2).toIntOrNull() ?: 0) - 1)?.let { "$it ${d.take(4)}" } }
        return when (b.period.lowercase()) {
            "ttm" -> "Trailing twelve months"
            "annual", "fy" -> month?.let { "FY ended $it" } ?: b.fiscalYear?.let { "FY$it" }
            "latest quote" -> "Latest quote"
            else -> null
        }
    }
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun build(state: ComparisonUiState, response: ComparisonResponse, oneYear: PerformanceComparison?, threeYear: PerformanceComparison?): ComparisonUiState {
        val companies = response.companies
        val listingCurrencies = companies.mapNotNull { it.record?.currency }.distinct()
        val reportingCurrencies = companies.flatMap { c -> c.annual.mapNotNull { it.currency } }.distinct()
        // Amounts get explicit markers ("US$", "C$") whenever the compared companies use different currencies.
        val explicit = (listingCurrencies + reportingCurrencies).distinct().size > 1
        val columns = companies.map { c ->
            val r = c.record
            CompanyColumn(c.symbol, r?.name ?: c.symbol, r?.exchange, r?.currency, r?.logoUrl,
                r?.price?.let { MetricFormatter.price(it, r.currency) } ?: "—", MetricFormatter.change(r?.changePercent), c.error)
        }
        fun metricRow(id: String, label: String? = null, alwaysDetail: Boolean = false): ComparisonRow {
            val d = ScreenerDefinitions.metric(id)
            val values = companies.map { it.record?.metrics?.get(id) }
            // The period each company's value actually covers (its basis), not just the catalog's default.
            val periods = values.map { v -> if (v?.availability == FinancialAvailability.AVAILABLE) periodLabel(v.basis) ?: v.note else null }
            val distinct = periods.filterNotNull().distinct()
            val rowPeriod = when {
                distinct.size == 1 && !alwaysDetail -> distinct.single()
                distinct.size > 1 -> "Periods differ by company"
                else -> d?.period
            }
            val cells = companies.mapIndexed { i, c ->
                val v = values[i]
                val cell = if (d?.unit == MetricUnit.MONEY && v?.availability == FinancialAvailability.AVAILABLE && v.value != null)
                    MetricCell(MetricFormatter.money(v.value, v.basis?.currency ?: c.record?.currency, explicit))
                else MetricFormatter.cell(d, v, c.record?.currency)
                if (cell.available && (alwaysDetail || distinct.size > 1)) cell.copy(detail = periods[i]) else cell
            }
            return ComparisonRow(id, label ?: d?.label ?: id, rowPeriod, cells)
        }
        fun textRow(id: String, label: String, get: (CompanyRecord) -> String?) = ComparisonRow(id, label, null,
            companies.map { c -> c.record?.let(get)?.takeIf { it.isNotBlank() }?.let { MetricCell(it) } ?: MetricCell("N/A", explanation = if (c.record == null) c.error ?: "Company data isn't available." else "Not reported for this company.") })
        // Market cap in each listing's own currency; a converted USD amount is shown underneath for non-USD listings.
        val marketCapRow = ComparisonRow("marketCap", "Market cap", "Latest quote; in each listing's currency", companies.map { c ->
            val r = c.record
            val local = r?.marketCap?.takeIf { it > 0 && it.isFinite() }
            val usd = r?.metrics?.get("marketCap")?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value
            when {
                local == null -> MetricCell("N/A", explanation = "Market value isn't available right now.")
                r.currency != null && r.currency != "USD" -> MetricCell(MetricFormatter.money(local, r.currency, explicit), detail = usd?.let { "≈ ${MetricFormatter.money(it, "USD", true)} converted" })
                else -> MetricCell(MetricFormatter.money(local, r.currency, explicit))
            }
        })
        fun performanceRow(id: String, label: String, perf: PerformanceComparison?) = ComparisonRow(id, label, "Price change only (not total return)",
            companies.map { c ->
                val series = perf?.series?.firstOrNull { it.symbol == c.symbol }
                series?.change?.let { MetricFormatter.cell(MetricDefinition(id, label, MetricGroup.GROWTH, MetricUnit.PERCENT, "", ""), MetricValue(it, FinancialAvailability.AVAILABLE)).copy(detail = series.note?.let { "To ${series.lastDate}" }) }
                    ?: MetricCell("N/A", explanation = series?.error ?: "Price history unavailable.")
            })
        val years = companies.flatMap { c -> c.annual.map { it.fiscalYear } }.distinct().sortedDescending().take(3)
        fun annualRow(year: Int, label: String, get: (AnnualFigures) -> Double?) = ComparisonRow("annual-$label-$year", "$label FY$year", "Fiscal year; reporting currency",
            companies.map { c ->
                val figures = c.annual.firstOrNull { it.fiscalYear == year }
                figures?.let(get)?.let { MetricCell(MetricFormatter.money(it, figures.currency, explicit), detail = periodLabel(FinancialBasis("annual", figures.date, figures.fiscalYear))) }
                    ?: MetricCell("N/A", explanation = "No fiscal-year $year figures.")
            })
        val labels = mapOf("quarterRevenueGrowth" to "Revenue growth — latest quarter", "revenueGrowth" to "Revenue growth — fiscal year", "netMargin" to "Net profit margin",
            "debtEquity" to "Debt to equity", "pe" to "P/E ratio (trailing)", "priceSales" to "Price to sales", "dividendYield" to "Dividend yield")
        val core = listOf(ComparisonSection("Overview", listOf(marketCapRow,
            textRow("sector", "Sector") { it.sector }, textRow("industry", "Industry") { it.industry },
            textRow("listing", "Listing") { r -> listOfNotNull(r.exchange, r.country, r.currency).joinToString(" · ").ifBlank { null } }),
            if (listingCurrencies.size > 1) "Prices and market caps are in each listing's own currency (${listingCurrencies.joinToString(" and ")}). Converted amounts are approximate." else null)) +
            CORE_SECTIONS.map { (title, ids) ->
                ComparisonSection(title, ids.map { id -> metricRow(id, labels[id], alwaysDetail = id == "quarterRevenueGrowth") }, when (title) {
                    "Growth" -> "Latest quarter compares the same fiscal quarter a year earlier; companies' quarters can end in different months."
                    "Financial Health" -> "Debt to equity isn't comparable for banks and insurers, whose balance sheets work differently."
                    "Valuation" -> "A loss means there's no meaningful P/E. A lower ratio isn't proof that a stock is undervalued."
                    "Shareholder Returns" -> "Dividend yield describes the past year's payments. Dividends can change and aren't guaranteed."
                    else -> null
                })
            }
        val advanced = listOf(
            ComparisonSection("More valuation measures", listOf("forwardPe", "priceBook", "evEbitda").map { metricRow(it) },
                "Trailing P/E uses reported earnings; forward P/E uses analyst estimates. They're never mixed.", advanced = true),
            ComparisonSection("More growth measures", listOf("epsGrowth", "netIncomeGrowth", "fcfGrowth").map { metricRow(it) }, advanced = true),
            ComparisonSection("More profitability measures", listOf("grossMargin", "operatingMargin", "roe", "roic").map { metricRow(it) }, advanced = true),
            ComparisonSection("More financial health measures", listOf("currentRatio", "interestCoverage", "cash", "debt").map { metricRow(it) },
                "Amounts are in each company's reporting currency and aren't converted.".takeIf { explicit }, advanced = true),
            ComparisonSection("Price performance and payouts", listOf(metricRow("payoutRatio"),
                performanceRow("return1y", "1-year price change", oneYear), performanceRow("return3y", "3-year price change", threeYear)),
                "Price change excludes dividends. Past price moves don't predict future returns.", advanced = true),
            ComparisonSection("Fiscal-year figures", years.flatMap { y -> listOf(annualRow(y, "Revenue") { it.revenue }, annualRow(y, "Net income") { it.netIncome }, annualRow(y, "Free cash flow") { it.freeCashFlow }) },
                if (reportingCurrencies.size > 1) "Amounts are in each company's reporting currency (${reportingCurrencies.joinToString()}) and aren't converted." else null, advanced = true)
        )
        return state.copy(columns = columns, sections = core + advanced, observations = response.observations, notes = response.notes, sampleData = response.sampleData, asOf = response.asOf)
    }

    fun selectPeriod(value: PerformancePeriod) { period.value = value }
    fun remove(symbol: String) = selection.remove(symbol)
    fun add(symbol: String, name: String) {
        when (selection.add(symbol, name)) {
            SelectionResult.FULL -> mutable.update { it.copy(message = "You can compare up to $MAX_COMPARED_COMPANIES companies. Remove or replace one first.") }
            SelectionResult.ALREADY_SELECTED -> mutable.update { it.copy(message = "${symbol.uppercase()} is already in the comparison.") }
            SelectionResult.ADDED -> Unit
        }
    }
    /** Swaps one company for another in place; the rest of the comparison stays. */
    fun replace(old: String, symbol: String, name: String) {
        when (selection.replace(old, symbol, name)) {
            SelectionResult.ALREADY_SELECTED -> mutable.update { it.copy(message = "${symbol.uppercase()} is already in the comparison.") }
            SelectionResult.FULL -> mutable.update { it.copy(message = "You can compare up to $MAX_COMPARED_COMPANIES companies.") }
            SelectionResult.ADDED -> Unit
        }
    }
    fun useExample(index: Int) { EXAMPLES.getOrNull(index)?.let { selection.set(it.companies) } }
    fun retry() { performanceRequests.clear(); refreshes.value++ }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
}

/** Optional AI explanation of a comparison: architecture only; nothing is sent automatically. */
fun interface ComparisonExplainer {
    /** Must be called only on an explicit user request, with the verified [observations] as its only facts. */
    suspend fun explain(observations: List<ComparisonObservation>): String?
}

/** The shipped explainer: deterministic observations are the explanation; no AI service is called. */
object NoComparisonExplainer : ComparisonExplainer {
    override suspend fun explain(observations: List<ComparisonObservation>): String? = null
}
