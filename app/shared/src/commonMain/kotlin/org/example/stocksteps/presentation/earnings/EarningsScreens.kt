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
    /** Opens the reminder sheet for an upcoming event. */
    data class Remind(val row: EarningsEventRow) : CalendarAction
    data object LoadMore : CalendarAction
    data object Retry : CalendarAction
    data object SignIn : CalendarAction
    data class Scenario(val name: String?) : CalendarAction
    /** Phase 5: "Your Earnings Digest" (StockSteps+) and the free watchlist shortcut. */
    data object Digest : CalendarAction
    data object RecentWatchlist : CalendarAction
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
internal fun EarningsCalendarScreen(state: EarningsCalendarState, modifier: Modifier, reminder: (String) -> ReminderControl? = { null }, showDigest: Boolean = false, onAction: (CalendarAction) -> Unit) {
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
        if (showDigest) item(key = "digest") {
            StockCard(Modifier.fillMaxWidth(), onClick = { onAction(CalendarAction.Digest) }, onClickLabel = "Open your earnings digest", verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your Earnings Digest", Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                    PlusBadge()
                }
                Text("What happened this week with the companies on your watchlists.", style = typography.small, color = colors.textSecondary)
                StockButton("Recent Earnings From Your Watchlist", onClick = { onAction(CalendarAction.RecentWatchlist) }, variant = StockButtonVariant.TEXT)
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
            items(rows, key = { it.id }) { row ->
                EventCard(row, onResults = row.reportId?.let { id -> { onAction(CalendarAction.OpenResults(id)) } },
                    reminder = reminder(row.id).takeIf { row.status == EarningsEventStatus.SCHEDULED }, onRemind = { onAction(CalendarAction.Remind(row)) }) { onAction(CalendarAction.Open(row.id)) }
            }
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
private fun EventCard(row: EarningsEventRow, onResults: (() -> Unit)?, reminder: ReminderControl? = null, onRemind: () -> Unit = {}, onClick: () -> Unit) {
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
        reminder?.let { ReminderButton(it, compact = true, onClick = onRemind) }
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
    data object Remind : EventAction
}

/** One event: date, timing, status and provenance, a beginner explanation, and existing actions. No figures. */
@Composable
internal fun EarningsEventScreen(state: EarningsEventState, watched: Boolean, watchlistEnabled: Boolean, modifier: Modifier, reminder: ReminderControl? = null, reminderNote: String? = null,
                                  onAction: (EventAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var education by remember { mutableStateOf<EarningsEducation.Topic?>(null) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings event" }) }
        state.error?.let { item(key = "error") {
            Column {
                StockErrorState(it, { onAction(EventAction.Retry) })
                // A missing event (e.g. from an old notification) falls back to the calendar.
                StockButton("Open Earnings Calendar", onClick = { onAction(EventAction.Calendar) }, variant = StockButtonVariant.TEXT)
            }
        } }
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
                    EarningsLines(listOf(MetricLine("Date", state.dateText.orEmpty()), MetricLine("Expected timing", state.timingText.orEmpty()),
                        MetricLine("Status", state.statusText.orEmpty())))
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
                    if (status == EarningsEventStatus.SCHEDULED && reminder != null) {
                        ReminderButton(reminder, Modifier.fillMaxWidth()) { onAction(EventAction.Remind) }
                        reminderNote?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                    }
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
    // E-07: the shared status badge (colours and the no-wrap rule from `StockSemanticStyles` / `StockBadgeLayout`).
    StockStatusBadge(text, org.example.stocksteps.theme.StockBadgeKind.NEUTRAL, size = StockBadgeSize.COMPACT)
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
    onRemind: () -> Unit,
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
    var question by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        // Phase 5D.2 hierarchy: company → status and dates → the quarter's answer (summary + EPS/revenue) → reaction → insights → history.
        item(key = "header") { DetailsHeader(state, onRemind, onCompany) }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading earnings" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, onRetry) } }
        d?.summary?.let { summary ->
            item(key = "summary") {
                // 5D.3 takeaway: quarter, the EPS headline from the server's classification (the server summary when there is none), badge,
                // reported vs estimated EPS, one explanation, one lesson.
                StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    state.headerPeriod?.let { Text(it, style = typography.label, color = colors.primaryText) }
                    StockTitleWithBadge(title = { Text(state.headline ?: summary, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) },
                        badge = { state.eps?.let { ClassificationBadge(it.classification) } })
                    // 5D4-3: the EPS card merged here — Reported · Estimate · Surprise, then the rest of the EPS lines folded (nothing removed).
                    val eps = state.eps
                    if (eps != null && eps.keyMetrics.isNotEmpty()) EarningsKeyMetrics(eps.keyMetrics, Modifier.semantics(mergeDescendants = true) { contentDescription = eps.accessibility })
                    else EarningsKeyMetrics(EarningsDisplay.takeawayMetrics(eps))
                    EarningsDisplay.visibleCaveat(eps?.note)?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                    // 5D.4.1: one "Show details" (rest of the explanation, Difference/Basis, the note, the lesson) instead of three controls.
                    // 5D.4.2: "Learn more" shares the toggle's row (wraps under it at large text) instead of a second 48 dp row.
                    val learnMore: (@Composable () -> Unit)? = EarningsEducation.topic("eps")?.let { topic -> { StockButton("Learn more", onClick = { education = topic }, variant = StockButtonVariant.TEXT, icon = StockIcons.ArrowForward) } }
                    EarningsShowDetails(d.summaryExplanation, hasMore = eps != null && (eps.secondary.isNotEmpty() || EarningsDisplay.hiddenNote(eps.note) != null), trailing = learnMore) {
                        if (eps != null) {
                            EarningsLines(eps.secondary)
                            EarningsDisplay.hiddenNote(eps.note)?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                            EarningsDisplay.cardTopic(eps.title)?.let { topic -> StockButton(topic.title, onClick = { education = topic }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb) }
                        }
                    }
                }
            }
        }
        // 5D4-3: with a takeaway card, EPS lives there; the separate EPS card only remains without one.
        listOfNotNull(state.eps.takeIf { d?.summary == null }, state.revenue).forEach { card ->
            item(key = "card-${card.title}") { ResultCardView(card) { education = EarningsDisplay.cardTopic(card.title) } }
        }
        val upcoming = d?.next
        if (d != null && state.eps == null && upcoming != null) item(key = "upcoming") {
            StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Not reported yet", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                // 5D.1 (E-02): the shared sentence names only the estimates that exist.
                state.nextEstimate?.let { Text(it, style = typography.small, color = colors.textBody) }
            }
        }
        if (state.reaction.isNotEmpty() || state.reactionSentence != null) item(key = "reaction") {
            // 5D.3: the measured change first, then Before/After, the non-causal sentence; window, source and method folded below.
            StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                // 5D.4.2: the measured change on its own line under the title.
                Text(EarningsDisplay.REACTION_TITLE, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                state.reactionHeadline?.let { EarningsReactionHeadline(it, state.reactionDirection) }
                EarningsLines(state.reactionKey.ifEmpty { state.reaction.takeIf { state.reactionHeadline == null }.orEmpty() })
                state.reactionSentence?.let { Text(it, style = typography.small, color = colors.textBody) }
                if (state.reactionDetails.isNotEmpty()) EarningsDisclosure(EarningsDisplay.REACTION_DETAILS_TITLE) {
                    EarningsLines(state.reactionDetails)
                    EarningsDisplay.topicAction("reaction")?.let { label ->
                        StockButton(label, onClick = { education = EarningsEducation.topic("reaction") }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
                    }
                }
            }
        }
        if (d != null && d.insights.isNotEmpty() || (d?.lockedInsights ?: 0) > 0) item(key = "insights") {
            Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Insights", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                // 5D.4.1 (option a): compact rows in one card — icon, period, title; the explanation and lesson behind "Show details".
                if (d!!.insights.isNotEmpty()) StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                    d.insights.forEachIndexed { index, insight ->
                        if (index > 0) HorizontalDivider(color = colors.borderSubtle)
                        // A lesson link only where a lesson exists.
                        val topic = EarningsEducation.topic(insight.methodology)
                        EarningsInsightRow(insight.category, insight.period, insight.title, insight.explanation, topic?.title, topic?.let { t -> { education = t } })
                    }
                }
                if (d.lockedInsights > 0) Text("${d.lockedInsights} more trend insight${if (d.lockedInsights == 1) "" else "s"} with StockSteps+.", style = typography.caption, color = colors.textSecondary)
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "history-title") {
                Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("Earnings history", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                    state.historyNote?.let { Text(it, style = typography.caption, color = colors.textSecondary) } // 5D.1B
                    val reported = state.epsChart.firstOrNull()?.values.orEmpty()
                    if (EarningsDisplay.historyChartShown(reported)) {
                        // 5D.4.2: plot height from the shared width rule (≈ 0.45 × width, 120–200), ≈ 130 dp at 360 dp instead of a fixed 150.
                        BoxWithConstraints(Modifier.fillMaxWidth()) { StockTrendChart(
                            height = org.example.stocksteps.theme.StockChartLayout.height(maxWidth.value).dp,
                            values = reported,
                            details = state.epsChartDetails, // 5D.1 (E-03): with the quarter's currency, like the history rows
                            // 5D.4: every quarter when they fit, and the EPS scale (highest / middle / lowest of the shown values).
                            xLabels = EarningsDisplay.historyXLabels(state.chartLabels, androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale),
                            yLabels = state.epsChartYLabels,
                            description = "Reported EPS versus estimate for ${state.chartLabels.size} quarters.",
                            seriesLabel = "Reported EPS", additional = listOf("Estimate" to state.epsChart[1].values)
                        ) }
                    } else Text(EarningsDisplay.HISTORY_CHART_TOO_SHORT, style = typography.caption, color = colors.textSecondary)
                }
            }
            items(state.history, key = { it.id }) { row -> HistoryRow(row, row.id in expanded) { expanded = if (row.id in expanded) expanded - row.id else expanded + row.id } }
            if (d?.historyLocked == true) item(key = "history-locked") {
                Text("Older quarters are available with StockSteps+.", style = typography.caption, color = colors.textSecondary)
            }
        }
        item(key = "ai") {
            StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockTitleWithBadge(title = { Text("Ask about these earnings", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary) },
                    badge = { if (!state.plus) StockStatusBadge("StockSteps+", org.example.stocksteps.theme.StockBadgeKind.PREMIUM) })
                Text("Answers use only the verified figures above and say when the data can't explain a cause.", style = typography.caption, color = colors.textSecondary)
                StockTextField(question, { question = it.take(500) }, "Your question", Modifier.fillMaxWidth(), placeholder = "e.g. Explain this earnings report", singleLine = false)
                StockButton(if (state.plus) "Ask" else "Ask with StockSteps+", onClick = { onAsk(question) }, modifier = Modifier.fillMaxWidth(),
                    enabled = question.isNotBlank() && !state.asking)
                if (state.asking) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Asking" })
                state.answer?.let { answer ->
                    Text(answer.answer, style = typography.small, color = colors.textBody)
                    if (answer.sources.isNotEmpty()) Text("Sources: " + answer.sources.joinToString(), style = typography.caption, color = colors.textSecondary)
                    answer.remainingToday?.let { Text("$it questions left today.", style = typography.caption, color = colors.textSecondary) }
                }
            }
        }
        item(key = "learn") {
            Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Learn", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                // E-19: the Watchlist/Portfolio grouped navigation rows instead of a column of text buttons (same topics, same dialogs).
                StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = StockStepsTheme.spacing.md)) {
                    EarningsEducation.topics.forEachIndexed { index, topic ->
                        if (index > 0) HorizontalDivider(color = colors.borderSubtle)
                        StockNavigationRow(topic.title, onClick = { education = topic }, actionLabel = "Opens ${topic.title}")
                    }
                }
                // 5D.4: the notes and disclaimer folded like Results (each once; the Sample badge stays in the header).
                if (d != null) EarningsDisclosure(EarningsDisplay.DISCLOSURES_TITLE, icon = StockIcons.Document) {
                    EarningsNotes(EarningsDisplay.detailsDisclosures(d.notes, d.sampleData))
                }
            }
        }
    }
    education?.let { topic -> EducationDialog(topic) { education = null } }
    if (state.upgradeRequired) AlertDialog(onDismissRequest = onDismissUpgrade, title = { Text("StockSteps+") },
        text = { Text("AI earnings research is part of StockSteps+. Nothing was sent.") },
        confirmButton = { TextButton(onClick = onUpgrade) { Text("See StockSteps+") } }, dismissButton = { TextButton(onClick = onDismissUpgrade) { Text("Not now") } })
    state.message?.let { message -> AlertDialog(onDismissRequest = onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } }) }
}

