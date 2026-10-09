package org.example.stocksteps.earnings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.network.StockStepsApiException
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 1: calendar rules and presenters (deterministic clock, fake remote). */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsCalendarTest {
    private fun event(symbol: String, date: String, session: EarningsTime = EarningsTime.BEFORE_OPEN, reported: Boolean = false,
                      sourceStatus: EarningsEventStatus? = null, dateStatus: EarningsDateStatus = EarningsDateStatus.ESTIMATED) =
        EarningsEvent("$symbol:2026-Q3", symbol, "$symbol Corp", "NYSE", "US", fiscalYear = 2026, fiscalQuarter = 3, date = date, session = session,
            dateStatus = dateStatus, actual = if (reported) EarningsActual(eps = 1.0, revenue = 2.0, source = "t") else null, source = "t", updatedAt = "2026-10-07T21:15:00Z",
            sourceStatus = sourceStatus)

    // ---------- Rules ----------

    @Test fun statusNeedsSourceDataNotJustAPassedDate() {
        val today = "2026-10-07"
        assertEquals(EarningsEventStatus.SCHEDULED, EarningsCalendarRules.status(event("A", "2026-10-07"), today))
        assertEquals(EarningsEventStatus.SCHEDULED, EarningsCalendarRules.status(event("A", "2026-10-09"), today))
        assertEquals(EarningsEventStatus.UNKNOWN, EarningsCalendarRules.status(event("A", "2026-10-06"), today))           // passed, no results
        assertEquals(EarningsEventStatus.REPORTED, EarningsCalendarRules.status(event("A", "2026-10-06", reported = true), today))
        assertEquals(EarningsEventStatus.REPORTED, EarningsCalendarRules.status(event("A", "2026-10-06", sourceStatus = EarningsEventStatus.REPORTED), today))
        assertEquals(EarningsEventStatus.POSTPONED, EarningsCalendarRules.status(event("A", "2026-10-09", sourceStatus = EarningsEventStatus.POSTPONED), today))
        assertEquals(EarningsEventStatus.CANCELED, EarningsCalendarRules.status(event("A", "2026-10-09", sourceStatus = EarningsEventStatus.CANCELED), today))
        assertEquals(EarningsEventStatus.UNKNOWN, EarningsCalendarRules.status(event("A", "2026-10-09", dateStatus = EarningsDateStatus.UNKNOWN), today))
        assertTrue(EarningsCalendarRules.inView(EarningsEventStatus.UNKNOWN, "upcoming"))
        assertFalse(EarningsCalendarRules.inView(EarningsEventStatus.UNKNOWN, "reported"))
        assertTrue(EarningsCalendarRules.inView(EarningsEventStatus.REPORTED, "results"))
    }

    @Test fun timingIsOnlyWhatTheSourceSays() {
        assertEquals("Before market open", EarningsCalendarRules.timing(event("A", "2026-10-08")))
        assertEquals("After market close", EarningsCalendarRules.timing(event("A", "2026-10-08", EarningsTime.AFTER_CLOSE)))
        assertEquals("During market hours", EarningsCalendarRules.timing(event("A", "2026-10-08", EarningsTime.DURING_MARKET)))
        assertEquals("Time not confirmed", EarningsCalendarRules.timing(event("A", "2026-10-08", EarningsTime.UNKNOWN)))
        assertEquals("After market close · 4:05 PM ET",
            EarningsCalendarRules.timing(event("A", "2026-10-08", EarningsTime.AFTER_CLOSE).copy(eventTime = "16:05", timeZone = "America/New_York")))
        assertEquals("Before market open · 7:30 AM ET", EarningsCalendarRules.timing(event("A", "2026-10-08").copy(eventTime = "07:30", timeZone = "America/Toronto")))
        assertEquals("Before market open", EarningsCalendarRules.timing(event("A", "2026-10-08").copy(eventTime = "25:99")))   // invalid time isn't shown
    }

    @Test fun weeksStartOnMondayAndSearchRangesStayBounded() {
        assertEquals("2026-10-05", EarningsCalendarRules.weekStart("2026-10-07"))           // Wednesday
        assertEquals("2026-10-05", EarningsCalendarRules.weekStart("2026-10-11"))           // Sunday belongs to the same week
        assertEquals("2026-10-12", EarningsCalendarRules.weekStart("2026-10-12"))
        assertEquals(listOf("2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03"), EarningsCalendarRules.week("2026-12-31"))
        assertEquals("2026-10-05" to "2026-10-11", EarningsCalendarRules.range("2026-10-07", CalendarTab.UPCOMING, searching = false))
        assertEquals("2026-10-05" to "2026-12-06", EarningsCalendarRules.range("2026-10-07", CalendarTab.UPCOMING, searching = true))
        assertEquals("2026-08-10" to "2026-10-11", EarningsCalendarRules.range("2026-10-07", CalendarTab.REPORTED, searching = true))
        assertEquals("Oct 5 – 11, 2026", EarningsCalendarRules.weekLabel("2026-10-05"))
        assertEquals("Sep 28 – Oct 4, 2026", EarningsCalendarRules.weekLabel("2026-09-28"))
        assertTrue(EarningsCalendarRules.isDate("2026-02-28")); assertFalse(EarningsCalendarRules.isDate("2026-02-30")); assertFalse(EarningsCalendarRules.isDate("10/07/2026"))
    }

    @Test fun searchMatchesTickersAndNameWordsCaseInsensitively() {
        val td = event("TD", "2026-11-30").copy(name = "Toronto-Dominion Bank")
        val gc = event("GCDT", "2026-10-12").copy(name = "Green Circle Decarbonize Technology Ltd.")
        assertTrue(EarningsCalendarRules.matches(td, "td")); assertTrue(EarningsCalendarRules.matches(td, "DOMINION")); assertTrue(EarningsCalendarRules.matches(td, "toronto-dom"))
        assertFalse(EarningsCalendarRules.matches(gc, "td"))                                 // not inside "Ltd."
        assertTrue(EarningsCalendarRules.matches(gc, " green circle "))
        assertTrue(EarningsCalendarRules.matches(gc, ""))
    }

    @Test fun selectionRoundTripsForStateRestoration() {
        val s = CalendarSelection("2026-10-08", CalendarMode.DAY, CalendarTab.REPORTED, CalendarFilter.WATCHLIST, "apple | inc")
        assertEquals(s, CalendarSelection.decode(s.encode()))
        assertNull(CalendarSelection.decode("garbage"))
        assertNull(CalendarSelection.decode("2026-13-40|WEEK|UPCOMING|ALL|"))
    }

    @Test fun accessibilityLabelsUseFullDatesAndWords() {
        val row = EarningsCalendarItem(event("KO", "2026-10-21"), EarningsStatus.UPCOMING, eventStatus = EarningsEventStatus.SCHEDULED).eventRow(setOf("KO"))
        assertEquals("KO Corp, KO, NYSE. Wednesday, October 21, 2026. Before market open. Scheduled · Estimated date. On your watchlist.", row.accessibility)
        assertEquals("Wednesday, October 7, 2026. 2 earnings reports. Today. Selected.", WeekDayView("2026-10-07", "Wed", "7", 2, true, true).accessibility)
        assertEquals("Thursday, October 8, 2026. No earnings reports.", WeekDayView("2026-10-08", "Thu", "8", 0, false, false).accessibility)
    }

    // ---------- Presenters ----------

    private class FakeRemote(val events: List<EarningsEvent>) : EarningsRemote {
        val calls = mutableListOf<Pair<String, EarningsCalendarQuery>>()
        var fail: Exception? = null
        var followed: Set<String> = emptySet()
        var freshness = DataFreshness.FRESH
        private fun page(query: EarningsCalendarQuery, source: List<EarningsEvent>, followedCount: Int? = null): EarningsCalendarPage {
            fail?.let { throw it }
            val matching = source.filter { it.date in query.from..query.to && EarningsCalendarRules.matches(it, query.query) }
                .map { EarningsCalendarItem(it, EarningsStatus.UPCOMING, eventStatus = EarningsCalendarRules.status(it, "2026-10-07")) }
                .filter { EarningsCalendarRules.inView(it.eventStatus, query.view) }
            val shown = query.day?.let { d -> matching.filter { it.event.date == d } } ?: matching
            val offset = query.cursor?.toInt() ?: 0
            val slice = shown.drop(offset).take(query.pageSize)
            return EarningsCalendarPage(slice, query.from, query.to, shown.size, (offset + slice.size).takeIf { it < shown.size }?.toString(), asOf = "now",
                dayCounts = matching.groupingBy { it.event.date }.eachCount(), freshness = freshness, fetchedAt = "2026-10-07T18:00:00Z", followedCount = followedCount)
        }
        override suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage { calls += "public" to query; return page(query, events) }
        override suspend fun following(query: EarningsCalendarQuery): EarningsCalendarPage {
            calls += "following" to query
            return page(query, events.filter { it.symbol in followed }, followed.size)
        }
        override suspend fun details(symbol: String, signedIn: Boolean) = throw UnsupportedOperationException()
        override suspend fun ask(symbol: String, question: String) = throw UnsupportedOperationException()
        override suspend fun event(id: String): EarningsEventInfo {
            fail?.let { throw it }
            val e = events.firstOrNull { it.id == id } ?: throw StockStepsApiException(404, ApiError("NOT_FOUND", "Not found"))
            return EarningsEventInfo(EarningsCalendarItem(e, EarningsStatus.UPCOMING, eventStatus = EarningsCalendarRules.status(e, "2026-10-07")), "now", sampleData = true)
        }
        override suspend fun next(symbol: String): NextEarnings {
            fail?.let { throw it }
            val e = events.filter { it.symbol == symbol && it.date >= "2026-10-07" }.minByOrNull { it.date }
            return NextEarnings(symbol, e, e?.let { EarningsCalendarRules.status(it, "2026-10-07") }, "now")
        }
        override suspend fun results(reportId: String): EarningsResultsResponse = throw StockStepsApiException(404, ApiError("NOT_REPORTED", "Not published"))
        override suspend fun latestResults(symbol: String): EarningsResultsResponse = throw StockStepsApiException(404, ApiError("NO_REPORT", "None"))
        override suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse = throw UnsupportedOperationException()
    }

    private val sample = listOf(
        event("INTC", "2026-10-07", EarningsTime.AFTER_CLOSE), event("LUCY", "2026-10-07", reported = true),
        event("CRBU", "2026-10-08"), event("TOPP", "2026-10-08", EarningsTime.AFTER_CLOSE), event("SPEC", "2026-10-08", EarningsTime.UNKNOWN),
        event("JPM", "2026-10-14"), event("KO", "2026-10-21"), event("BCE", "2026-10-06")
    )

    /** Presenters run in backgroundScope, which advanceUntilIdle ignores: advance virtual time instead. */
    private fun TestScope.settle() { advanceTimeBy(2_000); runCurrent() }

    private fun TestScope.presenter(remote: FakeRemote, session: MutableStateFlow<String?> = MutableStateFlow(null), watched: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
                                    initial: CalendarSelection? = null, pageSize: Int = 20) =
        EarningsCalendarPresenter(remote, backgroundScope, session, watched, today = { "2026-10-07" }, initial = initial, pageSize = pageSize, now = { testScheduler.currentTime }).also { it.start() }

    @Test fun weekViewLoadsWithReliableDayCountsAndDayViewNarrowsIt() = runTest {
        val remote = FakeRemote(sample)
        val p = presenter(remote)
        settle()
        val week = p.state.value
        assertEquals("Oct 5 – 11, 2026", week.weekLabel)
        assertEquals(listOf("INTC", "CRBU", "TOPP", "SPEC", "BCE").sorted(), week.rows.map { it.symbol }.sorted()) // LUCY is reported
        assertEquals(listOf(0, 1, 1, 3, 0, 0, 0), week.week.map { it.count })
        assertTrue(week.week.single { it.date == "2026-10-07" }.let { it.today && it.selected })
        assertEquals("upcoming", remote.calls.last().second.view)
        p.selectMode(CalendarMode.DAY); p.selectDate("2026-10-08"); settle()
        assertEquals("2026-10-08", remote.calls.last().second.day)
        assertEquals(listOf("CRBU", "TOPP", "SPEC"), p.state.value.rows.map { it.symbol })
        assertEquals(3, p.state.value.week.single { it.date == "2026-10-08" }.count)          // counts still cover the week
        p.selectDate("2026-10-10"); settle()
        assertEquals("No earnings events on this day.", p.state.value.emptyMessage)
        p.selectTab(CalendarTab.REPORTED); p.selectDate("2026-10-07"); settle()
        assertEquals(listOf("LUCY"), p.state.value.rows.map { it.symbol })
        assertEquals(EarningsEventStatus.REPORTED, p.state.value.rows.single().status)
    }

    @Test fun weekNavigationTodayAndCacheAvoidDuplicateRequests() = runTest {
        val remote = FakeRemote(sample)
        val p = presenter(remote)
        settle()
        val first = remote.calls.size
        p.nextWeek(); settle()
        assertEquals("2026-10-14", p.state.value.selection.date)
        assertEquals("2026-10-12", remote.calls.last().second.from)
        assertEquals(listOf("JPM"), p.state.value.rows.map { it.symbol })
        p.previousWeek(); settle()
        assertEquals(first + 1, remote.calls.size)                                             // the first week came from the cache
        p.nextWeek(); p.nextWeek(); settle()
        p.goToToday(); settle()
        assertEquals("2026-10-07", p.state.value.selection.date)
        val before = remote.calls.size
        p.refresh(); settle()
        assertEquals(before + 1, remote.calls.size)                                            // refresh bypasses the cache
        p.selectDate("not a date"); settle()
        assertEquals("2026-10-07", p.state.value.selection.date)
    }

    @Test fun searchIsDebouncedServerSideAndClearable() = runTest {
        val remote = FakeRemote(sample)
        val p = presenter(remote)
        settle()
        val before = remote.calls.size
        p.setQuery("j"); advanceTimeBy(100); p.setQuery("jp"); advanceTimeBy(100); p.setQuery("JPM"); settle()
        assertEquals(before + 1, remote.calls.size)                                            // one request for the final text
        val q = remote.calls.last().second
        assertEquals("JPM", q.query); assertNull(q.day)
        assertEquals("2026-10-05" to "2026-12-06", q.from to q.to)                             // bounded search window, not just this page
        assertEquals(listOf("JPM"), p.state.value.rows.map { it.symbol })
        assertNotNull(p.state.value.rangeText)
        p.setQuery("nothing"); settle()
        assertEquals("No earnings events match “nothing” in this period.", p.state.value.emptyMessage)
        val calls = remote.calls.size
        p.clearQuery(); settle()
        assertEquals(calls, remote.calls.size)                                                  // back to the cached week
        assertEquals(5, p.state.value.rows.size)
        assertFalse(p.state.value.searching)
    }

    @Test fun watchlistFilterNeedsSignInUsesTheServerAndFollowsAccountAndListChanges() = runTest {
        val remote = FakeRemote(sample)
        val session = MutableStateFlow<String?>(null)
        val watched = MutableStateFlow<Set<String>>(emptySet())
        val p = presenter(remote, session, watched, initial = CalendarSelection("", filter = CalendarFilter.WATCHLIST))
        settle()
        assertTrue(remote.calls.isEmpty())                                                      // signed out: nothing requested
        assertTrue(p.state.value.needsSignIn)
        assertEquals("Sign in to see earnings for companies on your watchlists.", p.state.value.emptyMessage)
        session.value = "alice"; settle()
        assertEquals("following" to "watchlist", remote.calls.last().let { it.first to it.second.scope })
        assertEquals(0, p.state.value.followedCount)
        assertTrue(p.state.value.emptyMessage!!.startsWith("Your watchlists are empty"))
        remote.followed = setOf("CRBU"); watched.value = setOf("CRBU"); settle()     // a watchlist change refreshes the filter
        assertEquals(listOf("CRBU"), p.state.value.rows.map { it.symbol })
        assertTrue(p.state.value.rows.single().watchlisted)
        val calls = remote.calls.size
        session.value = "bob"; settle()                                               // account switch: never alice's cached page
        assertEquals(calls + 1, remote.calls.size)
        session.value = null; settle()
        assertTrue(p.state.value.rows.isEmpty() && p.state.value.needsSignIn)                   // sign-out clears the list
        // All Companies keeps the date and tab and marks watched rows without another request.
        session.value = "alice"; p.selectFilter(CalendarFilter.ALL); settle()
        val n = remote.calls.size
        watched.value = setOf("INTC"); settle()
        assertEquals(n, remote.calls.size)
        assertEquals(listOf("INTC"), p.state.value.rows.filter { it.watchlisted }.map { it.symbol })
    }

    @Test fun errorsStaleDataAndPagingAreReported() = runTest {
        val remote = FakeRemote(sample)
        remote.fail = StockStepsApiException(503, ApiError("EARNINGS_UNAVAILABLE", "Earnings data isn't available right now."))
        val p = presenter(remote, pageSize = 2)
        settle()
        assertEquals("Earnings data isn't available right now.", p.state.value.error)
        assertTrue(p.state.value.week.all { it.count == null })                                 // no counts when unknown
        assertNull(p.state.value.emptyMessage)
        remote.fail = null; remote.freshness = DataFreshness.STALE
        p.refresh(); settle()
        assertNull(p.state.value.error)
        assertTrue(p.state.value.freshnessText!!.startsWith("Showing saved earnings data from Wed, Oct 7, 18:00 UTC"))
        assertEquals(2, p.state.value.rows.size); assertTrue(p.state.value.hasMore)
        p.loadMore(); settle()
        p.loadMore(); settle()
        assertEquals(5, p.state.value.rows.size); assertFalse(p.state.value.hasMore)
        assertEquals(p.state.value.rows.map { it.id }.distinct(), p.state.value.rows.map { it.id })
    }

    @Test fun eventDetailsCompanyNextDateAndMarketsSummary() = runTest {
        val remote = FakeRemote(sample + event("SSCX", "2026-10-13", sourceStatus = EarningsEventStatus.CANCELED))
        val details = EarningsEventPresenter("LUCY:2026-Q3", remote, backgroundScope)
        settle()
        assertTrue(details.state.value.reported)
        assertEquals("Results have been reported.", details.state.value.statusExplanation)
        assertEquals("2026-10-07", details.state.value.date)
        assertEquals("The source didn't say when this was last updated.", details.state.value.updatedText)
        val canceled = EarningsEventPresenter("SSCX:2026-Q3", remote, backgroundScope); settle()
        assertEquals("Canceled", canceled.state.value.statusText)
        val missing = EarningsEventPresenter("NOPE:2026-Q1", remote, backgroundScope); settle()
        assertTrue(missing.state.value.error!!.contains("isn't available"))

        val next = CompanyEarningsPresenter("KO", remote, backgroundScope); settle()
        assertEquals("Wed, Oct 21, 2026", next.state.value.dateText)
        assertEquals("Scheduled · Estimated date", next.state.value.statusText)
        val none = CompanyEarningsPresenter("GOOGL", remote, backgroundScope); settle()
        assertEquals("Next earnings date not available.", none.state.value.message)

        val session = MutableStateFlow<String?>("alice")
        remote.followed = setOf("KO", "JPM")
        val summary = EarningsSummaryPresenter(remote, backgroundScope, session, today = { "2026-10-07" }); settle()
        assertEquals(4, summary.state.value.upcomingCount)                                     // Oct 7–13 scheduled; canceled and reported excluded
        assertEquals("4 companies report in the next 7 days · next on Wed, Oct 7", summary.state.value.headline)
        assertNull(summary.state.value.watchlistText)                                           // none of alice's in the next 7 days
        remote.fail = IllegalStateException("offline"); summary.refresh(); settle()
        assertNull(summary.state.value.headline)                                                // no fabricated count
    }
}
