package org.example.stocksteps.screener

import org.example.stocksteps.model.FinancialAvailability
import kotlin.test.*

/** Global UI Refinement Phase 5C: shared Compare Companies display text (missing values, spoken labels, layout inputs). */
class ComparisonDisplayTest {
    private fun column(symbol: String, name: String, error: String? = null) =
        CompanyColumn(symbol, name, "NASDAQ", "USD", null, "$1.00", MetricFormatter.change(1.0), error)

    private val apple = column("AAPL", "Apple Inc.")
    private val tesla = column("TSLA", "Tesla, Inc.")
    private val pe = ScreenerDefinitions.metric("pe")
    private val cap = ScreenerDefinitions.metric("marketCap")

    @Test fun missingValuesReadNoDataNeverZeroOrNa() {
        val missing = MetricFormatter.cell(cap, MetricValue(availability = FinancialAvailability.MISSING))
        assertEquals("N/A", missing.text)                                  // presenter text unchanged
        assertEquals(ComparisonDisplay.NO_DATA, ComparisonDisplay.cellText(missing))
        // A genuine zero stays zero; "None" (no dividend) stays "None"; real values unchanged.
        val zeroYield = MetricFormatter.cell(ScreenerDefinitions.metric("dividendYield"), MetricValue(0.0, FinancialAvailability.AVAILABLE))
        assertEquals("0.0%", ComparisonDisplay.cellText(zeroYield))
        val none = MetricFormatter.cell(ScreenerDefinitions.metric("dividendYield"), MetricValue(0.0, FinancialAvailability.NO_DIVIDEND))
        assertEquals("None", ComparisonDisplay.cellText(none))
        assertEquals("27.8×", ComparisonDisplay.cellText(MetricFormatter.cell(pe, MetricValue(27.8, FinancialAvailability.AVAILABLE))))
    }

    @Test fun everySpokenValueNamesTheCompanyMetricAndPeriod() {
        val row = ComparisonRow("pe", "P/E ratio (trailing)", "Trailing twelve months",
            listOf(MetricFormatter.cell(pe, MetricValue(27.8, FinancialAvailability.AVAILABLE)),
                MetricFormatter.cell(pe, MetricValue(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR))))
        assertEquals("Apple Inc., P/E ratio (trailing), Trailing twelve months, 27.8×", ComparisonDisplay.cellSpoken(apple, row, row.cells[0]))
        val spoken = ComparisonDisplay.cellSpoken(tesla, row, row.cells[1])
        assertTrue(spoken.startsWith("Tesla, Inc., P/E ratio (trailing), Trailing twelve months, unavailable."), spoken)
        assertTrue(spoken.contains("loss"), "keeps the metric-specific reason: $spoken")
        assertEquals("P/E ratio (trailing) unavailable for TSLA. Explain why.", ComparisonDisplay.missingAction(row, tesla))
        // Selection order is kept and every company is named.
        assertEquals("P/E ratio (trailing), Trailing twelve months. Apple Inc. 27.8×; Tesla, Inc. unavailable", ComparisonDisplay.rowSpoken(row, listOf(apple, tesla)))
        assertFalse(ComparisonDisplay.rowSpoken(row, listOf(apple, tesla)).contains("N/A"))
    }

    @Test fun periodsAndFxDetailsStayInSpokenAndLayoutText() {
        val cell = MetricCell("$281.7B", detail = "FY ended Jun 2025")
        val row = ComparisonRow("revenue", "Revenue", null, listOf(cell))
        assertEquals("Apple Inc., Revenue, $281.7B, FY ended Jun 2025", ComparisonDisplay.cellSpoken(apple, row, cell))
        // Layout measures the short visible period (Phase 5C.1); the spoken label above keeps the full one.
        assertEquals(listOf("$281.7B", "FY Jun 2025"), ComparisonDisplay.layoutTexts(listOf(row)))
    }

