package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.earnings.*
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.model.EarningsTiming

// ---------- Earnings Center ----------

/** Upcoming / Results / Following; rows grouped by date. Beat/miss are words, never just colors. */
@Composable
internal fun EarningsCenterScreen(
    state: EarningsCenterState,
    modifier: Modifier,
    onTab: (EarningsTab) -> Unit,
    onRange: (EarningsRange) -> Unit,
    onFilters: (EarningsFilters) -> Unit,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EarningsEducation.Topic?>(null) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore && !state.loading) onLoadMore() }
    LazyColumn(modifier.background(colors.appBackground), state = listState,
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Earnings Center", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("When companies report, what analysts expect and what was reported.", style = typography.small, color = colors.textSecondary)
                if (state.sampleData) StockSampleDataBanner("Sample earnings data for development, not real announcements.")
                StockSegmentedControl(EarningsTab.entries.map { StockSegment(it, it.label) }, state.tab, onTab)
                StockPillSelector(state.ranges, state.range, { it.label }, onRange)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    listOf("US" to "US", "CA" to "Canada").forEach { (code, label) ->
                        val on = code in state.filters.markets
                        FilterChip(on, { onFilters(state.filters.copy(markets = if (on) state.filters.markets - code else state.filters.markets + code)) }, label = { Text(label) })
                    }
                    listOf(EarningsTime.BEFORE_OPEN, EarningsTime.AFTER_CLOSE, EarningsTime.UNKNOWN).forEach { session ->
                        val on = session in state.filters.sessions
                        FilterChip(on, { onFilters(state.filters.copy(sessions = if (on) state.filters.sessions - session else state.filters.sessions + session)) },
                            label = { Text(EarningsFormatter.sessionShort(session)) })
                    }
                    listOf("NASDAQ", "NYSE", "TSX").forEach { exchange ->
                        val on = exchange in state.filters.exchanges
                        FilterChip(on, { onFilters(state.filters.copy(exchanges = if (on) state.filters.exchanges - exchange else state.filters.exchanges + exchange)) }, label = { Text(exchange) })
                    }
                }
                Text("${EarningsFormatter.date(state.from)} – ${EarningsFormatter.date(state.to)}", style = typography.caption, color = colors.textSecondary)
            }
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, onRetry) } }
        if (state.tab == EarningsTab.FOLLOWING && !state.signedIn) item(key = "signin") {
            StockEmptyState("Sign in to see earnings for companies in your watchlists and portfolios.", actionText = "Sign in", onAction = onSignIn)
        } else if (!state.loading && state.error == null && state.rows.isEmpty()) item(key = "empty") {
            StockEmptyState(when (state.tab) {
                EarningsTab.FOLLOWING -> "None of the companies you follow report in this period. Add companies to a watchlist or portfolio to follow their earnings."
                EarningsTab.RESULTS -> "No reported results in this period."
                EarningsTab.UPCOMING -> "No earnings announcements in this period."
            })
        }
        state.days.forEach { (date, rows) ->
            item(key = "day-$date") {
                Text(EarningsFormatter.date(date), Modifier.padding(top = spacing.sm).semantics { heading(); contentDescription = EarningsFormatter.spokenDate(date) },
                    style = typography.label, color = colors.textSecondary)
            }
            items(rows, key = { it.id }) { row -> EarningsRow(row) { onOpen(row.symbol) } }
        }
        if (state.loadingMore) item(key = "more") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                Text("Learn the basics", style = typography.label, modifier = Modifier.padding(top = spacing.sm))
                EarningsEducation.topics.take(6).forEach { topic ->
                    TextButton(onClick = { education = topic }) { Text(topic.title) }
                }
            }
        }
    }
    education?.let { topic -> EducationDialog(topic) { education = null } }
}

