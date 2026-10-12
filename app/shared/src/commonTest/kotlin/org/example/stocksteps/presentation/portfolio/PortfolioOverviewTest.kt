package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.PortfolioValuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase B overview card: states, copy and formatting from existing presenter fields only. */
class PortfolioOverviewTest {
    private fun day(date: String, value: String?, currency: PortfolioCurrency = PortfolioCurrency.CAD) =
        PortfolioValuation(date, currency, value, null, null, null)

    private val base = PortfolioUiState(currency = "CAD", total = "2145.53", basis = "16.2", unrealized = "2129.33", realized = "0",
        dailyGain = "12.5", dailyPercent = "0.68", signedIn = true)
    private val rising = listOf(day("2026-09-08", "2000"), day("2026-09-09", "2050.5"), day("2026-10-08", "2145.53"))

    // 1 · history not requested
    @Test
    fun noPeriodChosenMeansNotRequested() {
        assertEquals(PortfolioHistoryState.NOT_REQUESTED, PortfolioOverviewDisplay.historyState(base, requested = null))
        assertEquals("", PortfolioOverviewDisplay.selectedPeriod(base, requested = null))
    }

    // 2 · Show 1M requests the 1M period through the existing loader
    @Test
    fun showActionRequestsOneMonth() {
        assertEquals("1M", PortfolioOverviewDisplay.SHOW_PERIOD)
        assertTrue(PortfolioOverviewDisplay.SHOW_PERIOD in PortfolioOverviewDisplay.PERIODS)
        assertEquals("Show 1M", PortfolioOverviewDisplay.SHOW_ACTION)
        val loading = base.copy(historyLoading = true, historyRange = "1M")
        assertEquals(PortfolioHistoryState.LOADING, PortfolioOverviewDisplay.historyState(loading, requested = "1M"))
        assertEquals("1M", PortfolioOverviewDisplay.selectedPeriod(loading, requested = "1M"))
    }

    // 3 · switching periods
    @Test
    fun selectedPillFollowsTheLoadedThenTheRequestedPeriod() {
        assertEquals(listOf("1D", "1W", "1M", "3M", "1Y", "ALL"), PortfolioOverviewDisplay.PERIODS)
        val loaded = base.copy(history = rising, historyRange = "1M")
        assertEquals("1M", PortfolioOverviewDisplay.selectedPeriod(loaded, requested = "1M"))
        // Switching to 3M: the presenter clears the old report and loads → the pill moves to 3M while loading.
        val switching = base.copy(historyLoading = true, historyRange = "3M")
        assertEquals("3M", PortfolioOverviewDisplay.selectedPeriod(switching, requested = "3M"))
        // A requested period that came back empty keeps its pill selected and says so.
        val empty = base.copy(historyRange = "1D")
        assertEquals("1D", PortfolioOverviewDisplay.selectedPeriod(empty, requested = "1D"))
        assertEquals(PortfolioHistoryState.EMPTY, PortfolioOverviewDisplay.historyState(empty, requested = "1D"))
        // History kept by the shared presenter from an earlier visit counts as loaded even before a choice on this screen.
        assertEquals(PortfolioHistoryState.LOADED, PortfolioOverviewDisplay.historyState(loaded, requested = null))
    }

    // 4 · flat series
    @Test
    fun flatSeriesIsSummarisedNotDrawn() {
        val flat = base.copy(history = listOf(day("2026-09-01", "1850"), day("2026-09-15", null), day("2026-10-08", "1850.00")))
        assertEquals(PortfolioHistoryState.FLAT, PortfolioOverviewDisplay.historyState(flat, "ALL"))
        assertEquals("Unchanged at CAD\u00A01,850.00 · Sep\u00A01,\u00A02026 – Oct\u00A08,\u00A02026", PortfolioOverviewDisplay.flatSummary(flat))
        assertFalse(PortfolioOverviewDisplay.flat(rising))
        assertEquals(listOf("2,145.53", "2,000.00"), PortfolioOverviewDisplay.yLabels(rising))
    }

