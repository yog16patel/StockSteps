package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.earnings.*

// Earnings Intelligence Lite, Phase 5 (StockSteps+): premium sections on Earnings Results, the
// personalized digest and its settings. Screens only render presenter state; plan, quotas and every
// AI sentence come from the server. AI text is shown as plain text (never HTML or links).

internal sealed interface PremiumAction {
    data object Explain : PremiumAction
    data object ToggleExplanation : PremiumAction
    data class Ask(val question: String) : PremiumAction
    data object ResetConversation : PremiumAction
    data object LoadHistory : PremiumAction
    data class Metric(val metric: HistoryMetric) : PremiumAction
    data object Upgrade : PremiumAction
    data object DismissUpgrade : PremiumAction
    data object SignIn : PremiumAction
    data object DismissSignIn : PremiumAction
    data object Retry : PremiumAction
    data class Scenario(val id: String?) : PremiumAction
}

/** "StockSteps+" marker in words (never colour alone). */
@Composable
internal fun PlusBadge(modifier: Modifier = Modifier) {
    Text("StockSteps+", modifier.clip(StockStepsTheme.shapes.pill).background(StockStepsTheme.colors.primaryContainer)
        .padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs), style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.primaryText)
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null, plus: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary)
            if (plus) PlusBadge()
        }
        subtitle?.let { Text(it, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary) }
    }
}

@Composable
private fun Caption(text: String, caution: Boolean = false) =
    Text(text, style = StockStepsTheme.typography.caption, color = if (caution) StockStepsTheme.colors.cautionText else StockStepsTheme.colors.textSecondary)

@Composable
private fun PremiumErrorLine(error: PremiumError, onRetry: (() -> Unit)?) {
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(error.message, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.cautionText)
        if (error.retryable && onRetry != null) StockButton("Try Again", onClick = onRetry, variant = StockButtonVariant.TEXT)
    }
}

/** Contextual StockSteps+ prompt (the existing upgrade path: Settings). Never shown automatically. */
@Composable
internal fun PremiumUpgradeDialog(reason: UpgradeReason, onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface,
        title = { Text(reason.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                Text(reason.body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
                PremiumCopy.BENEFITS.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody) }
                Caption(PremiumCopy.FAIR_USE)
                Caption("Basic earnings results, history and reminders stay free.")
            }
        },
        confirmButton = { StockButton("See StockSteps+", onClick = onUpgrade, variant = StockButtonVariant.TEXT) },
        dismissButton = { StockButton("Not now", onClick = onDismiss, variant = StockButtonVariant.TEXT) })
}

@Composable
internal fun PremiumSignInDialog(onSignIn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface, title = { Text("Sign in to continue") },
        text = { Text("Sign in to use StockSteps+ earnings features. Basic earnings results don't need an account.", style = StockStepsTheme.typography.body) },
        confirmButton = { StockButton("Sign In", onClick = { onDismiss(); onSignIn() }, variant = StockButtonVariant.TEXT) },
        dismissButton = { StockButton("Not now", onClick = onDismiss, variant = StockButtonVariant.TEXT) })
}

/** Claim text plus the source labels it cites (plain text). */
@Composable
private fun Claim(title: String, claim: ExplainedClaim, citations: List<EarningsCitation>) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textPrimary)
        Text(claim.text, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
        val labels = claim.sourceIds.mapNotNull { id -> citations.firstOrNull { it.sourceId == id }?.label }
        if (labels.isNotEmpty()) Caption("Source: ${labels.joinToString("; ")}")
    }
}

/**
 * The Earnings Results premium items: "Understand These Earnings" (with Key Takeaways), "Ask About
 * These Results" and "Historical Earnings". Free users see short descriptions and contextual prompts.
 */
internal fun LazyListScope.premiumEarningsItems(state: EarningsPremiumState, onAction: (PremiumAction) -> Unit) {
    item(key = "premium-understand") { UnderstandCard(state, onAction) }
    item(key = "premium-ask") { AskCard(state, onAction) }
    item(key = "premium-history") { HistoryCard(state, onAction) }
}

