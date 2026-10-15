package org.example.stocksteps.presentation.components

import androidx.compose.runtime.Composable

/**
 * A full-screen page shown as a dialog (5D.5.5D, Comparison's "Add to comparison"): it covers the whole window edge to edge, so the status bar
 * shows the page's own background instead of the dialog scrim. [content] applies its own system-bar insets (e.g. `safeDrawingPadding`).
 */
@Composable
internal expect fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit)
