package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.*
import kotlin.test.*

class ScreenerEngineTest {
    private fun v(value: Double?, availability: FinancialAvailability = FinancialAvailability.AVAILABLE, period: String = "TTM", date: String? = "2025-12-31") =
        MetricValue(value, if (value == null && availability == FinancialAvailability.AVAILABLE) FinancialAvailability.MISSING else availability, FinancialBasis(period, date))

    private fun company(symbol: String, cap: Double, sector: String = "Technology", country: String = "US", exchange: String = "NASDAQ", currency: String = "USD", vararg metrics: Pair<String, MetricValue>) =
        CompanyRecord(symbol, "$symbol Inc", exchange, country, currency, sector, "Software", metrics = mapOf("marketCap" to v(cap)) + metrics, changePercent = cap / 1e12)

    private val universe = listOf(
        company("AAA", 3e12, metrics = arrayOf("pe" to v(30.0), "revenueGrowth" to v(12.0), "epsGrowth" to v(10.0), "netMargin" to v(25.0), "debtEquity" to v(0.5))),
        company("BBB", 2e12, metrics = arrayOf("pe" to v(15.0), "revenueGrowth" to v(4.0), "epsGrowth" to v(-3.0), "netMargin" to v(10.0), "debtEquity" to v(2.0))),
        company("CCC", 5e11, sector = "Financial Services", metrics = arrayOf("pe" to v(11.0), "revenueGrowth" to v(8.0), "epsGrowth" to v(9.0), "netMargin" to v(30.0), "debtEquity" to v(6.0))),
        company("DDD.TO", 1e11, country = "CA", exchange = "TSX", currency = "CAD", metrics = arrayOf("pe" to v(null, FinancialAvailability.NON_POSITIVE_DENOMINATOR), "revenueGrowth" to v(20.0),
            "epsGrowth" to v(null, FinancialAvailability.UNRELIABLE_COMPARISON), "netMargin" to v(-5.0), "debtEquity" to v(0.2))),
        company("EEE.TO", 4e10, sector = "Energy", country = "CA", exchange = "TSX", currency = "CAD", metrics = arrayOf("pe" to v(22.0), "revenueGrowth" to v(Double.NaN), "netMargin" to v(8.0)))
    )
    private fun symbols(e: ScreenerEngine.Evaluation) = e.matches.map { it.company.symbol }
    private fun query(vararg ranges: RangeFilter, choices: List<ChoiceFilter> = emptyList(), sort: ScreenerSort = ScreenerSort(), pageSize: Int = 20) =
        ScreenerQuery(ranges = ranges.toList(), choices = choices, sort = sort, pageSize = pageSize)

    // ---------- Presets and filters ----------

    @Test fun presetsAreExplicitFilterDefinitionsNotSymbolLists() {
        assertEquals(listOf("growing", "dividend", "strong", "valuation"), ScreenerDefinitions.presets.map { it.id })
        for (preset in ScreenerDefinitions.presets) {
            assertTrue(preset.ranges.isNotEmpty())
            preset.ranges.forEach { r ->
                val definition = assertNotNull(ScreenerDefinitions.metric(r.metric), r.metric)
                assertTrue(definition.filterable)
                assertTrue(r.min != null || r.max != null)
            }
            assertTrue(preset.dataPeriod.isNotBlank() && preset.missingData.isNotBlank())
            assertTrue(preset.displayMetrics.size in 2..3)
            ScreenerEngine.validate(preset.query())
            assertFalse(Regex("(?i)\\b(good|safe|cheap|guaranteed|best|buy)\\b").containsMatchIn(preset.name + preset.description), preset.description)
        }
    }

    @Test fun minimumMaximumAndCombinedFilters() {
        assertEquals(listOf("AAA", "CCC", "DDD.TO"), symbols(ScreenerEngine.evaluate(universe, query(RangeFilter("revenueGrowth", min = 5.0)))))
        assertEquals(listOf("BBB", "CCC", "EEE.TO"), symbols(ScreenerEngine.evaluate(universe, query(RangeFilter("pe", max = 25.0)))))
        assertEquals(listOf("CCC"), symbols(ScreenerEngine.evaluate(universe, query(RangeFilter("pe", 10.0, 25.0), RangeFilter("revenueGrowth", min = 5.0)))))
    }

    @Test fun emptyResultsAreValid() {
        val result = ScreenerEngine.evaluate(universe, query(RangeFilter("pe", 1.0, 2.0)))
        assertTrue(result.matches.isEmpty())
    }

