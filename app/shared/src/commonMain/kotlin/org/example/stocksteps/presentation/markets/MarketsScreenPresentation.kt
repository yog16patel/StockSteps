package org.example.stocksteps.presentation.markets

import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.earnings.EarningsSummaryState
import org.example.stocksteps.markets.IndexCardModel
import org.example.stocksteps.markets.MarketHeaderModel
import org.example.stocksteps.markets.MarketLesson
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.markets.SectorsModel
import org.example.stocksteps.markets.SessionTone
import org.example.stocksteps.model.IndexQuote
import org.example.stocksteps.model.MarketSession
import org.example.stocksteps.model.MarketSessionStatus

/**
 * Wording and grouping for the Markets screen (Global UI Refinement Phase 4B), shared by Compose and SwiftUI. Formatting only: every
 * value, status and timestamp comes from the shared `MarketsPresenter` models; nothing is calculated or estimated here.
 */
object MarketsScreenPresentation {
    const val TITLE = "Markets"

    /** Status dot colour family: green only while the regular session is open, amber for extended hours, neutral otherwise — closed is not a loss. */
    enum class StatusDot { OPEN, EXTENDED, NEUTRAL }

    fun statusDot(tone: SessionTone): StatusDot = when (tone) {
        SessionTone.OPEN -> StatusDot.OPEN
        SessionTone.EXTENDED -> StatusDot.EXTENDED
        SessionTone.CLOSED, SessionTone.UNKNOWN -> StatusDot.NEUTRAL
    }

    // ---------- US Market status card (Phase 4B.1: compact, US only) ----------

    const val STATUS_TITLE = "US Market"

    /** Short status under the "US Market" title; holidays keep their name. Never derived from the device clock. */
    fun statusLabel(session: MarketSession): String = when (session.status) {
        MarketSessionStatus.OPEN -> "Open"
        MarketSessionStatus.PRE_MARKET -> "Pre-market"
        MarketSessionStatus.AFTER_HOURS -> "After-hours"
        MarketSessionStatus.CLOSED -> "Closed"
        MarketSessionStatus.WEEKEND -> "Closed for the weekend"
        MarketSessionStatus.HOLIDAY -> "Closed · ${session.holiday ?: "Market holiday"}"
        MarketSessionStatus.UNKNOWN -> "Status unavailable"
    }

    /** "Next open · Fri, Oct 9 · 9:30 AM ET" / "Closes · 4:00 PM ET" from the backend calendar; null when it gave nothing. */
    fun sessionLine(session: MarketSession): String? = MarketsPresenter.sessionLine(session)

    /** One short footer: "Sample quotes · Not live" for sample data, otherwise the source update time ("Updated 5:15 PM ET · Oct 7"). */
    fun statusFooter(header: MarketHeaderModel): String? = if (header.sampleData) "Sample quotes · Not live" else header.updated

    /** What screen readers hear for the footer: the full data notice, so the short visible text loses nothing. */
    fun statusFooterDescription(header: MarketHeaderModel): String =
        listOfNotNull(header.updated, header.notice.takeIf { it.isNotBlank() }).joinToString(". ")

    // ---------- Indices ----------

    /** The Markets screen lists US indices only (region from the API); other regions stay in the data and the Daily Brief. */
    fun usIndices(cards: List<IndexCardModel>, quotes: List<IndexQuote>): List<IndexCardModel> {
        val us = quotes.filter { it.region.equals("US", ignoreCase = true) }.map { it.id }.toSet()
        return cards.filter { it.id in us }
    }

    /** "-18.59 (-0.24%)", or just the percentage when the point change is missing. */
    fun indexChange(card: IndexCardModel): String = card.change?.let { "$it (${card.percent})" } ?: card.percent

    /** "As of 4:00 PM ET · Oct 7 · Proxy: SPY fund price" — the source quote time and proxy note, never a computed freshness. */
    fun indexMeta(card: IndexCardModel): String? = listOfNotNull(card.updated, card.proxyLabel).joinToString(" · ").ifEmpty { null }

    /** Index cards narrower than this (dp/pt) leave out the trend sparkline so the name and quote time keep their width. */
    const val TREND_MIN_ROW_WIDTH = 380

    fun showTrend(cardWidth: Float, card: IndexCardModel): Boolean = card.available && card.trend.size >= 2 && cardWidth >= TREND_MIN_ROW_WIDTH

    const val INDEX_UNAVAILABLE = "Not available right now"
    const val INDEX_HINT = "Explains this index"

    // ---------- Daily Market Brief ----------

    /** "Latest available · Oct 7 · 1 min read" (freshness and reading time only). */
    fun briefDetail(brief: DailyBrief, nowMillis: Long): String =
        "${BriefFormat.freshness(brief, nowMillis)} · ${brief.readingMinutes} min read"

    /** "Sample data" / "Offline copy" on their own line; null for a live, current brief. */
    fun briefLabels(brief: DailyBrief, offline: Boolean): String? =
        listOfNotNull("Offline copy".takeIf { offline }, "Sample data".takeIf { brief.sampleData }).joinToString(" · ").ifEmpty { null }

    // ---------- Sectors ----------

    /** Short caption under the bars (the period); the ETF-proxy methodology is one tap away via [methodologyLesson]. */
    fun sectorsCaption(sectors: SectorsModel): String = "${sectors.period}. Measured with sector ETFs."

    fun methodologyLesson(sectors: SectorsModel): MarketLesson =
        MarketLesson("sector-methodology", "How sector performance is measured", sectors.methodology, listOf(sectors.period))

    const val SECTORS_INFO = "How sector performance is measured"

    data class ResearchTool(val id: String, val title: String, val detail: String)

    val DISCOVER = ResearchTool("discover", "Discover Stocks", "Find companies by growth, dividends, strength or valuation")
    val COMPARE = ResearchTool("compare", "Compare Companies", "See two or three companies side by side")
    const val EARNINGS_TITLE = "Earnings Calendar"
    private const val EARNINGS_DEFAULT = "Upcoming earnings dates and reports"

    /** Calendar counts only (never estimated); the plain description while loading or when counts are unknown. */
    fun earningsDetail(summary: EarningsSummaryState?): String = summary?.headline ?: EARNINGS_DEFAULT
    fun earningsSecondary(summary: EarningsSummaryState?): String? = summary?.watchlistText
}