@Composable
private fun EarningsRow(row: EarningsRowView, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = row.accessibility }, onClick = onClick, onClickLabel = "Open ${row.symbol} earnings") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(row.symbol, logoUrl = row.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f)) {
                Text("${row.symbol}${row.exchange?.let { " · $it" } ?: ""}", style = typography.bodySemiBold, color = colors.textPrimary)
                Text(row.name, style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badge(if (row.reminder) "Reminder on" else row.status.label)
        }
        Text(listOfNotNull(row.sessionText, row.dateStatusText).joinToString(" · "), style = typography.caption, color = colors.textSecondary)
        row.previousDate?.let { Text("Date moved from ${EarningsFormatter.date(it)}", style = typography.caption, color = colors.cautionText) }
        if (row.epsResult != null || row.revenueResult != null) {
            Text(listOfNotNull(row.epsResult?.let { "EPS $it" }, row.revenueResult?.let { "Revenue $it" }).joinToString(" · "), style = typography.small, color = colors.textPrimary)
        } else Text(row.expectation, style = typography.small, color = colors.textPrimary)
        row.countdown?.let { Text(it, style = typography.caption, color = colors.primary) }
        row.following?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
    }
}

@Composable
private fun Badge(text: String) {
    Text(text, Modifier.clip(StockStepsTheme.shapes.pill).background(StockStepsTheme.colors.surfaceSecondary).padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.tiny, color = StockStepsTheme.colors.textPrimary)
}

@Composable
private fun EducationDialog(topic: EarningsEducation.Topic, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(topic.title) }, text = { Text(topic.body) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } })
}

// ---------- Earnings Details ----------

