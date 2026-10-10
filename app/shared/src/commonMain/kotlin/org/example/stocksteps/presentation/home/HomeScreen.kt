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
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.*
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.presentation.AdaptiveSinglePane
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** One keyed lazy scroll. Unsupported portfolio and lesson progress are deliberately absent. */
@Composable
internal fun HomeScreen(
    state: PersonalDashboard,
    localHour: Int,
    hinge: WindowHinge?,
    showMockPersonas: Boolean = false,
    portfolio: org.example.stocksteps.portfolio.PortfolioUiState? = null,
    onPortfolio: () -> Unit = {},
    /** The most recently visited unfinished Guided Research (saved progress), if any. */
    research: org.example.stocksteps.learning.ResearchProgress? = null,
    /** The shared Daily Market Brief state (compact preview only; the reader is its own screen). */
    dailyBrief: org.example.stocksteps.brief.DailyBriefUiState? = null,
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
                item("header") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        PersonalHomeHeader(onAction, Modifier.fillMaxWidth())
                        Text(PersonalDashboardRules.greeting(localHour), Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle,
                            color = StockStepsTheme.colors.textTitle)
                        Text("What matters to you today", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSupporting)
                    }
                }
                // Market first (Phase 4A): the brief's index snapshot and its entry share one card.
                item("markets") {
                    HomeMarketOverview(dailyBrief, org.example.stocksteps.presentation.brief.briefNow(), { onAction(HomeAction.DailyBrief) }, content)
                }
                if (state.initializing) {
                    item("restoring") { StockLoadingState(content) { StockRowSkeleton() } }
                } else {
                    if (portfolio != null) item("portfolio") { HomePortfolioSummary(portfolio, onPortfolio, content) }
                    item("watchlist") { HomeWatchlistSection(state, onAction, content) }
                    if (state.brief.isNotEmpty()) {
                        item("brief") { HomeFactsSection("Today in your watchlist", state.brief, onAction, content) }
                    }
                    if (state.events.isNotEmpty() || state.alertsError != null) {
                        item("events") { HomeFactsSection("Upcoming & alerts", state.events, onAction, content, alertsError = state.alertsError) }
                    }
                    // Learn and practice: a quieter second tier. Continue Learning shows only real saved progress.
                    item("learn") {
                        Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockSectionHeader("Learn and practice")
                            if (research != null) HomeLearnCard({ onAction(HomeAction.ContinueResearch(research.symbol, research.name)) },
                                title = "Continue learning",
                                body = "Researching ${research.name} · ${research.completedCount} of ${org.example.stocksteps.learning.ResearchStep.COUNT} steps completed",
                                actionLabel = "Continue researching ${research.name}")
                            else HomeLearnCard({ onAction(HomeAction.Learn) })
                            org.example.stocksteps.presentation.practice.PracticeEntryCard({ onAction(HomeAction.Practice) },
                                body = "Practice investing with virtual money. No real money is used.")
                        }
                    }
                    if (state.watchlistCount > 0) {
                        item("news") { PersonalHomeNews(state, onAction, content) }
                    } else if (state.mockPersona != null && state.newsNotice != null) {
                        item("capability-notice") {
                            Text(state.newsNotice.orEmpty(), content, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textMeta)
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item("recent") {
                            StockCard(modifier = content, contentPadding = PaddingValues(vertical = spacing.xs)) {
                                StockSectionHeader("Recently viewed", Modifier.padding(horizontal = spacing.cardPadding), actionText = "Clear",
                                    onActionClick = { onAction(HomeAction.ClearRecent) })
                                state.recent.forEach { recent ->
                                    StockRow(symbol = recent.instrument.symbol, name = recent.instrument.name, compact = true,
                                        onClick = { onAction(HomeAction.OpenStock(recent.instrument.symbol)) })
                                }
                            }
                        }
                    }
                    if (showMockPersonas) {
                        item("mock-persona") { HomePersonaPicker(state.mockPersona, onAction, content) }
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
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    // Rows run edge to edge inside the card (as on Portfolio); messages keep the card padding.
    val inset = Modifier.padding(horizontal = spacing.cardPadding)
    StockCard(modifier = modifier, contentPadding = PaddingValues(vertical = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader("Watchlist highlights", inset, actionText = "View all", onActionClick = { onAction(HomeAction.Watchlist) })
        when {
            state.watchlistLoading && state.watchlistCount == 0 -> StockLoadingState(inset) { StockRowSkeleton() }
            state.watchlistError != null && state.watchlistCount == 0 -> StockErrorState(state.watchlistError.orEmpty(), { onAction(HomeAction.RetryWatchlists) }, inset)
            state.watchlistCount == 0 -> Column(inset.padding(bottom = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Start with a company you know. Save it to follow its price and news.", style = typography.small, color = colors.textBody)
                StockButton("Find stocks", { onAction(HomeAction.Search) }, variant = StockButtonVariant.SECONDARY, icon = StockIcons.Search)
            }
            else -> {
                state.highlights.forEachIndexed { n, highlight ->
                    if (n > 0) StockDivider(startIndent = spacing.cardPadding)
                    StockRow(model = highlight.row, onClick = { onAction(HomeAction.OpenStock(highlight.instrument.symbol)) })
                    if (highlight.stale) Text("${highlight.instrument.symbol} · Saved or delayed price", inset, style = typography.caption, color = colors.cautionText)
                }
                if (state.quotesLoading) LinearProgressIndicator(inset.fillMaxWidth().semantics { contentDescription = "Updating prices" })
                state.quotesError?.let { StockErrorState(it, { onAction(HomeAction.RetryQuotes) }, inset) }
                state.watchlistError?.let { StockErrorState("Showing your saved watchlist.", { onAction(HomeAction.RetryWatchlists) }, inset) }
                state.quoteNotice?.let { Text(it, inset, style = typography.caption, color = colors.textMeta) }
            }
        }
    }
}

/** Personal facts as plain tappable rows (title, muted detail, chevron); a failed alerts load is reported inside the same card. */
@Composable
private fun HomeFactsSection(title: String, facts: List<HomeFact>, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier,
                             alertsError: String? = null) {
    StockCard(modifier = modifier) {
        StockSectionHeader(title)
        facts.forEachIndexed { n, fact ->
            if (n > 0) StockDivider()
            HomeLinkRow(fact.title, fact.detail, onClick = {
                onAction(if (fact.alerts) HomeAction.OpenStockAlerts(fact.symbol) else if (fact.earnings) HomeAction.OpenEarnings(fact.symbol) else HomeAction.OpenStock(fact.symbol))
            })
        }
        alertsError?.let { StockErrorState("Alerts unavailable. Try again.", { onAction(HomeAction.RetryAlerts) }) }
    }
}

@Composable
private fun PersonalHomeNews(state: PersonalDashboard, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    StockCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader("News for you")
        state.newsNotice?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textMeta) }
        if (state.newsLoading && state.stories.isEmpty()) StockNewsCardSkeleton()
        state.stories.forEach { story ->
            // The company is context for the story, so it reads as a muted label rather than a link-coloured one.
            Text(story.symbol, Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSupporting)
            StockNewsCard(model = NewsPresentation.model(story.article), onClick = { onAction(HomeAction.OpenArticle(story.article.url)) })
        }
        state.newsError?.let { StockErrorState(it, { onAction(HomeAction.RetryNews) }) }
        if (!state.newsLoading && state.newsError == null && state.stories.isEmpty()) {
            Text("No recent company news for your watchlist.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSupporting)
        }
    }
}

/** MOCK only: picks a sample Home scenario. Kept at the end so it never pushes personal content down. */
@Composable
private fun HomePersonaPicker(selected: String?, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        StockStatusBadge("Sample", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT)
        Box {
        TextButton(onClick = { expanded = true }) { Text("Scenario: ${selected ?: "My saved companies"}") }
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
}
