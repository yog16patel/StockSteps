package org.example.stocksteps.presentation.learn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.LearnDisplay
import org.example.stocksteps.learning.LearnTopic
import org.example.stocksteps.learning.LearningProgressRepository
import org.example.stocksteps.learning.ResearchProgress
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.theme.StockCardGridLayout
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Companies offered as a first research; any company can be researched through search. */
internal val LearnQuickPicks = listOf("AAPL" to "Apple", "KO" to "Coca-Cola", "JPM" to "JPMorgan Chase", "MSFT" to "Microsoft")

private val L = LearnDisplay

/** Icon per topic key (`LearnDisplay.topicIcon`); neutral, never a direction cue. */
internal fun learnTopicIcon(key: String): ImageVector = when (key) {
    "business" -> StockIcons.Briefcase
    "growth" -> StockIcons.TrendingUp
    "profit" -> StockIcons.Coin
    "health" -> StockIcons.Shield
    "valuation" -> StockIcons.Tag
    "income" -> StockIcons.Bank
    "earnings" -> StockIcons.BarChart
    "portfolio" -> StockIcons.PieChart
    else -> StockIcons.School
}

/** Topic icon colour from `LearnDisplay.topicTone`, using existing theme colours only. */
@Composable
internal fun learnTopicTint(tone: String): androidx.compose.ui.graphics.Color = when (tone) {
    "green" -> StockStepsTheme.colors.positiveText
    "indigo" -> StockStepsTheme.colors.learnAccent
    "orange" -> StockStepsTheme.colors.cautionText
    else -> StockStepsTheme.colors.primaryText
}

@OptIn(ExperimentalTime::class)
private fun dayOfYear(): Int = LearnDisplay.dayOfYear(Clock.System.now().toEpochMilliseconds())

/**
 * Learn home (Phase 5D.5.2): one clear start (resume, else the first company not yet researched), topics from the catalogue, today's concept,
 * real progress only, then more ways to learn. Everything shown comes from the existing catalogue and saved research progress.
 */
@Composable
internal fun LearnScreen(
    progress: LearningProgressRepository.State,
    hinge: WindowHinge?,
    onResearch: (symbol: String, name: String) -> Unit,
    onSearch: () -> Unit,
    onPractice: () -> Unit = {},
    onTopics: (topic: String?, search: Boolean) -> Unit = { _, _ -> },
    onTerm: (String) -> Unit = {}
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val inProgress = progress.inProgress
    val completed = progress.completed
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val concept = remember { L.todaysConcept(dayOfYear()) }
    // D-L2: resume the latest research, else the first suggested company not started, else search.
    val start: () -> Unit = {
        inProgress.firstOrNull()?.let { onResearch(it.symbol, it.name) }
            ?: LearnQuickPicks.firstOrNull { (s, _) -> progress.journey(s) == null }?.let { (s, n) -> onResearch(s, n) }
            ?: onSearch()
    }
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                item("header") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L.TITLE, Modifier.weight(1f).semantics { heading() }, style = typography.screenTitle, color = colors.textTitle)
                            IconButton(onClick = { onTopics(null, true) }) { Icon(StockIcons.Search, L.SEARCH_ACTION, tint = colors.iconSecondary) }
                        }
                        Text(L.INTRO, style = typography.body, color = colors.textBody)
                        if (progress.syncFailed) Text(L.SYNC_NOTE, style = typography.caption, color = colors.textMeta)
                    }
                }
                item("start") { StartCard(content, if (inProgress.isNotEmpty()) L.CONTINUE_ACTION else L.START_ACTION, start) }
                item("topics") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(L.EXPLORE, actionText = L.SEE_ALL, onActionClick = { onTopics(null, false) })
                        TopicGrid(L.featuredTopics()) { onTopics(it, false) }
                    }
                }
                item("concept") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(L.TODAY)
                        StockCard(Modifier.fillMaxWidth(), onClick = { onTerm(concept.id) }, onClickLabel = concept.title, bordered = false) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                                Icon(StockIcons.Lightbulb, null, Modifier.size(StockStepsTheme.dimensions.icon), tint = colors.cautionText)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                    Text(L.unbroken(concept.title), style = typography.bodySemiBold, color = colors.textTitle)
                                    Text(L.preview(concept), style = typography.small, color = colors.textSupporting)
                                }
                                Icon(StockIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
                            }
                        }
                    }
                }
                if (inProgress.isNotEmpty()) item("continue") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(L.CONTINUE)
                        inProgress.forEach { JourneyCard(it, "Continue", Modifier.fillMaxWidth()) { onResearch(it.symbol, it.name) } }
                    }
                }
                item("more") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        StockSectionHeader(L.MORE_WAYS)
                        StockNavigationRow(L.RESEARCH_ANY, onSearch, detail = L.RESEARCH_ANY_DETAIL, icon = StockIcons.Search)
                        StockDivider()
                        StockNavigationRow(L.PRACTICE, onPractice, detail = L.PRACTICE_DETAIL, icon = StockIcons.TrendingUp)
                    }
                }
                if (completed.isNotEmpty()) item("completed") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(L.COMPLETED)
                        completed.forEach { JourneyCard(it, "Review", Modifier.fillMaxWidth()) { onResearch(it.symbol, it.name) } }
                    }
                }
            }
        }
    }
}

