package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.Res
import org.example.stocksteps.resources.action_retry
import org.example.stocksteps.resources.state_loading
import org.jetbrains.compose.resources.stringResource

/** Neutral block that stands in for text while a section loads. */
@Composable
internal fun StockSkeleton(modifier: Modifier = Modifier, shape: Shape = StockStepsTheme.shapes.chip) {
    Box(
        modifier = modifier
            .height(StockStepsTheme.dimensions.skeletonLine)
            .clip(shape)
            .background(StockStepsTheme.colors.surfaceSecondary)
    )
}

/** Wraps skeleton content so screen readers hear one "Loading" instead of empty shapes. */
@Composable
internal fun StockLoadingState(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val label = stringResource(Res.string.state_loading)
    Box(modifier = modifier.clearAndSetSemantics { contentDescription = label }) { content() }
}

/**
 * Legitimate empty content: calm, never alarming. Without [title] it is the compact inline message used inside sections;
 * with a [title] it is the full state (optional icon, title, explanation, primary action) for empty screens and lists.
 */
@Composable
internal fun StockEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    title: String? = null,
    icon: ImageVector? = null
) {
    if (title == null) SectionMessage(message, modifier, actionText, onAction)
    else FullStateMessage(title, message, icon, actionText, onAction, StockButtonVariant.PRIMARY, isError = false, modifier)
}

/**
 * Section-level failure with a retry. Messages are user-facing copy only;
 * provider/HTTP details belong in logs. With a [title] it renders the full state (retry as a secondary button).
 */
@Composable
internal fun StockErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null
) {
    val retry = onRetry?.let { stringResource(Res.string.action_retry) }
    if (title == null) SectionMessage(message, modifier, retry, onRetry)
    else FullStateMessage(title, message, icon, retry, onRetry, StockButtonVariant.SECONDARY, isError = true, modifier)
}

@Composable
private fun SectionMessage(message: String, modifier: Modifier, actionText: String?, onAction: (() -> Unit)?) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = message, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textSecondary)
        if (actionText != null && onAction != null) {
            StockButton(text = actionText, onClick = onAction, variant = StockButtonVariant.TEXT)
        }
    }
}

@Composable
private fun FullStateMessage(
    title: String,
    message: String,
    icon: ImageVector?,
    actionText: String?,
    onAction: (() -> Unit)?,
    actionVariant: StockButtonVariant,
    isError: Boolean,
    modifier: Modifier
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                Modifier.size(StockStepsTheme.dimensions.stateIcon).clip(StockStepsTheme.shapes.card)
                    .background(if (isError) colors.negativeContainer else colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isError) colors.negativeText else colors.primaryText,
                    modifier = Modifier.size(StockStepsTheme.dimensions.iconLarge))
            }
        }
        Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary,
            textAlign = TextAlign.Center)
        Text(message, style = StockStepsTheme.typography.small, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            StockButton(text = actionText, onClick = onAction, modifier = Modifier.padding(top = spacing.xs), variant = actionVariant)
        }
    }
}
