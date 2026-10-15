package org.example.stocksteps.presentation.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import org.example.stocksteps.brief.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.BeginnerEducation
import org.example.stocksteps.learning.EducationEntry
import org.example.stocksteps.presentation.learn.BeginnerExplanationSheet
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.theme.StockBannerKind

internal sealed interface BriefAction {
    data object Retry : BriefAction
    data class OpenUrl(val url: String) : BriefAction
    data class OpenStock(val symbol: String) : BriefAction
    /** Opens Earnings Event Details for a calendar event id. */
    data class OpenEarnings(val eventId: String) : BriefAction
    /** Opens Earnings Results for a reported highlight. */
    data class OpenResults(val reportId: String) : BriefAction
    /** Opens the StockSteps+ personalized earnings digest (Phase 5). */
    data object EarningsDigest : BriefAction
    data class Explain(val storyId: String) : BriefAction
    data class Ask(val question: String) : BriefAction
    data object History : BriefAction
    data class OpenBrief(val id: String) : BriefAction
    data object Watchlist : BriefAction
    data object Learn : BriefAction
    data object SignIn : BriefAction
    data object Upgrade : BriefAction
    data object DismissUpgrade : BriefAction
    data object DismissMessage : BriefAction
    data class SavePreferences(val value: BriefPreferences) : BriefAction
    data object LoadPreferences : BriefAction
    data class Scenario(val name: String?) : BriefAction
}

private val D = DailyBriefDisplay

// ---------- Reader ----------

/**
 * Daily Brief reader (Global UI Refinement Phase 5D.5.1): answer first — one meta line, the server summary as the headline, freshness and
 * sessions, then compact index rows, stories with the first sentence and "Show details", personal sections, the Concept of the day and one
 * folded "About this brief". Borderless sections per call site; every value and sentence comes from the brief.
 */
@Composable
internal fun DailyBriefScreen(state: DailyBriefUiState, nowMillis: Long, modifier: Modifier, onAction: (BriefAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EducationEntry?>(null) }
    var preferences by remember { mutableStateOf(false) }
    val brief = state.current
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
        if (brief == null) {
            item("state") {
                if (state.loading) BriefSkeleton()
                else StockErrorState(state.error ?: D.UNAVAILABLE, { onAction(BriefAction.Retry) })
            }
            return@LazyColumn
        }
        item("header") { Header(brief, state, nowMillis, onAction) { preferences = true; onAction(BriefAction.LoadPreferences) } }

        item("markets") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockSectionHeader(D.MARKETS)
                if (brief.marketSnapshot.isEmpty()) Text(D.NO_INDICES, style = typography.small, color = colors.textSupporting)
                else Column {
                    brief.marketSnapshot.forEachIndexed { n, index ->
                        if (n > 0) StockDivider()
                        BriefIndexRow(index)
                    }
                }
                StockDisclosure(D.INDEX_EXPLAINER_TITLE, icon = StockIcons.School) {
                    Text(brief.indexExplainer, style = typography.small, color = colors.textBody)
                }
            }
        }

        item("stories-title") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockSectionHeader(D.STORIES)
                if (brief.stories.isEmpty()) Text(D.NO_STORIES, style = typography.small, color = colors.textSupporting)
            }
        }
        itemsIndexed(brief.stories, key = { _, it -> "story-${it.id}" }) { n, story ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                if (n > 0) StockDivider()
                StoryRow(story, state.ai[story.id], state.plus, nowMillis, onAction)
            }
        }
        if (brief.stories.isNotEmpty()) item("stories-about") {
            StockDisclosure(D.ABOUT_STORIES) { StockNotes(D.storyNotes(brief)) }
        }

        item("watchlist") { WatchlistSection(state, onAction) }

        state.personal?.earnings?.takeIf { it.isNotEmpty() }?.let { rows ->
            item("earnings") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockSectionHeader(D.EARNINGS)
                    rows.forEachIndexed { n, e ->
                        if (n > 0) StockDivider()
                        StockNavigationRow(D.earningsTitle(e), { onAction(e.eventId?.let { BriefAction.OpenEarnings(it) } ?: BriefAction.OpenStock(e.symbol)) },
                            detail = D.earningsDetail(e), actionLabel = D.earningsAction(e))
                    }
                    Text(D.EARNINGS_DATES_NOTE, style = typography.caption, color = colors.textMeta)
                }
            }
        }

        item("concept") { ConceptCard(brief.concept, onExplain = { education = BeginnerEducation.entry(brief.concept.learnTermId) }, onLearn = { onAction(BriefAction.Learn) }) }

        if (state.personal != null) item("premium") { PremiumSection(state, onAction) }

        item("footer") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockDisclosure(D.ABOUT_BRIEF) { StockNotes(D.aboutLines(brief)) }
                StockNavigationRow(D.HISTORY, { onAction(BriefAction.History) }, icon = StockIcons.Calendar)
            }
        }
    }
    education?.let { BeginnerExplanationSheet(it) { education = null } }
    if (preferences) PreferencesSheet(state, onAction) { preferences = false }
    if (state.upgrade) AlertDialog(onDismissRequest = { onAction(BriefAction.DismissUpgrade) }, title = { Text(D.UPGRADE_TITLE) },
        text = { Text(D.UPGRADE) },
        confirmButton = { StockButton(D.SEE_PLUS, { onAction(BriefAction.Upgrade) }, variant = StockButtonVariant.TEXT) },
        dismissButton = { StockButton(D.NOT_NOW, { onAction(BriefAction.DismissUpgrade) }, variant = StockButtonVariant.TEXT) })
    state.message?.let {
        AlertDialog(onDismissRequest = { onAction(BriefAction.DismissMessage) }, text = { Text(it) },
            confirmButton = { StockButton("OK", { onAction(BriefAction.DismissMessage) }, variant = StockButtonVariant.TEXT) })
    }
}

