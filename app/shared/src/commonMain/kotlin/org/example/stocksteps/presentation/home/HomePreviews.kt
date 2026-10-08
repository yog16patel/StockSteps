package org.example.stocksteps.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.settings.ThemeMode
import org.example.stocksteps.home.PersonalDashboard

@Preview(name = "Personal Home — New user", heightDp = 1000)
@Composable
private fun HomeLightPreview() = StockStepsTheme(ThemeMode.LIGHT) {
    HomeScreen(PersonalDashboard(initializing = false), 10, null) { }
}

@Preview(name = "Personal Home — Restoring", heightDp = 1000)
@Composable
private fun HomeLoadingPreview() = StockStepsTheme(ThemeMode.DARK) {
    HomeScreen(PersonalDashboard(), 20, null) { }
}
