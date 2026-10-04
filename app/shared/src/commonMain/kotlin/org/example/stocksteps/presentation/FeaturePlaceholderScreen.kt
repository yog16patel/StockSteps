package org.example.stocksteps.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.example.stocksteps.*
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun FeaturePlaceholderScreen(title: String, description: String, hinge: WindowHinge?) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val pane = paneLayout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density, origin.x, origin.y, hinge).search
        val region = if (hinge == null) Modifier.fillMaxSize() else with(density) {
            Modifier
                .absoluteOffset(pane.x.toDp(), pane.y.toDp())
                .size(pane.width.toDp(), pane.height.toDp())
        }
        Column(
            modifier = region.padding(ThemeSpacing.extraLarge.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(text = description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
