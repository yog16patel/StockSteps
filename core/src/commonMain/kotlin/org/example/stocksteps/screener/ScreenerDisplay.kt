package org.example.stocksteps.screener

/**
 * Global UI Refinement Phase 5B: display text and grouping for Discover Stocks, shared by Compose and SwiftUI so both platforms show the
 * same labels, categories and spoken descriptions. Formatting only: no calculations, no new data, no change to filter meaning (ranges stay
 * inclusive min/max on the server; the operators below are just another way of writing them).
 */
object ScreenerDisplay {
    const val TITLE = "Discover Stocks"
    const val SUBTITLE = "Find companies using filters that matter to you."
    /** Same wording as Watchlist rows (Phase 5A) for a value the data doesn't have — never $0.00 or 0%. */
    const val UNAVAILABLE = "No data available"
    const val CUSTOM_SCREEN = "Create custom screen"
    const val DISCLAIMER = "Screens describe financial characteristics. They aren't recommendations to buy or sell."
    const val EMPTY_TITLE = "No companies match your filters"
    const val EMPTY_MESSAGE = "Try changing or clearing some of your filters to see more results."
    const val ERROR_TITLE = "Couldn't load screener results"
    const val CATALOG_ERROR_TITLE = "Couldn't load the screener"

    /** `ApiError.code` values of the screener routes when the screening universe can't be loaded (the server sends the messages below). */
    const val CODE_UNAVAILABLE = "SCREENER_UNAVAILABLE"
    /** Phase 7E: the Discover search field (both apps). */
    const val TEXT_SEARCH_LABEL = "Find a company by name or ticker"
    const val CODE_PROVIDER_RESTRICTED = "SCREENER_PROVIDER_RESTRICTED"
    const val CODE_RATE_LIMITED = "SCREENER_RATE_LIMITED"
    const val UNAVAILABLE_MESSAGE = "The screening universe couldn't be loaded. Try again shortly."
    /** The market data plan doesn't include the provider's company screener: trying again can't help, so no Retry is offered. */
    const val PROVIDER_RESTRICTED_MESSAGE = "Discover Stocks isn't available right now because this server's market data plan doesn't include " +
        "stock screening. Trying again won't help. You can still search for a company by name or ticker."
    const val RATE_LIMITED_MESSAGE = "Market data requests are limited right now. Try again in a few minutes."
    const val PROVIDER_RESTRICTED_TITLE = "Discover Stocks isn't available"
    /** No answer from the StockSteps server (offline, unreachable or timed out). */
    const val CONNECTION_MESSAGE = "Discover Stocks couldn't reach the StockSteps server. Check your connection and try again."
    /** The server answered with something the app couldn't read. */
    const val UNEXPECTED_RESPONSE_MESSAGE = "Discover Stocks received a response it couldn't read. Try again later."

    fun catalogErrorTitle(code: String?): String = if (code == CODE_PROVIDER_RESTRICTED) PROVIDER_RESTRICTED_TITLE else CATALOG_ERROR_TITLE

    /** Whether Retry can help: not when the provider's plan doesn't include screening. */
    fun catalogRetryable(code: String?): Boolean = code != CODE_PROVIDER_RESTRICTED

    fun matchLine(total: Int): String = "$total ${if (total == 1) "company matches" else "companies match"}"

    fun filterCount(count: Int): String = when (count) { 0 -> "No filters"; 1 -> "1 filter"; else -> "$count filters" }

    /** Evaluated universe and companies left out, as one muted line (was two to three separate captions). */
    fun coverageLine(universe: UniverseInfo?, excluded: Int): String? {
        val coverage = universe?.let { if (it.complete) "Checked all ${it.size} companies" else "Checked ${it.evaluated} of ${it.size} companies" }
        val left = if (excluded > 0) "$excluded left out for missing data" else null
        return listOfNotNull(coverage, left).joinToString(" · ").ifEmpty { null }
    }

    /**
     * Why these companies appear: names the values each row shows, and says when they are the values the filters checked
     * (`ScreenerDefinitions.displayMetricsFor` shows filtered metrics first). Null when nothing is shown.
     */
    fun resultsExplanation(applied: ScreenerQuery?, shown: List<MetricDefinition>): String? {
        if (shown.isEmpty()) return null
        val filtered = applied?.ranges.orEmpty().filter { it.min != null || it.max != null }.map { it.metric }.toSet()
        val checked = shown.filter { it.id in filtered }
        return if (checked.isNotEmpty()) "Each company shows the values your filters checked: ${list(checked.map { it.label })}."
        else "Each company shows ${list(shown.map { it.label })} for context."
    }