    @Test fun selectionRowsSayPartialData() {
        assertEquals("Apple Inc., AAPL, NASDAQ · USD", ComparisonDisplay.selectionSpoken(SelectedCompany("AAPL", "Apple"), apple))
        assertEquals("Tesla, Inc., TSLA, NASDAQ · USD, partial data",
            ComparisonDisplay.selectionSpoken(SelectedCompany("TSLA", "Tesla"), column("TSLA", "Tesla, Inc.", error = "Company data isn't available right now.")))
        // Before the comparison loads, the selection's own name is used.
        assertEquals("Apple, AAPL", ComparisonDisplay.selectionSpoken(SelectedCompany("AAPL", "Apple"), null))
    }

    @Test fun selectionOrderIsUnchangedByReplace() {
        val selection = ComparisonSelection()
        listOf("AAPL", "MSFT", "NVDA").forEach { selection.add(it, it) }
        selection.replace("MSFT", "TSLA", "Tesla")
        assertEquals(listOf("AAPL", "TSLA", "NVDA"), selection.selected.value.map { it.symbol }, "Replace keeps the position")
        selection.remove("AAPL")
        assertEquals(listOf("TSLA", "NVDA"), selection.selected.value.map { it.symbol })
        listOf("A", "B", "C").forEach { selection.add(it, it) }
        assertEquals(MAX_COMPARED_COMPANIES, selection.selected.value.size)
    }

    // ---------- Phase 5C.1 ----------

    @Test fun onlyGrowthAndProfitabilityStartOpen() {
        listOf("Growth", "Profitability").forEach { assertTrue(ComparisonDisplay.expandedByDefault(it), it) }
        listOf("Overview", "Financial Health", "Valuation", "Shareholder Returns", "Price performance and payouts", ComparisonDisplay.HISTORY_TITLE)
            .forEach { assertFalse(ComparisonDisplay.expandedByDefault(it), it) }
        val section = ComparisonSection("Overview", listOf(ComparisonRow("a", "A", null, emptyList()), ComparisonRow("b", "B", null, emptyList())))
        assertEquals("2 metrics", ComparisonDisplay.sectionSummary(section))
        assertEquals("Overview, 2 metrics, collapsed", ComparisonDisplay.sectionSpoken(section, expanded = false))
        assertEquals(section, ComparisonDisplay.sectionOf(listOf(section), "b"))
        assertNull(ComparisonDisplay.sectionOf(listOf(section), "zzz"))
    }

    @Test fun shortPeriodsKeepTheFiscalInformation() {
        assertEquals("TTM", ComparisonDisplay.shortPeriod("Trailing twelve months"))
        assertEquals("FY Sep 2025", ComparisonDisplay.shortPeriod("FY ended Sep 2025"))
        assertEquals("Q3 FY26 vs Q3 FY25", ComparisonDisplay.shortPeriod("Q3 FY2026 vs Q3 FY2025"))
        assertEquals("Periods differ", ComparisonDisplay.shortPeriod("Periods differ by company"))
        // Unknown text and converted amounts are never altered.
        assertEquals("≈ US$181.0B converted", ComparisonDisplay.shortPeriod("≈ US$181.0B converted"))
        assertEquals("Fiscal year; reporting currency", ComparisonDisplay.shortPeriod("Fiscal year; reporting currency"))
        // Spoken text keeps the full period.
        val cell = MetricCell("+6.4%", detail = "Q3 FY2026 vs Q3 FY2025")
        assertTrue(ComparisonDisplay.cellSpoken(apple, ComparisonRow("g", "Revenue growth", null, listOf(cell)), cell).endsWith("Q3 FY2026 vs Q3 FY2025"))
        assertEquals(listOf("+6.4%", "Q3 FY26 vs Q3 FY25"), ComparisonDisplay.layoutTexts(listOf(ComparisonRow("g", "G", null, listOf(cell)))))
        val shared = ComparisonSection("Growth", listOf(ComparisonRow("a", "A", "Periods differ by company", emptyList()), ComparisonRow("b", "B", "Periods differ by company", emptyList())))
        assertEquals("Periods differ by company", ComparisonDisplay.sharedPeriod(shared))
        assertNull(ComparisonDisplay.sharedPeriod(shared.copy(rows = shared.rows + ComparisonRow("c", "C", "TTM", emptyList()))))
    }