/**
 * Phase 5D.2: one group of label/value rows (a card's lines) with one shared layout decision (`StockLayout.labelColumn`, same rule as SwiftUI).
 * Side by side, labels keep the group's widest label on one line (aligned values) and long values wrap between words in the rest; when that
 * doesn't fit — narrow screens, large text — every row stacks (value under its label). Replaces the 5D.1 `Line`, whose weighted label beside an
 * unweighted value broke "Window" / "Source" into one letter per line when the value was wider than the row. Nothing is truncated.
 */
@Composable
internal fun EarningsLines(lines: List<MetricLine>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    val labelStyle = typography.small
    val valueStyle = typography.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    val gap = StockStepsTheme.spacing.md
    BoxWithConstraints(modifier.fillMaxWidth()) {
        fun width(text: String, style: androidx.compose.ui.text.TextStyle) = with(density) { measurer.measure(text, style).size.width.toDp().value }
        fun longestWord(text: String, style: androidx.compose.ui.text.TextStyle) = text.split(' ').maxOfOrNull { width(it, style) } ?: 0f
        val column = org.example.stocksteps.theme.StockLayout.labelColumn(
            widestLabel = lines.maxOf { width(it.label, labelStyle) },
            longestLabelWord = lines.maxOf { longestWord(it.label, labelStyle) },
            longestValueWord = lines.maxOf { maxOf(longestWord(it.value, valueStyle), it.detail?.let { d -> longestWord(d, typography.caption) } ?: 0f) },
            availableWidth = maxWidth.value, gap = gap.value)
        Column(verticalArrangement = Arrangement.spacedBy(if (column == null) StockStepsTheme.spacing.sm else StockStepsTheme.spacing.xs)) {
            lines.forEach { line ->
                val value: @Composable (TextAlign) -> Unit = { align ->
                    Text(line.value, Modifier.fillMaxWidth(), style = valueStyle, color = colors.textPrimary, textAlign = align)
                    line.detail?.let { Text(it, Modifier.fillMaxWidth(), style = typography.caption, color = colors.textSecondary, textAlign = align) }
                }
                if (column == null) {
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
                        Text(line.label, style = labelStyle, color = colors.textSecondary)
                        value(TextAlign.Start)
                    }
                } else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // +1 dp: the measured width rounds to whole pixels, so the label never wraps by a fraction.
                    Text(line.label, Modifier.width((column + 1f).dp), style = labelStyle, color = colors.textSecondary)
                    Column(Modifier.weight(1f)) { value(TextAlign.End) }
                }
            }
        }
    }
}