    @Test fun missingNegativeAndInvalidMetricsAreExcludedAndCounted() {
        // DDD.TO has no meaningful P/E (losses); it's never treated as a P/E of 0.
        val pe = ScreenerEngine.evaluate(universe, query(RangeFilter("pe", max = 100.0)))
        assertFalse("DDD.TO" in symbols(pe))
        assertEquals(1, pe.excludedForMissingData)
        // NaN revenue growth is invalid, not a match.
        val growth = ScreenerEngine.evaluate(universe, query(RangeFilter("revenueGrowth", min = -100.0)))
        assertFalse("EEE.TO" in symbols(growth))
        // EPS growth across a loss is unavailable, never a percentage.
        assertFalse("DDD.TO" in symbols(ScreenerEngine.evaluate(universe, query(RangeFilter("epsGrowth", min = -100.0)))))
    }

    @Test fun invalidFiltersAreRejected() {
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(RangeFilter("pe", 30.0, 10.0))) }
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(RangeFilter("pe", min = Double.NaN))) }
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(RangeFilter("madeUpRatio", min = 1.0))) }
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(RangeFilter("forwardPe", max = 20.0))) } // comparison-only
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(RangeFilter("pe", min = 1.0), RangeFilter("pe", max = 9.0))) }
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.validate(query(pageSize = 500)) }
    }

    @Test fun sectorExchangeAndCanadianFiltering() {
        assertEquals(listOf("CCC"), symbols(ScreenerEngine.evaluate(universe, query(choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("financial services")))))))
        assertEquals(listOf("DDD.TO", "EEE.TO"), symbols(ScreenerEngine.evaluate(universe, query(choices = listOf(ChoiceFilter(ChoiceField.EXCHANGE, listOf("TSX")))))))
        assertEquals(listOf("DDD.TO", "EEE.TO"), symbols(ScreenerEngine.evaluate(universe, query(choices = listOf(ChoiceFilter(ChoiceField.COUNTRY, listOf("CA")))))))
    }

    @Test fun leverageCriteriaAreNotAppliedToFinancialsAndSaySo() {
        val result = ScreenerEngine.evaluate(universe, query(RangeFilter("debtEquity", max = 1.0)))
        assertEquals(listOf("AAA", "CCC", "DDD.TO"), symbols(result))
        assertTrue(result.matches.first { it.company.symbol == "CCC" }.notApplied.single().contains("financial services"))
    }

    // ---------- Sorting and pagination ----------

    @Test fun sortingPutsMissingValuesLastInBothDirections() {
        val desc = ScreenerEngine.evaluate(universe, query(sort = ScreenerSort(SortField.METRIC, "pe", true)))
        assertEquals(listOf("AAA", "EEE.TO", "BBB", "CCC", "DDD.TO"), symbols(desc))
        val asc = ScreenerEngine.evaluate(universe, query(sort = ScreenerSort(SortField.METRIC, "pe", false)))
        assertEquals(listOf("CCC", "BBB", "EEE.TO", "AAA", "DDD.TO"), symbols(asc))
        assertEquals(listOf("AAA", "BBB", "CCC", "DDD.TO", "EEE.TO"), symbols(ScreenerEngine.evaluate(universe, query(sort = ScreenerSort(SortField.NAME, descending = false)))))
        assertEquals("EEE.TO", symbols(ScreenerEngine.evaluate(universe, query(sort = ScreenerSort(SortField.MARKET_CAP, descending = false)))).first())
    }

    @Test fun cursorPagingCoversTheWholeSortedUniverseExactlyOnce() {
        val q = query(sort = ScreenerSort(SortField.METRIC, "netMargin", true), pageSize = 2)
        val all = ScreenerEngine.evaluate(universe, q).matches
        val collected = mutableListOf<String>()
        var cursor: String? = null
        do {
            val (rows, next) = ScreenerEngine.page(all, q.copy(cursor = cursor))
            assertTrue(rows.size <= 2)
            collected += rows.map { it.company.symbol }
            cursor = next
        } while (cursor != null)
        assertEquals(all.map { it.company.symbol }, collected) // global order, not page-local sorting
        assertEquals(collected.distinct(), collected)
        val (_, next) = ScreenerEngine.page(all, q)
        assertFailsWith<ScreenerValidationException> { ScreenerEngine.page(all, q.copy(sort = ScreenerSort(), cursor = next)) } // cursor from another query
    }

    // ---------- Record building ----------

    private fun fundamentals(retrievedAt: String = "2026-10-01T00:00:00Z", fyDate: String = "2025-12-31", priorEps: Double = 1.0) = CompanyFundamentals(
        "XYZ",
        financials = CompanyFinancials(growth = mapOf("epsGrowth" to FinancialFact(20.0, availability = FinancialAvailability.AVAILABLE)),
            shareholderReturns = mapOf("dividendYield" to FinancialFact(availability = FinancialAvailability.NO_DIVIDEND), "shares" to FinancialFact(amount = 1_000_000_000, availability = FinancialAvailability.AVAILABLE))),
        valuation = CompanyValuation(mapOf("pe" to FinancialFact(18.0, availability = FinancialAvailability.AVAILABLE, basis = FinancialBasis("TTM")))),
        retrievedAt = retrievedAt,
        history = listOf(
            FinancialPeriodStatement("FY", 2025, fyDate, "CAD", epsDiluted = 1.2, freeCashFlow = 120.0),
            FinancialPeriodStatement("FY", 2024, "2024-12-31", "CAD", epsDiluted = priorEps, freeCashFlow = 100.0)
        )
    )
    private val quote = StockQuote("XYZ", "XYZ", 50.0, 1.0, 2.0, 51.0, 49.0, yearHigh = 60.0, yearLow = 40.0, marketCap = 1L)
    private val profile = CompanyProfile("XYZ", "XYZ Corp", sector = "Industrials", country = "CA", currency = "CAD", exchange = "TSX")

    @Test fun recordUsesTheSameFactsAsCompanyDetailsAndConvertsMarketCap() {
        val record = CompanyRecordBuilder.build("XYZ", quote, profile, fundamentals(), usdRate = 0.75, today = "2026-10-08")
        assertEquals(18.0, record.metrics["pe"]?.value) // the Company Details P/E fact, unchanged
        assertEquals(50e9 * 0.75, record.metrics["marketCap"]?.value) // price × shares, converted to USD
        assertEquals(50.0, record.metrics["yearRangePosition"]?.value)
        assertEquals(20.0, record.metrics["fcfGrowth"]?.value!!, 1e-9)
        assertEquals(0.0, record.metrics["dividendYield"]?.value) // no dividend counts as 0%, labelled
        assertEquals(FinancialAvailability.NO_DIVIDEND, record.metrics["dividendYield"]?.availability)
        assertFalse(record.stale)
    }

    @Test fun turnaroundGrowthAndStaleFundamentalsAreLabelled() {
        val turnaround = CompanyRecordBuilder.build("XYZ", quote, profile, fundamentals(priorEps = -0.5), 0.75, "2026-10-08")
        assertNull(turnaround.metrics["epsGrowth"]?.value)
        assertEquals(FinancialAvailability.UNRELIABLE_COMPARISON, turnaround.metrics["epsGrowth"]?.availability)
        assertTrue(CompanyRecordBuilder.build("XYZ", quote, profile, fundamentals(fyDate = "2024-02-28"), 0.75, "2026-10-08").stale)
        assertTrue(CompanyRecordBuilder.build("XYZ", quote, profile, fundamentals(retrievedAt = "2026-06-01T00:00:00Z"), 0.75, "2026-10-08").stale)
        assertNull(CompanyRecordBuilder.build("XYZ", quote, profile, fundamentals(), usdRate = null, today = "2026-10-08").metrics["marketCap"]?.value) // no FX → not guessed
    }

    // ---------- Comparison ----------

    @Test fun twoAndFourCompanyObservationsAreDescriptiveOnly() {
        val two = ComparisonEngine.observations(universe.take(2))
        val margin = two.first { it.metric == "netMargin" }
        assertEquals("AAA Inc has a higher net margin than BBB Inc (25.0% vs 10.0%).", margin.text)
        val four = ComparisonEngine.observations(universe.take(4))
        assertTrue(four.first { it.metric == "netMargin" }.text.startsWith("Among these 4 companies, net margin ranges from -5.0% (DDD.TO Inc) to 30.0% (CCC Inc)"))
        // Debt/equity skips the bank; P/E skips the loss-making company.
        assertFalse(four.first { it.metric == "debtEquity" }.text.contains("CCC"))
        assertFalse(four.first { it.metric == "pe" }.text.contains("DDD.TO"))
        for (o in two + four) assertFalse(Regex("(?i)\\b(better|best|winner|should|buy|sell|outperform)\\b").containsMatchIn(o.text), o.text)
    }

    @Test fun mismatchedPeriodsAreCalledOutAndIncompatibleBasesSkipped() {
        val a = company("AAA", 1e12, metrics = arrayOf("operatingMargin" to v(30.0, period = "annual", date = "2025-06-30")))
        val b = company("BBB", 1e12, metrics = arrayOf("operatingMargin" to v(20.0, period = "annual", date = "2025-12-31")))
        val caveat = ComparisonEngine.observations(listOf(a, b)).first { it.metric == "operatingMargin" }.caveat!!
        assertTrue(caveat.contains("different dates"))
        val ttm = company("BBB", 1e12, metrics = arrayOf("operatingMargin" to v(20.0, period = "TTM")))
        assertTrue(ComparisonEngine.observations(listOf(a, ttm)).none { it.metric == "operatingMargin" })
    }

    @Test fun moneyAcrossCurrenciesIsComparedBySignOnly() {
        val usd = company("AAA", 1e12, metrics = arrayOf("freeCashFlow" to v(5e9)))
        val cad = company("BBB.TO", 1e11, currency = "CAD", metrics = arrayOf("freeCashFlow" to v(-1e9)))
        val text = ComparisonEngine.observations(listOf(usd, cad)).single { it.metric == "freeCashFlow" }.text
        assertEquals("AAA Inc reports positive free cash flow; BBB.TO Inc doesn't.", text)
        assertFalse(text.contains("5"))
    }

    // ---------- Performance normalization ----------

    @Test fun performanceIsRebasedTo100OnASharedAxis() {
        val (dates, series) = PerformanceNormalizer.normalize(mapOf(
            "A" to listOf("2026-01-02" to 50.0, "2026-01-05" to 55.0, "2026-01-06" to 60.0),
            "B" to listOf("2026-01-02" to 400.0, "2026-01-06" to 380.0)
        ), "2026-01-01", "2026-01-06")
        assertEquals(listOf("2026-01-02", "2026-01-05", "2026-01-06"), dates)
        fun rounded(values: List<Double?>) = values.map { it?.let { v -> kotlin.math.round(v * 1e6) / 1e6 } }
        assertEquals(listOf(100.0, 110.0, 120.0), rounded(series[0].values))
        assertEquals(listOf(100.0, 100.0, 95.0), rounded(series[1].values)) // holiday carried forward, not a gap
        assertEquals(20.0, series[0].change!!, 1e-9)
        assertEquals(-5.0, series[1].change!!, 1e-9) // higher share price ≠ better performance
    }

    @Test fun unadjustedSplitsAreAdjustedSoTheyDontLookLikeCrashes() {
        val (_, series) = PerformanceNormalizer.normalize(mapOf("A" to listOf("2026-01-02" to 200.0, "2026-01-05" to 202.0, "2026-01-06" to 102.0)),
            "2026-01-01", "2026-01-06", splits = mapOf("A" to listOf(Split("2026-01-06", 2.0))))
        assertEquals(listOf(100.0, 101.0, 102.0), series.single().values.map { it?.let { v -> kotlin.math.round(v * 1e6) / 1e6 } })
    }

    @Test fun historyThatDoesntCoverThePeriodIsUnavailable() {
        val (_, series) = PerformanceNormalizer.normalize(mapOf("A" to listOf("2026-03-01" to 10.0, "2026-03-02" to 11.0)), "2026-01-01", "2026-03-02")
        assertNull(series.single().change)
        assertNotNull(series.single().error)
        assertTrue(series.single().values.all { it == null })
    }

    @Test fun linesShareOneBaseDateAcrossDifferentTradingCalendars() {
        // B's market was closed on Jan 2 (first trading day Jan 5): every line starts at 100 on Jan 5.
        val r = PerformanceNormalizer.compute(mapOf(
            "A" to listOf("2026-01-05" to 55.0, "2026-01-02" to 50.0, "2026-01-06" to 60.0, "2026-01-06" to 61.0),
            "B" to listOf("2026-01-05" to 400.0, "2026-01-06" to 380.0)
        ), "2026-01-01", "2026-01-06")
        assertEquals("2026-01-05", r.baseDate)
        assertEquals(listOf("2026-01-05", "2026-01-06"), r.dates)                                      // sorted, deduplicated, from the base date
        assertEquals(100.0, r.series[0].values.first()); assertEquals(100.0, r.series[1].values.first())
        assertEquals(60.0 / 55.0 * 100 - 100, r.series[0].change!!, 1e-9)                               // first close for a date wins; base = Jan 5
    }

    @Test fun historyEndingEarlyIsMeasuredToItsLastCloseAndGapsArentInvented() {
        val r = PerformanceNormalizer.compute(mapOf(
            "A" to listOf("2026-01-02" to 10.0, "2026-01-05" to 11.0, "2026-01-20" to 12.0, "2026-02-27" to 13.0),
            "B" to listOf("2026-01-02" to 20.0, "2026-01-05" to 22.0)
        ), "2026-01-01", "2026-02-27")
        val b = r.series.first { it.symbol == "B" }
        assertEquals("2026-01-05", b.lastDate); assertEquals(10.0, b.change!!, 1e-9); assertTrue(b.note!!.contains("2026-01-05"))
        val a = r.series.first { it.symbol == "A" }
        assertNull(a.note)
        assertEquals(130.0, a.values[r.dates.indexOf("2026-02-27")]!!, 1e-9)                             // a real close, rebased to the shared Jan 2 base
        assertTrue(b.values.drop(2).all { it == null })                                                 // no closes after Jan 5 + 5 days are drawn
        assertTrue(r.series.all { s -> s.values.filterNotNull().all { it.isFinite() } })
    }

    // ---------- Selection ----------

    @Test fun replaceKeepsPositionAndSetDeduplicatesAndLimits() {
        val selection = ComparisonSelection()
        selection.add("AAPL", "Apple"); selection.add("MSFT", "Microsoft"); selection.add("KO", "Coca-Cola")
        assertEquals(SelectionResult.ADDED, selection.replace("MSFT", "td", "TD Bank"))
        assertEquals(listOf("AAPL", "TD", "KO"), selection.selected.value.map { it.symbol })
        assertEquals(SelectionResult.ALREADY_SELECTED, selection.replace("TD", "aapl", "Apple"))
        assertEquals(listOf("AAPL", "TD", "KO"), selection.selected.value.map { it.symbol })
        // Same ticker on different exchanges are different companies.
        assertEquals(SelectionResult.ADDED, selection.replace("TD", "TD.TO", "TD Bank (TSX)"))
        selection.set(listOf(SelectedCompany("a", "A"), SelectedCompany("A", "A again"), SelectedCompany("B", "B"), SelectedCompany("C", "C"), SelectedCompany("D", "D"), SelectedCompany("E", "E")))
        assertEquals(listOf("A", "B", "C", "D"), selection.selected.value.map { it.symbol })
    }

    @Test fun selectionPreventsDuplicatesAndEnforcesTheMaximum() {
        val selection = ComparisonSelection()
        assertEquals(SelectionResult.ADDED, selection.add("aapl", "Apple"))
        assertEquals(SelectionResult.ALREADY_SELECTED, selection.add("AAPL", "Apple"))
        listOf("MSFT", "RY.TO", "SHOP.TO").forEach { selection.add(it, it) }
        assertEquals(SelectionResult.FULL, selection.add("KO", "Coca-Cola"))
        assertEquals(MAX_COMPARED_COMPANIES, selection.selected.value.size)
        selection.remove("msft")
        assertEquals(listOf("AAPL", "RY.TO", "SHOP.TO"), selection.selected.value.map { it.symbol })
    }

    // ---------- Formatting ----------

    @Test fun unavailableValuesExplainThemselves() {
        val pe = ScreenerDefinitions.metric("pe")
        assertEquals("N/A", MetricFormatter.cell(pe, MetricValue(null, FinancialAvailability.NON_POSITIVE_DENOMINATOR)).text)
        assertTrue(MetricFormatter.cell(pe, MetricValue(null, FinancialAvailability.NON_POSITIVE_DENOMINATOR)).explanation!!.contains("zero or negative"))
        assertEquals("None", MetricFormatter.cell(ScreenerDefinitions.metric("dividendYield"), MetricValue(0.0, FinancialAvailability.NO_DIVIDEND)).text)
        assertEquals("24.5×", MetricFormatter.cell(pe, MetricValue(24.46, FinancialAvailability.AVAILABLE)).text)
        assertEquals("+12.3%", MetricFormatter.cell(ScreenerDefinitions.metric("revenueGrowth"), MetricValue(12.34, FinancialAvailability.AVAILABLE)).text)
        assertEquals("C$1.5B", MetricFormatter.money(1.5e9, "CAD"))
        assertEquals("$2.50T", MetricFormatter.money(2.5e12, "USD"))
        val info = MetricFormatter.education("netMargin", ScreenerDefinitions.metric("netMargin"))
        assertNotNull(info.calculation); assertNotNull(info.why); assertTrue(info.meaning.isNotBlank())
    }
}