    // 5 · insufficient and missing history
    @Test
    fun insufficientMissingAndGapsAreNamed() {
        val one = base.copy(history = listOf(day("2026-10-08", "1850"), day("2026-10-07", null)))
        assertEquals(PortfolioHistoryState.INSUFFICIENT, PortfolioOverviewDisplay.historyState(one, "1W"))
        assertEquals("Only one dated value so far: Oct 8, 2026 · CAD 1,850.00", PortfolioOverviewDisplay.insufficientSummary(one))
        val none = base.copy(history = listOf(day("2026-10-08", null)))
        assertEquals(PortfolioHistoryState.INSUFFICIENT, PortfolioOverviewDisplay.historyState(none, "1D"))
        assertEquals("No dated values in this period.", PortfolioOverviewDisplay.insufficientSummary(none))
        val gaps = base.copy(history = rising + day("2026-10-09", null))
        assertEquals(PortfolioHistoryState.LOADED, PortfolioOverviewDisplay.historyState(gaps, "1M"))
        assertTrue(PortfolioOverviewDisplay.hasGaps(gaps.history))
        assertFalse(PortfolioOverviewDisplay.hasGaps(rising))
        assertEquals("Oct 9, 2026 · CAD Unavailable", PortfolioOverviewDisplay.pointDetail(day("2026-10-09", null)))
        // A failed request is an error with Retry, never an empty chart.
        assertEquals(PortfolioHistoryState.ERROR, PortfolioOverviewDisplay.historyState(base.copy(error = "Market valuation unavailable."), "1M"))
        assertEquals(PortfolioHistoryState.NOT_REQUESTED, PortfolioOverviewDisplay.historyState(base.copy(error = "Offline"), null))
    }

    // 6 · missing total / daily change
    @Test
    fun missingTotalAndDailyChangeStayUnavailable() {
        val o = PortfolioOverviewDisplay.of(base.copy(total = null, dailyGain = null, dailyPercent = null, basis = null, unrealized = null, realized = null), 1)
        assertEquals("CAD —", o.total)
        assertFalse(o.totalAvailable)
        assertNull(o.today)
        assertEquals(PriceDirection.UNAVAILABLE, o.todayDirection)
        assertEquals(listOf("CAD —", "CAD —", "CAD —"), o.metrics.map { it.value })
        assertTrue("Total value unavailable" in o.spoken && "Today's change unavailable" in o.spoken)
        // A daily amount without a percentage shows the amount only (no invented percentage).
        assertEquals("+CAD 12.50", PortfolioOverviewDisplay.of(base.copy(dailyPercent = null), 1).today)
    }

    // 7 · currency on every metric, from the account's reporting currency
    @Test
    fun everyAmountCarriesTheReportingCurrency() {
        val o = PortfolioOverviewDisplay.of(base, 1)
        assertEquals("CAD 2,145.53", o.total)
        assertEquals("+CAD 12.50 (+0.68%)", o.today)
        assertEquals(listOf("Invested cost" to "CAD 16.20", "Unrealized P/L" to "+CAD 2,129.33", "Realized P/L" to "CAD 0.00"),
            o.metrics.map { it.label to it.value })
        val usd = PortfolioOverviewDisplay.of(base.copy(currency = "USD"), 1)
        assertTrue(usd.total.startsWith("USD ") && usd.metrics.all { "USD" in it.value } && "USD" in usd.today!!)
    }

    // 8 · negative and zero amounts
    @Test
    fun negativeAndZeroAmounts() {
        val o = PortfolioOverviewDisplay.of(base.copy(unrealized = "-125", realized = "0.00", dailyGain = "-3.2", dailyPercent = "-0.15"), 1)
        assertEquals("-CAD 125.00", o.metrics[1].value)
        assertEquals(PriceDirection.DOWN, o.metrics[1].direction)
        assertEquals("Unrealized P/L loss of CAD 125.00", o.metrics[1].spoken)
        assertEquals("CAD 0.00", o.metrics[2].value)
        assertEquals(PriceDirection.UNCHANGED, o.metrics[2].direction)
        assertEquals("-CAD 3.20 (-0.15%)", o.today)
        assertEquals(PriceDirection.DOWN, o.todayDirection)
        val zero = PortfolioOverviewDisplay.of(base.copy(dailyGain = "0", dailyPercent = "0"), 1)
        assertEquals("CAD 0.00 (0.00%)", zero.today)
        assertEquals(PriceDirection.UNCHANGED, zero.todayDirection)
    }

