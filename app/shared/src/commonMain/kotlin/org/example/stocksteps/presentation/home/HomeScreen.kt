package org.example.stocksteps.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.presentation.AdaptiveSinglePane

/**
 * Beginner order: greeting, what the market is doing, what is moving, what to learn,
 * then news. One lazy vertical scroll; sections load and fail independently.
 * Search and the watchlist live in their own destinations.
 */
@Composable
internal fun HomeScreen(
    state: HomeState,
    signedIn: Boolean,
    hinge: WindowHinge?,
    onAction: (HomeAction) -> Unit,
    // "View All" renders only when its destination exists; Markets and News screens are pending.
    viewAllMovers: Boolean = false,
    viewAllNews: Boolean = false,
    news: List<NewsUiModel> = rememberHomeNews(state)
) {
    val spacing = StockStepsTheme.spacing
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.sectionGap)
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(start = spacing.screen, top = spacing.sm, end = spacing.screen, bottom = spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "header") { HomeHeader(signedIn, content) }
                item(key = "market") {
                    MarketSummarySection(state.market, onRetry = { onAction(HomeAction.RetryMarket) }, modifier = content.padding(top = spacing.md))
                }
                item(key = "movers") {
                    MoversSection(
                        movers = state.movers,
                        onSelect = { onAction(HomeAction.SelectMovers(it)) },
                        onOpenStock = { onAction(HomeAction.OpenStock(it)) },
                        onRetry = { onAction(HomeAction.RetryMarket) },
                        onViewAll = if (viewAllMovers) { { onAction(HomeAction.ViewAllMovers) } } else null,
                        modifier = section
                    )
                }
                item(key = "learn") { HomeLearnCard(onLearn = { onAction(HomeAction.Learn) }, modifier = section) }
                item(key = "news") {
                    HomeNewsSection(
                        status = state.newsStatus,
                        news = news,
                        onOpenArticle = { onAction(HomeAction.OpenArticle(it)) },
                        onRetry = { onAction(HomeAction.RetryNews) },
                        onViewAll = if (viewAllNews) { { onAction(HomeAction.ViewAllNews) } } else null,
                        modifier = section
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberHomeNews(state: HomeState): List<NewsUiModel> = remember(state.news) {
    state.news.map(NewsPresentation::model).distinctBy { it.id }.take(HomePresentation.MAX_NEWS)
}
