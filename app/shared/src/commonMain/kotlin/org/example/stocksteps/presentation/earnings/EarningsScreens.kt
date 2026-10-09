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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.earnings.*
import org.example.stocksteps.model.EarningsTiming

// ---------- Earnings Calendar ----------

internal sealed interface CalendarAction {
    data object PreviousWeek : CalendarAction
    data object NextWeek : CalendarAction
    data object Today : CalendarAction
    data class SelectDate(val date: String) : CalendarAction
    data class Mode(val mode: CalendarMode) : CalendarAction
    data class Tab(val tab: CalendarTab) : CalendarAction
    data class Filter(val filter: CalendarFilter) : CalendarAction
    data class Query(val text: String) : CalendarAction
    data object ClearQuery : CalendarAction
    data class Open(val eventId: String) : CalendarAction
    /** Opens Earnings Results for a verified reported event. */
    data class OpenResults(val reportId: String) : CalendarAction
    data object LoadMore : CalendarAction
    data object Retry : CalendarAction
    data object SignIn : CalendarAction
    data class Scenario(val name: String?) : CalendarAction
}

/** MOCK-only demo scenarios; the server ignores them in REAL. */
internal val SAMPLE_SCENARIOS: List<Pair<String?, String>> = listOf(null to "Normal", "stale-cache" to "Stale cached data", "provider-unavailable" to "Provider unavailable")

/** Epoch-day conversions for the Material date picker (UTC midnight; the date itself is never shifted). */
private fun epochMillis(date: String): Long? = org.example.stocksteps.markets.MarketsPresenter.dayNumber(date)?.let { it.toLong() * 86_400_000L }
private fun dateOf(millis: Long): String = org.example.stocksteps.portfolio.analytics.AnalyticsDates.plusDays("1970-01-01", (millis / 86_400_000L).toInt())

