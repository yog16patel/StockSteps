package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import kotlin.test.*

/** Company Comparison Phase 2: the deterministic guided-interpretation engine and presenter state. */
class ComparisonInterpretationTest {
    private val available = FinancialAvailability.AVAILABLE
    private fun v(value: Double?, period: String = "TTM", date: String? = null, availability: FinancialAvailability = available, note: String? = null, currency: String? = null) =
        MetricValue(value, if (value == null && availability == available) FinancialAvailability.MISSING else availability, FinancialBasis(period, date, currency = currency), note)

    private fun record(symbol: String, name: String = "$symbol Corp", sector: String? = "Technology", industry: String? = "Software - Application", currency: String = "USD",
                       marketCap: Double? = 1e11, usdCap: Double? = marketCap, stale: Boolean = false, vararg metrics: Pair<String, MetricValue>) =
        CompanyRecord(symbol, name, "NASDAQ", "US", currency, sector, industry, marketCap = marketCap, stale = stale,
            metrics = mapOf("marketCap" to MetricValue(usdCap, if (usdCap == null) FinancialAvailability.MISSING else available, FinancialBasis("Latest quote", currency = "USD"))) + metrics)

    private fun interpret(vararg records: CompanyRecord, fx: List<FxConversion> = emptyList()) =
        ComparisonInterpretationEngine.interpret(records.map { ComparedCompany(it, it.symbol) }, fx)

    private val ranking = Regex("(?i)\\b(better|best|worst|winner|wins|buy|sell|cheap|expensive|bargain|overvalued|undervalued|outperform|recommend|safe|unsafe|will rise|will fall)\\b")

    private fun texts(i: ComparisonInterpretation) = i.summary.map { it.text } + i.metrics.values.flatMap { listOfNotNull(it.observation, it.headline) }

    // ---------- Definitions ----------

    @Test fun everySupportedMetricHasAllFiveSections() {
        val i = interpret(record("AAA"), record("BBB"))
        assertEquals(ComparisonInterpretationEngine.GUIDED.toSet(), i.metrics.keys)
        for (m in i.metrics.values) {
            assertTrue(m.title.startsWith("Understanding "), m.metricId)
            assertTrue(m.definition.isNotBlank() && m.whyItMatters.isNotBlank() && m.observation.isNotBlank(), m.metricId)
            assertTrue(m.caveats.isNotEmpty() && m.related.isNotEmpty() && m.questions.isNotEmpty(), m.metricId)
            assertTrue(m.related.none { it.id == m.metricId }, "${m.metricId} links to itself")
            assertTrue(m.keyCaveats.size <= 2)
        }
        assertTrue(i.metrics.getValue("pe").definition.contains("earnings per share"))
        assertTrue(i.metrics.getValue("marketCap").definition.contains("share price × shares outstanding"))
        assertTrue(i.metrics.getValue("debtEquity").related.any { it.id == "interestCoverage" && !it.guided })
        assertTrue(i.metrics.getValue("pe").related.any { it.id == "revenueGrowth" && it.guided })
        assertEquals("Revenue growth — fiscal year", i.metrics.getValue("revenueGrowth").label)                     // same label as the table row
    }

    // ---------- Relative comparisons ----------

    @Test fun twoCompanyRelativeComparisonUsesActualValuesAndQualifies() {
        val i = interpret(record("AAA", "Alpha Inc.", metrics = arrayOf("pe" to v(32.4))), record("BBB", "Beta Co", metrics = arrayOf("pe" to v(18.0))))
        val pe = i.metrics.getValue("pe")
        assertEquals(Comparability.COMPARABLE, pe.comparability)
        assertEquals("Alpha Inc. has a higher trailing P/E than Beta Co (32.4× vs 18.0×).", pe.observation)       // "Inc.." is tidied
        assertTrue(pe.meaning!!.contains("pay more per dollar of reported earnings for Alpha Inc."))
        assertTrue(pe.meaning!!.contains("doesn't make Beta Co cheaper"))
        assertEquals("Beta Co has a lower trailing P/E than Alpha Inc. (18.0× vs 32.4×), but differences in earnings, growth expectations and business risk may affect the comparison.", pe.headline)
        assertTrue(pe.caveats.any { it.contains("isn't proof that a stock is undervalued") })
        assertTrue(texts(i).none { ranking.containsMatchIn(it) }, texts(i).firstOrNull { ranking.containsMatchIn(it) })
    }

