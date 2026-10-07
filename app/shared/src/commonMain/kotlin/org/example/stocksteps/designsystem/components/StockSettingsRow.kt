package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Settings/list row: optional leading icon or content, title, optional subtitle, and either a
 * chevron (when [onClick] is set) or custom [trailing] content. The whole row is one
 * accessible target; icons are decorative because the title already names the row.
 */
@Composable
internal fun StockSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = StockStepsTheme.spacing.md)
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.semantics(mergeDescendants = true) {}
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(Modifier.size(StockStepsTheme.dimensions.iconLarge), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(title, style = StockStepsTheme.typography.bodyMedium, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = StockStepsTheme.typography.small, color = colors.textSecondary)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                StockIcons.ChevronRight,
                contentDescription = null,
                tint = colors.iconSecondary,
                modifier = Modifier.size(StockStepsTheme.dimensions.icon)
            )
        }
    }
}
