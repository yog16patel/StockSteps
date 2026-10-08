package org.example.stocksteps.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.model.AlertStatus
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.example.stocksteps.watchlist.AlertCardModel
import org.example.stocksteps.watchlist.AlertFilter
import org.example.stocksteps.watchlist.AlertPresenter
import org.jetbrains.compose.resources.stringResource

/** All alerts, or one stock's alerts (from a watchlist row or a notification). */
@Serializable
internal data class AlertsRoute(val symbol: String? = null)

@Composable
internal fun AlertsScene(
    route: AlertsRoute,
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    notifications: NotificationAccess?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onOpenStock: (String) -> Unit,
    onSignIn: () -> Unit
) {
    val model = viewModel(key = "alerts:${route.symbol}") { AlertsViewModel(accounts.auth, accounts.alerts, route.symbol) }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf<AlertCardModel?>(null) }
    var notificationsOn by remember { mutableStateOf(notifications?.enabled ?: false) }
    AlertsScreen(
        state = state,
        hinge = hinge,
        notificationsOn = notificationsOn || notifications == null,
        backIcon = backIcon,
        onBack = onBack,
        onFilter = model::setFilter,
        onRefresh = model::refresh,
        onPause = model::pause,
        onResume = model::resume,
        onDelete = { confirmDelete = it },
        onOpenStock = onOpenStock,
        onOpenUrl = { runCatching { uriHandler.openUri(it) } },
        onSignIn = onSignIn,
        onEnableNotifications = { notificationsOn = notifications?.request() ?: false }
    )
    state.message?.let { message ->
        AlertDialog(onDismissRequest = model::dismissMessage, containerColor = StockStepsTheme.colors.surface,
            text = { Text(message, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) },
            confirmButton = { StockButton(stringResource(Res.string.action_ok), onClick = model::dismissMessage, variant = StockButtonVariant.TEXT) })
    }
    confirmDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = StockStepsTheme.colors.surface,
            title = { Text(stringResource(Res.string.alerts_delete_title), style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
            text = { Text("${card.symbol} · ${card.title}", style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) },
            dismissButton = { StockButton(stringResource(Res.string.action_cancel), onClick = { confirmDelete = null }, variant = StockButtonVariant.TEXT) },
            confirmButton = { StockButton(stringResource(Res.string.watchlist_delete), onClick = { model.delete(card.id); confirmDelete = null }, variant = StockButtonVariant.DESTRUCTIVE) }
        )
    }
}

