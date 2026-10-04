package org.example.stocksteps.presentation.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import org.example.stocksteps.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.example.stocksteps.theme.ThemeSpacing

@Composable
private fun DiscoveryContent(
    state: DiscoveryState,
    modifier: Modifier,
    onRetry: (DiscoverySection) -> Unit,
    onRefresh: () -> Unit,
    onExplore: (String) -> Unit,
    onSearch: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.padding(ThemeSpacing.screen.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)
    ) {
        item {
            Text(text = "Discover", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Explore market movers and the latest headlines.")
            TextButton(onClick = onSearch) { Text(text = "Search stocks") }
            TextButton(onClick = onRefresh) { Text(text = "Refresh") }
        }
        item {
            MarketMoversSection("Top gainers", state.gainers, { onRetry(DiscoverySection.GAINERS) }, onExplore)
        }
        item {
            MarketMoversSection("Top losers", state.losers, { onRetry(DiscoverySection.LOSERS) }, onExplore)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
                Text(text = "Market news", style = MaterialTheme.typography.titleLarge)
                FeedStatus(state.news, { onRetry(DiscoverySection.NEWS) })
                state.news.items.forEach { article ->
                    org.example.stocksteps.presentation.news.NewsCard(article)
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryScreen(
    state: DiscoveryState,
    hinge: WindowHinge?,
    onRetry: (DiscoverySection) -> Unit,
    onRefresh: () -> Unit,
    onExplore: (String) -> Unit,
    onSearch: () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val layout = paneLayout(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            density.density,
            origin.x,
            origin.y,
            hinge
        )
        // Keep discovery in one safe region when a hinge separates the window.
        val pane = if (hinge == null) {
            PaneRect(
                x = 0f,
                y = 0f,
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat()
            )
        } else {
            layout.search
        }
        val modifier = with(density) {
            Modifier
                .absoluteOffset(pane.x.toDp(), pane.y.toDp())
                .size(pane.width.toDp(), pane.height.toDp())
        }
        DiscoveryContent(
            state = state,
            modifier = modifier,
            onRetry = onRetry,
            onRefresh = onRefresh,
            onExplore = onExplore,
            onSearch = onSearch
        )
    }
}