/**
 * Details header (Phase 5D.2): logo, the full company name (wraps, never cut), ticker · exchange, the status as a `StockStatusBadge`, the report
 * date and the next report, then one primary and one secondary action (side by side only while both labels fit, `StockActionLayout`).
 */
@Composable
private fun DetailsHeader(state: EarningsDetailsState, onRemind: () -> Unit, onCompany: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val d = state.details
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockTickerAvatar(state.symbol, logoUrl = d?.event?.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                // 5D.4.2 (5D42-2): section size — the bar already titles the screen; long names wrap.
                Text(d?.name ?: state.symbol, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                // The quarter is the summary card's eyebrow when there is one, so it isn't repeated here.
                Text(listOfNotNull(state.symbol, d?.event?.exchange, state.headerPeriod.takeIf { d?.summary == null }).joinToString(" · "),
                    style = typography.small, color = colors.textSecondary)
            }
        }
        // D-1 (owner-approved, 5D.3): status + a compact Sample badge; the full sample sentence stays in the page notes and the app strip.
        if (d != null) Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockStatusBadge(d.status.label, org.example.stocksteps.theme.StockBadgeKind.NEUTRAL)
            if (d.sampleData) StockStatusBadge(EarningsDisplay.SAMPLE_BADGE, org.example.stocksteps.theme.StockBadgeKind.SAMPLE,
                contentDescription = EarningsDisplay.SAMPLE_SENTENCE)
        }
        // 5D.4.1: the reported quarter and the next report on separate labelled lines.
        EarningsDisplay.reportedLine(d?.status, state.headerDate, state.headerSession, state.headerDateStatus)?.let { Text(it, style = typography.small, color = colors.textBody) }
        state.nextLine?.let { line ->
            Column(Modifier.semantics(mergeDescendants = true) { state.next?.let { contentDescription = it } }) {
                Text(line, style = typography.small, color = colors.primaryText)
                state.nextDetail?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
            }
        }
        if (d?.stale == true) StockBanner(org.example.stocksteps.theme.StockBannerKind.WARNING, "The next date hasn't been updated recently and may have changed.")
        val density = androidx.compose.ui.platform.LocalDensity.current
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val remind = d?.next != null
        val labels = listOfNotNull("Remind me".takeIf { remind }, "Company details")
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = spacing.xxs)) {
            val widths = labels.map { with(density) { measurer.measure(it, typography.bodySemiBold).size.width.toDp().value } + (if (it == "Remind me") StockStepsTheme.dimensions.icon.value + spacing.sm.value else 0f) }
            val sideBySide = labels.size > 1 && org.example.stocksteps.theme.StockActionLayout.sideBySide(widths, maxWidth.value, spacing.lg.value, spacing.sm.value,
                accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale)
            val remindButton: @Composable (Modifier) -> Unit = { m -> StockButton("Remind me", onRemind, m, variant = StockButtonVariant.OUTLINED, icon = StockIcons.Bell) }
            val companyButton: @Composable (Modifier) -> Unit = { m -> StockButton("Company details", onCompany, m) }
            if (sideBySide) Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) { remindButton(Modifier.weight(1f)); companyButton(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (remind) remindButton(Modifier.fillMaxWidth())
                companyButton(Modifier.fillMaxWidth())
            }
        }
    }
}

