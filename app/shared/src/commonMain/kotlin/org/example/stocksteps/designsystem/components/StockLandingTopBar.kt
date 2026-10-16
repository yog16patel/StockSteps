package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.navigation.AppTab
import org.example.stocksteps.navigation.LandingTopBarDisplay

/**
 * The shared landing top bar of the five tab landing pages (Phase 5D.5.6B, plan §7): the page heading (the StockSteps brand mark on Home), then
 * the page's own [actions] (48 dp icon buttons, e.g. Search), then Notifications (bell) and Settings (gear) — always last, in that order, on
 * every page. Copy and spoken labels come from core `LandingTopBarDisplay`. When the heading and the icon row can't share a line (large text,
 * narrow widths) the icons move to their own row under the heading (one `StockLayout.metricRowStacked` decision); nothing is truncated or hidden.
 */
@Composable
internal fun StockLandingTopBar(
    tab: AppTab,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<@Composable () -> Unit> = emptyList()
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val title = LandingTopBarDisplay.title(tab)
    val brand = LandingTopBarDisplay.showsBrand(tab)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val titleWidth = with(density) {
            if (brand) measurer.measure(title, typography.cardTitle).size.width.toDp().value + StockStepsTheme.dimensions.brandMark.value + spacing.sm.value
            else measurer.measure(title, typography.screenTitle).size.width.toDp().value
        }
        val icons = (actions.size + 2) * StockStepsTheme.dimensions.touchTarget.value
        val stacked = org.example.stocksteps.theme.StockLayout.metricRowStacked(titleWidth, icons, maxWidth.value, spacing.sm.value)
        val heading = @Composable { m: Modifier ->
            if (brand) StockBrandMark(title, m)
            else Text(title, m.semantics { heading() }, style = typography.screenTitle, color = colors.textTitle)
        }
        val iconRow = @Composable {
            actions.forEach { it() }
            IconButton(onClick = onNotifications) { Icon(StockIcons.Bell, LandingTopBarDisplay.NOTIFICATIONS_SPOKEN, tint = colors.iconPrimary) }
            IconButton(onClick = onSettings) { Icon(StockIcons.Settings, LandingTopBarDisplay.SETTINGS_SPOKEN, tint = colors.iconPrimary) }
        }
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            heading(Modifier)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { iconRow() }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            heading(Modifier.weight(1f))
            iconRow()
        }
    }
}
