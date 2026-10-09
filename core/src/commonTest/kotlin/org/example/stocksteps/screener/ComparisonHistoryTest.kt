package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialPeriodStatement
import kotlin.test.*

/** Company Comparison Phase 3: periods, formulas, alignment, tiers and observations of the history engine. */
class ComparisonHistoryTest {
    private val today = "2026-10-07"
    private fun q(period: String, fy: Int, date: String?, revenue: Double? = 100.0, net: Double? = 10.0, eps: Double? = 1.0, currency: String? = "USD") =
        FinancialPeriodStatement(period, fy, date, currency, revenue = revenue, netIncome = net, epsDiluted = eps)
    private fun fy(fy: Int, revenue: Double? = 100.0, net: Double? = 10.0, eps: Double? = 1.0, currency: String = "USD", month: String = "12-31") =
        q("FY", fy, "$fy-$month", revenue, net, eps, currency)
    private fun input(symbol: String, vararg statements: FinancialPeriodStatement, name: String = "$symbol Corp") = HistoryInput(symbol, name, statements.toList())
    private fun named(symbol: String, name: String, vararg statements: FinancialPeriodStatement) = HistoryInput(symbol, name, statements.toList())
    private fun run(range: HistoryRange, plus: Boolean, vararg inputs: HistoryInput) = HistoricalComparisonEngine.compute(inputs.toList(), range, plus, today)
    private fun HistoricalComparisonEngine.Result.series(metric: HistoryMetric, symbol: String) = metrics.first { it.metric == metric }.series.first { it.symbol == symbol }
    private fun HistoricalComparisonEngine.Result.values(metric: HistoryMetric, symbol: String) = series(metric, symbol).points.map { it.value }

    /** Eight quarters of a calendar-year company, newest first, revenue growing 10/quarter from 100. */
    private val calendarQuarters = listOf(
        q("Q2", 2026, "2026-06-30", 170.0), q("Q1", 2026, "2026-03-31", 160.0), q("Q4", 2025, "2025-12-31", 150.0), q("Q3", 2025, "2025-09-30", 140.0),
        q("Q2", 2025, "2025-06-30", 130.0), q("Q1", 2025, "2025-03-31", 120.0), q("Q4", 2024, "2024-12-31", 110.0), q("Q3", 2024, "2024-09-30", 100.0))

    private val ranking = Regex("(?i)\\b(better|best|worst|winner|buy|sell|cheap|expensive|outperform|recommend|will rise|will fall|because)\\b")

    // ---------- Periods ----------

    @Test fun freeViewIsTheLatestFourCompletedQuartersOldestFirst() {
        val future = q("Q3", 2026, "2026-12-31", 999.0)                                                  // not completed yet
        val r = run(HistoryRange.ONE_YEAR, false, input("AAA", future, *calendarQuarters.toTypedArray(), fy(2025)))
        val revenue = r.series(HistoryMetric.REVENUE, "AAA")
        assertEquals(listOf("Q3 FY2025", "Q4 FY2025", "Q1 FY2026", "Q2 FY2026"), revenue.points.map { it.label })
        assertEquals(listOf(140.0, 150.0, 160.0, 170.0), revenue.points.map { it.value })
        assertTrue(revenue.points.none { it.fiscalPeriod == "FY" })                                      // annual rows never mixed in
        assertEquals(HistoryGranularity.QUARTERLY, HistoryRange.ONE_YEAR.granularity)
        assertEquals(HistoryGranularity.ANNUAL, HistoryRange.FIVE_YEARS.granularity)
        assertEquals("2026-06-30", revenue.points.last().periodEnd)
    }

