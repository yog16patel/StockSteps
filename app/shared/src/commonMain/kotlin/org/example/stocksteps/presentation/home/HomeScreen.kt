package org.example.stocksteps.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.*

@Composable
internal fun HomeScreen(state: HomeState, hinge: WindowHinge?, onRefresh: () -> Unit, onSearch: () -> Unit, onExplore: (String) -> Unit) {
    var showingLesson by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(homeColor(HomeTokens.background))) {
        AdaptiveSinglePane(hinge) { region ->
            Column(
                modifier = region.verticalScroll(rememberScrollState()).padding(HomeTokens.screenPadding.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = HomeTokens.contentWidth.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(HomeTokens.sectionGap.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.extraLarge.dp)) {
                        Column {
                            Text("Good morning", style = HomeTokens.title.homeStyle(), color = homeColor(HomeTokens.ink))
                            Text("Markets made simple.", style = HomeTokens.body.homeStyle(), color = homeColor(HomeTokens.muted))
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = HomeTokens.searchHeight.dp)
                                .background(homeColor(HomeTokens.surface), RoundedCornerShape(HomeTokens.cardRadius.dp))
                                .clickable(onClick = onSearch).padding(horizontal = ThemeSpacing.extraLarge.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Search stocks or companies", style = HomeTokens.body.homeStyle(), color = homeColor(HomeTokens.muted))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
                        Text("Market snapshot", style = HomeTokens.section.homeStyle(), color = homeColor(HomeTokens.ink))
                        state.snapshot?.let { snapshot ->
                            Text(
                                text = when (snapshot.marketStatus) {
                                    org.example.stocksteps.model.MarketStatus.OPEN -> "US market open"
                                    org.example.stocksteps.model.MarketStatus.CLOSED -> "US regular session closed"
                                    org.example.stocksteps.model.MarketStatus.PRE_MARKET -> "Pre-market"
                                    org.example.stocksteps.model.MarketStatus.AFTER_HOURS -> "After-hours"
                                    org.example.stocksteps.model.MarketStatus.UNKNOWN -> "Market status unavailable"
                                },
                                style = HomeTokens.caption.homeStyle(),
                                color = homeColor(HomeTokens.muted)
                            )
                            Text("ETF proxies · USD", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
                        }
                        if (state.snapshotLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.snapshotError?.let {
                            Text(it, style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
                            TextButton(onClick = onRefresh) { Text("Retry") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.tiny.dp)) {
                            state.indices.forEach { HomeIndexCard(it, Modifier.weight(1f)) }
                        }
                        if (state.indices.any { it.error }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Some index data is unavailable.", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted), modifier = Modifier.weight(1f))
                                TextButton(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
                        Text("Your watchlist", style = HomeTokens.section.homeStyle(), color = homeColor(HomeTokens.ink))
                        if (state.stocks.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(homeColor(HomeTokens.surface), RoundedCornerShape(HomeTokens.cardRadius.dp))
                                    .padding(ThemeSpacing.large.dp)
                            ) {
                                Text("Your next step starts with a stock.", style = HomeTokens.body.homeStyle(), color = homeColor(HomeTokens.ink))
                                TextButton(onClick = onSearch) { Text("Find stocks to follow") }
                            }
                        }
                        state.stocks.forEach { HomeStockRow(it, onExplore) }
                    }
                    state.snapshot?.let { snapshot ->
                        Text("Updated ${snapshot.lastUpdated}", style = HomeTokens.caption.homeStyle(), color = homeColor(HomeTokens.muted))
                        HomeMovers("Top Gainers", "gainers", snapshot.gainers, state, onRefresh, onExplore)
                        HomeMovers("Top Losers", "losers", snapshot.losers, state, onRefresh, onExplore)
                        HomeMovers("Most Active", "mostActive", snapshot.mostActive, state, onRefresh, onExplore)
                    }
                    HomeLessonCard { showingLesson = true }
                }
            }
        }
    }
    if (showingLesson) AlertDialog(
        onDismissRequest = { showingLesson = false },
        title = { Text("What is a P/E ratio?") },
        text = { Text("P/E compares a company's share price with its yearly earnings per share.\n\nA $100 share with $5 in yearly earnings per share has a P/E of 20. Investors are paying $20 for each $1 of earnings.\n\nCompare similar companies. A higher or lower P/E alone does not tell you whether a stock is a good investment. Companies with losses may not have a meaningful P/E.") },
        confirmButton = { TextButton(onClick = { showingLesson = false }) { Text("Got it") } }
    )
}
