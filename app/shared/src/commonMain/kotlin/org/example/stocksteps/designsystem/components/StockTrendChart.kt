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
    comparisonLabel: String? = null
) {
    val valid = values.filterNotNull() + comparison.orEmpty().filterNotNull()
    if (valid.isEmpty()) return
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val focus = selected ?: values.indexOfLast { it != null }
    val high = maxOf(valid.max(), reference ?: valid.max())
    val low = minOf(valid.min(), reference ?: valid.min())
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(details.getOrNull(focus).orEmpty(), Modifier.clearAndSetSemantics {}, style = typography.label, color = colors.textPrimary)
        if (comparison != null && seriesLabel != null && comparisonLabel != null) {
            Row(Modifier.padding(top = spacing.xxs).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text("— $seriesLabel", style = typography.caption, color = colors.primary)
                Text("- - $comparisonLabel", style = typography.caption, color = colors.textSecondary)
            }
        }
        if (reference != null && referenceLabel != null) {
            Text("- - $referenceLabel", Modifier.padding(top = spacing.xxs).clearAndSetSemantics {}, style = typography.caption, color = colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth().padding(top = spacing.sm).height(CHART_HEIGHT)) {
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
                    drawLine(colors.textTertiary, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                }
                // Draw each run of consecutive values separately so gaps stay visible.
                fun series(points: List<Double?>, color: androidx.compose.ui.graphics.Color, dashed: Boolean) {
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
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
                comparison?.let { series(it, colors.textSecondary, dashed = true) }
                series(values, colors.primary, dashed = false)
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
