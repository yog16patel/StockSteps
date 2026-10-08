package org.example.stocksteps.presentation.practice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.practice.OrderSide
import org.example.stocksteps.practice.PracticeOrderPresenter
import org.example.stocksteps.practice.PracticeTab
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.settings.BackendEnvironment

/** Owns nothing itself: the Practice presenter lives in the account graph (shared by entry points). */
@Composable
internal fun PracticeScene(
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    onBuy: () -> Unit,
    onExplore: () -> Unit,
    onLearn: () -> Unit,
    onSignIn: () -> Unit,
    onCompany: (String) -> Unit,
    onTrade: (String, OrderSide) -> Unit,
    onManagePlan: () -> Unit
) {
    val presenter = accounts.practice
    val state by presenter.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(presenter) {
        presenter.refresh()
        onPauseOrDispose { }
    }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PracticeScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                when (action) {
                    is PracticeAction.Tab -> presenter.selectTab(action.tab)
                    is PracticeAction.Range -> presenter.selectRange(action.range)
                    is PracticeAction.Filter -> presenter.filter(action.type)
                    PracticeAction.Refresh -> presenter.refresh()
                    PracticeAction.BuyStock -> if (state.atFreeLimit) presenter.showHoldingLimit() else onBuy()
                    PracticeAction.ExploreStocks -> onExplore()
                    PracticeAction.Learn -> onLearn()
                    PracticeAction.SignIn -> onSignIn()
                    is PracticeAction.Trade -> onTrade(action.symbol, action.side)
                    is PracticeAction.OpenCompany -> onCompany(action.symbol)
                    is PracticeAction.Locked -> presenter.showLocked(action.feature)
                    PracticeAction.ShowTrial -> presenter.showTrialConfirm()
                    PracticeAction.StartTrial -> presenter.startTrial()
                    PracticeAction.ShowPlus -> presenter.showPlus()
                    PracticeAction.ManagePlan -> { presenter.dismissSheet(); onManagePlan() }
                    PracticeAction.ShowReset -> presenter.showReset()
                    PracticeAction.Reset -> presenter.reset()
                    PracticeAction.DismissSheet -> presenter.dismissSheet()
                    PracticeAction.DismissMessage -> presenter.dismissMessage()
                    is PracticeAction.Answer -> presenter.answerChallenge(action.challengeId, action.optionId)
                    is PracticeAction.RetryChallenge -> presenter.retryChallenge(action.challengeId)
                    is PracticeAction.Scenario -> presenter.loadScenario(action.name)
                }
            }
        }
    }
}

internal class PracticeOrderViewModel(route: PracticeOrderRoute, accounts: AccountDependencies) : ViewModel() {
    val presenter: PracticeOrderPresenter = accounts.practiceOrder(route.symbol, if (route.side == "SELL") OrderSide.SELL else OrderSide.BUY, viewModelScope).also { it.start() }
}

/** Owns one order's presenter; after a fill the shared Practice overview is refreshed. */
@Composable
internal fun PracticeOrderScene(
    route: PracticeOrderRoute,
    environment: BackendEnvironment,
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    onViewHoldings: () -> Unit,
    onExplore: () -> Unit,
    onLearn: () -> Unit,
    onUpgrade: () -> Unit,
    onBack: () -> Unit
) {
    val model = viewModel(key = "practice-order:${route.symbol}:${route.side}:$environment") { PracticeOrderViewModel(route, accounts) }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) { if (state.result != null) accounts.practice.refresh() }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PracticeOrderScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                val p = model.presenter
                when (action) {
                    is OrderAction.Mode -> p.setMode(action.mode)
                    is OrderAction.Input -> p.setInput(action.text)
                    OrderAction.Review -> p.review()
                    OrderAction.Back -> p.back()
                    OrderAction.Confirm -> p.confirm()
                    OrderAction.RefreshPrice -> p.refreshPreview()
                    OrderAction.DismissLimit -> p.dismissHoldingLimit()
                    OrderAction.UpgradeOptions -> { p.dismissHoldingLimit(); accounts.practice.showHoldingLimit(); onUpgrade() }
                    OrderAction.ViewHoldings -> { accounts.practice.selectTab(PracticeTab.HOLDINGS); onViewHoldings() }
                    OrderAction.ExploreStocks -> onExplore()
                    OrderAction.ContinueLearning -> onLearn()
                }
            }
        }
    }
}
