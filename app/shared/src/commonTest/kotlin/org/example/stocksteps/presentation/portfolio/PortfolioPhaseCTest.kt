package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.PortfolioAllocation
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioCurrencyAllocation
import org.example.stocksteps.portfolio.PortfolioHoldingRow
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.analytics.InsightCategory
import org.example.stocksteps.theme.StockAllocationLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase C: holdings display + sort, allocation donut/legend, holding details, insight icons, donut geometry. */
class PortfolioPhaseCTest {
    private fun row(symbol: String, name: String, currency: String = "USD", value: String? = "1000", gain: String? = "200", percent: String? = "25") =
        PortfolioHoldingRow(symbol, name, "NASDAQ", currency, "10", "100", value, "800", gain, percent, "0", "0", null, stale = false)

    private val aapl = row("AAPL", "Apple")
    private val msft = row("MSFT", "Microsoft", value = "3000", gain = "-50", percent = "-1.64")
    private val td = row("TD.TO", "Toronto-Dominion Bank", currency = "CAD", value = "500", gain = null, percent = null)
    private val state = PortfolioUiState(currency = "CAD", holdings = listOf(aapl, msft, td),
        allocations = listOf(
            PortfolioAllocation("AAPL", PortfolioCurrency.USD, "1350", "24.32"),
            PortfolioAllocation("MSFT", PortfolioCurrency.USD, "4050", "72.97"),
            PortfolioAllocation("TD.TO", PortfolioCurrency.CAD, "150", "2.71")),
        currencyAllocations = listOf(PortfolioCurrencyAllocation(PortfolioCurrency.USD, "5400", "97.29"), PortfolioCurrencyAllocation(PortfolioCurrency.CAD, "150", "2.71")))

    // 1 · donut eligibility
    @Test
    fun donutOnlyForValidAllocations() {
        assertTrue(PortfolioAllocationDisplay.of(state).donut)
        val over = state.copy(allocations = listOf(PortfolioAllocation("AAPL", PortfolioCurrency.USD, "1500", "110.5")))
        assertFalse(PortfolioAllocationDisplay.of(over).donut)
        val negative = state.copy(allocations = state.allocations + PortfolioAllocation("CASH", PortfolioCurrency.CAD, "-10", "-1"))
        assertFalse(PortfolioAllocationDisplay.of(negative).donut)
        val missing = state.copy(allocations = listOf(PortfolioAllocation("AAPL", PortfolioCurrency.USD, null, null)))
        assertFalse(PortfolioAllocationDisplay.of(missing).donut)
        assertFalse(PortfolioAllocationDisplay.of(state.copy(allocations = emptyList())).donut)
        assertTrue(PortfolioAllocationDisplay.of(state.copy(allocations = listOf(PortfolioAllocation("AAPL", PortfolioCurrency.USD, "1350", "100")))).donut)
    }

    // 2 · invalid allocation → bars with the real values (never normalised)
    @Test
    fun invalidAllocationKeepsReportedValuesForBars() {
        val view = PortfolioAllocationDisplay.of(state.copy(allocations = listOf(PortfolioAllocation("AAPL", PortfolioCurrency.USD, "1500", "110.5"))))
        assertFalse(view.donut)
        assertNull(view.donutDescription)
        assertEquals("110.50%", view.holdings.single().percent)
        assertEquals(1f, view.holdings.single().fraction)   // bar clamped for drawing only
        assertEquals("—", PortfolioAllocationDisplay.of(state.copy(allocations = listOf(PortfolioAllocation("X", PortfolioCurrency.CAD, null, null)))).holdings.single().percent)
    }

    // 3 · accessible legend labels (category, percent, amount)
    @Test
    fun legendEntriesAreSpokenInFull() {
        val view = PortfolioAllocationDisplay.of(state)
        assertEquals("MSFT, USD, 72.97 percent of account value, CAD 4,050.00", view.holdings[1].spoken)
        assertEquals("MSFT · USD", view.holdings[1].label)
        assertEquals(listOf(0, 1, 2), view.holdings.map { it.colorIndex })
        assertEquals("USD, 97.29 percent of account value, CAD 5,400.00", view.currencies[0].spoken)
        assertEquals("X, CAD, percentage unavailable", PortfolioAllocationDisplay.of(state.copy(allocations = listOf(PortfolioAllocation("X", PortfolioCurrency.CAD, null, null)))).holdings.single().spoken)
        assertTrue(view.donutDescription!!.startsWith("Allocation by holding: AAPL, USD, 24.32 percent"))
    }

    // 4 · stable, display-only sorting
    @Test
    fun sortingIsDisplayOnlyAndStable() {
        assertEquals(listOf("AAPL", "MSFT", "TD.TO"), PortfolioHoldingsDisplay.items(state, null).map { it.symbol })
        // VALUE by reporting-currency value (CAD 4,050 > 1,350 > 150), not native amounts.
        assertEquals(listOf("MSFT", "AAPL", "TD.TO"), PortfolioHoldingsDisplay.items(state, PortfolioHoldingSort.VALUE).map { it.symbol })
        assertEquals(listOf("AAPL", "MSFT", "TD.TO"), PortfolioHoldingsDisplay.items(state, PortfolioHoldingSort.NAME).map { it.symbol })
        assertEquals(listOf("AAPL", "MSFT", "TD.TO"), state.holdings.map { it.symbol }, "state untouched")
        val tie = state.copy(holdings = listOf(row("B", "B", percent = "5"), row("A", "A", percent = "5")))
        assertEquals(listOf("B", "A"), PortfolioHoldingsDisplay.items(tie, PortfolioHoldingSort.CHANGE).map { it.symbol })
        assertEquals("Sort: Value", PortfolioHoldingsDisplay.sortLabel(PortfolioHoldingSort.VALUE))
        assertEquals("Sort: Default", PortfolioHoldingsDisplay.sortLabel(null))
    }

