package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.*

/** Research checklist actions (wired by the Scene to the shared presenter). */
internal data class ResearchActions(
    val onCreate: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onClose: () -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onStatus: (String, ResearchStatus) -> Unit = { _, _ -> },
    val onEditNote: (String, String) -> Unit = { _, _ -> },
    val onSaveNote: (String) -> Unit = {},
    val onDiscardNote: (String) -> Unit = {},
    val onSummary: () -> Unit = {},
    val onCloseSummary: () -> Unit = {},
    val onSnapshot: () -> Unit = {},
    val onDeleteSnapshot: (String) -> Unit = {},
    val onExport: () -> Unit = {},
    val onUpsell: () -> Unit = {},
    val onDismissUpsell: () -> Unit = {},
    val onUpgrade: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** Phase 5: AI help for one question (question id, whether a saved note exists; the note is only sent after the user agrees). */
    val onAskAi: (String, Boolean) -> Unit = { _, _ -> }
)

/**
 * Company Comparison Phase 4: Guided Research Checklist. Free: the basic checklist, notes, progress,
 * three saved sessions and the basic summary. StockSteps+ (server-verified): advanced questions,
 * snapshots, the detailed summary and a PDF report. Free accounts get one upgrade entry point (the
 * advanced card), never repeated banners. "What the data shows" comes from the comparison on screen.
 */
@Composable
internal fun ComparisonResearchScreen(
    state: ResearchUiState,
    comparison: ComparisonUiState?,
    selected: List<String>,
    actions: ResearchActions,
    modifier: Modifier
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var deleting by remember { mutableStateOf<ResearchSessionInfo?>(null) }
    val open = state.open
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Research checklist", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("Work through the questions at your own pace and write what you notice. Your notes are private to your account. Education, not investment advice.",
                    style = typography.small, color = colors.textSecondary)
            }
        }
        if (!state.signedIn) {
            item(key = "signin") {
                StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text("Sign in to save your research", Modifier.semantics { heading() }, style = typography.cardTitle)
                    Text("Research sessions, notes and progress are saved to your account so you can resume later. The checklist is free.", style = typography.small, color = colors.textBody)
                    StockButton("Sign In", onClick = actions.onSignIn, modifier = Modifier.fillMaxWidth())
                }
            }
            return@LazyColumn
        }
        if (state.loading || state.opening || state.busy) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading research" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, actions.onRetry) } }
        if (open == null) {
            state.sessions?.let { sessions -> item(key = "sessions") { SessionsCard(sessions, selected, actions) { deleting = it } } }
            return@LazyColumn
        }
        item(key = "session") { SessionHeader(open, actions) }
        state.summary?.let { summary -> item(key = "summary") { SummaryCard(summary, actions.onCloseSummary) } }
        if (state.summaryLoading) item(key = "summaryLoading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Building summary" }) }
        state.categories { ResearchContext.lines(it, comparison) }.forEach { category ->
            item(key = "cat-${category.category.id}") {
                Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(category.category.title + if (category.category.tier == ResearchTier.PLUS) " · StockSteps+" else "", Modifier.semantics { heading() },
                        style = typography.sectionTitle, color = colors.textPrimary)
                    Text(category.category.description, style = typography.caption, color = colors.textSecondary)
                }
            }
            category.questions.forEach { q -> item(key = "q-${q.question.id}") { QuestionCard(q, actions) } }
        }
        if (state.advancedPreview.isNotEmpty()) item(key = "advanced") {
            StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Advanced research · StockSteps+", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                state.advancedPreview.forEach { Text("• ${it.title}: ${it.description}", style = typography.small, color = colors.textBody) }
                Text("StockSteps+ also adds research snapshots, a detailed summary and a PDF report.", style = typography.caption, color = colors.textSecondary)
                StockButton("Learn about StockSteps+", onClick = actions.onUpsell, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.snapshots.isNotEmpty()) item(key = "snapshots") {
            StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Snapshots", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                Text("Saved copies of your progress and notes. Values in a snapshot are as of the date shown, not live.", style = typography.caption, color = colors.textSecondary)
                state.snapshots.forEach { snap ->
                    Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(snap.label, style = typography.bodySemiBold, color = colors.textPrimary)
                            Text("${snap.progress.reviewed} of ${snap.progress.total} reviewed · values as of ${snap.dataAsOf?.take(10) ?: "not reported"}", style = typography.caption, color = colors.textSecondary)
                        }
                        TextButton(onClick = { actions.onDeleteSnapshot(snap.id) }, Modifier.semantics { contentDescription = "Delete snapshot ${snap.label}" }) { Text("Delete") }
                    }
                }
            }
        }
    }
    deleting?.let { session ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete “${session.title}”?") },
            text = { Text("Its checklist answers, notes and snapshots will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { actions.onDelete(session.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    state.upsell?.let { upsell ->
        AlertDialog(onDismissRequest = actions.onDismissUpsell, containerColor = colors.surface,
            title = { Text(upsell.title, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(upsell.body, style = typography.body, color = colors.textBody)
                    upsell.benefits.forEach { Text("• $it", style = typography.small, color = colors.textBody) }
                    Text(upsell.footnote, style = typography.caption, color = colors.textSecondary)
                }
            },
            confirmButton = { StockButton("See StockSteps+", onClick = { actions.onDismissUpsell(); actions.onUpgrade() }, variant = StockButtonVariant.TEXT) },
            dismissButton = { StockButton("Not now", onClick = actions.onDismissUpsell, variant = StockButtonVariant.TEXT) })
    }
    state.message?.let { message -> AlertDialog(onDismissRequest = actions.onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = actions.onDismissMessage) { Text("OK") } }) }
}

