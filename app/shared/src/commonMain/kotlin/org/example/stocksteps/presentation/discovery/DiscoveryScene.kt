package org.example.stocksteps.presentation.discovery

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.*
import org.example.stocksteps.di.StockStepsDependencies

@Composable
internal fun DiscoveryScene(
    backend: org.example.stocksteps.settings.BackendRouter,
    environment: org.example.stocksteps.settings.BackendEnvironment,
    hinge: WindowHinge?,
    onExplore: (String) -> Unit,
    onSearch: () -> Unit
) {
    val model = viewModel(key = "discovery:$environment") {
        StockStepsDependencies(backend::currentUrl).discoveryViewModel()
    }
    val state by model.state.collectAsStateWithLifecycle()
    DiscoveryScreen(
        state = state,
        hinge = hinge,
        onRetry = model::retry,
        onRefresh = model::refresh,
        onExplore = onExplore,
        onSearch = onSearch
    )
}
