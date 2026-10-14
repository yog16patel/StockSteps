package org.example.stocksteps.presentation.earnings

import org.example.stocksteps.earnings.Classification
import org.example.stocksteps.earnings.EarningsEducation
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
}
