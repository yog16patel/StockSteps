package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Search surface that opens the Search destination. It is deliberately not
 * editable: typing happens on the Search screen, which owns debounce/results.
 * The editable field variant arrives with the Search screen migration.
 */
@Composable
internal fun StockSearchEntry(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.searchHeight)
            .clip(shape)
            .background(colors.surface)
            .border(StockStepsTheme.dimensions.border, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        SearchGlyph(colors.iconSecondary)
        Text(
            text = placeholder,
            style = StockStepsTheme.typography.body,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Decorative magnifier drawn in common code; no icon library is bundled yet. */
@Composable
internal fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(StockStepsTheme.dimensions.iconSmall)) {
        val stroke = size.minDimension * GLYPH_STROKE
        val radius = size.minDimension * GLYPH_RADIUS
        val center = Offset(size.width * GLYPH_CENTER, size.height * GLYPH_CENTER)
        drawCircle(color, radius, center, style = Stroke(stroke))
        val handle = radius * HANDLE_START
        drawLine(
            color = color,
            start = Offset(center.x + handle, center.y + handle),
            end = Offset(size.width * HANDLE_END, size.height * HANDLE_END),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private const val GLYPH_STROKE = 0.1f
private const val GLYPH_RADIUS = 0.3f
private const val GLYPH_CENTER = 0.42f
private const val HANDLE_START = 0.72f
private const val HANDLE_END = 0.88f
