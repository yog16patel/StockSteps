package org.example.stocksteps.home

import org.example.stocksteps.model.*
import kotlin.test.*

class PersonalDashboardRulesTest {
    private val instruments = listOf("AAPL", "MSFT", "NVDA", "META").map { InstrumentRef(it, "$it Inc", "NASDAQ", "USD") }
    private val quotes = listOf(WatchQuote("AAPL", price = 250.0, changePercent = 1.0), WatchQuote("MSFT", price = 500.0, changePercent = -4.0), WatchQuote("NVDA", price = 180.0, changePercent = 5.0))
    @Test fun significantMovesFirstPreserveSavedOrder() {
        assertEquals(listOf("MSFT", "NVDA", "AAPL"), PersonalDashboardRules.highlights(instruments, quotes, false).map { it.instrument.symbol })
    }
    @Test fun staleQuotesNeverPromotedAsTodaysMoves() {
        val rows = PersonalDashboardRules.highlights(instruments, quotes.map { it.copy(stale = true) }, false)
        assertEquals(listOf("AAPL", "MSFT", "NVDA"), rows.map { it.instrument.symbol })
        assertTrue(rows.all { it.stale })
    }
    @Test fun offlineIsExplicitAndDoesNotRankOldQuotes() {
        assertTrue(PersonalDashboardRules.highlights(instruments, quotes, true).all { it.stale })
        assertEquals("AAPL", PersonalDashboardRules.highlights(instruments, quotes, true).first().instrument.symbol)
    }
    @Test fun nullValuesDoNotBecomeZero() {
        val row = PersonalDashboardRules.highlights(instruments, emptyList(), false).first().row
        assertNull(row.price)
        assertEquals("—", row.change)
    }
    @Test fun dailyBriefOnlyFreshSavedSignificantFacts() {
        val facts = VerifiedDailyBrief().build(instruments, quotes + WatchQuote("FOREIGN", changePercent = 20.0), emptyList())
        assertEquals(listOf("NVDA", "MSFT"), facts.map { it.symbol })
        assertTrue(facts.all { "not your investment return" in it.detail })
    }
    @Test fun dailyBriefSkipsStaleAndNonFiniteMoves() {
        assertTrue(VerifiedDailyBrief().build(instruments, listOf(WatchQuote("AAPL", changePercent = Double.NaN), WatchQuote("MSFT", changePercent = 10.0, stale = true)), emptyList()).isEmpty())
    }
    @Test fun thresholdIsConfigurable() {
        assertEquals(listOf("AAPL", "MSFT", "NVDA"), PersonalDashboardRules.highlights(instruments, quotes, false, 10.0).map { it.instrument.symbol })
        assertTrue(VerifiedDailyBrief(10.0).build(instruments, quotes, emptyList()).isEmpty())
    }
    @Test fun earningsPreserveEstimatedConfirmedAndChronology() {
        val result = PersonalDashboardRules.events(listOf(
            UpcomingEarnings("NVDA", "2026-10-10", status = EarningsDateStatus.CONFIRMED, source = "calendar"),
            UpcomingEarnings("AAPL", "2026-10-09", source = "calendar"),
            UpcomingEarnings("MSFT", "2026-10-07", source = "calendar")), emptyList(), instruments.map { it.symbol }.toSet(), "2026-10-08", 0)
        assertEquals(listOf("AAPL", "NVDA"), result.map { it.symbol })
        assertTrue(result.first().detail.startsWith("Estimated"))
        assertTrue(result.last().detail.startsWith("Confirmed"))
    }
    @Test fun irrelevantEarningsAreNotShown() {
        assertTrue(PersonalDashboardRules.events(listOf(UpcomingEarnings("OTHER", "2026-10-10", source = "calendar")), emptyList(), setOf("AAPL"), "2026-10-08", 0).isEmpty())
    }
    @Test fun triggeredAlertHasWorkingAlertTargetAndFiltersFutureOld() {
        fun event(id: String, time: Long) = AlertEvent(id, "rule", "AAPL", AlertType.DAILY_MOVE, "Price alert", "Crossed threshold", time, delivery = DeliveryStatus.SIMULATED)
        val events = PersonalDashboardRules.events(emptyList(), listOf(event("valid", 999), event("future", 1001), event("old", -999999999)), setOf("AAPL"), "2026-10-08", 1000)
        assertEquals(1, events.size)
        assertTrue(events.single().alerts)
        assertEquals("AAPL", events.single().symbol)
    }
    @Test fun newsDeduplicatesAcrossSymbolsAndSortsNewest() {
        fun article(url: String, date: String) = NewsArticle("Headline", url, publishedAt = date)
        val shared = article("https://example.com/shared", "2026-10-08T15:00:00Z")
        val result = PersonalDashboardRules.stories(mapOf("AAPL" to listOf(shared), "MSFT" to listOf(shared.copy(url = shared.url + "#story"), article("https://example.com/old", "2026-10-07T15:00:00Z")), "OTHER" to listOf(article("https://example.com/foreign", "2026-10-09T15:00:00Z"))), setOf("AAPL", "MSFT"))
        assertEquals(2, result.size)
        assertEquals(shared.url, result.first().article.url)
    }
    @Test fun unsafeArticleLinksAreNotNavigable() {
        assertTrue(PersonalDashboardRules.stories(mapOf("AAPL" to listOf(NewsArticle("Bad", "javascript:alert(1)"))), setOf("AAPL")).isEmpty())
    }
    @Test fun emptyPersonalizationDoesNotInventContent() {
        assertTrue(PersonalDashboardRules.highlights(emptyList(), quotes, false).isEmpty())
        assertTrue(VerifiedDailyBrief().build(emptyList(), quotes, emptyList()).isEmpty())
        assertTrue(PersonalDashboardRules.stories(emptyMap(), emptySet()).isEmpty())
    }
    @Test fun greetingUsesLocalHourWithoutFakeName() {
        assertEquals("Good morning", PersonalDashboardRules.greeting(8))
        assertEquals("Good afternoon", PersonalDashboardRules.greeting(14))
        assertEquals("Good evening", PersonalDashboardRules.greeting(23))
    }
    @Test fun allListsOrderedAndDeduplicatedByListingSymbol() {
        fun list(id: String, order: Int, entries: List<WatchlistEntry>) = Watchlist(id, id, order, 0, 0, entries = entries)
        fun entry(id: String, symbol: String, order: Int) = WatchlistEntry(id, InstrumentRef(symbol), order, 0)
        assertEquals(listOf("AAPL", "SHOP.TO", "SHOP"), PersonalDashboardRules.instruments(listOf(
            list("second", 1, listOf(entry("c", "SHOP", 0), entry("d", "AAPL", 1))),
            list("first", 0, listOf(entry("b", "SHOP.TO", 1), entry("a", "AAPL", 0))))).map { it.symbol })
    }
}
