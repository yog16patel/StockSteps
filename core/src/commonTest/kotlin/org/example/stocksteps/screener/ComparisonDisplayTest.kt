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
        assertEquals(listOf("$281.7B", "FY ended Jun 2025"), ComparisonDisplay.layoutTexts(listOf(row)))
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
}