    @Test fun noUniversalThresholdsOnlyRelativeStatements() {
        fun sentence(a: Double, b: Double) = interpret(record("AAA", metrics = arrayOf("pe" to v(a), "debtEquity" to v(a / 10), "revenueGrowth" to v(a, "annual", "2025-12-31"))),
            record("BBB", metrics = arrayOf("pe" to v(b), "debtEquity" to v(b / 10), "revenueGrowth" to v(b, "annual", "2025-12-31"))))
            .metrics.filterKeys { it in setOf("pe", "debtEquity", "revenueGrowth") }.mapValues { it.value.observation.replace(Regex("[−+]?[0-9]+\\.[0-9]+[×%]?"), "#") }
        // A P/E of 8 vs 12 is described exactly like 80 vs 120: no "cheap"/"expensive" levels, no "too much debt" levels.
        assertEquals(sentence(8.0, 12.0), sentence(80.0, 120.0))
        assertEquals(sentence(12.0, 30.0), sentence(30.0, 75.0))
    }

    @Test fun equalAndSlightlyDifferentValuesAreNotOverstated() {
        val equal = interpret(record("AAA", metrics = arrayOf("netMargin" to v(20.04))), record("BBB", metrics = arrayOf("netMargin" to v(19.96)))).metrics.getValue("netMargin")
        assertTrue(equal.observation.contains("the same net profit margin as displayed (20.0%)"), equal.observation)
        assertNull(equal.headline)
        val close = interpret(record("AAA", metrics = arrayOf("netMargin" to v(20.0))), record("BBB", metrics = arrayOf("netMargin" to v(20.3)))).metrics.getValue("netMargin")
        assertTrue(close.observation.contains("have a similar net profit margin"), close.observation)
        assertTrue(close.meaning!!.contains("says little on its own"))
        assertNull(close.headline)
        // Small in points but large in relative terms isn't "similar".
        val yields = interpret(record("AAA", metrics = arrayOf("dividendYield" to v(0.7))), record("BBB", metrics = arrayOf("dividendYield" to v(0.3)))).metrics.getValue("dividendYield")
        assertTrue(yields.observation.contains("has a higher dividend yield than"), yields.observation)
        val ratios = interpret(record("AAA", metrics = arrayOf("priceSales" to v(10.0))), record("BBB", metrics = arrayOf("priceSales" to v(10.4)))).metrics.getValue("priceSales")
        assertTrue(ratios.observation.contains("similar"))
    }

    @Test fun threeAndFourCompaniesShowARangeInSelectionOrderNeverARanking() {
        val i = interpret(record("AAA", "Alpha", metrics = arrayOf("netMargin" to v(12.0))), record("BBB", "Beta", metrics = arrayOf("netMargin" to v(30.0))),
            record("CCC", "Gamma", metrics = arrayOf("netMargin" to v(5.0))), record("DDD", "Delta", metrics = arrayOf("netMargin" to v(18.0))))
        val m = i.metrics.getValue("netMargin")
        assertEquals("Among these 4 companies, net profit margin ranges from 5.0% (Gamma) to 30.0% (Beta). Values: Alpha 12.0%, Beta 30.0%, Gamma 5.0%, Delta 18.0%.", m.observation)
        assertEquals("Net profit margin ranges from 5.0% (Gamma) to 30.0% (Beta).", m.headline)
        assertTrue(texts(i).none { ranking.containsMatchIn(it) || it.contains("#1") || it.contains("first place") })
    }

    @Test fun growthWordingFollowsTheSignOfEachValue() {
        fun growth(a: Double, b: Double) = interpret(record("AAA", "Alpha", metrics = arrayOf("revenueGrowth" to v(a, "annual", "2025-12-31"))),
            record("BBB", "Beta", metrics = arrayOf("revenueGrowth" to v(b, "annual", "2025-12-31")))).metrics.getValue("revenueGrowth")
        assertTrue(growth(8.0, -3.0).meaning!!.startsWith("Alpha's revenue grew while Beta's declined"))
        assertTrue(growth(-2.0, -9.0).meaning!!.startsWith("Both companies' revenue declined"))
        assertTrue(growth(12.0, 4.0).meaning!!.contains("Faster growth isn't automatically better"))
        assertEquals("Alpha has higher revenue growth than Beta (+12.0% vs +4.0%).", growth(12.0, 4.0).observation)
    }

