package org.example.stocksteps.service

import kotlin.math.abs
import kotlin.math.pow
import org.example.stocksteps.model.*

object FinancialCalculations {
    private const val MINIMUM_HISTORY_OBSERVATIONS = 3
    private const val HISTORY_WINDOW_YEARS = 5
    private const val MAXIMUM_HISTORY_DISPERSION = 10.0
    fun finite(value: Double?): Double? = value?.takeIf { it.isFinite() }
    fun divide(numerator: Double?, denominator: Double?): Double? {
        val n = finite(numerator) ?: return null
        val d = finite(denominator)?.takeIf { it > 0 } ?: return null
        return finite(n / d)
    }
    fun yoy(current: Double?, previous: Double?): Double? {
        val c = finite(current) ?: return null
        val p = finite(previous)?.takeIf { it != 0.0 } ?: return null
        return finite((c - p) / abs(p) * 100)
    }
    fun cagr(current: Double?, beginning: Double?, years: Int): Double? {
        if (years <= 0) return null
        val c = finite(current)?.takeIf { it > 0 } ?: return null
        val b = finite(beginning)?.takeIf { it > 0 } ?: return null
        return finite(((c / b).pow(1.0 / years) - 1) * 100)
    }
    /** Provider outflows are negative. Unexpected positive CapEx cannot support a fallback. */
    fun capexSpending(raw: Double?): Double? = finite(raw)?.takeIf { it <= 0 }?.let { -it }
    fun freeCashFlow(direct: Double?, operating: Double?, capex: Double?): Double? =
        finite(direct) ?: capexSpending(capex)?.let { spending -> finite(operating)?.let { finite(it - spending) } }
    fun margin(amount: Double?, revenue: Double?): Double? = divide(amount, revenue)?.let { finite(it * 100) }
    fun historical(current: Double?, observations: List<FinancialObservation>): HistoricalComparison {
        val ordered = observations.sortedByDescending { it.year }.take(HISTORY_WINDOW_YEARS)
        val values = ordered.mapNotNull { finite(it.value)?.takeIf { v -> v > 0 } }.sorted()
        val mean = if (values.isEmpty()) null else finite(values.sum() / values.size)
        val median = if (values.isEmpty()) null else if (values.size % 2 == 1) values[values.size / 2]
            else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
        // A >10x range is flagged, never silently winsorized or discarded.
        val dispersed = values.size >= MINIMUM_HISTORY_OBSERVATIONS && values.last() / values.first() > MAXIMUM_HISTORY_DISPERSION
        val reliable = values.size >= MINIMUM_HISTORY_OBSERVATIONS && mean != null && !dispersed
        val difference = if (reliable && current != null && current > 0) yoy(current, mean) else null
        return HistoricalComparison(
            observations = ordered,
            average = mean?.takeIf { values.size >= MINIMUM_HISTORY_OBSERVATIONS },
            median = median,
            minimum = values.firstOrNull(),
            maximum = values.lastOrNull(),
            validCount = values.size,
            differencePercent = difference,
            reliable = reliable,
            note = when {
                dispersed -> "Historical multiples span more than 10×; the comparison is unreliable."
                values.size < MINIMUM_HISTORY_OBSERVATIONS -> "Fewer than three valid fiscal-year observations."
                values.size < HISTORY_WINDOW_YEARS -> "Average of ${values.size} valid observations in the five-year window."
                else -> "Average of five fiscal-year observations, not a daily five-year average."
            }
        )
    }
}