    @Test fun barsOnlyForPercentagesAndNeverForMissingValues() {
        val growth = ScreenerDefinitions.metric("revenueGrowth")
        fun cell(v: Double?) = MetricFormatter.cell(growth, v?.let { MetricValue(it, FinancialAvailability.AVAILABLE) } ?: MetricValue())
        val bars = ComparisonDisplay.bars(ComparisonRow("revenueGrowth", "Revenue growth", null, listOf(cell(40.0), cell(-10.0), cell(0.0), cell(null))))!!
        assertEquals(1f, bars[0]!!.fraction)
        assertEquals(0.25f, bars[1]!!.fraction); assertTrue(bars[1]!!.negative)
        assertEquals(0f, bars[2]!!.fraction); assertFalse(bars[2]!!.negative)   // a genuine zero has a (zero-length) bar
        assertNull(bars[3])                                                    // missing: no bar, shown as "No data"
        // Money cells are built without a value, so amounts in different currencies are never drawn as bars.
        assertNull(ComparisonDisplay.bars(ComparisonRow("cash", "Cash", null, listOf(MetricCell("$1.0B"), MetricCell("C$2.0B")))))
        // One value alone has nothing to compare.
        assertNull(ComparisonDisplay.bars(ComparisonRow("revenueGrowth", "Revenue growth", null, listOf(cell(5.0), cell(null)))))
        assertNull(MetricFormatter.cell(ScreenerDefinitions.metric("pe"), MetricValue(20.0, FinancialAvailability.AVAILABLE)).value, "multiples get no bar")
    }

    @Test fun notesInsightsAndInfoLabelsStayConcise() {
        val growth = ScreenerDefinitions.metric("revenueGrowth")
        fun cell(v: Double) = MetricFormatter.cell(growth, MetricValue(v, FinancialAvailability.AVAILABLE))
        val withBars = ComparisonSection("Growth", listOf(ComparisonRow("revenueGrowth", "Revenue growth", null, listOf(cell(1.0), cell(2.0)))), note = "Quarters differ.")
        assertEquals(listOf("Quarters differ.", ComparisonDisplay.BAR_NOTE), ComparisonDisplay.sectionNotes(withBars))
        assertTrue(ComparisonDisplay.sectionNotes(ComparisonSection("Overview", emptyList())).isEmpty())
        assertEquals("What to keep in mind about Growth, collapsed", ComparisonDisplay.notesSpoken(withBars, open = false))
        assertNull(ComparisonDisplay.insightsToggle(1, open = false))
        assertEquals("Show all 3 insights", ComparisonDisplay.insightsToggle(3, open = false))
        assertEquals("Show less", ComparisonDisplay.insightsToggle(3, open = true))
        val row = withBars.rows.single()
        assertEquals("Explain Revenue growth", ComparisonDisplay.infoLabel(row, guided = true, expanded = false))
        assertEquals("Hide explanation of Revenue growth", ComparisonDisplay.infoLabel(row, guided = true, expanded = true))
        assertEquals("About Revenue growth", ComparisonDisplay.infoLabel(row, guided = false, expanded = false))
    }