    // ---------- Missing, not meaningful and incomparable data ----------

    @Test fun negativeEarningsGiveNoOrdinaryPeInterpretation() {
        val i = interpret(record("KO", "Coca-Cola", metrics = arrayOf("pe" to v(28.1))),
            record("RIVN", "Rivian", metrics = arrayOf("pe" to v(null, availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR))))
        val pe = i.metrics.getValue("pe")
        assertEquals(Comparability.INSUFFICIENT_DATA, pe.comparability)
        assertEquals("Only Coca-Cola has a value for trailing P/E (28.1×), so there's nothing to compare it with here.", pe.observation)
        assertNull(pe.headline); assertNull(pe.meaning)
        assertTrue(pe.caveats.first().startsWith("Rivian: Not meaningful") && pe.caveats.first().contains("loss"))
        assertTrue(pe.questions.first().contains("price to sales"))
        assertFalse(pe.observation.contains("Rivian"))                                                          // no value is invented for it
    }

    @Test fun zeroOrNegativeEquityAndMissingValuesAreExplainedNotFilled() {
        val i = interpret(record("AAA", metrics = arrayOf("debtEquity" to v(0.9), "dividendYield" to v(null))),
            record("BBB", metrics = arrayOf("debtEquity" to v(null, availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR), "dividendYield" to v(1.5))))
        val de = i.metrics.getValue("debtEquity")
        assertEquals(Comparability.INSUFFICIENT_DATA, de.comparability)
        assertTrue(de.caveats.first().contains("equity is zero or negative"))
        assertTrue(de.questions.any { it.contains("interest coverage") })
        val dy = i.metrics.getValue("dividendYield")
        assertEquals(Comparability.INSUFFICIENT_DATA, dy.comparability)
        assertTrue(dy.caveats.first().contains("isn't the same as no dividend"))                               // unknown ≠ 0%
        assertTrue(listOf(de.observation, dy.observation).none { it.contains("0.0") })
    }

    @Test fun noDividendIsARealZeroButUnknownHistoryIsNot() {
        val dy = interpret(record("KO", "Coca-Cola", metrics = arrayOf("dividendYield" to v(2.9))),
            record("RIVN", "Rivian", metrics = arrayOf("dividendYield" to v(0.0, availability = FinancialAvailability.NO_DIVIDEND)))).metrics.getValue("dividendYield")
        assertEquals("Coca-Cola (2.9%) paid dividends over the past year; Rivian paid none.", dy.observation)
        assertTrue(dy.meaning!!.contains("doesn't say which"))
        val none = interpret(record("AAA", metrics = arrayOf("dividendYield" to v(0.0, availability = FinancialAvailability.NO_DIVIDEND))),
            record("BBB", metrics = arrayOf("dividendYield" to v(0.0, availability = FinancialAvailability.NO_DIVIDEND)))).metrics.getValue("dividendYield")
        assertTrue(none.observation.startsWith("Neither"))
    }

    @Test fun negativeNetMarginIsALossNotAVerdict() {
        val m = interpret(record("KO", "Coca-Cola", metrics = arrayOf("netMargin" to v(22.6))), record("RIVN", "Rivian", metrics = arrayOf("netMargin" to v(-80.0)))).metrics.getValue("netMargin")
        assertEquals("Coca-Cola has a higher net profit margin than Rivian (22.6% vs −80.0%).", m.observation)
        assertTrue(m.meaning!!.contains("Rivian (−80.0%) reported a net loss for the period. A loss in one period doesn't mean a company will stay unprofitable."))
    }