/** Meta line with the Sample badge and the ⋯ menu, the summary as the headline, freshness and sessions, then stale/offline banners. */
@Composable
private fun Header(brief: DailyBrief, state: DailyBriefUiState, nowMillis: Long, onAction: (BriefAction) -> Unit, onNotify: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var menu by remember { mutableStateOf(false) }
    var scenarios by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockTitleWithBadge(Modifier.weight(1f), title = { Text(D.meta(brief), style = typography.caption, color = colors.textMeta) }, badge = {
                if (brief.sampleData) StockStatusBadge(D.SAMPLE, StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT, contentDescription = D.SAMPLE_DESCRIPTION)
            })
            Box {
                IconButton(onClick = { menu = true }) { Icon(StockIcons.More, D.MORE_OPTIONS, tint = colors.iconSecondary) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(D.NOTIFY) }, { menu = false; onNotify() }, leadingIcon = { Icon(StockIcons.Bell, null) })
                    DropdownMenuItem({ Text(D.HISTORY) }, { menu = false; onAction(BriefAction.History) }, leadingIcon = { Icon(StockIcons.Calendar, null) })
                    // MOCK only: sample briefs come from the local fixtures; the server refuses scenarios in REAL.
                    if (brief.sampleData) DropdownMenuItem({ Text(D.SCENARIOS) }, { menu = false; scenarios = true }, leadingIcon = { Icon(StockIcons.Layers, null) })
                }
                DropdownMenu(scenarios, { scenarios = false }) {
                    DropdownMenuItem({ Text(D.SCENARIO_NONE) }, { scenarios = false; onAction(BriefAction.Scenario(null)) })
                    DailyBriefPresenter.SCENARIOS.forEach { n -> DropdownMenuItem({ Text(n) }, { scenarios = false; onAction(BriefAction.Scenario(n)) }) }
                }
            }
        }
        Text(brief.summaryLine, Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }, style = typography.sectionTitle, color = colors.textTitle)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(D.freshness(brief, nowMillis), style = typography.caption, color = colors.textMeta)
            D.sessionLines(brief).forEach { Text(it, style = typography.caption, color = colors.textMeta) }
        }
        D.banners(brief, nowMillis, state.offline).forEach { message ->
            StockBanner(if (message == D.STALE) StockBannerKind.WARNING else StockBannerKind.INFO, message)
        }
    }
}

