package org.example.stocksteps.presentation.earnings

import org.example.stocksteps.earnings.Classification
import org.example.stocksteps.earnings.EarningsEducation
import org.example.stocksteps.earnings.EarningsFormatter
import org.example.stocksteps.earnings.HistoryRowView
import org.example.stocksteps.earnings.MetricLine
import org.example.stocksteps.theme.StockBadgeKind

/** How one estimate comparison is shown: the words, the badge style and the spoken text. */
data class EarningsClassificationDisplay(val label: String, val kind: StockBadgeKind, val spoken: String)

/**
 * Phase 5D.1 (E-01): the one presentation of an EPS / revenue [Classification], used by Earnings Results and Earnings Details on Android and
 * iOS, so the same result never looks "good" on one screen and neutral on another. Display only: the classification comes from the server's
 * exact comparison and is never recomputed here. The label always carries the meaning (colour is secondary) and only describes the result
 * against the estimate, never whether the stock is a good investment.
 */
object EarningsDisplay {
    const val BEAT = "Beat estimate"
    const val MET = "Met estimate"
    const val MISSED = "Missed estimate"
    const val UNAVAILABLE = "Comparison unavailable"

    fun classification(classification: Classification): EarningsClassificationDisplay = when (classification) {
        Classification.BEAT -> EarningsClassificationDisplay(BEAT, StockBadgeKind.POSITIVE, "Result: beat the estimate")
        Classification.MET -> EarningsClassificationDisplay(MET, StockBadgeKind.NEUTRAL, "Result: met the estimate")
        Classification.MISS -> EarningsClassificationDisplay(MISSED, StockBadgeKind.NEGATIVE, "Result: missed the estimate")
        Classification.UNAVAILABLE -> EarningsClassificationDisplay(UNAVAILABLE, StockBadgeKind.NEUTRAL, "Comparison with the estimate unavailable")
    }

    // ---------- Phase 5D.2: shared copy and display choices for Earnings Details / Results (both platforms) ----------

    /** One heading for the price-reaction section on Details and Results (was "Price reaction" vs "How Did the Stock React?"). */
    const val REACTION_TITLE = "How did the stock react?"
    const val TAKEAWAY_TITLE = "Beginner takeaway"
    const val LEARN_TITLE = "Learn more"
    const val COMPANY_ACTION = "View company details"
    /** Shown instead of the EPS history chart while fewer than two quarters have a reported EPS (the rows stay below). */
    const val HISTORY_CHART_TOO_SHORT = "The chart appears once at least two quarters have reported EPS."

    /** Card heading: "EPS" is spelled out once for beginners; other titles are unchanged. */
    fun cardTitle(title: String): String = if (title == "EPS") "Earnings per share (EPS)" else title

    /** The education topic a Details result card already opens (unchanged destinations: EPS → surprise, revenue → revenue). */
    fun cardTopic(title: String): EarningsEducation.Topic? = EarningsEducation.topic(if (title == "EPS") "surprise" else "revenue")

    /** A link's label is the title of the topic it opens, so the words match the destination ("What is earnings surprise?"). */
    fun topicAction(key: String): String? = EarningsEducation.topic(key)?.title

    /** The EPS chart is drawn only with at least two reported values; otherwise [HISTORY_CHART_TOO_SHORT]. */
    fun historyChartShown(reported: List<Double?>): Boolean = reported.count { it != null } >= 2

    /** Collapsed history row: "EPS C$1.94 vs C$2.00 est. · −3.0%", the same amounts as the chart tooltip; falls back to the row's EPS text. */
    fun historyEpsLine(row: HistoryRowView): String = "EPS " + (row.epsVsEstimate ?: row.eps) + (row.surprise?.let { " · $it" } ?: "")

    /** Collapsed history row: "Revenue C$4.45B · +3.0% YoY". */
    fun historyRevenueLine(row: HistoryRowView): String = "Revenue ${row.revenue}" + (row.growth?.let { " · $it YoY" } ?: "")

    /** Expanded history row: the lines not already shown collapsed (the EPS comparison is on the collapsed row). */
    fun historyDetailLines(row: HistoryRowView): List<MetricLine> = if (row.epsVsEstimate != null) row.lines.filterNot { it.label == "EPS" } else row.lines

    // ---------- Phase 5D.3: visual polish (shared copy and choices; display only) ----------

    /** D-1 (owner-approved): a compact "Sample" badge beside the status replaces the in-page sample strip; the full sentence stays in the notes. */
    const val SAMPLE_BADGE = "Sample"
    const val SAMPLE_SENTENCE = "Sample earnings data for development, not real results."
    const val REACTION_DETAILS_TITLE = "How it's measured and source"
    const val RESULTS_REACTION_DETAILS_TITLE = "How to read this"
    const val DISCLOSURES_TITLE = "Sources and disclosures"
    const val EDUCATION_NOT_ADVICE = "This is education, not investment advice."
    const val PRICE_CAUSES = "Price moves have many causes. This shows what happened, not why, and isn't investment advice."