    @Test fun differentKindsOfPeriodsAreNotComparedAndDifferentMonthsAreQualified() {
        val mixed = interpret(record("AAA", "Alpha", metrics = arrayOf("netMargin" to v(24.0, "annual", "2025-09-30"))),
            record("BBB", "Beta", metrics = arrayOf("netMargin" to v(30.0, "TTM")))).metrics.getValue("netMargin")
        assertEquals(Comparability.NOT_COMPARABLE, mixed.comparability)
        assertTrue(mixed.observation.contains("different kinds of periods") && mixed.observation.contains("FY ended Sep 2025") && mixed.observation.contains("Trailing twelve months"))
        assertNull(mixed.headline)
        val months = interpret(record("AAA", metrics = arrayOf("revenueGrowth" to v(6.0, "annual", "2025-09-27"))),
            record("BBB", metrics = arrayOf("revenueGrowth" to v(15.0, "annual", "2025-06-30")))).metrics.getValue("revenueGrowth")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, months.comparability)
        assertTrue(months.caveats.first().contains("different months"))
        assertTrue(months.headline!!.endsWith("Compare with care (see Explain)."))
        val far = interpret(record("AAA", metrics = arrayOf("revenueGrowth" to v(6.0, "annual", "2025-12-31"))),
            record("BBB", metrics = arrayOf("revenueGrowth" to v(15.0, "annual", "2025-03-31")))).metrics.getValue("revenueGrowth")
        assertTrue(far.caveats.first().contains("clearly different dates"))
        // Missing period metadata is disclosed, not assumed.
        val unknown = interpret(record("AAA", metrics = arrayOf("pe" to v(20.0))), record("BBB", metrics = arrayOf("pe" to MetricValue(25.0, available)))).metrics.getValue("pe")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, unknown.comparability)
        assertTrue(unknown.caveats.first().contains("reporting period for BBB Corp isn't reported"))
    }

    @Test fun staleStatementsAreFlagged() {
        val m = interpret(record("AAA", stale = true, metrics = arrayOf("netMargin" to v(10.0))), record("BBB", metrics = arrayOf("netMargin" to v(20.0)))).metrics.getValue("netMargin")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, m.comparability)
        assertTrue(m.caveats.any { it.contains("more than 18 months old") })
    }

    // ---------- Currencies ----------

    @Test fun marketCapsInDifferentCurrenciesAreConvertedOnlyWithADisclosedRate() {
        val fx = listOf(FxConversion("CAD", "USD", 1 / 1.35, "2026-10-07", "Bank of Canada"))
        val ry = record("RY.TO", "Royal Bank of Canada", "Financial Services", "Banks - Diversified", "CAD", marketCap = 245e9, usdCap = 245e9 / 1.35)
        val td = record("TD", "Toronto-Dominion Bank", "Financial Services", "Banks - Diversified", "USD", marketCap = 105.2e9)
        val cap = interpret(ry, td, fx = fx).metrics.getValue("marketCap")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, cap.comparability)
        assertEquals("Royal Bank of Canada has a larger market capitalization than Toronto-Dominion Bank (C$245.0B ≈ US$181.5B vs US$105.2B).", cap.observation)
        assertTrue(cap.caveats.first().contains("1 CAD = 0.7407 USD (Bank of Canada, 2026-10-07)"), cap.caveats.first())
        // No conversion available: amounts in different currencies are never compared numerically.
        val unconverted = interpret(ry.copy(metrics = ry.metrics - "marketCap"), td).metrics.getValue("marketCap")
        assertEquals(Comparability.NOT_COMPARABLE, unconverted.comparability)
        assertTrue(unconverted.observation.contains("no exchange rate"))
        assertNull(unconverted.meaning)
        // Same currency: compared directly, no conversion mentioned.
        val same = interpret(record("AAA", marketCap = 4e12), record("BBB", marketCap = 1e12)).metrics.getValue("marketCap")
        assertEquals(Comparability.COMPARABLE, same.comparability)
        assertTrue(same.caveats.none { it.contains("converting") })
        assertTrue(same.meaning!!.contains("not necessarily higher sales, stronger profitability or more cash"))
    }

    @Test fun ratiosAcrossCurrenciesAreComparableAndGrowthIsQualified() {
        val i = interpret(record("SHOP.TO", currency = "CAD", metrics = arrayOf("pe" to v(60.0), "revenueGrowth" to v(20.0, "annual", "2025-12-31", currency = "USD"))),
            record("CSU.TO", currency = "CAD", metrics = arrayOf("pe" to v(90.0), "revenueGrowth" to v(18.0, "annual", "2025-12-31", currency = "CAD"))))
        assertEquals(Comparability.COMPARABLE, i.metrics.getValue("pe").comparability)
        val growth = i.metrics.getValue("revenueGrowth")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, growth.comparability)
        assertTrue(growth.caveats.first().contains("different currencies (USD and CAD)"))
    }

    // ---------- Industries ----------

    @Test fun crossIndustryComparisonsAreQualifiedNotBlocked() {
        val i = interpret(record("AAPL", "Apple", industry = "Consumer Electronics", metrics = arrayOf("pe" to v(27.8), "netMargin" to v(24.0), "debtEquity" to v(1.3))),
            record("JPM", "JPMorgan", "Financial Services", "Banks - Diversified", metrics = arrayOf("pe" to v(14.9), "netMargin" to v(33.5), "debtEquity" to v(2.5))))
        assertNotNull(i.industryNote)
        assertEquals(InsightCategory.INDUSTRY, i.summary.first().category)
        assertTrue(i.summary.first().text.contains("different sectors (Technology and Financial Services)"))
        val pe = i.metrics.getValue("pe")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, pe.comparability)                                    // still compared
        assertTrue(pe.observation.contains("has a higher trailing P/E"))
        assertTrue(pe.caveats.any { it.contains("different sectors") } && pe.caveats.any { it.contains("Bank earnings") })
        assertTrue(i.metrics.getValue("netMargin").caveats.any { it.contains("JPMorgan is a bank or insurer") })
        // Debt to equity: the bank's value is left out with a reason, never compared with Apple's.
        val de = i.metrics.getValue("debtEquity")
        assertEquals(Comparability.NOT_COMPARABLE, de.comparability)
        assertTrue(de.caveats.first().startsWith("JPMorgan: Not compared: banks and insurers"))
        assertFalse(de.observation.contains("2.50"))
    }

    @Test fun sameIndustryMissingSectorAndReitContext() {
        val banks = interpret(record("RY.TO", sector = "Financial Services", industry = "Banks - Diversified"), record("TD", sector = "Financial Services", industry = "Banks - Diversified"))
        assertTrue(banks.summary.first().text.contains("same industry (Banks - Diversified)"))
        assertNull(banks.industryNote)
        assertEquals(Comparability.NOT_COMPARABLE, banks.metrics.getValue("debtEquity").comparability)
        val unknown = interpret(record("LUCY", "Innovative Eyewear", sector = null, industry = null, metrics = arrayOf("netMargin" to v(4.3))), record("AAPL", "Apple", metrics = arrayOf("netMargin" to v(24.0))))
        assertTrue(unknown.summary.first().text.startsWith("Sector isn't reported for Innovative Eyewear"))
        assertTrue(unknown.metrics.getValue("netMargin").caveats.any { it.contains("Sector isn't reported") })
        val reit = interpret(record("GIPR", "Generation Income", "Real Estate", "REIT - Diversified", metrics = arrayOf("pe" to v(107.4), "dividendYield" to v(6.0))),
            record("AAPL", "Apple", metrics = arrayOf("pe" to v(27.8), "dividendYield" to v(0.4))))
        assertEquals(IndustryKind.REIT, ComparisonInterpretationEngine.kind(record("GIPR", sector = "Real Estate", industry = "REIT - Diversified")))
        assertTrue(reit.metrics.getValue("pe").caveats.any { it.contains("funds from operations") })
        assertTrue(reit.metrics.getValue("dividendYield").caveats.any { it.contains("REITs generally must pay out") })
        // No invented benchmarks anywhere.
        assertTrue((texts(reit) + reit.metrics.values.flatMap { it.caveats }).none { it.contains("industry average", ignoreCase = true) || it.contains("sector average", ignoreCase = true) })
    }

    // ---------- Summary ----------

    @Test fun learningSummaryIsShortGroundedAndDeterministic() {
        val a = record("AAA", "Alpha", metrics = arrayOf("revenueGrowth" to v(15.0, "annual", "2025-12-31"), "netMargin" to v(12.0), "pe" to v(40.0), "debtEquity" to v(0.4)))
        val b = record("BBB", "Beta", metrics = arrayOf("revenueGrowth" to v(4.0, "annual", "2025-12-31"), "netMargin" to v(25.0), "pe" to v(15.0), "debtEquity" to v(1.4)))
        val i = interpret(a, b)
        assertTrue(i.summary.size in 2..ComparisonInterpretationEngine.SUMMARY_LIMIT)
        assertEquals(listOf(InsightCategory.INDUSTRY, InsightCategory.GROWTH, InsightCategory.PROFITABILITY), i.summary.map { it.category })
        assertEquals("Alpha reported faster year-over-year revenue growth than Beta for their latest fiscal years (+15.0% vs +4.0%).", i.summary[1].text)
        // Every number in the summary is a value displayed in the table.
        val cells = listOf(a, b).flatMap { r -> r.metrics.map { (id, mv) -> MetricFormatter.cell(ScreenerDefinitions.metric(id), mv, r.currency).text } }
        Regex("[−+]?[0-9]+\\.[0-9]+[×%]?").findAll(i.summary.joinToString(" ") { it.text }).forEach { assertTrue(it.value in cells, "${it.value} isn't a displayed value") }
        // Same input → identical output; input order doesn't change the facts.
        assertEquals(i, interpret(a, b))
        assertEquals(i.metrics.mapValues { it.value.comparability }, interpret(b, a).metrics.mapValues { it.value.comparability })
        assertEquals(i.metrics.getValue("pe").headline, interpret(b, a).metrics.getValue("pe").headline)
        assertTrue(texts(i).none { ranking.containsMatchIn(it) })
    }

    @Test fun partialFailuresAndSparseDataAreSaidPlainly() {
        val ok = record("AAA", "Alpha")
        val i = ComparisonInterpretationEngine.interpret(listOf(ComparedCompany(ok, "AAA"), ComparedCompany(null, "BBB", "Company data isn't available right now.")))
        assertEquals(InsightCategory.DATA, i.summary.first().category)
        assertTrue(i.summary.first().text.contains("BBB isn't available"))
        assertTrue(i.summary.any { it.text.contains("never filled in or estimated") })
        assertTrue(i.metrics.values.all { it.comparability == Comparability.INSUFFICIENT_DATA })
        assertTrue(i.metrics.getValue("pe").caveats.first().startsWith("BBB: Company data isn't available"))
        assertEquals(ComparisonInterpretation.EMPTY, ComparisonInterpretationEngine.interpret(listOf(ComparedCompany(ok, "AAA"))))
    }
}

