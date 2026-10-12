package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioFixtureCatalog
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.theme.StockChartLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Portfolio Phase B.1 polish: account labels, total labels, sample copy and scenario groups, chart height. */
class PortfolioPhaseB1Test {
    @Test
    fun accountLabelDoesNotRepeatTheReportingCurrency() {
        assertEquals("TFSA CAD", PortfolioPresentation.accountLabel(PortfolioAccount("a", "TFSA CAD", PortfolioCategory.TFSA, PortfolioCurrency.CAD)))
        assertEquals("Personal (usd)", PortfolioPresentation.accountLabel(PortfolioAccount("b", "Personal (usd)", reportingCurrency = PortfolioCurrency.USD)))
        // Different currencies stay visible, and a name without a currency keeps the suffix.
        assertEquals("US savings USD · CAD", PortfolioPresentation.accountLabel(PortfolioAccount("c", "US savings USD", reportingCurrency = PortfolioCurrency.CAD)))
        assertEquals("Personal · CAD", PortfolioPresentation.accountLabel(PortfolioAccount("d", "Personal")))
        // A currency inside another word isn't a currency mention.
        assertEquals("Cadence · CAD", PortfolioPresentation.accountLabel(PortfolioAccount("e", "Cadence")))
    }

    @Test
    fun selectedAndAllAccountsTotalsAreNamedApart() {
        val state = PortfolioUiState(currency = "CAD", total = "1000", combinedTotal = "1370.37")
        val one = PortfolioOverviewDisplay.of(state, 1)
        assertEquals("Total value", one.totalLabel)
        val many = PortfolioOverviewDisplay.of(state, 2)
        assertEquals("Selected account total", many.totalLabel)
        assertEquals("All accounts total · CAD 1,370.37", many.allAccounts)
        assertTrue(many.spoken.startsWith("Selected account total CAD 1,000.00"))
        assertTrue("All accounts total CAD 1,370.37" in many.spoken)
    }

    @Test
    fun sampleCopyIsShortButTheFullExplanationStays() {
        assertTrue(PortfolioDisplay.SAMPLE_SHORT.length < 60)
        assertTrue("Read-only sample portfolio" in PortfolioDisplay.SAMPLE_SHORT && "can't be saved" in PortfolioDisplay.SAMPLE_SHORT)
        assertTrue("stay unchanged" in PortfolioDisplay.SAMPLE_BANNER)
        assertTrue("read-only" in PortfolioDisplay.ADD_DISABLED_SAMPLE && "My saved accounts" in PortfolioDisplay.ADD_DISABLED_SAMPLE)
        assertTrue(PortfolioDisplay.CHART_NOTE_SHORT.length < PortfolioDisplay.CHART_NOTE.length)
        assertTrue("deposits" in PortfolioDisplay.CHART_NOTE_SHORT && "a rise isn't always a gain" in PortfolioDisplay.CHART_NOTE)
    }

    @Test
    fun scenarioGroupsListEveryFixtureOnceWithSavedAccountsFirst() {
        val groups = PortfolioScenarioGroups.groups(PortfolioFixtureCatalog.ids)
        assertEquals("Your portfolio", groups.first().title)
        assertEquals(listOf(PortfolioDisplay.SAVED_ACCOUNTS), groups.first().ids)
        val all = groups.flatMap { it.ids }
        assertEquals(all.size, all.toSet().size)
        assertEquals((listOf(PortfolioDisplay.SAVED_ACCOUNTS) + PortfolioFixtureCatalog.ids).toSet(), all.toSet())
        assertTrue(groups.none { it.title == "Other" }, "every current fixture has a named group")
        assertTrue(groups.none { it.ids.isEmpty() })
        // An unknown future id is never hidden.
        assertEquals(listOf("brand-new"), PortfolioScenarioGroups.groups(listOf("brand-new")).last { it.title == "Other" }.ids)
    }

    @Test
    fun unavailableNoticeUsesWordsNotEngineCodes() {
        assertEquals("Unavailable: USD to CAD exchange rate on Sep 1, 2026 (for cost), USD to CAD exchange rate",
            PortfolioOverviewDisplay.noticeText("Unavailable: FX:basis:USD:2026-09-01, FX:USD/CAD", "CAD"))
        assertEquals("Unavailable: AAPL price, TD.TO price", PortfolioOverviewDisplay.noticeText("Unavailable: AAPL, TD.TO", "CAD"))
        // Unknown shapes are shown as given; other notices untouched; missing stays missing.
        assertEquals("Unavailable: FX:odd", PortfolioOverviewDisplay.noticeText("Unavailable: FX:odd", "CAD"))
        assertEquals("Something else", PortfolioOverviewDisplay.noticeText("Something else", "CAD"))
        assertEquals(null, PortfolioOverviewDisplay.noticeText(null, "CAD"))
        assertEquals("Unavailable: AAPL price", PortfolioOverviewDisplay.of(PortfolioUiState(notice = "Unavailable: AAPL"), 1).notice)
    }

    @Test
    fun chartHeightFollowsTheAvailableWidth() {
        assertEquals(133.2f, StockChartLayout.height(296f), 0.01f)
        assertEquals(StockChartLayout.MIN_HEIGHT, StockChartLayout.height(200f))
        assertEquals(StockChartLayout.MAX_HEIGHT, StockChartLayout.height(900f))
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected, was $actual")
}
