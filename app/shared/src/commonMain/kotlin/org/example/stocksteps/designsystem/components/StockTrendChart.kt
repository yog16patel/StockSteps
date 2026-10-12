package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockChartSeriesStyle

/**
 * Line chart for a series with gaps (one slot per period; null = no valid value). Segments are
 * never joined across a gap. An optional dashed [reference] line marks an average, and an optional
 * dashed [comparison] series (same slots, e.g. a benchmark) is drawn in a second color with a legend
 * so the lines differ by pattern as well as color. Touching or dragging shows the slot's [details];
 * [description] is read by screen readers.
 */
@Composable
internal fun StockTrendChart(
    values: List<Double?>,
    details: List<String>,
    xLabels: List<String>,
    yLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    reference: Double? = null,
    referenceLabel: String? = null,
    comparison: List<Double?>? = null,
    seriesLabel: String? = null,
    comparisonLabel: String? = null,
    /** More comparison series (label, values): each has its own color and dash pattern. */
    additional: List<Pair<String, List<Double?>>> = emptyList(),
    /** Slots marked with a dashed vertical line (e.g. an earnings event). */
    markers: List<Int> = emptyList(),
    /** Slots drawn as hollow rings (e.g. baseline and endpoint observations). */
    highlights: List<Int> = emptyList(),
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT,
    /** Optional replacement for the one-line [details] above the chart (receives the focused slot); decorative for screen readers. */
    selectionContent: (@Composable (Int) -> Unit)? = null
) {
    val valid = values.filterNotNull() + comparison.orEmpty().filterNotNull() + additional.flatMap { it.second.filterNotNull() }
    if (valid.isEmpty()) return
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val focus = selected ?: values.indexOfLast { it != null }
    val rawHigh = maxOf(valid.max(), reference ?: valid.max())
    val rawLow = minOf(valid.min(), reference ?: valid.min())
    // A flat series (every value equal) is drawn through the middle, not along the bottom edge (Phase 3).
    val flat = rawHigh <= rawLow
    val high = if (flat) rawHigh + 1.0 else rawHigh
    val low = if (flat) rawLow - 1.0 else rawLow
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        if (selectionContent != null) Box(Modifier.clearAndSetSemantics {}) { selectionContent(focus) }
        else Text(details.getOrNull(focus).orEmpty(), Modifier.clearAndSetSemantics {}, style = typography.label, color = colors.textPrimary)
        // Shared line styles (StockChartSeriesStyle): the legend draws each line's real colour and dash pattern as a swatch; labels use readable
        // text colours (the orange series colour is too light for caption text). Series 0 = [values], then [comparison], then [additional].
        val palette = colors.palette
        val extraOffset = if (comparison != null) 2 else 1
        val referenceLine = StockChartSeriesStyle.reference(palette)
        if (seriesLabel != null && (comparison != null && comparisonLabel != null || additional.isNotEmpty())) {
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = spacing.xxs).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockChartLegendItem(StockChartSeriesStyle.series(0, palette), seriesLabel)
                if (comparison != null && comparisonLabel != null) StockChartLegendItem(StockChartSeriesStyle.series(1, palette), comparisonLabel)
                additional.forEachIndexed { i, (label, _) -> StockChartLegendItem(StockChartSeriesStyle.series(i + extraOffset, palette), label) }
            }
        }
        if (reference != null && referenceLabel != null) {
            StockChartLegendItem(referenceLine, referenceLabel, Modifier.padding(top = spacing.xxs).clearAndSetSemantics {})
        }
        Row(Modifier.fillMaxWidth().padding(top = spacing.sm).height(height)) {
            Canvas(
                Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics {}
                    .pointerInput(values) {
                        fun index(x: Float) = ((x / size.width) * values.lastIndex).let { kotlin.math.round(it).toInt() }.coerceIn(0, values.lastIndex)
                        detectTapGestures(onPress = { selected = index(it.x); tryAwaitRelease() })
                    }
                    .pointerInput(values) {
                        fun index(x: Float) = ((x / size.width) * values.lastIndex).let { kotlin.math.round(it).toInt() }.coerceIn(0, values.lastIndex)
                        detectDragGestures(onDrag = { change, _ -> selected = index(change.position.x) })
                    }
            ) {
                val inset = 6.dp.toPx()
                val step = if (values.size > 1) size.width / values.lastIndex else 0f
                fun y(v: Double) = (inset + (size.height - inset * 2) * (1 - (v - low) / span)).toFloat()
                reference?.let {
                    drawLine(colors.rgb(referenceLine.color), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = referenceLine.width.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(referenceLine.dash.map { d -> d.dp.toPx() }.toFloatArray()))
                }
                // Draw each run of consecutive values separately so gaps stay visible.
                fun series(points: List<Double?>, color: androidx.compose.ui.graphics.Color, dashed: Boolean, pattern: FloatArray? = null) {
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = pattern?.let { PathEffect.dashPathEffect(it) } ?: if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
                    var path: Path? = null
                    points.forEachIndexed { i, v ->
                        if (v == null) {
                            path?.let { drawPath(it, color, style = stroke) }
                            path = null
                        } else {
                            val point = Offset(i * step, y(v))
                            path = (path ?: Path().apply { moveTo(point.x, point.y) }).apply { lineTo(point.x, point.y) }
                            if ((i == 0 || points[i - 1] == null) && (i == points.lastIndex || points[i + 1] == null)) drawCircle(color, 2.5.dp.toPx(), point)
                        }
                    }
                    path?.let { drawPath(it, color, style = stroke) }
                }
                fun styled(points: List<Double?>, index: Int) {
                    val line = StockChartSeriesStyle.series(index, palette)
                    series(points, colors.rgb(line.color), dashed = line.dash.isNotEmpty(), pattern = line.dash.takeIf { it.isNotEmpty() }?.map { d -> d.dp.toPx() }?.toFloatArray())
                }
                comparison?.let { styled(it, 1) }
                additional.forEachIndexed { i, (_, points) -> styled(points, i + extraOffset) }
                markers.filter { it in values.indices }.forEach { i ->
                    drawLine(colors.caution, Offset(i * step, 0f), Offset(i * step, size.height), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                }
                series(values, colors.primary, dashed = false)
                highlights.forEach { i -> values.getOrNull(i)?.let { v ->
                    drawCircle(colors.primary, 6.dp.toPx(), Offset(i * step, y(v)), style = Stroke(2.dp.toPx()))
                } }
                values.getOrNull(focus)?.let { v ->
                    val point = Offset(focus * step, y(v))
                    if (selected != null) drawLine(colors.border, Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = 1.dp.toPx())
                    drawCircle(colors.surface, 5.dp.toPx(), point)
                    drawCircle(colors.primary, 3.5.dp.toPx(), point)
                }
            }
            Column(Modifier.fillMaxHeight().padding(start = spacing.sm).clearAndSetSemantics {}, verticalArrangement = Arrangement.SpaceBetween) {
                yLabels.forEach { Text(it, style = typography.tiny, color = colors.textTertiary, textAlign = TextAlign.End) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = spacing.xs).clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { Text(it, style = typography.tiny, color = colors.textTertiary, maxLines = 1) }
        }
    }
}

private val CHART_HEIGHT = 150.dp

/** A short sample of a chart line (its colour and dash pattern), so a legend never relies on colour or a text glyph alone. Decorative. */
@Composable
internal fun StockChartSwatch(line: StockChartSeriesStyle.Line, modifier: Modifier = Modifier) {
    val color = StockStepsTheme.colors.rgb(line.color)
    Canvas(modifier.size(width = 20.dp, height = 8.dp).clearAndSetSemantics {}) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = maxOf(line.width, 1.5f).dp.toPx(), cap = StrokeCap.Round,
            pathEffect = line.dash.takeIf { it.isNotEmpty() }?.let { d -> PathEffect.dashPathEffect(d.map { it.dp.toPx() }.toFloatArray()) })
    }
}

/** Swatch + label (caption, `textSecondary`), kept together when a legend wraps. */
@Composable
internal fun StockChartLegendItem(line: StockChartSeriesStyle.Line, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        StockChartSwatch(line)
        Text(label, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
    }
}
