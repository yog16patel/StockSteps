package org.example.stocksteps.presentation.watchlist

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies

@Composable
internal fun WatchListScene(accounts: AccountDependencies, hinge: WindowHinge?, onSignIn: () -> Unit, onSearch: () -> Unit, onExplore: (String) -> Unit) {
    val model = viewModel { accounts.watchlistViewModel() }
    val state by model.state.collectAsStateWithLifecycle()
    WatchListScreen(
        state = state,
        hinge = hinge,
        onRemove = model::remove,
        onRetrySync = model::retry,
        onSignIn = onSignIn,
        onSearch = onSearch,
        onExplore = onExplore
    )
}
