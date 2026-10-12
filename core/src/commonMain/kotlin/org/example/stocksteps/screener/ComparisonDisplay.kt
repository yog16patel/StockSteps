package org.example.stocksteps.screener

/**
 * Global UI Refinement Phase 5C: display text for Compare Companies shared by Compose and SwiftUI (copy, compact missing-value wording and
 * spoken labels that name the company for every value). Formatting only: values, periods, FX notes and explanations come unchanged from
 * `ComparisonPresenter` / `MetricFormatter`; nothing is calculated, inferred or filled in.
 */
object ComparisonDisplay {
    const val TITLE = "Compare Companies"
    /** The second sentence keeps the long-standing no-ranking safeguard in the header. */
    const val SUBTITLE = "See how companies differ across key financial metrics. The numbers don't say which is a better investment."
    /** Compact wording for a narrow value cell; the reason stays one tap away ([missingAction]). Full-width text uses "No data available". */
    const val NO_DATA = "No data"
    const val NO_DATA_FULL = "No data available"
    const val FEWER_TITLE = "Pick two companies to start"
    const val FEWER_MESSAGE = "Add at least two companies to see a comparison. Search, use Compare on a company's page or your watchlist, or try an example:"
    const val ERROR_TITLE = "Couldn't load the comparison"
    const val CHART_ERROR_TITLE = "Couldn't load the chart"
    const val CHART_ERROR_NOTE = "The rest of the comparison is still available. This only affects the share price chart."
    const val PARTIAL = "Partial data"

    /** What a cell shows: "No data" for a missing value (never "N/A" or 0); "None" (no dividend) and real values unchanged. */
    fun cellText(cell: MetricCell): String = if (!cell.available && cell.text == "N/A") NO_DATA else cell.text

    /** Spoken value for one company: "Microsoft Corporation, Revenue, FY ended Jun 2025, $281.7B" or "…, unavailable. <reason>". */
    fun cellSpoken(column: CompanyColumn, row: ComparisonRow, cell: MetricCell): String {
        val head = listOfNotNull(column.name, row.label, row.period).joinToString(", ")
        return if (cell.available) "$head, ${cell.accessibility}"
        else "$head, ${if (cell.text == "None") "none" else "unavailable"}. ${cell.explanation.orEmpty()}".trimEnd()
    }

    /** The whole row as one spoken element (side-by-side layout), companies in selection order. */
    fun rowSpoken(row: ComparisonRow, columns: List<CompanyColumn>): String =
        (listOfNotNull(row.label, row.period).joinToString(", ") + ". ") + columns.zip(row.cells).joinToString("; ") { (column, cell) ->
            "${column.name} " + if (cell.available) cell.accessibility else if (cell.text == "None") "none" else "unavailable"
        }

    /** Label of the control that explains a missing value: "Market cap unavailable for TSLA. Explain why." */
    fun missingAction(row: ComparisonRow, column: CompanyColumn): String = "${row.label} unavailable for ${column.symbol}. Explain why."

    /** Selected company, spoken: "Apple Inc., AAPL, NASDAQ · USD, partial data". */
    fun selectionSpoken(company: SelectedCompany, column: CompanyColumn?): String =
        listOfNotNull(column?.name ?: company.name, company.symbol, column?.listing?.ifBlank { null }, if (column?.error != null) PARTIAL.lowercase() else null)
            .joinToString(", ")

    /** Every piece of visible text in [rows] that a value column must fit without breaking a word (values, short periods and "No data"). */
    fun layoutTexts(rows: List<ComparisonRow>): List<String> = rows.flatMap { row ->
        row.cells.flatMap { cell -> listOfNotNull(cellText(cell), cell.detail?.let(::shortPeriod)) }
    }

    // ---------- Phase 5C.1: collapsible sections, short periods, comparison bars ----------

    /** Growth and Profitability open by default; every other section (and the history card) starts collapsed. */
    fun expandedByDefault(title: String): Boolean = title == "Growth" || title == "Profitability"

    const val HISTORY_TITLE = "Historical comparison"

    /** Collapsed header detail: "4 metrics". */
    fun sectionSummary(section: ComparisonSection): String = "${section.rows.size} ${if (section.rows.size == 1) "metric" else "metrics"}"

    /** Spoken header: "Overview, 4 metrics, collapsed". */
    fun sectionSpoken(section: ComparisonSection, expanded: Boolean): String = "${section.title}, ${sectionSummary(section)}, ${if (expanded) "expanded" else "collapsed"}"

    /** The section that holds a metric row (to open it before scrolling to that row). */
    fun sectionOf(sections: List<ComparisonSection>, rowId: String): ComparisonSection? = sections.firstOrNull { s -> s.rows.any { it.id == rowId } }

