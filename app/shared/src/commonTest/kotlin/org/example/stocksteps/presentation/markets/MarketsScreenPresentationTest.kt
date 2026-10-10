package org.example.stocksteps.presentation.markets

import kotlin.time.Instant
import org.example.stocksteps.brief.BriefEdition
import org.example.stocksteps.brief.BriefStatus
import org.example.stocksteps.brief.ConceptOfTheDay
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.markets.SectorsModel
import org.example.stocksteps.model.IndexQuote
import org.example.stocksteps.model.MarketSession
import org.example.stocksteps.model.MarketSessionStatus
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

    private fun session(status: MarketSessionStatus, nextOpenLocal: String? = "2026-10-09T09:30", holiday: String? = null) = MarketSession(
        market = "US stocks (NYSE, Nasdaq)", status = status, timezone = "America/New_York", asOf = "2026-10-08T23:46:00Z", sessionDate = "2026-10-08",
        opensAt = "09:30", closesAt = "16:00", nextOpenLocal = nextOpenLocal, utcOffsetMinutes = -240, holiday = holiday, source = "calendar")

    @Test
    fun usMarketStatusIsShortAndFromTheCalendar() {
        assertEquals("After-hours", present.statusLabel(session(MarketSessionStatus.AFTER_HOURS)))
        assertEquals("Open", present.statusLabel(session(MarketSessionStatus.OPEN)))
        assertEquals("Closed · Thanksgiving", present.statusLabel(session(MarketSessionStatus.HOLIDAY, holiday = "Thanksgiving")))
        assertEquals("Status unavailable", present.statusLabel(session(MarketSessionStatus.UNKNOWN, nextOpenLocal = null)))
        assertEquals("Next open · Fri, Oct 9 · 9:30\u00A0AM\u00A0ET", present.sessionLine(session(MarketSessionStatus.AFTER_HOURS)))
        assertNull(present.sessionLine(session(MarketSessionStatus.UNKNOWN, nextOpenLocal = null)))
    }

    @Test
    fun statusFooterIsShortButTheFullNoticeIsSpoken() {
        val sample = header()
        assertEquals("Sample quotes · Not live", present.statusFooter(sample))
        assertEquals("Updated 5:15 PM ET · Oct 7. Sample data.", present.statusFooterDescription(sample))
        val real = sample.copy(sampleData = false, notice = "Prices may be delayed up to 15 minutes.")
        assertEquals("Updated 5:15 PM ET · Oct 7", present.statusFooter(real))
        assertNull(present.statusFooter(real.copy(updated = null)))
    }

    @Test
    fun onlyUsIndicesAreListed() {
        val cards = listOf("SP500", "TSX", "DOW").map { card().copy(id = it) }
        val quotes = listOf(quote("SP500", "US"), quote("TSX", "Canada"), quote("DOW", "US"))
        assertEquals(listOf("SP500", "DOW"), present.usIndices(cards, quotes).map { it.id })
        assertEquals(emptyList(), present.usIndices(cards, emptyList()))
    }
    private fun quote(id: String, region: String) = IndexQuote(id, id, id, unit = "points", currency = "USD", region = region)

    @Test
    fun briefCardKeepsFreshnessAndLabelsSeparate() {
        val updated = "2026-10-07T21:15:00Z"
        val brief = DailyBrief(id = "b", briefDate = "2026-10-07", sessionDate = "2026-10-07", edition = BriefEdition.entries.first(),
            summaryLine = "", concept = ConceptOfTheDay("c", "t", "e", null, "w", "l"), generatedAt = updated, updatedAt = updated,
            status = BriefStatus.COMPLETE, readingMinutes = 1, sampleData = true)
        val later = Instant.parse("2026-10-09T22:00:00Z").toEpochMilliseconds()
        assertEquals("Latest available · Oct 7 · 1 min read", present.briefDetail(brief, later))
        assertEquals("Offline copy · Sample data", present.briefLabels(brief, offline = true))
        assertNull(present.briefLabels(brief.copy(sampleData = false), offline = false))
    }

    @Test
    fun sectorMethodologyStaysAvailable() {
        val sectors = SectorsModel(emptyList(), "Measured with the Select Sector SPDR ETFs.", "Change since the previous close", false, "")
        assertEquals("Change since the previous close. Measured with sector ETFs.", present.sectorsCaption(sectors))
        assertEquals("Measured with the Select Sector SPDR ETFs.", present.methodologyLesson(sectors).body)
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
        assertEquals("Upcoming earnings dates and reports", present.earningsDetail(null))
        assertEquals("Upcoming earnings dates and reports", present.earningsDetail(EarningsSummaryState(loading = true)))
        assertEquals("4 companies report in the next 7 days · next on Mon, Oct 12",
            present.earningsDetail(EarningsSummaryState(loading = false, upcomingCount = 4, nextDateText = "Mon, Oct 12")))
        assertEquals("2 on your watchlists", present.earningsSecondary(EarningsSummaryState(loading = false, watchlistCount = 2)))
        assertNull(present.earningsSecondary(EarningsSummaryState(loading = false, watchlistCount = 0)))
    }
}