/** "Start here": the one highlighted card (learn tint, existing illustration hidden at large text), with a single primary action. */
@Composable
private fun StartCard(modifier: Modifier, action: String, onStart: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val largeText = LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    // Reference: a light-blue "Start here" card (existing primaryContainer token).
    StockCard(modifier, containerColor = colors.primaryContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(L.START_HERE.uppercase(), Modifier.semantics { contentDescription = L.START_HERE }, style = typography.tiny, color = colors.textSupporting)
                Text(L.START_TITLE, Modifier.semantics { heading() }, style = typography.cardTitle.copy(fontWeight = FontWeight.Bold), color = colors.textTitle)
                Text(L.START_BODY, style = typography.small, color = colors.textBody)
            }
            if (!largeText) Image(painterResource(Res.drawable.ill_learn_basics), null, Modifier.size(StockStepsTheme.dimensions.learnIllustration))
        }
        StockButton(action, onStart)
    }
}

/** Topic tiles: two columns while every topic word fits (`StockCardGridLayout`), else one; each opens that topic. */
@Composable
private fun TopicGrid(topics: List<LearnTopic>, onTopic: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = with(density) {
            topics.flatMap { StockCardGridLayout.words(it.name) }.maxOfOrNull { measurer.measure(it, typography.bodySemiBold).size.width }?.toDp()?.value ?: 0f
        }
        // Reference: icon beside name and coverage at normal text; at large text a compact tile (icon above, name only, centred) that stays in two
        // columns while every topic word fits the tile, else one column (word-safe; never a broken word).
        val largeText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale
        val inset = if (largeText) spacing.md.value else spacing.md.value + (StockStepsTheme.dimensions.icon.value + spacing.sm.value) / 2
        val columns = StockCardGridLayout.columns(widest, maxWidth.value, spacing.sm.value, inset, accessibilityText = false)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            topics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    row.forEach { topic ->
                        val icon = learnTopicIcon(L.topicIcon(topic.name))
                        val tint = learnTopicTint(L.topicTone(topic.name))
                        StockCard(Modifier.weight(1f).fillMaxHeight(), onClick = { onTopic(topic.name) }, onClickLabel = "Open ${topic.name}", bordered = false,
                            contentPadding = PaddingValues(spacing.md)) {
                            if (largeText) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.icon), tint = tint)
                                Text(topic.name, style = typography.bodySemiBold, color = colors.textTitle, textAlign = TextAlign.Center)
                            } else Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.icon), tint = tint)
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                    Text(topic.name, style = typography.bodySemiBold, color = colors.textTitle)
                                    Text(L.topicSummary(topic), style = typography.caption, color = colors.textSupporting)
                                }
                            }
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(journey: ResearchProgress, action: String, modifier: Modifier, onClick: () -> Unit) {
    val typography = StockStepsTheme.typography
    val label = L.journeyDetail(journey.completedCount)
    StockCard(modifier.semantics(mergeDescendants = true) { contentDescription = "${journey.name}, $label" }, onClick = onClick, onClickLabel = "$action ${journey.name}",
        bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(journey.symbol, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f)) {
                Text(journey.name, style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                Text(label, style = typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
            Text(action, style = typography.label, color = StockStepsTheme.colors.primaryText)
        }
        LinearProgressIndicator(progress = { journey.completedCount / L.stepCount().toFloat() }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            color = StockStepsTheme.colors.primary, trackColor = StockStepsTheme.colors.surfaceSecondary)
    }
}