@Composable
private fun UnderstandCard(state: EarningsPremiumState, onAction: (PremiumAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(PremiumCopy.TITLE, PremiumCopy.SUBTITLE)
        if (state.sampleData) Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            PremiumCopy.SCENARIOS.forEach { (id, label) -> StockChip(label, state.scenario == id, onClick = { onAction(PremiumAction.Scenario(id)) }) }
        }
        if (!state.signedIn) {
            Text("An explanation of what was reported, how it compared with expectations and what's still uncertain.", style = StockStepsTheme.typography.small, color = colors.textBody)
            StockButton("Sign In to Explain With AI", onClick = { onAction(PremiumAction.SignIn) }, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
            return@StockCard
        }
        if (state.loading && state.overview == null) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading StockSteps+ features" })
        state.overviewError?.let { PremiumErrorLine(it) { onAction(PremiumAction.Retry) } }
        val overview = state.overview ?: return@StockCard
        state.planNote?.let { Caption(it, caution = true) }
        val e = state.explanation
        if (e == null) {
            Text(overview.preview, style = StockStepsTheme.typography.small, color = colors.textBody)
            if (overview.explanation == ExplanationAvailability.STALE) Caption("An earlier explanation used figures that have since changed. A new one will use the current data.", caution = true)
            if (!state.plus) Caption("Explanations use only the verified figures on this screen. Part of StockSteps+.")
            StockButton(if (state.plus) "Explain With AI" else "Explain With AI · StockSteps+", onClick = { onAction(PremiumAction.Explain) },
                variant = if (state.plus) StockButtonVariant.PRIMARY else StockButtonVariant.OUTLINED, enabled = !state.explaining && overview.aiAvailable,
                icon = StockIcons.Lightbulb, modifier = Modifier.fillMaxWidth())
            if (state.explaining) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                CircularProgressIndicator(Modifier.size(StockStepsTheme.dimensions.iconSmall), strokeWidth = StockStepsTheme.spacing.xxs)
                Caption("Building an explanation from the verified figures…")
            }
            state.explainError?.let { PremiumErrorLine(it) { onAction(PremiumAction.Explain) } }
            if (state.plus) state.explainUsage?.let { Caption(it) }
            overview.notes.forEach { Caption(it) }
            return@StockCard
        }
        if (e.sample) StockSampleDataBanner("Sample explanation from a template (MOCK). No AI service was used.")
        if (e.cached) Caption("Saved explanation from ${e.generatedAt.take(10)}; it matches the current data.")
        Text(e.summary.text, style = StockStepsTheme.typography.body, color = colors.textBody)
        // Key Takeaways stay visible; the detail is behind "Show full explanation" (progressive disclosure).
        Column(Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.educationContainer).padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text("Key Takeaways", Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textPrimary)
            e.beginnerTakeaways.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
        }
        StockButton(if (state.expanded) "Show less" else "Show full explanation", onClick = { onAction(PremiumAction.ToggleExplanation) }, variant = StockButtonVariant.TEXT,
            modifier = Modifier.semantics { stateDescription = if (state.expanded) "Expanded" else "Collapsed" })
        if (state.expanded) {
            e.epsExplanation?.let { Claim("EPS compared with expectations", it, e.citations) }
            e.revenueExplanation?.let { Claim("Revenue compared with expectations", it, e.citations) }
            e.growthExplanation?.let { Claim("Compared with earlier periods", it, e.citations) }
            e.priceReactionExplanation?.let { Claim("What the stock did after earnings", it, e.citations) }
            if (e.uncertaintyNotes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("What remains uncertain", Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textPrimary)
                e.uncertaintyNotes.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
            }
            if (e.keyTerms.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Key terms", Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textPrimary)
                e.keyTerms.forEach { t -> Text("${t.term}: ${t.meaning}", style = StockStepsTheme.typography.small, color = colors.textBody) }
            }
            if (e.citations.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Sources", style = StockStepsTheme.typography.label, color = colors.textSecondary)
                e.citations.filter { it.kind != CitationKind.LESSON }.forEach { c -> Caption("${c.label} · ${c.provider}" + (c.retrievedAt?.let { " · retrieved ${it.take(10)}" } ?: "")) }
            }
        }
        e.dataWarnings.forEach { Caption(it, caution = true) }
        Caption(e.disclaimer)
        state.explainUsage?.let { Caption(it) }
    }
}

