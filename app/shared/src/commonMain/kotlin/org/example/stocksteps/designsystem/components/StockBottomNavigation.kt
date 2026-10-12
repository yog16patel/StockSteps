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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockNavigationLayout

/** Label font scale for the tab bar (accessibility closure): null = icons only; [Float.NaN] = no rule (labels follow the system scale). */
private val LocalTabLabelScale = compositionLocalOf<Float?> { Float.NaN }

/**
 * App tab bar: ~56dp of content plus the platform's navigation-bar / home-indicator
 * inset, applied here once (callers must not add bottom system padding again).
 */
@Composable
internal fun StockBottomNavigation(modifier: Modifier = Modifier, labels: List<String> = emptyList(), content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    // Accessibility closure: labels grow with the font scale until the widest no longer fits its tab, then stop (never ellipsized; icons only if
    // even the standard size can't fit). Shared rule: StockNavigationLayout.
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = StockStepsTheme.typography.caption
    val labelWidth = if (labels.isEmpty()) 0f else maxWidth.value / labels.size - StockStepsTheme.spacing.xs.value * 2
    val scale = remember(labels, labelWidth, density.fontScale) {
        if (labels.isEmpty()) Float.NaN else {
            val widest = labels.maxOf { with(density) { measurer.measure(it, style).size.width.toDp().value } } / density.fontScale
            StockNavigationLayout.labelScale(widest, labelWidth, density.fontScale)
        }
    }
    CompositionLocalProvider(LocalTabLabelScale provides scale) {
    Column(modifier = Modifier.fillMaxWidth().background(StockStepsTheme.colors.surface)) {
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
    val scale = LocalTabLabelScale.current
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = StockStepsTheme.dimensions.bottomBarHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // Icons-only mode keeps the destination name for screen readers.
            .then(if (scale == null) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(horizontal = StockStepsTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs, Alignment.CenterVertically)
    ) {
        // Icons are decorative; the label names the tab for screen readers.
        Box(Modifier.size(StockStepsTheme.dimensions.navIcon), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides color, content = icon)
        }
        if (scale != null) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (scale.isNaN()) density else Density(density.density, scale)) {
                Text(
                    text = label,
                    style = StockStepsTheme.typography.caption,
                    color = if (selected) StockStepsTheme.colors.primaryText else StockStepsTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