/** One story: meta, headline, the provider excerpt's first sentence; details hold the rest, the company and the explanation. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StoryRow(story: BriefStory, ai: BriefAiState?, plus: Boolean, nowMillis: Long, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(D.storyMeta(story, nowMillis), style = typography.caption, color = colors.textMeta)
        Text(story.headline, Modifier.semantics { heading() }, style = typography.bodySemiBold, color = colors.textTitle)
        StockShowDetails(D.excerpt(story), hasMore = true, trailing = {
            StockLinkAction(D.READ_ARTICLE, { onAction(BriefAction.OpenUrl(story.sourceUrl)) }, icon = StockIcons.ArrowForward, description = D.readArticleLabel(story))
        }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                story.relatedSymbols.forEach { s -> StockButton(D.companyAction(s), { onAction(BriefAction.OpenStock(s)) }, variant = StockButtonVariant.SECONDARY) }
                StockLinkAction(D.understandLabel(plus), { onAction(BriefAction.Explain(story.id)) }, icon = StockIcons.Lightbulb, enabled = ai?.loading != true)
            }
        }
        ai?.let { AiAnswer(it, onAction) }
    }
}

@Composable
private fun AiAnswer(ai: BriefAiState, onAction: (BriefAction) -> Unit) {
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    when {
        ai.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = D.PREPARING })
        ai.error != null -> Text(ai.error!!, style = typography.small, color = colors.negativeText)
        ai.answer != null -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            val a = ai.answer!!
            Text(D.aiLabel(a), style = typography.label, color = colors.primaryText)
            Text(a.answer, style = typography.body, color = colors.textBody)
            a.points.forEach { Text("• $it", style = typography.small, color = colors.textBody) }
            if (a.insufficientEvidence) Text(D.INSUFFICIENT, style = typography.caption, color = colors.cautionText)
            a.sources.forEach { s -> StockLinkAction(D.aiSource(s), { onAction(BriefAction.OpenUrl(s.url)) }, icon = StockIcons.ArrowForward) }
            a.remainingToday?.let { Text(D.aiRemaining(it), style = typography.caption, color = colors.textMeta) }
        }
    }
}

@Composable
private fun WatchlistSection(state: DailyBriefUiState, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        StockSectionHeader(D.WATCHLIST)
        val personal = state.personal
        when {
            !state.signedIn -> StockEmptyState(D.SIGN_IN_PROMPT, actionText = D.SIGN_IN, onAction = { onAction(BriefAction.SignIn) })
            personal == null && state.personalLoading -> StockLoadingState { Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                StockSkeleton(Modifier.fillMaxWidth(0.8f)); StockSkeleton(Modifier.fillMaxWidth(0.6f)) } }
            personal == null -> Text(state.personalError ?: D.WATCHLIST_UNAVAILABLE, style = typography.small, color = colors.textSupporting)
            personal.watchlistCount == 0 -> StockEmptyState(D.WATCHLIST_EMPTY, actionText = D.GO_TO_WATCHLIST, onAction = { onAction(BriefAction.Watchlist) })
            personal.watchlistHighlights.isEmpty() -> Text(D.nothingNotable(personal.watchlistCount), style = typography.small, color = colors.textSupporting)
            else -> Column {
                personal.watchlistHighlights.forEachIndexed { n, h ->
                    if (n > 0) StockDivider()
                    StockNavigationRow(h.text, {
                        h.url?.let { onAction(BriefAction.OpenUrl(it)) } ?: h.reportId?.let { onAction(BriefAction.OpenResults(it)) } ?: onAction(BriefAction.OpenStock(h.symbol))
                    }, detail = listOfNotNull(D.highlightTitle(h), h.publisher).joinToString(" · "), actionLabel = D.highlightAction(h))
                }
                if (personal.moreHighlights > 0) {
                    StockDivider()
                    StockNavigationRow(D.moreHighlights(personal.moreHighlights), { onAction(BriefAction.Upgrade) }, badge = "StockSteps+")
                }
            }
        }
        personal?.notes?.firstOrNull { it.startsWith("Watching") }?.let { Text(it, style = typography.caption, color = colors.textMeta) }
        if (state.signedIn && (personal?.watchlistCount ?: 0) > 0) {
            StockNavigationRow(D.EARNINGS_DIGEST, { onAction(BriefAction.EarningsDigest) }, icon = StockIcons.Lightbulb, badge = "StockSteps+")
        }
    }
}

/** The one tinted card on the screen: the concept title, the first sentence, details behind "Show details"; the full explanation in the sheet. */
@Composable
private fun ConceptCard(concept: ConceptOfTheDay, onExplain: () -> Unit, onLearn: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(D.CONCEPT, style = typography.label, color = colors.primaryText)
        Text(concept.title, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
        StockShowDetails(concept.explanation, hasMore = true, trailing = {
            StockLinkAction(D.UNDERSTAND_CONCEPT, onExplain, icon = StockIcons.School)
        }) {
            concept.example?.let { Text(D.conceptExample(it), style = typography.small, color = colors.textBody) }
            Text(concept.whyToday, style = typography.caption, color = colors.textSupporting)
            StockLinkAction(D.OPEN_LEARN, onLearn, icon = StockIcons.ArrowForward)
        }
    }
}

