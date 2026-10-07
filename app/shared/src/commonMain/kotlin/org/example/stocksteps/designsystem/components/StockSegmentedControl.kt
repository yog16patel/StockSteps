package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import org.example.stocksteps.designsystem.theme.StockStepsTheme

internal data class StockSegment(val label: String, val icon: ImageVector? = null)

/**
 * Equal-width single-choice options (e.g. Light | Dark | System). Selection is informational
 * blue. Large text or narrow widths stack the icon above the label instead of clipping it.
 */
@Composable
internal fun StockSegmentedControl(
    segments: List<StockSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.button
    val stacked = LocalDensity.current.fontScale > StockStepsTheme.dimensions.largeFontScale
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        segments.forEachIndexed { index, segment ->
            val selected = index == selectedIndex
            val content = if (selected) colors.primaryText else colors.textSecondary
            val body: @Composable () -> Unit = {
                segment.icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall)) }
                Text(segment.label, style = StockStepsTheme.typography.label, color = content, textAlign = TextAlign.Center)
            }
            val itemModifier = Modifier
                .weight(1f)
                .heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .clip(shape)
                .background(if (selected) colors.primaryContainer else colors.surfaceSecondary)
                .border(StockStepsTheme.dimensions.border, if (selected) colors.primary else colors.borderSubtle, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                .padding(horizontal = spacing.xs, vertical = spacing.sm)
            if (stacked) {
                Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically)) { body() }
            } else {
                Row(itemModifier, horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { body() }
            }
        }
    }
}
