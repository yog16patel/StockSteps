package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.earnings.*
import org.example.stocksteps.presentation.watchlist.NotificationAccess

/** Settings → Notifications → Earnings Reminders (also from the Watchlist shortcut). */
@Serializable internal data object EarningsRemindersRoute

/** Platform notification permission for earnings screens (provided by the app shell; null in previews). */
internal val LocalNotificationAccess = staticCompositionLocalOf<NotificationAccess?> { null }

/** What a reminder sheet is about: one upcoming event. */
internal data class ReminderTarget(val eventId: String, val symbol: String, val name: String, val dateText: String, val timingText: String?, val dateNote: String?)

/** Reminder control: words carry the state (not only the icon's colour). */
@Composable
internal fun ReminderButton(control: ReminderControl, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val label = when (control) {
        ReminderControl.OFF -> if (compact) "Remind" else "Remind Me"
        ReminderControl.ON -> "Reminder On"
        ReminderControl.SAVING -> "Saving…"
        ReminderControl.FAILED -> "Couldn't save · Retry"
    }
    StockButton(label, onClick = onClick, modifier = modifier.semantics { stateDescription = label },
        variant = if (control == ReminderControl.ON) StockButtonVariant.SECONDARY else if (compact) StockButtonVariant.TEXT else StockButtonVariant.OUTLINED,
        enabled = control != ReminderControl.SAVING, icon = StockIcons.Bell)
}

/** Shows the reminder sheet for [target] when non-null; all writes go through the shared presenter. */
@Composable
internal fun ReminderSheetHost(accounts: AccountDependencies?, target: ReminderTarget?, onDismiss: () -> Unit, onSignIn: () -> Unit) {
    target ?: return
    val presenter = accounts?.earningsReminders
    val state by (presenter?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(EarningsRemindersState()) }).collectAsStateWithLifecycle()
    val access = LocalNotificationAccess.current
    LaunchedEffect(access) { access?.let { presenter?.setPermission(it.enabled) } }
    if (presenter == null || !state.signedIn) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Remind Me About Earnings") }, text = { Text("Sign in to save earnings reminders. They're free.") },
            confirmButton = { TextButton(onClick = { onDismiss(); onSignIn() }) { Text("Sign in") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } })
        return
    }
    EarningsReminderSheet(target, state, onSave = { offset, results -> presenter.remind(target.eventId, offset, results); onDismiss() },
        onTurnOff = { presenter.turnOff(target.eventId); onDismiss() }, onDismiss = onDismiss)
}

