package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import org.example.stocksteps.designsystem.icons.StockIcons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Themed text input (watchlist names, notes, alert thresholds): label above, optional supporting
 * text or error below (announced to screen readers), 48dp minimum height.
 */
@Composable
internal fun StockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    enabled: Boolean = true,
    /** Portfolio D1: keyboard action key (Next / Done); the defaults keep every other caller unchanged. */
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.chip
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.labelValueGap)) {
        Text(label, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(shape)
                .background(colors.surfaceSecondary)
                .border(StockStepsTheme.dimensions.border, if (error != null) colors.negative else colors.borderSubtle, shape)
                .padding(horizontal = spacing.md, vertical = spacing.sm)
        ) {
            prefix?.let { Text(it, Modifier.padding(end = spacing.xs), style = StockStepsTheme.typography.body, color = colors.textSecondary) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = StockStepsTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
                decorationBox = { input ->
                    Box {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = StockStepsTheme.typography.body, color = colors.textTertiary)
                        input()
                    }
                }
            )
        }
        (error ?: supporting)?.let { Text(it, style = StockStepsTheme.typography.caption, color = if (error != null) colors.negativeText else colors.textTertiary) }
    }
}

/**
 * Portfolio D1: a date field styled like [StockTextField] that opens a picker on tap (the caller shows it). [value] is the readable date;
 * announced as a button with the label, value and any error; 48dp minimum height.
 */
@Composable
internal fun StockDateField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, placeholder: String? = null,
                            error: String? = null, supporting: String? = null, actionLabel: String = "Choose date") {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.chip
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.labelValueGap)) {
        Text(label, Modifier.clearAndSetSemantics {}, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(shape)
                .background(colors.surfaceSecondary)
                .border(StockStepsTheme.dimensions.border, if (error != null) colors.negative else colors.borderSubtle, shape)
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
                .semantics {
                    contentDescription = "$label, ${value.ifBlank { placeholder.orEmpty() }}"
                    if (error != null) error(error)
                }
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value.ifBlank { placeholder.orEmpty() }, Modifier.weight(1f), style = StockStepsTheme.typography.body,
                color = if (value.isBlank()) colors.textTertiary else colors.textPrimary)
            Icon(StockIcons.Calendar, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
        }
        (error ?: supporting)?.let { Text(it, style = StockStepsTheme.typography.caption, color = if (error != null) colors.negativeText else colors.textTertiary) }
    }
}
