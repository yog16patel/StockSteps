package org.example.stocksteps.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.ThemeMode
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Quiet, neutral Settings: Account, Appearance, Notifications, About, then Sign Out.
 * Rows without a destination show "Coming soon" instead of a chevron, so nothing looks
 * tappable that is not.
 */
@Composable
internal fun SettingsScreen(state: SettingsUiState, hinge: WindowHinge?, showHeader: Boolean = true, onAction: (SettingsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmRealData by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(start = spacing.screen, top = spacing.sm, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showHeader) item(key = "header") { SettingsHeader(content) }
                item(key = "account") {
                    SettingsSection(Res.string.settings_section_account, content.padding(top = spacing.xl)) {
                        AccountRow(state.account, state.availableLinks, onAction)
                    }
                }
                state.security?.let { security ->
                    item(key = "security") {
                        SettingsSection(Res.string.settings_section_security, content.padding(top = spacing.xl)) {
                            SecuritySection(security, state.securityMessage, state.securityBusy, onAction)
                        }
                    }
                }
                item(key = "appearance") {
                    SettingsSection(Res.string.settings_section_appearance, content.padding(top = spacing.xl)) {
                        StockSettingsRow(
                            title = stringResource(Res.string.settings_theme_title),
                            subtitle = stringResource(Res.string.settings_theme_subtitle),
                            icon = StockIcons.Theme,
                            contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.sm)
                        )
                        ThemeSelector(state.themeMode, onSelect = { onAction(SettingsAction.SelectTheme(it)) }, Modifier.padding(bottom = spacing.xs))
                    }
                }
                item(key = "notifications") {
                    SettingsSection(Res.string.settings_section_notifications, content.padding(top = spacing.xl)) {
                        LinkRow(SettingsLink.PRICE_ALERTS, Res.string.settings_price_alerts, StockIcons.Bell, state, onAction, Res.string.settings_price_alerts_subtitle)
                        RowDivider()
                        LinkRow(SettingsLink.MARKET_NEWS, Res.string.settings_market_news, StockIcons.News, state, onAction, Res.string.settings_market_news_subtitle)
                    }
                }
                state.backendEnvironment?.let { environment ->
                    item(key = "development") {
                        SettingsSection(Res.string.settings_section_development, content.padding(top = spacing.xl)) {
                            BackendSourceSetting(
                                environment = environment,
                                // Mock → Real asks first (quota); Real → Mock applies immediately.
                                onSelect = { selected ->
                                    if (selected == BackendEnvironment.REAL && environment == BackendEnvironment.MOCK) confirmRealData = true
                                    else onAction(SettingsAction.SelectBackend(selected))
                                }
                            )
                        }
                    }
                }
                item(key = "about") {
                    SettingsSection(Res.string.settings_section_about, content.padding(top = spacing.xl)) {
                        val version = state.appVersion?.let { stringResource(Res.string.settings_version, it) }
                        StockSettingsRow(
                            title = stringResource(Res.string.settings_about_app),
                            subtitle = version,
                            icon = StockIcons.Info,
                            onClick = state.clickable(SettingsLink.ABOUT, onAction)
                        )
                        RowDivider()
                        LinkRow(SettingsLink.PRIVACY, Res.string.settings_privacy, StockIcons.Shield, state, onAction)
                        RowDivider()
                        LinkRow(SettingsLink.TERMS, Res.string.settings_terms, StockIcons.Document, state, onAction)
                        RowDivider()
                        LinkRow(SettingsLink.HELP, Res.string.settings_help, StockIcons.Help, state, onAction)
                    }
                }
                // Guests have no session, so there is nothing to sign out of.
                if (state.account is SettingsAccount.SignedIn) {
                    item(key = "sign-out") {
                        Column(content.padding(top = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockButton(
                                text = stringResource(Res.string.settings_sign_out),
                                onClick = { confirmSignOut = true },
                                variant = StockButtonVariant.DESTRUCTIVE,
                                enabled = !state.signingOut,
                                icon = StockIcons.SignOut,
                                modifier = Modifier.fillMaxWidth()
                            )
                            state.message?.let { Text(it, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary) }
                        }
                    }
                }
            }
        }
    }
    if (confirmRealData) {
        AlertDialog(
            onDismissRequest = { confirmRealData = false },
            containerColor = StockStepsTheme.colors.surface,
            title = { Text(stringResource(Res.string.settings_backend_confirm_title), style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
            text = { Text(stringResource(Res.string.settings_backend_confirm_body), style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) },
            dismissButton = {
                StockButton(stringResource(Res.string.action_cancel), onClick = { confirmRealData = false }, variant = StockButtonVariant.TEXT)
            },
            confirmButton = {
                StockButton(
                    stringResource(Res.string.settings_backend_confirm_action),
                    onClick = { confirmRealData = false; onAction(SettingsAction.SelectBackend(BackendEnvironment.REAL)) },
                    variant = StockButtonVariant.SECONDARY
                )
            }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = StockStepsTheme.colors.surface,
            title = { Text(stringResource(Res.string.settings_sign_out_title), style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
            text = { Text(stringResource(Res.string.settings_sign_out_body), style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) },
            dismissButton = {
                StockButton(stringResource(Res.string.action_cancel), onClick = { confirmSignOut = false }, variant = StockButtonVariant.TEXT)
            },
            confirmButton = {
                StockButton(
                    stringResource(Res.string.settings_sign_out),
                    onClick = { confirmSignOut = false; onAction(SettingsAction.SignOut) },
                    variant = StockButtonVariant.DESTRUCTIVE
                )
            }
        )
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(
            stringResource(Res.string.settings_title),
            modifier = Modifier.semantics { heading() },
            style = StockStepsTheme.typography.screenTitle,
            color = StockStepsTheme.colors.textPrimary
        )
        Text(stringResource(Res.string.settings_subtitle), style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textSecondary)
    }
}

/** Section label + one card; rows inside are flat and separated by dividers. */
@Composable
private fun SettingsSection(title: StringResource, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        Text(
            stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = StockStepsTheme.typography.bodySemiBold,
            color = StockStepsTheme.colors.textSecondary
        )
        StockCard(contentPadding = PaddingValues(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.xxs), content = content)
    }
}

@Composable
private fun AccountRow(account: SettingsAccount, links: Set<SettingsLink>, onAction: (SettingsAction) -> Unit) {
    when (account) {
        SettingsAccount.Loading -> StockLoadingState {
            Row(
                Modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)
            ) {
                StockAccountAvatar()
                Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                    StockSkeleton(Modifier.fillMaxWidth(SKELETON_TITLE))
                    StockSkeleton(Modifier.fillMaxWidth(SKELETON_SUBTITLE))
                }
            }
        }
        SettingsAccount.Guest -> StockSettingsRow(
            title = stringResource(Res.string.settings_guest_title),
            subtitle = stringResource(Res.string.settings_guest_subtitle),
            leading = { StockAccountAvatar() },
            onClick = { onAction(SettingsAction.SignIn) }
        )
        is SettingsAccount.SignedIn -> StockSettingsRow(
            title = stringResource(Res.string.settings_account_fallback_name),
            subtitle = account.email ?: stringResource(Res.string.settings_account_signed_in),
            leading = { StockAccountAvatar() },
            onClick = if (SettingsLink.ACCOUNT in links) { { onAction(SettingsAction.Open(SettingsLink.ACCOUNT)) } } else null
        )
    }
}

