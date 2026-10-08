package org.example.stocksteps.watchlist

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.*
import kotlin.test.*

class WatchlistPresenterTest {
    private val session = MarketSession("US", MarketSessionStatus.CLOSED, "America/New_York", "2026-10-07T21:15:00Z", sessionDate = "2026-10-07", utcOffsetMinutes = -240, source = "c")
    private fun entry(symbol: String, note: String? = null) = WatchlistEntry("e-$symbol", InstrumentRef(symbol, "$symbol Inc.", currency = "USD"), 0, 0, note)
    private fun quote(symbol: String, change: Double?, session: String = "2026-10-07", stale: Boolean = false, currency: String = "USD") =
        WatchQuote(symbol, "$symbol Corp", 100.0, null, change, 99.0, currency, "2026-10-07T20:00:00Z", session, stale)
    private val list = Watchlist("default", "My Stocks", 0, 0, 0, true, listOf(entry("AAA", "Why I'm watching:\nmore"), entry("BBB"), entry("CCC"), entry("DDD"), entry("EEE")))
    private fun data(vararg quotes: WatchQuote, earnings: List<UpcomingEarnings> = emptyList()) = WatchDataResponse(quotes.toList(), earnings, session, "2026-10-07T21:15:00Z", "n")

    @Test fun equalWeightedSummaryUsesOnlyFreshSameSessionQuotes() {
        val model = WatchlistPresenter.build(listOf(list), null, data(
            quote("AAA", 2.0), quote("BBB", -1.0), quote("CCC", 5.0, stale = true), quote("DDD", null), quote("EEE", 9.0, session = "2026-10-06")
        ), emptyList(), null)
        val summary = model.summary!!
        assertEquals("+0.50% today", summary.change, "(2 + -1) / 2; stale, missing and older-session quotes excluded")
        assertEquals("Equal-weighted average of 2 of 5 stocks · not a portfolio return", summary.methodology)
        assertEquals("5 companies", summary.companies)
        assertEquals("Updated 5:15 PM ET", summary.updated)
        assertTrue(summary.stale)
    }

    @Test fun rowsShowCurrencyStalenessAlertsAndNotePreview() {
        val alerts = listOf(AlertRule("a1", InstrumentRef("AAA"), AlertType.PRICE_ABOVE, 1.0, "USD", status = AlertStatus.ACTIVE, createdAt = 0, updatedAt = 0),
            AlertRule("a2", InstrumentRef("AAA"), AlertType.NEWS, status = AlertStatus.PAUSED, createdAt = 0, updatedAt = 0))
        val rows = WatchlistPresenter.build(listOf(list), "default", data(quote("AAA", 1.234), quote("CCC", 1.0, stale = true)), alerts, null).rows
        val aaa = rows.first()
        assertEquals("$100.00", aaa.price); assertEquals("USD", aaa.currency); assertEquals("+1.23%", aaa.change); assertEquals(PriceDirection.UP, aaa.direction)
        assertEquals("Why I'm watching:", aaa.notePreview); assertEquals(1, aaa.activeAlerts, "paused alerts don't count")
        assertNull(aaa.staleLabel)
        assertEquals("Delayed · as of Oct 7", rows.first { it.symbol == "CCC" }.staleLabel)
        val missing = rows.first { it.symbol == "BBB" }
        assertNull(missing.price); assertEquals(PriceDirection.UNAVAILABLE, missing.direction); assertEquals("BBB Inc.", missing.name)
        val offline = WatchlistPresenter.build(listOf(list), "default", data(quote("AAA", 1.0)), emptyList(), offlineSince = 1).rows.first()
        assertEquals("Saved prices · as of Oct 7", offline.staleLabel)
    }

    @Test fun insightsOnlyStateWhatTheDataSupports() {
        val insights = WatchlistPresenter.build(listOf(list), null, data(quote("AAA", 2.0), quote("BBB", -1.5), quote("CCC", 0.5),
            earnings = listOf(UpcomingEarnings("BBB", "2026-10-15", status = EarningsDateStatus.ESTIMATED, source = "s"), UpcomingEarnings("CCC", "2026-12-01", source = "s"))), emptyList(), null).insights
        assertEquals(listOf("2 stocks rose and 1 stock fell today.", "AAA had the largest gain (+2.00%).", "BBB had the largest decline (-1.50%).",
            "BBB Corp reports earnings on Oct 15 (estimated date)."), insights)
        assertTrue(WatchlistPresenter.build(listOf(list), null, data(quote("AAA", 2.0)), emptyList(), null).insights.isEmpty(), "one stock: no comparison")
    }

    @Test fun emptyAndMultipleLists() {
        val growth = Watchlist("g", "Growth", 1, 0, 0)
        val model = WatchlistPresenter.build(listOf(list, growth), "g", null, emptyList(), null)
        assertEquals(listOf(false, true), model.tabs.map { it.selected })
        assertTrue(model.emptyMessage!!.startsWith("No stocks in Growth"))
        assertNull(model.summary!!.change)
        assertEquals("default", WatchlistPresenter.build(listOf(list, growth), "deleted", null, emptyList(), null).selectedId, "a missing selection falls back to the default list")
    }

    @Test fun alertCardsAndHistoryLabels() {
        val rules = listOf(
            AlertRule("p", InstrumentRef("AAPL"), AlertType.PRICE_ABOVE, 250.0, "USD", status = AlertStatus.ACTIVE, armed = false, createdAt = 1_791_403_200_000, updatedAt = 0),
            AlertRule("m", InstrumentRef("NVDA"), AlertType.DAILY_MOVE, 5.0, direction = MoveDirection.EITHER, repeat = RepeatPolicy.REPEAT, status = AlertStatus.TRIGGERED, createdAt = 0, updatedAt = 0, lastTriggeredAt = 1_791_403_200_000),
            AlertRule("e", InstrumentRef("MSFT"), AlertType.EARNINGS, earningsTiming = EarningsTiming.DAY_BEFORE, status = AlertStatus.PAUSED, createdAt = 0, updatedAt = 0)
        )
        val active = AlertPresenter.cards(rules, AlertFilter.ACTIVE, -240).single()
        assertEquals("Price above $250.00 USD", active.title); assertEquals("Waiting for the price to cross", active.statusLabel); assertTrue(active.detail.startsWith("Created Oct 7"))
        val triggered = AlertPresenter.cards(rules, AlertFilter.TRIGGERED, -240).single()
        assertEquals("Daily move of ±5.0%", triggered.title); assertTrue(triggered.detail.contains("last triggered Oct 7 at 4:00 PM ET"))
        assertEquals("Earnings reminder · day before", AlertPresenter.cards(rules, AlertFilter.PAUSED, -240).single().title)
        val history = AlertPresenter.history(listOf(AlertEvent("k", "p", "AAPL", AlertType.PRICE_ABOVE, "t", "b", 1_791_403_200_000, delivery = DeliveryStatus.ACCEPTED, sourceUrl = "http://insecure")), -240).single()
        assertEquals("Sent to your devices", history.delivery); assertEquals("Oct 7, 4:00 PM ET", history.time); assertNull(history.sourceUrl)
    }
}