/** Phase 2 presenter behaviour: explanations open/close/deepen, related metrics, selection preserved. */
class ComparisonGuidancePresenterTest {
    private class Data(val records: Map<String, CompanyRecord>, var failures: Int = 0) : ScreenerDataSource {
        var compareCalls = 0
        override suspend fun catalog() = error("unused")
        override suspend fun search(query: ScreenerQuery) = error("unused")
        override suspend fun compare(symbols: List<String>): ComparisonResponse {
            compareCalls++
            if (failures-- > 0) throw IllegalStateException("offline")
            return ComparisonResponse(symbols.map { ComparedCompany(records[it], it, if (records[it] == null) "Company data isn't available right now." else null) }, asOf = "2026-10-07T21:15:00Z",
                fx = listOf(FxConversion("CAD", "USD", 0.74, "2026-10-07", "fixed sample rate")))
        }
        override suspend fun performance(symbols: List<String>, period: PerformancePeriod) =
            PerformanceComparison(period, ReturnKind.PRICE_RETURN, listOf("d1", "d2"), symbols.map { PerformanceSeries(it, listOf(100.0, 101.0), 1.0) })
    }

    private fun r(symbol: String, pe: Double?, sector: String = "Technology") = CompanyRecord(symbol, "$symbol Inc", "NYSE", "US", "USD", sector, "Software", marketCap = 1e11,
        metrics = mapOf("pe" to MetricValue(pe, if (pe == null) FinancialAvailability.NON_POSITIVE_DENOMINATOR else FinancialAvailability.AVAILABLE, FinancialBasis("TTM")),
            "netMargin" to MetricValue(20.0, FinancialAvailability.AVAILABLE, FinancialBasis("TTM"))))

