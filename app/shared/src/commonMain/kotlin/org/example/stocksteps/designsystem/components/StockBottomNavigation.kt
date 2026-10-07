package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * App tab bar: ~56dp of content plus the platform's navigation-bar / home-indicator
 * inset, applied here once (callers must not add bottom system padding again).
 */
@Composable
internal fun StockBottomNavigation(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(StockStepsTheme.colors.surface)) {
        StockDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = StockStepsTheme.dimensions.bottomBarHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun RowScope.StockBottomNavigationItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val color = if (selected) StockStepsTheme.colors.primary else StockStepsTheme.colors.iconSecondary
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = StockStepsTheme.dimensions.bottomBarHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs, Alignment.CenterVertically)
    ) {
        // Icons are decorative; the label names the tab for screen readers.
        Box(Modifier.size(StockStepsTheme.dimensions.navIcon), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides color, content = icon)
        }
        Text(
            text = label,
            style = StockStepsTheme.typography.caption,
            color = if (selected) StockStepsTheme.colors.primaryText else StockStepsTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
