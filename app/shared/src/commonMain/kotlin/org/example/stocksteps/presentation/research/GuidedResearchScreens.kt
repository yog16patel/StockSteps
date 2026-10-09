package org.example.stocksteps.presentation.research

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.*
import org.example.stocksteps.presentation.learn.BeginnerExplanationSheet
import org.example.stocksteps.presentation.learn.MetricInfoIcon

/** Navigation and actions from the research guide; the Screen never resolves dependencies. */
internal sealed interface GuidedResearchAction {
    data object StartOrContinue : GuidedResearchAction
    data class Open(val screen: Int) : GuidedResearchAction
    data object Next : GuidedResearchAction
    data object Back : GuidedResearchAction
    data class Answer(val optionId: String) : GuidedResearchAction
    data object RetryQuiz : GuidedResearchAction
    data object Restart : GuidedResearchAction
    data object Retry : GuidedResearchAction
    data class Ask(val question: String) : GuidedResearchAction
    /** Free or signed-out users tapping "Ask StockSteps AI": the upgrade or sign-in message. */
    data object RequestAi : GuidedResearchAction
    data object Upgrade : GuidedResearchAction
    data object DismissUpgrade : GuidedResearchAction
    data object DismissMessage : GuidedResearchAction
    data object ToggleWatchlist : GuidedResearchAction
    data object CompanyDetails : GuidedResearchAction
    data object ResearchAnother : GuidedResearchAction
    data object ContinueLearning : GuidedResearchAction
}

@Composable
internal fun GuidedResearchScreen(
    state: GuidedResearchState,
    watched: Boolean,
    watchlistEnabled: Boolean,
    modifier: Modifier,
    onAction: (GuidedResearchAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    var education by remember { mutableStateOf<EducationEntry?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(state.screen) { scroll.scrollTo(0) }
    Column(modifier.background(StockStepsTheme.colors.appBackground).verticalScroll(scroll).padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        val snapshot = state.snapshot
        when {
            state.loading && snapshot == null -> {
                Text(state.name, style = StockStepsTheme.typography.screenTitle, color = StockStepsTheme.colors.textPrimary)
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading company information" })
            }
            snapshot == null -> StockErrorState(state.error ?: "This company's information couldn't be loaded.", { onAction(GuidedResearchAction.Retry) })
            state.screen == GuidedResearchState.OVERVIEW -> Overview(state, snapshot, onAction)
            state.screen == GuidedResearchState.SUMMARY -> Summary(state, snapshot, watched, watchlistEnabled, onAction)
            else -> state.step?.let { StepContent(state, it, onAction) { entry -> education = entry } }
        }
    }
    education?.let { BeginnerExplanationSheet(it) { education = null } }
    if (state.upgradeRequired) AlertDialog(onDismissRequest = { onAction(GuidedResearchAction.DismissUpgrade) }, title = { Text("StockSteps+") },
        text = { Text("Ask StockSteps AI is part of StockSteps+. Every lesson, step and quiz in this guide stays free.") },
        confirmButton = { TextButton(onClick = { onAction(GuidedResearchAction.Upgrade) }) { Text("See StockSteps+") } },
        dismissButton = { TextButton(onClick = { onAction(GuidedResearchAction.DismissUpgrade) }) { Text("Not now") } })
    state.message?.let { message -> AlertDialog(onDismissRequest = { onAction(GuidedResearchAction.DismissMessage) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onAction(GuidedResearchAction.DismissMessage) }) { Text("OK") } }) }
}

// ---------- Overview ----------