    @Test fun companyHeaderPinsFromTheFirstSectionWithUnlabelledColumns() {
        val growth = ScreenerDefinitions.metric("revenueGrowth")
        fun cell(v: Double) = MetricFormatter.cell(growth, MetricValue(v, FinancialAvailability.AVAILABLE))
        val bars = ComparisonSection("Growth", listOf(ComparisonRow("revenueGrowth", "Revenue growth", null, listOf(cell(1.0), cell(2.0)))))
        val columns = ComparisonSection("Valuation", listOf(ComparisonRow("pe", "P/E", null, listOf(MetricCell("20.0"), MetricCell("30.0")))))
        assertNull(ComparisonDisplay.companyHeaderSection(listOf(bars), sideBySide = true), "bar rows name each company")
        assertEquals("Valuation", ComparisonDisplay.companyHeaderSection(listOf(bars, columns), sideBySide = true), "not over the bars above it")
        assertNull(ComparisonDisplay.companyHeaderSection(listOf(columns), sideBySide = false), "stacked lines name each company")
        assertNull(ComparisonDisplay.companyHeaderSection(emptyList(), sideBySide = true), "every section collapsed")
    }

    @Test fun moreMetricsAndSectionLabelsUseLiveValues() {
        assertEquals("More metrics (26)", ComparisonDisplay.moreMetricsLabel(26, showing = false))
        assertEquals("Hide extra metrics", ComparisonDisplay.moreMetricsLabel(26, showing = true))
        val section = ComparisonSection("Valuation", listOf(ComparisonRow("pe", "P/E", "Trailing twelve months", emptyList()),
            ComparisonRow("ps", "P/S", "Trailing twelve months", emptyList())))
        assertEquals("Valuation, 2 metrics, Trailing twelve months", ComparisonDisplay.sectionLabel(section, open = true))
        assertEquals("Valuation, 2 metrics", ComparisonDisplay.sectionLabel(section, open = false))
    }

    private val nvda = column("NVDA", "NVIDIA Corporation")
    private val msft = column("MSFT", "Microsoft Corporation")
    private val dates = "Reporting periods end on different dates (AAPL 2025-09-30, MSFT 2025-06-30, NVDA 2026-01-31, TSLA 2025-12-31)."
    private val fourCompanyObservations = listOf(
        ComparisonObservation("operatingMargin", "Among these 4 companies, operating margin ranges from 7.2% (Tesla, Inc.) to 60.0% (NVIDIA Corporation).", dates),
        ComparisonObservation("netMargin", "Among these 4 companies, net margin ranges from 7.3% (Tesla, Inc.) to 53.0% (NVIDIA Corporation).", dates),
        ComparisonObservation("quarterRevenueGrowth", "Among these 4 companies, latest-quarter revenue growth ranges from 0.9% (Tesla, Inc.) to 43.4% (NVIDIA Corporation).",
            "Each company's latest quarter can cover different months. Past growth doesn't indicate future growth."),
        ComparisonObservation("revenueGrowth", "Among these 4 companies, revenue growth ranges from 0.9% (Tesla, Inc.) to 43.4% (NVIDIA Corporation).",
            "$dates Past growth doesn't indicate future growth."),
        ComparisonObservation("pe", "Among these 4 companies, trailing P/E ranges from 27.8× (Apple Inc.) to 185.2× (Tesla, Inc.).", "A lower P/E isn't proof that a stock is undervalued."),
        ComparisonObservation("debtEquity", "Among these 4 companies, debt / equity ranges from 0.08 (NVIDIA Corporation) to 1.34 (Apple Inc.).", dates),
        ComparisonObservation("dividendYield", "Among these 3 companies, dividend yield ranges from 0.2% (NVIDIA Corporation) to 0.7% (Microsoft Corporation).", "Dividends can change and aren't guaranteed."),
        ComparisonObservation("freeCashFlow", "All 4 companies report positive free cash flow."))

