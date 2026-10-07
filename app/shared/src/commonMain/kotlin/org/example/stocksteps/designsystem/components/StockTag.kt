package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Non-interactive neutral label (sector, industry, size band). */
@Composable
internal fun StockTag(text: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.pill
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceSecondary)
            .border(StockStepsTheme.dimensions.border, colors.borderSubtle, shape)
            .padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.caption,
        color = colors.textSecondary,
        maxLines = 1
    )
}

/** Short status label tinted by [tone]; the text always states the status, so color is never the only signal. */
@Composable
internal fun StockBadge(text: String, tone: FactTone, modifier: Modifier = Modifier) {
    val (container, content) = toneColors(tone)
    Text(
        text = text,
        modifier = modifier
            .clip(StockStepsTheme.shapes.pill)
            .background(container)
            .padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.label,
        color = content,
        maxLines = 1
    )
}

/** Decorative icon on a tinted rounded square. */
@Composable
internal fun StockIconTile(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(StockStepsTheme.dimensions.iconTile).clip(StockStepsTheme.shapes.chip).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}

/** Container/content colors for a semantic tone (contrast-safe text on each container). */
@Composable
internal fun toneColors(tone: FactTone): Pair<Color, Color> {
    val colors = StockStepsTheme.colors
    return when (tone) {
        FactTone.POSITIVE -> colors.positiveContainer to colors.positiveText
        FactTone.NEGATIVE -> colors.negativeContainer to colors.negativeText
        FactTone.CAUTION -> colors.warningContainer to colors.cautionText
        FactTone.NEUTRAL -> colors.surfaceSecondary to colors.textSecondary
    }
}