/**
 * Earnings Calendar: week strip with day counts, Upcoming/Reported, All/My Watchlist, day or week
 * view and search. Compact cards show dates, timing and status only (no figures).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EarningsCalendarScreen(state: EarningsCalendarState, modifier: Modifier, onAction: (CalendarAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var help by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore && !state.loading) onAction(CalendarAction.LoadMore) }
    LazyColumn(modifier.background(colors.appBackground), state = listState,
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Earnings Calendar", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("See when companies are expected to report earnings.", style = typography.small, color = colors.textSecondary)
                StockButton("What are earnings?", onClick = { help = true }, variant = StockButtonVariant.TEXT, icon = StockIcons.Help)
                if (state.sampleData) {
                    StockSampleDataBanner("Sample earnings data for development, not real announcements.")
                    Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        SAMPLE_SCENARIOS.forEach { (name, label) -> StockChip(label, state.scenario == name, onClick = { onAction(CalendarAction.Scenario(name)) }) }
                    }
                }
            }
        }
        item(key = "week") { WeekStrip(state, onAction) { picking = true } }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockSegmentedControl(CalendarTab.entries.map { StockSegment(it, it.label) }, state.selection.tab, { onAction(CalendarAction.Tab(it)) })
                Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    CalendarFilter.entries.forEach { filter -> StockChip(filter.label, state.selection.filter == filter, onClick = { onAction(CalendarAction.Filter(filter)) }) }
                }
                if (!state.searching) StockPillSelector(CalendarMode.entries, state.selection.mode, { if (it == CalendarMode.DAY) "Selected day" else "Whole week" }, { onAction(CalendarAction.Mode(it)) })
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockTextField(state.selection.query, { onAction(CalendarAction.Query(it)) }, "Search by company or ticker", Modifier.weight(1f), placeholder = "e.g. Apple or AAPL")
                    if (state.selection.query.isNotEmpty()) StockButton("Clear", onClick = { onAction(CalendarAction.ClearQuery) }, variant = StockButtonVariant.TEXT)
                }
                state.rangeText?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                state.freshnessText?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.caption, color = colors.cautionText) }
                if (state.sourceStale) Text("Some dates haven't been updated by the data provider recently and may have changed.", style = typography.caption, color = colors.cautionText)
            }
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, { onAction(CalendarAction.Retry) }) } }
        state.emptyMessage?.let { message ->
            item(key = "empty") {
                StockEmptyState(message, actionText = if (state.needsSignIn) "Sign in" else null, onAction = if (state.needsSignIn) ({ onAction(CalendarAction.SignIn) }) else null)
            }
        }
        state.days.forEach { (date, rows) ->
            item(key = "day-$date") {
                Text(EarningsFormatter.date(date), Modifier.padding(top = spacing.sm).semantics { heading(); contentDescription = EarningsFormatter.spokenDate(date) },
                    style = typography.label, color = colors.textSecondary)
            }
            items(rows, key = { it.id }) { row -> EventCard(row, onResults = row.reportId?.let { id -> { onAction(CalendarAction.OpenResults(id)) } }) { onAction(CalendarAction.Open(row.id)) } }
        }
        if (state.loadingMore) item(key = "more") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading more earnings" }) }
        item(key = "notes") {
            Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                Text("Not investment advice. Report dates can change until a company confirms them.", style = typography.caption, color = colors.textTertiary)
            }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("What are earnings?") }, text = { Text(EarningsCalendarRules.WHAT_ARE_EARNINGS) },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } })
    if (picking) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = epochMillis(state.selection.date))
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { onAction(CalendarAction.SelectDate(dateOf(it))) }; picking = false }) { Text("Show date") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable
private fun WeekStrip(state: EarningsCalendarState, onAction: (CalendarAction) -> Unit, onPick: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(CalendarAction.PreviousWeek) }) { Icon(StockIcons.ChevronRight, "Previous week", Modifier.rotate(180f), tint = colors.iconPrimary) }
            Text(state.weekLabel, Modifier.weight(1f).semantics { heading(); liveRegion = LiveRegionMode.Polite; contentDescription = "Week of ${state.weekLabel}" },
                style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary, textAlign = TextAlign.Center)
            IconButton(onClick = { onAction(CalendarAction.NextWeek) }) { Icon(StockIcons.ChevronRight, "Next week", tint = colors.iconPrimary) }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            state.week.forEach { day -> DayCell(day, Modifier.weight(1f)) { onAction(CalendarAction.SelectDate(day.date)) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockButton("Today", onClick = { onAction(CalendarAction.Today) }, variant = StockButtonVariant.TEXT, enabled = state.selection.date != state.today)
            StockButton("Choose date", onClick = onPick, variant = StockButtonVariant.TEXT)
        }
    }
}

/** Selected: filled and underlined (not color alone); today: outlined; counts are numbers. */
@Composable
private fun DayCell(day: WeekDayView, modifier: Modifier, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val shape = StockStepsTheme.shapes.chip
    val text = if (day.selected) colors.onPrimary else colors.textPrimary
    Column(modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(shape)
        .background(if (day.selected) colors.primaryDark else colors.surfaceSecondary)
        .then(if (day.today && !day.selected) Modifier.border(StockStepsTheme.dimensions.border, colors.primary, shape) else Modifier)
        .selectable(selected = day.selected, role = Role.Tab, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = day.accessibility }
        .padding(vertical = StockStepsTheme.spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.weekday, style = typography.tiny, color = if (day.selected) colors.onPrimary else colors.textSecondary, maxLines = 1)
        Text(day.day, style = typography.bodySemiBold, color = text, maxLines = 1)
        Text(day.count?.takeIf { it > 0 }?.toString() ?: " ", style = typography.tiny, color = if (day.selected) colors.onPrimary else colors.primaryText, maxLines = 1)
        Box(Modifier.width(StockStepsTheme.spacing.lg).height(StockStepsTheme.dimensions.border * 2).background(if (day.selected) colors.onPrimary else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun EventCard(row: EarningsEventRow, onResults: (() -> Unit)?, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = row.accessibility }, onClick = onClick, onClickLabel = "Open ${row.symbol} earnings event") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(row.symbol, logoUrl = row.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f)) {
                Text(row.name, style = typography.bodySemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(row.symbol, row.exchange).joinToString(" · "), style = typography.caption, color = colors.textSecondary)
            }
            Badge(row.status.label)
        }
        Text("${row.dateText} · ${row.timingText}", style = typography.caption, color = colors.textSecondary)
        if (row.status == EarningsEventStatus.SCHEDULED) Text(row.statusText.substringAfter(" · "), style = typography.caption, color = colors.textSecondary)
        row.previousDate?.let { Text("Date moved from ${EarningsFormatter.date(it)}", style = typography.caption, color = colors.cautionText) }
        if (row.watchlisted) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Icon(StockIcons.Star, null, Modifier.size(StockStepsTheme.dimensions.iconSmall), tint = colors.primary)
            Text("On your watchlist", style = typography.caption, color = colors.textSecondary)
        }
        // Only verified reported events with a published report link to results.
        onResults?.let { StockButton("View Results", onClick = it, variant = StockButtonVariant.TEXT) }
    }
}

