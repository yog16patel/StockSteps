package org.example.stocksteps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch
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
    backendEnvironment: BackendEnvironmentStore? = null,
    /** Optional biometric app lock (null on hosts without it, e.g. previews). */
    appLock: org.example.stocksteps.security.AppLockManager? = null,
    /** Push permission (Android supplies it; null where push isn't available). */
    notifications: org.example.stocksteps.presentation.watchlist.NotificationAccess? = null,
    notificationLinks: kotlinx.coroutines.flow.Flow<String>? = null,
    /** Hosts may supply the same router used by account data clients. */
    backendRouter: BackendRouter? = null
) {
    // The root owns the theme: Settings writes the preference, everything re-themes from here.
    val preferences = themePreferences ?: remember { InMemoryThemePreferenceStore() }
    val mode by preferences.themeMode.collectAsStateWithLifecycle()
    val backend = backendRouter ?: remember(backendEndpoints, backendEnvironment, baseUrl) {
        BackendRouter(
            store = backendEnvironment ?: InMemoryBackendEnvironmentStore(),
            endpoints = backendEndpoints ?: BackendEndpoints(real = baseUrl ?: localBackendUrl())
        )
    }
    StockStepsTheme(mode) {
        val lockState = appLock?.state?.collectAsStateWithLifecycle()?.value
        val locked = lockState == org.example.stocksteps.security.AppLockState.LOCKED
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            // While locked the app stays composed (navigation state survives) but is covered by an
            // opaque lock surface and removed from the accessibility tree.
            androidx.compose.foundation.layout.Box(
                if (locked) androidx.compose.ui.Modifier.clearAndSetSemantics {} else androidx.compose.ui.Modifier
            ) {
                AppNavigation(backend, hinge, navigationIcon, accounts, backIcon, preferences, appVersion, appLock, notifications, notificationLinks)
            }
            if (locked && appLock != null) {
                org.example.stocksteps.presentation.security.AppUnlockScreen(appLock, onUseAccountLogin = {
                    // Fallback for lockouts or removed biometrics: end the Firebase session; Login follows.
                    scope.launch { runCatching { accounts?.signOut()?.invoke() } }
                })
            }
        }
    }
}
