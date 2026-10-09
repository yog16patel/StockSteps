package org.example.stocksteps.presentation.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.brief.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.BeginnerEducation
import org.example.stocksteps.learning.EducationEntry
import org.example.stocksteps.presentation.learn.BeginnerExplanationSheet

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

// ---------- Compact preview (Home, Markets) ----------

/** Compact card: title, freshness (never "today" for an older brief), one line, reading time, action. */
@Composable
internal fun DailyBriefPreviewCard(state: DailyBriefUiState?, nowMillis: Long, onOpen: () -> Unit, modifier: Modifier = Modifier, onHistory: (() -> Unit)? = null) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val brief = state?.latest
    StockCard(modifier.fillMaxWidth(), onClick = if (brief != null) onOpen else null, onClickLabel = "Read the Daily Market Brief",
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Icon(StockIcons.News, contentDescription = null, tint = colors.primary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
            Text("Your Daily Market Brief", Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        }
        when {
            brief != null -> {
                val stale = BriefFormat.isStale(brief, nowMillis)
                Text(BriefFormat.freshness(brief, nowMillis) + if (state.offline) " · Offline copy" else "", style = typography.caption,
                    color = if (stale || state.offline) colors.cautionText else colors.textSecondary)
                Text(brief.summaryLine, style = typography.body, color = colors.textBody, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${brief.readingMinutes} min read" + if (brief.sampleData) " · Sample data" else "", Modifier.weight(1f), style = typography.caption, color = colors.textSecondary)
                    onHistory?.let { TextButton(onClick = it) { Text("Previous briefs") } }
                    TextButton(onClick = onOpen) { Text(if (stale) "Read Latest Brief" else "Read Today's Brief") }
                }
            }
            state?.loading != false -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading the Daily Market Brief" })
            else -> Text(state.error ?: "The brief isn't available right now.", style = typography.small, color = colors.textSecondary)
        }
    }
}

// ---------- Reader ----------