@Composable
internal fun EarningsDetailsScreen(
    state: EarningsDetailsState,
    modifier: Modifier,
    onReminder: (EarningsTiming?, Int?, Boolean, Double?) -> Unit,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onCompany: () -> Unit,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
    onDismissUpgrade: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val d = state.details
    var education by remember { mutableStateOf<EarningsEducation.Topic?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var reminderSheet by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StockTickerAvatar(state.symbol, logoUrl = d?.event?.logoUrl, size = StockStepsTheme.dimensions.logo)
                    Column(Modifier.weight(1f)) {
                        Text(d?.name ?: state.symbol, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary, maxLines = 2)
                        Text(listOfNotNull(state.symbol, d?.event?.exchange, state.headerPeriod).joinToString(" · "), style = typography.small, color = colors.textSecondary)
                    }
                }
                d?.let { Badge(it.status.label) }
                listOfNotNull(state.headerDate, state.headerSession, state.headerDateStatus).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = typography.small, color = colors.textSecondary)
                }
                state.next?.let { Text(it, style = typography.caption, color = colors.primary) }
                if (d?.sampleData == true) StockSampleDataBanner("Sample earnings data for development, not real results.")
                if (d?.stale == true) Text("The next date hasn't been updated recently and may have changed.", style = typography.caption, color = colors.cautionText)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    OutlinedButton(onClick = { if (state.signedIn) reminderSheet = true else onSignIn() }, enabled = !state.reminderBusy) {
                        Text(if (state.reminder != null) "Reminder on" else "Remind me")
                    }
                    TextButton(onClick = onCompany) { Text("Company details") }
                }
            }
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, onRetry) } }
        d?.summary?.let { summary ->
            item(key = "summary") {
                StockCard {
                    Text(summary, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                    d.summaryExplanation?.let { Text(it, style = typography.small, color = colors.textBody) }
                }
            }
        }
        listOfNotNull(state.eps, state.revenue).forEach { card ->
            item(key = "card-${card.title}") { ResultCardView(card) { education = EarningsEducation.topic(if (card.title == "EPS") "surprise" else "revenue") } }
        }
        val upcoming = d?.next
        if (d != null && state.eps == null && upcoming != null) item(key = "upcoming") {
            val estimate = upcoming.estimate
            StockCard {
                Text("Not reported yet", style = typography.cardTitle)
                Text("Analysts expect EPS of ${EarningsFormatter.eps(estimate?.eps, estimate?.currency)} and revenue of ${EarningsFormatter.revenue(estimate?.revenue, estimate?.currency)}.",
                    style = typography.small)
            }
        }
        if (state.reaction.isNotEmpty() || state.reactionSentence != null) item(key = "reaction") {
            StockCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Price reaction", Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle)
                    TextButton(onClick = { education = EarningsEducation.topic("reaction") }) { Text("How it's measured") }
                }
                state.reaction.forEach { Line(it) }
                state.reactionSentence?.let { Text(it, style = typography.small, color = colors.textBody) }
            }
        }
        if (d != null && d.insights.isNotEmpty() || (d?.lockedInsights ?: 0) > 0) item(key = "insights") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Insights", Modifier.semantics { heading() }, style = typography.sectionTitle)
                d!!.insights.forEach { insight ->
                    StockInsightCard(insight.title, insight.explanation, eyebrow = insight.period, actionText = "Learn more",
                        onClick = { education = EarningsEducation.topic(insight.methodology) })
                }
                if (d.lockedInsights > 0) Text("${d.lockedInsights} more trend insight${if (d.lockedInsights == 1) "" else "s"} with StockSteps+.", style = typography.caption, color = colors.textSecondary)
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "history-title") {
                Column {
                    Text("Earnings history", Modifier.semantics { heading() }, style = typography.sectionTitle)
                    if (state.epsChart.firstOrNull()?.values?.count { it != null } ?: 0 >= 2) {
                        StockTrendChart(
                            values = state.epsChart[0].values,
                            details = state.chartLabels.indices.map { i -> "${state.chartLabels[i]} · reported ${state.epsChart[0].values[i]?.let { EarningsFormatter.eps(it, null) } ?: "—"} · est. ${state.epsChart[1].values[i]?.let { EarningsFormatter.eps(it, null) } ?: "—"}" },
                            xLabels = listOf(state.chartLabels.first(), state.chartLabels.last()), yLabels = emptyList(),
                            description = "Reported EPS versus estimate for ${state.chartLabels.size} quarters.",
                            seriesLabel = "Reported EPS", additional = listOf("Estimate" to state.epsChart[1].values)
                        )
                    }
                }
            }
            items(state.history, key = { it.id }) { row ->
                val open = row.id in expanded
                Column(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .clickable(onClickLabel = if (open) "Collapse" else "Expand") { expanded = if (open) expanded - row.id else expanded + row.id }
                    .semantics(mergeDescendants = true) { contentDescription = row.accessibility; stateDescription = if (open) "Expanded" else "Collapsed" }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.period, Modifier.weight(1f), style = typography.bodySemiBold)
                        Text("EPS ${row.eps}", style = typography.caption, color = colors.textSecondary)
                    }
                    Text("Revenue ${row.revenue}" + (row.growth?.let { " · $it YoY" } ?: ""), style = typography.caption, color = colors.textSecondary)
                    if (open) row.lines.forEach { Line(it) }
                    HorizontalDivider(Modifier.padding(top = StockStepsTheme.spacing.xs), color = colors.borderSubtle)
                }
            }
            if (d?.historyLocked == true) item(key = "history-locked") {
                Text("Older quarters are available with StockSteps+.", style = typography.caption, color = colors.textSecondary)
            }
        }
        item(key = "ai") {
            StockCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask about these earnings", Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle)
                    if (!state.plus) Badge("StockSteps+")
                }
                Text("Answers use only the verified figures above and say when the data can't explain a cause.", style = typography.caption, color = colors.textSecondary)
                OutlinedTextField(question, { question = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("e.g. Explain this earnings report") })
                Button(onClick = { onAsk(question) }, enabled = question.isNotBlank() && !state.asking) { Text(if (state.plus) "Ask" else "Ask with StockSteps+") }
                if (state.asking) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.answer?.let { answer ->
                    Text(answer.answer, style = typography.small, color = colors.textBody)
                    if (answer.sources.isNotEmpty()) Text("Sources: " + answer.sources.joinToString(), style = typography.caption, color = colors.textSecondary)
                    answer.remainingToday?.let { Text("$it questions left today.", style = typography.caption, color = colors.textTertiary) }
                }
            }
        }
        item(key = "learn") {
            Column {
                Text("Learn", Modifier.semantics { heading() }, style = typography.sectionTitle)
                EarningsEducation.topics.forEach { topic -> TextButton(onClick = { education = topic }) { Text(topic.title) } }
                d?.notes?.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                Text("Not investment advice. Earnings results don't predict how a stock will move.", style = typography.caption, color = colors.textTertiary)
            }
        }
    }
    education?.let { topic -> EducationDialog(topic) { education = null } }
    if (reminderSheet) ReminderDialog(state, onDismiss = { reminderSheet = false }) { timing, lead, results, surprise -> onReminder(timing, lead, results, surprise); reminderSheet = false }
    if (state.upgradeRequired) AlertDialog(onDismissRequest = onDismissUpgrade, title = { Text("StockSteps+") },
        text = { Text("AI earnings research is part of StockSteps+. Nothing was sent.") },
        confirmButton = { TextButton(onClick = onUpgrade) { Text("See StockSteps+") } }, dismissButton = { TextButton(onClick = onDismissUpgrade) { Text("Not now") } })
    state.message?.let { message -> AlertDialog(onDismissRequest = onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } }) }
}

