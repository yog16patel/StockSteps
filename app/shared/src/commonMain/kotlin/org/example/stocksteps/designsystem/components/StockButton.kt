package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.theme.StockStepsTheme

internal enum class StockButtonVariant { PRIMARY, SECONDARY, OUTLINED, DESTRUCTIVE, TEXT }

@Composable
internal fun StockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StockButtonVariant = StockButtonVariant.PRIMARY,
    enabled: Boolean = true
) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.button
    val (container, content, border) = when (variant) {
        StockButtonVariant.PRIMARY -> Triple(colors.primary, colors.onPrimary, null)
        StockButtonVariant.SECONDARY -> Triple(colors.primaryContainer, colors.primaryText, null)
        StockButtonVariant.OUTLINED -> Triple(Color.Transparent, colors.primaryText, colors.border)
        StockButtonVariant.DESTRUCTIVE -> Triple(colors.negativeContainer, colors.negativeText, null)
        StockButtonVariant.TEXT -> Triple(Color.Transparent, colors.primaryText, null)
    }
    val background = if (!enabled && container != Color.Transparent) colors.surfaceSecondary else container
    val textColor = if (enabled) content else colors.textDisabled
    val horizontal = if (variant == StockButtonVariant.TEXT) StockStepsTheme.spacing.xs else StockStepsTheme.spacing.lg
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (variant == StockButtonVariant.TEXT) StockStepsTheme.dimensions.chipHeight else StockStepsTheme.dimensions.buttonHeight)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(StockStepsTheme.dimensions.border, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontal),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = StockStepsTheme.typography.bodyMedium, color = textColor)
    }
}