    // 5 · missing change percentage
    @Test
    fun changeFallsBackToAmountThenUnavailable() {
        val items = PortfolioHoldingsDisplay.items(state, PortfolioHoldingSort.CHANGE)
        assertEquals(listOf("AAPL", "MSFT", "TD.TO"), items.map { it.symbol }, "missing change % stays last")
        assertEquals("+25.00%", items[0].change)
        assertEquals("-1.64%", items[1].change)
        assertEquals(PriceDirection.DOWN, items[1].direction)
        assertNull(items[2].change)
        assertEquals(PriceDirection.UNAVAILABLE, items[2].direction)
        assertEquals("+200.00", PortfolioHoldingsDisplay.item(row("Z", "Z", percent = null), emptyList()).change)
    }

    // 6 · optional weight, only from a single matching server allocation
    @Test
    fun weightComesOnlyFromAnUnambiguousAllocation() {
        assertEquals("72.97% of account value", PortfolioHoldingsDisplay.weight("MSFT", state.allocations))
        assertNull(PortfolioHoldingsDisplay.weight("NVDA", state.allocations))
        assertNull(PortfolioHoldingsDisplay.weight("AAPL", state.allocations + PortfolioAllocation("AAPL", PortfolioCurrency.CAD, "10", "1")))
        assertNull(PortfolioHoldingsDisplay.weight("AAPL", listOf(PortfolioAllocation("AAPL", PortfolioCurrency.USD, null, null))))
        val item = PortfolioHoldingsDisplay.item(msft, state.allocations)
        assertTrue(item.spoken.endsWith("72.97% of account value"))
    }

    // 7 · missing values stay missing in holding details
    @Test
    fun holdingDetailsKeepMissingValuesMissing() {
        val metrics = PortfolioHoldingsDisplay.detailMetrics(row("X", "X", value = null, gain = null, percent = null).copy(price = null))
        val byLabel = metrics.associate { it.label to it.value }
        assertEquals(listOf("Shares", "Price", "Market value", "Cost", "Unrealized P/L", "Realized P/L", "Dividends"), metrics.map { it.label })
        assertEquals("USD —", byLabel["Price"])
        assertEquals("USD —", byLabel["Market value"])
        assertEquals("USD —", byLabel["Unrealized P/L"])
        assertEquals("Unrealized P/L unavailable", metrics[4].spoken)
        val full = PortfolioHoldingsDisplay.detailMetrics(aapl).associate { it.label to it.value }
        assertEquals("+USD 200.00 (+25.00%)", full["Unrealized P/L"])
        assertEquals("USD 800.00", full["Cost"])
        assertEquals("10", full["Shares"])
    }

    // 8 · responsive / geometry rules
    @Test
    fun donutGeometryAndLegendPlacement() {
        assertEquals(122.4f, StockAllocationLayout.diameter(360f), 0.01f)
        assertEquals(96f, StockAllocationLayout.diameter(200f), 0.01f)
        assertEquals(160f, StockAllocationLayout.diameter(900f), 0.01f)
        assertTrue(StockAllocationLayout.legendBeside(328f, 150f))
        assertFalse(StockAllocationLayout.legendBeside(328f, 260f), "large text → legend below")
        assertFalse(StockAllocationLayout.legendBeside(0f, 10f))
        val slices = StockAllocationLayout.slices(listOf(0.5f, 0.25f, 0.001f))
        assertEquals(-90f, slices[0].first, 0.01f)
        assertEquals(178f, slices[0].second, 0.01f)        // 180° minus the gap
        assertEquals(90f, slices[1].first, 0.01f)
        assertEquals(StockAllocationLayout.MIN_SWEEP_DEGREES, slices[2].second, 0.01f)  // tiny slice stays visible
        assertEquals(360f, StockAllocationLayout.slices(listOf(1f)).single().second, 0.01f)  // single slice: full ring, no gap
        assertTrue(StockAllocationLayout.slices(emptyList()).isEmpty())
    }

    // 9 · insight category icons/labels are display-only and cover every category
    @Test
    fun everyInsightCategoryHasAnIconAndReadableLabel() {
        InsightCategory.entries.forEach { c ->
            PortfolioInsightIcons.icon(c)
            assertFalse('_' in PortfolioInsightIcons.label(c) || PortfolioInsightIcons.label(c) == c.name, "$c")
        }
        assertEquals("Concentration", PortfolioInsightIcons.label(InsightCategory.CONCENTRATION))
    }

    // 10 · reporting-currency amounts in the legend; native value on the holding row
    @Test
    fun currenciesFollowExistingRules() {
        val view = PortfolioAllocationDisplay.of(state)
        assertEquals("CAD 1,350.00", view.holdings[0].amount)
        val aaplItem = PortfolioHoldingsDisplay.item(aapl, state.allocations)
        assertEquals("USD 1,000.00", aaplItem.value)
        assertEquals("AAPL · NASDAQ · 10\u00A0shares", aaplItem.subtitle)
        assertNull(PortfolioAllocationDisplay.of(state.copy(currencyAllocations = listOf(PortfolioCurrencyAllocation(PortfolioCurrency.USD, null, null)))).currencies.single().amount)
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float, message: String? = null) =
        assertTrue(abs(expected - actual) <= tolerance, message ?: "expected $expected, was $actual")
}