@Composable
private fun AskCard(state: EarningsPremiumState, onAction: (PremiumAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var text by rememberSaveable(state.reportId) { mutableStateOf("") }
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle("Ask About These Results", "Answers use this report's verified figures and StockSteps lessons.")
        if (state.signedIn && state.overview == null) return@StockCard
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            state.chips.forEach { chip ->
                StockButton(chip.text, onClick = { onAction(if (state.signedIn) PremiumAction.Ask(chip.text) else PremiumAction.SignIn) }, variant = StockButtonVariant.OUTLINED, enabled = !state.asking)
            }
        }
        state.conversation.forEach { turn ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("You asked: ${turn.question}", style = StockStepsTheme.typography.label, color = colors.textPrimary)
                val error = turn.error
                val a = turn.answer
                when {
                    turn.pending -> Caption("Thinking…")
                    error != null -> PremiumErrorLine(error, onRetry = { onAction(PremiumAction.Ask(turn.question)) })
                    a != null -> {
                        if (a.sample) Caption("Sample answer (MOCK template)")
                        Text(a.answer, style = StockStepsTheme.typography.small, color = if (a.scope == AnswerScope.UNSUPPORTED) colors.cautionText else colors.textBody)
                        a.points.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
                        if (a.scope == AnswerScope.INSUFFICIENT_DATA) Caption("The verified data can't answer this fully.")
                        if (a.citations.isNotEmpty()) Caption("Source: " + a.citations.joinToString("; ") { it.label })
                    }
                }
            }
        }
        state.contextNote?.let { Caption(it, caution = true) }
        StockTextField(text, { if (it.length <= 300) text = it }, "Your question", Modifier.fillMaxWidth(), placeholder = "e.g. Why can a stock fall after an EPS beat?",
            supporting = "${text.length}/300 · Educational answers only, never advice.", singleLine = false, enabled = !state.asking)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StockButton(if (state.plus || !state.signedIn) "Ask" else "Ask · StockSteps+", onClick = { onAction(PremiumAction.Ask(text)); if (state.plus) text = "" },
                enabled = text.isNotBlank() && !state.asking, variant = StockButtonVariant.SECONDARY)
            if (state.conversation.isNotEmpty()) StockButton("Start Over", onClick = { onAction(PremiumAction.ResetConversation) }, variant = StockButtonVariant.TEXT)
        }
        if (state.plus) state.askUsage?.let { Caption(it) }
    }
}

@Composable
private fun HistoryCard(state: EarningsPremiumState, onAction: (PremiumAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle("Historical Earnings", "Revenue and EPS over recent fiscal quarters, with plain-English observations.")
        val h = state.history
        if (h == null) {
            state.overview?.historyQuarters?.takeIf { it > 0 }?.let { Caption("$it reported quarter${if (it == 1) "" else "s"} available (up to ${HistoricalEarningsEngine.MAX_QUARTERS} shown).") }
            StockButton(if (state.plus || !state.signedIn) "Show Historical Earnings" else "Show Historical Earnings · StockSteps+",
                onClick = { onAction(if (state.signedIn) PremiumAction.LoadHistory else PremiumAction.SignIn) }, variant = StockButtonVariant.OUTLINED,
                enabled = !state.historyLoading, modifier = Modifier.fillMaxWidth())
            if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading historical earnings" })
            state.historyError?.let { PremiumErrorLine(it) { onAction(PremiumAction.LoadHistory) } }
            return@StockCard
        }
        if (h.sampleData) StockSampleDataBanner("Sample earnings history for development, not real results.")
        StockSegmentedControl(HistoryMetric.entries.map { StockSegment(it, it.label) }, state.metric, { onAction(PremiumAction.Metric(it)) })
        state.chart?.let { chart ->
            if (chart.values.any { it != null }) StockTrendChart(chart.values, chart.details, chart.xLabels, chart.yLabels, chart.description,
                reference = if (chart.zeroLine) 0.0 else null, referenceLabel = if (chart.zeroLine) "Zero (a loss is below this line)" else null,
                seriesLabel = chart.metric.label, height = StockStepsTheme.dimensions.touchTarget * 3)
            chart.emptyNote?.let { Caption(it) }
            Caption("Tap or drag on the chart to see each quarter's full value.")
        }
        if (h.deterministicObservations.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text("What the history shows", Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textPrimary)
            h.deterministicObservations.forEach { Text("• ${it.text}", style = StockStepsTheme.typography.small, color = colors.textBody) }
        }
        state.historyRows.forEach { row ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = row.accessibility }, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockDivider()
                Text(row.label, style = StockStepsTheme.typography.label, color = colors.textPrimary)
                if (row.missing) Caption("No results for this quarter from the data source.")
                row.lines.forEach { l -> Row { Text(l.label, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = colors.textSecondary); Text(l.value, style = StockStepsTheme.typography.small, color = colors.textPrimary, fontWeight = FontWeight.Medium) } }
                row.notes.forEach { Caption(it) }
            }
        }
        h.dataWarnings.forEach { Caption(it, caution = true) }
        h.nextCursor?.let { StockButton("Show Earlier Quarters", onClick = { onAction(PremiumAction.LoadHistory) }, variant = StockButtonVariant.TEXT) }
        Caption(h.disclaimer)
        h.sources.forEach { Caption(it) }
    }
}

