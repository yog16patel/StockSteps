package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.portfolio.analytics.PortfolioAnalyticsPresenter
import org.example.stocksteps.presentation.AdaptiveSinglePane

internal class PortfolioInsightsViewModel(val presenter: PortfolioAnalyticsPresenter) : ViewModel() {
    val state = presenter.state
}

/** Acquires the shared Insights presenter and wires its actions; rendering is [PortfolioInsightsScreen]. */
@Composable
internal fun PortfolioInsightsScene(
    accounts: AccountDependencies,
    onOpenCompany: (String) -> Unit,
    onSignIn: () -> Unit,
    hinge: WindowHinge? = null
) {
    val model = viewModel(key = "portfolio-insights") { PortfolioInsightsViewModel(accounts.insightsPresenter) }
    val state by model.state.collectAsStateWithLifecycle()
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PortfolioInsightsScreen(
                state = state,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onSelectAccount = model.presenter::selectAccount,
                onSelectPeriod = model.presenter::selectPeriod,
                onSelectBenchmark = model.presenter::selectBenchmark,
                onScenario = model.presenter::showScenario,
                onRefresh = model.presenter::refresh,
                onCompany = onOpenCompany,
                onSignIn = onSignIn
            )
        }
    }
}