    @Test fun observationsShowThreePrimaryInsightsAndCompactAdditionalOnes() {
        val view = ComparisonObservationsDisplay.view(fourCompanyObservations, listOf(apple, msft, nvda, tesla))
        assertEquals("Based on the 4 companies selected", view.subtitle)
        assertEquals(listOf("Revenue growth", "Profitability", "Valuation"), view.top.map { it.heading }, "category titles, one per topic")
        assertEquals(listOf(ObservationIcon.GROWTH, ObservationIcon.PROFITABILITY, ObservationIcon.VALUATION), view.top.map { it.icon })
        assertEquals("Ranges from 0.9% (TSLA) to 43.4% (NVDA).", view.top[0].text, "growth title already names the metric")
        assertEquals("Net margin ranges from 7.3% (TSLA) to 53.0% (NVDA).", view.top[1].text, "category title → the text names the metric")
        assertEquals("Trailing P/E ranges from 27.8× (AAPL) to 185.2× (TSLA).", view.top[2].text)
        assertEquals("Past growth doesn't indicate future growth.", view.top[0].caveat, "dates moved to the footer; metric caveat kept")
        assertEquals("Revenue growth. Ranges from 0.9% (TSLA) to 43.4% (NVDA). Past growth doesn't indicate future growth.", view.top[0].spoken,
            "one label per insight, no doubled full stops")
        // Additional observations: everything else, titled by metric, in topic order, each once.
        assertEquals(listOf("Quarterly revenue growth", "Operating margin", "Debt / equity", "Dividend yield", "Free cash flow"), view.more.map { it.heading })
        assertEquals(listOf(ObservationIcon.GROWTH, ObservationIcon.OPERATING, ObservationIcon.DEBT, ObservationIcon.DIVIDEND, ObservationIcon.CASH), view.more.map { it.icon })
        assertEquals(8, view.total, "nothing is dropped")
        assertTrue((view.top + view.more).map { it.metric }.let { it.size == it.distinct().size }, "no insight appears twice")
        assertEquals("Each company's latest quarter can cover different months.", view.more[0].caveat, "a caveat sentence is shown once")
        assertEquals("Ranges from 0.2% (NVDA) to 0.7% (MSFT) · 3 of 4 companies report it.", view.more[3].text, "missing yields aren't counted as zero")
        assertEquals("All 4 companies report positive free cash flow.", view.more[4].text)
        assertEquals("Reporting periods may differ. These observations aren't investment recommendations.", view.note)
        assertTrue((view.top + view.more).none { it.text.contains("2025-") || (it.caveat ?: "").contains("2025-") }, "no repeated reporting dates")
        assertEquals("View all 8 insights", ComparisonObservationsDisplay.toggle(view.total, open = false))
        assertEquals("Show fewer insights", ComparisonObservationsDisplay.toggle(view.total, open = true))
        assertNull(ComparisonObservationsDisplay.toggle(3, open = false), "no toggle when there is nothing more")
        assertEquals("These observations aren't investment recommendations.",
            ComparisonObservationsDisplay.view(fourCompanyObservations.takeLast(1), listOf(apple, msft, nvda, tesla)).note)
    }

    @Test fun twoCompanyObservationsKeepBothValuesAndTheCount() {
        val observations = listOf(
            ComparisonObservation("netMargin", "Microsoft Corporation has a higher net margin than Apple Inc. (36.0% vs 32.0%)."),
            ComparisonObservation("revenueGrowth", "Microsoft Corporation has faster revenue growth than Apple Inc. (14.9% vs 6.4%).", "Past growth doesn't indicate future growth."))
        val view = ComparisonObservationsDisplay.view(observations, listOf(apple, msft))
        assertEquals("Based on the 2 companies selected", view.subtitle)
        assertEquals(listOf("Revenue growth", "Profitability"), view.top.map { it.heading }, "only supported topics; nothing invented to fill a slot")
        assertEquals("MSFT has a higher net margin than AAPL (36.0% vs 32.0%).", view.top[1].text)
        assertTrue(view.more.isEmpty()); assertNull(ComparisonObservationsDisplay.toggle(view.total, open = false))
    }