// ---------- Personalized digest ----------

internal sealed interface DigestAction {
    data object Retry : DigestAction
    data object Explain : DigestAction
    data class OpenResults(val reportId: String) : DigestAction
    data class OpenEvent(val eventId: String) : DigestAction
    data object Settings : DigestAction
    data object Upgrade : DigestAction
    data object DismissUpgrade : DigestAction
    data object SignIn : DigestAction
    data object RecentWatchlist : DigestAction
    data object LoadHistory : DigestAction
    data class Cadence(val cadence: DigestCadence) : DigestAction
    data class Day(val day: String) : DigestAction
    data class Upcoming(val include: Boolean) : DigestAction
    data class Interest(val key: String) : DigestAction
    data object Reminders : DigestAction
    data class Scenario(val id: String?) : DigestAction
}

private val DIGEST_SCENARIOS = listOf<Pair<String?, String>>(null to "Normal", "no-events" to "No digest events", "empty-watchlist" to "Empty watchlist", "ai-failure" to "AI failure")

@Composable
internal fun EarningsDigestScreen(state: EarningsDigestState, modifier: Modifier, onAction: (DigestAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var lesson by remember { mutableStateOf<DigestLesson?>(null) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your Earnings Digest", Modifier.weight(1f).semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                    PlusBadge()
                }
                Text("What happened with the companies on your watchlists. Watching a company doesn't mean you own it.", style = typography.small, color = colors.textSecondary)
            }
        }
        if (!state.signedIn) { item(key = "signin") { StockEmptyState("Sign in to see your personalized earnings digest.", actionText = "Sign In", onAction = { onAction(DigestAction.SignIn) }) }; return@LazyColumn }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading your digest" }) }
        state.settingsError?.let { item(key = "settings-error") { StockErrorState(it.message, { onAction(DigestAction.Retry) }) } }
        val settings = state.settings
        if (settings != null && !settings.plus) {
            item(key = "upgrade") {
                StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("A weekly summary of your watchlist's earnings: what was reported, what's coming up and what to learn from it.", style = typography.body, color = colors.textBody)
                    PremiumCopy.status(settings.entitlementStatus)?.let { Caption(it, caution = true) }
                    StockButton("See StockSteps+", onClick = { onAction(DigestAction.Upgrade) }, variant = StockButtonVariant.PRIMARY, modifier = Modifier.fillMaxWidth())
                    StockButton("Recent Earnings From Your Watchlist", onClick = { onAction(DigestAction.RecentWatchlist) }, variant = StockButtonVariant.TEXT)
                    Caption("Recent results and the Earnings Calendar stay free.")
                }
            }
        }
        state.error?.let { item(key = "error") { StockErrorState(it.message, if (it.retryable) ({ onAction(DigestAction.Retry) }) else null) } }
        val d = state.digest ?: return@LazyColumn
        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("${EarningsFormatter.date(d.periodStart)} – ${EarningsFormatter.date(d.periodEnd)}", style = typography.bodySemiBold, color = colors.textPrimary)
                Caption("From ${d.watchlistCompanies} compan${if (d.watchlistCompanies == 1) "y" else "ies"} on your watchlists.")
                if (d.sampleData) {
                    StockSampleDataBanner("Sample earnings data for development, not real results.")
                    Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        DIGEST_SCENARIOS.forEach { (id, label) -> StockChip(label, state.scenario == id, onClick = { onAction(DigestAction.Scenario(id)) }) }
                    }
                }
            }
        }
        if (d.empty) item(key = "empty") { StockEmptyState(d.emptyReason ?: "Nothing to report this week.", actionText = "Recent Earnings From Your Watchlist", onAction = { onAction(DigestAction.RecentWatchlist) }) }
        if (d.recentlyReported.isNotEmpty()) item(key = "reported") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Recently Reported", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                d.recentlyReported.forEach { r ->
                    StockCard(Modifier.fillMaxWidth(), onClick = { onAction(DigestAction.OpenResults(r.reportId)) }, onClickLabel = "Open ${r.companyName} results",
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text("${r.companyName} (${r.symbol})", style = typography.bodySemiBold, color = colors.textPrimary)
                        Text(r.headline, style = typography.small, color = colors.textBody)
                        Caption("Reported ${EarningsFormatter.date(r.reportDate)}")
                        r.reactionText?.let { Caption(it) }
                        r.dataNote?.let { Caption(it, caution = true) }
                    }
                }
            }
        }
        if (d.upcomingEvents.isNotEmpty()) item(key = "upcoming") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Coming Up", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                d.upcomingEvents.forEach { u ->
                    StockCard(Modifier.fillMaxWidth(), onClick = { onAction(DigestAction.OpenEvent(u.eventId)) }, onClickLabel = "Open ${u.companyName} earnings event",
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text("${u.companyName} (${u.symbol})", style = typography.bodySemiBold, color = colors.textPrimary)
                        Caption(u.whenText)
                    }
                }
            }
        }
        if (d.educationalHighlights.isNotEmpty()) item(key = "learn") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("What to Learn", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                d.educationalHighlights.forEach { l ->
                    StockButton(l.title, onClick = { lesson = l }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
                    Caption(l.reason)
                }
            }
        }
        if (!d.empty) item(key = "ai") {
            StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionTitle("Summarize With AI", "A short educational summary of this digest, built only from the items above.")
                val ai = d.aiSummary
                if (ai == null) {
                    StockButton("Summarize With AI", onClick = { onAction(DigestAction.Explain) }, enabled = !state.aiLoading, variant = StockButtonVariant.SECONDARY, icon = StockIcons.Lightbulb)
                    if (state.aiLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    if (ai.sample) Caption("Sample summary (MOCK template)")
                    Text(ai.text, style = typography.small, color = colors.textBody)
                    ai.points.forEach { Text("• $it", style = typography.small, color = colors.textBody) }
                    Caption("Education, not investment advice.")
                }
                state.aiError?.let { PremiumErrorLine(it) { onAction(DigestAction.Explain) } }
                PremiumCopy.quotaText(d.usage?.quota(EarningsAiCategory.DIGEST))?.let { Caption(it) }
            }
        }
        d.dataNotes.forEach { note -> item(key = "note-$note") { Caption(note) } }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockButton("Digest Settings", onClick = { onAction(DigestAction.Settings) }, variant = StockButtonVariant.OUTLINED, icon = StockIcons.Bell, modifier = Modifier.fillMaxWidth())
                StockButton("Recent Earnings From Your Watchlist", onClick = { onAction(DigestAction.RecentWatchlist) }, variant = StockButtonVariant.TEXT)
                if (state.history == null) StockButton("Earlier Digests", onClick = { onAction(DigestAction.LoadHistory) }, variant = StockButtonVariant.TEXT)
            }
        }
        state.history?.let { h -> item(key = "history") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Earlier Digests", Modifier.semantics { heading() }, style = typography.label, color = colors.textPrimary)
                if (h.entries.isEmpty()) Caption("No earlier digests yet.")
                h.entries.forEach { e -> Caption("${EarningsFormatter.date(e.periodStart)} – ${EarningsFormatter.date(e.periodEnd)}: ${e.reportedCount} reported, ${e.upcomingCount} coming up" +
                    if (e.notified) " · notification sent" else "") }
            }
        } }
    }
    lesson?.let { l -> AlertDialog(onDismissRequest = { lesson = null }, title = { Text(l.title) }, text = { Text(l.body) },
        confirmButton = { TextButton(onClick = { lesson = null }) { Text("Got it") } }) }
}