@Composable
internal fun DailyBriefScreen(state: DailyBriefUiState, nowMillis: Long, modifier: Modifier, onAction: (BriefAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EducationEntry?>(null) }
    var preferences by remember { mutableStateOf(false) }
    var scenarios by remember { mutableStateOf(false) }
    val brief = state.current
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your Daily Market Brief", Modifier.weight(1f).semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                    TextButton(onClick = { preferences = true; onAction(BriefAction.LoadPreferences) }) { Text("Notify me") }
                }
                Text("Understand the market in a few minutes.", style = typography.body, color = colors.textBody)
                brief?.let { b ->
                    Text("${b.edition.label} · ${BriefFormat.date(b.briefDate)} · ${b.readingMinutes} min read", style = typography.caption, color = colors.textSecondary)
                    Text("Market data for the ${BriefFormat.date(b.sessionDate)} session · Updated ${BriefFormat.published(b.updatedAt) ?: b.updatedAt}",
                        style = typography.caption, color = colors.textSecondary)
                    b.sessions.forEach { s -> Text("${s.name}: ${s.label}", style = typography.caption, color = colors.textSecondary) }
                    if (BriefFormat.isStale(b, nowMillis)) Banner("This is the latest brief available. It isn't from today.")
                    if (state.offline) Banner("You're offline. This is a copy saved on this device.")
                    if (b.sampleData) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sample data for development", Modifier.weight(1f), style = typography.caption, color = colors.cautionText)
                        Box {
                            TextButton(onClick = { scenarios = true }) { Text("Scenarios") }
                            DropdownMenu(scenarios, { scenarios = false }) {
                                DropdownMenuItem({ Text("Current (no scenario)") }, { scenarios = false; onAction(BriefAction.Scenario(null)) })
                                DailyBriefPresenter.SCENARIOS.forEach { n -> DropdownMenuItem({ Text(n) }, { scenarios = false; onAction(BriefAction.Scenario(n)) }) }
                            }
                        }
                    }
                }
            }
        }
        if (brief == null) {
            item("state") {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading the Daily Market Brief" })
                else StockErrorState(state.error ?: "The brief isn't available right now.", { onAction(BriefAction.Retry) })
            }
            return@LazyColumn
        }
        item("summary") { Text(brief.summaryLine, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.bodyMedium, color = colors.textPrimary) }

        // Section 2 — Market at a glance
        item("glance") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle("Market at a Glance")
                if (brief.marketSnapshot.isEmpty()) Text("Index values aren't available right now.", style = typography.small, color = colors.textSecondary)
                brief.marketSnapshot.forEach { IndexRow(it) }
                Text(brief.indexExplainer, style = typography.small, color = colors.textSecondary)
            }
        }

        // Section 3 — Stories
        item("stories-title") { SectionTitle("Market Stories") }
        if (brief.stories.isEmpty()) item("no-stories") { Text("No market stories met our source checks for this brief.", style = typography.small, color = colors.textSecondary) }
        items(brief.stories, key = { "story-${it.id}" }) { story -> StoryCard(story, state.ai[story.id], state.plus, onAction) }

        // Section 4 — Watchlist
        item("watchlist") { WatchlistSection(state, onAction) }

        // Section 5 — Earnings
        state.personal?.earnings?.takeIf { it.isNotEmpty() }?.let { rows ->
            item("earnings") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SectionTitle("Upcoming Earnings")
                    rows.forEach { e ->
                        StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
                            onClick = { onAction(e.eventId?.let { BriefAction.OpenEarnings(it) } ?: BriefAction.OpenStock(e.symbol)) },
                            onClickLabel = if (e.eventId != null) "Open ${e.symbol} earnings event" else "Open ${e.symbol}") {
                            Text("${e.name ?: e.symbol} (${e.symbol})", style = typography.bodySemiBold, color = colors.textPrimary)
                            Text(listOfNotNull("Expected ${BriefFormat.date(e.date)}", "${e.dateStatus} date", e.timing,
                                e.epsEstimate?.let { "EPS estimate ${e.currency ?: ""} ${BriefFormat.groupDigits(it)}".replace("  ", " ") },
                                when (e.reason) { "portfolio" -> "In your portfolio"; "watchlist" -> "On your watchlist"; else -> "General example" }).joinToString(" · "),
                                style = typography.caption, color = colors.textSecondary)
                        }
                    }
                    Text("Report dates can change until a company confirms them.", style = typography.caption, color = colors.textSecondary)
                }
            }
        }

        // Section 6 — Concept of the Day
        item("concept") {
            StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Concept of the Day", style = typography.label, color = colors.primaryText)
                Text(brief.concept.title, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                Text(brief.concept.explanation, style = typography.body, color = colors.textBody)
                brief.concept.example?.let { Text("Example: $it", style = typography.small, color = colors.textBody) }
                Text(brief.concept.whyToday, style = typography.caption, color = colors.textSecondary)
                Row {
                    TextButton(onClick = { education = BeginnerEducation.entry(brief.concept.learnTermId) }) { Text("Understand more") }
                    TextButton(onClick = { onAction(BriefAction.Learn) }) { Text("Open Learn") }
                }
            }
        }

        // Section 7 — Premium
        item("premium") { PremiumSection(state, onAction) }

        item("footer") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                brief.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                Text(brief.disclaimer, style = typography.caption, color = colors.textSecondary)
                TextButton(onClick = { onAction(BriefAction.History) }) { Text("Previous briefs") }
            }
        }
    }
    education?.let { BeginnerExplanationSheet(it) { education = null } }
    if (preferences) PreferencesSheet(state, onAction) { preferences = false }
    if (state.upgrade) AlertDialog(onDismissRequest = { onAction(BriefAction.DismissUpgrade) }, title = { Text("StockSteps+") },
        text = { Text("AI explanations, the full personalized brief and the complete brief history are part of StockSteps+. The market summary, stories and Concept of the Day stay free.") },
        confirmButton = { TextButton(onClick = { onAction(BriefAction.Upgrade) }) { Text("See StockSteps+") } },
        dismissButton = { TextButton(onClick = { onAction(BriefAction.DismissUpgrade) }) { Text("Not now") } })
    state.message?.let { AlertDialog(onDismissRequest = { onAction(BriefAction.DismissMessage) }, text = { Text(it) }, confirmButton = { TextButton(onClick = { onAction(BriefAction.DismissMessage) }) { Text("OK") } }) }
}

@Composable
private fun SectionTitle(text: String) = Text(text, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)

@Composable
private fun Banner(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(StockStepsTheme.colors.warningContainer).padding(StockStepsTheme.spacing.sm),
        style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textPrimary)
}