    @Test fun chartHelpAndSourcesKeepEveryNoteInTheRightGroup() {
        val chart = ComparisonNotesDisplay.chartGroups(listOf(
            "Price change only: every line starts at 100 on 2025-10-07. Dividends aren't included, so this isn't total return.",
            "This shows how share prices moved, not how the businesses performed, and past moves don't predict future returns.",
            "Prices are split-adjusted by the data provider, so a split doesn't appear as a drop.",
            "Sample price history for development, not real prices."))
        assertEquals(listOf("How the comparison works", "What is excluded", "Important limitations", "Data methodology"), chart.map { it.title })
        assertEquals("Each line starts at 100 on Oct 7, 2025.", chart[0].lines[0])
        assertEquals(listOf("Dividends aren't included, so this isn't total return."), chart[1].lines)
        assertEquals(2, chart[3].lines.size, "split adjustment and the sample note stay")
        assertEquals(listOf("Share price moves aren't the same as business performance.", "Past price moves don't predict future returns."), chart[2].lines,
            "the known limitations sentence becomes two points")
        assertEquals(listOf("How the comparison works"), ComparisonNotesDisplay.chartGroups(emptyList()).map { it.title }, "no empty groups")
        val sources = ComparisonNotesDisplay.sourceGroups(listOf(
            "Fiscal years end in different months, so annual figures cover different periods.",
            "Trailing (TTM) and estimated forward P/E are shown separately and never mixed.",
            "Financial statements retrieved: AAPL 2026-10-07, MSFT 2026-10-07, NVDA 2026-10-07, TSLA 2026-10-07.",
            "Sample fixture data for development (quotes, profiles, financial statements, quarterly results and prices); not live market data.",
            "A new provider note."), asOf = "2026-10-07T21:17:00Z")
        assertEquals(listOf("Reporting periods", "Metric definitions", "Data sources", "Sample data"), sources.map { it.title })
        assertEquals(listOf("Financial statements retrieved Oct 7, 2026 for all 4 companies.", "A new provider note.", "Comparison prepared Oct 7, 2026, 9:17 PM UTC."),
            sources[2].lines, "readable dates; unknown notes kept verbatim")
        val mixed = ComparisonNotesDisplay.sourceGroups(listOf("Financial statements retrieved: AAPL 2026-10-07, MSFT 2026-09-30."), null)
        assertEquals(listOf("Financial statements retrieved:", "AAPL · Oct 7, 2026", "MSFT · Sep 30, 2026"), mixed.single().lines)
        assertTrue(ComparisonNotesDisplay.sourceGroups(emptyList(), null).isEmpty())
    }

    @Test fun chartSelectionListsEveryDrawnCompanyWithItsLineStyle() {
        val chart = PerformanceComparison(PerformancePeriod.entries.first(), ReturnKind.entries.first(), dates = listOf("2026-10-06", "2026-10-07"),
            series = listOf(PerformanceSeries("AAPL", values = listOf(100.0, 131.26)), PerformanceSeries("MSFT", values = listOf(100.0, null)),
                PerformanceSeries("TSLA", error = "Price history isn't available."), PerformanceSeries("NVDA", values = listOf(100.0, 128.31))))
        val selection = ComparisonChartDisplay.selection(chart, 1)!!
        assertEquals("Oct 7, 2026", selection.date)
        assertEquals(listOf(ChartSelectionEntry("AAPL", "131.3", 0), ChartSelectionEntry("MSFT", ComparisonDisplay.NO_DATA, 1), ChartSelectionEntry("NVDA", "128.3", 2)),
            selection.entries, "drawn order and style index; missing stays 'No data'; failed series left out")
        assertEquals("Oct 7, 2026: AAPL 131.3, MSFT No data, NVDA 128.3", selection.spoken)
        assertNull(ComparisonChartDisplay.selection(chart, 5))
        assertEquals(1, ComparisonChartDisplay.defaultIndex(chart))
        assertEquals(2, ComparisonChartDisplay.selectionColumns(4, accessibilityText = false))
        assertEquals(1, ComparisonChartDisplay.selectionColumns(4, accessibilityText = true))
        assertEquals(1, ComparisonChartDisplay.selectionColumns(1, accessibilityText = false))
    }
}
