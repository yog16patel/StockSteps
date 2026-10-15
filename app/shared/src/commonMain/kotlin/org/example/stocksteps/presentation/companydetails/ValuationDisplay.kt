package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.ValuationModel

/**
 * Valuation page layout (Global UI Refinement 5D.5.4C, C2), shared by Compose and SwiftUI. Display only: every value, label, badge and sentence
 * comes from the shared `ValuationModel`; nothing is computed, compared or reworded here.
 */
object ValuationDisplay {

    const val KEY_METRICS_TITLE = "Key Valuation Metrics"
    const val HISTORY_TITLE = "P/E vs History"
    const val UNDERSTANDING_TITLE = "Understanding P/E Ratio"

    /** D-V2 (approved copy): ratios are context, never a signal on their own. */
    const val FOOTNOTE = "Different ratios measure different things; none is a buy or sell signal on its own."

    /** One ratio tile: the model's own label and value, the first sentence of its existing description as the caption (D-V1). */
    data class KeyMetric(val label: String, val value: String, val caption: String?, val metricId: String?)

    /**
     * The ratio grid: P/E first (the model's own text — "N/A" with its reason elsewhere when earnings are negative or missing, never 0), then the
     * other ratios the model has, in its order. A ratio the model leaves out (missing, or not meaningful) has no tile.
     */
    fun keyMetrics(model: ValuationModel): List<KeyMetric> =
        listOf(KeyMetric(keepTogether("P/E Ratio"), model.currentPe, null, "pe")) +
            model.otherMetrics.map { row ->
                KeyMetric(keepTogether(row.label), row.value, row.helper?.let(CompanyDetailsPresentation::firstSentence), row.metricId)
            }

    /** The current / average / difference row of P/E vs History — only when the model has a reliable average (never implied otherwise). */
    data class Comparison(val currentLabel: String, val current: String, val averageLabel: String, val average: String, val difference: String?)

    fun comparison(model: ValuationModel): Comparison? =
        model.average?.let { avg -> Comparison("Current", model.currentPe, keepTogether(model.averageLabel), avg, model.difference) }

    /**
     * Keeps a ratio abbreviation whole ("P/E", "P/S") with invisible word joiners around the slash: at the largest text sizes a label wrapped as
     * "Average P/" + "E". The visible text is unchanged; [plain] removes the joiners.
     */
    fun keepTogether(label: String): String = SLASH_ABBREVIATION.replace(label) { "${it.groupValues[1]}\u2060/\u2060${it.groupValues[2]}" }

    fun plain(text: String): String = text.replace("\u2060", "")

    private val SLASH_ABBREVIATION = Regex("""(\w)/(\w)""")
}