    @Test fun missingPeriodsStayEmptyAndAreNeverInterpolated() {
        val gap = calendarQuarters.filterNot { it.period == "Q4" && it.fiscalYear == 2025 }
        val r = run(HistoryRange.ONE_YEAR, false, input("AAA", *gap.toTypedArray()))
        val points = r.series(HistoryMetric.REVENUE, "AAA").points
        assertEquals("Q4 FY2025", points[1].label)
        assertNull(points[1].value)
        assertEquals(FinancialAvailability.MISSING, points[1].availability)
        assertTrue(points[1].note!!.contains("no quarterly report"))
        assertTrue(r.metrics.first { it.metric == HistoryMetric.REVENUE }.insights.any { it.contains("1 of 4 periods has no value here; nothing is filled in") })
    }

    @Test fun annualThreeAndFiveYearWindows() {
        val years = (2020..2025).map { fy(it, revenue = 100.0 + (it - 2020) * 20) }.reversed()
        assertEquals(listOf("FY2023", "FY2024", "FY2025"), run(HistoryRange.THREE_YEARS, true, input("AAA", *years.toTypedArray())).series(HistoryMetric.REVENUE, "AAA").points.map { it.label })
        assertEquals(listOf(120.0, 140.0, 160.0, 180.0, 200.0), run(HistoryRange.FIVE_YEARS, true, input("AAA", *years.toTypedArray())).values(HistoryMetric.REVENUE, "AAA"))
        // Only four years reported: the fifth slot is explicit, not invented.
        val short = run(HistoryRange.FIVE_YEARS, true, input("BBB", *years.take(4).toTypedArray())).series(HistoryMetric.REVENUE, "BBB").points
        assertEquals("FY2021", short.first().label); assertNull(short.first().value)
    }

    // ---------- Formulas ----------

    @Test fun revenueGrowthComparesTheSameFiscalQuarterAYearEarlier() {
        val r = run(HistoryRange.ONE_YEAR, true, input("AAA", *calendarQuarters.toTypedArray()))
        // Q2 FY2026 (170) vs Q2 FY2025 (130) — not vs the previous quarter (160).
        val growth = r.values(HistoryMetric.REVENUE_GROWTH, "AAA")
        assertEquals((170.0 - 130.0) / 130.0 * 100, growth.last()!!, 1e-9)
        assertEquals((140.0 - 100.0) / 100.0 * 100, growth.first()!!, 1e-9)
        val annual = run(HistoryRange.THREE_YEARS, true, input("BBB", fy(2025, 121.0), fy(2024, 110.0), fy(2023, 100.0), fy(2022, 80.0)))
        assertEquals(listOf(25.0, 10.0, 10.0), annual.values(HistoryMetric.REVENUE_GROWTH, "BBB").map { kotlin.math.round(it!! * 1e6) / 1e6 })
    }

    @Test fun growthIsWithheldForZeroNegativeMissingOrDifferentCurrencyBaselines() {
        val quarters = listOf(q("Q4", 2026, "2026-06-30", 50.0), q("Q3", 2026, "2026-03-31", 40.0), q("Q2", 2026, "2025-12-31", 30.0), q("Q1", 2026, "2025-09-30", 20.0),
            q("Q4", 2025, "2025-06-30", 0.0), q("Q3", 2025, "2025-03-31", -5.0), q("Q2", 2025, "2024-12-31", 25.0, currency = "CAD"))     // Q1 FY2025 absent
        val growth = run(HistoryRange.ONE_YEAR, true, input("AAA", *quarters.toTypedArray())).series(HistoryMetric.REVENUE_GROWTH, "AAA").points
        assertEquals(listOf(FinancialAvailability.INSUFFICIENT_HISTORY, FinancialAvailability.PERIOD_MISMATCH, FinancialAvailability.NON_POSITIVE_DENOMINATOR, FinancialAvailability.NON_POSITIVE_DENOMINATOR),
            growth.map { it.availability })
        assertTrue(growth.all { it.value == null })
        assertTrue(growth[1].note!!.contains("CAD in Q2 FY2025, USD in Q2 FY2026"))
    }