@Composable
private fun LinkRow(
    link: SettingsLink,
    title: StringResource,
    icon: ImageVector,
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    subtitle: StringResource? = null
) {
    val onClick = state.clickable(link, onAction)
    StockSettingsRow(
        title = stringResource(title),
        subtitle = subtitle?.let { stringResource(it) },
        icon = icon,
        onClick = onClick,
        trailing = if (onClick == null) {
            { Text(stringResource(Res.string.settings_coming_soon), style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textTertiary) }
        } else null
    )
}

private fun SettingsUiState.clickable(link: SettingsLink, onAction: (SettingsAction) -> Unit): (() -> Unit)? =
    if (link in availableLinks) { { onAction(SettingsAction.Open(link)) } } else null

/** Starts under the text column (icon 24 + gap 12) so dividers stay subtle. */
@Composable
private fun RowDivider() = StockDivider(startIndent = StockStepsTheme.dimensions.iconLarge + StockStepsTheme.spacing.md)

@Composable
private fun ThemeSelector(mode: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier) {
    StockSegmentedControl(
        options = listOf(
            StockSegment(ThemeMode.LIGHT, stringResource(Res.string.settings_theme_light), StockIcons.Light),
            StockSegment(ThemeMode.DARK, stringResource(Res.string.settings_theme_dark), StockIcons.Dark),
            StockSegment(ThemeMode.SYSTEM, stringResource(Res.string.settings_theme_system), StockIcons.System)
        ),
        selected = mode,
        onSelect = onSelect,
        modifier = modifier
    )
}

