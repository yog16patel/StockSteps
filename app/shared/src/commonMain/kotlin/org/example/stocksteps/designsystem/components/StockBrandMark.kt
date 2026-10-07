package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Compact brand row: the existing "S" tile from the login design plus the wordmark.
 * No bitmap logo asset exists in the project; replace the tile when one is supplied.
 */
@Composable
internal fun StockBrandMark(name: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val tile = typography.bodySemiBold
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(StockStepsTheme.dimensions.brandMark)
                .clip(StockStepsTheme.shapes.chip)
                .background(colors.primary)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text("S", style = tile.copy(fontSize = tile.fontSize / fontScale), color = colors.onPrimary)
        }
        Text(name, style = typography.cardTitle, color = colors.textPrimary)
    }
}