    @Test fun netMarginHandlesLossesAndZeroRevenue() {
        val r = run(HistoryRange.THREE_YEARS, true, input("AAA", fy(2025, 200.0, -30.0), fy(2024, 0.0, -10.0), fy(2023, 100.0, null)))
        val margin = r.series(HistoryMetric.NET_MARGIN, "AAA").points
        assertEquals(FinancialAvailability.MISSING, margin[0].availability)                              // net income not reported
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, margin[1].availability)             // zero revenue
        assertEquals(-15.0, margin[2].value!!, 1e-9)                                                    // a loss is a negative margin
    }

    @Test fun epsGrowthNeedsAPositiveBaselineAndDescribesTurnaroundsInWords() {
        val r = run(HistoryRange.THREE_YEARS, true, input("AAA", fy(2025, eps = 0.20), fy(2024, eps = -0.50), fy(2023, eps = -0.40), fy(2022, eps = 0.80)))
        val growth = r.series(HistoryMetric.EPS_GROWTH, "AAA").points
        assertEquals(-150.0, growth[0].value!!, 1e-9)                                                  // FY2023 vs a positive FY2022: a real percentage
        assertTrue(growth.drop(1).all { it.value == null && it.availability == FinancialAvailability.UNRELIABLE_COMPARISON })
        assertTrue(growth[2].note!!.contains("from −0.50 in FY2024 to 0.20") && growth[2].note!!.contains("from a loss to a profit"))
        assertTrue(growth[1].note!!.contains("a larger loss"))
        val positive = run(HistoryRange.THREE_YEARS, true, input("BBB", fy(2025, eps = 1.5), fy(2024, eps = 1.2), fy(2023, eps = 1.0), fy(2022, eps = 0.8)))
        assertEquals(25.0, positive.values(HistoryMetric.EPS_GROWTH, "BBB").last()!!, 1e-9)
    }

    @Test fun missingDilutedEpsIsNeverReplaced() {
        val r = run(HistoryRange.THREE_YEARS, false, input("AAA", fy(2025, eps = null), fy(2024, eps = 2.0), fy(2023, eps = 1.0)))
        val eps = r.series(HistoryMetric.EPS_DILUTED, "AAA").points
        assertNull(eps.last().value)
        assertTrue(eps.last().note!!.contains("Diluted EPS wasn't reported"))
    }

    @Test fun revenueIndexUsesAPositiveBaseAndNeverCrossesCurrencies() {
        val r = run(HistoryRange.THREE_YEARS, true, input("AAA", fy(2025, 150.0), fy(2024, 120.0), fy(2023, 100.0)),
            input("BBB", fy(2025, 300.0, currency = "CAD"), fy(2024, 200.0, currency = "USD"), fy(2023, 250.0, currency = "USD")),
            input("CCC", fy(2025, 10.0), fy(2024, 5.0), fy(2023, 0.0)))
        assertEquals(listOf(100.0, 120.0, 150.0), r.values(HistoryMetric.REVENUE_INDEX, "AAA"))
        assertEquals("FY2023 = 100", r.series(HistoryMetric.REVENUE_INDEX, "AAA").base)
        assertEquals(FinancialAvailability.PERIOD_MISMATCH, r.series(HistoryMetric.REVENUE_INDEX, "BBB").points.last().availability)
        assertTrue(r.series(HistoryMetric.REVENUE_INDEX, "CCC").points.all { it.value == null })     // zero base: no index
        assertTrue(r.metrics.first { it.metric == HistoryMetric.REVENUE_INDEX }.insights.any { it.contains("not company size or investment return") })
    }

    // ---------- Tiers ----------

    @Test fun freeCallersNeverGetPremiumMetricsOrInsights() {
        val free = run(HistoryRange.ONE_YEAR, false, input("AAA", *calendarQuarters.toTypedArray()), input("BBB", *calendarQuarters.toTypedArray()))
        assertEquals(listOf(HistoryMetric.REVENUE, HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED), free.metrics.map { it.metric })
        assertTrue(free.metrics.flatMap { it.insights }.none { it.contains("changed by") || it.contains("index") })
        val plus = run(HistoryRange.ONE_YEAR, true, input("AAA", *calendarQuarters.toTypedArray()))
        assertEquals(HistoryMetric.entries, plus.metrics.map { it.metric })
    }

    // ---------- Alignment and currencies ----------

    @Test fun periodAlignmentIsDisclosedNeverShifted() {
        fun ends(vararg dates: String) = run(HistoryRange.THREE_YEARS, false,
            *dates.mapIndexed { i, month -> input("C$i", fy(2025, month = month), fy(2024, month = month), fy(2023, month = month)) }.toTypedArray())
        assertEquals(PeriodAlignment.ALIGNED, ends("12-31", "12-28").periods.last().alignment)
        assertEquals(PeriodAlignment.CLOSE, ends("12-31", "10-31").periods.last().alignment)
        val apart = ends("12-31", "06-30")
        assertEquals(PeriodAlignment.DIFFERENT, apart.periods.last().alignment)
        assertEquals(listOf("2025-12-31", "2025-06-30"), apart.periods.last().ends)                       // each company's own dates
        assertTrue(apart.insights.any { it.contains("end in different months (C0 Corp: Dec; C1 Corp: Jun)") })
        val unknown = run(HistoryRange.THREE_YEARS, false, input("AAA", q("FY", 2025, null), q("FY", 2024, null), q("FY", 2023, null)), input("BBB", fy(2025), fy(2024), fy(2023)))
        assertEquals(PeriodAlignment.UNKNOWN, unknown.periods.last().alignment)
    }

    @Test fun currenciesArePreservedAndNeverComparedAsAmounts() {
        val r = run(HistoryRange.THREE_YEARS, false, input("RY.TO", fy(2025, 300.0, currency = "CAD"), fy(2024, 250.0, currency = "CAD"), fy(2023, 200.0, currency = "CAD")),
            input("AAA", fy(2025, 300.0), fy(2024, 250.0), fy(2023, 200.0)))
        assertEquals("CAD", r.series(HistoryMetric.REVENUE, "RY.TO").currency)
        assertEquals("CAD", r.series(HistoryMetric.REVENUE, "RY.TO").points.last().currency)
        assertTrue(r.insights.any { it.contains("each company's reporting currency (CAD and USD) and aren't converted") })
        assertTrue(r.metrics.first { it.metric == HistoryMetric.REVENUE }.insights.first().contains("C$300"))
        val switched = run(HistoryRange.THREE_YEARS, false, input("AAA", fy(2025, 300.0, currency = "CAD"), fy(2024, 250.0), fy(2023, 200.0)), input("BBB", fy(2025), fy(2024), fy(2023)))
        assertTrue(switched.metrics.first { it.metric == HistoryMetric.REVENUE }.insights.any { it.contains("more than one currency") })
        assertTrue(switched.metrics.first { it.metric == HistoryMetric.REVENUE }.insights.none { it.startsWith("AAA Corp's revenue increased") })
    }

    // ---------- Observations ----------

    @Test fun observationsAreDerivedFromTheValuesAndNeverRankOrExplainWhy() {
        val r = run(HistoryRange.FIVE_YEARS, true,
            named("AAA", "Alpha", *(2021..2025).map { fy(it, 100.0 + (it - 2021) * 10, 10.0 + it - 2021) }.reversed().toTypedArray()),
            named("BBB", "Beta", fy(2025, 90.0, -5.0, -0.1), fy(2024, 95.0, 4.0, 0.2), fy(2023, 100.0, -3.0, -0.1), fy(2022, 105.0, 2.0, 0.1), fy(2021, 110.0, 1.0, 0.05)))
        val revenue = r.metrics.first { it.metric == HistoryMetric.REVENUE }.insights
        assertTrue("Alpha's revenue increased in each displayed fiscal year ($100 in FY2021 to $140 in FY2025)." in revenue, revenue.toString())
        assertTrue("Beta's revenue decreased in each displayed fiscal year ($110 in FY2021 to $90 in FY2025)." in revenue)
        assertTrue("Alpha's revenue changed by +40.0% from FY2021 to FY2025." in revenue)                    // premium detail, annual only
        val income = r.metrics.first { it.metric == HistoryMetric.NET_INCOME }.insights
        assertTrue("Beta reported negative net income in 2 of the 5 displayed fiscal years with figures." in income)
        assertTrue("Beta's net income changed from positive in FY2024 to negative in FY2025." in income || income.any { it.startsWith("Beta's net income changed from positive in FY2022 to negative in FY2023") })
        val all = r.insights + r.metrics.flatMap { it.insights }
        assertTrue(all.none { ranking.containsMatchIn(it) }, all.firstOrNull { ranking.containsMatchIn(it) })
        // Quarterly: no "changed by" percentages across seasonal quarters, and a seasonality reminder.
        val quarterly = run(HistoryRange.ONE_YEAR, true, input("AAA", *calendarQuarters.toTypedArray()))
        assertTrue(quarterly.metrics.flatMap { it.insights }.none { it.contains("changed by") })
        assertTrue(quarterly.insights.any { it.contains("seasonal") })
        assertEquals(r, run(HistoryRange.FIVE_YEARS, true, *listOf(
            named("AAA", "Alpha", *(2021..2025).map { fy(it, 100.0 + (it - 2021) * 10, 10.0 + it - 2021) }.reversed().toTypedArray()),
            named("BBB", "Beta", fy(2025, 90.0, -5.0, -0.1), fy(2024, 95.0, 4.0, 0.2), fy(2023, 100.0, -3.0, -0.1), fy(2022, 105.0, 2.0, 0.1), fy(2021, 110.0, 1.0, 0.05))).toTypedArray()))
    }

    @Test fun failedAndEmptyCompaniesAreReportedNotHidden() {
        val r = HistoricalComparisonEngine.compute(listOf(input("AAA", *calendarQuarters.toTypedArray()), HistoryInput("BBB", "Beta", null, "financial history isn't available right now"),
            input("CCC", fy(2025))), HistoryRange.ONE_YEAR, false, today)
        assertEquals("financial history isn't available right now", r.companies[1].error)
        assertTrue(r.insights.any { it.startsWith("Beta: financial history isn't available right now") })
        assertTrue(r.insights.any { it.startsWith("CCC Corp has no reported quarterly figures") })
        assertTrue(r.series(HistoryMetric.REVENUE, "BBB").points.isEmpty())
        assertEquals(PeriodAlignment.UNKNOWN, r.periods.last().alignment)                                // only one company has data
    }

    @Test fun formattingKeepsSignsUnitsAndCurrencies() {
        assertEquals("−$0.04", HistoricalComparisonEngine.format(HistoryMetric.EPS_DILUTED, -0.04, "USD"))
        assertEquals("C$2.22", HistoricalComparisonEngine.format(HistoryMetric.EPS_DILUTED, 2.22, "CAD"))
        assertEquals("−US$4.1B", HistoricalComparisonEngine.format(HistoryMetric.NET_INCOME, -4.08e9, "USD", explicit = true))
        assertEquals("+12.5%", HistoricalComparisonEngine.format(HistoryMetric.REVENUE_GROWTH, 12.5, null))
        assertEquals("−4.9%", HistoricalComparisonEngine.format(HistoryMetric.NET_MARGIN, -4.92, null))
        assertEquals("121.7", HistoricalComparisonEngine.format(HistoryMetric.REVENUE_INDEX, 121.74, null))
        assertEquals(HistoryRange.THREE_YEARS, HistoryRange.parse(" 3y "))
        assertNull(HistoryRange.parse("10Y"))
    }
}

