package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Full-width navigation row inside a card (Phase 4B, generalised from Home's link row): optional icon, title, muted detail lines and a
 * chevron. One tap target of at least `rowCompactMinHeight`; the title and detail wrap instead of truncating. [actionLabel] is the
 * click label screen readers announce ("Opens the Earnings Calendar").
 *
 * Phase 5: title `listTitle` (16, the primary list line) and [detail] `small` (14, was the 12 caption, too small for the sentence that says
 * what a row opens); [secondaryDetail] stays a caption (metadata such as "Sample data").
 */
@Composable
internal fun StockNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    detailColor: Color = StockStepsTheme.colors.textSupporting,
    /** A second muted line (e.g. watchlist counts) under [detail]. */
    secondaryDetail: String? = null,
    actionLabel: String? = null,
    /** Optional status beside the title (e.g. "StockSteps+" as PREMIUM, Phase 5C); default none, so existing rows are unchanged. */
    badge: String? = null,
    badgeKind: org.example.stocksteps.theme.StockBadgeKind = org.example.stocksteps.theme.StockBadgeKind.PREMIUM,
    /** 5D.5.1: a short body line under the title (Home's Daily Brief entry shows the summary's first sentence); never truncated, so a number or
     *  name is never cut ("S&P 50…" at AX5); callers keep it short. Default none. */
    summary: String? = null
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick).padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            if (badge == null) Text(title, style = StockStepsTheme.typography.listTitle, color = colors.textTitle)
            else StockTitleWithBadge(title = { Text(title, style = StockStepsTheme.typography.listTitle, color = colors.textTitle) },
                badge = { StockStatusBadge(badge, badgeKind, size = StockBadgeSize.COMPACT) })
            if (summary != null) Text(summary, style = StockStepsTheme.typography.small, color = colors.textBody)
            if (detail != null) Text(detail, style = StockStepsTheme.typography.small, color = detailColor)
            if (secondaryDetail != null) Text(secondaryDetail, style = StockStepsTheme.typography.caption, color = colors.textSupporting)
        }
        Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
    }
}
