package org.example.stocksteps.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import org.example.stocksteps.settings.ThemeMode
import org.example.stocksteps.designsystem.theme.StockStepsTheme as DesignSystemTheme

/** Entry point kept for existing callers; the design system owns the actual theme. */
@Composable
fun StockStepsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    DesignSystemTheme(mode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT, content = content)
}