class ScreenerPresenterTest {
    private class FakeData : ScreenerDataSource {
        val searches = mutableListOf<ScreenerQuery>()
        var fail = false
        private val records = (1..5).map { i ->
            CompanyRecord("S$i", "Company $i", "NYSE", "US", "USD", "Technology", metrics = mapOf("marketCap" to MetricValue(i * 1e10, FinancialAvailability.AVAILABLE),
                "revenueGrowth" to MetricValue(i * 3.0, FinancialAvailability.AVAILABLE), "epsGrowth" to MetricValue(i * 2.0, FinancialAvailability.AVAILABLE),
                "netMargin" to MetricValue(i * 4.0, FinancialAvailability.AVAILABLE)), price = 10.0 * i, changePercent = 1.0)
        }
        override suspend fun catalog() = ScreenerCatalog(ScreenerDefinitions.presets, ScreenerDefinitions.metrics, universe = UniverseInfo("test", 5, 5, true), updatedAt = "now")
        override suspend fun search(query: ScreenerQuery): ScreenerPage {
            searches += query
            if (fail) throw IllegalStateException("down")
            val evaluation = ScreenerEngine.evaluate(records, query.copy(cursor = null))
            val (rows, next) = ScreenerEngine.page(evaluation.matches, query)
            return ScreenerPage(rows, evaluation.matches.size, next, UniverseInfo("test", 5, 5, true), query, displayMetrics = ScreenerDefinitions.displayMetricsFor(query), asOf = "now")
        }
        override suspend fun compare(symbols: List<String>) = ComparisonResponse(symbols.map { s -> ComparedCompany(records.firstOrNull { it.symbol == s }, s, if (records.none { it.symbol == s }) "Company data isn't available right now." else null) },
            observations = ComparisonEngine.observations(records.filter { it.symbol in symbols }), asOf = "now")
        val performanceCalls = mutableListOf<PerformancePeriod>()
        override suspend fun performance(symbols: List<String>, period: PerformancePeriod): PerformanceComparison {
            performanceCalls += period
            return PerformanceComparison(period, ReturnKind.PRICE_RETURN, listOf("d1", "d2"), symbols.map { PerformanceSeries(it, listOf(100.0, 110.0), 10.0) })
        }
    }