    // 9 · all accounts
    @Test
    fun allAccountsLineOnlyWithSeveralAccounts() {
        assertNull(PortfolioOverviewDisplay.of(base.copy(combinedTotal = "5000"), 1).allAccounts)
        assertEquals("All accounts total · CAD\u00A05,000.00", PortfolioOverviewDisplay.of(base.copy(combinedTotal = "5000"), 2).allAccounts)
        val missing = PortfolioOverviewDisplay.of(base.copy(combinedTotal = null), 3)
        assertEquals("All accounts total · CAD\u00A0—", missing.allAccounts)
        assertTrue("All accounts total unavailable" in missing.spoken)
    }

    // 10 · sample scenario
    @Test
    fun sampleScenarioIsLabelledAndItsHistoryIsShown() {
        val sample = base.copy(mockAvailable = true, mockScenario = "mixed-currencies", busy = true, history = rising, historyRange = "ALL")
        val o = PortfolioOverviewDisplay.of(sample, 1)
        assertEquals("Sample", o.sampleBadge)
        assertEquals("Scenario: Mixed currencies", o.sampleScenario)
        assertTrue(o.spoken.startsWith("Read-only sample. Scenario: Mixed currencies."))
        assertEquals(PortfolioHistoryState.LOADED, PortfolioOverviewDisplay.historyState(sample, requested = null))
        assertEquals("ALL", PortfolioOverviewDisplay.selectedPeriod(sample, requested = null))
        assertNull(PortfolioOverviewDisplay.of(base.copy(mockAvailable = true), 1).sampleBadge)
        assertEquals("Mixed currencies", PortfolioDisplay.scenarioLabel("mixed-currencies"))
        assertEquals("Missing FX", PortfolioDisplay.scenarioLabel("missing-fx"))
        assertEquals(PortfolioDisplay.SAVED_ACCOUNTS, PortfolioDisplay.scenarioLabel(PortfolioDisplay.SAVED_ACCOUNTS))
    }

    // 11 · accessibility descriptions
    @Test
    fun spokenSummariesSayDirectionInWords() {
        val o = PortfolioOverviewDisplay.of(base, 1)
        assertEquals("Total value CAD 2,145.53. Today's change up CAD 12.50 (+0.68%).", o.spoken)
        assertEquals(listOf("Invested cost CAD 16.20", "Unrealized P/L gain of CAD 2,129.33", "Realized P/L CAD 0.00, no gain or loss"),
            o.metrics.map { it.spoken })
        assertEquals("Account value in CAD, including cash. Sep 8, 2026 · CAD 2,000.00. Oct 8, 2026 · CAD 2,145.53. Missing observations are gaps.",
            PortfolioOverviewDisplay.chartDescription(base.copy(history = rising)))
        // The chart is never called performance.
        assertFalse("performance" in PortfolioDisplay.CHART_TITLE.lowercase())
        assertTrue("deposits" in PortfolioDisplay.CHART_NOTE)
    }

    // 12 · Phase A regression: same-named accounts still resolve by id
    @Test
    fun phaseAAccountSelectionStillById() {
        val a = PortfolioAccount("a", "TFSA")
        val b = PortfolioAccount("b", "TFSA")
        assertEquals(listOf("a", "b"), PortfolioFormRules.accountOptions(listOf(a, b)).map { it.id })
        assertEquals("b", PortfolioFormRules.resolveAccountId("b", listOf(a, b), default = "a"))
    }
}
