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
        assertEquals("Updated 30 min ago · Market data for the Oct 7 session", DailyBriefDisplay.freshness(brief, now))
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
}