@Composable
private fun IndexRow(i: BriefIndex) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = BriefFormat.accessibility(i) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.displayName, style = typography.bodySemiBold, color = colors.textPrimary)
                Text(i.stateLabel, style = typography.caption, color = if (i.state == QuoteState.STALE || i.state == QuoteState.UNAVAILABLE) colors.cautionText else colors.textSecondary)
                i.proxyNote?.let { Text(it, style = typography.caption, color = colors.textTertiary) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(BriefFormat.value(i), style = typography.bodySemiBold, color = colors.textPrimary)
                if (i.value != null) Text("${BriefFormat.direction(i)} ${BriefFormat.change(i)}", style = typography.caption,
                    color = when (BriefFormat.sign(i)) { 1 -> colors.positiveText; -1 -> colors.negativeText; else -> colors.textSecondary })
            }
        }
    }
}

@Composable
private fun StoryCard(story: BriefStory, ai: BriefAiState?, plus: Boolean, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(story.topic.uppercase(), style = typography.tiny, color = colors.primaryText)
        Text(story.headline, Modifier.semantics { heading() }, style = typography.bodySemiBold, color = colors.textPrimary)
        story.summary?.let { Text(it, style = typography.body, color = colors.textBody) }
        Text(listOfNotNull(story.publisher ?: "Publisher not provided", BriefFormat.published(story.publishedAt)).joinToString(" · "), style = typography.caption, color = colors.textSecondary)
        if (story.relatedSymbols.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            story.relatedSymbols.forEach { s -> AssistChip(onClick = { onAction(BriefAction.OpenStock(s)) }, label = { Text(s) }) }
        }
        Text(story.evidenceNote, style = typography.caption, color = colors.textTertiary)
        Row {
            TextButton(onClick = { onAction(BriefAction.OpenUrl(story.sourceUrl)) }, modifier = Modifier.semantics { contentDescription = "Read the original article from ${story.publisher ?: "the publisher"}" }) { Text("Read article") }
            TextButton(onClick = { onAction(BriefAction.Explain(story.id)) }, enabled = ai?.loading != true) { Text(if (plus) "Understand More" else "Understand More · StockSteps+") }
        }
        ai?.let { AiAnswer(it, onAction) }
    }
}

@Composable
private fun AiAnswer(ai: BriefAiState, onAction: (BriefAction) -> Unit) {
    val typography = StockStepsTheme.typography
    when {
        ai.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Preparing an explanation" })
        ai.error != null -> Text(ai.error!!, style = typography.small, color = StockStepsTheme.colors.negativeText)
        ai.answer != null -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            val a = ai.answer!!
            Text(if (a.usesAi) "AI explanation (StockSteps+)" else "Sample explanation (mock)", style = typography.label, color = StockStepsTheme.colors.primaryText)
            Text(a.answer, style = typography.body)
            a.points.forEach { Text("• $it", style = typography.small) }
            if (a.insufficientEvidence) Text("The sources don't contain enough to answer that fully.", style = typography.caption, color = StockStepsTheme.colors.cautionText)
            a.sources.forEach { s -> TextButton(onClick = { onAction(BriefAction.OpenUrl(s.url)) }) { Text("Source: ${s.publisher ?: s.title ?: "article"}") } }
            a.remainingToday?.let { Text("$it explanations left today", style = typography.caption, color = StockStepsTheme.colors.textSecondary) }
        }
    }
}