    private fun run(block: suspend CoroutineScope.(FakeData, ScreenerPresenter, ComparisonSelection) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val data = FakeData()
        val selection = ComparisonSelection()
        try {
            val presenter = ScreenerPresenter(data, selection, scope, pageSize = 2)
            presenter.start()
            withTimeout(5_000) { presenter.state.first { it.presets.isNotEmpty() } }
            block(data, presenter, selection)
        } finally { scope.cancel() }
    }

    @Test fun draftEditsDontSearchUntilApplied() = run { data, presenter, _ ->
        presenter.setRange("revenueGrowth", 5.0, null)
        presenter.setRange("revenueGrowth", 7.0, null)
        delay(200)
        assertTrue(data.searches.isEmpty())
        assertEquals(1, presenter.state.value.draft.activeFilterCount)
        presenter.apply()
        val state = withTimeout(5_000) { presenter.state.first { it.total == 3 && !it.loading } }
        assertEquals(listOf(RangeFilter("revenueGrowth", 7.0, null)), data.searches.single().ranges)
        assertEquals(2, state.rows.size) // first page
        assertTrue(state.hasMore)
    }

    @Test fun presetLoadMoreSortResetAndRetry() = run { data, presenter, _ ->
        presenter.selectPreset(ScreenerDefinitions.GROWING)
        withTimeout(5_000) { presenter.state.first { !it.loading && it.applied?.presetId == "growing" } }
        presenter.resetAll()
        withTimeout(5_000) { presenter.state.first { !it.loading && it.total == 5 && it.applied?.ranges?.isEmpty() == true } }
        presenter.loadMore()
        withTimeout(5_000) { presenter.state.first { it.rows.size == 4 } }
        presenter.sort(ScreenerSort(SortField.NAME, descending = false))
        val sorted = withTimeout(5_000) { presenter.state.first { !it.loading && it.applied?.sort?.field == SortField.NAME && it.rows.size == 2 } }
        assertEquals(listOf("S1", "S2"), sorted.rows.map { it.symbol })
        data.fail = true
        presenter.retry()
        assertNotNull(withTimeout(5_000) { presenter.state.first { it.error != null } }.error)
    }

