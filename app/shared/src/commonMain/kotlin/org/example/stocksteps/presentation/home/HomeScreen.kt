package org.example.stocksteps.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.*
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.presentation.AdaptiveSinglePane

/** One keyed lazy scroll. Unsupported portfolio and lesson progress are deliberately absent. */
@Composable
internal fun HomeScreen(
    state: PersonalDashboard,
    localHour: Int,
    hinge: WindowHinge?,
    showMockPersonas: Boolean = false,
    portfolio: org.example.stocksteps.portfolio.PortfolioUiState? = null,
    onPortfolio: () -> Unit = {},
    onAction: (HomeAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val content = Modifier
        .widthIn(max = StockStepsTheme.dimensions.contentMaxWidth)
        .fillMaxWidth()
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item("header") { PersonalHomeHeader(onAction, content) }
                if (showMockPersonas) {
                    item("mock-persona") { HomePersonaPicker(state.mockPersona, onAction) }
                }
                if (state.initializing) {
                    item("restoring") { StockLoadingState { StockRowSkeleton() } }
                } else {
                    item("greeting") {
                        Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(PersonalDashboardRules.greeting(localHour), style = StockStepsTheme.typography.sectionTitle,
                                color = StockStepsTheme.colors.textPrimary)
                            Text("What matters to you today", style = StockStepsTheme.typography.small,
                                color = StockStepsTheme.colors.textSecondary)
                        }
                    }
                    if (state.mockPersona != null && state.newsNotice != null) {
                        item("capability-notice") {
                            Text(state.newsNotice.orEmpty(), modifier = content, style = StockStepsTheme.typography.small,
                                color = StockStepsTheme.colors.textSecondary)
                        }
                    }
                    if (portfolio != null) item("portfolio") { org.example.stocksteps.presentation.portfolio.PortfolioSummaryCard(portfolio, onPortfolio) }
                    item("watchlist") { HomeWatchlistSection(state, onAction, content) }
                    if (state.brief.isNotEmpty()) {
                        item("brief") {
                            HomeFactsSection("Your Daily Brief", state.brief, onAction, content)
                        }
                    }
                    if (state.events.isNotEmpty() || state.alertsError != null) {
                        item("events") {
                            Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                HomeFactsSection("Upcoming & alerts", state.events, onAction)
                                state.alertsError?.let { StockErrorState("Alerts unavailable. Try again.", { onAction(HomeAction.RetryAlerts) }) }
                            }
                        }
                    }
                    // Learn is currently a destination, not a progress engine. No fictitious completion.
                    item("learn") { HomeLearnCard({ onAction(HomeAction.Learn) }, content) }
                    if (state.watchlistCount > 0) {
                        item("news") { PersonalHomeNews(state, onAction, content) }
                    }
                    if (state.recent.isNotEmpty()) {
                        item("recent") {
                            StockCard(modifier = content, bordered = false) {
                                StockSectionHeader("Recently viewed", actionText = "Clear", onActionClick = { onAction(HomeAction.ClearRecent) })
                                state.recent.forEach { recent ->
                                    StockRow(symbol = recent.instrument.symbol, name = recent.instrument.name,
                                        onClick = { onAction(HomeAction.OpenStock(recent.instrument.symbol)) })
                                }
                            }
                        }
                    }
                    item("refresh") {
                        TextButton(onClick = { onAction(HomeAction.Refresh) }) { Text("Refresh dashboard") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalHomeHeader(onAction: (HomeAction) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { StockBrandMark("StockSteps") }
        IconButton(onClick = { onAction(HomeAction.Search) }) {
            Icon(StockIcons.Search, contentDescription = "Search stocks")
        }
        // Alert history has no unread/read contract, so no invented unread badge.
        IconButton(onClick = { onAction(HomeAction.Alerts) }) {
            Icon(StockIcons.Bell, contentDescription = "Your alerts")
        }
        IconButton(onClick = { onAction(HomeAction.Settings) }) {
            Icon(StockIcons.Person, contentDescription = "Profile and settings")
        }
    }
}

@Composable
private fun HomeWatchlistSection(state: PersonalDashboard, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    StockCard(modifier = modifier, bordered = false) {
        StockSectionHeader("Watchlist highlights", actionText = "View all", onActionClick = { onAction(HomeAction.Watchlist) })
        when {
            state.watchlistLoading && state.watchlistCount == 0 -> StockLoadingState { StockRowSkeleton() }
            state.watchlistError != null && state.watchlistCount == 0 -> StockErrorState(state.watchlistError.orEmpty(), { onAction(HomeAction.RetryWatchlists) })
            state.watchlistCount == 0 -> {
                Text("Start with a company you know. Save it to follow its price and news.", style = StockStepsTheme.typography.small)
                TextButton(onClick = { onAction(HomeAction.Search) }) { Text("Find stocks") }
            }
            else -> {
                state.highlights.forEach { highlight ->
                    StockRow(model = highlight.row, onClick = { onAction(HomeAction.OpenStock(highlight.instrument.symbol)) })
                    if (highlight.stale) Text("${highlight.instrument.symbol} · Saved or delayed price", style = StockStepsTheme.typography.caption)
                }
                if (state.quotesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.quotesError?.let { StockErrorState(it, { onAction(HomeAction.RetryQuotes) }) }
                state.watchlistError?.let { StockErrorState("Showing your saved watchlist.", { onAction(HomeAction.RetryWatchlists) }) }
                state.quoteNotice?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun HomeFactsSection(title: String, facts: List<HomeFact>, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    StockCard(modifier = modifier, bordered = false) {
        StockSectionHeader(title)
        facts.forEach { fact ->
            TextButton(onClick = {
                onAction(if (fact.alerts) HomeAction.OpenStockAlerts(fact.symbol) else HomeAction.OpenStock(fact.symbol))
            }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                    Text(fact.title, style = StockStepsTheme.typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                    Text(fact.detail, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun PersonalHomeNews(state: PersonalDashboard, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    StockCard(modifier = modifier, bordered = false) {
        StockSectionHeader("News for you")
        state.newsNotice?.let { Text(it, style = StockStepsTheme.typography.caption) }
        if (state.newsLoading && state.stories.isEmpty()) StockNewsCardSkeleton()
        state.stories.forEach { story ->
            Text(story.symbol, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.primary)
            StockNewsCard(model = NewsPresentation.model(story.article), onClick = { onAction(HomeAction.OpenArticle(story.article.url)) })
        }
        state.newsError?.let { StockErrorState(it, { onAction(HomeAction.RetryNews) }) }
        if (!state.newsLoading && state.newsError == null && state.stories.isEmpty()) {
            Text("No recent company news for your watchlist.", style = StockStepsTheme.typography.small)
        }
    }
}

@Composable
private fun HomePersonaPicker(selected: String?, onAction: (HomeAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Sample scenario: ${selected ?: "My saved companies"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<String?>(null) + listOf("new-user", "watchlist-only", "portfolio-only", "watchlist-and-portfolio", "learning-only",
                "upcoming-earnings", "triggered-alerts", "no-news", "stale-quotes", "partial-failures")).forEach { id ->
                DropdownMenuItem(text = { Text(id ?: "My saved companies") }, onClick = {
                    expanded = false
                    onAction(HomeAction.Persona(id))
                })
            }
        }
    }
}
