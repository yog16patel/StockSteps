package org.example.stocksteps.presentation.markets

import org.example.stocksteps.earnings.EarningsSummaryState
import org.example.stocksteps.markets.IndexCardModel
import org.example.stocksteps.markets.MarketHeaderModel
import org.example.stocksteps.markets.SessionTone

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

    /** "US stocks (NYSE, Nasdaq) · Updated 5:15 PM ET · Oct 7" — which market and how fresh, in one muted line. */
    fun statusMeta(header: MarketHeaderModel, market: String?): String? =
        listOfNotNull(market?.takeIf { it.isNotBlank() }, header.updated).joinToString(" · ").ifEmpty { null }

    /** "-18.59 (-0.24%)", or just the percentage when the point change is missing. */
    fun indexChange(card: IndexCardModel): String = card.change?.let { "$it (${card.percent})" } ?: card.percent

    /** "As of 4:00 PM ET · Oct 7 · Proxy: SPY fund price" — the source quote time and proxy note, never a computed freshness. */
    fun indexMeta(card: IndexCardModel): String? = listOfNotNull(card.updated, card.proxyLabel).joinToString(" · ").ifEmpty { null }

    /** Index cards narrower than this (dp/pt) leave out the trend sparkline so the name and quote time keep their width. */
    const val TREND_MIN_ROW_WIDTH = 380

    fun showTrend(cardWidth: Float, card: IndexCardModel): Boolean = card.available && card.trend.size >= 2 && cardWidth >= TREND_MIN_ROW_WIDTH

    const val INDEX_UNAVAILABLE = "Not available right now"
    const val INDEX_HINT = "Explains this index"

    data class ResearchTool(val id: String, val title: String, val detail: String)

    val DISCOVER = ResearchTool("discover", "Discover Stocks", "Find companies by growth, dividends, strength or valuation")
    val COMPARE = ResearchTool("compare", "Compare Companies", "See two or three companies side by side")
    const val EARNINGS_TITLE = "Earnings Calendar"
    private const val EARNINGS_DEFAULT = "See when companies are reporting results"

    /** Calendar counts only (never estimated); the plain description while loading or when counts are unknown. */
    fun earningsDetail(summary: EarningsSummaryState?): String = summary?.headline ?: EARNINGS_DEFAULT
    fun earningsSecondary(summary: EarningsSummaryState?): String? = summary?.watchlistText
}