    @Test fun compareSelectionIsSharedAndLimited() = run { _, presenter, selection ->
        presenter.apply()
        withTimeout(5_000) { presenter.state.first { it.rows.isNotEmpty() } }
        listOf("S1", "S2", "S3", "S4").forEach { presenter.toggleCompare(it, it) }
        presenter.toggleCompare("S5", "S5")
        assertNotNull(withTimeout(5_000) { presenter.state.first { it.message != null } }.message)
        assertEquals(4, selection.selected.value.size)
        presenter.toggleCompare("S1", "S1") // toggling removes
        assertEquals(3, withTimeout(5_000) { presenter.state.first { it.selected.size == 3 } }.selected.size)
    }

    @Test fun comparisonAlignsRowsWithColumnsAndExplainsGaps() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val selection = ComparisonSelection()
            val presenter = ComparisonPresenter(FakeData(), selection, scope)
            assertTrue(presenter.state.value.needsMore)
            selection.add("S1", "S1"); selection.add("S4", "S4"); selection.add("MISSING", "Missing")
            val state = withTimeout(5_000) { presenter.state.first { it.columns.size == 3 && !it.loading } }
            state.sections.flatMap { it.rows }.forEach { assertEquals(3, it.cells.size, it.id) }
            assertNotNull(state.columns.last().error)
            assertEquals("N/A", state.sections.first { it.title == "Valuation" }.rows.first { it.id == "pe" }.cells.first().text)
            assertEquals("+10.0%", state.sections.first { it.title == "Price performance and payouts" }.rows.first { it.id == "return1y" }.cells.first().text)
            presenter.remove("MISSING")
            withTimeout(5_000) { presenter.state.first { it.columns.size == 2 } }
            presenter.selectPeriod(PerformancePeriod.FIVE_YEARS)
            assertEquals(PerformancePeriod.FIVE_YEARS, withTimeout(5_000) { presenter.state.first { it.chart?.period == PerformancePeriod.FIVE_YEARS } }.chart?.period)
        } finally { scope.cancel() }
    }
}