@Composable
private fun SessionsCard(sessions: ResearchSessionsResponse, selected: List<String>, actions: ResearchActions, onDelete: (ResearchSessionInfo) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Your research (${sessions.sessions.size} of ${sessions.limit}${if (sessions.plus) ", fair use" else ""})", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        if (sessions.sessions.isEmpty()) Text("No saved research yet. Start one for the companies you're comparing.", style = typography.small, color = colors.textBody)
        sessions.sessions.forEach { s ->
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = typography.bodySemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.symbols.joinToString(" · ") + " · ${s.reviewed} reviewed" + if (s.snapshotCount > 0) " · ${s.snapshotCount} snapshots" else "",
                        style = typography.caption, color = colors.textSecondary)
                }
                TextButton(onClick = { actions.onOpen(s.id) }, Modifier.semantics { contentDescription = "Open ${s.title}" }) { Text("Open") }
                TextButton(onClick = { onDelete(s) }, Modifier.semantics { contentDescription = "Delete ${s.title}" }) { Text("Delete") }
            }
        }
        if (selected.size >= 2) StockButton("Start research: ${selected.joinToString(" vs ")}", onClick = actions.onCreate, enabled = sessions.canCreate, modifier = Modifier.fillMaxWidth())
        else Text("Choose at least two companies on Compare to start new research.", style = typography.caption, color = colors.textSecondary)
        sessions.message?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
        if (!sessions.canCreate && !sessions.plus) StockButton("Learn about StockSteps+", onClick = actions.onUpsell, variant = StockButtonVariant.TEXT)
    }
}

@Composable
private fun SessionHeader(open: ResearchSessionResponse, actions: ResearchActions) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val progress = open.progress
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        TextButton(onClick = actions.onClose, Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("‹ All research") }
        Text(open.session.title, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Text(open.session.symbols.joinToString(" · "), style = typography.caption, color = colors.textSecondary)
        LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = "${progress.reviewed} of ${progress.total} questions reviewed" })
        Text("${progress.reviewed} of ${progress.total} reviewed · ${progress.needsMore} need more research · ${progress.notReviewed} not reviewed",
            style = typography.small, color = colors.textBody)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockButton("Summary", onClick = actions.onSummary, variant = StockButtonVariant.OUTLINED)
            if (open.plus) {
                StockButton("Snapshot", onClick = actions.onSnapshot, variant = StockButtonVariant.OUTLINED)
                StockButton("Export PDF", onClick = actions.onExport, variant = StockButtonVariant.OUTLINED)
            }
        }
    }
}

