package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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

/** Legitimate empty content: calm, never alarming. */
@Composable
internal fun StockEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    SectionMessage(message, modifier, actionText, onAction)
}

/**
 * Section-level failure with a retry. Messages are user-facing copy only;
 * provider/HTTP details belong in logs.
 */
@Composable
internal fun StockErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    SectionMessage(message, modifier, onRetry?.let { stringResource(Res.string.action_retry) }, onRetry)
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