@Composable
private fun WatchlistSection(state: DailyBriefUiState, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        SectionTitle("From Your Watchlist")
        val personal = state.personal
        when {
            !state.signedIn -> StockEmptyState("Sign in to see news, price moves and earnings for companies you follow.", actionText = "Sign in", onAction = { onAction(BriefAction.SignIn) })
            personal == null && state.personalLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            personal == null -> Text(state.personalError ?: "Your watchlist updates aren't available right now.", style = typography.small, color = colors.textSecondary)
            personal.watchlistCount == 0 -> StockEmptyState("Add companies to a watchlist to see their updates in your brief.", actionText = "Go to Watchlist", onAction = { onAction(BriefAction.Watchlist) })
            personal.watchlistHighlights.isEmpty() -> Text("Nothing notable from the ${personal.watchlistCount} companies you follow in this session.", style = typography.small, color = colors.textSecondary)
            else -> {
                personal.watchlistHighlights.forEach { h ->
                    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, onClick = {
                        h.url?.let { onAction(BriefAction.OpenUrl(it)) } ?: h.reportId?.let { onAction(BriefAction.OpenResults(it)) } ?: onAction(BriefAction.OpenStock(h.symbol))
                    }, onClickLabel = if (h.url != null) "Read the article" else if (h.reportId != null) "View ${h.symbol} earnings results" else "Open ${h.symbol}") {
                        Text("${h.symbol}${h.name?.let { " · $it" } ?: ""}", style = typography.label, color = colors.textSecondary)
                        Text(h.text, style = typography.body, color = colors.textPrimary)
                        h.publisher?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                    }
                }
                if (personal.moreHighlights > 0) Text("${personal.moreHighlights} more updates are included with StockSteps+.", style = typography.small, color = colors.primaryText,
                    modifier = Modifier.clickable { onAction(BriefAction.Upgrade) }.heightIn(min = StockStepsTheme.dimensions.touchTarget))
            }
        }
        personal?.notes?.firstOrNull { it.startsWith("Watching") }?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
        if (state.signedIn && (personal?.watchlistCount ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            StockButton("Your Earnings Digest", onClick = { onAction(BriefAction.EarningsDigest) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb, modifier = Modifier.weight(1f, fill = false))
            org.example.stocksteps.presentation.earnings.PlusBadge()
        }
    }
}

@Composable
private fun PremiumSection(state: DailyBriefUiState, onAction: (BriefAction) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val personal = state.personal ?: return
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("StockSteps+ insights", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        val insights = personal.premiumInsights
        if (insights == null) {
            Text("See how today's market relates to the companies you follow, more company stories, and ask follow-up questions with source-backed AI explanations.",
                style = typography.small, color = colors.textBody)
            TextButton(onClick = { onAction(BriefAction.Upgrade) }) { Text("Preview StockSteps+") }
        } else {
            insights.forEach { Text("• $it", style = typography.body, color = colors.textBody) }
            if (personal.companyStories.isNotEmpty()) {
                Text("More from companies you follow", style = typography.label, color = colors.textSecondary)
                personal.companyStories.forEach { s ->
                    TextButton(onClick = { onAction(BriefAction.OpenUrl(s.sourceUrl)) }) { Text("${s.relatedSymbols.firstOrNull()?.let { "$it: " } ?: ""}${s.headline}", maxLines = 2) }
                }
            }
            var question by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(question, { question = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Ask about this brief") })
            StockButton("Ask", { onAction(BriefAction.Ask(question)) }, enabled = question.trim().length >= 3 && state.ai["ask"]?.loading != true)
            state.ai["ask"]?.let { AiAnswer(it, onAction) }
            Text("Answers use only this brief's sources. No predictions or buy/sell advice.", style = typography.caption, color = colors.textSecondary)
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

@Composable
internal fun DailyBriefHistoryScreen(state: DailyBriefUiState, modifier: Modifier, onAction: (BriefAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val history = state.history
    LazyColumn(modifier.background(StockStepsTheme.colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item("title") { Text("Previous briefs", Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle) }
        if (history == null) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else {
            if (history.items.isEmpty()) item("empty") { StockEmptyState("No briefs yet.") }
            items(history.items, key = { it.id }) { s ->
                StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, onClick = { onAction(BriefAction.OpenBrief(s.id)) }, onClickLabel = "Read this brief") {
                    Text("${s.edition.label} · ${BriefFormat.date(s.briefDate)}", style = StockStepsTheme.typography.bodySemiBold)
                    Text(s.summaryLine, style = StockStepsTheme.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${s.readingMinutes} min read", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
                }
            }
            if (history.lockedCount > 0) item("locked") {
                StockCard(Modifier.fillMaxWidth(), onClick = { onAction(BriefAction.Upgrade) }, onClickLabel = "See StockSteps+") {
                    Text("${history.lockedCount} older briefs are included with StockSteps+.", style = StockStepsTheme.typography.small)
                }
            }
        }
    }
    if (state.upgrade) AlertDialog(onDismissRequest = { onAction(BriefAction.DismissUpgrade) }, title = { Text("StockSteps+") },
        text = { Text("The full brief history is part of StockSteps+. The latest three briefs stay free.") },
        confirmButton = { TextButton(onClick = { onAction(BriefAction.Upgrade) }) { Text("See StockSteps+") } },
        dismissButton = { TextButton(onClick = { onAction(BriefAction.DismissUpgrade) }) { Text("Not now") } })
}
