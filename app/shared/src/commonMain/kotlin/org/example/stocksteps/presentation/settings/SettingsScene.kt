package org.example.stocksteps.presentation.settings

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.example.stocksteps.resources.*
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
    links: Map<SettingsLink, () -> Unit> = emptyMap(),
    appLock: org.example.stocksteps.security.AppLockManager? = null
) {
    val model = viewModel { accounts.accountViewModel() }
    val session by model.session.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle()
    val storedBackend by backend.selection.collectAsStateWithLifecycle()
    val user = session.user
    val lockState by (appLock?.state ?: kotlinx.coroutines.flow.MutableStateFlow(org.example.stocksteps.security.AppLockState.NOT_REQUIRED)).collectAsStateWithLifecycle()
    var settingsVersion by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var securityMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var securityBusy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val security = androidx.compose.runtime.remember(user?.id, lockState, settingsVersion) { appLock?.takeIf { user != null }?.settings() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val enableReason = org.jetbrains.compose.resources.stringResource(Res.string.security_enable_reason)
    val disableReason = org.jetbrains.compose.resources.stringResource(Res.string.security_disable_reason)
    val cancelledText = org.jetbrains.compose.resources.stringResource(Res.string.security_cancelled)
    val unavailableText = org.jetbrains.compose.resources.stringResource(Res.string.security_biometric_unavailable)
    val lockoutText = org.jetbrains.compose.resources.stringResource(Res.string.lock_lockout)
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
        backendEnvironment = backend.effective(storedBackend).takeIf { backend.mockAvailable },
        security = security,
        securityMessage = securityMessage,
        securityBusy = securityBusy
    )
    SettingsScreen(state, hinge, showHeader = false) { event ->
        when (event) {
            is SettingsAction.SelectTheme -> themePreferences.setThemeMode(event.mode)
            is SettingsAction.SelectBackend -> backend.select(event.environment)
            is SettingsAction.Open -> links[event.link]?.invoke()
            SettingsAction.SignIn -> onSignIn()
            SettingsAction.SignOut -> model.logout()
            is SettingsAction.SetAppLock -> if (appLock != null && !securityBusy) {
                securityBusy = true
                scope.launch {
                    val result = if (event.enabled) appLock.enable(enableReason) else appLock.disable(disableReason)
                    securityMessage = when (result) {
                        org.example.stocksteps.security.BiometricResult.Success -> null
                        org.example.stocksteps.security.BiometricResult.Cancelled -> if (event.enabled) cancelledText else null
                        org.example.stocksteps.security.BiometricResult.Lockout -> lockoutText
                        else -> unavailableText
                    }
                    settingsVersion++
                    securityBusy = false
                }
            }
            is SettingsAction.SetLockTimeout -> { appLock?.setTimeout(event.timeout); settingsVersion++ }
        }
    }
}