/** My Alerts: Active / Triggered / Paused, history with delivery status, and how alerts are checked. */
@Composable
internal fun AlertsScreen(
    state: AlertsState,
    hinge: WindowHinge?,
    notificationsOn: Boolean,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onFilter: (AlertFilter) -> Unit,
    onRefresh: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (AlertCardModel) -> Unit,
    onOpenStock: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSignIn: () -> Unit,
    onEnableNotifications: suspend () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val scope = rememberCoroutineScope()
    val offset = -240 // Times are shown in US Eastern time, like market hours.
    val cards = remember(state.rules, state.filter) { AlertPresenter.cards(state.rules, state.filter, offset) }
    val history = remember(state.history) { AlertPresenter.history(state.history, offset) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = state.symbol?.let { stringResource(Res.string.alerts_for, it) } ?: stringResource(Res.string.alerts_title), backButton = AppBarBackButton.BACK),
            onBack = onBack, backIcon = backIcon
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.signedIn) {
                    item(key = "signin") {
                        StockInsightCard(stringResource(Res.string.watchlist_signin_title), stringResource(Res.string.alerts_signin_body),
                            actionText = stringResource(Res.string.watchlist_signin_action), onClick = onSignIn, modifier = content.padding(top = spacing.md))
                    }
                    return@LazyColumn
                }
                if (!notificationsOn) {
                    item(key = "push") {
                        StockCard(content.padding(top = spacing.md), bordered = false, containerColor = colors.cautionContainer, contentPadding = PaddingValues(spacing.md)) {
                            Text(stringResource(Res.string.alerts_push_off), style = StockStepsTheme.typography.small, color = colors.textBody)
                            StockButton(stringResource(Res.string.alert_push_enable), onClick = { scope.launchSafely(onEnableNotifications) },
                                modifier = Modifier.padding(top = spacing.sm), variant = StockButtonVariant.SECONDARY)
                        }
                    }
                }
                item(key = "filters") {
                    StockPillSelector(AlertFilter.entries, state.filter, label = { filter ->
                        "${filter.label} ${state.rules.count { it.status.name == filter.name }}"
                    }, onSelect = onFilter, modifier = content.padding(top = spacing.md))
                }
                when {
                    state.alerts.value == null && state.alerts.loading -> item(key = "loading") { StockLoadingState(content.padding(top = spacing.md)) { repeat(3) { StockRowSkeleton() } } }
                    state.alerts.value == null -> item(key = "error") {
                        StockCard(content.padding(top = spacing.md), bordered = false) { StockErrorState(state.alerts.error ?: stringResource(Res.string.alerts_failed), onRefresh) }
                    }
                    cards.isEmpty() -> item(key = "empty") {
                        StockCard(content.padding(top = spacing.md), bordered = false) { StockEmptyState(stringResource(Res.string.alerts_empty, state.filter.label.lowercase())) }
                    }
                    else -> items(cards, key = { it.id }) { card ->
                        AlertCard(card, content.padding(top = spacing.sm), busy = state.busy, onPause = onPause, onResume = onResume, onDelete = onDelete, onOpenStock = onOpenStock)
                    }
                }
                state.alerts.value?.deliveryNote?.let { note ->
                    item(key = "note") { Text(note, content.padding(top = spacing.md), style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
                }
                if (history.isNotEmpty()) {
                    item(key = "history-title") { StockSectionHeader(stringResource(Res.string.alerts_history), content.padding(top = spacing.xl)) }
                    items(history, key = { "h-${it.id}" }) { row ->
                        StockCard(content.padding(top = spacing.sm), bordered = false, onClick = row.sourceUrl?.let { url -> { onOpenUrl(url) } }, contentPadding = PaddingValues(spacing.md)) {
                            Text(row.title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
                            Text(row.body, Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.small, color = colors.textBody)
                            Text("${row.time} · ${row.delivery}", Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(card: AlertCardModel, modifier: Modifier, busy: Boolean, onPause: (String) -> Unit, onResume: (String) -> Unit, onDelete: (AlertCardModel) -> Unit, onOpenStock: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(card.symbol, Modifier.clickable(role = Role.Button) { onOpenStock(card.symbol) }.semantics { heading() },
                style = StockStepsTheme.typography.bodySemiBold, color = colors.primaryText)
            Spacer(Modifier.weight(1f))
            StockBadge(card.statusLabel, when { card.waitingForCross -> FactTone.CAUTION; card.status == AlertStatus.ACTIVE -> FactTone.POSITIVE; else -> FactTone.NEUTRAL })
        }
        Text(card.title, Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.body, color = colors.textPrimary)
        Text(card.detail, style = StockStepsTheme.typography.caption, color = colors.textTertiary)
        Row(Modifier.padding(top = spacing.xs), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            when (card.status) {
                AlertStatus.ACTIVE -> StockButton(stringResource(Res.string.alerts_pause), onClick = { onPause(card.id) }, enabled = !busy, variant = StockButtonVariant.TEXT)
                else -> StockButton(stringResource(if (card.status == AlertStatus.TRIGGERED) Res.string.alerts_rearm else Res.string.alerts_resume), onClick = { onResume(card.id) }, enabled = !busy, variant = StockButtonVariant.TEXT)
            }
            StockButton(stringResource(Res.string.watchlist_delete), onClick = { onDelete(card) }, enabled = !busy, variant = StockButtonVariant.TEXT)
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchSafely(block: suspend () -> Unit) {
    launch { runCatching { block() } }
}