@Composable
private fun PremiumSection(state: DailyBriefUiState, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val personal = state.personal ?: return
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        StockSectionHeader(D.PLUS)
        val insights = personal.premiumInsights
        if (insights == null) {
            Text(D.PLUS_PREVIEW, style = typography.small, color = colors.textBody)
            StockLinkAction(D.PLUS_PREVIEW_ACTION, { onAction(BriefAction.Upgrade) }, icon = StockIcons.ArrowForward)
        } else {
            insights.forEach { Text("• $it", style = typography.body, color = colors.textBody) }
            if (personal.companyStories.isNotEmpty()) {
                Text(D.PLUS_STORIES, style = typography.label, color = colors.textSupporting)
                personal.companyStories.forEach { s ->
                    StockNavigationRow(s.headline, { onAction(BriefAction.OpenUrl(s.sourceUrl)) }, detail = s.relatedSymbols.firstOrNull(), actionLabel = D.readArticleLabel(s))
                }
            }
            var question by rememberSaveable { mutableStateOf("") }
            StockTextField(question, { question = it.take(300) }, D.ASK_LABEL, Modifier.fillMaxWidth(), singleLine = false)
            StockButton(D.ASK, { onAction(BriefAction.Ask(question)) }, variant = StockButtonVariant.SECONDARY,
                enabled = question.trim().length >= 3 && state.ai["ask"]?.loading != true)
            state.ai["ask"]?.let { AiAnswer(it, onAction) }
            Text(D.ASK_NOTE, style = typography.caption, color = colors.textMeta)
        }
    }
}

