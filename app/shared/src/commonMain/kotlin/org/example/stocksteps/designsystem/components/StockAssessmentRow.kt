package org.example.stocksteps.designsystem.components

import org.example.stocksteps.theme.StockBadgeLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * One fact-based assessment line: icon tile, title, optional descriptive badge, the fact, and a
 * plain-English note. Tappable rows open more detail and show a chevron.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockAssessmentRow(
    icon: ImageVector,
    iconContainer: Color,
    iconContent: Color,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    tone: FactTone = FactTone.NEUTRAL,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    /**
     * 5D.5.4B: the status as tinted words on its own line under the title (Category → Interpretation → fact → note), the same for every
     * row so all rows align and a long status ("More debt than cash") wraps between words at any size. Default off (pill badge, unchanged).
     */
    statusBelowTitle: Boolean = false
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        StockIconTile(icon, iconContainer, iconContent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            // Short badges share the title's line while both fit (badge at the end); otherwise — and always for badges over the shared
            // `StockBadgeLayout` limit (Phase 4C.3; 20 characters since 5D.5.4A) — the badge sits below, so it never squeezes the title or breaks it mid-word.
            if (statusBelowTitle) {
                Text(title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
                if (badge != null) Text(badge, style = StockStepsTheme.typography.bodySemiBold,
                    color = colors.rgb(org.example.stocksteps.theme.StockSemanticStyles.badge(tone.badgeKind(), colors.palette).content))
            } else if (badge == null || StockBadgeLayout.mayShareTitleLine(badge)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.padding(end = spacing.sm), style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
                    if (badge != null) StockBadge(badge, tone)
                }
            } else {
                Text(title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
                StockBadge(badge, tone)
            }
            Text(detail, style = StockStepsTheme.typography.small, color = colors.textBody)
            if (note != null) Text(note, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
        }
        if (onClick != null) {
            Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        }
    }
}
