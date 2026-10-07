package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Where today's price sits between a low and a high (day range, 52-week range). The marker
 * position is the only graphic; the low/high labels and description carry the values.
 */
@Composable
internal fun StockRangeBar(title: String, low: String, high: String, position: Float, description: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val dims = StockStepsTheme.dimensions
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)
    ) {
        Text(title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
        Canvas(Modifier.fillMaxWidth().height(dims.rangeMarker)) {
            val bar = dims.rangeBar.toPx()
            val top = (size.height - bar) / 2
            val radius = CornerRadius(bar / 2)
            drawRoundRect(colors.borderSubtle, Offset(0f, top), Size(size.width, bar), radius)
            val x = (size.width * position.coerceIn(0f, 1f))
            val start = (x - size.width * FILL_FRACTION).coerceAtLeast(0f)
            drawRoundRect(colors.positive, Offset(start, top), Size(x - start, bar), radius)
            drawCircle(colors.textPrimary, dims.rangeMarker.toPx() / 2, Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2))
        }
        Row {
            Text(low, style = StockStepsTheme.typography.small, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(high, style = StockStepsTheme.typography.small, color = colors.textSecondary)
        }
    }
}

private const val FILL_FRACTION = 0.15f