/** "Earnings Digest & AI" settings: weekly digest (opt-in), learning interests and AI usage. */
@Composable
internal fun EarningsDigestSettingsScreen(state: EarningsDigestState, permissionDenied: Boolean, modifier: Modifier, onAction: (DigestAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Earnings Digest & AI", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("A weekly summary of your watchlist's earnings, and your AI allowance.", style = typography.small, color = colors.textSecondary)
            }
        }
        if (!state.signedIn) { item(key = "signin") { StockEmptyState("Sign in to manage your earnings digest.", actionText = "Sign In", onAction = { onAction(DigestAction.SignIn) }) }; return@LazyColumn }
        state.settingsError?.let { item(key = "error") { StockErrorState(it.message, { onAction(DigestAction.Retry) }) } }
        val s = state.settings ?: return@LazyColumn
        val p = s.preferences
        item(key = "digest") {
            StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle("Personalized Earnings Digest", "Off unless you turn it on. Nothing is sent in a week with nothing to report.")
                StockSegmentedControl(DigestCadence.entries.map { StockSegment(it, it.label) }, p.cadence, { onAction(DigestAction.Cadence(it)) })
                Text(s.statusMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.small, color = if (s.deliveryActive) colors.textBody else colors.cautionText)
                s.nextDeliveryText?.let { Caption("Next: $it") }
                if (permissionDenied && p.cadence == DigestCadence.WEEKLY) Caption("Notifications are off for StockSteps on this device, so the digest won't appear until you allow them.", caution = true)
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Day", style = typography.label, color = colors.textPrimary)
                Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    DigestPolicy.DAYS.forEach { day -> StockChip(DigestPolicy.dayLabel(day).take(3), p.dayOfWeek == day, onClick = { onAction(DigestAction.Day(day)) },
                        modifier = Modifier.semantics { contentDescription = DigestPolicy.dayLabel(day) }) }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .toggleable(p.includeUpcoming, role = Role.Switch, onValueChange = { onAction(DigestAction.Upcoming(it)) }), verticalAlignment = Alignment.CenterVertically) {
                    Text("Include upcoming earnings", Modifier.weight(1f), style = typography.body, color = colors.textBody)
                    Switch(p.includeUpcoming, onCheckedChange = null)
                }
                Caption("Delivered around ${s.deliveryTime} (${s.timeZone})" + (s.quietHours?.let { ", outside quiet hours $it" } ?: "") + ". Time and quiet hours are set in Earnings Reminders.")
                StockButton("Earnings Reminders Settings", onClick = { onAction(DigestAction.Reminders) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Bell)
                if (!s.plus) StockButton("See StockSteps+", onClick = { onAction(DigestAction.Upgrade) }, variant = StockButtonVariant.OUTLINED)
            }
        }
        item(key = "interests") {
            StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle("Learning Interests", "Used only to choose lessons in your digest.", plus = false)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    DigestPolicy.INTERESTS.forEach { (key, label) ->
                        val on = key in p.interests
                        StockChip(if (on) "✓ $label" else label, on, onClick = { onAction(DigestAction.Interest(key)) }, modifier = Modifier.semantics { stateDescription = if (on) "Selected" else "Not selected" })
                    }
                }
            }
        }
        item(key = "usage") {
            StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle("AI Usage", PremiumCopy.FAIR_USE)
                val usage = s.usage
                if (usage == null || !usage.plus) Caption("AI earnings features are part of StockSteps+.")
                else usage.quotas.forEach { q ->
                    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(q.category.label, style = typography.label, color = colors.textPrimary)
                        LinearProgressIndicator(progress = { if (q.limit == 0) 0f else q.remaining.toFloat() / q.limit }, modifier = Modifier.fillMaxWidth())
                        PremiumCopy.quotaText(q)?.let { Caption(it) }
                    }
                }
                usage?.note?.let { Caption(it, caution = true) }
                PremiumCopy.status(s.entitlementStatus)?.let { Caption(it, caution = true) }
                if (s.sampleData) Caption("MOCK: limits and AI output are simulated; no AI service or billing is used.")
            }
        }
    }
}
