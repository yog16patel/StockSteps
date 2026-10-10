package org.example.stocksteps.presentation.home

import kotlin.time.Instant
import org.example.stocksteps.brief.BriefEdition
import org.example.stocksteps.brief.BriefIndex
import org.example.stocksteps.brief.BriefMarketSession
import org.example.stocksteps.brief.BriefPhase
import org.example.stocksteps.brief.BriefStatus
import org.example.stocksteps.brief.ConceptOfTheDay
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.brief.QuoteState
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeDashboardPresentationTest {
    private val present = HomeDashboardPresentation
    private fun index(id: String, value: Double? = 7772.36, change: Double? = -18.59, percent: Double? = -0.2386, state: QuoteState = QuoteState.SESSION_CLOSE) =
        BriefIndex(id, id, "US", value, change, percent, "USD", "points", quoteAsOf = "2026-10-07T20:00:00Z", state = state, stateLabel = "Close · Oct 7")
    private fun brief(indices: List<BriefIndex>, updatedAt: String = "2026-10-07T21:15:00Z") = DailyBrief(
        id = "b", briefDate = "2026-10-07", sessionDate = "2026-10-07", edition = BriefEdition.entries.first(),
        sessions = listOf(
            BriefMarketSession("US", "US stocks (NYSE, Nasdaq)", BriefPhase.AFTER_HOURS, "2026-10-07", "2026-10-07", timezone = "America/New_York"),
            BriefMarketSession("CA", "Canadian stocks (TSX)", BriefPhase.CLOSED, "2026-10-07", "2026-10-07", timezone = "America/Toronto")),
        summaryLine = "", marketSnapshot = indices, concept = ConceptOfTheDay("c", "t", "e", null, "w", "l"),
        generatedAt = updatedAt, updatedAt = updatedAt, status = BriefStatus.COMPLETE, readingMinutes = 1)
    private val now = Instant.parse("2026-10-07T22:15:00Z").toEpochMilliseconds()

    @Test
    fun indicesAreTheBriefsFirstThreeWithoutPlaceholders() {
        assertEquals(listOf("a", "b", "c"), present.indices(brief(listOf("a", "b", "c", "d").map { index(it) })).map { it.indexId })
        assertTrue(present.indices(brief(emptyList())).isEmpty())
    }

    @Test
    fun indexChangeIsSignedAndDirectionNeverDependsOnColourAlone() {
        val down = index("spx")
        assertEquals("−18.59 (−0.24%)", present.indexChange(down))
        assertEquals(PriceDirection.DOWN, present.indexDirection(down))
        assertEquals(PriceDirection.UP, present.indexDirection(index("tsx", change = 94.42, percent = 0.31)))
        assertEquals(PriceDirection.UNCHANGED, present.indexDirection(index("flat", change = 0.0, percent = 0.0)))
    }

    @Test
    fun missingIndexValuesStayUnavailable() {
        val missing = index("x", value = null, change = null, percent = null, state = QuoteState.UNAVAILABLE)
        assertNull(present.indexChange(missing))
        assertEquals(PriceDirection.UNAVAILABLE, present.indexDirection(missing))
        assertTrue(present.indexCaution(missing))
        assertTrue(present.indexCaution(index("s", state = QuoteState.STALE)))
        assertFalse(present.indexCaution(index("ok")))
    }

    @Test
    fun sessionsUseShortMarketNamesAndTheSourcePhase() {
        assertEquals(listOf("US stocks: Closed for the day (after-hours trading)", "Canadian stocks: Closed"), present.sessionLines(brief(emptyList())))
    }

    @Test
    fun briefMetaAndActionReflectFreshnessAndOfflineCopies() {
        val fresh = brief(emptyList())
        assertEquals("Read today's brief", present.briefAction(fresh, now))
        assertTrue(present.briefMeta(fresh, now, offline = true).contains("Offline copy"))
        assertTrue(present.briefMeta(fresh, now, offline = false).endsWith("1 min read"))
        val old = Instant.parse("2026-10-09T22:15:00Z").toEpochMilliseconds()
        assertEquals("Read latest brief", present.briefAction(fresh, old))
        assertTrue(present.briefMeta(fresh, old, offline = false).startsWith("Latest available · Oct 7"))
    }

    @Test
    fun portfolioSummaryStatesAndReadableFreshness() {
        val none = PortfolioUiState()
        assertFalse(present.hasPortfolio(none))
        assertEquals("Create portfolio", present.portfolioAction(none))
        val archivedOnly = PortfolioUiState(accounts = listOf(PortfolioAccount("a", "Old", archived = true)))
        assertFalse(present.hasPortfolio(archivedOnly))
        assertEquals("View portfolio", present.portfolioAction(archivedOnly))
        val owned = PortfolioUiState(accounts = listOf(PortfolioAccount("a", "Test account")), accountName = "Test account",
            quoteAsOf = "2026-10-07T20:00:00Z", fxAsOf = "2026-10-09")
        assertTrue(present.hasPortfolio(owned))
        assertEquals("Total value · Test account", present.portfolioLabel(owned))
        assertEquals("Prices as of Oct 7, 2026, 8:00 PM UTC", present.portfolioFreshness(owned))
        assertEquals("Prices as of Oct 7, 2026, 8:00 PM UTC · Offline · last saved portfolio", present.portfolioFreshness(owned.copy(offline = true)))
        assertNull(present.portfolioFreshness(PortfolioUiState()))
    }
}
