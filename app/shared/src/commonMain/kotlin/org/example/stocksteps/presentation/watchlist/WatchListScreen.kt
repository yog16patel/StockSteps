package org.example.stocksteps.presentation.watchlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun WatchListScreen(state: WatchListState, hinge: WindowHinge?, onRemove: (String) -> Unit, onRetrySync: () -> Unit, onSignIn: () -> Unit, onSearch: () -> Unit, onExplore: (String) -> Unit) {
    AdaptiveSinglePane(hinge) { region ->
        LazyColumn(
            modifier = region.padding(ThemeSpacing.extraLarge.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)
        ) {
            item { Text(text = "WatchList", style = MaterialTheme.typography.headlineMedium) }
            item {
                if (state.session.initializing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else if (state.session.user == null) {
                    Text(text = "Saved on this device. Sign in to sync across devices.")
                    TextButton(onClick = onSignIn) { Text(text = "Sign in or create account") }
                } else {
                    Text(text = "Account: ${state.session.user?.email ?: "Signed in"}")
                    Text(text = if (state.sync.syncing) "Syncing…" else if (state.sync.pendingCount > 0) "${state.sync.pendingCount} changes waiting to sync" else "No pending changes")
                }
            }
            state.error?.let { item { Text(text = it, color = MaterialTheme.colorScheme.error) } }
            state.sync.error?.let { message -> item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetrySync) { Text(text = "Retry sync") }
            } }
            item { Button(onClick = onSearch) { Text(text = "Find stocks to add") } }
            if (state.items.isEmpty()) item { Text(text = "Your watchlist is empty. Find a stock and add it from its details.") }
            items(state.items, key = { it.symbol }) { stock ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onExplore(stock.symbol) }) { Text(text = stock.symbol) }
                    TextButton(onClick = { onRemove(stock.symbol) }, enabled = !state.session.initializing) { Text(text = "Remove ${stock.symbol}") }
                }
                HorizontalDivider()
            }
        }
    }
}
