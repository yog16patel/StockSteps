package org.example.stocksteps.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.paneLayout

/** A scroll/list screen gets a safe region on separating hinges. */
@Composable
internal fun AdaptiveSinglePane(hinge: WindowHinge?, content: @Composable (Modifier) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().safeContentPadding()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val pane = paneLayout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density, origin.x, origin.y, hinge).search
        val modifier = if (hinge == null) Modifier.fillMaxSize() else with(density) {
            Modifier.absoluteOffset(pane.x.toDp(), pane.y.toDp()).size(pane.width.toDp(), pane.height.toDp())
        }
        content(modifier)
    }
}
