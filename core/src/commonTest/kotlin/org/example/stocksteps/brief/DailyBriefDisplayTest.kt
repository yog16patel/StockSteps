package org.example.stocksteps.brief

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.format.ReadableText
import kotlin.test.*

/** Global UI Refinement Phase 5D.5.1: Daily Brief reader copy and display rules (display only; nothing reworded or invented). */
class DailyBriefDisplayTest {
    private val now = kotlin.time.Instant.parse("2026-10-07T21:45:00Z").toEpochMilliseconds()
    private val brief = sampleBrief("2026-10-07T21:15:00Z")

    @Test fun metaAndFreshnessAreOneLineEach() {
        assertEquals("After-close brief · Oct 7 · 2 min read", DailyBriefDisplay.meta(brief))
        // No index shown (sampleBrief has none): the session date stays in the freshness line.
        assertEquals("Updated 30 min ago · Market data for the Oct 7 session", DailyBriefDisplay.freshness(brief, now))
        // Indices shown: their labels carry the session, so freshness is just the update time (5D.5.1c).
        val idx = BriefIndex("SP500", "S&P 500", "US", 1.0, 0.0, 0.0, "USD", "points", state = QuoteState.SESSION_CLOSE, stateLabel = "Close · Oct 7")
        assertEquals("Updated 30 min ago", DailyBriefDisplay.freshness(brief.copy(marketSnapshot = listOf(idx)), now))
    }

    @Test fun relativeTimesNeverGuessATimeZone() {
        assertEquals("just now", DailyBriefDisplay.published("2026-10-07T21:44:30Z", now))
        assertEquals("45 min ago", DailyBriefDisplay.published("2026-10-07T21:00:00Z", now))
        assertEquals("3 hr ago", DailyBriefDisplay.published("2026-10-07T18:30:00Z", now))
        // Older than 20 hours (or unparseable): the date, never "today" or a clock time.
        assertEquals("Oct 6", DailyBriefDisplay.published("2026-10-06T12:00:00Z", now))
        assertEquals("Oct 7", DailyBriefDisplay.published("2026-10-07", now))
        assertEquals("Updated Oct 7", DailyBriefDisplay.updated(sampleBrief("2026-10-06T20:00:00Z"), now))
    }

    @Test fun staleAndOfflineBannersOnlyWhenTrue() {
        assertEquals(emptyList(), DailyBriefDisplay.banners(brief, now, offline = false))
        assertEquals(listOf(DailyBriefDisplay.OFFLINE), DailyBriefDisplay.banners(brief, now, offline = true))
        assertEquals(listOf(DailyBriefDisplay.STALE, DailyBriefDisplay.OFFLINE), DailyBriefDisplay.banners(sampleBrief("2026-10-06T20:00:00Z"), now, offline = true))
    }

    @Test fun storyMetaSaysAMissingPublisherAndUsesTheServerTopic() {
        val story = brief.stories.first()
        assertEquals("Markets · Reuters", DailyBriefDisplay.storyMeta(story, now))
        assertEquals("Earnings · Publisher not provided · 3 hr ago",
            DailyBriefDisplay.storyMeta(story.copy(topic = "Earnings", publisher = null, publishedAt = "2026-10-07T18:30:00Z"), now))
        assertEquals("Read the original article from the publisher", DailyBriefDisplay.readArticleLabel(story.copy(publisher = null)))
    }

    @Test fun anExcerptThatOnlyRepeatsTheHeadlineIsNotShownTwice() {
        val story = brief.stories.first().copy(headline = "IEA to accelerate oil reserve release, says 100 million barrels still to come - Reuters",
            summary = "IEA to accelerate oil reserve release, says 100 million barrels still to come  Reuters")
        assertNull(DailyBriefDisplay.excerpt(story))
        val real = story.copy(summary = "The agency said member countries would release more oil.")
        assertEquals("The agency said member countries would release more oil.", DailyBriefDisplay.excerpt(real))
        assertNull(DailyBriefDisplay.excerpt(story.copy(summary = null)))
    }

    @Test fun evidenceNoteAndAboutLinesAppearOnce() {
        val three = brief.copy(stories = List(3) { brief.stories.first().copy(id = "s$it") },
            notes = listOf("Sample data for development: captured prices and news, not live markets.", BriefContent.DISCLAIMER))
        assertEquals(listOf(StoryRanker.EVIDENCE_NOTE), DailyBriefDisplay.storyNotes(three))
        assertEquals(listOf("Sample data for development: captured prices and news, not live markets.", BriefContent.DISCLAIMER), DailyBriefDisplay.aboutLines(three))
        assertEquals(emptyList(), DailyBriefDisplay.storyNotes(brief.copy(stories = emptyList())))
    }

