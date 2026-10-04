package org.example.stocksteps.presentation.discovery

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.*
import org.example.stocksteps.di.StockStepsDependencies

@Composable
internal fun DiscoveryScene(
    baseUrl: String?,
    hinge: WindowHinge?,
    onExplore: (String) -> Unit,
    onSearch: () -> Unit
) {
    val model = viewModel(key = "discovery:${baseUrl.orEmpty()}") {
        StockStepsDependencies(baseUrl ?: localBackendUrl()).discoveryViewModel()
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