/** One history quarter (Phase 5D.2): period + classification badge, "EPS C$1.94 vs C$2.00 est. · −3.0%" (the chart tooltip's amounts), revenue, expandable details. */
@Composable
private fun HistoryRow(row: HistoryRowView, open: Boolean, onToggle: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
        .clickable(onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle)
        .semantics(mergeDescendants = true) { contentDescription = row.accessibility; stateDescription = if (open) "Expanded" else "Collapsed" }
        .padding(top = StockStepsTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
                StockTitleWithBadge(title = { Text(row.period, style = typography.bodySemiBold, color = colors.textPrimary) }, badge = {
                    val shown = EarningsDisplay.classification(row.classification)
                    StockStatusBadge(shown.label, shown.kind, size = StockBadgeSize.COMPACT, contentDescription = shown.spoken)
                })
                Text(EarningsDisplay.historyEpsLine(row), style = typography.small, color = colors.textBody)
                Text(EarningsDisplay.historyRevenueLine(row), style = typography.caption, color = colors.textSecondary)
            }
            Icon(StockIcons.ChevronRight, null, Modifier.rotate(if (open) 90f else 0f).size(StockStepsTheme.dimensions.icon), tint = colors.iconSecondary)
        }
        if (open) EarningsLines(EarningsDisplay.historyDetailLines(row), Modifier.padding(top = StockStepsTheme.spacing.xs))
        HorizontalDivider(Modifier.padding(top = StockStepsTheme.spacing.xs), color = colors.borderSubtle)
    }
}