    @Test fun homeSummaryIsTheServerFirstSentenceUnchanged() {
        val two = brief.copy(summaryLine = "At the close on Oct 7, the S&P 500 fell 0.36% and the Nasdaq Composite fell 0.42%. In Canada, the S&P/TSX Composite rose 0.47%.")
        assertEquals("At the close on Oct 7, the S&P 500 fell 0.36% and the Nasdaq Composite fell 0.42%.", DailyBriefDisplay.homeSummary(two))
        assertEquals(brief.summaryLine, DailyBriefDisplay.homeSummary(brief))
        assertNull(DailyBriefDisplay.homeSummary(brief.copy(summaryLine = "  ")))
        assertEquals("At the close on Oct 7, the S&P 500 fell 0.36% and the Nasdaq Composite fell 0.42%.", DailyBriefDisplay.historySummary(two.summary()))
    }

    @Test fun firstSentenceNeverSplitsAmountsOrPercents() {
        assertEquals("EPS was C$1.94 against 1.90." to "Revenue rose 3.0%.", ReadableText.firstSentence("EPS was C$1.94 against 1.90. Revenue rose 3.0%."))
        assertEquals("One line" to null, ReadableText.firstSentence("One line"))
    }

    @Test fun indexRowsKeepMissingValuesMissing() {
        val idx = BriefIndex("SP500", "S&P 500", "US", 6800.5, -40.25, -0.59, "USD", "points", state = QuoteState.SESSION_CLOSE, stateLabel = "Close · Oct 7")
        assertEquals("−40.25 (−0.59%)", DailyBriefDisplay.indexChange(idx))
        assertEquals(PriceDirection.DOWN, DailyBriefDisplay.indexDirection(idx))
        val missing = idx.copy(value = null, change = null, changePercent = null, state = QuoteState.UNAVAILABLE)
        assertNull(DailyBriefDisplay.indexChange(missing))
        assertEquals(PriceDirection.UNAVAILABLE, DailyBriefDisplay.indexDirection(missing))
        assertTrue(DailyBriefDisplay.indexCaution(missing))
        assertFalse(DailyBriefDisplay.indexCaution(idx))
    }

    @Test fun earningsDetailNeverInventsACurrencySymbol() {
        val e = BriefEarnings("AAPL", "Apple", "2026-10-30", "After market close", "Confirmed", 1.234, null, "watchlist", "AAPL:2026-Q4")
        assertEquals("Expected Oct 30 · Confirmed date · After market close · EPS estimate 1.23 · On your watchlist", DailyBriefDisplay.earningsDetail(e))
        assertEquals("Expected Oct 30 · Confirmed date · After market close · EPS estimate USD 1.23 · On your watchlist", DailyBriefDisplay.earningsDetail(e.copy(currency = "USD")))
        assertEquals("Apple (AAPL)", DailyBriefDisplay.earningsTitle(e))
        // A negative estimate keeps its sign in front (was "0.-42").
        assertEquals("Expected Oct 30 · Confirmed date · After market close · EPS estimate USD −0.42 · On your watchlist",
            DailyBriefDisplay.earningsDetail(e.copy(currency = "USD", epsEstimate = -0.42)))
        assertEquals("−1,234.50", BriefFormat.groupDigits(-1234.5))
        assertEquals("1,234.50", BriefFormat.groupDigits(1234.5))
        assertEquals("Open AAPL", DailyBriefDisplay.earningsAction(e.copy(eventId = null)))
    }

    @Test fun countsReadCorrectlyForOne() {
        assertEquals("Nothing notable from the 1 company you follow in this session.", DailyBriefDisplay.nothingNotable(1))
        assertEquals("1 more update is included with StockSteps+.", DailyBriefDisplay.moreHighlights(1))
        assertEquals("3 older briefs are included with StockSteps+.", DailyBriefDisplay.lockedHistory(3))
    }

    @Test fun headingsAreSentenceCaseAndNeverClaimACause() {
        val headings = listOf(DailyBriefDisplay.TITLE, DailyBriefDisplay.MARKETS, DailyBriefDisplay.STORIES, DailyBriefDisplay.WATCHLIST, DailyBriefDisplay.EARNINGS,
            DailyBriefDisplay.CONCEPT, DailyBriefDisplay.ABOUT_STORIES, DailyBriefDisplay.ABOUT_BRIEF)
        headings.forEach { h -> assertTrue(h.split(' ').drop(1).none { it.first().isUpperCase() }, h) }
        headings.forEach { h -> assertFalse(h.contains("moved", ignoreCase = true) || h.contains("why", ignoreCase = true), h) }
    }
    // ---------- 5D.5.1b ----------