/** Mock vs Real is a development choice, so it uses neutral/blue states, never green/red. */
@Composable
private fun BackendSourceSetting(environment: BackendEnvironment, onSelect: (BackendEnvironment) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockSettingsRow(
        title = stringResource(Res.string.settings_backend_title),
        subtitle = stringResource(Res.string.settings_backend_subtitle),
        icon = StockIcons.Storage,
        contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.sm)
    )
    StockSegmentedControl(
        options = listOf(
            StockSegment(BackendEnvironment.MOCK, stringResource(Res.string.settings_backend_mock)),
            StockSegment(BackendEnvironment.REAL, stringResource(Res.string.settings_backend_real))
        ),
        selected = environment,
        onSelect = onSelect
    )
    Column(Modifier.padding(top = spacing.sm, bottom = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Box(Modifier.size(StockStepsTheme.dimensions.statusDot).background(colors.primary, CircleShape))
            Text(
                stringResource(if (environment == BackendEnvironment.MOCK) Res.string.settings_backend_mock_status else Res.string.settings_backend_real_status),
                style = StockStepsTheme.typography.small,
                color = colors.textSecondary
            )
        }
        if (environment == BackendEnvironment.REAL) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(StockIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Text(stringResource(Res.string.settings_backend_quota_warning), style = StockStepsTheme.typography.small, color = colors.textSecondary)
            }
        }
    }
}

private const val SKELETON_TITLE = 0.4f
private const val SKELETON_SUBTITLE = 0.6f


/** Biometric toggle (system prompt before any change), timeout options, and session information. */
@Composable
private fun SecuritySection(
    security: org.example.stocksteps.security.AppLockSettings,
    message: String?,
    busy: Boolean,
    onAction: (SettingsAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val available = security.available == org.example.stocksteps.security.BiometricAvailability.AVAILABLE
    val subtitle = when (security.available) {
        org.example.stocksteps.security.BiometricAvailability.AVAILABLE -> stringResource(Res.string.security_biometric_subtitle)
        org.example.stocksteps.security.BiometricAvailability.NOT_ENROLLED -> stringResource(Res.string.security_biometric_not_enrolled)
        org.example.stocksteps.security.BiometricAvailability.UNAVAILABLE -> stringResource(Res.string.security_biometric_unavailable)
    }
    StockSettingsRow(
        title = stringResource(Res.string.security_biometric_title),
        subtitle = subtitle,
        icon = StockIcons.Shield,
        // The switch shows only when it can actually be used (or to turn an existing lock off).
        trailing = if (available || security.enabled) { {
            androidx.compose.material3.Switch(
                checked = security.enabled,
                onCheckedChange = { onAction(SettingsAction.SetAppLock(it)) },
                enabled = !busy
            )
        } } else null
    )
    if (security.enabled) {
        RowDivider()
        StockSettingsRow(title = stringResource(Res.string.security_timeout_title), contentPadding = PaddingValues(top = spacing.md, bottom = spacing.xs))
        StockSegmentedControl(
            options = listOf(
                StockSegment(org.example.stocksteps.security.AppLockTimeout.IMMEDIATELY, stringResource(Res.string.security_timeout_immediately)),
                StockSegment(org.example.stocksteps.security.AppLockTimeout.FIVE_MINUTES, stringResource(Res.string.security_timeout_five)),
                StockSegment(org.example.stocksteps.security.AppLockTimeout.FIFTEEN_MINUTES, stringResource(Res.string.security_timeout_fifteen)),
                StockSegment(org.example.stocksteps.security.AppLockTimeout.NEVER, stringResource(Res.string.security_timeout_never))
            ),
            selected = security.timeout,
            onSelect = { onAction(SettingsAction.SetLockTimeout(it)) },
            modifier = Modifier.padding(bottom = spacing.sm)
        )
    }
    message?.let { Text(it, Modifier.padding(bottom = spacing.sm), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary) }
    RowDivider()
    StockSettingsRow(
        title = stringResource(Res.string.security_stay_signed_in),
        subtitle = stringResource(Res.string.security_stay_signed_in_subtitle),
        icon = StockIcons.Person
    )
}
