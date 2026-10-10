package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.theme.StockSemanticStyles
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Non-interactive neutral label (sector, industry, size band). */
@Composable
internal fun StockTag(text: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.pill
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceSecondary)
            .border(StockStepsTheme.dimensions.border, colors.borderSubtle, shape)
            .padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.caption,
        color = colors.textSupporting,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

/** Short status label tinted by [tone]; the text always states the status, so color is never the only signal. */
@Composable
internal fun StockBadge(text: String, tone: FactTone, modifier: Modifier = Modifier) {
    StockStatusBadge(text, tone.badgeKind(), modifier)
}

internal enum class StockBadgeSize { COMPACT, REGULAR }

/**
 * The status-label system (Phase 2): [kind] picks the semantic colours (`StockSemanticStyles`, shared with SwiftUI). Text states
 * the meaning (never colour alone), scales with font size and never splits a word — it keeps one line and ellipsizes only if the
 * whole row is narrower than the badge. COMPACT for dense rows (`tiny`), REGULAR elsewhere (`label`).
 */
@Composable
internal fun StockStatusBadge(
    text: String,
    kind: StockBadgeKind,
    modifier: Modifier = Modifier,
    size: StockBadgeSize = StockBadgeSize.REGULAR,
    contentDescription: String? = null
) {
    val colors = StockStepsTheme.colors
    val tone = StockSemanticStyles.badge(kind, colors.palette)
    val shape = StockStepsTheme.shapes.pill
    val compact = size == StockBadgeSize.COMPACT
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(colors.rgb(tone.container))
            .then(if (tone.border != tone.container) Modifier.border(StockStepsTheme.dimensions.border, colors.rgb(tone.border), shape) else Modifier)
            .padding(horizontal = if (compact) StockStepsTheme.spacing.xs + StockStepsTheme.spacing.xxs else StockStepsTheme.spacing.sm,
                vertical = StockStepsTheme.spacing.xxs)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        style = if (compact) StockStepsTheme.typography.tiny else StockStepsTheme.typography.label,
        color = colors.rgb(tone.content),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * A title with a trailing status badge: side by side while both fit on one line, otherwise the badge moves below the title — the
 * badge never splits mid-word and the title never collapses to one letter per line (Phase 2, large font scale).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockTitleWithBadge(modifier: Modifier = Modifier, title: @Composable () -> Unit, badge: @Composable () -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        title()
        badge()
    }
}

internal fun FactTone.badgeKind(): StockBadgeKind = when (this) {
    FactTone.POSITIVE -> StockBadgeKind.POSITIVE
    FactTone.NEGATIVE -> StockBadgeKind.NEGATIVE
    FactTone.CAUTION -> StockBadgeKind.WARNING
    FactTone.NEUTRAL -> StockBadgeKind.NEUTRAL
}

/** Decorative icon on a tinted rounded square. */
@Composable
internal fun StockIconTile(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(StockStepsTheme.dimensions.iconTile).clip(StockStepsTheme.shapes.chip).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}

/** Container/content colors for a semantic tone (contrast-safe text on each container). */
@Composable
internal fun toneColors(tone: FactTone): Pair<Color, Color> {
    val colors = StockStepsTheme.colors
    val t = StockSemanticStyles.badge(tone.badgeKind(), colors.palette)
    return colors.rgb(t.container) to colors.rgb(t.content)
}
