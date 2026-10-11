package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.FinancialAvailability
import kotlin.test.*

/** Global UI Refinement Phase 5B: shared Discover Stocks display text (labels, categories, operators, rows and states). */
class ScreenerDisplayTest {
    private val pe = ScreenerDefinitions.metric("pe")!!
    private val yield = ScreenerDefinitions.metric("dividendYield")!!
    private val cap = ScreenerDefinitions.metric("marketCap")!!

    @Test fun countsAndCoverageAreWordedOnce() {
        assertEquals("1 company matches", ScreenerDisplay.matchLine(1))
        assertEquals("24 companies match", ScreenerDisplay.matchLine(24))
        assertEquals("0 companies match", ScreenerDisplay.matchLine(0))
        assertEquals("No filters", ScreenerDisplay.filterCount(0))
        assertEquals("1 filter", ScreenerDisplay.filterCount(1))
        assertEquals("3 filters", ScreenerDisplay.filterCount(3))
        assertEquals("Checked all 150 companies", ScreenerDisplay.coverageLine(UniverseInfo("u", 150, 150, true), 0))
        // A partial evaluation never reads as full-market coverage.
        assertEquals("Checked 120 of 150 companies · 4 left out for missing data", ScreenerDisplay.coverageLine(UniverseInfo("u", 150, 120, false), 4))
        assertNull(ScreenerDisplay.coverageLine(null, 0))
    }

