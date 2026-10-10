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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import org.example.stocksteps.designsystem.theme.StockStepsTheme

internal data class StockSegment<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * Equal-width single-choice options (Light | Dark | System, Mock Data | Real Data). Selection is
 * informational blue, never positive/negative. Large text stacks the icon above the label.
 */
@Composable
internal fun <T> StockSegmentedControl(
    options: List<StockSegment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
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
        options.forEach { segment ->
            val isSelected = segment.value == selected
            val content = if (isSelected) colors.primaryText else colors.textSecondary
            val body: @Composable () -> Unit = {
                segment.icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall)) }
                Text(segment.label, style = if (isSelected) StockStepsTheme.typography.label.copy(fontWeight = FontWeight.SemiBold) else StockStepsTheme.typography.label,
                    color = content, textAlign = TextAlign.Center)
            }
            val itemModifier = Modifier
                .weight(1f)
                .heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .clip(shape)
                .background(if (isSelected) colors.primaryContainer else colors.surfaceSecondary)
                .border(StockStepsTheme.dimensions.border, if (isSelected) colors.primary else colors.borderSubtle, shape)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(segment.value) })
                .padding(horizontal = spacing.xs, vertical = spacing.sm)
            if (stacked) {
                Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically)) { body() }
            } else {
                Row(itemModifier, horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { body() }
            }
        }
    }
}