    private fun test(block: suspend CoroutineScope.(Data, ComparisonSelection, ComparisonPresenter) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = Data(mapOf("AAA" to r("AAA", 30.0), "BBB" to r("BBB", 12.0), "CCC" to r("CCC", null, "Financial Services")))
            val selection = ComparisonSelection()
            block(data, selection, ComparisonPresenter(data, selection, scope))
        } finally { scope.cancel() }
    }

    private suspend fun ComparisonPresenter.until(predicate: (ComparisonUiState) -> Boolean) = withTimeout(5_000) { state.first(predicate) }

    @Test fun explanationsOpenCollapseAndDeepenWithoutTouchingTheSelection(): Unit = test { data, selection, presenter ->
        selection.add("AAA", "AAA Inc"); selection.add("BBB", "BBB Inc")
        val loaded = presenter.until { it.guides.isNotEmpty() && !it.loading }
        assertTrue(loaded.insights.isNotEmpty())
        assertEquals("AAA Inc has a higher trailing P/E than BBB Inc (30.0× vs 12.0×).", loaded.guide("pe")!!.observation)
        assertNull(loaded.guide("sector"))                                                                  // text rows have no guide
        presenter.toggleExplanation("pe")
        assertTrue(presenter.state.value.isExpanded("pe"))
        presenter.toggleDeeper("pe")
        assertTrue(presenter.state.value.isDeeper("pe"))
        presenter.toggleExplanation("pe")                                                                   // collapse closes "Learn more" too
        assertFalse(presenter.state.value.isExpanded("pe")); assertFalse(presenter.state.value.isDeeper("pe"))
        presenter.toggleExplanation("netMargin"); presenter.toggleExplanation("pe")
        presenter.collapseExplanations()
        assertTrue(presenter.state.value.expanded.isEmpty())
        assertEquals(listOf("AAA", "BBB"), selection.selected.value.map { it.symbol })
        assertEquals(1, data.compareCalls)                                                                  // explanations never refetch
    }

    @Test fun relatedMetricsSwitchGroupsAndOpenMoreMetrics(): Unit = test { _, selection, presenter ->
        selection.add("AAA", "AAA Inc"); selection.add("BBB", "BBB Inc")
        presenter.until { it.guides.isNotEmpty() }
        // From Valuation (P/E) to Growth (revenue growth): opened and focused.
        presenter.toggleExplanation("pe")
        presenter.openRelated("revenueGrowth")
        presenter.state.value.let { assertTrue(it.isExpanded("revenueGrowth") && it.isExpanded("pe")); assertEquals("revenueGrowth", it.focus); assertFalse(it.showMore) }
        presenter.clearFocus()
        assertNull(presenter.state.value.focus)
        // An extra metric without a guide opens "More metrics" and is focused there.
        presenter.openRelated("interestCoverage")
        presenter.state.value.let { assertTrue(it.showMore); assertEquals("interestCoverage", it.focus); assertFalse(it.isExpanded("interestCoverage")) }
        presenter.toggleMore()
        assertFalse(presenter.state.value.showMore)
    }

    @Test fun changingCompaniesRecomputesGuidesAndKeepsOpenExplanations(): Unit = test { _, selection, presenter ->
        selection.add("AAA", "AAA Inc"); selection.add("BBB", "BBB Inc")
        presenter.until { it.guides.isNotEmpty() }
        presenter.toggleExplanation("pe")
        presenter.replace("BBB", "CCC", "CCC Inc")                                                           // a loss-making bank
        val next = presenter.until { s -> s.columns.map { it.symbol } == listOf("AAA", "CCC") && !s.loading }
        assertTrue(next.isExpanded("pe"))
        assertEquals(Comparability.INSUFFICIENT_DATA, next.guide("pe")!!.comparability)                    // missing-data state, no invented value
        assertTrue(next.guide("pe")!!.caveats.first().contains("loss"))
        assertNotNull(next.industryNote)
        assertEquals(listOf("AAA", "CCC"), selection.selected.value.map { it.symbol })
        selection.remove("CCC")
        presenter.until { it.guides.isEmpty() && it.insights.isEmpty() }
    }

    @Test fun loadingFailureAndRetryProduceGuidesOnlyFromLoadedData(): Unit = test { data, selection, presenter ->
        data.failures = 1
        selection.add("AAA", "AAA Inc"); selection.add("ZZZ", "Missing Co")
        val failed = presenter.until { it.error != null }
        assertTrue(failed.guides.isEmpty())
        presenter.retry()
        val ok = presenter.until { it.guides.isNotEmpty() && it.error == null }
        assertTrue(ok.insights.first().text.contains("ZZZ isn't available"))
        assertEquals(Comparability.INSUFFICIENT_DATA, ok.guide("netMargin")!!.comparability)
        assertEquals(listOf("AAA", "ZZZ"), selection.selected.value.map { it.symbol })
    }
}
