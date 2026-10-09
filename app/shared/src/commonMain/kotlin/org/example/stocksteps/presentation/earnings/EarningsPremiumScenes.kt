package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.earnings.*
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.settings.BackendEnvironment

/** Owns the digest presenter for one signed-in account graph (cleared with the screen). */
internal class EarningsDigestViewModel(accounts: AccountDependencies?, loadDigest: Boolean) : ViewModel() {
    val presenter = EarningsDigestPresenter(accounts?.let { RemoteEarningsPremium(it.userApi) }, viewModelScope,
        accounts?.watchlists?.earningsSession() ?: flowOf(null), loadDigest).also { it.start() }
}

/** Re-reads the server's plan when the displayed StockSteps+ state changes (e.g. after visiting Settings). */
@Composable
internal fun RefreshOnPlanChange(accounts: AccountDependencies?, refresh: () -> Unit) {
    val plan by (accounts?.entitlements?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()
    var seen by remember { mutableStateOf<Any?>(Unit) }
    val key = plan?.tier to plan?.status
    LaunchedEffect(key) { if (seen != Unit && seen != key) refresh(); seen = key }
}

@Composable
internal fun EarningsDigestScene(
    accounts: AccountDependencies?,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    onOpenResults: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onSignIn: () -> Unit,
    onRecentWatchlist: () -> Unit
) {
    val model = viewModel(key = "earnings-digest:$environment") { EarningsDigestViewModel(accounts, loadDigest = true) }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    RefreshOnPlanChange(accounts) { model.presenter.refresh() }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsDigestScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                digestAction(model.presenter, action, onOpenResults, onOpenEvent, onSettings, onUpgrade, onSignIn, onRecentWatchlist, onReminders = {})
            }
        }
    }
    state.upgrade?.let { PremiumUpgradeDialog(it, onUpgrade = { model.presenter.dismissUpgrade(); onUpgrade() }, onDismiss = model.presenter::dismissUpgrade) }
}

@Composable
internal fun EarningsDigestSettingsScene(
    accounts: AccountDependencies?,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    onUpgrade: () -> Unit,
    onSignIn: () -> Unit,
    onReminders: () -> Unit
) {
    val model = viewModel(key = "earnings-digest-settings:$environment") { EarningsDigestViewModel(accounts, loadDigest = false) }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    val access = LocalNotificationAccess.current
    RefreshOnPlanChange(accounts) { model.presenter.refresh() }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsDigestSettingsScreen(state, access?.enabled == false, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                digestAction(model.presenter, action, {}, {}, {}, onUpgrade, onSignIn, {}, onReminders)
            }
        }
    }
    state.upgrade?.let { PremiumUpgradeDialog(it, onUpgrade = { model.presenter.dismissUpgrade(); onUpgrade() }, onDismiss = model.presenter::dismissUpgrade) }
}

private fun digestAction(p: EarningsDigestPresenter, action: DigestAction, onOpenResults: (String) -> Unit, onOpenEvent: (String) -> Unit, onSettings: () -> Unit,
                         onUpgrade: () -> Unit, onSignIn: () -> Unit, onRecentWatchlist: () -> Unit, onReminders: () -> Unit) {
    when (action) {
        DigestAction.Retry -> p.refresh()
        DigestAction.Explain -> p.explain()
        is DigestAction.OpenResults -> onOpenResults(action.reportId)
        is DigestAction.OpenEvent -> onOpenEvent(action.eventId)
        DigestAction.Settings -> onSettings()
        DigestAction.Upgrade -> onUpgrade()
        DigestAction.DismissUpgrade -> p.dismissUpgrade()
        DigestAction.SignIn -> onSignIn()
        DigestAction.RecentWatchlist -> onRecentWatchlist()
        DigestAction.LoadHistory -> p.loadHistory()
        is DigestAction.Cadence -> p.updatePreferences { it.copy(cadence = action.cadence) }
        is DigestAction.Day -> p.updatePreferences { it.copy(dayOfWeek = action.day) }
        is DigestAction.Upcoming -> p.updatePreferences { it.copy(includeUpcoming = action.include) }
        is DigestAction.Interest -> p.updatePreferences { it.copy(interests = if (action.key in it.interests) it.interests - action.key else it.interests + action.key) }
        DigestAction.Reminders -> onReminders()
        is DigestAction.Scenario -> p.setScenario(action.id)
    }
}