/** Phase 3 presenter: ranges, locked previews, metric switching, plan changes, selection changes, retry. */
class ComparisonHistoryPresenterTest {
    private class NoData : ScreenerDataSource {
        override suspend fun catalog() = error("unused")
        override suspend fun search(query: ScreenerQuery) = error("unused")
        override suspend fun compare(symbols: List<String>) = ComparisonResponse(symbols.map { ComparedCompany(null, it, "n/a") }, asOf = "2026-10-07T21:15:00Z")
        override suspend fun performance(symbols: List<String>, period: PerformancePeriod) = PerformanceComparison(period, ReturnKind.PRICE_RETURN)
    }

    /** Fake server: the plan comes from [account] (as the real server reads it from the stored entitlement). */
    private class Server(val account: kotlinx.coroutines.flow.MutableStateFlow<String?>) : ComparisonHistorySource {
        val calls = mutableListOf<Pair<List<String>, HistoryRange>>()
        var failNext = false
        override suspend fun history(symbols: List<String>, range: HistoryRange): HistoricalComparison {
            calls += symbols to range
            if (failNext) { failNext = false; throw IllegalStateException("offline") }
            val plus = account.value?.contains("PLUS") == true
            if (range.premium && !plus) throw org.example.stocksteps.network.StockStepsApiException(403, org.example.stocksteps.model.ApiError("PLUS_REQUIRED", "Part of StockSteps+."))
            val statements = (2019..2025).map { y -> FinancialPeriodStatement("FY", y, "$y-12-31", "USD", revenue = 100.0 + y - 2019, netIncome = if (y == 2025) null else 5.0, epsDiluted = 1.0) } +
                (1..4).flatMap { q -> listOf(2024, 2025).map { y -> FinancialPeriodStatement("Q$q", y, "$y-${(q * 3).toString().padStart(2, '0')}-28", "USD", revenue = 10.0 * q, netIncome = 1.0, epsDiluted = 0.1) } }
            val r = HistoricalComparisonEngine.compute(symbols.map { HistoryInput(it, "$it Inc", statements) }, range, plus, "2026-10-07")
            return HistoricalComparison(range, range.granularity, r.companies, r.metrics, r.periods, r.insights, emptyList(),
                if (plus) HistoryAccess(true, true, HistoryRange.entries, HistoryMetric.entries) else HistoryAccess(false, account.value != null), "test", "2026-10-07T21:15:00Z")
        }
    }

