package org.example.stocksteps.presentation.portfolio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE is what Compose's imePadding expects on the activity window.
@Composable
internal actual fun KeyboardResizesContent() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        // Reference-counted: during a navigation transition the old and new screen overlap, so the mode to restore is captured only by the
        // first active user and restored by the last (a per-instance copy restored "resize" after the form had closed).
        if (window != null) {
            if (KeyboardResizeUsers.count++ == 0) KeyboardResizeUsers.original = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose {
            // An "unspecified" adjust mode (the activity default) can't be set back — Window.setSoftInputMode ignores 0 — and the system resolves
            // it to pan for this Compose activity (dumpsys "adjust=pan" before the form opened), so pan is restored explicitly in that case.
            if (window != null && --KeyboardResizeUsers.count == 0) KeyboardResizeUsers.original?.let { mode ->
                val adjust = mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                window.setSoftInputMode(if (adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED) mode or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN else mode)
            }
        }
    }
}

private object KeyboardResizeUsers {
    var count = 0
    var original: Int? = null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