    private fun idx(id: String, market: String, pct: Double?, state: QuoteState = QuoteState.SESSION_CLOSE, label: String = "Close · Oct 7") =
        BriefIndex(id, id, market, if (pct == null) null else 1000.0, pct?.let { it * 10 }, pct, "USD", "points", state = state, stateLabel = label)
    private fun session(market: String, phase: BriefPhase, holiday: String? = null) =
        BriefMarketSession(market, market, phase, "2026-10-07", "2026-10-07", holiday, "America/New_York")
    private fun withIndices(vararg i: BriefIndex, edition: BriefEdition = BriefEdition.AFTER_CLOSE, sessions: List<BriefMarketSession> = emptyList()) =
        brief.copy(marketSnapshot = i.toList(), edition = edition, sessions = sessions, sessionDate = "2026-10-09")

    @Test fun takeawaySaysDirectionPerMarketWithoutMagnitudeOrCause() {
        val b = withIndices(idx("SP500", "US", -0.24), idx("NASDAQ", "US", -0.26), idx("TSX", "CA", 0.31))
        assertEquals("US stocks closed lower. Canadian stocks closed higher.", DailyBriefDisplay.takeaway(b))
        assertEquals("US stocks closed mixed.", DailyBriefDisplay.takeaway(withIndices(idx("SP500", "US", -0.24), idx("NASDAQ", "US", 0.3))))
        assertEquals("US stocks closed about unchanged.", DailyBriefDisplay.takeaway(withIndices(idx("SP500", "US", 0.001), idx("NASDAQ", "US", -0.004))))
        val words = listOf("slight", "sharp", "soar", "plunge", "because", "after", "due to", "today")
        val text = DailyBriefDisplay.takeaway(b)!!.lowercase()
        words.forEach { assertFalse(text.contains(it), it) }
    }

    @Test fun takeawayNeverCallsAnOlderSessionToday() {
        val pre = withIndices(idx("SP500", "US", -0.24, QuoteState.PREVIOUS_CLOSE), idx("TSX", "CA", 0.31, QuoteState.PREVIOUS_CLOSE), edition = BriefEdition.PRE_MARKET)
        assertEquals("In the last session (Oct 9), US stocks closed lower. Canadian stocks closed higher.", DailyBriefDisplay.takeaway(pre))
        val weekend = withIndices(idx("SP500", "US", 0.5, QuoteState.PREVIOUS_CLOSE), edition = BriefEdition.WEEKEND)
        assertEquals("In the last session (Oct 9), US stocks closed higher.", DailyBriefDisplay.takeaway(weekend))
        val live = withIndices(idx("SP500", "US", -0.24, QuoteState.LIVE_DELAYED), idx("NASDAQ", "US", -0.1, QuoteState.LIVE_DELAYED), edition = BriefEdition.MARKET_HOURS)
        assertEquals("US stocks are lower so far today (delayed).", DailyBriefDisplay.takeaway(live))
    }

    @Test fun takeawayIgnoresStaleOrMissingValuesAndFallsBackToNull() {
        val staleCa = withIndices(idx("SP500", "US", -0.24), idx("TSX", "CA", 0.31, QuoteState.STALE, "From an earlier session (Oct 3)"))
        assertEquals("US stocks closed lower.", DailyBriefDisplay.takeaway(staleCa))
        assertNull(DailyBriefDisplay.takeaway(withIndices(idx("SP500", "US", null, QuoteState.UNAVAILABLE, "Not available right now"))))
        assertNull(DailyBriefDisplay.takeaway(withIndices()))
        // Live and closed US values together are not blended into one sentence.
        assertNull(DailyBriefDisplay.takeaway(withIndices(idx("SP500", "US", -0.2, QuoteState.LIVE_DELAYED), idx("NASDAQ", "US", -0.2))))
        // Home falls back to the summary's first sentence.
        assertEquals(brief.summaryLine, DailyBriefDisplay.homeSummary(withIndices()))
    }

