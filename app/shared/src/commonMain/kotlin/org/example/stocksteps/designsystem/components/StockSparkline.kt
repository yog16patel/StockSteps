package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Minimal trend line from real closes (oldest first): no axes, no labels. Color follows
 * the row's change direction, which the adjacent text already states, so it is decorative.
 */
@Composable
internal fun StockSparkline(closes: List<Double>, direction: PriceDirection, modifier: Modifier = Modifier) {
    if (closes.size < 2) return
    val colors = StockStepsTheme.colors
    val color = when (direction) {
        PriceDirection.UP -> colors.positive
        PriceDirection.DOWN -> colors.negative
        else -> colors.iconSecondary
    }
    val strokeWidth = StockStepsTheme.dimensions.sparklineStroke
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val stroke = strokeWidth.toPx()
        val min = closes.min()
        val range = (closes.max() - min).takeIf { it > 0 } ?: 1.0
        val usableHeight = size.height - stroke
        val step = size.width / (closes.size - 1)
        val path = Path()
        closes.forEachIndexed { index, close ->
            val point = Offset(index * step, (stroke / 2 + usableHeight * (1 - (close - min) / range)).toFloat())
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