/** 5D.1 (E-01): the classification badge shared with Earnings Results (`EarningsDisplay`), so a result reads the same on both screens. */
@Composable
private fun ResultCardView(card: ResultCard, onLearn: () -> Unit) {
    val colors = StockStepsTheme.colors
    StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = card.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            EarningsCardTitle(EarningsDisplay.cardTitle(card.title), card.classification)
            // 5D.3: the three key numbers first; the rest (difference, basis, quarter-over-quarter) smaller below.
            if (card.keyMetrics.isNotEmpty()) EarningsKeyMetrics(card.keyMetrics) else EarningsLines(card.lines)
            EarningsDisplay.visibleCaveat(card.note)?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
        }
        // 5D.4.1: secondary lines, the no-estimate reason and the lesson behind one "Show details" (key numbers stay visible).
        val secondary = if (card.keyMetrics.isNotEmpty()) card.secondary else emptyList()
        EarningsShowDetails(null, hasMore = true) {
            EarningsLines(secondary)
            EarningsDisplay.hiddenNote(card.note)?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
            // The label is the title of the lesson it opens (was "What does this mean?").
            EarningsDisplay.cardTopic(card.title)?.let { topic -> StockButton(topic.title, onClick = onLearn, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb) }
        }
    }
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
internal fun EarningsResultsScreen(state: EarningsResultsState, modifier: Modifier, reaction: EarningsPriceReactionState? = null,
                                   premium: EarningsPremiumState? = null, onPremium: (PremiumAction) -> Unit = {}, onAction: (ResultsAction) -> Unit) {
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
            // 5D.4.1: the quarter's one-line answer right under the header (first sentence; the rest behind "Show details").
            state.takeaway?.let { takeaway -> item(key = "takeaway") { EarningsTakeawayCard(takeaway, state.warnings) } }
            state.eps?.let { item(key = "eps") { FinancialComparisonCard(it, it.key in state.expanded) { onAction(ResultsAction.Toggle(it.key)) } } }
            state.revenue?.let { item(key = "revenue") { FinancialComparisonCard(it, it.key in state.expanded, state.comparisonNotes) { onAction(ResultsAction.Toggle(it.key)) } } }
            // 5D.3: the YoY growth is revenue's third key metric when available; the card only remains for its "not available" reason.
            state.yearOverYear?.takeIf { !state.yearOverYearFolded }?.let { item(key = "yoy") { RevenueGrowthCard(it) } }
            // 5D.4: the previous-quarter comparison is a secondary revenue row when available; the card only remains for its reason.
            state.quarterOverQuarter?.takeIf { !state.quarterOverQuarterFolded }?.let { item(key = "qoq") { RevenueGrowthCard(it) } }
            reaction?.let { r -> item(key = "reaction") { PriceReactionSection(r, onAction) { lesson = it } } }
            // Phase 5 (StockSteps+): after every free section, so nothing free is pushed down or hidden.
            if (premium != null && !state.offline) premiumEarningsItems(premium, onPremium)
            item(key = "learn") {
                Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(EarningsDisplay.LEARN_TITLE, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
                    // 5D.3: grouped navigation rows (same lessons; a missing one still falls back to the Learn tab, never a broken link).
                    StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = spacing.md)) {
                        state.learn.forEachIndexed { index, link ->
                            if (index > 0) HorizontalDivider(color = colors.borderSubtle)
                            StockNavigationRow(link.title, onClick = { if (link.body != null) lesson = link else onAction(ResultsAction.Learn) },
                                icon = StockIcons.Lightbulb, actionLabel = "Opens ${link.title}")
                        }
                    }
                    StockButton(EarningsDisplay.COMPANY_ACTION, onClick = { onAction(ResultsAction.Company) }, variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth())
                }
            }
            // 5D.3: sources, publication time, the sample sentence and the education/price notes, once, in one collapsed section.
            item(key = "sources") {
                EarningsDisclosure(EarningsDisplay.DISCLOSURES_TITLE, icon = StockIcons.Document) {
                    EarningsNotes(EarningsDisplay.resultsDisclosures(state.publication, state.sources, state.sampleData))
                }
            }
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
        // 5D.4: "Q2 FY2026 results" at section size (the bar already says "Earnings results"; the long title wrapped at 1.0×).
        Text(EarningsDisplay.resultsTitle(state.title), Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary)
        state.periodLines.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
        // D-1 (owner-approved, 5D.3): a compact Sample badge; the full sentence is in "Sources and disclosures" and the app strip stays.
        if (state.sampleData) StockStatusBadge(EarningsDisplay.SAMPLE_BADGE, org.example.stocksteps.theme.StockBadgeKind.SAMPLE, contentDescription = EarningsDisplay.SAMPLE_SENTENCE)
        // E-06: data caveats are banners (icon + words, announced), not bare caution captions.
        state.freshnessText?.let { StockBanner(org.example.stocksteps.theme.StockBannerKind.WARNING, it) }
    }
}

