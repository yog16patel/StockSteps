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
}
