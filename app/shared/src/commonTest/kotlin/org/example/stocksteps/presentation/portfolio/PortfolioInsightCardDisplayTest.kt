package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.analytics.Availability
import org.example.stocksteps.portfolio.analytics.InsightCategory
import org.example.stocksteps.portfolio.analytics.PortfolioInsight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio C.1 cleanup: insight cards show the finding first, amounts with the reporting currency, and the rest behind "Show details". */
class PortfolioInsightCardDisplayTest {
    private fun insight(title: String, explanation: String, metrics: Map<String, String> = emptyMap(), category: InsightCategory = InsightCategory.PERFORMANCE,
                        completeness: Availability = Availability.AVAILABLE) =
        PortfolioInsight("x", category, title, explanation, metrics, null, "2026-10-08T20:00:00Z", completeness, "twr")

    @Test
    fun amountsGetTheReportingCurrencyAndGrouping() {
        val card = PortfolioInsightCardDisplay.card(insight("Your investments earned 4128.79 over 1Y",
            "Time-weighted return was 8.33%. There were no deposits or withdrawals in this period.",
            mapOf("investmentGain" to "4128.79", "netExternalFlows" to "0", "timeWeightedReturn" to "8.33")), "CAD")
        assertEquals("Your investments earned CAD 4,128.79 over 1Y", card.headline)
        assertEquals("Time-weighted return was 8.33%.", card.summary) // a percentage equal to nothing in the money metrics stays as it is
        assertEquals("There were no deposits or withdrawals in this period.", card.details)
    }

    @Test
    fun negativeMetricsMatchTheEngineAbsoluteAmount() {
        val text = PortfolioInsightCardDisplay.withCurrency("Over 1Y, currency movement subtracted 283.94.", mapOf("fxEffect" to "-283.94"), "USD")
        assertEquals("Over 1Y, currency movement subtracted USD 283.94.", text)
    }

    @Test
    fun onlyWholeNumbersAreReplacedAndMissingCurrencyIsNeverGuessed() {
        val metrics = mapOf("contribution" to "40.00")
        assertEquals("Up 140.00 and 40.00%", PortfolioInsightCardDisplay.withCurrency("Up 140.00 and 40.00%", metrics, "CAD"))
        assertEquals("added 40.00 to", PortfolioInsightCardDisplay.withCurrency("added 40.00 to", metrics, ""))
    }

    @Test
    fun cautionSentencesStayVisibleAndSingleSentencesHaveNoDetails() {
        val benchmark = PortfolioInsightCardDisplay.card(insight("1.1 points ahead of the S&P/TSX Composite",
            "Over 1Y, your time-weighted return was 8.33% and the S&P/TSX Composite returned 7.22% in CAD (price only, without dividends). Past differences don't indicate future results.",
            category = InsightCategory.BENCHMARK), "CAD")
        assertTrue(benchmark.summary.endsWith("Past differences don't indicate future results."))
        assertNull(benchmark.details)
        val single = PortfolioInsightCardDisplay.card(insight("A", "Only one sentence here."), "CAD")
        assertNull(single.details)
    }

    @Test
    fun sentencesNeverSplitInsideNumbersOrNames() {
        assertEquals(listOf("Return was 8.33% and the S&P/TSX rose 7.22%.", "Next one."),
            PortfolioInsightCardDisplay.sentences("Return was 8.33% and the S&P/TSX rose 7.22%. Next one."))
    }

    @Test
    fun partialDataIsNamedInTheEyebrow() {
        val card = PortfolioInsightCardDisplay.card(insight("t", "e.", completeness = Availability.PARTIAL), "CAD")
        assertTrue(card.eyebrow.endsWith("Partial data"))
    }
}
