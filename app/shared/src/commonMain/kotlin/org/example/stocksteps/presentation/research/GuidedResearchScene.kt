package org.example.stocksteps.presentation.research

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
import kotlinx.coroutines.flow.map
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.learning.GuidedResearchPresenter
import org.example.stocksteps.learning.GuidedResearchState
import org.example.stocksteps.learning.ephemeralLearningProgress
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.stocksearch.StockWatchlistState
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class GuidedResearchViewModel(route: GuidedResearchRoute, data: StockStepsDependencies, accounts: AccountDependencies?) : ViewModel() {
    private val close = data::close
    @OptIn(ExperimentalTime::class)
    val presenter = GuidedResearchPresenter(
        data = data.guidedResearch(),
        progress = accounts?.learning ?: ephemeralLearningProgress(viewModelScope),
        plus = accounts?.entitlements?.state?.map { it?.plus } ?: flowOf(null),
        asker = accounts?.researchAsk,
        scope = viewModelScope,
        today = { Clock.System.now().toString().take(10) }
    ).also { it.load(route.symbol, route.name, route.step) }
    override fun onCleared() = close()
}

/** Owns one company's research presenter (keyed by Mock/Real) and wires navigation. */
@Composable
internal fun GuidedResearchScene(
    route: GuidedResearchRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onCompany: (String) -> Unit,
    onSearch: () -> Unit,
    onLearn: () -> Unit,
    onUpgrade: () -> Unit
) {
    val model = viewModel(key = "research:${route.symbol}:$environment") {
        GuidedResearchViewModel(route, StockStepsDependencies(backend::currentUrl), accounts)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    // The same watchlist toggle used by Company Details and Search.
    val watchlistModel = accounts?.let { owner -> viewModel(key = "stock-watchlist") { owner.stockWatchlistViewModel() } }
    val watchlist by (watchlistModel?.state ?: MutableStateFlow(StockWatchlistState())).collectAsStateWithLifecycle()
    val presenter = model.presenter
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GuidedResearchScreen(state, watched = route.symbol.uppercase() in watchlist.symbols, watchlistEnabled = watchlist.enabled,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                when (action) {
                    GuidedResearchAction.StartOrContinue -> presenter.startOrContinue()
                    is GuidedResearchAction.Open -> presenter.open(action.screen)
                    GuidedResearchAction.Next -> presenter.next()
                    GuidedResearchAction.Back -> presenter.back()
                    is GuidedResearchAction.Answer -> presenter.answer(action.optionId)
                    GuidedResearchAction.RetryQuiz -> presenter.retryQuiz()
                    GuidedResearchAction.Restart -> presenter.restart()
                    GuidedResearchAction.Retry -> presenter.retry()
                    is GuidedResearchAction.Ask -> presenter.ask(action.question)
                    GuidedResearchAction.RequestAi -> presenter.requestAi()
                    GuidedResearchAction.Upgrade -> { presenter.dismissUpgrade(); onUpgrade() }
                    GuidedResearchAction.DismissUpgrade -> presenter.dismissUpgrade()
                    GuidedResearchAction.DismissMessage -> presenter.dismissMessage()
                    GuidedResearchAction.ToggleWatchlist -> watchlistModel?.toggle(StockSearchResult(state.symbol, state.name))
                    GuidedResearchAction.CompanyDetails -> onCompany(state.symbol)
                    GuidedResearchAction.ResearchAnother -> onSearch()
                    GuidedResearchAction.ContinueLearning -> onLearn()
                }
            }
        }
    }
    watchlist.choosing?.let { stock ->
        org.example.stocksteps.presentation.stocksearch.WatchlistChooserSheet(stock, watchlist.lists,
            onChoose = { watchlistModel?.choose(it) }, onDismiss = { watchlistModel?.cancelChoice() })
    }
}
