package org.example.stocksteps.presentation.learn

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.components.StockButton
import org.example.stocksteps.designsystem.components.StockButtonVariant
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.learning.EducationEntry

/** A 48dp "i" button that opens a beginner explanation; read as "What is revenue?". */
@Composable
internal fun MetricInfoIcon(entry: EducationEntry, onClick: (EducationEntry) -> Unit) {
    IconButton(onClick = { onClick(entry) }, modifier = Modifier.size(StockStepsTheme.dimensions.touchTarget)) {
        Icon(StockIcons.Info, contentDescription = entry.title, tint = StockStepsTheme.colors.primary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}

/**
 * The reusable explanation sheet: what it is, why it matters, how to read it, and its limits.
 * Content comes from BeginnerEducation, so every screen explains a metric the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BeginnerExplanationSheet(entry: EducationEntry, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(entry.title, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
            Section("What it is", entry.short)
            Section("Why it matters", entry.why)
            Section("How to read it", entry.interpret)
            entry.example?.let { Section("Example", it) }
            entry.detailed?.let { Section("More detail", it) }
            Section("Keep in mind", entry.limitations)
            StockButton("Got it", onDismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.SECONDARY)
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary)
        Text(body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
    }
}

/**
 * A tapped metric's explanation (Phase 5D.5.3): the screen's label, the existing metric explanation and — only when the metric has a Learn
 * term — a primary "Learn more about …" action that closes the sheet and opens the term. Nothing here is new educational text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetricExplanationSheet(explanation: org.example.stocksteps.learning.MetricExplanation, onLearn: (String) -> Unit, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(explanation.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            Text(explanation.explanation, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
            MetricLearnMore(explanation.term, onLearn, onDismiss)
        }
    }
}

/** "Learn more about P/E ratio" (primary, full width) when [term] exists; it closes the current sheet first, then opens the term. */
@Composable
internal fun MetricLearnMore(term: EducationEntry?, onLearn: (String) -> Unit, onDismiss: () -> Unit) {
    term ?: return
    StockButton(org.example.stocksteps.learning.MetricLearnTerms.learnMoreLabel(term), { onDismiss(); onLearn(term.id) }, Modifier.fillMaxWidth())
}
