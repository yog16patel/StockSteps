package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Compact single-choice pill filter (movers, periods, Annual/Quarterly): 34dp visual, 48dp touch target. Place in a `selectableGroup()`. */
@Composable
internal fun StockChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.pill
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = StockStepsTheme.dimensions.chipHeight)
            .clip(shape)
            // Strong blue selection; PrimaryDark keeps white text above 4.5:1 (Primary is 3.7:1).
            .background(if (selected) colors.primaryDark else colors.surfaceSecondary)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = StockStepsTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = StockStepsTheme.typography.label,
            color = if (selected) colors.onPrimary else colors.textSecondary,
            maxLines = 1
        )
    }
}
