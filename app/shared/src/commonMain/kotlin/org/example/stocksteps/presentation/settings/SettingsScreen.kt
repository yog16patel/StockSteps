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
import org.example.stocksteps.settings.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Quiet, neutral Settings: Account, Appearance, Notifications, About, then Sign Out.
 * Rows without a destination show "Coming soon" instead of a chevron, so nothing looks
 * tappable that is not.
 */
@Composable
internal fun SettingsScreen(state: SettingsUiState, hinge: WindowHinge?, onAction: (SettingsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    var confirmSignOut by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(start = spacing.screen, top = spacing.sm, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "header") { SettingsHeader(content) }
                item(key = "account") {
                    SettingsSection(Res.string.settings_section_account, content.padding(top = spacing.xl)) {
                        AccountRow(state.account, state.availableLinks, onAction)
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
    val segments = listOf(
        StockSegment(stringResource(Res.string.settings_theme_light), StockIcons.Light),
        StockSegment(stringResource(Res.string.settings_theme_dark), StockIcons.Dark),
        StockSegment(stringResource(Res.string.settings_theme_system), StockIcons.System)
    )
    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    StockSegmentedControl(segments, selectedIndex = modes.indexOf(mode), onSelect = { onSelect(modes[it]) }, modifier = modifier)
}

private const val SKELETON_TITLE = 0.4f
private const val SKELETON_SUBTITLE = 0.6f