@Composable
private fun Line(line: MetricLine) {
    Row(Modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.xxs).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        Text(line.label, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
        Column(horizontalAlignment = Alignment.End) {
            Text(line.value, style = StockStepsTheme.typography.numberLabel, color = StockStepsTheme.colors.textPrimary)
            line.detail?.let { Text(it, style = StockStepsTheme.typography.tiny, color = StockStepsTheme.colors.textTertiary) }
        }
    }
}

/** Beat/miss shown as words and a neutral accent: a beat isn't presented as "good for the stock". */
@Composable
private fun ResultCardView(card: ResultCard, onLearn: () -> Unit) {
    val colors = StockStepsTheme.colors
    StockCard(Modifier.semantics(mergeDescendants = true) { contentDescription = card.accessibility }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.title, Modifier.weight(1f), style = StockStepsTheme.typography.cardTitle)
            Text(card.headline, style = StockStepsTheme.typography.label, color = if (card.classification == Classification.UNAVAILABLE) colors.textSecondary else colors.primary)
        }
        card.lines.forEach { Line(it) }
        card.note?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
        TextButton(onClick = onLearn) { Text("What does this mean?") }
    }
}

@Composable
private fun ReminderDialog(state: EarningsDetailsState, onDismiss: () -> Unit, onSave: (EarningsTiming?, Int?, Boolean, Double?) -> Unit) {
    val current = state.reminder
    var timing by remember { mutableStateOf(current?.earningsTiming ?: EarningsTiming.BOTH) }
    var lead by remember { mutableStateOf(current?.earningsLeadDays ?: 1) }
    var results by remember { mutableStateOf(current?.earningsResults ?: false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Earnings reminder") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            listOf(EarningsTiming.DAY_BEFORE to "Day before", EarningsTiming.DAY_OF to "On the day", EarningsTiming.BOTH to "Both").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable { timing = value }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(timing == value, { timing = value }); Text(label)
                }
            }
            Text("StockSteps+ options", style = StockStepsTheme.typography.label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Days before: $lead", Modifier.weight(1f))
                TextButton(onClick = { lead = (lead - 1).coerceAtLeast(1) }, enabled = state.plus) { Text("−") }
                TextButton(onClick = { lead = (lead + 1).coerceAtMost(7) }, enabled = state.plus) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify when results are out", Modifier.weight(1f)); Switch(results, { results = it }, enabled = state.plus)
            }
            if (!state.plus) Text("Day before and day-of reminders are free. Earlier reminders and results notifications are part of StockSteps+.", style = StockStepsTheme.typography.caption)
            Text("If the date is only estimated or the time isn't announced, reminders use the date and may move if the company changes it. You won't get a reminder for both the old and new date.",
                style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
        }
    }, confirmButton = { TextButton(onClick = { onSave(timing, lead.takeIf { state.plus && it != 1 }, results && state.plus, null) }) { Text("Save") } },
        dismissButton = { Row {
            if (current != null) TextButton(onClick = { onSave(null, null, false, null) }) { Text("Turn off") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}
