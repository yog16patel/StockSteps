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

    /** The label beside the daily change ("Today" / "Oct 8") and its spoken form ("today" / "on Oct 8"). */
    data class ChangeLabel(val text: String, val spoken: String)

    /** [changeLabel] for the overview's quote: its exchange date, the server's market status and today's exchange date. */
    fun changeLabel(overview: CompanyOverview): ChangeLabel =
        changeLabel(overview.marketStatus, overview.updatedAt?.let(::exchangeDate), currentExchangeDate())

    /**
     * Which session the daily change belongs to (Phase 4C.2). "Today" only when the quote's own exchange date is today's exchange date
     * **and** the server reports a known, non-pre-market session — the device clock alone never makes a quote "today", and before the
     * open the change still belongs to the previous session. Otherwise the quote's date ("Oct 8"); without a quote time, "Today" only while
     * the server says the market is open, else "Latest session". Dates are "yyyy-MM-dd" in exchange (New York) time.
     */
    fun changeLabel(status: MarketStatus, quoteDate: String?, today: String?): ChangeLabel {
        val sameDay = quoteDate != null && quoteDate == today && status != MarketStatus.PRE_MARKET && status != MarketStatus.UNKNOWN
        val openWithoutTime = quoteDate == null && status == MarketStatus.OPEN
        if (sameDay || openWithoutTime) return ChangeLabel("Today", "today")
        val date = quoteDate?.let(::monthDay) ?: return ChangeLabel("Latest session", "in the latest session")
        return ChangeLabel(date, "on $date")
    }

    /** "2026-10-08" → "Oct 8"; null when the date isn't in that form. */
    private fun monthDay(date: String): String? {
        val parts = date.split('-')
        val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return null
        val day = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return "$month $day"
    }
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * One spoken sentence for the price block: "Price $336.67. Up $3.04, 0.91%, today. Updated 4:00 PM EDT · Oct 7." The direction is in
     * words (never colour alone); a missing price or change is said to be unavailable, never zero; [change] names the session.
     */
    fun priceDescription(overview: CompanyOverview, updated: String?, change: ChangeLabel): String {
        val price = overview.price?.let { "Price $it." } ?: "Price unavailable."
        val amount = overview.changeAmount?.trimStart('+', '-', '−')
        val percent = overview.changePercent.trimStart('+', '-', '−')
        val sentence = when (overview.direction) {
            PriceDirection.UP -> "Up ${listOfNotNull(amount, percent).joinToString(", ")}, ${change.spoken}."
            PriceDirection.DOWN -> "Down ${listOfNotNull(amount, percent).joinToString(", ")}, ${change.spoken}."
            PriceDirection.UNCHANGED -> "Unchanged ${change.spoken}."
            PriceDirection.UNAVAILABLE -> "Change unavailable."
        }
        return listOfNotNull(price, sentence, updated?.let { "Updated $it." }).joinToString(" ")
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

    /**
     * Quote stats under the chart without Market Cap and P/E, which At a Glance already shows, and (5D.5.4A, D-E4) without High and Low
     * while the Day Range bar beside the chart shows them; without a day range they stay, so no value disappears.
     */
    fun chartStats(overview: CompanyOverview): List<InfoRow> = overview.quickStats.filter {
        it.label !in CHART_STATS_IN_GLANCE && (overview.dayRange == null || it.label !in CHART_STATS_IN_DAY_RANGE)
    }
    private val CHART_STATS_IN_GLANCE = setOf("Market Cap", "P/E")
    private val CHART_STATS_IN_DAY_RANGE = setOf("High", "Low")

    // ---------- Page sections (5D.5.4A) ----------

    /** In-page section bar entries (D-E1): each scrolls to its section on this page; none opens a new screen. */
    enum class Section(val label: String) { OVERVIEW("Overview"), FINANCIALS("Financials"), NEWS("News"), ABOUT("About") }

    /** The bar's entries in page order; Financials and About only when the page shows those sections (never a tab that scrolls nowhere). */
    fun sections(overview: CompanyOverview?): List<Section> = listOfNotNull(
        Section.OVERVIEW,
        Section.FINANCIALS.takeIf { overview != null },
        Section.NEWS,
        Section.ABOUT.takeIf { overview != null && hasAbout(overview) }
    )

    /** The About section exists when there is a description, country or classification. */
    fun hasAbout(overview: CompanyOverview): Boolean = overview.about != null || overview.country != null || overview.sector != null

    /**
     * The selected entry for the current scroll position, shared by both platforms: [tops] is each present section's top relative to the bottom
     * of the pinned bar (≤ [tolerance] means it reached the bar). The last section that reached it wins; Overview before any did. At the end of
     * the page ([atEnd]) the last section whose top is on screen ([viewport]) wins, so a short final section can still be selected.
     */
    fun selectedSection(tops: Map<Section, Float>, tolerance: Float, atEnd: Boolean = false, viewport: Float = Float.MAX_VALUE): Section {
        val ordered = Section.entries.filter { it in tops }
        if (atEnd) ordered.lastOrNull { (tops[it] ?: Float.MAX_VALUE) < viewport }?.let { return it }
        return ordered.lastOrNull { (tops[it] ?: Float.MAX_VALUE) <= tolerance } ?: Section.OVERVIEW
    }

    // ---------- What the company does (5D.5.4A) ----------

    /** "What Apple does": the first sentence of the existing description and "Sector · Industry"; null when neither exists. */
    data class WhatItDoes(val title: String, val summary: String?, val classification: String?)

    fun whatItDoes(overview: CompanyOverview): WhatItDoes? {
        val summary = overview.about?.let(::firstSentence)
        val classification = listOfNotNull(overview.sector, overview.industry).joinToString(" · ").ifBlank { null }
        if (summary == null && classification == null) return null
        return WhatItDoes("What ${overview.shortName} does", summary, classification)
    }

    /**
     * The first sentence of [text] (the existing description, never reworded): it ends at ".", "!" or "?" followed by a space and a capital,
     * except after a known abbreviation ("Apple Inc. is…"), an initialism ("U.S.") or a single initial; the whole text when no such end exists.
     */
    fun firstSentence(text: String): String? {
        val trimmed = text.trim().ifEmpty { return null }
        Regex("""[.!?](?=\s+[A-Z0-9“"])""").findAll(trimmed).forEach { match ->
            val before = trimmed.substring(0, match.range.first).substringAfterLast(' ').trimStart('(', '“', '"')
            if (before.length > 1 && '.' !in before && before.lowercase() !in ABBREVIATIONS) return trimmed.substring(0, match.range.last + 1)
        }
        return trimmed
    }
    private val ABBREVIATIONS = setOf("inc", "corp", "co", "ltd", "plc", "llc", "lp", "s.a", "n.v", "ag", "u.s", "u.k", "mr", "mrs", "ms", "dr", "st",
        "jr", "sr", "no", "vs", "etc", "e.g", "i.e", "approx", "dept", "est", "fig", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec")

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

    // ---------- Valuation ----------

    /**
     * The Valuation section's headline, or null when the Understand section's Valuation row already says the same sentence (Phase 4C.3: "Trading
     * near its historical P/E valuation." appeared twice). The section keeps its numbers and the interpretive explanation.
     */
    fun valuationHeadline(overview: CompanyOverview): String? =
        overview.valuation?.headline?.takeUnless { headline -> overview.assessment.any { it.explanation == headline } }

    // ---------- Section titles ----------

    const val VALUATION_HEALTH_TITLE = "Valuation & financial health"
    const val RESEARCH_TITLE = "Research further"
}