    private fun test(account: String?, block: suspend CoroutineScope.(Server, ComparisonSelection, ComparisonPresenter) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val flow = kotlinx.coroutines.flow.MutableStateFlow(account)
            val server = Server(flow)
            val selection = ComparisonSelection()
            val presenter = ComparisonPresenter(NoData(), selection, scope, server, flow)
            selection.add("AAA", "AAA Inc"); selection.add("BBB", "BBB Inc")
            block(server, selection, presenter)
        } finally { scope.cancel() }
    }
    private suspend fun ComparisonPresenter.until(predicate: (ComparisonUiState) -> Boolean) = withTimeout(5_000) { state.first(predicate) }

    @Test fun freeUsersKeepOneYearAndSeeAPreviewInsteadOfARequest(): Unit = test("free|FREE") { server, _, presenter ->
        val loaded = presenter.until { it.history != null && !it.historyLoading }
        assertTrue(loaded.historyEnabled && !loaded.historyPlus)
        assertEquals(HistoryRange.ONE_YEAR, loaded.historyRange)
        assertTrue(loaded.historyLocked(HistoryRange.THREE_YEARS) && loaded.historyLocked(HistoryMetric.NET_MARGIN) && !loaded.historyLocked(HistoryMetric.EPS_DILUTED))
        val calls = server.calls.size
        presenter.selectHistoryRange(HistoryRange.FIVE_YEARS)
        presenter.state.value.let { assertEquals("See 5Y history with StockSteps+", it.historyUpsell!!.title); assertEquals(HistoryRange.ONE_YEAR, it.historyRange); assertFalse(it.historyUpsell!!.signIn) }
        presenter.dismissHistoryUpsell()
        presenter.selectHistoryMetric(HistoryMetric.REVENUE_INDEX)
        assertNotNull(presenter.state.value.historyUpsell)
        presenter.dismissHistoryUpsell()
        presenter.selectHistoryMetric(HistoryMetric.NET_INCOME)                                               // free metric: no request
        assertEquals(HistoryMetric.NET_INCOME, presenter.state.value.historyMetric)
        assertEquals(calls, server.calls.size)                                                                // no protected request was made
        val view = presenter.state.value.historyView()!!
        assertEquals(listOf("AAA", "BBB"), view.symbols)
        assertEquals("Latest", view.rows.first().title)
        assertEquals(4, view.series.first().values.size)
    }

    @Test fun guestsAreAskedToSignIn(): Unit = test(null) { _, _, presenter ->
        presenter.until { it.history != null }
        presenter.selectHistoryRange(HistoryRange.THREE_YEARS)
        assertTrue(presenter.state.value.historyUpsell!!.signIn)
    }

    @Test fun plusRangesMetricsCacheAndPlanExpiry(): Unit = test("plus|PLUS") { server, _, presenter ->
        presenter.until { it.historyPlus }
        presenter.selectHistoryRange(HistoryRange.FIVE_YEARS)
        val five = presenter.until { it.history?.range == HistoryRange.FIVE_YEARS && !it.historyLoading }
        assertEquals(5, five.history!!.periods.size)
        presenter.selectHistoryMetric(HistoryMetric.NET_MARGIN)
        val margin = presenter.state.value.historyView()!!
        assertEquals("N/A", margin.rows.first().cells.first().text)                                          // FY2025 net income missing → no margin
        assertTrue(margin.rows.first().cells.first().explanation!!.contains("wasn't reported"))
        val calls = server.calls.size
        presenter.selectHistoryRange(HistoryRange.ONE_YEAR)
        presenter.until { it.history?.range == HistoryRange.ONE_YEAR }
        presenter.selectHistoryRange(HistoryRange.FIVE_YEARS)
        presenter.until { it.history?.range == HistoryRange.FIVE_YEARS }
        assertEquals(calls, server.calls.size)                                                               // both ranges came from the cache
        // The plan ends while 5Y is shown: refetch, the server refuses, back to the free view with the preview.
        val flow = (server.account)
        flow.value = "plus|FREE|EXPIRED"
        val downgraded = presenter.until { it.historyUpsell != null && it.historyRange == HistoryRange.ONE_YEAR && it.history?.range == HistoryRange.ONE_YEAR && !it.historyLoading }
        assertFalse(downgraded.historyPlus)
        assertEquals(HistoryMetric.REVENUE, downgraded.historyMetric)                                        // premium metric dropped
    }

    @Test fun changingCompaniesNeverShowsTheOldSelectionAndRetryRecovers(): Unit = test("free|FREE") { server, selection, presenter ->
        presenter.until { it.history?.companies?.map { c -> c.symbol } == listOf("AAA", "BBB") }
        selection.replace("BBB", "CCC", "CCC Inc")
        val next = presenter.until { s -> s.history == null || s.history.companies.map { it.symbol } == listOf("AAA", "CCC") }
        assertTrue(next.history == null || next.history.companies.none { it.symbol == "BBB" })
        presenter.until { it.history?.companies?.map { c -> c.symbol } == listOf("AAA", "CCC") }
        server.failNext = true
        presenter.retryHistory()
        presenter.until { it.historyError != null }
        presenter.retryHistory()
        presenter.until { it.historyError == null && it.history != null && !it.historyLoading }
        selection.remove("CCC")
        presenter.until { it.history == null }
    }
}