    /**
     * Short visible period; the full text stays in spoken labels and the metric's info sheet. Only the known patterns are shortened:
     * "Trailing twelve months" → "TTM", "FY ended Sep 2025" → "FY Sep 2025", "Q3 FY2026 vs Q3 FY2025" → "Q3 FY26 vs Q3 FY25",
     * "Periods differ by company" → "Periods differ". Anything else is returned unchanged.
     */
    fun shortPeriod(text: String): String = when {
        text == "Trailing twelve months" -> "TTM"
        text == "Periods differ by company" -> "Periods differ"
        text == "Latest quote; in each listing's currency" -> "Latest quote · local currency"
        text.startsWith("FY ended ") -> "FY " + text.removePrefix("FY ended ")
        QUARTER_PAIR.matches(text) -> text.replace(Regex("""FY20(\d\d)"""), "FY$1")
        else -> text
    }
    private val QUARTER_PAIR = Regex("""^Q[1-4] FY20\d\d vs Q[1-4] FY20\d\d$""")

    /** The period shared by every row of a section (shown once under its title instead of on each row), or null. */
    fun sharedPeriod(section: ComparisonSection): String? =
        section.rows.map { it.period }.distinct().singleOrNull()?.takeIf { section.rows.size > 1 }

    /** Under every section with bars, once: bars show size, never which is better. */
    const val BAR_NOTE = "Bar length shows the size of each value, not which company is better."

    /**
     * Compact horizontal bars for a row (Phase 5C.1): only for percentage metrics (never money, so amounts in different currencies are never drawn
     * against each other) with at least two values. Per cell: null = missing (shown as "No data", no bar); otherwise the bar's share of the
     * longest bar (0 for a genuine zero) and whether the value is negative. Bars are drawn from the same starting edge; negatives are labelled.
     */
    fun bars(row: ComparisonRow): List<ComparisonBar?>? {
        val values = row.cells.map { cell -> cell.value?.takeIf { cell.available && it.isFinite() } }
        if (values.count { it != null } < 2) return null
        val max = values.filterNotNull().maxOf { kotlin.math.abs(it) }
        return values.map { v -> v?.let { ComparisonBar(if (max > 0) (kotlin.math.abs(it) / max).toFloat() else 0f, it < 0) } }
    }

    fun sectionHasBars(section: ComparisonSection): Boolean = section.rows.any { bars(it) != null }

    /**
     * Phase 5C.1 closure: the section from which the company header is pinned — the first open section (in display order) with a row whose values
     * sit in unlabelled columns ([sideBySide] and no bars). Null = no pinned header: bar rows and stacked lines already name every company on
     * each line, so a header over them would only repeat the tickers.
     */
    fun companyHeaderSection(openSections: List<ComparisonSection>, sideBySide: Boolean): String? =
        if (!sideBySide) null else openSections.firstOrNull { section -> section.rows.any { bars(it) == null } }?.title

    // ---------- Final polish: notes behind one control, concise insights, one info action ----------

    /** The section's explanatory notes (section note, then the bar note), shown behind one "What to keep in mind" control instead of always. */
    fun sectionNotes(section: ComparisonSection): List<String> = listOfNotNull(section.note, BAR_NOTE.takeIf { sectionHasBars(section) })

    const val NOTES_LABEL = "What to keep in mind"

    /** "More metrics (26)" / "Hide extra metrics" — the count is the live number of extra metrics. */
    fun moreMetricsLabel(count: Int, showing: Boolean): String = if (showing) "Hide extra metrics" else "More metrics ($count)"
    /** One line under the button so beginners know the extra sections are optional (final polish). */
    const val MORE_METRICS_NOTE = "Optional, more advanced measures. The sections above cover the basics."

    /** Accessibility label of a section header (state is announced separately): title, metric count and — when open with one shared period — that period in full. */
    fun sectionLabel(section: ComparisonSection, open: Boolean): String =
        listOfNotNull(section.title, sectionSummary(section), sharedPeriod(section)?.takeIf { open }).joinToString(", ")
    fun notesSpoken(section: ComparisonSection, open: Boolean): String = "$NOTES_LABEL about ${section.title}, ${if (open) "expanded" else "collapsed"}"

    /** Insights shown before "Show all": the first one; the rest and the footer appear on demand. */
    const val INSIGHTS_PREVIEW = 1
    fun insightsToggle(total: Int, open: Boolean): String? = if (total <= INSIGHTS_PREVIEW) null else if (open) "Show less" else "Show all $total insights"

    /** One accessible info action per metric: guided metrics expand the inline explanation, others open the info sheet. */
    fun infoLabel(row: ComparisonRow, guided: Boolean, expanded: Boolean): String = when {
        guided && expanded -> "Hide explanation of ${row.label}"
        guided -> "Explain ${row.label}"
        else -> "About ${row.label}"
    }

    /** Same for the history table: each cell's value ("No data" when missing) and its fiscal period. */
    fun historyLayoutTexts(view: HistoryView): List<String> = view.rows.flatMap { row ->
        row.cells.flatMap { cell -> listOfNotNull(if (cell.available) cell.text else NO_DATA, cell.period) }
    }
}