@Composable
private fun Overview(state: GuidedResearchState, snapshot: ResearchSnapshot, onAction: (GuidedResearchAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    CompanyHeader(snapshot)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Understand This Stock", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
        Text("Learn how to research this company in five simple steps. Each step explains one question using the company's reported numbers.",
            style = typography.body, color = colors.textBody)
    }
    ProgressBlock(state.completedCount)
    snapshot.notes.dropLast(1).forEach { Note(it) }
    if (snapshot.kind == CompanyKind.FUND) Note("This is a fund (ETF). The guide still works, but several steps explain why company figures don't apply to funds.")
    if (snapshot.kind == CompanyKind.UNSUPPORTED) Note("Reported financial statements aren't available for this company, so most steps can only explain the concepts.")
    if (state.syncFailed) Note("Your progress is saved on this device. It will sync with your account when the connection is back.")
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        snapshot.steps.forEach { step ->
            val done = state.completed(step.step.number)
            val current = !done && state.progress?.currentStep == step.step.number
            StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = ResearchStep.accessibilityLabel(step.step.number, step.question, if (done) "Completed" else if (current) "In progress" else "Not started")
            }, onClick = { onAction(GuidedResearchAction.Open(step.step.number)) }, onClickLabel = "Open step ${step.step.number}", verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StepNumber(step.step.number, done)
                    Column(Modifier.weight(1f)) {
                        Text(step.question, style = typography.bodySemiBold, color = colors.textPrimary)
                        Text(if (done) "Completed" else if (current) "In progress" else step.description, style = typography.caption,
                            color = if (done) colors.positiveText else colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    StockButton(state.primaryLabel, { onAction(GuidedResearchAction.StartOrContinue) }, Modifier.fillMaxWidth())
    if (state.started) StockButton("Start over", { onAction(GuidedResearchAction.Restart) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
    Text(snapshot.notes.last(), style = typography.caption, color = colors.textSecondary)
}

@Composable
private fun CompanyHeader(snapshot: ResearchSnapshot) {
    val typography = StockStepsTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        StockTickerAvatar(snapshot.symbol, logoUrl = snapshot.logoUrl)
        Column(Modifier.weight(1f)) {
            Text(snapshot.name, Modifier.semantics { heading() }, style = typography.screenTitle, color = StockStepsTheme.colors.textPrimary)
            Text(listOfNotNull(snapshot.symbol, snapshot.sector).joinToString(" · "), style = typography.caption, color = StockStepsTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun ProgressBlock(completed: Int) {
    Column(Modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text("$completed of ${ResearchStep.COUNT} steps completed", style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textPrimary)
        LinearProgressIndicator(progress = { completed / ResearchStep.COUNT.toFloat() }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            color = StockStepsTheme.colors.primary, trackColor = StockStepsTheme.colors.surfaceSecondary)
    }
}

@Composable
private fun StepNumber(number: Int, done: Boolean) {
    val colors = StockStepsTheme.colors
    Box(Modifier.size(StockStepsTheme.dimensions.icon * 1.5f).clip(CircleShape).background(if (done) colors.positiveContainer else colors.primaryContainer).clearAndSetSemantics { },
        contentAlignment = Alignment.Center) {
        Text(if (done) "✓" else "$number", style = StockStepsTheme.typography.label, color = if (done) colors.positiveText else colors.primaryText)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(StockStepsTheme.colors.cautionContainer).padding(StockStepsTheme.spacing.sm),
        style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textPrimary)
}

// ---------- One step ----------

@Composable
private fun StepContent(state: GuidedResearchState, step: StepView, onAction: (GuidedResearchAction) -> Unit, onLearn: (EducationEntry) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val number = step.step.number
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text("Step $number of ${ResearchStep.COUNT}", style = typography.label, color = colors.primaryText)
        LinearProgressIndicator(progress = { number / ResearchStep.COUNT.toFloat() }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            color = colors.primary, trackColor = colors.surfaceSecondary)
    }
    Text(step.question, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
    step.intro.forEach { Text(it, style = typography.body, color = colors.textBody) }

    // NUMBER → CONTEXT
    if (step.figures.isNotEmpty() || step.chart != null) StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("The numbers", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        step.figures.forEach { figure ->
            Row(Modifier.fillMaxWidth().padding(vertical = spacing.xxs).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(figure.label, style = typography.small, color = colors.textSecondary)
                    figure.detail?.let { Text(it, style = typography.caption, color = colors.textTertiary) }
                }
                Text(figure.value, style = typography.bodySemiBold, color = colors.textPrimary)
            }
        }
        step.chart?.let { ResearchBars(it) }
        step.period?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
    }

    // EXPLANATION
    StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("What this means", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Text(step.meaning ?: step.unavailableReason ?: "This information isn't available for this company.", style = typography.body, color = colors.textBody)
        if (step.meaning != null) step.unavailableReason?.let { Text(it, style = typography.small, color = colors.textSecondary) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text("Keep in mind", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
        Text(step.limitation, style = typography.small, color = colors.textBody)
    }
    Column(Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).border(StockStepsTheme.dimensions.border, colors.primary, StockStepsTheme.shapes.card).padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text("Key takeaway", Modifier.semantics { heading() }, style = typography.label, color = colors.primaryText)
        Text(step.takeaway, style = typography.bodyMedium, color = colors.textPrimary)
    }

    // EDUCATION
    val related = step.learnMore.mapNotNull(BeginnerEducation::entry)
    if (related.isNotEmpty()) Column {
        Text("Learn the terms", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
        related.forEach { entry ->
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = "Explain") { onLearn(entry) }, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, Modifier.weight(1f), style = typography.body, color = colors.primaryText)
                MetricInfoIcon(entry, onLearn)
            }
        }
    }
    QuizCard(step.quiz, state.answers[step.quiz.id], state.progress?.quiz(step.quiz), onAction)
    AskCard(state, onAction)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockButton(if (number == 1) "Overview" else "Back", { onAction(GuidedResearchAction.Back) }, Modifier.weight(1f), variant = StockButtonVariant.OUTLINED)
        StockButton(if (number == ResearchStep.COUNT) "Finish" else "Continue", { onAction(GuidedResearchAction.Next) }, Modifier.weight(1f))
    }
}

/** Horizontal bars with the exact value written beside each bar (never color or length alone). */
@Composable
private fun ResearchBars(chart: SimpleChart) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val max = chart.bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(Modifier.fillMaxWidth().padding(top = spacing.xs).clearAndSetSemantics { contentDescription = chart.description },
        verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(chart.title, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        chart.bars.forEachIndexed { index, bar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(bar.label, Modifier.widthIn(min = 72.dp), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                Box(Modifier.weight(1f).height(spacing.md)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((bar.value / max).toFloat().coerceIn(0.02f, 1f)).clip(StockStepsTheme.shapes.chip)
                        .background(if (index == chart.bars.lastIndex) colors.primary else colors.primary.copy(alpha = 0.45f)))
                }
                Text(bar.display, style = StockStepsTheme.typography.caption, color = colors.textPrimary)
            }
        }
        chart.note?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
    }
}

@Composable
private fun QuizCard(quiz: Quiz, result: QuizResult?, saved: QuizRecord?, onAction: (GuidedResearchAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Quick check (optional)", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
        Text(quiz.question, style = typography.bodySemiBold, color = colors.textPrimary)
        Column(Modifier.semantics { selectableGroup() }, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            quiz.options.forEach { option ->
                val chosen = result?.selectedOptionId == option.id
                val correct = result != null && option.id == quiz.correctOptionId
                Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(StockStepsTheme.shapes.card)
                    .border(StockStepsTheme.dimensions.border, when { correct -> colors.positiveBorder; chosen -> colors.negativeBorder; else -> colors.border }, StockStepsTheme.shapes.card)
                    .background(when { correct -> colors.positiveContainer; chosen -> colors.negativeContainer; else -> colors.surface })
                    .clickable(enabled = result == null, role = androidx.compose.ui.semantics.Role.RadioButton) { onAction(GuidedResearchAction.Answer(option.id)) }
                    .padding(horizontal = spacing.sm, vertical = spacing.xs)
                    .semantics { selected = chosen; if (correct) stateDescription = "Correct answer" else if (chosen) stateDescription = "Your answer, not correct" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(option.text, Modifier.weight(1f), style = typography.body, color = colors.textPrimary)
                    if (correct) Text("Correct", style = typography.caption, color = colors.positiveText)
                    else if (chosen) Text("Your answer", style = typography.caption, color = colors.negativeText)
                }
            }
        }
        if (result != null) {
            Text(result.feedback, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.bodySemiBold,
                color = if (result.correct) colors.positiveText else colors.negativeText)
            Text(result.explanation, style = typography.small, color = colors.textBody)
            StockButton("Try again", { onAction(GuidedResearchAction.RetryQuiz) }, variant = StockButtonVariant.TEXT)
        } else if (saved != null) {
            Text(if (saved.correct) "You answered this correctly before." else "You tried this before. Give it another go.", style = typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun AskCard(state: GuidedResearchState, onAction: (GuidedResearchAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    var open by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    if (!open && state.answer == null) {
        StockButton(if (state.plus) "Ask StockSteps AI" else "Ask StockSteps AI · StockSteps+", { if (state.plus) open = true else onAction(GuidedResearchAction.RequestAi) },
            Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
        return
    }
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Ask StockSteps AI", Modifier.semantics { heading() }, style = typography.cardTitle)
        Spacer(modifier = Modifier.height(spacing.xxs))
        Text("Answers use this company's public figures only. They don't predict prices or recommend buying or selling.", style = typography.caption, color = StockStepsTheme.colors.textSecondary)
        OutlinedTextField(question, { question = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("e.g. What does this mean for a beginner?") })
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockButton("Ask", { onAction(GuidedResearchAction.Ask(question)) }, enabled = question.trim().length >= 3 && !state.asking)
            StockButton("Close", { open = false }, variant = StockButtonVariant.TEXT)
        }
        if (state.asking) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Asking StockSteps AI" })
        state.answer?.let { answer ->
            Text(answer.answer, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.body, color = StockStepsTheme.colors.textBody)
            if (answer.sources.isNotEmpty()) Text("Sources: " + answer.sources.joinToString(), style = typography.caption, color = StockStepsTheme.colors.textSecondary)
            answer.remainingToday?.let { Text("$it questions left today", style = typography.caption, color = StockStepsTheme.colors.textSecondary) }
        }
    }
}

// ---------- Summary ----------

@Composable
private fun Summary(state: GuidedResearchState, snapshot: ResearchSnapshot, watched: Boolean, watchlistEnabled: Boolean, onAction: (GuidedResearchAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    CompanyHeader(snapshot)
    Text(if (state.progress?.finished == true) "You've researched ${GuidedResearchEngine.shortName(snapshot.name)}" else "Your research so far",
        Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
    ProgressBlock(state.completedCount)
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("What you learned", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        snapshot.steps.forEach { step ->
            Column(Modifier.fillMaxWidth().padding(vertical = spacing.xxs).semantics(mergeDescendants = true) { }) {
                Text(step.question, style = typography.small, color = colors.textSecondary)
                Text(step.summary, style = typography.body, color = colors.textPrimary)
            }
        }
    }
    Text("This summary explains reported figures. It isn't a recommendation to buy, sell or hold, and it doesn't predict the share price.",
        style = typography.caption, color = colors.textSecondary)
    StockButton("Review steps", { onAction(GuidedResearchAction.Open(1)) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
    if (watchlistEnabled) StockButton(if (watched) "Saved to watchlist" else "Add to Watchlist", { onAction(GuidedResearchAction.ToggleWatchlist) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
    StockButton("Company details", { onAction(GuidedResearchAction.CompanyDetails) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
    StockButton("Research another company", { onAction(GuidedResearchAction.ResearchAnother) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
    StockButton("Continue learning", { onAction(GuidedResearchAction.ContinueLearning) }, Modifier.fillMaxWidth())
}

// ---------- Entry points ----------

/**
 * Company Details entry: "Understand This Stock". [completed] is the real saved progress for this
 * company (null when never started), so the card never shows invented completion.
 */
@Composable
internal fun UnderstandStockCard(completed: Int?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val label = when {
        completed == null -> "Start learning"
        completed >= ResearchStep.COUNT -> "Review research"
        else -> "Continue learning"
    }
    StockCard(modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Understand This Stock", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Text("Learn how to research this company in five simple steps.", style = typography.small, color = colors.textBody)
        if (completed != null) Text("$completed of ${ResearchStep.COUNT} steps completed", style = typography.caption, color = colors.primaryText)
        StockButton(label, onOpen, Modifier.fillMaxWidth().padding(top = spacing.xs))
    }
}
