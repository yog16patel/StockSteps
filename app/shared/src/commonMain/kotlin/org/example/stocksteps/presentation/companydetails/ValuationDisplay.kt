package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.InfoRow
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
        listOf(KeyMetric(keepTogether("P/E Ratio"), model.currentPe, null, PE_METRIC_ID)) +
            model.otherMetrics.map { row ->
                KeyMetric(keepTogether(row.label), row.value, row.helper?.let(CompanyDetailsPresentation::firstSentence), row.metricId)
            }

    /** The current / average / difference row of P/E vs History — only when the model has a reliable average (never implied otherwise). */
    data class Comparison(val currentLabel: String, val current: String, val averageLabel: String, val average: String, val difference: String?)

    fun comparison(model: ValuationModel): Comparison? =
        model.average?.let { avg -> Comparison("Current", model.currentPe, keepTogether(model.averageLabel), avg, model.difference) }

    /**
     * Valuation & Growth (C3): shown exactly when it was before (the model has more than one row), without the model's P/E row — P/E is the first
     * key-metric tile and leads P/E vs History, so a third copy added nothing. Growth rows keep the model's order, values and directions.
     */
    fun growthVisible(model: ValuationModel): Boolean = model.growth.size > 1

    fun growthRows(model: ValuationModel): List<InfoRow> = model.growth.filterNot { it.metricId == PE_METRIC_ID }

    private const val PE_METRIC_ID = "pe"

    /**
     * Keeps a ratio abbreviation whole ("P/E", "P/S") with invisible word joiners around the slash: at the largest text sizes a label wrapped as
     * "Average P/" + "E". C3: the page's model sentences go through it too (the Insight read "current P/" + "E is close" at AX5). The
     * visible text is unchanged; [plain] removes the joiners.
     */
    fun keepTogether(text: String): String = SLASH_ABBREVIATION.replace(text) { "${it.groupValues[1]}\u2060/\u2060${it.groupValues[2]}" }

    fun plain(text: String): String = text.replace("\u2060", "")

    private val SLASH_ABBREVIATION = Regex("""(\w)/(\w)""")
}
