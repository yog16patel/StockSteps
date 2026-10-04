package org.example.stocksteps.presentation.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.theme.ThemeSpacing

private const val PREVIEW_MOVER_COUNT = 5

@Composable
internal fun MarketMoversSection(
    title: String,
    feed: FeedState<MarketMover>,
    onRetry: () -> Unit,
    onExplore: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        FeedStatus(feed, onRetry)
        feed.items.take(PREVIEW_MOVER_COUNT).forEach { mover ->
            OutlinedCard(
                onClick = { onExplore(mover.symbol) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(ThemeSpacing.large.dp),
                    verticalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)
                ) {
                    Text(text = mover.symbol, style = MaterialTheme.typography.titleMedium)
                    mover.name?.let { Text(text = it) }
                    Text(text = "${mover.changePercent}% · Price ${mover.price}")
                }
            }
        }
    }
}

@Composable
internal fun <T> FeedStatus(feed: FeedState<T>, onRetry: () -> Unit) {
    if (feed.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    feed.error?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(text = "Retry") }
    }
    if (!feed.loading && feed.error == null && feed.items.isEmpty()) Text(text = "Nothing available right now.")
}
