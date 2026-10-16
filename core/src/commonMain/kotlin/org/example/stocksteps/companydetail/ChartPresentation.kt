package org.example.stocksteps.companydetail

import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.PricePoint

/** Everything a price chart shows besides the line itself, shared by Compose and SwiftUI. */
data class ChartSummary(
    val closes: List<Double>,
    /** Change over the selected range, e.g. "+12.40% past 1Y", "-1.87% from 9:30 AM on Oct 7" (1D), "+92.05% since 2021" (ALL). */
    val change: String,
    /**
     * Direction of the selected period's own first-to-last change (UI Phase 3, U3): the line colour, the [change] caption and the
     * [description] all follow it. On 1D it used to follow the day's quote change (vs the previous close), so a day that opened higher and
     * faded drew a green falling line under a "-1.87%" caption.
     */
    val direction: PriceDirection,
    /** Right-axis labels, top to bottom (high, middle, low). */
    val yLabels: List<String>,
    /** Bottom-axis labels, left to right, evenly spaced. */
    val xLabels: List<String>,
    /** Screen-reader summary of the whole chart: the same direction, change and period as the caption, then the prices. */
    val description: String
)

/**
 * Why a chart can't be drawn (Phase 4C.2). [NO_HISTORY] is an answer (the source has fewer than two prices for the range), so retrying is
 * pointless; [CONNECTION] and [TEMPORARY] are failures worth retrying.
 */
enum class ChartIssue { NO_HISTORY, CONNECTION, TEMPORARY }

/**
 * The compact state shown instead of the chart (short [title], one-sentence [message]); [canRetry] decides whether a Try again action is
 * offered. Phase 4C.3 added the title so both platforms render the same centered state.
 */
data class ChartNotice(val title: String, val message: String, val canRetry: Boolean)

/**
 * Price-chart labels from backend points ("yyyy-MM-dd HH:mm:ss" for intraday, "yyyy-MM-dd"
 * for daily). Pure string handling, so no time-zone or date library is involved; times are
 * shown exactly as the backend reports them (exchange time).
 */
object ChartPresentation {
    private const val X_LABELS = 4
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * The chart's caption, axes and spoken summary for the selected [range]. Everything describes that period's own first-to-last change
     * (the calculation is unchanged); the header's daily change (vs the previous close) is a different measure and keeps its own label.
     */
    fun summary(points: List<PricePoint>, range: ChartRange): ChartSummary? {
        val closes = points.map { it.close }.filter { it.isFinite() }
        if (closes.size < 2) return null
        val first = closes.first()
        val last = closes.last()
        val percent = if (first > 0) (last - first) / first * 100 else null
        val direction = HomePresentation.direction(percent)
        val period = period(points, range)
        val high = closes.max()
        val low = closes.min()
        val change = "${HomePresentation.percent(percent)} $period"
        return ChartSummary(
            closes = closes,
            change = change,
            direction = direction,
            yLabels = listOf(high, (high + low) / 2, low).map(::axisPrice),
            xLabels = xLabels(points, range),
            description = "Price chart, ${range.label}: ${spokenChange(direction, percent)} $period, from ${axisPrice(first)} to ${axisPrice(last)}."
        )
    }

    /**
     * Classifies a failed chart request: "not found" from the backend means no history for the range; no HTTP status means the backend
     * wasn't reached; anything else (server error, rate limit, provider outage) is temporary.
     */
    fun issue(cause: Throwable): ChartIssue {
        val failure = cause as? StockDataException ?: return ChartIssue.TEMPORARY
        return when {
            failure.code in NO_HISTORY_CODES -> ChartIssue.NO_HISTORY
            failure.status == null -> ChartIssue.CONNECTION
            else -> ChartIssue.TEMPORARY
        }
    }

    /** A successful response that can't be drawn (fewer than two finite closes) is "no history", never an error. */
    fun issue(chart: PriceChart): ChartIssue? = if (summary(chart.points, chart.range) == null) ChartIssue.NO_HISTORY else null

    fun notice(issue: ChartIssue): ChartNotice = when (issue) {
        ChartIssue.NO_HISTORY -> ChartNotice("No price history", "No prices are available for this period. Try another range.", canRetry = false)
        ChartIssue.CONNECTION -> ChartNotice("Couldn't load the chart", "Check your connection and try again.", canRetry = true)
        ChartIssue.TEMPORARY -> ChartNotice("Chart temporarily unavailable", "Price data isn't available right now. Try again in a moment.", canRetry = true)
    }

    private val NO_HISTORY_CODES = setOf("CHART_NOT_FOUND", "SYMBOL_NOT_FOUND")

    /**
     * UI Phase 3: what the change covers, from the points' own dates (wording only; the percentage is unchanged). 1D names its session ("on
     * Oct 7") — "today" was shown for an earlier session while the header said "Oct 7" — and ALL names its first year ("since 2021", not
     * "past ALL"); other ranges keep "past 1M".
     */
    private fun period(points: List<PricePoint>, range: ChartRange): String = when (range) {
        // 1D names its baseline (the first point's time), so it isn't read as the header's change since the previous close.
        ChartRange.ONE_DAY -> {
            val day = points.lastOrNull()?.time?.let(::monthDay)
            val start = points.firstOrNull()?.time?.let(::clock)
            when {
                day != null && start != null -> "from $start on $day"
                day != null -> "on $day"
                else -> "over the day"
            }
        }
        ChartRange.ALL -> points.firstOrNull()?.time?.let(::year)?.let { "since $it" } ?: "over all available history"
        else -> "past ${range.label}"
    }

    /** "down 1.87%" — the direction in words, never colour alone. */
    private fun spokenChange(direction: PriceDirection, percent: Double?): String = when (direction) {
        PriceDirection.UP -> "up ${HomePresentation.percent(percent).removePrefix("+")}"
        PriceDirection.DOWN -> "down ${HomePresentation.percent(percent).removePrefix("-")}"
        PriceDirection.UNCHANGED -> "unchanged"
        PriceDirection.UNAVAILABLE -> "change unavailable"
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
