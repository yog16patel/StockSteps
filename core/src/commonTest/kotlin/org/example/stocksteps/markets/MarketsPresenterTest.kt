package org.example.stocksteps.markets

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.data.RemoteMarketsRepository
import org.example.stocksteps.domain.GetMarketsOverview
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class MarketsPresenterTest {
    private val now = 1_791_408_000_000L // 2026-10-07T21:20Z

    private fun session(status: MarketSessionStatus, nextOpenLocal: String? = "2026-10-08T09:30", holiday: String? = null) = MarketSession(
        market = "US stocks", status = status, timezone = "America/New_York", asOf = "2026-10-07T21:15:00Z", sessionDate = "2026-10-07",
        opensAt = "09:30", closesAt = "16:00", nextOpenLocal = nextOpenLocal, utcOffsetMinutes = -240, holiday = holiday, source = "calendar"
    )

    private fun overview(status: MarketSessionStatus = MarketSessionStatus.AFTER_HOURS, errors: List<String> = emptyList()) = MarketsOverview(
        session = session(status),
        indices = listOf(
            IndexQuote("SP500", "S&P 500", "^GSPC", 7772.36, -18.59, -0.2386, "points", "USD", "US", asOf = "2026-10-07T20:00:00Z", trend = listOf(1.0, 2.0)),
            IndexQuote("DOW", "Dow Jones Industrial Average", "DIA", 511.02, -3.54, -0.688, "USD", "USD", "US", isProxy = true),
            IndexQuote("NASDAQ_COMPOSITE", "Nasdaq Composite", "^IXIC", unit = "points", currency = "USD", region = "US", error = ApiError("INDEX_UNAVAILABLE", "x"))
        ),
        gainers = MarketMoverList((1..8).map { MarketMover("G$it", "Gainer $it", 2.0, 0.5, 30.0 - it) }, "Universe", "Source", "Largest % gain"),
        losers = MarketMoverList(emptyList(), "Universe", "Source", "Largest % decline"),
        mostActive = MarketMoverList(listOf(MarketMover("BUSY", null, 5.0, 0.0, 0.0, volume = 1_250_000.0)), "Universe", "Source", "Absolute share volume"),
        sectors = SectorPerformanceSection(listOf(SectorPerformance("Technology", "XLK", "Fund", 2.4), SectorPerformance("Energy", "XLE", "Fund", -1.2),
            SectorPerformance("Utilities", "XLU", "Fund", null)), "ETF proxies", "Change since the previous close"),
        dataNotice = "Delayed", generatedAt = "2026-10-07T21:15:00Z",
        errors = errors.map { SnapshotSectionError(it, ApiError("PROVIDER_UNAVAILABLE", "x")) }
    )

    @Test fun headerUsesTheSessionAndExchangeTime() {
        val after = MarketsPresenter.header(overview())
        assertEquals("After-hours", after.statusLabel); assertEquals(SessionTone.EXTENDED, after.tone)
        assertEquals("Opens Thu, Oct 8 at 9:30 AM ET", after.detail)
        assertEquals("Updated 5:15 PM ET · Oct 7", after.updated)
        assertEquals("Closes 4:00 PM ET", MarketsPresenter.header(overview(MarketSessionStatus.OPEN)).detail)
        val holiday = MarketsPresenter.header(overview().copy(session = session(MarketSessionStatus.HOLIDAY, "2026-11-27T09:30", "Thanksgiving Day")))
        assertEquals("Closed · Thanksgiving Day", holiday.statusLabel)
        assertEquals("Opens Fri, Nov 27 at 9:30 AM ET", holiday.detail)
    }

    @Test fun indicesKeepPointsProxiesAndMissingValuesDistinct() {
        val cards = MarketsPresenter.build(overview(), MoversTab.GAINERS, false, now).indices
        assertEquals("7,772.36", cards[0].value); assertEquals("-18.59", cards[0].change); assertEquals("-0.24%", cards[0].percent)
        assertEquals(PriceDirection.DOWN, cards[0].direction); assertNull(cards[0].proxyLabel)
        assertEquals("As of 4:00 PM ET · Oct 7", cards[0].updated)
        assertEquals("$511.02", cards[1].value); assertEquals("Proxy: DIA fund price", cards[1].proxyLabel)
        assertFalse(cards[2].available); assertEquals("—", cards[2].value); assertEquals("Nasdaq Composite: unavailable", cards[2].accessibilityLabel)
    }

    @Test fun moversTabsPreviewExpandAndReportEmptyOrFailed() {
        val preview = MarketsPresenter.build(overview(), MoversTab.GAINERS, false, now).movers
        assertEquals(MarketsPresenter.PREVIEW_ROWS, preview.rows.size)
        assertEquals(8, MarketsPresenter.build(overview(), MoversTab.GAINERS, true, now).movers.rows.size)
        assertEquals("No losers to show right now.", MarketsPresenter.build(overview(), MoversTab.LOSERS, false, now).movers.emptyMessage)
        val failed = MarketsPresenter.build(overview(errors = listOf("losers")), MoversTab.LOSERS, false, now).movers
        assertTrue(failed.failed); assertNull(failed.emptyMessage)
        val active = MarketsPresenter.build(overview(), MoversTab.MOST_ACTIVE, false, now).movers.rows.single()
        assertEquals("Vol 1.3M", active.volume); assertNull(active.row.name)
    }

    @Test fun sectorsScaleBarsAndSkipMissingValues() {
        val sectors = MarketsPresenter.build(overview(), MoversTab.GAINERS, false, now).sectors
        assertEquals(listOf("Technology", "Energy"), sectors.rows.map { it.sector }, "missing returns aren't shown as 0%")
        assertEquals(1f, sectors.rows[0].fraction); assertEquals(0.5f, sectors.rows[1].fraction)
        assertEquals("+2.40%", sectors.rows[0].change)
        assertTrue(sectors.chartDescription.contains("Best: Technology +2.40%"))
    }

    @Test fun lessonsRotateDeterministicallyAndEducationExists() {
        assertEquals(MarketsPresenter.lessonFor("2026-10-07"), MarketsPresenter.lessonFor("2026-10-07"))
        assertNotEquals(MarketsPresenter.lessonFor("2026-10-07").id, MarketsPresenter.lessonFor("2026-10-08").id)
        assertTrue(MarketEducation.index("NASDAQ_COMPOSITE")!!.more.first().contains("Nasdaq-100"))
        assertTrue(MarketEducation.sector("Energy", "XLE", "ETF proxies").more.first().contains("XLE"))
        assertEquals("Oct 7", MarketsPresenter.dateLabel(1_791_403_200, -240))
        assertEquals("12:00 AM", MarketsPresenter.timeLabel(1_791_345_600, -240))
    }

    @Test fun repositoryDecodesTheOverviewAndReportsFailures() = runBlocking<Unit> {
        val body = """{"session":{"market":"US","status":"OPEN","timezone":"America/New_York","asOf":"2026-10-07T14:00:00Z","source":"c"},
            "gainers":{"universe":"u","source":"s","rankedBy":"r"},"losers":{"universe":"u","source":"s","rankedBy":"r"},
            "mostActive":{"universe":"u","source":"s","rankedBy":"r"},"sectors":{"methodology":"m","period":"p"},
            "dataNotice":"n","generatedAt":"2026-10-07T14:00:00Z","futureField":1}"""
        HttpClient(MockEngine { request ->
            assertEquals("/api/v1/markets/overview", request.url.encodedPath)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { client ->
            assertEquals(MarketSessionStatus.OPEN, GetMarketsOverview(RemoteMarketsRepository(StockStepsApi(client, "https://stocksteps.test")))().session.status)
        }
        HttpClient(MockEngine { respond("""{"code":"PROVIDER_RATE_LIMITED","message":"Try later."}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            configureStockStepsClient()
        }.use { client ->
            assertFailsWith<StockDataException> { GetMarketsOverview(RemoteMarketsRepository(StockStepsApi(client, "https://stocksteps.test")))() }
        }
    }

    @Test
    fun sessionLineUsesTheBackendCalendarOnly() {
        // After-hours on Wed Oct 7: next open from the calendar (Thu Oct 8), never from device weekday logic.
        assertEquals("Next open · Thu, Oct 8 · 9:30\u00A0AM\u00A0ET", MarketsPresenter.sessionLine(session(MarketSessionStatus.AFTER_HOURS)))
        // A holiday Monday: the calendar already skipped to Tuesday; the line just reports it.
        assertEquals("Next open · Tue, Oct 13 · 9:30\u00A0AM\u00A0ET",
            MarketsPresenter.sessionLine(session(MarketSessionStatus.HOLIDAY, nextOpenLocal = "2026-10-13T09:30", holiday = "Columbus Day")))
        assertEquals("Next open · today · 9:30\u00A0AM\u00A0ET", MarketsPresenter.sessionLine(session(MarketSessionStatus.PRE_MARKET, nextOpenLocal = "2026-10-07T09:30")))
        assertEquals("Closes · 4:00\u00A0PM\u00A0ET", MarketsPresenter.sessionLine(session(MarketSessionStatus.OPEN)))
        assertEquals("Closes early · 1:00\u00A0PM\u00A0ET",
            MarketsPresenter.sessionLine(session(MarketSessionStatus.OPEN).copy(closesAt = "13:00", earlyClose = true)))
        // Calendar unavailable or malformed: no line rather than a guess.
        assertNull(MarketsPresenter.sessionLine(session(MarketSessionStatus.UNKNOWN, nextOpenLocal = null)))
        assertNull(MarketsPresenter.sessionLine(session(MarketSessionStatus.CLOSED, nextOpenLocal = "garbage")))
    }
}
