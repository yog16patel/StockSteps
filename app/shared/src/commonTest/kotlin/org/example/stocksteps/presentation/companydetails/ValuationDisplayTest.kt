package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.InfoRow
import org.example.stocksteps.companydetail.ValuationExplainer
import org.example.stocksteps.companydetail.ValuationModel
import org.example.stocksteps.companydetail.ValuationPosition
import org.example.stocksteps.companydetail.ValuationRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValuationDisplayTest {
    private fun model(currentPe: String = "28.0x", average: String? = "27.5x", difference: String? = "+2%", averageLabel: String = "5-Year Average", other: List<InfoRow> = emptyList(), growth: List<InfoRow> = emptyList()) = ValuationModel(
        range = ValuationRange.FIVE_YEARS, currentPe = currentPe, currentPeAvailable = currentPe != "N/A", badge = "Near Historical Average",
        position = ValuationPosition.NEAR, meaning = "m", averageLabel = averageLabel, average = average, difference = difference, methodology = "x",
        chart = null, rangeSummary = null, understanding = "u", highReasons = ValuationExplainer("Why can P/E be high?", emptyList()),
        lowReasons = ValuationExplainer("Why can P/E be low?", emptyList()), otherMetrics = other, growth = growth, growthNote = "", insight = "", historyMessage = null)

    private val apple = listOf(
        InfoRow("Price to Sales (P/S)", "10.6x", "Market value ÷ annual revenue.", metricId = "priceSales"),
        InfoRow("Price to Book (P/B)", "46.0x", "Market value ÷ shareholders' equity (book value).", metricId = "priceBook"),
        InfoRow("EV / EBITDA", "29.6x", "Enterprise value ÷ earnings before interest, taxes, depreciation and amortization.", metricId = "evEbitda"),
        InfoRow("PEG Ratio", "1.17", "P/E ÷ expected earnings growth, as defined by the data provider; growth periods differ between sources.", metricId = "peg"),
        InfoRow("Dividend Yield", "0.31%", "Yearly dividends ÷ share price. Yield alone doesn't show whether a stock is cheap or expensive.", metricId = "dividendYield"))

    @Test
    fun keyMetricsArePeThenTheModelsRatiosWithTheirOwnValues() {
        val metrics = ValuationDisplay.keyMetrics(model(other = apple))
        assertEquals(listOf("P/E Ratio", "Price to Sales (P/S)", "Price to Book (P/B)", "EV / EBITDA", "PEG Ratio", "Dividend Yield"), metrics.map { ValuationDisplay.plain(it.label) })
        assertEquals(listOf("28.0x", "10.6x", "46.0x", "29.6x", "1.17", "0.31%"), metrics.map { it.value })
        assertEquals(listOf("pe", "priceSales", "priceBook", "evEbitda", "peg", "dividendYield"), metrics.map { it.metricId })
        // Captions are the first sentence of the existing description (never new wording).
        assertEquals("Yearly dividends ÷ share price.", metrics[5].caption)
        assertTrue(metrics[4].caption!!.startsWith("P/E ÷ expected earnings growth"))
        assertNull(metrics[0].caption)
    }

    @Test
    fun missingRatiosHaveNoTileAndUnavailableValuesStayAsTheModelSaysThem() {
        // Negative earnings: the model's "N/A", never a number or zero.
        val loss = ValuationDisplay.keyMetrics(model(currentPe = "N/A", other = listOf(InfoRow("Dividend Yield", "No dividend", "The company doesn't currently pay a regular dividend.", metricId = "dividendYield"))))
        assertEquals(listOf("N/A", "No dividend"), loss.map { it.value })
        // Only P/E when the model has no other ratios.
        assertEquals(listOf("P/E Ratio"), ValuationDisplay.keyMetrics(model()).map { ValuationDisplay.plain(it.label) })
    }

    @Test
    fun ratioAbbreviationsStayWholeWithoutChangingTheVisibleText() {
        assertEquals("P\u2060/\u2060E Ratio", ValuationDisplay.keepTogether("P/E Ratio"))
        assertEquals("Price to Sales (P\u2060/\u2060S)", ValuationDisplay.keepTogether("Price to Sales (P/S)"))
        assertEquals("EV / EBITDA", ValuationDisplay.keepTogether("EV / EBITDA"))
        // C3: model sentences too — the visible text is unchanged.
        val insight = "Apple's current P/E is close to its 5-year average."
        assertEquals("Apple's current P\u2060/\u2060E is close to its 5-year average.", ValuationDisplay.keepTogether(insight))
        assertEquals(insight, ValuationDisplay.plain(ValuationDisplay.keepTogether(insight)))
        assertEquals("Price to Book (P/B)", ValuationDisplay.plain(ValuationDisplay.keepTogether("Price to Book (P/B)")))
    }

    @Test
    fun valuationAndGrowthDropsOnlyTheDuplicatePeRowAndKeepsItsVisibilityRule() {
        val pe = InfoRow("P/E Ratio", "28.0x", metricId = "pe")
        val revenue = InfoRow("Revenue Growth (latest year)", "+6.4%", metricId = "revenueGrowth")
        val eps = InfoRow("EPS Growth (latest year)", "+75.5%", metricId = "epsGrowth")
        val full = model(growth = listOf(pe, revenue, eps))
        assertTrue(ValuationDisplay.growthVisible(full))
        assertEquals(listOf(revenue, eps), ValuationDisplay.growthRows(full))
        // Hidden exactly as before: P/E alone (no growth figure) or a single growth row.
        assertEquals(false, ValuationDisplay.growthVisible(model(growth = listOf(pe))))
        assertEquals(false, ValuationDisplay.growthVisible(model(growth = listOf(revenue))))
        // Without a current P/E the model's two growth rows stay as they were.
        assertEquals(listOf(revenue, eps), ValuationDisplay.growthRows(model(growth = listOf(revenue, eps))))
    }

    @Test
    fun comparisonOnlyWithAReliableAverage() {
        assertEquals(ValuationDisplay.Comparison("Current", "28.0x", "5-Year Average", "27.5x", "+2%"), ValuationDisplay.comparison(model()))
        assertEquals("5-Year Average P\u2060/\u2060E", ValuationDisplay.comparison(model(averageLabel = "5-Year Average P/E"))!!.averageLabel)
        assertNull(ValuationDisplay.comparison(model(average = null, difference = null)))
        assertEquals("—", ValuationDisplay.comparison(model(difference = null))!!.difference ?: "—")
    }
}
