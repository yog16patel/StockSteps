package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockBannerKind
import org.example.stocksteps.theme.StockSemanticStyles

/**
 * Inline banner (Phase 2): icon, optional title, wrapping message, optional action and — only where product rules allow it — a
 * dismiss button. Colours come from `StockSemanticStyles.banner` (shared with SwiftUI); the icon and the words carry the meaning.
 * ERROR and WARNING are announced politely when they appear. The app-wide MOCK strip stays [StockSampleDataBanner]; use
 * [StockBannerKind.SAMPLE] for in-content sample-data disclosures. Never dismissible for SAMPLE.
 */
@Composable
internal fun StockBanner(
    kind: StockBannerKind,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "Dismiss"
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val tone = StockSemanticStyles.banner(kind, colors.palette)
    val shape = StockStepsTheme.shapes.card
    Row(
        modifier = modifier.fillMaxWidth().clip(shape).background(colors.rgb(tone.container))
            .border(StockStepsTheme.dimensions.border, colors.rgb(tone.border), shape)
            .padding(start = spacing.md, top = spacing.md, bottom = spacing.md, end = if (onDismiss != null) spacing.none else spacing.md)
            .semantics(mergeDescendants = true) {
                if (kind == StockBannerKind.ERROR || kind == StockBannerKind.WARNING) liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Icon(kind.icon(), contentDescription = null, tint = colors.rgb(tone.icon), modifier = Modifier.size(StockStepsTheme.dimensions.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            title?.let { Text(it, style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle) }
            Text(message, style = StockStepsTheme.typography.small, color = colors.rgb(tone.content))
            if (actionText != null && onAction != null) {
                StockButton(actionText, onAction, variant = StockButtonVariant.TEXT)
            }
        }
        if (onDismiss != null && kind != StockBannerKind.SAMPLE) {
            Box(Modifier.size(StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClickLabel = dismissLabel, onClick = onDismiss)
                .semantics { contentDescription = dismissLabel }, contentAlignment = Alignment.Center) {
                Icon(StockIcons.Close, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            }
        }
    }
}

private fun StockBannerKind.icon(): ImageVector = when (this) {
    StockBannerKind.SUCCESS -> StockIcons.Check
    StockBannerKind.WARNING, StockBannerKind.ERROR -> StockIcons.Warning
    StockBannerKind.INFO, StockBannerKind.SAMPLE -> StockIcons.Info
}
