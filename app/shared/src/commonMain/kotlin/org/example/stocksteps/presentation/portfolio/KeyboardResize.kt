package org.example.stocksteps.presentation.portfolio

import androidx.compose.runtime.Composable

/**
 * Portfolio D1.1: while this is in the composition the keyboard resizes the screen instead of panning it, so a form that uses `imePadding`
 * keeps its top bar and status-bar inset (the pan slid the title under the clock and left a blank gap above the keyboard). The previous mode
 * is restored when the screen leaves. Android only; a no-op elsewhere.
 */
@Composable
internal expect fun KeyboardResizesContent()