    /** Neutral icon per server insight category (never a direction cue): "percent", "chart", "layers", "info", else "lightbulb". */
    fun insightIcon(category: String): String = when (category) {
        "surprise" -> "percent"
        "growth" -> "chart"
        "trend" -> "layers"
        "mixed" -> "info"
        else -> "lightbulb"
    }

    /** The two numbers the Details takeaway shows (reported and estimated EPS), from the EPS card's key metrics. */
    fun takeawayMetrics(eps: org.example.stocksteps.earnings.ResultCard?): List<MetricLine> =
        eps?.keyMetrics?.take(2)?.map { MetricLine(if (it.label == "Reported") "Reported EPS" else "Estimated EPS", it.value) }.orEmpty()

    /** Results "Sources and disclosures": publication, sources, the sample sentence when sample data, then the education and price notes. */
    fun resultsDisclosures(publication: String?, sources: List<String>, sampleData: Boolean): List<String> =
        // distinct(): the server's notes may already carry the sample sentence; each note appears once.
        (listOfNotNull(publication) + sources + listOfNotNull(SAMPLE_SENTENCE.takeIf { sampleData }) + EDUCATION_NOT_ADVICE + PRICE_CAUSES).distinct()

    // ---------- Phase 5D.4: less scrolling, same content ----------

    const val DETAILS_NOT_ADVICE = "Not investment advice. Earnings results don't predict how a stock will move."

    /**
     * 5D4-2 (option A): a long server explanation shows its first sentence; the rest stays one tap away ("Show details"). Splits only at ". ",
     * "? " or "! " followed by a capital letter, so amounts ("C$1.94", "3.0%") and abbreviations never split. Returns (first, rest or null).
     */
    fun firstSentence(text: String): Pair<String, String?> {
        val match = Regex("""[.?!]\s+(?=[A-Z])""").find(text) ?: return text to null
        val end = match.range.first + 1
        return text.substring(0, end) to text.substring(match.range.last + 1).trim().ifEmpty { null }
    }

    /** Details notes folded like Results: the server notes, the sample sentence for sample data, the disclaimer — each once. */
    fun detailsDisclosures(notes: List<String>, sampleData: Boolean): List<String> =
        (notes + listOfNotNull(SAMPLE_SENTENCE.takeIf { sampleData }) + DETAILS_NOT_ADVICE).distinct()

    /**
     * History chart quarter labels: every quarter while up to 5 (3 at large text) fit, else first and last only — labels are spread evenly, so
     * they sit under their points only when all or just the ends are shown (a middle label would point at the wrong quarter).
     */
    fun historyXLabels(labels: List<String>, largeText: Boolean): List<String> =
        if (labels.size <= (if (largeText) 3 else 5)) labels else listOfNotNull(labels.firstOrNull(), labels.lastOrNull())

    /** Results title without the repeated word: "Q2 FY2026 Earnings Results" → "Q2 FY2026 results" (the screen is already "Earnings results"). */
    fun resultsTitle(title: String): String = title.removeSuffix(" Earnings Results").let { if (it == title) title else "$it results" }

    // ---------- Phase 5D.4.1: one "Show details" per card ----------

    /** A caveat that must stay visible outside "Show details": the unknown-currency caption (5D.1B), when [note] contains it. */
    fun visibleCaveat(note: String?): String? = note?.takeIf { EarningsFormatter.UNKNOWN_CURRENCY in it }?.let { EarningsFormatter.UNKNOWN_CURRENCY }

    /** [note] without the visible caveat (it is shown once, outside the details). */
    fun hiddenNote(note: String?): String? = note?.replace(EarningsFormatter.UNKNOWN_CURRENCY, "")?.trim()?.ifEmpty { null }

    /** Header line for the reported (or scheduled) quarter: "Reported · Wed, Jul 22 · After market close". */
    fun reportedLine(status: org.example.stocksteps.earnings.EarningsStatus?, date: String?, session: String?, dateStatus: String?): String? {
        val reported = status == org.example.stocksteps.earnings.EarningsStatus.REPORTED || status == org.example.stocksteps.earnings.EarningsStatus.PARTIALLY_REPORTED
        val parts = listOfNotNull(date, session, dateStatus.takeIf { !reported })
        return parts.takeIf { it.isNotEmpty() }?.let { (if (reported) listOf("Reported") + it else it).joinToString(" · ") }
    }
}
