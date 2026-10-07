package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Beginner price chart: one line with a soft area fill, right-side price labels and bottom
 * time labels. Touching or dragging reveals the point under the finger via [scrubLabel]
 * (shown in [header] position by the caller). No indicators, candles or volume.
 */
@Composable
internal fun StockLineChart(
    closes: List<Double>,
    direction: PriceDirection,
    yLabels: List<String>,
    xLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    onScrub: (Int?) -> Unit = {}
) {
    if (closes.size < 2) return
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val line = when (direction) {
        PriceDirection.UP -> colors.positive
        PriceDirection.DOWN -> colors.negative
        else -> colors.primary
    }
    val stroke = StockStepsTheme.dimensions.sparklineStroke * 1.5f
    val marker = StockStepsTheme.dimensions.statusDot
    var touched by remember(closes) { mutableStateOf<Int?>(null) }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clearAndSetSemantics {}
                    .pointerInput(closes) {
                        fun index(x: Float) = ((x / size.width) * closes.lastIndex).toInt().coerceIn(0, closes.lastIndex)
                        detectTapGestures(
                            onPress = { offset ->
                                touched = index(offset.x).also(onScrub)
                                tryAwaitRelease()
                                touched = null
                                onScrub(null)
                            }
                        )
                    }
                    .pointerInput(closes) {
                        fun index(x: Float) = ((x / size.width) * closes.lastIndex).toInt().coerceIn(0, closes.lastIndex)
                        detectDragGestures(
                            onDragStart = { touched = index(it.x).also(onScrub) },
                            onDrag = { change, _ -> touched = index(change.position.x).also(onScrub) },
                            onDragEnd = { touched = null; onScrub(null) },
                            onDragCancel = { touched = null; onScrub(null) }
                        )
                    }
            ) {
                val width = stroke.toPx()
                val inset = marker.toPx()
                val min = closes.min()
                val range = (closes.max() - min).takeIf { it > 0 } ?: 1.0
                val usable = size.height - inset * 2
                val step = size.width / closes.lastIndex
                fun point(index: Int) = Offset(index * step, (inset + usable * (1 - (closes[index] - min) / range)).toFloat())
                val path = Path().apply {
                    closes.indices.forEach { if (it == 0) moveTo(point(it).x, point(it).y) else lineTo(point(it).x, point(it).y) }
                }
                val area = Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = AREA_ALPHA), line.copy(alpha = 0f))))
                drawPath(path, line, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val focus = touched ?: closes.lastIndex
                val dot = point(focus)
                if (touched != null) drawLine(colors.border, Offset(dot.x, 0f), Offset(dot.x, size.height), strokeWidth = width / 2)
                drawCircle(colors.surface, inset / 1.5f, dot)
                drawCircle(line, inset / 2.2f, dot)
            }
            Column(
                Modifier.fillMaxHeight().padding(start = StockStepsTheme.spacing.sm).clearAndSetSemantics {},
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                yLabels.forEach { Text(it, style = typography.tiny, color = colors.textTertiary, textAlign = TextAlign.End) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = StockStepsTheme.spacing.xs).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { Text(it, style = typography.tiny, color = colors.textTertiary, maxLines = 1) }
        }
    }
}

private const val AREA_ALPHA = 0.22f
