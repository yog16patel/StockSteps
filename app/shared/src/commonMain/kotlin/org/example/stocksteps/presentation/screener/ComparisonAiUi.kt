package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.screener.*

/** AI assistant actions (wired by the Scene to the shared presenter and the Compare screen). */
internal data class ComparisonAiActions(
    val onSummary: (ComparisonAiType) -> Unit = {},
    val onSuggestion: (SuggestedAiQuestion) -> Unit = {},
    val onInput: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onNewConversation: () -> Unit = {},
    /** Opens the metric or history the evidence comes from on the Compare screen (selection kept). */
    val onEvidence: (FinancialEvidence) -> Unit = {},
    val onResearchQuestion: (String) -> Unit = {},
    val onConfirmResearch: (Boolean) -> Unit = {},
    val onCancelResearch: () -> Unit = {},
    /** Null when no open research session matches the turn's question (copying needs one). */
    val onCopyToNotes: ((AiTurn) -> Unit)? = null,
    val onUpsell: () -> Unit = {},
    val onDismissUpsell: () -> Unit = {},
    val onUpgrade: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onDismissReset: () -> Unit = {},
    val onBack: () -> Unit = {}
)

/** Compare-screen entry point (one card; free accounts see what it is and that it's StockSteps+). */
@Composable
internal fun ComparisonAiEntryCard(onOpen: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("Ask StockSteps AI", Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary)
            StockTag("StockSteps+")
        }
        Text("Understand the financial differences between these companies. Answers use only the verified figures on this screen, with every number linked to its data.",
            style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
        StockButton("Ask StockSteps AI", onClick = onOpen, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Company Comparison Phase 5: the AI Comparison Assistant. Focused on the selected companies (not a
 * general chatbot): suggested questions, a bounded conversation, cited evidence that opens the source
 * metric, data freshness and limitations, and the remaining allowance. StockSteps+ is decided by the server.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComparisonAiScreen(
    state: ComparisonAiUiState,
    suggestions: List<SuggestedAiQuestion>,
    names: Map<String, String>,
    actions: ComparisonAiActions,
    modifier: Modifier
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val list = rememberLazyListState()
    // New answers scroll into view.
    LaunchedEffect(state.turns.size) { if (state.turns.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    LazyColumn(modifier.background(colors.appBackground), state = list,
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Ask StockSteps AI", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("Understand the financial differences between these companies.", style = typography.small, color = colors.textSecondary)
                Text(state.symbols.joinToString(" · ") { s -> names[s]?.takeIf { it != s }?.let { "$it ($s)" } ?: s }, style = typography.label, color = colors.textBody,
                    modifier = Modifier.semantics { contentDescription = "Comparing ${state.symbols.joinToString(", ")}" })
                TextButton(onClick = actions.onBack, Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("‹ Back to the comparison") }
            }
        }
        state.resetNote?.let { note -> item(key = "reset") {
            StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(note, style = typography.small, color = colors.textBody)
                StockButton("OK", onClick = actions.onDismissReset, variant = StockButtonVariant.TEXT)
            }
        } }
        if (!state.signedIn || (state.usage != null && !state.plus)) {
            item(key = "gate") { GateCard(AiUpsell(signIn = !state.signedIn), actions) }
            return@LazyColumn
        }
        if (state.symbols.size < 2) { item(key = "pick") { StockEmptyState("Choose at least two companies on Compare first.") }; return@LazyColumn }
        item(key = "usage") { UsageLine(state) }
        if (state.turns.isEmpty()) item(key = "start") {
            StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Start with an overview", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                Text("A short, beginner-friendly explanation of the biggest financial differences, with the data behind each point.", style = typography.small, color = colors.textBody)
                StockButton("Explain the biggest differences", onClick = { actions.onSummary(ComparisonAiType.OVERVIEW) }, enabled = state.canAsk, modifier = Modifier.fillMaxWidth())
            }
        }
        items(state.turns, key = { it.id }) { turn -> TurnCard(turn, actions) }
        item(key = "suggestions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(if (state.turns.isEmpty()) "Or ask about" else "Ask a follow-up", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    suggestions.filterNot { state.turns.isEmpty() && it.type == ComparisonAiType.OVERVIEW }.forEach { s ->
                        StockChip(s.text, selected = false, onClick = { actions.onSuggestion(s) }, modifier = Modifier.semantics { role = Role.Button; contentDescription = "Ask: ${s.text}" })
                    }
                }
            }
        }
        item(key = "input") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockTextField(state.input, actions.onInput, "Your question", Modifier.fillMaxWidth(), placeholder = "For example: how do their margins compare?",
                    supporting = "${state.remaining} characters left · about these companies only", singleLine = false, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    StockButton(if (state.busy) "Thinking…" else "Ask", onClick = actions.onSend, enabled = state.canAsk && state.inputValid)
                    if (state.turns.isNotEmpty()) StockButton("New conversation", onClick = actions.onNewConversation, variant = StockButtonVariant.TEXT, enabled = !state.busy)
                }
            }
        }
        item(key = "footer") {
            Text("Education, not investment advice. StockSteps AI explains the data; it doesn't recommend stocks or predict prices, and it can make mistakes, so check the linked figures.",
                style = typography.caption, color = colors.textTertiary)
        }
    }
    state.pendingResearch?.let { pending ->
        AlertDialog(onDismissRequest = actions.onCancelResearch, containerColor = colors.surface,
            title = { Text("Include your note?", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text("“${pending.questionText}”", style = typography.bodySemiBold, color = colors.textPrimary)
                    Text("If you include it, your saved note for this question (only this one) is sent to StockSteps' AI service to tailor the answer. It isn't shared with other users, isn't changed, and isn't saved with the answer.",
                        style = typography.small, color = colors.textBody)
                }
            },
            confirmButton = { StockButton("Include my note", onClick = { actions.onConfirmResearch(true) }, variant = StockButtonVariant.TEXT) },
            dismissButton = { StockButton("Ask without it", onClick = { actions.onConfirmResearch(false) }, variant = StockButtonVariant.TEXT) })
    }
    state.upsell?.let { upsell ->
        AlertDialog(onDismissRequest = actions.onDismissUpsell, containerColor = colors.surface,
            title = { Text(upsell.title, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) },
            text = { UpsellBody(upsell) },
            confirmButton = { StockButton(if (upsell.signIn) "Sign In" else "See StockSteps+", onClick = { actions.onDismissUpsell(); if (upsell.signIn) actions.onSignIn() else actions.onUpgrade() }, variant = StockButtonVariant.TEXT) },
            dismissButton = { StockButton("Not now", onClick = actions.onDismissUpsell, variant = StockButtonVariant.TEXT) })
    }
    state.message?.let { message -> AlertDialog(onDismissRequest = actions.onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = actions.onDismissMessage) { Text("OK") } }) }
}

