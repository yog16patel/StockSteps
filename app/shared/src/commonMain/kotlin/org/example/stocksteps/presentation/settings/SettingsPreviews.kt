package org.example.stocksteps.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.settings.ThemeMode

// Preview-only sample account; production reads the authenticated session.
private val signedIn = SettingsUiState(SettingsAccount.SignedIn("sample@example.com"), ThemeMode.SYSTEM, appVersion = "1.0")

@Composable
private fun SettingsPreview(state: SettingsUiState, mode: ThemeMode) = StockStepsTheme(mode) { SettingsScreen(state, hinge = null) {} }

@Preview(name = "Settings — Light", heightDp = 1100)
@Composable
private fun SettingsLightPreview() = SettingsPreview(signedIn.copy(themeMode = ThemeMode.LIGHT), ThemeMode.LIGHT)

@Preview(name = "Settings — Dark", heightDp = 1100)
@Composable
private fun SettingsDarkPreview() = SettingsPreview(signedIn.copy(themeMode = ThemeMode.DARK), ThemeMode.DARK)

@Preview(name = "Settings — Guest", heightDp = 1000)
@Composable
private fun SettingsGuestPreview() = SettingsPreview(signedIn.copy(account = SettingsAccount.Guest), ThemeMode.LIGHT)

@Preview(name = "Settings — Large font", widthDp = 340, heightDp = 1400, fontScale = 1.6f)
@Composable
private fun SettingsLargeFontPreview() = SettingsPreview(signedIn, ThemeMode.LIGHT)