/** One card for EPS and revenue: numbers, classification in words, explanation, expandable definition. */
@Composable
private fun FinancialComparisonCard(card: ComparisonCardView, expanded: Boolean, comparisonNotes: List<String> = emptyList(), onToggle: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = card.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            EarningsCardTitle(card.title, card.classification)
            if (card.keyMetrics.isNotEmpty()) EarningsKeyMetrics(card.keyMetrics) else EarningsLines(card.lines)
            EarningsDisplay.visibleCaveat(card.basisNote)?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
        }
        // 5D.4.1: one "Show details", collapsed: rest of the explanation, reason, basis, difference / prior periods, comparison notes.
        val secondary = if (card.keyMetrics.isNotEmpty()) card.secondary else emptyList()
        EarningsShowDetails(card.explanation, hasMore = card.reason != null || card.basisNote != null || secondary.isNotEmpty() || comparisonNotes.isNotEmpty()) {
            card.reason?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
            EarningsDisplay.hiddenNote(card.basisNote)?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
            EarningsLines(secondary)
            EarningsNotes(comparisonNotes)
        }
        FinancialTermInfo(card.infoTitle, card.infoBody, expanded, onToggle)
    }
}

/**
 * 5D.4.2: a result card's title and classification badge. "Comparison unavailable" (NEUTRAL, no estimate) goes below the title in the compact size so
 * it never competes with the title; a Beat / Met / Missed badge stays beside it while both fit (`StockTitleWithBadge`). Rule: `EarningsDisplay.badgeBelowTitle`.
 */
