package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.GlanceMetric
import org.example.stocksteps.companydetail.InfoRow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.presentation.markets.MarketsScreenPresentation

/**
 * Wording, grouping and de-duplication for Company Details (Global UI Refinement Phase 4C), shared by Compose and SwiftUI. Formatting only:
 * every value, definition and calculation comes from the shared `CompanyOverview`; nothing is computed or estimated here.
 */
object CompanyDetailsPresentation {

    // ---------- Market status chip (Phase 4B.1 semantics) ----------

    /** "Open" / "Pre-market" / "After-hours" / "Closed"; null for UNKNOWN (status is never guessed from the device clock). */
    fun statusLabel(status: MarketStatus): String? = when (status) {
        MarketStatus.OPEN -> "Open"
        MarketStatus.PRE_MARKET -> "Pre-market"
        MarketStatus.AFTER_HOURS -> "After-hours"
        MarketStatus.CLOSED -> "Closed"
        MarketStatus.UNKNOWN -> null
    }

    /** Spoken form: "US market: after-hours trading". */
    fun statusDescription(status: MarketStatus): String? = when (status) {
        MarketStatus.OPEN -> "US market open"
        MarketStatus.PRE_MARKET -> "US market: pre-market trading"
        MarketStatus.AFTER_HOURS -> "US market: after-hours trading"
        MarketStatus.CLOSED -> "US market closed"
        MarketStatus.UNKNOWN -> null
    }

    /** Green only while open, amber for extended hours, neutral when closed — closed is not a loss. */
    fun statusDot(status: MarketStatus): MarketsScreenPresentation.StatusDot = when (status) {
        MarketStatus.OPEN -> MarketsScreenPresentation.StatusDot.OPEN
        MarketStatus.PRE_MARKET, MarketStatus.AFTER_HOURS -> MarketsScreenPresentation.StatusDot.EXTENDED
        MarketStatus.CLOSED, MarketStatus.UNKNOWN -> MarketsScreenPresentation.StatusDot.NEUTRAL
    }

    // ---------- Price ----------

    /**
     * One spoken sentence for the price block: "Price $336.67. Up $3.04, 0.91%, today. Updated 4:00 PM EDT · Oct 7." The direction is in
     * words (never colour alone); a missing price or change is said to be unavailable, never zero.
     */
    fun priceDescription(overview: CompanyOverview, updated: String?): String {
        val price = overview.price?.let { "Price $it." } ?: "Price unavailable."
        val amount = overview.changeAmount?.trimStart('+', '-', '−')
        val percent = overview.changePercent.trimStart('+', '-', '−')
        val change = when (overview.direction) {
            PriceDirection.UP -> "Up ${listOfNotNull(amount, percent).joinToString(", ")}, today."
            PriceDirection.DOWN -> "Down ${listOfNotNull(amount, percent).joinToString(", ")}, today."
            PriceDirection.UNCHANGED -> "Unchanged today."
            PriceDirection.UNAVAILABLE -> "Today's change unavailable."
        }
        return listOfNotNull(price, change, updated?.let { "Updated $it." }).joinToString(" ")
    }

    // ---------- At a Glance ----------

    /** A glance metric split for display: the complete number as the value, any qualifier moved into the supporting line. */
    data class GlanceDisplay(val value: String, val helper: String)

    private val QUALIFIERS = mapOf("YoY" to "Year over year")

    /**
     * "+14.9% (YoY)" → value "+14.9%", helper "Year over year · Growing revenue", so the number is never cut ("+14.9 % (Y…").
     * Values without a trailing qualifier are unchanged.
     */
    fun glance(metric: GlanceMetric): GlanceDisplay {
        val match = Regex("""^(.+?) \(([^()]+)\)$""").matchEntire(metric.value.trim())
        if (match == null) return GlanceDisplay(metric.value, metric.helper)
        val qualifier = match.groupValues[2].let { QUALIFIERS[it] ?: it }
        return GlanceDisplay(match.groupValues[1], listOf(qualifier, metric.helper).filter { it.isNotBlank() }.joinToString(" · "))
    }

    /** Quote stats under the chart without Market Cap and P/E, which At a Glance already shows. */
    fun chartStats(overview: CompanyOverview): List<InfoRow> = overview.quickStats.filter { it.label !in CHART_STATS_IN_GLANCE }
    private val CHART_STATS_IN_GLANCE = setOf("Market Cap", "P/E")

    /** Key ratios without the ones At a Glance already shows (same label), so a value appears once. */
    fun keyRatios(overview: CompanyOverview): List<InfoRow> {
        val shown = overview.glance.map { it.label }.toSet()
        return overview.keyRatios.filter { it.label !in shown }
    }

    // ---------- Understand the company ----------

    const val UNDERSTAND_TITLE = "Understand"
    fun understandTitle(overview: CompanyOverview): String = "Understand ${overview.shortName}"

    /** Sector and industry as label/value rows (shown once, in About, instead of a separate section). */
    fun classification(overview: CompanyOverview): List<InfoRow> =
        listOfNotNull(overview.sector?.let { InfoRow("Sector", it) }, overview.industry?.let { InfoRow("Industry", it) })

    // ---------- Section titles ----------

    const val VALUATION_HEALTH_TITLE = "Valuation & financial health"
    const val RESEARCH_TITLE = "Research further"
}
