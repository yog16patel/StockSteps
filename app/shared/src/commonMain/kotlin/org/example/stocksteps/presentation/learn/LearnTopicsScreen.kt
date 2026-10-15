package org.example.stocksteps.presentation.learn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.BeginnerEducation
import org.example.stocksteps.learning.EducationEntry
import org.example.stocksteps.learning.LearnDisplay
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.painterResource

private val L = LearnDisplay

/**
 * Topics (Phase 5D.5.2): all catalogue topics with a term filter, or one topic's terms when [topic] is set. Rows only; every value comes from
 * `BeginnerEducation` through [LearnDisplay].
 */
@Composable
internal fun LearnTopicsScreen(topic: String?, focusSearch: Boolean, hinge: WindowHinge?, onTopic: (String) -> Unit, onTerm: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val selected = topic?.let { L.topic(it) }
    val results = remember(query) { L.search(query) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    LaunchedEffect(focusSearch) { if (focusSearch && selected == null) runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                item("header") {
                    Column(content.padding(bottom = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(selected?.name ?: L.TOPICS_TITLE, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textTitle)
                        Text(selected?.let { L.termCount(it) } ?: L.TOPICS_INTRO, style = typography.body, color = colors.textBody)
                        // Reference: a clean list; the filter appears when Learn's search opened this screen (or while it holds a query).
                        if (selected == null && (focusSearch || query.isNotEmpty())) StockTextField(query, { query = it.take(60) }, L.SEARCH_LABEL, Modifier.fillMaxWidth().focusRequester(focus))
                    }
                }
                when {
                    selected != null -> itemsIndexed(selected.terms, key = { _, it -> it.id }) { _, e -> TermRow(e, content, onTerm) }
                    query.isNotBlank() -> {
                        item("count") {
                            Text(if (results.isEmpty()) L.searchEmpty(query) else L.resultCount(results.size), content.semantics { liveRegion = LiveRegionMode.Polite },
                                style = typography.caption, color = colors.textMeta)
                        }
                        itemsIndexed(results, key = { _, it -> "r-${it.id}" }) { _, e -> TermRow(e, content, onTerm) }
                    }
                    else -> itemsIndexed(L.topics(), key = { _, it -> it.name }) { _, t ->
                        LearnCardRow(t.name, L.topicSummary(t), content, "Open ${t.name}", learnTopicIcon(L.topicIcon(t.name)), learnTopicTint(L.topicTone(t.name))) { onTopic(t.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermRow(entry: EducationEntry, modifier: Modifier, onTerm: (String) -> Unit) =
    LearnCardRow(entry.title, L.preview(entry), modifier, "Read ${entry.title}") { onTerm(entry.id) }

/** Reference list style: each entry is its own borderless card — optional coloured icon, bold title, one supporting line, chevron. */
@Composable
private fun LearnCardRow(title: String, detail: String, modifier: Modifier, actionLabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
                         tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    StockCard(modifier, onClick = onClick, onClickLabel = actionLabel, bordered = false, contentPadding = PaddingValues(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (icon != null) Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.icon), tint = tint ?: colors.primaryText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(L.unbroken(title), style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
                Text(detail, style = StockStepsTheme.typography.small, color = colors.textSupporting)
            }
            Icon(StockIcons.ChevronRight, null, Modifier.size(StockStepsTheme.dimensions.icon), tint = colors.textTertiary)
        }
    }
}

/**
 * A term's reading page (Phase 5D.5.2): the catalogue's explanation as a short lesson — topic, title, the "what it is" sentence, an outline,
 * then each section, and the next term. The illustration band is decorative and hidden at large text.
 */
@Composable
internal fun LearnTermScreen(id: String, hinge: WindowHinge?, onTerm: (String) -> Unit, onTopics: () -> Unit, onSearch: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val entry = BeginnerEducation.entry(id) ?: return
    val next = L.next(entry)
    val largeText = LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!largeText) item("art") {
                    Box(content.height(StockStepsTheme.dimensions.learnIllustration * 2).background(colors.primaryContainer, StockStepsTheme.shapes.card)
                        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                        Image(painterResource(Res.drawable.ill_learn_basics), null, Modifier.size(StockStepsTheme.dimensions.learnIllustration * 1.5f))
                    }
                }
                item("title") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(L.unbroken(entry.title), Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textTitle)
                        Text(entry.short, style = typography.body, color = colors.textBody)
                    }
                }
                item("outline") {
                    StockCard(content, containerColor = colors.primaryContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(L.IN_THIS_LESSON, Modifier.semantics { heading() }, style = typography.bodySemiBold, color = colors.textTitle)
                        L.outline(entry).forEach { Text("• $it", style = typography.small, color = colors.textBody) }
                    }
                }
                L.sections(entry).forEach { section ->
                    item("s-${section.title}") {
                        Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(section.title, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
                            Text(section.body, style = typography.body, color = colors.textBody)
                        }
                    }
                }
                item("next") {
                    Column(content) {
                        if (next != null) { StockNavigationRow(L.nextLabel(next), { onTerm(next.id) }, detail = next.topic, icon = StockIcons.ArrowForward); StockDivider() }
                        StockNavigationRow(L.ALL_TOPICS, onTopics, icon = StockIcons.School)
                        StockDivider()
                        StockNavigationRow(L.RESEARCH_ANY, onSearch, detail = L.RESEARCH_ANY_DETAIL, icon = StockIcons.Search)
                    }
                }
            }
        }
    }
}