    private fun list(items: List<String>): String = when (items.size) {
        0 -> ""; 1 -> items[0]; 2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    // ---------- Presets (discover cards) ----------

    /**
     * One-line, factual summaries of the existing presets for the discover cards; the exact criteria stay in "How screening works" and in
     * the applied-filter chips. Characteristics only — never "good", "safe", "cheap" or "undervalued".
     */
    fun presetSummary(preset: ScreenerPreset): String = when (preset.id) {
        ScreenerDefinitions.GROWING -> "Revenue and EPS grew in the latest fiscal year"
        ScreenerDefinitions.DIVIDEND -> "Pay dividends, with a yield of at least 1.5%"
        ScreenerDefinitions.STRONG -> "Positive cash flow and debt within set limits"
        ScreenerDefinitions.VALUATION -> "P/E, price/sales and EV/EBITDA within set ranges"
        else -> preset.description
    }

    /** Exact preset criteria as lines ("P/E ratio (trailing): 5.0×–25.0×"), for the help sheet. */
    fun presetCriteria(preset: ScreenerPreset): List<String> = preset.ranges.map { "${label(it.metric)}: ${rangeValue(it)}" }

    // ---------- Filter categories ----------

    enum class Category(val title: String) {
        COMPANY("Company characteristics"), VALUATION("Valuation"), GROWTH("Growth"), PROFITABILITY("Profitability"),
        HEALTH("Financial health"), SHAREHOLDER("Shareholder returns")
    }

    private fun categoryOf(group: MetricGroup): Category = when (group) {
        MetricGroup.MARKET -> Category.COMPANY
        MetricGroup.VALUATION -> Category.VALUATION
        MetricGroup.GROWTH -> Category.GROWTH
        MetricGroup.PROFITABILITY -> Category.PROFITABILITY
        MetricGroup.HEALTH -> Category.HEALTH
        MetricGroup.SHAREHOLDER -> Category.SHAREHOLDER
    }

    /** Only categories that have filterable metrics or attribute choices; [activeCount] counts the draft being edited. */
    fun categories(metrics: List<MetricDefinition>, choices: Map<ChoiceField, List<String>>, draft: ScreenerQuery): List<FilterCategoryView> =
        Category.entries.mapNotNull { category ->
            val inCategory = metrics.filter { it.filterable && categoryOf(it.group) == category }
            val fields = if (category == Category.COMPANY) choiceFields(choices) else emptyList()
            if (inCategory.isEmpty() && fields.isEmpty()) return@mapNotNull null
            val active = draft.ranges.count { r -> (r.min != null || r.max != null) && inCategory.any { it.id == r.metric } } +
                draft.choices.count { c -> c.values.isNotEmpty() && c.field in fields }
            val names = fields.map { it.label } + inCategory.map { it.label }
            FilterCategoryView(category.name, category.title, names.take(3).joinToString(", ") + if (names.size > 3) "…" else "", inCategory, fields, active)
        }

    /** Attribute fields worth offering (more than one value in the universe). */
    fun choiceFields(choices: Map<ChoiceField, List<String>>): List<ChoiceField> = ChoiceField.entries.filter { choices[it].orEmpty().size > 1 }

    // ---------- Range operators (display form of inclusive min/max) ----------

    enum class Operator(val label: String) { ANY("Any value"), AT_LEAST("At least"), AT_MOST("At most"), BETWEEN("Between") }

    fun operatorOf(range: RangeFilter?): Operator = when {
        range?.min != null && range.max != null -> Operator.BETWEEN
        range?.min != null -> Operator.AT_LEAST
        range?.max != null -> Operator.AT_MOST
        else -> Operator.ANY
    }

    /**
     * The draft min/max for an operator and the typed values (display units already converted). "At least" and "At most" have one input
     * ([first]); "Between" uses both.
     */
    fun bounds(operator: Operator, first: Double?, second: Double?): RangeBounds = when (operator) {
        Operator.ANY -> RangeBounds(null, null)
        Operator.AT_LEAST -> RangeBounds(first, null)
        Operator.AT_MOST -> RangeBounds(null, first)
        Operator.BETWEEN -> RangeBounds(first, second)
    }
    /** Pre-fills the inputs from an existing range: the single value for one-sided ranges, min then max for "Between". */
    fun firstValue(range: RangeFilter?): Double? = range?.min ?: range?.max
    fun secondValue(range: RangeFilter?): Double? = range?.max?.takeIf { range.min != null }

    /** Values are typed in display units: percent as 12.5, multiples as 18, money in billions. */
    fun inputUnit(definition: MetricDefinition?): String = when (definition?.unit) {
        MetricUnit.PERCENT -> "%"; MetricUnit.MULTIPLE -> "×"; MetricUnit.MONEY -> "billion"; else -> ""
    }
    private fun scale(definition: MetricDefinition?) = if (definition?.unit == MetricUnit.MONEY) 1e9 else 1.0
    fun inputText(value: Double?, definition: MetricDefinition?): String = value?.let {
        val v = it / scale(definition)
        if (v == kotlin.math.round(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    }.orEmpty()
    /**
     * Null for empty or non-numeric input ("no limit"). Thousands separators are removed ("100,000" = 100000, "1,250.5"); a single comma
     * without a dot is a decimal comma from a comma-decimal keyboard ("2,5" = 2.5). Phase 5B closure: every comma used to become a decimal
     * point, so "100,000" filtered on 100.
     */
    fun parseInput(text: String, definition: MetricDefinition?): Double? {
        val t = text.trim()
        val normalized = when {
            GROUPED.matches(t) -> t.replace(",", "")
            t.count { it == ',' } == 1 && '.' !in t -> t.replace(',', '.')
            else -> t
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }?.times(scale(definition))
    }
    private val GROUPED = Regex("""^-?\d{1,3}(,\d{3})+(\.\d+)?$""")
    fun boundsError(low: Double?, high: Double?): String? = if (low != null && high != null && low > high) "The first value is above the second." else null

    /**
     * The first inverted range in a draft ("P/E ratio (trailing): the first value is above the second."), or null. The editor disables "Show
     * results" while one exists, so an input mistake isn't sent and shown as "Couldn't load screener results" (the server rejects it too).
     */
    fun invalidRange(draft: ScreenerQuery): String? =
        draft.ranges.firstOrNull { r -> r.min != null && r.max != null && r.min > r.max }?.let { "${label(it.metric)}: the first value is above the second." }

    // ---------- Applied filters ----------

    fun label(metric: String): String = ScreenerDefinitions.metric(metric)?.label ?: metric

    /** "≥ 5.0%", "≤ 25.0×", "5.0×–25.0×" (same formatting as before Phase 5B). */
    fun rangeValue(range: RangeFilter): String {
        val min = range.min; val max = range.max
        return when {
            min != null && max != null -> "${value(range.metric, min)}–${value(range.metric, max)}"
            min != null -> "≥ ${value(range.metric, min)}"
            max != null -> "≤ ${value(range.metric, max)}"
            else -> Operator.ANY.label
        }
    }

    /** Spoken form: "at least 5.0%", "at most 25.0×", "between 5.0× and 25.0×". */
    fun rangeSpoken(range: RangeFilter): String {
        val min = range.min; val max = range.max
        return when {
            min != null && max != null -> "between ${value(range.metric, min)} and ${value(range.metric, max)}"
            min != null -> "at least ${value(range.metric, min)}"
            max != null -> "at most ${value(range.metric, max)}"
            else -> "any value"
        }
    }

    private fun value(metric: String, v: Double): String = when (ScreenerDefinitions.metric(metric)?.unit) {
        MetricUnit.PERCENT -> "${MetricFormatter.decimals(v, 1)}%"
        MetricUnit.MULTIPLE -> "${MetricFormatter.decimals(v, 1)}×"
        MetricUnit.MONEY -> MetricFormatter.money(v, if (metric == "marketCap") "USD" else null)
        else -> MetricFormatter.decimals(v, 2)
    }

    /** Every active filter of [query] as a removable chip / row, ranges first, in the query's order. */
    fun activeFilters(query: ScreenerQuery?): List<ActiveFilterView> {
        query ?: return emptyList()
        val ranges = query.ranges.filter { it.min != null || it.max != null }.map { r ->
            ActiveFilterView("range:${r.metric}", label(r.metric), rangeValue(r), "${label(r.metric)}, ${rangeSpoken(r)}", metric = r.metric)
        }
        val choices = query.choices.filter { it.values.isNotEmpty() }.map { c ->
            ActiveFilterView("choice:${c.field.name}", c.field.label, c.values.joinToString(", "), "${c.field.label}, ${c.values.joinToString(" or ")}", field = c.field)
        }
        return ranges + choices
    }

    // ---------- Sorting ----------

    fun sortOptions(shown: List<MetricDefinition>): List<SortOption> =
        listOf(SortOption(ScreenerSort(SortField.MARKET_CAP), "Market cap (high to low)"), SortOption(ScreenerSort(SortField.MARKET_CAP, descending = false), "Market cap (low to high)"),
            SortOption(ScreenerSort(SortField.NAME, descending = false), "Name (A–Z)"), SortOption(ScreenerSort(SortField.CHANGE_PERCENT), "Daily change (high to low)")) +
            shown.flatMap { listOf(SortOption(ScreenerSort(SortField.METRIC, it.id, true), "${it.label} (high to low)"), SortOption(ScreenerSort(SortField.METRIC, it.id, false), "${it.label} (low to high)")) }

    private fun sortName(sort: ScreenerSort): String = when (sort.field) {
        SortField.MARKET_CAP -> "Market cap"; SortField.NAME -> "Name"; SortField.CHANGE_PERCENT -> "Daily change"
        SortField.METRIC -> sort.metric?.let(::label) ?: "Metric"
    }

    /** Visible label in words (Phase 8B; was an arrow): "Sort: Market cap (high to low)", "Sort: Name (A–Z)" — the menu's own wording. */
    fun sortLabel(sort: ScreenerSort): String = "Sort: ${sortName(sort)}" + when {
        sort.field == SortField.NAME -> " (A–Z)"
        sort.descending -> " (high to low)"
        else -> " (low to high)"
    }

    fun sortSpoken(sort: ScreenerSort): String = "Sorted by ${sortName(sort).lowercase()}" + when {
        sort.field == SortField.NAME -> ", A to Z"
        sort.descending -> ", high to low"
        else -> ", low to high"
    }

    // ---------- Phase 8B: scope, sector shortcut, clear all ----------

    /**
     * What Discover covers, on the landing page as well as with results: "160 companies · <the server's own universe description>". The
     * description is shown verbatim (REAL: the curated list "isn't the whole market"; MOCK: "… (not real data)"); null without a catalogue.
     */
    fun scopeLine(universe: UniverseInfo?): String? = universe?.let { u ->
        listOf("${u.size} ${if (u.size == 1) "company" else "companies"}", u.description.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    const val ALL_SECTORS = "All sectors"
    const val CLEAR_ALL = "Clear all"
    const val CLEAR_ALL_SPOKEN = "Clear all filters"
    const val ABOUT_RESULTS = "About these results"
    const val SECTORS_SPOKEN = "Filter by sector"

    /** One-tap sector choice: "All sectors" then the catalogue's own sector values (the server's list and order; nothing added). */
    fun sectorOptions(choices: Map<ChoiceField, List<String>>): List<String> =
        choices[ChoiceField.SECTOR].orEmpty().takeIf { it.size > 1 }?.let { listOf(ALL_SECTORS) + it }.orEmpty()

    /**
     * The highlighted option: the one applied sector, "All sectors" when none is applied, or "" (nothing highlighted) when the filter sheet
     * applied several sectors — the shortcut never pretends a multi-sector filter is one sector.
     */
    fun selectedSector(query: ScreenerQuery?): String {
        val values = query?.choices?.firstOrNull { it.field == ChoiceField.SECTOR }?.values.orEmpty()
        return when (values.size) { 0 -> ALL_SECTORS; 1 -> values.single(); else -> "" }
    }

    /** The sector filter values for a tapped option: none for "All sectors", else that one sector (the same filter the sheet builds). */
    fun sectorValues(option: String): List<String> = if (option == ALL_SECTORS || option.isEmpty()) emptyList() else listOf(option)

    // ---------- Result rows ----------

    /** A row's metrics with "No data available" for missing values (never "N/A", zero or a guess); [filtered] = one of the applied filters. */
    fun metricItems(row: ScreenerRowView, shown: List<MetricDefinition>, applied: ScreenerQuery?): List<ScreenerMetricItem> {
        val filtered = applied?.ranges.orEmpty().filter { it.min != null || it.max != null }.map { it.metric }.toSet()
        return row.metrics.mapIndexed { index, (label, cell) ->
            val id = shown.getOrNull(index)?.id
            val text = if (cell.available || cell.text == "None") cell.text else UNAVAILABLE
            ScreenerMetricItem(id, label, text, cell.available, cell.tone, id != null && id in filtered, "$label ${if (cell.available) cell.accessibility else text.lowercase()}")
        }
    }
}

data class FilterCategoryView(
    val id: String,
    val title: String,
    /** The first few filter names, for the category row ("P/E ratio (trailing), Price / sales, …"). */
    val detail: String,
    val metrics: List<MetricDefinition>,
    val choices: List<ChoiceField>,
    val activeCount: Int
) {
    val accessibility: String get() = "$title. $detail" + if (activeCount > 0) ". ${ScreenerDisplay.filterCount(activeCount)} set" else ""
}

data class RangeBounds(val min: Double?, val max: Double?)

data class ActiveFilterView(
    val id: String,
    val label: String,
    val value: String,
    val spoken: String,
    val metric: String? = null,
    val field: ChoiceField? = null
) { val chipText: String get() = "$label: $value" }

data class SortOption(val sort: ScreenerSort, val label: String)

data class ScreenerMetricItem(
    val metricId: String?,
    val label: String,
    val text: String,
    val available: Boolean,
    val tone: Tone,
    val filtered: Boolean,
    val spoken: String
)