/** Company Comparison Phase 1: explanations, currencies, periods and presenter behaviour. */
class ComparisonPhase1Test {
    private fun basis(period: String, date: String?, currency: String? = null) = FinancialBasis(period, date, currency = currency)

    @Test fun unavailableValuesSayWhySpecificToTheMetric() {
        assertTrue(MetricFormatter.explanation("pe", MetricValue(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR)).contains("loss"))
        assertTrue(MetricFormatter.explanation("debtEquity", MetricValue(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR)).contains("equity is zero or negative"))
        assertEquals("Source note.", MetricFormatter.explanation("pe", MetricValue(availability = FinancialAvailability.MISSING, note = "Source note.")))
        assertTrue(MetricFormatter.explanation("dividendYield", MetricValue()).contains("isn't the same as no dividend"))
        val none = MetricFormatter.cell(ScreenerDefinitions.metric("dividendYield"), MetricValue(0.0, FinancialAvailability.NO_DIVIDEND))
        assertEquals("None", none.text)
        val nan = MetricFormatter.cell(ScreenerDefinitions.metric("pe"), MetricValue(Double.NaN, FinancialAvailability.AVAILABLE))
        assertEquals("N/A", nan.text)                                                                  // never "NaN" or "Infinity"
        assertEquals("N/A", MetricFormatter.cell(ScreenerDefinitions.metric("pe"), MetricValue(Double.POSITIVE_INFINITY, FinancialAvailability.AVAILABLE)).text)
    }

