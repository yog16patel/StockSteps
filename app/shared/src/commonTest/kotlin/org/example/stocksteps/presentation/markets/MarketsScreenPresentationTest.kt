package org.example.stocksteps.presentation.markets

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.earnings.EarningsSummaryState
import org.example.stocksteps.markets.IndexCardModel
import org.example.stocksteps.markets.MarketHeaderModel
import org.example.stocksteps.markets.SessionTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarketsScreenPresentationTest {
    private val present = MarketsScreenPresentation
    private fun card(change: String? = "-18.59", updated: String? = "As of 4:00 PM ET · Oct 7", proxy: String? = null) =
        IndexCardModel("sp500", "S&P 500", "7,772.36", change, "-0.24%", PriceDirection.DOWN, proxy, emptyList(), updated, true, "")
    private fun header(updated: String? = "Updated 5:15 PM ET · Oct 7") =
        MarketHeaderModel("After-hours", SessionTone.EXTENDED, "Opens Thu, Oct 8 at 9:30 AM ET", updated, "Sample data.", true)

    @Test
    fun closedIsNeutralNotALoss() {
        assertEquals(MarketsScreenPresentation.StatusDot.OPEN, present.statusDot(SessionTone.OPEN))
        assertEquals(MarketsScreenPresentation.StatusDot.EXTENDED, present.statusDot(SessionTone.EXTENDED))
        assertEquals(MarketsScreenPresentation.StatusDot.NEUTRAL, present.statusDot(SessionTone.CLOSED))
        assertEquals(MarketsScreenPresentation.StatusDot.NEUTRAL, present.statusDot(SessionTone.UNKNOWN))
    }

    @Test
    fun statusMetaNamesTheMarketAndSourceTime() {
        assertEquals("US stocks (NYSE, Nasdaq) · Updated 5:15 PM ET · Oct 7", present.statusMeta(header(), "US stocks (NYSE, Nasdaq)"))
        assertEquals("Updated 5:15 PM ET · Oct 7", present.statusMeta(header(), " "))
        assertNull(present.statusMeta(header(updated = null), null))
    }

    @Test
    fun indexChangeAndMetaComeFromTheModel() {
        assertEquals("-18.59 (-0.24%)", present.indexChange(card()))
        assertEquals("-0.24%", present.indexChange(card(change = null)))
        assertEquals("As of 4:00 PM ET · Oct 7 · Proxy: SPY fund price", present.indexMeta(card(proxy = "Proxy: SPY fund price")))
        assertNull(present.indexMeta(card(updated = null)))
    }

    @Test
    fun trendOnlyWhenTheCardIsWideAndHasHistory() {
        val withTrend = card().copy(trend = listOf(1.0, 2.0))
        assertEquals(true, present.showTrend(400f, withTrend))
        assertEquals(false, present.showTrend(361f, withTrend))
        assertEquals(false, present.showTrend(400f, card().copy(trend = listOf(1.0))))
        assertEquals(false, present.showTrend(400f, withTrend.copy(available = false)))
    }

    @Test
    fun earningsToolUsesCalendarCountsOnly() {
        assertEquals("See when companies are reporting results", present.earningsDetail(null))
        assertEquals("See when companies are reporting results", present.earningsDetail(EarningsSummaryState(loading = true)))
        assertEquals("4 companies report in the next 7 days · next on Mon, Oct 12",
            present.earningsDetail(EarningsSummaryState(loading = false, upcomingCount = 4, nextDateText = "Mon, Oct 12")))
        assertEquals("2 on your watchlists", present.earningsSecondary(EarningsSummaryState(loading = false, watchlistCount = 2)))
        assertNull(present.earningsSecondary(EarningsSummaryState(loading = false, watchlistCount = 0)))
    }
}
