package org.example.stocksteps.presentation.home

import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.BriefIndex
import org.example.stocksteps.brief.BriefMarketSession
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.brief.DailyBriefDisplay
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

    /** "US stocks: Closed for the day (after-hours trading)" — the exchange list stays in the brief reader (5D.5.1: wording in `DailyBriefDisplay`). */
    fun sessionLine(session: BriefMarketSession): String = DailyBriefDisplay.sessionLine(session)
    fun sessionLines(brief: DailyBrief): List<String> = DailyBriefDisplay.sessionLines(brief)

    /** "−18.59 (−0.24%)", or null when the brief has no change (the row then shows the quote state only). */
    fun indexChange(index: BriefIndex): String? = DailyBriefDisplay.indexChange(index)
    fun indexDirection(index: BriefIndex): PriceDirection = DailyBriefDisplay.indexDirection(index)
    /** Stale or unavailable quotes are labelled in the caution colour, never shown as current. */
    fun indexCaution(index: BriefIndex): Boolean = DailyBriefDisplay.indexCaution(index)

    /** D-5 (5D.5.1): the summary's first sentence under the brief entry; the reader shows it in full. */
    fun briefSummary(brief: DailyBrief): String? = DailyBriefDisplay.homeSummary(brief)

    /** "Latest available · Oct 7 · 1 min read", plus "Offline copy" when it came from the device cache. */
    fun briefMeta(brief: DailyBrief, nowMillis: Long, offline: Boolean): String =
        listOfNotNull(BriefFormat.freshness(brief, nowMillis), "Offline copy".takeIf { offline }, "${brief.readingMinutes} min read")
            .joinToString(" · ")

    fun briefAction(brief: DailyBrief, nowMillis: Long): String =
        if (BriefFormat.isStale(brief, nowMillis)) "Read latest brief" else "Read today's brief"

    // ---------- Portfolio summary ----------

    fun hasPortfolio(state: PortfolioUiState): Boolean = state.accounts.any { !it.archived }

    /**
     * Phase 8A: the summary card's total, today's change, metrics (each with its currency), notice (engine codes in words) and spoken text
     * come from the Portfolio overview itself, so Home never words the same figures differently. Display only.
     */
    fun portfolioOverview(state: PortfolioUiState): org.example.stocksteps.presentation.portfolio.PortfolioOverview =
        org.example.stocksteps.presentation.portfolio.PortfolioOverviewDisplay.of(state, state.accounts.count { !it.archived })

    fun portfolioAction(state: PortfolioUiState): String = if (state.accounts.isEmpty()) "Create portfolio" else "View portfolio"

    /** "Total value · Test account" (the selected account, as on Portfolio). */
    fun portfolioLabel(state: PortfolioUiState): String = "Total value · ${state.accountName}"

    const val PORTFOLIO_EMPTY = "Track the investments you own, alongside your separate watchlists."

    /** One meta line: "Prices as of Oct 7, 2026, 8:00 PM UTC" (the exchange-rate date stays on Portfolio). */
    fun portfolioFreshness(state: PortfolioUiState): String? =
        listOfNotNull(PortfolioPresentation.freshnessLines(state).firstOrNull(), "Offline · last saved portfolio".takeIf { state.offline })
            .joinToString(" · ").ifEmpty { null }
}