    @Test fun resultsExplainWhichValuesTheFiltersChecked() {
        val query = ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 25.0)))
        assertEquals("Each company shows the values your filters checked: P/E ratio (trailing).",
            ScreenerDisplay.resultsExplanation(query, listOf(pe)))
        // Without range filters the shown values are context, not a reason for the match.
        assertEquals("Each company shows P/E ratio (trailing) and Dividend yield for context.", ScreenerDisplay.resultsExplanation(ScreenerQuery(), listOf(pe, yield)))
        assertNull(ScreenerDisplay.resultsExplanation(query, emptyList()))
    }

    @Test fun categoriesFollowTheExistingGroupsAndCountTheDraft() {
        val choices = mapOf(ChoiceField.SECTOR to listOf("Technology", "Energy"), ChoiceField.COUNTRY to listOf("US"))
        val draft = ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 25.0), RangeFilter("priceSales", max = 5.0), RangeFilter("marketCap", min = 1e10)),
            choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("Energy"))))
        val categories = ScreenerDisplay.categories(ScreenerDefinitions.metrics, choices, draft)
        assertEquals(listOf("Company characteristics", "Valuation", "Growth", "Profitability", "Financial health", "Shareholder returns"), categories.map { it.title })
        val company = categories.first()
        // Only attributes with a real choice are offered (one country = nothing to choose); market metrics live here too.
        assertEquals(listOf(ChoiceField.SECTOR), company.choices)
        assertEquals(2, company.activeCount)
        assertTrue(company.metrics.any { it.id == "marketCap" })
        val valuation = categories[1]
        assertEquals(2, valuation.activeCount)
        // Comparison-only metrics are never offered as filters; dividend yield stays where it is defined (Valuation).
        assertFalse(valuation.metrics.any { it.id == "forwardPe" })
        assertTrue(valuation.metrics.any { it.id == "dividendYield" })
        assertTrue(valuation.accessibility.endsWith("2 filters set"), valuation.accessibility)
        assertFalse(categories.last().metrics.any { !it.filterable })
    }

    @Test fun operatorsAreTheSameInclusiveMinAndMax() {
        assertEquals(ScreenerDisplay.Operator.ANY, ScreenerDisplay.operatorOf(null))
        assertEquals(ScreenerDisplay.Operator.AT_LEAST, ScreenerDisplay.operatorOf(RangeFilter("pe", min = 5.0)))
        assertEquals(ScreenerDisplay.Operator.AT_MOST, ScreenerDisplay.operatorOf(RangeFilter("pe", max = 25.0)))
        assertEquals(ScreenerDisplay.Operator.BETWEEN, ScreenerDisplay.operatorOf(RangeFilter("pe", 5.0, 25.0)))
        assertEquals(RangeBounds(5.0, null), ScreenerDisplay.bounds(ScreenerDisplay.Operator.AT_LEAST, 5.0, 99.0))
        assertEquals(RangeBounds(null, 25.0), ScreenerDisplay.bounds(ScreenerDisplay.Operator.AT_MOST, 25.0, null))
        assertEquals(RangeBounds(5.0, 25.0), ScreenerDisplay.bounds(ScreenerDisplay.Operator.BETWEEN, 5.0, 25.0))
        assertEquals(RangeBounds(null, null), ScreenerDisplay.bounds(ScreenerDisplay.Operator.ANY, 5.0, 25.0))
        // Inputs pre-fill from the stored range.
        assertEquals(25.0, ScreenerDisplay.firstValue(RangeFilter("pe", max = 25.0)))
        assertNull(ScreenerDisplay.secondValue(RangeFilter("pe", max = 25.0)))
        assertEquals(25.0, ScreenerDisplay.secondValue(RangeFilter("pe", 5.0, 25.0)))
        assertEquals("The first value is above the second.", ScreenerDisplay.boundsError(30.0, 10.0))
        assertNull(ScreenerDisplay.boundsError(10.0, 10.0))
    }

    @Test fun inputsUseDisplayUnits() {
        assertEquals("billion", ScreenerDisplay.inputUnit(cap))
        assertEquals("%", ScreenerDisplay.inputUnit(yield))
        assertEquals("×", ScreenerDisplay.inputUnit(pe))
        assertEquals(2e9, ScreenerDisplay.parseInput("2", cap))
        assertEquals(2.5, ScreenerDisplay.parseInput("2,5", yield))
        assertNull(ScreenerDisplay.parseInput("", pe))          // empty = no limit, never zero
        assertNull(ScreenerDisplay.parseInput("abc", pe))
        assertEquals("2", ScreenerDisplay.inputText(2e9, cap))
        assertEquals("12.5", ScreenerDisplay.inputText(12.5, yield))
        assertEquals("", ScreenerDisplay.inputText(null, pe))
    }

    @Test fun activeFiltersReadAsWordsAndKeepOrder() {
        val query = ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 25.0), RangeFilter("dividendYield", min = 2.0), RangeFilter("evEbitda")),
            choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("Energy", "Utilities"))))
        val filters = ScreenerDisplay.activeFilters(query)
        assertEquals(listOf("P/E ratio (trailing): ≤ 25.0×", "Dividend yield: ≥ 2.0%", "Sector: Energy, Utilities"), filters.map { it.chipText })
        assertEquals("P/E ratio (trailing), at most 25.0×", filters[0].spoken)
        assertEquals("Sector, Energy or Utilities", filters[2].spoken)
        assertEquals("pe", filters[0].metric)
        assertEquals(ChoiceField.SECTOR, filters[2].field)
        assertTrue(ScreenerDisplay.activeFilters(null).isEmpty())
        assertEquals("Market cap (USD): $2.0B–$10.0B", ScreenerDisplay.activeFilters(ScreenerQuery(ranges = listOf(RangeFilter("marketCap", 2e9, 1e10)))).single().chipText)
    }

    @Test fun sortLabelsAreShortAndSpokenInWords() {
        assertEquals("Sort: Market cap ↓", ScreenerDisplay.sortLabel(ScreenerSort()))
        assertEquals("Sorted by market cap, high to low", ScreenerDisplay.sortSpoken(ScreenerSort()))
        assertEquals("Sort: Name (A–Z)", ScreenerDisplay.sortLabel(ScreenerSort(SortField.NAME, descending = false)))
        assertEquals("Sort: P/E ratio (trailing) ↑", ScreenerDisplay.sortLabel(ScreenerSort(SortField.METRIC, "pe", false)))
        val options = ScreenerDisplay.sortOptions(listOf(pe))
        assertEquals(6, options.size)
        assertEquals("P/E ratio (trailing) (low to high)", options.last().label)
    }

    private fun row(price: String = "$336.67", change: MetricCell = MetricFormatter.change(0.91), metrics: List<Pair<String, MetricCell>> = emptyList()) =
        ScreenerRowView("AAPL", "Apple Inc.", "NASDAQ", "USD", null, price, change, metrics, emptyList(), stale = false, selected = false)

    @Test fun missingMetricsAreNeverZeroOrNa() {
        val metrics = listOf(
            pe.label to MetricFormatter.cell(pe, MetricValue(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR)),
            yield.label to MetricFormatter.cell(yield, MetricValue(0.0, FinancialAvailability.NO_DIVIDEND)),
            "Net margin" to MetricFormatter.cell(ScreenerDefinitions.metric("netMargin"), MetricValue(24.3, FinancialAvailability.AVAILABLE)))
        val shown = listOf(pe, yield, ScreenerDefinitions.metric("netMargin")!!)
        val items = ScreenerDisplay.metricItems(row(metrics = metrics), shown, ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 25.0))))
        assertEquals(listOf("No data available", "None", "24.3%"), items.map { it.text })
        assertEquals(listOf(true, false, false), items.map { it.filtered })
        assertFalse(items[0].available)
        assertEquals("P/E ratio (trailing) no data available", items[0].spoken)
        assertTrue(items.none { it.text == "N/A" || it.text.startsWith("0") })
    }

    @Test fun rowSpeaksDirectionInWordsAndMissingPrices() {
        assertTrue(row().accessibility.contains("Price $336.67, up 0.91% today."), row().accessibility)
        assertTrue(row(change = MetricFormatter.change(-1.5)).accessibility.contains("down 1.50% today"))
        assertTrue(row(change = MetricFormatter.change(0.0)).accessibility.contains("unchanged today"))
        assertTrue(row(change = MetricFormatter.change(null)).accessibility.contains("change unavailable"))
        val missing = row(price = MISSING_PRICE, change = MetricFormatter.change(null),
            metrics = listOf(pe.label to MetricFormatter.cell(pe, MetricValue())))
        assertFalse(missing.priceAvailable)
        assertTrue(missing.accessibility.contains("Price unavailable."))
        assertTrue(missing.accessibility.contains("P/E ratio (trailing) no data available."))
        assertFalse(missing.accessibility.contains("$0"))
        assertTrue(row().priceAvailable)
    }

    @Test fun presetCardsDescribeCharacteristicsNotAdvice() {
        ScreenerDefinitions.presets.forEach { preset ->
            val summary = ScreenerDisplay.presetSummary(preset)
            listOf("good", "safe", "cheap", "undervalued", "best", "buy").forEach { word -> assertFalse(summary.lowercase().contains(word), "${preset.id}: $summary") }
            assertNotEquals(preset.description, summary, "a short card summary exists for ${preset.id}")
            assertEquals(preset.ranges.size, ScreenerDisplay.presetCriteria(preset).size)
        }
        assertEquals("P/E ratio (trailing): 5.0×–25.0×", ScreenerDisplay.presetCriteria(ScreenerDefinitions.preset(ScreenerDefinitions.VALUATION)!!).first())
    }

    // ---------- Presenter states the screens rely on ----------

    private class Data(val total: Int, var fail: Boolean = false) : ScreenerDataSource {
        val searches = mutableListOf<ScreenerQuery>()
        private val records = (1..total).map { CompanyRecord("S$it", "Company $it", price = 10.0 * it, changePercent = 1.0) }
        override suspend fun catalog() = ScreenerCatalog(ScreenerDefinitions.presets, ScreenerDefinitions.metrics, universe = UniverseInfo("u", 5, 5, true), updatedAt = "now")
        override suspend fun search(query: ScreenerQuery): ScreenerPage {
            searches += query
            if (fail) throw IllegalStateException("down")
            return ScreenerPage(records.map { ScreenerResultRow(it) }, records.size, null, UniverseInfo("u", 5, 5, true), query, asOf = "now")
        }
        override suspend fun compare(symbols: List<String>) = error("unused")
        override suspend fun performance(symbols: List<String>, period: PerformancePeriod) = error("unused")
    }

    private fun run(data: Data, block: suspend (ScreenerPresenter) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val presenter = ScreenerPresenter(data, ComparisonSelection(), scope)
            presenter.start()
            withTimeout(5_000) { presenter.state.first { it.presets.isNotEmpty() } }
            block(presenter)
        } finally { scope.cancel() }
    }

    @Test fun emptyResultsAndFailuresAreDifferentStates() {
        run(Data(0)) { presenter ->
            presenter.apply()
            val empty = withTimeout(5_000) { presenter.state.first { it.applied != null && !it.loading } }
            assertNull(empty.error)
            assertEquals(0, empty.total)
            assertTrue(empty.rows.isEmpty())
        }
        val failing = Data(3, fail = true)
        run(failing) { presenter ->
            presenter.apply()
            val failed = withTimeout(5_000) { presenter.state.first { it.error != null } }
            assertEquals(0, failed.total, "a failed request never reports matches")
            failing.fail = false
            presenter.retry()
            assertEquals(3, withTimeout(5_000) { presenter.state.first { !it.loading && it.error == null && it.total == 3 } }.rows.size)
        }
    }

    @Test fun filtersPersistUntilClearedAndRemovingOneKeepsTheRest() = run(Data(2)) { presenter ->
        presenter.setRange("pe", null, 25.0)
        presenter.setChoice(ChoiceField.SECTOR, listOf("Energy"))
        presenter.apply()
        withTimeout(5_000) { presenter.state.first { it.applied?.activeFilterCount == 2 && !it.loading } }
        // The editor reopens on the applied filters (the draft is the applied query).
        assertEquals(presenter.state.value.applied, presenter.state.value.draft)
        assertEquals(2, ScreenerDisplay.activeFilters(presenter.state.value.applied).size)
        // Removing a chip: clear that filter, then apply (what both screens do).
        presenter.setChoice(ChoiceField.SECTOR, emptyList()); presenter.apply()
        val one = withTimeout(5_000) { presenter.state.first { it.applied?.activeFilterCount == 1 && !it.loading } }
        assertEquals(listOf("pe"), ScreenerDisplay.activeFilters(one.applied).map { it.metric })
        presenter.resetAll()
        assertEquals(0, withTimeout(5_000) { presenter.state.first { it.applied?.activeFilterCount == 0 && !it.loading } }.activeFilterCount)
    }

    @Test fun catalogFailureCanBeRetriedWithStart() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var fail = true
        val data = object : ScreenerDataSource {
            override suspend fun catalog(): ScreenerCatalog { if (fail) throw IllegalStateException("down"); return ScreenerCatalog(ScreenerDefinitions.presets, ScreenerDefinitions.metrics, universe = UniverseInfo("u", 1, 1, true), updatedAt = "now") }
            override suspend fun search(query: ScreenerQuery) = error("unused")
            override suspend fun compare(symbols: List<String>) = error("unused")
            override suspend fun performance(symbols: List<String>, period: PerformancePeriod) = error("unused")
        }
        try {
            val presenter = ScreenerPresenter(data, ComparisonSelection(), scope)
            presenter.start()
            assertNotNull(withTimeout(5_000) { presenter.state.first { it.catalogError != null } }.catalogError)
            fail = false
            presenter.start() // the screens' "Try again"
            assertNull(withTimeout(5_000) { presenter.state.first { it.presets.isNotEmpty() } }.catalogError)
        } finally { scope.cancel() }
    }
}