    @Test fun mixedCurrenciesGetExplicitMarkers() {
        assertEquals("US$1.2B", MetricFormatter.money(1.2e9, "USD", explicit = true))
        assertEquals("C$1.2B", MetricFormatter.money(1.2e9, "CAD", explicit = true))
        assertEquals("$1.2B", MetricFormatter.money(1.2e9, "USD", explicit = false))
        assertEquals("−US$3.0M", MetricFormatter.money(-3e6, "USD", explicit = true))
    }

    private class Data(val records: Map<String, CompanyRecord>) : ScreenerDataSource {
        val performanceCalls = mutableListOf<PerformancePeriod>()
        override suspend fun catalog() = error("unused")
        override suspend fun search(query: ScreenerQuery) = error("unused")
        override suspend fun compare(symbols: List<String>) = ComparisonResponse(symbols.map { ComparedCompany(records[it], it, if (records[it] == null) "Company data isn't available right now." else null) },
            observations = ComparisonEngine.observations(symbols.mapNotNull { records[it] }), asOf = "2026-10-07T21:15:00Z")
        override suspend fun performance(symbols: List<String>, period: PerformancePeriod): PerformanceComparison {
            performanceCalls += period
            return PerformanceComparison(period, ReturnKind.PRICE_RETURN, listOf("d1", "d2"), symbols.map { PerformanceSeries(it, listOf(100.0, 105.0), 5.0) })
        }
    }

    private val usd = CompanyRecord("AAPL", "Apple Inc.", "NASDAQ", "US", "USD", "Technology", "Consumer Electronics", marketCap = 3.4e12, price = 230.0,
        metrics = mapOf("marketCap" to MetricValue(3.4e12, FinancialAvailability.AVAILABLE, basis("Latest quote", null, "USD")),
            "netMargin" to MetricValue(24.0, FinancialAvailability.AVAILABLE, basis("annual", "2025-09-30")),
            "pe" to MetricValue(27.8, FinancialAvailability.AVAILABLE, basis("TTM", null)),
            "quarterRevenueGrowth" to MetricValue(8.0, FinancialAvailability.AVAILABLE, basis("quarter", "2026-06-27"), "Q3 FY2026 vs Q3 FY2025"),
            "debtEquity" to MetricValue(1.34, FinancialAvailability.AVAILABLE, basis("annual", "2025-09-30"))))
    private val cad = CompanyRecord("RY.TO", "Royal Bank of Canada", "TSX", "CA", "CAD", "Financial Services", "Banks", marketCap = 245e9, price = 180.0,
        metrics = mapOf("marketCap" to MetricValue(181e9, FinancialAvailability.AVAILABLE, basis("Latest quote", null, "USD")),
            "netMargin" to MetricValue(26.1, FinancialAvailability.AVAILABLE, basis("annual", "2025-10-31")),
            "pe" to MetricValue(null, FinancialAvailability.NON_POSITIVE_DENOMINATOR, basis("TTM", null)),
            "quarterRevenueGrowth" to MetricValue(null, FinancialAvailability.MISSING, note = "No published quarterly results are available for this company."),
            "debtEquity" to MetricValue(null, FinancialAvailability.NON_POSITIVE_DENOMINATOR)))

