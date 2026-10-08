package org.example.stocksteps.presentation.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.BeginnerEducation
import org.example.stocksteps.learning.EducationEntry
import org.example.stocksteps.learning.LearningProgressRepository
import org.example.stocksteps.learning.ResearchProgress
import org.example.stocksteps.learning.ResearchStep
import org.example.stocksteps.presentation.AdaptiveSinglePane

/** Companies offered as a first research; any company can be researched through search. */
internal val LearnQuickPicks = listOf("AAPL" to "Apple", "KO" to "Coca-Cola", "JPM" to "JPMorgan Chase", "MSFT" to "Microsoft")

/** Learn hub: start, continue and review company research, plus the financial terms glossary. All free. */
@Composable
internal fun LearnScreen(
    progress: LearningProgressRepository.State,
    hinge: WindowHinge?,
    onResearch: (symbol: String, name: String) -> Unit,
    onSearch: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EducationEntry?>(null) }
    val inProgress = progress.inProgress
    val completed = progress.completed
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                item("intro") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Learn", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                        Text("Research a company one simple question at a time. Every lesson is free.", style = typography.body, color = colors.textBody)
                        if (progress.syncFailed) Text("Your progress is saved on this device and will sync with your account when the connection is back.",
                            style = typography.caption, color = colors.textSecondary)
                    }
                }
                if (inProgress.isNotEmpty()) {
                    item("continue-title") { Title("Continue learning", content) }
                    items(inProgress, key = { "continue-${it.symbol}" }) { journey -> JourneyCard(journey, "Continue", content) { onResearch(journey.symbol, journey.name) } }
                }
                item("start-title") { Title("Start learning", content) }
                item("start") {
                    StockCard(content, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                        Text("Understand a stock in five steps", style = typography.cardTitle, color = colors.textPrimary)
                        Text("What it does, whether it's growing, whether it makes money, how much debt it has, and whether the stock looks expensive.",
                            style = typography.small, color = colors.textBody)
                        LearnQuickPicks.filter { (symbol, _) -> progress.journey(symbol) == null }.forEach { (symbol, name) ->
                            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                                .clickable(onClickLabel = "Research $name") { onResearch(symbol, name) },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                StockTickerAvatar(symbol, size = StockStepsTheme.dimensions.logoCompact)
                                Text("Research $name", Modifier.weight(1f), style = typography.body, color = colors.primaryText)
                                Text(symbol, style = typography.caption, color = colors.textSecondary)
                            }
                        }
                    }
                }
                item("search-title") { Title("Research a company", content) }
                item("search") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Search for any company, then choose \"Understand This Stock\" on its page.", style = typography.small, color = colors.textBody)
                        StockButton("Search companies", onSearch, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
                    }
                }
                if (completed.isNotEmpty()) {
                    item("completed-title") { Title("Completed research", content) }
                    items(completed, key = { "done-${it.symbol}" }) { journey -> JourneyCard(journey, "Review", content) { onResearch(journey.symbol, journey.name) } }
                }
                item("terms-title") { Title("Financial terms", content) }
                items(BeginnerEducation.entries, key = { "term-${it.id}" }) { entry ->
                    Row(content.heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = "Explain") { education = entry },
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = typography.body, color = colors.textPrimary)
                            Text(entry.topic, style = typography.caption, color = colors.textSecondary)
                        }
                        MetricInfoIcon(entry) { education = it }
                    }
                }
            }
        }
    }
    education?.let { BeginnerExplanationSheet(it) { education = null } }
}

@Composable
private fun Title(text: String, modifier: Modifier) {
    Text(text, modifier.padding(top = StockStepsTheme.spacing.md).semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
}

@Composable
private fun JourneyCard(journey: ResearchProgress, action: String, modifier: Modifier, onClick: () -> Unit) {
    val typography = StockStepsTheme.typography
    val label = "${journey.completedCount} of ${ResearchStep.COUNT} steps completed"
    StockCard(modifier.semantics(mergeDescendants = true) { contentDescription = "${journey.name}, $label" }, onClick = onClick, onClickLabel = "$action ${journey.name}", verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(journey.symbol, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f)) {
                Text(journey.name, style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                Text(label, style = typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
            Text(action, style = typography.label, color = StockStepsTheme.colors.primaryText)
        }
        LinearProgressIndicator(progress = { journey.completedCount / ResearchStep.COUNT.toFloat() }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            color = StockStepsTheme.colors.primary, trackColor = StockStepsTheme.colors.surfaceSecondary)
    }
}