@Composable
private fun QuestionCard(q: ResearchQuestionView, actions: ResearchActions) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(q.question.text, Modifier.semantics { heading() }, style = typography.bodySemiBold, color = colors.textPrimary)
        Text(q.question.help, style = typography.caption, color = colors.textSecondary)
        if (q.context.isNotEmpty()) {
            Text("What the data shows", style = typography.tiny, color = colors.textTertiary)
            q.context.forEach { Text("• $it", style = typography.small, color = colors.textBody) }
        }
        if (q.locked) {
            Text("Your answer: ${q.status.label}" + if (q.note.isNotBlank()) " — ${q.note}" else "", style = typography.small, color = colors.textBody)
            Text("Saved during StockSteps+. Kept and readable; editing advanced questions needs StockSteps+.", style = typography.caption, color = colors.textSecondary)
            return@StockCard
        }
        StockSegmentedControl(ResearchStatus.entries.map { StockSegment(it, it.label) }, q.status, { actions.onStatus(q.question.id, it) },
            Modifier.fillMaxWidth().semantics { contentDescription = "Status: ${q.status.label}" })
        StockTextField(q.draft, { actions.onEditNote(q.question.id, it) }, "Your note", Modifier.fillMaxWidth(), placeholder = "What do you notice? What would you check next?",
            supporting = "${q.remaining} characters left · private to your account", singleLine = false, minLines = 2)
        if (q.dirty) Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StockButton(if (q.saving) "Saving…" else "Save note", onClick = { actions.onSaveNote(q.question.id) }, enabled = !q.saving)
            TextButton(onClick = { actions.onDiscardNote(q.question.id) }, Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("Discard") }
        }
        if (q.saving && !q.dirty) Text("Saving…", style = typography.caption, color = colors.textSecondary)
        StockButton("Ask StockSteps AI about this", onClick = { actions.onAskAi(q.question.id, q.note.isNotBlank()) }, variant = StockButtonVariant.TEXT,
            modifier = Modifier.semantics { contentDescription = "Ask StockSteps AI about: ${q.question.text}. StockSteps+." })
    }
}

@Composable
private fun SummaryCard(summary: ResearchSummary, onClose: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(if (summary.detailed) "Detailed research summary" else "Research summary", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Text("Financial data as of ${summary.dataAsOf?.take(10) ?: "not reported"}. Labels show what each line is: reported data, a calculation, your note, education or a limitation.",
            style = typography.caption, color = colors.textSecondary)
        if (summary.sampleData) Text("Sample data for development, not real financial data.", style = typography.caption, color = colors.cautionText)
        summary.sections.forEach { section ->
            Text(section.title, Modifier.padding(top = spacing.xs).semantics { heading() }, style = typography.label, color = colors.textPrimary)
            section.items.forEach { item ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Text(item.kind.label + (item.attribution?.let { " · $it" } ?: ""), style = typography.tiny, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.text, style = typography.small, color = colors.textBody)
                }
            }
        }
        StockButton("Close summary", onClick = onClose, variant = StockButtonVariant.TEXT)
    }
}

/** Compare-screen entry point to the checklist (one card; no repeated banners). */
@Composable
internal fun ResearchEntryCard(onResearch: () -> Unit) {
    StockCard(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Research checklist", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary)
        Text("Work through guided questions about these companies, write private notes and track your progress. Free; StockSteps+ adds advanced research and reports.",
            style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
        StockButton("Open research checklist", onClick = onResearch, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
    }
}
