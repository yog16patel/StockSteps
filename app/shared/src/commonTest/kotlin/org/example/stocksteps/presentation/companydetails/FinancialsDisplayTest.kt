package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.FinancialBar
import org.example.stocksteps.companydetail.FinancialChart
import org.example.stocksteps.companydetail.FinancialStatementSection
import org.example.stocksteps.companydetail.FinancialStatementsModel
import org.example.stocksteps.companydetail.InfoRow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.companydetail.SectionAvailability
import org.example.stocksteps.presentation.companydetails.FinancialsDisplay.Group
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinancialsDisplayTest {

    @Test
    fun titleUsesTheTickerSoALongCompanyNameNeverTruncatesThePageName() {
        // KAN-16: "Pinnacle Food Group Limited Class A Common Shares Financials" lost "Financials" to the ellipsis in the top bar.
        assertEquals("PFAI Financials", FinancialsDisplay.title("PFAI"))
        assertEquals("SSHC.TO Financials", FinancialsDisplay.title("SSHC.TO"))
    }
    private val revenueChart = FinancialChart(listOf(FinancialBar("FY24", 391.0, ""), FinancialBar("FY25", 416.2, "")), primaryLabel = "Revenue", description = "r")
    private val twoSeries = revenueChart.copy(secondary = listOf(FinancialBar("FY24", 93.7, ""), FinancialBar("FY25", 133.2, "")), secondaryLabel = "Net Income")

    private fun section(id: String, title: String = id, chart: FinancialChart? = null, rows: List<InfoRow> = emptyList(), headline: String? = null,
                        change: String? = null, availability: SectionAvailability = SectionAvailability.CONTENT, label: String? = null) =
        FinancialStatementSection(id, title, availability, headline = headline, headlineLabel = label, change = change,
            changeDirection = change?.let { PriceDirection.UP }, rows = rows, chart = chart, whatIsIt = "", whyItMatters = "")

    private fun model(vararg sections: FinancialStatementSection) =
        FinancialStatementsModel(null, "USD", null, sections.toList(), emptyList(), null, null, emptyList(), null)

    private val apple = model(
        section("revenue", "Revenue", revenueChart, headline = "$416.2B", change = "+6.4% vs FY2024", label = "FY2025"),
        section("profitability", "Profitability", twoSeries, rows = listOf(
            InfoRow("Net Income", "$133.2B", "Profit left after expenses, interest and taxes.", "+41.9%", PriceDirection.UP, metricId = "netIncome"),
            InfoRow("Net Margin", "32.0%", metricId = "netMargin"),
            InfoRow("Operating Margin", "32.0%", "Profit from core operations before interest and taxes.", metricId = "operatingMargin"),
            InfoRow("Gross Margin", "47.0%", "Revenue left after the direct costs of products and services.", metricId = "grossMargin"))),
        section("cashFlow"), section("health"), section("eps"), section("dividends"))

    @Test
    fun keyMetricsAreTheModelsOwnValuesWithTheRevenueComparisonPeriod() {
        val metrics = FinancialsDisplay.keyMetrics(apple)
        assertEquals(listOf("Revenue", "Net Income", "Gross Margin", "Operating Margin"), metrics.map { it.label })
        assertEquals(listOf("$416.2B", "$133.2B", "47.0%", "32.0%"), metrics.map { it.value })
        assertEquals("+6.4% vs FY2024", metrics[0].caption)
        assertEquals("+41.9% vs FY2024", metrics[1].caption)
        // Margins: the model's own description, no invented year-over-year change (D-F3).
        assertNull(metrics[2].direction)
        assertEquals("Revenue left after the direct costs of products and services.", metrics[2].caption)
        assertEquals(listOf("revenue", "netIncome", "grossMargin", "operatingMargin"), metrics.map { it.metricId })
        assertEquals("Key Metrics (FY2025)", FinancialsDisplay.keyMetricsTitle(apple))
    }

    @Test
    fun missingValuesAreLeftOutNeverZero() {
        val sparse = model(section("revenue", "Revenue", availability = SectionAvailability.NOT_REPORTED),
            section("profitability", rows = listOf(InfoRow("Net Income", "$1.0B", metricId = "netIncome"))))
        val metrics = FinancialsDisplay.keyMetrics(sparse)
        assertEquals(listOf("Net Income"), metrics.map { it.label })
        // Without a revenue comparison the net income change shows on its own (here: none).
        assertNull(metrics[0].caption)
        assertEquals("Key Metrics", FinancialsDisplay.keyMetricsTitle(sparse))
    }

    @Test
    fun overviewChartPrefersTheTwoSeriesChartAndIsNotRepeated() {
        assertEquals(twoSeries, FinancialsDisplay.overviewChart(apple))
        assertFalse(FinancialsDisplay.chartInSection(apple, "profitability"))
        assertFalse(FinancialsDisplay.chartInSection(apple, "revenue"))
        assertTrue(FinancialsDisplay.chartInSection(apple, "cashFlow"))
        // Without the two-series chart the revenue chart moves up instead.
        val revenueOnly = model(section("revenue", chart = revenueChart), section("profitability"))
        assertEquals(revenueChart, FinancialsDisplay.overviewChart(revenueOnly))
        assertFalse(FinancialsDisplay.chartInSection(revenueOnly, "revenue"))
        // No chart at all → every section keeps its own (none moves).
        val none = model(section("revenue"), section("cashFlow", chart = revenueChart))
        assertNull(FinancialsDisplay.overviewChart(none))
        assertTrue(FinancialsDisplay.chartInSection(none, "cashFlow"))
    }

    @Test
    fun groupsListOnlySectionsOnThePageAndAnchorToTheFirst() {
        assertEquals(Group.entries, FinancialsDisplay.groups(apple))
        assertEquals("revenue", FinancialsDisplay.anchorSection(apple, Group.INCOME))
        assertEquals("cashFlow", FinancialsDisplay.anchorSection(apple, Group.CASH_FLOW))
        assertEquals(listOf(Group.INCOME), FinancialsDisplay.groups(model(section("eps"))))
        assertEquals("eps", FinancialsDisplay.anchorSection(model(section("eps")), Group.INCOME))
        assertEquals(emptyList(), FinancialsDisplay.groups(null))
        assertEquals(listOf("Income", "Balance Sheet", "Cash Flow"), Group.entries.map { it.label })
    }

    @Test
    fun selectedGroupIsTheGroupOfTheSectionNearestTheBar() {
        // Page order interleaves groups: cash flow (Cash Flow) sits above health (Balance Sheet), EPS (Income) below it.
        val atHealth = mapOf("revenue" to -1500f, "profitability" to -900f, "cashFlow" to -300f, "health" to -2f, "eps" to 500f, "dividends" to 900f)
        assertEquals(Group.BALANCE_SHEET, FinancialsDisplay.selectedGroup(atHealth, 1f))
        val atEps = atHealth.mapValues { it.value - 510f }
        assertEquals(Group.INCOME, FinancialsDisplay.selectedGroup(atEps, 1f))
        // Nothing reached the bar yet → the first section's group.
        assertEquals(Group.INCOME, FinancialsDisplay.selectedGroup(mapOf("revenue" to 40f, "health" to 900f), 1f))
        // End of the page: the last section on screen.
        assertEquals(Group.CASH_FLOW, FinancialsDisplay.selectedGroup(atHealth, 1f, atEnd = true, viewport = 1000f))
        assertNull(FinancialsDisplay.selectedGroup(emptyMap(), 1f))
        assertNull(FinancialsDisplay.selectedGroup(mapOf("summary" to 0f), 1f))
    }

    @Test
    fun revenueHeadlineIsShownOnceWhenTheKeyMetricsTileShowsIt() {
        // UI Phase 3: "$416.2B · +6.4% vs FY2024" was the overview caption, the first tile and the Revenue section's headline.
        assertFalse(FinancialsDisplay.headlineInSection(apple, "revenue"))
        assertTrue(FinancialsDisplay.headlineInSection(apple, "profitability"))
        assertTrue(FinancialsDisplay.headlineInSection(apple, "health"))
        // Without a revenue tile (no headline), the section keeps whatever it has.
        val noTile = model(section("revenue", "Revenue", revenueChart))
        assertTrue(FinancialsDisplay.headlineInSection(noTile, "revenue"))
    }
}