/** One company's bar: [fraction] of the longest bar in the row (0–1), [negative] = the value is below zero (labelled, not only coloured). */
data class ComparisonBar(val fraction: Float, val negative: Boolean)

/** One "What the numbers show" item: short heading + concise text; [caveat] only when it is specific to this metric and not shown above. */
data class ObservationItem(val metric: String, val heading: String, val text: String, val caveat: String?) {
    val spoken: String get() = listOfNotNull(heading, text, caveat).joinToString(". ")
}

/** The card: [top] (up to [ComparisonObservationsDisplay.TOP]) shown by default, [more] behind "View all", one shared [note]. */
data class ObservationsView(val subtitle: String, val top: List<ObservationItem>, val more: List<ObservationItem>, val note: String) {
    val total: Int get() = top.size + more.size
}

/**
 * "What the numbers show" (Comparison polish): presentation only over the server's deterministic `ComparisonObservation`s — no value is computed,
 * re-ranked or invented. Picks up to three items from distinct topics (growth, profitability, valuation, financial health, dividends; first available
 * metric per topic, in that order), gives each a short heading, shortens company names to tickers, condenses the engine's "Among these N companies, …
 * ranges from …" sentence (adding "N of M companies report it" when some have no value), moves the repeated "Reporting periods end on different
 * dates (…)" caveat into one shared note and shows each remaining caveat sentence only once. Text in an unexpected shape is kept unchanged.
 */
object ComparisonObservationsDisplay {
    const val TITLE = "What the numbers show"
    const val TOP = 3
    private const val PERIODS_PREFIX = "Reporting periods end on different dates"
    private const val NOT_ADVICE = "These describe reported figures. They aren't rankings or recommendations."
    private const val PERIODS_NOTE = "Reporting periods can differ by company."

    private val topics = listOf(
        listOf("revenueGrowth", "quarterRevenueGrowth"), listOf("netMargin", "operatingMargin"), listOf("pe"),
        listOf("debtEquity", "freeCashFlow"), listOf("dividendYield"))
    private val headings = mapOf(
        "revenueGrowth" to "Revenue growth", "quarterRevenueGrowth" to "Quarterly revenue growth", "netMargin" to "Net margin",
        "operatingMargin" to "Operating margin", "pe" to "Trailing P/E", "debtEquity" to "Debt / equity", "dividendYield" to "Dividend yield",
        "freeCashFlow" to "Free cash flow")
    private val RANGE = Regex("""^Among these (\d+) companies, .+? (ranges from .+)$""")

    fun subtitle(companies: Int): String = "Based on the $companies companies selected"
    fun toggle(total: Int, open: Boolean): String? = if (total <= TOP) null else if (open) "Show fewer insights" else "View all $total insights"

    fun view(observations: List<ComparisonObservation>, columns: List<CompanyColumn>): ObservationsView {
        val topicOf = { metric: String -> topics.indexOfFirst { metric in it }.let { if (it < 0) topics.size else it } }
        // Top: the first observation of each topic in topic order; then everything else in the engine's order.
        val firsts = observations.groupBy { topicOf(it.metric) }.filterKeys { it < topics.size }.entries.sortedBy { it.key }.map { (_, group) ->
            group.minBy { o -> topics[topicOf(o.metric)].indexOf(o.metric) }
        }
        val top = firsts.take(TOP)
        val more = observations.filter { it !in top }
        val shown = mutableSetOf<String>()
        var periodsDiffer = false
        fun item(o: ComparisonObservation): ObservationItem {
            var caveat = o.caveat?.trim().orEmpty()
            if (caveat.startsWith(PERIODS_PREFIX)) {
                periodsDiffer = true
                caveat = caveat.substringAfter(").", "").trim()
            }
            val sentences = caveat.split(Regex("""(?<=\.)\s+""")).map { it.trim() }.filter { it.isNotEmpty() && shown.add(it) }
            return ObservationItem(o.metric, headings[o.metric] ?: ScreenerDefinitions.metric(o.metric)?.label ?: o.metric,
                concise(o.text, columns), sentences.joinToString(" ").ifEmpty { null })
        }
        val topItems = top.map(::item)
        val moreItems = more.map(::item)
        return ObservationsView(subtitle(columns.size), topItems, moreItems, if (periodsDiffer) "$PERIODS_NOTE $NOT_ADVICE" else NOT_ADVICE)
    }

    /** Tickers instead of company names; the range sentence without its repeated lead-in, plus how many companies report the metric. */
    fun concise(text: String, columns: List<CompanyColumn>): String {
        val short = columns.sortedByDescending { it.name.length }.fold(text) { t, c -> if (c.name.isBlank()) t else t.replace(c.name, c.symbol) }
        val match = RANGE.matchEntire(short) ?: return short
        val reporting = match.groupValues[1].toIntOrNull()
        val range = match.groupValues[2].replaceFirstChar { it.uppercase() }
        return if (reporting != null && reporting < columns.size) "${range.removeSuffix(".")} · $reporting of ${columns.size} companies report it." else range
    }
}