/** "Remind Me About Earnings": event date vs notification date, timing (1/3/7 days), results option, permission state. */
@Composable
internal fun EarningsReminderSheet(target: ReminderTarget, state: EarningsRemindersState, onSave: (Int, Boolean) -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val existing = state.reminderFor(target.eventId)
    val prefs = state.preferences
    var offset by remember { mutableStateOf(existing?.offsetDays ?: prefs.defaultOffsetDays) }
    var results by remember { mutableStateOf(existing?.results ?: true) }
    val access = LocalNotificationAccess.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(access?.enabled) }
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Remind Me About Earnings", Modifier.semantics { heading() }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("${target.name} (${target.symbol})", style = typography.bodySemiBold, color = colors.textPrimary)
            Text("Earnings date: ${target.dateText}" + (target.dateNote?.let { " · $it" } ?: ""), style = typography.small, color = colors.textBody)
            Text("Reporting time: ${target.timingText ?: "Not confirmed"}", style = typography.small, color = colors.textBody)
            Text("Notify me", style = typography.label, color = colors.textSecondary)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                ReminderPolicy.OFFSETS.forEach { d -> StockChip(if (d == 1) "1 day before" else "$d days before", offset == d, onClick = { offset = d }) }
            }
            Text("Delivered around ${clock(prefs.deliveryTime)} your time (${prefs.timeZone}) on that day. Push services can delay delivery, so it isn't exact.",
                style = typography.caption, color = colors.textSecondary)
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).toggleable(results, role = Role.Switch) { results = it },
                verticalAlignment = Alignment.CenterVertically) {
                Text("Also tell me when results are published", Modifier.weight(1f), style = typography.body)
                Switch(results, null)
            }
            state.deliveryWarning?.let { Text(it, style = typography.caption, color = colors.cautionText) }
            if (allowed == false && access != null) StockButton("Allow notifications", onClick = { scope.launch { allowed = access.request() } }, variant = StockButtonVariant.TEXT)
            Text("A reminder is a heads-up, not a signal to buy or sell.", style = typography.caption, color = colors.textTertiary)
        }
    }, confirmButton = { TextButton(onClick = { onSave(offset, results) }) { Text(if (existing != null) "Save" else "Remind Me") } },
        dismissButton = { Row {
            if (existing != null) TextButton(onClick = onTurnOff) { Text("Turn off") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}

internal fun clock(hhmm: String): String {
    val (h, m) = hhmm.split(':').map { it.toIntOrNull() ?: 0 }
    return "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}

/** Earnings Reminders settings: preferences, permission, reminders with status, scheduled notifications. */
@Composable
internal fun EarningsRemindersScene(accounts: AccountDependencies?, onSignIn: () -> Unit, onOpenEvent: (String) -> Unit) {
    val presenter = accounts?.earningsReminders
    val state by (presenter?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(EarningsRemindersState()) }).collectAsStateWithLifecycle()
    val access = LocalNotificationAccess.current
    LaunchedEffect(Unit) { access?.let { presenter?.setPermission(it.enabled) }; presenter?.refresh() }
    EarningsRemindersScreen(state, Modifier.fillMaxSize(), access, onSignIn, onOpenEvent,
        onPreferences = { t -> presenter?.updatePreferences(t) }, onDelete = { presenter?.delete(it) }, onRefresh = { presenter?.refresh() },
        onPermission = { presenter?.setPermission(it) })
}

@Composable
internal fun EarningsRemindersScreen(
    state: EarningsRemindersState, modifier: Modifier, access: NotificationAccess?, onSignIn: () -> Unit, onOpenEvent: (String) -> Unit,
    onPreferences: ((EarningsReminderPreferences) -> EarningsReminderPreferences) -> Unit, onDelete: (String) -> Unit, onRefresh: () -> Unit, onPermission: (Boolean) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val scope = rememberCoroutineScope()
    val p = state.preferences
    @Composable fun Toggle(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = typography.body, color = if (enabled) colors.textPrimary else colors.textDisabled)
                subtitle?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
            }
            Switch(checked, null, enabled = enabled)
        }
    }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Earnings Reminders", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("Get a heads-up before companies report, and when results are out. Free.", style = typography.small, color = colors.textSecondary)
                if (state.response?.sampleData == true) StockSampleDataBanner("Sample mode: notifications are simulated, nothing is sent through Firebase.")
            }
        }
        if (!state.signedIn) { item(key = "signin") { StockEmptyState("Sign in to set up earnings reminders.", actionText = "Sign in", onAction = onSignIn) }; return@LazyColumn }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading reminders" }) }
        state.message?.let { item(key = "message") { StockErrorState(it, onRefresh) } }
        item(key = "permission") {
            StockCard(Modifier.fillMaxWidth()) {
                Text("Device notifications", style = typography.cardTitle, color = colors.textPrimary)
                Text(when (state.permissionGranted) {
                    true -> "Allowed for StockSteps on this device."
                    false -> "Off for StockSteps on this device. Reminders are saved, but nothing will appear until you allow notifications."
                    null -> "Couldn't check this device's notification setting."
                }, style = typography.small, color = if (state.permissionGranted == false) colors.cautionText else colors.textBody)
                if (state.permissionGranted != true && access != null) StockButton("Allow notifications", onClick = { scope.launch { onPermission(access.request()) } }, variant = StockButtonVariant.TEXT)
            }
        }
        item(key = "prefs") {
            StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Toggle("Earnings notifications", "Turns every earnings reminder on or off. Your reminders are kept.", p.enabled) { v -> onPreferences { it.copy(enabled = v) } }
                Toggle("Automatically remind me about earnings for companies in my watchlist.", "Each company once, even if it's on several lists. Off unless you turn it on.",
                    p.watchlistAuto, p.enabled) { v -> onPreferences { it.copy(watchlistAuto = v) } }
                Text("Default timing", style = typography.label, color = colors.textSecondary)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReminderPolicy.OFFSETS.forEach { d -> StockChip(if (d == 1) "1 day before" else "$d days before", p.defaultOffsetDays == d, onClick = { onPreferences { it.copy(defaultOffsetDays = d) } }) }
                }
                Toggle("Results available", "When verified results are published (not just because the date passed).", p.resultsAvailable, p.enabled) { v -> onPreferences { it.copy(resultsAvailable = v) } }
                Toggle("Date changes", "If the data source moves an expected date.", p.dateChanges, p.enabled) { v -> onPreferences { it.copy(dateChanges = v) } }
                Toggle("Canceled reports", "If the data source says a report was canceled.", p.cancellations, p.enabled) { v -> onPreferences { it.copy(cancellations = v) } }
                Text("Delivery time", style = typography.label, color = colors.textSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    listOf("07:00", "08:00", "09:00", "12:00", "18:00").forEach { t -> StockChip(clock(t), p.deliveryTime == t, onClick = { onPreferences { it.copy(deliveryTime = t) } }) }
                }
                Toggle("Quiet hours (10:00 PM – 7:00 AM)", "Anything due then waits until 7:00 AM.", p.quietStart != null) { v ->
                    onPreferences { if (v) it.copy(quietStart = "22:00", quietEnd = "07:00") else it.copy(quietStart = null, quietEnd = null) } }
                Text("Times use your device's time zone (${p.timeZone}) and follow you when you travel. Earnings dates are the exchange's local date.",
                    style = typography.caption, color = colors.textSecondary)
            }
        }
        val reminders = state.response?.reminders.orEmpty()
        item(key = "list-title") { Text("Your reminders", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) }
        if (reminders.isEmpty()) item(key = "none") { Text("No reminders yet. Tap \"Remind Me\" on an upcoming earnings event.", style = typography.small, color = colors.textSecondary) }
        items(reminders, key = { it.reminderId }) { r ->
            StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, onClick = r.earningsEventId?.let { id -> { onOpenEvent(id) } }) {
                Text("${r.companyName ?: r.instrumentId} (${r.instrumentId})", style = typography.bodySemiBold, color = colors.textPrimary)
                Text(listOfNotNull(if (r.source == ReminderSource.WATCHLIST_AUTO) "From your watchlist" else if (r.earningsEventId == null) "Every upcoming report" else null,
                    if (r.offsetDays == 1) "1 day before" else "${r.offsetDays} days before", r.eventDate?.let { "Earnings ${EarningsFormatter.date(it)}" }).joinToString(" · "),
                    style = typography.caption, color = colors.textSecondary)
                Text(r.statusMessage ?: r.nextDeliveryText?.let { "Notification: $it" } ?: r.scheduleStatus.label, style = typography.small, color = colors.textBody)
                if (r.source == ReminderSource.MANUAL) StockButton("Remove", onClick = { onDelete(r.reminderId) }, variant = StockButtonVariant.TEXT,
                    enabled = r.reminderId !in state.saving)
            }
        }
        val notes = state.response?.notifications.orEmpty()
        if (notes.isNotEmpty()) {
            item(key = "notes-title") { Text("Scheduled and sent", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textPrimary) }
            items(notes, key = { it.idempotencyKey }) { n ->
                Column(Modifier.semantics(mergeDescendants = true) { }) {
                    Text("${n.type.label} · ${n.instrumentId} · ${n.status.name.lowercase().replaceFirstChar { it.uppercase() }}", style = typography.label, color = colors.textPrimary)
                    Text("${EarningsFormatter.date(n.scheduledFor.take(10))}, ${n.scheduledFor.drop(11).take(5)} UTC" + (n.failureReason?.let { " · $it" } ?: ""),
                        style = typography.caption, color = colors.textSecondary)
                }
            }
            item(key = "notes-help") { Text("\"Submitted\" means the push service accepted it; it doesn't confirm the device showed it.", style = typography.caption, color = colors.textTertiary) }
        }
    }
}