// ---------- Earnings Event Details ----------

internal sealed interface EventAction {
    data object Company : EventAction
    data object Calendar : EventAction
    data object Results : EventAction
    data object Retry : EventAction
    data object SignIn : EventAction
    data object ToggleWatchlist : EventAction
}

/** One event: date, timing, status and provenance, a beginner explanation, and existing actions. No figures. */
@Composable
internal fun EarningsEventScreen(state: EarningsEventState, watched: Boolean, watchlistEnabled: Boolean, modifier: Modifier, onAction: (EventAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EarningsEducation.Topic?>(null) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings event" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, { onAction(EventAction.Retry) }) } }
        val status = state.status
        if (status != null) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockTickerAvatar(state.symbol, logoUrl = state.logoUrl, size = StockStepsTheme.dimensions.logo)
                        Column(Modifier.weight(1f)) {
                            Text(state.name, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(state.symbol, state.exchange, state.period).joinToString(" · "), style = typography.small, color = colors.textSecondary)
                        }
                    }
                    if (state.sampleData) StockSampleDataBanner("Sample earnings data for development, not real announcements.")
                    state.freshnessText?.let { Text(it, style = typography.caption, color = colors.cautionText) }
                }
            }
            item(key = "event") {
                StockCard(Modifier.fillMaxWidth()) {
                    Text("Earnings report", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                    Line(MetricLine("Date", state.dateText.orEmpty()))
                    Line(MetricLine("Expected timing", state.timingText.orEmpty()))
                    Line(MetricLine("Status", state.statusText.orEmpty()))
                    state.previousDate?.let { Text("Date moved from ${EarningsFormatter.date(it)}", style = typography.caption, color = colors.cautionText) }
                    state.statusExplanation?.let { Text(it, style = typography.small, color = colors.textBody) }
                    // Only a verified report links to Earnings Results; never a placeholder.
                    if (state.reportId != null) StockButton("View Results", onClick = { onAction(EventAction.Results) }, variant = StockButtonVariant.OUTLINED)
                }
            }
            item(key = "explain") {
                StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false) {
                    Text("What is an earnings report?", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                    Text(EarningsCalendarRules.EVENT_EXPLANATION, style = typography.body, color = colors.textBody)
                }
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockButton("View Company Details", onClick = { onAction(EventAction.Company) }, modifier = Modifier.fillMaxWidth())
                    if (watchlistEnabled) StockButton(if (watched) "Remove from Watchlist" else "Add to Watchlist", onClick = { onAction(EventAction.ToggleWatchlist) },
                        modifier = Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED, icon = if (watched) StockIcons.Star else StockIcons.StarOutline)
                    else StockButton("Sign in to use watchlists", onClick = { onAction(EventAction.SignIn) }, modifier = Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
                    StockButton("Learn About Earnings", onClick = { education = EarningsEducation.topic("quarterly") }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
                    StockButton("View in Earnings Calendar", onClick = { onAction(EventAction.Calendar) }, variant = StockButtonVariant.TEXT)
                }
            }
            item(key = "source") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    listOfNotNull(state.updatedText, state.sourceText).forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                    state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                    Text("Not investment advice. Earnings dates don't predict how a stock will move.", style = typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
    education?.let { topic -> EducationDialog(topic) { education = null } }
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

// ---------- Earnings Results (Phase 2) ----------

internal sealed interface ResultsAction {
    data object Retry : ResultsAction
    data class Toggle(val key: String) : ResultsAction
    data object Company : ResultsAction
    data object Learn : ResultsAction
    data class Window(val window: ReactionWindow) : ResultsAction
    data object RetryReaction : ResultsAction
    data object ToggleChart : ResultsAction
}

/**
 * Earnings Results: header → EPS → revenue → growth → previous quarter → takeaway → learn → sources.
 * Every number and sentence comes from the server's calculations via the presenter; this only renders.
 */
@Composable
internal fun EarningsResultsScreen(state: EarningsResultsState, modifier: Modifier, reaction: EarningsPriceReactionState? = null, onAction: (ResultsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var lesson by remember { mutableStateOf<LearnLink?>(null) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (state.loading || state.refreshing) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings results" }) }
        if (state.notPublished) item(key = "unavailable") { EarningsUnavailableState(state.error ?: "Results for this period haven't been published yet.", onCompany = { onAction(ResultsAction.Company) }) }
        else state.error?.let { item(key = "error") { StockErrorState(it, { onAction(ResultsAction.Retry) }) } }
        if (state.response != null) {
            item(key = "header") { EarningsResultHeader(state) }
            state.eps?.let { item(key = "eps") { FinancialComparisonCard(it, it.key in state.expanded) { onAction(ResultsAction.Toggle(it.key)) } } }
            state.revenue?.let { item(key = "revenue") { FinancialComparisonCard(it, it.key in state.expanded) { onAction(ResultsAction.Toggle(it.key)) } } }
            state.yearOverYear?.let { item(key = "yoy") { RevenueGrowthCard(it) } }
            state.quarterOverQuarter?.let { item(key = "qoq") { RevenueGrowthCard(it) } }
            state.takeaway?.let { takeaway -> item(key = "takeaway") { EarningsTakeawayCard(takeaway, state.warnings) } }
            reaction?.let { r -> item(key = "reaction") { PriceReactionSection(r, onAction) { lesson = it } } }
            item(key = "learn") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text("Learn More", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                    state.learn.forEach { link ->
                        // An existing lesson opens in place; a missing one falls back to the Learn tab (never a broken link).
                        StockButton(link.title, onClick = { if (link.body != null) lesson = link else onAction(ResultsAction.Learn) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
                    }
                    StockButton("View Company Details", onClick = { onAction(ResultsAction.Company) }, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "sources") { EarningsSourceFooter(state.sources) }
        }
    }
    lesson?.let { l -> AlertDialog(onDismissRequest = { lesson = null }, title = { Text(l.title) }, text = { Text(l.body.orEmpty()) },
        confirmButton = { TextButton(onClick = { lesson = null }) { Text("Got it") } }, dismissButton = { TextButton(onClick = { lesson = null; onAction(ResultsAction.Learn) }) { Text("Open Learn") } }) }
}

@Composable
private fun EarningsResultHeader(state: EarningsResultsState) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(state.symbolLine.substringBefore(" ·"), logoUrl = state.logoUrl, size = StockStepsTheme.dimensions.logo)
            Column(Modifier.weight(1f)) {
                Text(state.companyName, style = typography.bodySemiBold, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.symbolLine, style = typography.caption, color = colors.textSecondary)
            }
        }
        Text(state.title, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
        state.periodLines.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
        if (state.sampleData) StockSampleDataBanner("Sample earnings data for development, not real results.")
        state.freshnessText?.let { Text(it, style = typography.caption, color = colors.cautionText) }
    }
}

/** One card for EPS and revenue: numbers, classification in words, explanation, expandable definition. */
@Composable
private fun FinancialComparisonCard(card: ComparisonCardView, expanded: Boolean, onToggle: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = card.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.title, Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                SurpriseIndicator(card.classificationText, card.classification)
            }
            card.lines.forEach { Line(it) }
            card.explanation?.let { Text(it, style = typography.small, color = colors.textBody) }
            card.reason?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
            card.basisNote?.let { Text(it, style = typography.caption, color = colors.textTertiary) }
        }
        FinancialTermInfo(card.infoTitle, card.infoBody, expanded, onToggle)
    }
}

/** The classification as a labelled pill: the word carries the meaning; colour is secondary. */
@Composable
private fun SurpriseIndicator(text: String, classification: Classification) {
    val colors = StockStepsTheme.colors
    val (background, content) = when (classification) {
        Classification.BEAT -> colors.positiveContainer to colors.positiveText
        Classification.MISS -> colors.negativeContainer to colors.negativeText
        Classification.MET -> colors.surfaceSecondary to colors.textPrimary
        Classification.UNAVAILABLE -> colors.surfaceSecondary to colors.textSecondary
    }
    Text(text, Modifier.clip(StockStepsTheme.shapes.pill).background(background).padding(horizontal = StockStepsTheme.spacing.sm, vertical = StockStepsTheme.spacing.xxs),
        style = StockStepsTheme.typography.label, color = content)
}

@Composable
private fun FinancialTermInfo(title: String, body: String, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(onClickLabel = if (expanded) "Hide explanation" else "Show explanation", onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }, verticalAlignment = Alignment.CenterVertically) {
            Icon(StockIcons.Info, null, Modifier.size(StockStepsTheme.dimensions.iconSmall), tint = StockStepsTheme.colors.primary)
            Text(title, Modifier.padding(start = StockStepsTheme.spacing.xs).weight(1f), style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.primaryText)
            Icon(StockIcons.ChevronRight, null, Modifier.rotate(if (expanded) 90f else 0f), tint = StockStepsTheme.colors.iconSecondary)
        }
        if (expanded) Text(body, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    }
}

@Composable
private fun RevenueGrowthCard(card: GrowthCardView) {
    val colors = StockStepsTheme.colors
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = card.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(card.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        card.lines.forEach { Line(it) }
        Text(card.explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
        card.reason?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
    }
}

@Composable
private fun EarningsTakeawayCard(takeaway: String, warnings: List<String>) {
    val colors = StockStepsTheme.colors
    StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Beginner Takeaway", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        Text(takeaway, style = StockStepsTheme.typography.body, color = colors.textBody)
        warnings.forEach { Text(it, style = StockStepsTheme.typography.caption, color = colors.cautionText) }
        Text("This is education, not investment advice.", style = StockStepsTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun EarningsSourceFooter(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text("Sources", style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary)
        lines.forEach { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
    }
}

@Composable
private fun EarningsUnavailableState(message: String, onCompany: () -> Unit) {
    StockEmptyState(message, actionText = "View Company Details", onAction = onCompany)
}

// ---------- Post-earnings price reaction (Phase 3) ----------

/**
 * "How Did the Stock React?": window choice, before/after closes, change, measurement, Phase 2
 * context, a non-causal explanation and a daily-close chart with the earnings marker. Unavailable
 * (honest status) and failed (retryable) are shown differently.
 */
@Composable
private fun PriceReactionSection(state: EarningsPriceReactionState, onAction: (ResultsAction) -> Unit, onLesson: (LearnLink) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("How Did the Stock React?", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            ReactionWindow.entries.forEach { w ->
                val pending = state.windows.firstOrNull { it.window == w }?.status == ReactionStatus.WINDOW_INCOMPLETE
                StockChip(w.label + if (pending) " · not yet" else "", state.window == w, onClick = { onAction(ResultsAction.Window(w)) })
            }
        }
        if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading price reaction" })
        state.error?.let { StockErrorState(it, { onAction(ResultsAction.RetryReaction) }) }
        state.contextLine?.let { Text(it, style = typography.label, color = colors.textPrimary) }
        if (state.lines.isNotEmpty()) Column(Modifier.semantics(mergeDescendants = true) { contentDescription = state.accessibility }) {
            state.lines.forEach { Line(it) }
        }
        state.statusText?.let { Text(it, style = typography.small, color = colors.textSecondary) }
        state.summary?.let { Text(it, style = typography.small, color = colors.textBody) }
        state.chart?.let { chart ->
            StockTrendChart(chart.values, chart.details, chart.xLabels, chart.yLabels, chart.description,
                markers = listOf(chart.eventIndex).filter { it >= 0 }, highlights = listOf(chart.baselineIndex, chart.endpointIndex).filter { it >= 0 },
                height = if (state.expandedChart) 240.dp else 150.dp)
            Text("Dashed line: ${chart.eventLabel}. Rings: baseline and endpoint closes. Daily closes only, so the marker shows the session, not the exact time.",
                style = typography.caption, color = colors.textSecondary)
            chart.missingNote?.let { Text(it, style = typography.caption, color = colors.cautionText) }
            StockButton(if (state.expandedChart) "Smaller chart" else "Larger chart", onClick = { onAction(ResultsAction.ToggleChart) }, variant = StockButtonVariant.TEXT)
        }
        state.explanation?.let { Text(it, style = typography.body, color = colors.textBody) }
        state.mixedNote?.let { Text(it, style = typography.small, color = colors.textSecondary) }
        state.warnings.forEach { Text(it, style = typography.caption, color = colors.cautionText) }
        if (state.stale) Text("Showing saved price data; the price provider isn't responding.", style = typography.caption, color = colors.cautionText)
        state.learn.forEach { link -> StockButton(link.title, onClick = { onLesson(link) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb) }
        state.sources.forEach { Text("Prices: $it · regular-session closes, split-adjusted", style = typography.caption, color = colors.textTertiary) }
        Text("Price moves have many causes. This shows what happened, not why, and isn't investment advice.", style = typography.caption, color = colors.textTertiary)
    }
}