@Composable
private fun UpsellBody(upsell: AiUpsell) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(upsell.body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
        upsell.benefits.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody) }
        Text(upsell.footnote, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
    }
}

@Composable
private fun GateCard(upsell: AiUpsell, actions: ComparisonAiActions) {
    StockCard(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(upsell.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary)
        UpsellBody(upsell)
        StockButton(if (upsell.signIn) "Sign In" else "See StockSteps+", onClick = if (upsell.signIn) actions.onSignIn else actions.onUpgrade, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UsageLine(state: ComparisonAiUiState) {
    val usage = state.usage ?: return
    val colors = StockStepsTheme.colors
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(usage.label, style = StockStepsTheme.typography.caption, color = if (usage.exhausted) colors.cautionText else colors.textSecondary)
        if (usage.exhausted) Text("Your allowance is used up for now" + (usage.dailyResetAt?.let { " (daily requests reset ${it.take(10)} 00:00 UTC)" } ?: "") + ". The comparison and guided explanations stay available.",
            style = StockStepsTheme.typography.caption, color = colors.cautionText)
        usage.note?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
        if (usage.sample) Text("Sample AI for development: deterministic templates, not an AI service.", style = StockStepsTheme.typography.caption, color = colors.cautionText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurnCard(turn: AiTurn, actions: ComparisonAiActions) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        // The question, right-aligned like a message.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(turn.title, Modifier.widthIn(max = 320.dp).background(colors.primaryContainer, StockStepsTheme.shapes.chip).padding(horizontal = spacing.sm, vertical = spacing.xs)
                .semantics { contentDescription = "You asked: ${turn.title}" }, style = typography.small, color = colors.textPrimary)
        }
        val error = turn.error
        val response = turn.response
        when {
            turn.loading -> StockCard { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "StockSteps AI is preparing an answer" }) }
            error != null -> StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(error, style = typography.small, color = colors.textBody)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (turn.retryable) StockButton("Try again", onClick = { actions.onRetry(turn.id) }, variant = StockButtonVariant.OUTLINED)
                    if (turn.errorCode == "PLUS_REQUIRED") StockButton("Learn about StockSteps+", onClick = actions.onUpsell, variant = StockButtonVariant.TEXT)
                    StockButton("Dismiss", onClick = { actions.onRemove(turn.id) }, variant = StockButtonVariant.TEXT)
                }
            }
            response != null -> AnswerCard(turn, response, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerCard(turn: AiTurn, r: ComparisonAiResponse, actions: ComparisonAiActions) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(when (r.scope) {
                ComparisonAiScope.DECLINED -> "StockSteps"
                ComparisonAiScope.FALLBACK -> "StockSteps (AI answer unavailable)"
                else -> "StockSteps AI"
            }, Modifier.weight(1f).semantics { heading() }, style = typography.label, color = colors.textSecondary)
            if (r.sample) StockStatusBadge("Sample", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT)
            if (r.cached) StockTag("Saved answer")
        }
        Text(r.summary, style = typography.body, color = colors.textBody)
        r.observations.forEach { o ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(o.category, style = typography.tiny, color = colors.textTertiary)
                Text(o.text, style = typography.bodySemiBold, color = colors.textPrimary)
                o.whyItMatters?.let { Text("Why it matters: $it", style = typography.small, color = colors.textBody) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xxs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    o.evidenceIds.mapNotNull(r::evidence).forEach { e -> EvidenceChip(e) { actions.onEvidence(e) } }
                }
            }
        }
        if (r.caveats.isNotEmpty()) {
            Text("Important caveats", Modifier.padding(top = spacing.xxs).semantics { heading() }, style = typography.label, color = colors.textPrimary)
            r.caveats.forEach { Text("• $it", style = typography.small, color = colors.textBody) }
        }
        if (r.missingData.isNotEmpty()) {
            Text("Not in the data", Modifier.semantics { heading() }, style = typography.label, color = colors.textPrimary)
            r.missingData.take(4).forEach { Text("• $it", style = typography.caption, color = colors.textSecondary) }
        }
        if (r.researchQuestions.isNotEmpty()) {
            Text("Questions to research", Modifier.semantics { heading() }, style = typography.label, color = colors.textPrimary)
            r.researchQuestions.forEach { q ->
                Text("• $q", Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClickLabel = "Ask this") {
                    actions.onSuggestion(SuggestedAiQuestion(q, ComparisonAiType.QUESTION)) }.padding(vertical = spacing.xxs), style = typography.small, color = colors.primaryText)
            }
        }
        r.note?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
        Text(listOfNotNull(r.dataAsOf?.let { "Data as of ${it.take(10)}" }, "answered ${r.generatedAt.take(10)} ${r.generatedAt.drop(11).take(5)} UTC",
            r.usedNote.takeIf { it }?.let { "used your note" }).joinToString(" · "), style = typography.caption, color = colors.textTertiary)
        r.warnings.firstOrNull()?.let { Text(it, style = typography.caption, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        if (turn.request.researchQuestionId != null) actions.onCopyToNotes?.let { copy ->
            StockButton("Copy to my note draft", onClick = { copy(turn) }, variant = StockButtonVariant.TEXT)
        }
        Text(r.disclaimer, style = typography.tiny, color = colors.textTertiary)
    }
}

@Composable
private fun EvidenceChip(e: FinancialEvidence, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    Box(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClickLabel = "View data", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Source. ${e.accessibility}. Opens the data on Compare." }, contentAlignment = Alignment.CenterStart) {
        Text("${e.chip}: ${e.value}", Modifier.background(colors.surfaceSecondary, StockStepsTheme.shapes.pill).padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
            style = StockStepsTheme.typography.tiny, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