/** Loading layout in the reader's shape (meta, two headline lines, three index rows, two story rows); read as one "Loading". */
@Composable
private fun BriefSkeleton() {
    val spacing = StockStepsTheme.spacing
    StockLoadingState(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            StockSkeleton(Modifier.fillMaxWidth(0.5f))
            StockSkeleton(Modifier.fillMaxWidth().height(spacing.xl))
            StockSkeleton(Modifier.fillMaxWidth(0.7f).height(spacing.xl))
            Spacer(Modifier.height(spacing.sm))
            repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) { StockSkeleton(Modifier.weight(1f)); StockSkeleton(Modifier.width(spacing.xxxl * 2)) } }
            Spacer(Modifier.height(spacing.sm))
            repeat(2) { Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) { StockSkeleton(Modifier.fillMaxWidth(0.4f)); StockSkeleton(Modifier.fillMaxWidth(0.9f)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesSheet(state: DailyBriefUiState, onAction: (BriefAction) -> Unit, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Daily brief notification", Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
            if (!state.signedIn) {
                Text("Sign in to get a notification when your brief is ready.")
                StockButton("Sign in", { onDismiss(); onAction(BriefAction.SignIn) })
                return@Column
            }
            val p = state.preferences
            if (p == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify me when the brief is ready", Modifier.weight(1f))
                Switch(p.notificationsEnabled, { onAction(BriefAction.SavePreferences(p.copy(notificationsEnabled = it))) }, enabled = !state.preferencesBusy)
            }
            Text("Delivery time (${p.timeZone})", style = StockStepsTheme.typography.label)
            StockPillSelector(listOf(7, 8, 9, 17, 18), p.deliveryHour, { h -> if (h < 12) "$h AM" else "${h - 12} PM" }, { onAction(BriefAction.SavePreferences(p.copy(deliveryHour = it))) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mention companies I follow")
                    Text("StockSteps+", style = StockStepsTheme.typography.caption)
                }
                Switch(p.personalizedNotifications, { if (state.plus) onAction(BriefAction.SavePreferences(p.copy(personalizedNotifications = it))) else onAction(BriefAction.Upgrade) },
                    enabled = !state.preferencesBusy && p.notificationsEnabled)
            }
            Text("Sent on market days only, once per brief. Your device's notification permission also needs to be on. You can turn this off at any time.",
                style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
            StockButton("Done", onDismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
        }
    }
}


// ---------- History ----------

/** Previous briefs as navigation rows (sample briefs badged); the StockSteps+ remainder as one row. */
@Composable
internal fun DailyBriefHistoryScreen(state: DailyBriefUiState, modifier: Modifier, onAction: (BriefAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val history = state.history
    LazyColumn(modifier.background(StockStepsTheme.colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md)) {
        if (history == null) item("loading") {
            StockLoadingState(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) { repeat(3) { StockSkeleton(Modifier.fillMaxWidth(0.8f)) } } }
        } else {
            if (history.items.isEmpty()) item("empty") { StockEmptyState(D.NO_HISTORY) }
            itemsIndexed(history.items, key = { _, it -> it.id }) { n, s ->
                if (n > 0) StockDivider()
                StockNavigationRow(D.historyTitle(s), { onAction(BriefAction.OpenBrief(s.id)) }, summary = D.historySummary(s), detail = D.historyDetail(s),
                    actionLabel = "Read this brief", badge = if (s.sampleData) D.SAMPLE else null, badgeKind = StockBadgeKind.SAMPLE)
            }
            if (history.lockedCount > 0) item("locked") {
                StockDivider()
                StockNavigationRow(D.lockedHistory(history.lockedCount), { onAction(BriefAction.Upgrade) }, actionLabel = D.SEE_PLUS, badge = "StockSteps+")
            }
        }
    }
    if (state.upgrade) AlertDialog(onDismissRequest = { onAction(BriefAction.DismissUpgrade) }, title = { Text(D.UPGRADE_TITLE) },
        text = { Text(D.HISTORY_UPGRADE) },
        confirmButton = { StockButton(D.SEE_PLUS, { onAction(BriefAction.Upgrade) }, variant = StockButtonVariant.TEXT) },
        dismissButton = { StockButton(D.NOT_NOW, { onAction(BriefAction.DismissUpgrade) }, variant = StockButtonVariant.TEXT) })
}
