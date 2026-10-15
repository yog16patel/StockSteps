package org.example.stocksteps.presentation.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import org.example.stocksteps.designsystem.theme.StockStepsTheme

@Composable
internal actual fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val view = LocalView.current
        val lightPage = StockStepsTheme.colors.appBackground.luminance() > 0.5f
        SideEffect {
            window?.let {
                // Edge to edge with no dim: the page itself paints behind the status and navigation bars.
                it.setDimAmount(0f)
                @Suppress("DEPRECATION") it.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION") it.navigationBarColor = Color.TRANSPARENT
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = lightPage
                    isAppearanceLightNavigationBars = lightPage
                }
            }
        }
        content()
    }
}