    @Test fun takeawaySaysAHolidayClosureFromTheSession() {
        val b = withIndices(idx("TSX", "CA", -0.4, QuoteState.LIVE_DELAYED), edition = BriefEdition.HOLIDAY,
            sessions = listOf(session("US", BriefPhase.HOLIDAY, "Thanksgiving"), session("CA", BriefPhase.OPEN)))
        assertEquals("US markets are closed for Thanksgiving. Canadian stocks are lower so far today (delayed).", DailyBriefDisplay.takeaway(b))
        // Canada closed for its holiday while US trades: never "Canadian stocks closed higher" from an older close (us-open-ca-closed).
        val caHoliday = withIndices(idx("SP500", "US", -0.24, QuoteState.LIVE_DELAYED), idx("TSX", "CA", 0.31, QuoteState.PREVIOUS_CLOSE, "Close · Oct 9"),
            edition = BriefEdition.MARKET_HOURS, sessions = listOf(session("US", BriefPhase.OPEN), session("CA", BriefPhase.HOLIDAY, "Thanksgiving Day")))
        assertEquals("US stocks are lower so far today (delayed). Canadian markets are closed for Thanksgiving Day.", DailyBriefDisplay.takeaway(caHoliday))
        // Both closed for the same holiday: one sentence.
        val both = withIndices(idx("SP500", "US", -0.24, QuoteState.PREVIOUS_CLOSE), idx("TSX", "CA", 0.31, QuoteState.PREVIOUS_CLOSE), edition = BriefEdition.HOLIDAY,
            sessions = listOf(session("US", BriefPhase.HOLIDAY, "Christmas Day"), session("CA", BriefPhase.HOLIDAY, "Christmas Day")))
        assertEquals("US and Canadian markets are closed for Christmas Day.", DailyBriefDisplay.takeaway(both))
    }

    @Test fun oneStatusBannerKeepsEachStateTrue() {
        val stale = sampleBrief("2026-10-06T20:00:00Z")
        assertNull(DailyBriefDisplay.status(brief, now, offline = false))
        assertEquals(DailyBriefDisplay.Status(false, DailyBriefDisplay.OFFLINE), DailyBriefDisplay.status(brief, now, offline = true))
        assertEquals(DailyBriefDisplay.Status(true, DailyBriefDisplay.STALE), DailyBriefDisplay.status(stale, now, offline = false))
        assertEquals(DailyBriefDisplay.Status(true, "${DailyBriefDisplay.STALE} ${DailyBriefDisplay.OFFLINE}"), DailyBriefDisplay.status(stale, now, offline = true))
    }

    @Test fun tilesShowPercentAndPointsAndTheSharedStateOnce() {
        val sp = BriefIndex("SP500", "S&P 500", "US", 7772.36, -18.59, -0.24, "USD", "points", state = QuoteState.SESSION_CLOSE, stateLabel = "Close · Oct 7")
        val tsx = sp.copy(indexId = "TSX", displayName = "S&P/TSX Composite", market = "CA", value = 30544.18, change = 94.42, changePercent = 0.31)
        assertEquals("−0.24%", DailyBriefDisplay.indexPercent(sp)); assertEquals("−18.59", DailyBriefDisplay.indexPoints(sp))
        assertEquals("+0.31%", DailyBriefDisplay.indexPercent(tsx)); assertEquals("+94.42", DailyBriefDisplay.indexPoints(tsx))
        assertNull(DailyBriefDisplay.indexPercent(sp.copy(value = null)))
        val shared = brief.copy(marketSnapshot = listOf(sp, tsx))
        assertEquals("All values: Close · Oct 7", DailyBriefDisplay.sharedStateLine(shared))
        assertFalse("Close" in DailyBriefDisplay.tileTokens(shared))
        val differ = brief.copy(marketSnapshot = listOf(sp, tsx.copy(state = QuoteState.STALE, stateLabel = "From an earlier session (Oct 3)")))
        assertNull(DailyBriefDisplay.sharedStateLine(differ))
        assertTrue("earlier" in DailyBriefDisplay.tileTokens(differ))
        assertTrue("S&P/TSX" in DailyBriefDisplay.tileTokens(shared) && "Composite" in DailyBriefDisplay.tileTokens(shared))
    }

    @Test fun earningsRowsKeepTheServerStatusAndNeverInventAnEstimate() {
        val e = BriefEarnings("CRBU", "Caribou Biosciences, Inc.", "2026-10-08", "Before market open", "Estimated", -0.42, "USD", "general", "CRBU:2026-Q3")
        assertEquals("Expected Oct 8 · Before market open", DailyBriefDisplay.earningsWhen(e))
        assertEquals("Estimated", DailyBriefDisplay.earningsStatus(e)); assertFalse(DailyBriefDisplay.earningsConfirmed(e))
        assertTrue(DailyBriefDisplay.earningsConfirmed(e.copy(dateStatus = "Confirmed")))
        assertEquals("EPS estimate USD −0.42 · General example", DailyBriefDisplay.earningsNote(e))
        assertEquals("General example", DailyBriefDisplay.earningsNote(e.copy(epsEstimate = null)))
        assertEquals("Expected Oct 8", DailyBriefDisplay.earningsWhen(e.copy(timing = null)))
    }

    @Test fun historyRowsSplitTypeFromDate() {
        val s = brief.summary()
        assertEquals("After-close brief", DailyBriefDisplay.historyTitle(s))
        assertEquals("Oct 7 · 2 min read", DailyBriefDisplay.historyDetail(s))
    }
}
