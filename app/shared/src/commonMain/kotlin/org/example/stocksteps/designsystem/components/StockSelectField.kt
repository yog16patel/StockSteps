package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Form picker matching [StockTextField]: label above, the selected option's readable label in a field, a menu of options,
 * supporting or error text below. [optionLabel] must return user-facing copy (`StockLabels.humanize` as a fallback), never enum names.
 */
@Composable
internal fun <T> StockSelectField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    enabled: Boolean = true
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.chip
    var expanded by remember { mutableStateOf(false) }
    val current = selected?.let(optionLabel)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.labelValueGap)) {
        Text(label, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(shape)
                    .background(colors.surfaceSecondary)
                    .border(StockStepsTheme.dimensions.border, if (error != null) colors.negative else colors.borderSubtle, shape)
                    .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                    .semantics {
                        contentDescription = label
                        stateDescription = current ?: placeholder.orEmpty()
                        if (error != null) error(error)
                    }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current ?: placeholder.orEmpty(), Modifier.weight(1f), style = StockStepsTheme.typography.body,
                    color = if (!enabled) colors.textDisabled else if (current != null) colors.textPrimary else colors.textTertiary)
                Icon(StockIcons.ChevronDown, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), style = StockStepsTheme.typography.body, color = colors.textPrimary) },
                        onClick = { expanded = false; onSelect(option) }
                    )
                }
            }
        }
        (error ?: supporting)?.let { Text(it, style = StockStepsTheme.typography.caption, color = if (error != null) colors.negativeText else colors.textTertiary) }
    }
}
