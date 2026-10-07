package org.example.stocksteps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.presentation.AppNavigation
import org.example.stocksteps.settings.InMemoryThemePreferenceStore
import org.example.stocksteps.settings.ThemePreferenceStore
import org.example.stocksteps.settings.BackendEndpoints
import org.example.stocksteps.settings.BackendEnvironmentStore
import org.example.stocksteps.settings.BackendRouter
import org.example.stocksteps.settings.InMemoryBackendEnvironmentStore

@Composable
fun App(
    baseUrl: String? = null,
    accounts: org.example.stocksteps.di.AccountDependencies? = null,
    hinge: WindowHinge? = null,
    navigationIcon: @Composable (MainDestination) -> Unit = { Text(it.label.take(1)) },
    backIcon: @Composable () -> Unit = { Text("Back") },
    themePreferences: ThemePreferenceStore? = null,
    appVersion: String? = null,
    backendEndpoints: BackendEndpoints? = null,
    backendEnvironment: BackendEnvironmentStore? = null
) {
    // The root owns the theme: Settings writes the preference, everything re-themes from here.
    val preferences = themePreferences ?: remember { InMemoryThemePreferenceStore() }
    val mode by preferences.themeMode.collectAsStateWithLifecycle()
    val backend = remember(backendEndpoints, backendEnvironment, baseUrl) {
        BackendRouter(
            store = backendEnvironment ?: InMemoryBackendEnvironmentStore(),
            endpoints = backendEndpoints ?: BackendEndpoints(real = baseUrl ?: localBackendUrl())
        )
    }
    StockStepsTheme(mode) {
        AppNavigation(backend, hinge, navigationIcon, accounts, backIcon, preferences, appVersion)
    }
}
