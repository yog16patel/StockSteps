package org.example.stocksteps.presentation.settings

import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.ThemeMode

/** Settings destinations; a row is tappable only when its destination is available. */
internal enum class SettingsLink { ACCOUNT, PRICE_ALERTS, MARKET_NEWS, ABOUT, PRIVACY, TERMS, HELP }

internal sealed interface SettingsAccount {
    data object Loading : SettingsAccount
    data object Guest : SettingsAccount
    /** The auth model has no display name yet, so only the email is shown. */
    data class SignedIn(val email: String?) : SettingsAccount
}

internal data class SettingsUiState(
    val account: SettingsAccount,
    val themeMode: ThemeMode,
    val appVersion: String?,
    val availableLinks: Set<SettingsLink> = emptySet(),
    val signingOut: Boolean = false,
    val message: String? = null,
    /** Null hides the Development section (no mock backend configured, e.g. release builds). */
    val backendEnvironment: BackendEnvironment? = null
)

internal sealed interface SettingsAction {
    data class SelectTheme(val mode: ThemeMode) : SettingsAction
    data class SelectBackend(val environment: BackendEnvironment) : SettingsAction
    data class Open(val link: SettingsLink) : SettingsAction
    data object SignIn : SettingsAction
    data object SignOut : SettingsAction
}
