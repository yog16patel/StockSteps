package org.example.stocksteps.companydetail

import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PricePoint

/** Everything a price chart shows besides the line itself, shared by Compose and SwiftUI. */
data class ChartSummary(
    val closes: List<Double>,
    /** Change over the selected range, e.g. "+12.40% past 1Y". */
    val change: String,
    val direction: PriceDirection,
    /** Right-axis labels, top to bottom (high, middle, low). */
    val yLabels: List<String>,
    /** Bottom-axis labels, left to right, evenly spaced. */
    val xLabels: List<String>,
    /** Screen-reader summary of the whole chart. */
    val description: String
)

/**
 * Price-chart labels from backend points ("yyyy-MM-dd HH:mm:ss" for intraday, "yyyy-MM-dd"
 * for daily). Pure string handling, so no time-zone or date library is involved; times are
 * shown exactly as the backend reports them (exchange time).
 */
object ChartPresentation {
    private const val X_LABELS = 4
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun summary(points: List<PricePoint>, range: ChartRange, dayDirection: PriceDirection? = null): ChartSummary? {
        val closes = points.map { it.close }.filter { it.isFinite() }
        if (closes.size < 2) return null
        val first = closes.first()
        val last = closes.last()
        val percent = if (first > 0) (last - first) / first * 100 else null
        // 1D follows today's quote change so the line colour matches the header.
        val direction = if (range == ChartRange.ONE_DAY && dayDirection != null) dayDirection else HomePresentation.direction(percent)
        val high = closes.max()
        val low = closes.min()
        val change = "${HomePresentation.percent(percent)} ${if (range == ChartRange.ONE_DAY) "today" else "past ${range.label}"}"
        return ChartSummary(
            closes = closes,
            change = change,
            direction = direction,
            yLabels = listOf(high, (high + low) / 2, low).map(::axisPrice),
            xLabels = xLabels(points, range),
            description = "Price chart, ${range.label}: from ${axisPrice(first)} to ${axisPrice(last)}, ${HomePresentation.percent(percent)}."
        )
    }

    /** Label for a touched point: time/date plus price. */
    fun scrubLabel(point: PricePoint, range: ChartRange): String =
        "${fullTime(point.time, range)} • ${HomePresentation.price(point.close, "USD") ?: axisPrice(point.close)}"

    private fun xLabels(points: List<PricePoint>, range: ChartRange): List<String> {
        if (points.size < X_LABELS) return points.map { shortTime(it.time, range) }
        val step = (points.size - 1).toDouble() / (X_LABELS - 1)
        return (0 until X_LABELS).map { index -> shortTime(points[(index * step).toInt().coerceAtMost(points.lastIndex)].time, range) }
    }

    private fun shortTime(time: String, range: ChartRange): String = when (range) {
        ChartRange.ONE_DAY -> clock(time) ?: time
        ChartRange.ONE_WEEK, ChartRange.ONE_MONTH, ChartRange.THREE_MONTHS -> monthDay(time) ?: time
        ChartRange.ONE_YEAR -> month(time)?.let { m -> year(time)?.let { "$m '${it.takeLast(2)}" } ?: m } ?: time
        ChartRange.FIVE_YEARS, ChartRange.ALL -> year(time) ?: time
    }

    private fun fullTime(time: String, range: ChartRange): String =
        if (range == ChartRange.ONE_DAY) clock(time) ?: time
        else listOfNotNull(monthDay(time), year(time)).joinToString(", ").ifEmpty { time }

    /** "2026-10-07 09:30:00" → "9:30 AM". */
    private fun clock(time: String): String? {
        val parts = time.substringAfter(' ', "").split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1) ?: return null
        val suffix = if (hour >= 12) "PM" else "AM"
        val twelve = (hour % 12).let { if (it == 0) 12 else it }
        return "$twelve:$minute $suffix"
    }

    private fun month(time: String): String? = time.split('-').getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) }
    private fun year(time: String): String? = time.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
    private fun monthDay(time: String): String? {
        val day = time.split('-').getOrNull(2)?.take(2)?.toIntOrNull() ?: return null
        return month(time)?.let { "$it $day" }
    }

    private fun axisPrice(value: Double) = HomePresentation.price(value, null) ?: ""
}
