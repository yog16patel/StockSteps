package org.example.stocksteps.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) =
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) { content() }
