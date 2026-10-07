package org.example.stocksteps.presentation.settings

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.settings.BackendRouter
import org.example.stocksteps.settings.ThemePreferenceStore

/**
 * Wires account state, the app-level theme preference and destinations. No Account, Notification,
 * About, Privacy, Terms or Help destinations exist yet, so [links] is empty by default.
 */
@Composable
internal fun SettingsScene(
    accounts: AccountDependencies,
    themePreferences: ThemePreferenceStore,
    backend: BackendRouter,
    appVersion: String?,
    hinge: WindowHinge?,
    onSignIn: () -> Unit,
    links: Map<SettingsLink, () -> Unit> = emptyMap()
) {
    val model = viewModel { accounts.accountViewModel() }
    val session by model.session.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle()
    val storedBackend by backend.selection.collectAsStateWithLifecycle()
    val user = session.user
    val state = SettingsUiState(
        account = when {
            session.initializing -> SettingsAccount.Loading
            user == null -> SettingsAccount.Guest
            else -> SettingsAccount.SignedIn(user.email)
        },
        themeMode = themeMode,
        appVersion = appVersion,
        availableLinks = links.keys,
        signingOut = action.busy,
        message = action.error ?: session.configurationError,
        backendEnvironment = backend.effective(storedBackend).takeIf { backend.mockAvailable }
    )
    SettingsScreen(state, hinge) { event ->
        when (event) {
            is SettingsAction.SelectTheme -> themePreferences.setThemeMode(event.mode)
            is SettingsAction.SelectBackend -> backend.select(event.environment)
            is SettingsAction.Open -> links[event.link]?.invoke()
            SettingsAction.SignIn -> onSignIn()
            SettingsAction.SignOut -> model.logout()
        }
    }
}