@Composable
private fun EarningsCardTitle(title: String, classification: Classification) {
    val titleText: @Composable () -> Unit = { Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary) }
    if (EarningsDisplay.badgeBelowTitle(classification)) Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        titleText()
        val shown = EarningsDisplay.classification(classification)
        StockStatusBadge(shown.label, shown.kind, size = StockBadgeSize.COMPACT, contentDescription = shown.spoken)
    } else StockTitleWithBadge(title = titleText, badge = { ClassificationBadge(classification) })
}

/** 5D.1 (E-01): the classification as the shared status badge: the words carry the meaning; colour is secondary. */
@Composable
private fun ClassificationBadge(classification: Classification) {
    val shown = EarningsDisplay.classification(classification)
    StockStatusBadge(shown.label, shown.kind, contentDescription = shown.spoken)
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
    StockCard(bordered = false, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = card.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(card.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        EarningsLines(card.lines)
        Text(card.explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
        card.reason?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
    }
}

@Composable
private fun EarningsTakeawayCard(takeaway: String, warnings: List<String>) {
    val colors = StockStepsTheme.colors
    StockCard(Modifier.fillMaxWidth(), containerColor = colors.educationContainer, bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text(EarningsDisplay.TAKEAWAY_TITLE, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        EarningsShowDetails(takeaway, hasMore = false)
        if (warnings.isNotEmpty()) StockBanner(org.example.stocksteps.theme.StockBannerKind.WARNING, warnings.joinToString("\n"))
    }
}

@Composable
private fun EarningsUnavailableState(message: String, onCompany: () -> Unit) {
    StockEmptyState(message, actionText = EarningsDisplay.COMPANY_ACTION, onAction = onCompany)
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
    StockCard(bordered = false, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        // 5D.4.2: the measured change on its own line under the title.
        Text(EarningsDisplay.REACTION_TITLE, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        state.headline?.let { EarningsReactionHeadline(it, state.direction) }
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            ReactionWindow.entries.forEach { w ->
                val pending = state.windows.firstOrNull { it.window == w }?.status == ReactionStatus.WINDOW_INCOMPLETE
                StockChip(w.label + if (pending) " · not yet" else "", state.window == w, onClick = { onAction(ResultsAction.Window(w)) })
            }
        }
        if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading price reaction" })
        state.error?.let { StockErrorState(it, { onAction(ResultsAction.RetryReaction) }) }
        if (state.lines.isNotEmpty()) Column(Modifier.semantics(mergeDescendants = true) { contentDescription = state.accessibility }) {
            EarningsLines(state.keyLines.ifEmpty { state.lines })
        }
        state.statusText?.let { Text(it, style = typography.small, color = colors.textSecondary) }
        state.summary?.let { Text(it, style = typography.small, color = colors.textBody) }
        state.chart?.let { chart ->
            StockTrendChart(chart.values, chart.details, chart.xLabels, chart.yLabels, chart.description,
                markers = listOf(chart.eventIndex).filter { it >= 0 }, highlights = listOf(chart.baselineIndex, chart.endpointIndex).filter { it >= 0 },
                height = if (state.expandedChart) 240.dp else 150.dp)
            chart.missingNote?.let { Text(it, style = typography.caption, color = colors.cautionText) }
            StockButton(if (state.expandedChart) "Smaller chart" else "Larger chart", onClick = { onAction(ResultsAction.ToggleChart) }, variant = StockButtonVariant.TEXT)
        }

        state.mixedNote?.let { Text(it, style = typography.small, color = colors.textSecondary) }
        // E-06: one warning banner for the section's caveats (the words are unchanged).
        val caveats = state.warnings + listOfNotNull("Showing saved price data; the price provider isn't responding.".takeIf { state.stale })
        if (caveats.isNotEmpty()) StockBanner(org.example.stocksteps.theme.StockBannerKind.WARNING, caveats.joinToString("\n"))
        // 5D.3: the measured window, the chart key, the context line, lessons and price sources, folded (nothing removed).
        EarningsDisclosure(EarningsDisplay.RESULTS_REACTION_DETAILS_TITLE) {
            EarningsLines(state.detailLines)
            state.chart?.let { chart -> EarningsNotes(listOf("Dashed line: ${chart.eventLabel}. Rings: baseline and endpoint closes. Daily closes only, so the marker shows the session, not the exact time.")) }
            state.contextLine?.let { EarningsNotes(listOf(it)) }
            // 5D.4 (R4-04): the server's reaction explanation moved here from between the numbers and the chart (unchanged text).
            state.explanation?.let { Text(it, style = typography.small, color = colors.textBody) }
            state.learn.forEach { link -> StockButton(link.title, onClick = { onLesson(link) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb) }
            EarningsNotes(state.sources.map { "Prices: $it · regular-session closes, split-adjusted" })
        }
    }
}
