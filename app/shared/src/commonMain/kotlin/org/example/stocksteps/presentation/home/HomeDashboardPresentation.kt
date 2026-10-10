package org.example.stocksteps.presentation.home

import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.BriefIndex
import org.example.stocksteps.brief.BriefMarketSession
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.brief.QuoteState
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.presentation.portfolio.PortfolioPresentation

/**
 * Wording and grouping for the Home dashboard (Global UI Refinement Phase 4A), shared by Compose and SwiftUI. Formatting only: index
 * values come from the Daily Brief already loaded on Home (no extra requests) and portfolio figures from the portfolio presenter.
 */
object HomeDashboardPresentation {
    /** Home shows the brief's first three indices (S&P 500, Nasdaq Composite, S&P/TSX); the brief reader lists all of them. */
    const val MAX_INDICES = 3
    const val INDICES_UNAVAILABLE = "Index values aren't available right now."

    fun indices(brief: DailyBrief): List<BriefIndex> = brief.marketSnapshot.take(MAX_INDICES)

    /** "US stocks: Closed for the day (after-hours trading)" — the exchange list stays in the brief reader. */
    fun sessionLine(session: BriefMarketSession): String = "${sessionName(session)}: ${session.label}"
    fun sessionLines(brief: DailyBrief): List<String> = brief.sessions.map(::sessionLine)
    private fun sessionName(session: BriefMarketSession): String = when (session.market) {
        "US" -> "US stocks"
        "CA" -> "Canadian stocks"
        else -> session.name
    }

    /** "−18.59 (−0.24%)", or null when the brief has no change (the row then shows the quote state only). */
    fun indexChange(index: BriefIndex): String? = if (index.value == null || index.change == null) null else BriefFormat.change(index)

    fun indexDirection(index: BriefIndex): PriceDirection = when {
        index.value == null || index.changePercent == null -> PriceDirection.UNAVAILABLE
        else -> when (BriefFormat.sign(index)) { 1 -> PriceDirection.UP; -1 -> PriceDirection.DOWN; else -> PriceDirection.UNCHANGED }
    }

    /** Stale or unavailable quotes are labelled in the caution colour, never shown as current. */
    fun indexCaution(index: BriefIndex): Boolean = index.state == QuoteState.STALE || index.state == QuoteState.UNAVAILABLE

    /** "Latest available · Oct 7 · 1 min read", plus "Offline copy" when it came from the device cache. */
    fun briefMeta(brief: DailyBrief, nowMillis: Long, offline: Boolean): String =
        listOfNotNull(BriefFormat.freshness(brief, nowMillis), "Offline copy".takeIf { offline }, "${brief.readingMinutes} min read")
            .joinToString(" · ")

    fun briefAction(brief: DailyBrief, nowMillis: Long): String =
        if (BriefFormat.isStale(brief, nowMillis)) "Read latest brief" else "Read today's brief"

    // ---------- Portfolio summary ----------

    fun hasPortfolio(state: PortfolioUiState): Boolean = state.accounts.any { !it.archived }

    fun portfolioAction(state: PortfolioUiState): String = if (state.accounts.isEmpty()) "Create portfolio" else "View portfolio"

    /** "Total value · Test account" (the selected account, as on Portfolio). */
    fun portfolioLabel(state: PortfolioUiState): String = "Total value · ${state.accountName}"

    const val PORTFOLIO_EMPTY = "Track the investments you own, alongside your separate watchlists."

    /** One meta line: "Prices as of Oct 7, 2026, 8:00 PM UTC" (the exchange-rate date stays on Portfolio). */
    fun portfolioFreshness(state: PortfolioUiState): String? =
        listOfNotNull(PortfolioPresentation.freshnessLines(state).firstOrNull(), "Offline · last saved portfolio".takeIf { state.offline })
            .joinToString(" · ").ifEmpty { null }
}
