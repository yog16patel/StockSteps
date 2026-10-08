package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.earnings.EarningsCenterPresenter
import org.example.stocksteps.earnings.EarningsDetailsPresenter
import org.example.stocksteps.earnings.EarningsRemote
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

internal class EarningsCenterViewModel(remote: EarningsRemote, accounts: AccountDependencies?, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsCenterPresenter(remote, viewModelScope, accounts?.alerts)
    override fun onCleared() = close()
}

internal class EarningsDetailsViewModel(symbol: String, remote: EarningsRemote, accounts: AccountDependencies?, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsDetailsPresenter(symbol, remote, viewModelScope, accounts?.alerts)
    override fun onCleared() = close()
}

/** Owns the Earnings Center presenter (keyed by Mock/Real) and wires navigation. */
@Composable
internal fun EarningsCenterScene(
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenEarnings: (String) -> Unit,
    onSignIn: () -> Unit
) {
    val model = viewModel(key = "earnings-center:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        EarningsCenterViewModel(data.earningsRemote(accounts), accounts, data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.presenter.start() }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsCenterScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onTab = model.presenter::selectTab, onRange = model.presenter::selectRange, onFilters = model.presenter::setFilters,
                onOpen = onOpenEarnings, onLoadMore = model.presenter::loadMore, onRetry = model.presenter::refresh, onSignIn = onSignIn)
        }
    }
}

/** Owns one company's Earnings Details presenter; Company Details stays the company destination. */
@Composable
internal fun EarningsDetailsScene(
    route: EarningsDetailsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenCompany: (String) -> Unit,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit
) {
    val model = viewModel(key = "earnings-details:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        EarningsDetailsViewModel(route.symbol, data.earningsRemote(accounts), accounts, data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsDetailsScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onReminder = model.presenter::setReminder, onAsk = model.presenter::ask, onRetry = model.presenter::refresh,
                onCompany = { onOpenCompany(route.symbol) }, onSignIn = onSignIn,
                onUpgrade = { model.presenter.dismissUpgrade(); onUpgrade() }, onDismissUpgrade = model.presenter::dismissUpgrade,
                onDismissMessage = model.presenter::dismissMessage)
        }
    }
}