    @Test fun presenterShowsBeginnerGroupsPeriodsCurrenciesAndOneRequestPerChartPeriod() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = Data(mapOf("AAPL" to usd, "RY.TO" to cad))
            val selection = ComparisonSelection()
            val presenter = ComparisonPresenter(data, selection, scope)
            assertEquals(3, presenter.state.value.examples.size)
            selection.add("AAPL", "Apple Inc."); selection.add("RY.TO", "Royal Bank of Canada")
            val state = withTimeout(5_000) { presenter.state.first { it.columns.size == 2 && !it.loading && !it.chartLoading && it.chart != null } }
            assertEquals(listOf("Overview", "Growth", "Profitability", "Financial Health", "Valuation", "Shareholder Returns"), state.sections.filterNot { it.advanced }.map { it.title })
            assertTrue(state.advancedCount > 10)
            fun row(id: String) = state.sections.flatMap { it.rows }.first { it.id == id }
            // Market cap: each listing's own currency, explicit markers, converted USD as a labelled detail.
            assertEquals(listOf("US$3.40T", "C$245.0B"), row("marketCap").cells.map { it.text })
            assertEquals("≈ US$181.0B converted", row("marketCap").cells[1].detail)
            // Periods differ (Sep vs Oct fiscal year ends): each value says which period it covers.
            assertEquals("Periods differ by company", row("netMargin").period)
            assertEquals(listOf("FY ended Sep 2025", "FY ended Oct 2025"), row("netMargin").cells.map { it.detail })
            assertEquals("Q3 FY2026 vs Q3 FY2025", row("quarterRevenueGrowth").cells[0].detail)
            assertTrue(row("quarterRevenueGrowth").cells[1].explanation!!.contains("No published quarterly results"))
            assertTrue(row("pe").cells[1].explanation!!.contains("loss"))
            assertTrue(row("debtEquity").cells[1].explanation!!.contains("equity"))
            assertEquals("TSX · CAD", state.columns[1].listing)
            // The 1Y history is fetched once and shared by the chart and the price-change row.
            assertEquals(1, data.performanceCalls.count { it == PerformancePeriod.ONE_YEAR })
            assertEquals(1, data.performanceCalls.count { it == PerformancePeriod.THREE_YEARS })
            // Replace keeps the comparison going; examples replace the selection.
            presenter.replace("RY.TO", "KO", "Coca-Cola")
            assertEquals(listOf("AAPL", "KO"), selection.selected.value.map { it.symbol })
            presenter.replace("KO", "AAPL", "Apple Inc.")
            assertTrue(withTimeout(5_000) { presenter.state.first { it.message != null } }.message!!.contains("already"))
            presenter.useExample(1)
            assertEquals(listOf("RY.TO", "TD"), selection.selected.value.map { it.symbol })
            selection.set(listOf(SelectedCompany("A", "A"), SelectedCompany("B", "B"), SelectedCompany("C", "C"), SelectedCompany("D", "D")))
            assertNotNull(withTimeout(5_000) { presenter.state.first { it.selected.size == 4 } }.crowdedHint)
            presenter.add("E", "E")
            assertTrue(withTimeout(5_000) { presenter.state.first { it.message?.contains("up to 4") == true } }.message!!.contains("Remove or replace"))
        } finally { scope.cancel() }
    }

    @Test fun observationsNeverRankAndSkipIncomparablePeriods() {
        val other = usd.copy(symbol = "MSFT", name = "Microsoft", metrics = usd.metrics + ("quarterRevenueGrowth" to MetricValue(15.0, FinancialAvailability.AVAILABLE, basis("quarter", "2026-06-30"))))
        val o = ComparisonEngine.observations(listOf(usd, other))
        val q = o.first { it.metric == "quarterRevenueGrowth" }
        assertEquals("Microsoft has faster revenue growth in its latest quarter than Apple Inc. (15.0% vs 8.0%).", q.text)
        assertTrue(q.caveat!!.contains("different months"))
        assertTrue(o.none { Regex("(?i)\\b(better|best|winner|buy|sell|undervalued)\\b").containsMatchIn(it.text) })
        // TTM vs annual for the same metric isn't compared.
        val ttm = other.copy(metrics = other.metrics + ("netMargin" to MetricValue(30.0, FinancialAvailability.AVAILABLE, basis("TTM", null))))
        assertTrue(ComparisonEngine.observations(listOf(usd, ttm)).none { it.metric == "netMargin" })
    }
}
