package org.example.stocksteps.earnings

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import org.example.stocksteps.model.AlertStatus
import org.example.stocksteps.model.AlertType
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.userdata.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

// ---------- Planner (pure, deterministic) ----------

/** A notification the schedule says should exist. */
data class PlannedNotification(
    val key: String,
    val reminderId: String?,
    val type: EarningsNotificationType,
    val event: EarningsEvent,
    val scheduledFor: Instant,
    val title: String,
    val body: String
)

data class ReminderPlan(val notifications: List<PlannedNotification>, val reminders: List<EarningsReminder>, val autoDates: Map<String, String>)

/**
 * Backend-authoritative earnings reminder schedule.
 *
 * - Pre-earnings: the event's exchange-local date − offset calendar days, at the user's delivery time in
 *   their IANA zone, converted to UTC. DST: a time that doesn't exist (spring forward) moves forward by
 *   the gap (02:30 → 03:30); an ambiguous time (fall back) uses the first occurrence (earlier offset).
 * - Quiet hours: a time inside them moves to the end of the quiet period.
 * - Late policy: a pre-earnings reminder is sent up to [LATE_GRACE] after its time, otherwise skipped
 *   (never an outdated "tomorrow" on the day itself). One-off notices (results, date change,
 *   cancellation) are scheduled at detection time (quiet hours respected).
 * - Results: only when the source has reported figures for that fiscal period, once per event, and for
 *   automatic/company reminders only if published within [RESULTS_WINDOW_DAYS] (no old results on opt-in).
 * - Unknown/postponed dates pause reminders (WAITING_FOR_DATE); nothing is invented. Canceled events
 *   (explicit source flag) cancel reminders and optionally notify once.
 * - Deduplication key: user | type | event | offset | event date (pre-earnings) — the same logical
 *   notification from a manual and an automatic reminder, or two watchlists, is one notification;
 *   different offsets stay distinct.
 */
object ReminderPlanner {
    val LATE_GRACE: Duration = Duration.ofHours(2)
    const val RESULTS_WINDOW_DAYS = 3L
    private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val LONG_DAY = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun zone(prefs: EarningsReminderPreferences): ZoneId = runCatching { ZoneId.of(prefs.timeZone) }.getOrDefault(ZoneId.of("America/Toronto"))
    private fun time(text: String?) = text?.takeIf(ReminderPolicy::validTime)?.let(LocalTime::parse)

    /** The UTC instant for a local date and time (DST policy above), moved out of quiet hours. */
    fun localInstant(date: LocalDate, prefs: EarningsReminderPreferences): Instant =
        quiet(ZonedDateTime.ofLocal(date.atTime(time(prefs.deliveryTime) ?: LocalTime.of(9, 0)), zone(prefs), null), prefs).toInstant()

    /** Moves a time that falls inside quiet hours to their end (windows may cross midnight). */
    fun quiet(at: ZonedDateTime, prefs: EarningsReminderPreferences): ZonedDateTime {
        val start = time(prefs.quietStart) ?: return at
        val end = time(prefs.quietEnd) ?: return at
        if (start == end) return at
        val t = at.toLocalTime()
        val inside = if (start < end) t >= start && t < end else t >= start || t < end
        if (!inside) return at
        val endDate = if (start > end && t >= start) at.toLocalDate().plusDays(1) else at.toLocalDate()
        return ZonedDateTime.ofLocal(endDate.atTime(end), at.zone, null)
    }

    fun key(uid: String, type: EarningsNotificationType, eventId: String, extra: String = "") = listOf(uid, type.name, eventId, extra).joinToString("|").trimEnd('|')

    private fun localToday(now: Instant, event: EarningsEvent) =
        now.atZone(runCatching { ZoneId.of(event.timeZone ?: if (event.exchange.equals("TSX", true) || event.symbol.endsWith(".TO", true)) "America/Toronto" else "America/New_York") }
            .getOrDefault(ZoneId.of("America/New_York"))).toLocalDate()

    private fun name(e: EarningsEvent) = e.name.takeIf { it.isNotBlank() } ?: e.symbol

    private fun preText(e: EarningsEvent, date: LocalDate, offset: Int): String {
        val confirmed = e.dateStatus == EarningsDateStatus.CONFIRMED
        return if (confirmed) "${name(e)} is expected to report earnings ${if (offset == 1) "tomorrow" else "in $offset days, on ${LONG_DAY.format(date)}"}."
        else "${name(e)} is currently expected to report earnings on ${LONG_DAY.format(date)}. The date may change."
    }

    private fun reported(e: EarningsEvent) = e.actual?.let { it.eps != null || it.revenue != null } == true || e.sourceStatus == EarningsEventStatus.REPORTED

    /** The next schedulable event, or the latest reported one (for results), for a company. */
    fun target(events: List<EarningsEvent>, eventId: String?, now: Instant): EarningsEvent? {
        if (eventId != null) return events.firstOrNull { it.id == eventId }
        val upcoming = events.filter { !reported(it) && it.sourceStatus != EarningsEventStatus.CANCELED && it.date >= localToday(now, it).toString() }.minByOrNull { it.date }
        val recent = events.filter { reported(it) }.maxByOrNull { it.date }?.takeIf { it.date >= localToday(now, it).minusDays(RESULTS_WINDOW_DAYS).toString() }
        return recent ?: upcoming
    }

    /**
     * Plans one user. [events] are each company's events (one shared fetch per symbol per pass);
     * [watched] are canonical symbols across all of the user's watchlists (deduplicated).
     */
    fun plan(uid: String, doc: EarningsReminderDocument, watched: Set<String>, events: Map<String, List<EarningsEvent>>, now: Instant): ReminderPlan {
        val prefs = doc.preferences
        val out = LinkedHashMap<String, PlannedNotification>()
        val autoDates = HashMap<String, String>()
        fun add(n: PlannedNotification) { if (n.key !in out) out[n.key] = n }

        fun schedule(r: EarningsReminder, event: EarningsEvent?, specificEvent: Boolean): EarningsReminder {
            if (!r.enabled) return r.copy(scheduleStatus = ReminderScheduleStatus.PAUSED, statusMessage = "This reminder is turned off.", nextDeliveryAt = null, nextDeliveryText = null)
            if (!prefs.enabled) return r.copy(scheduleStatus = ReminderScheduleStatus.PAUSED, statusMessage = "Earnings notifications are turned off.", nextDeliveryAt = null, nextDeliveryText = null)
            event ?: return r.copy(scheduleStatus = ReminderScheduleStatus.WAITING_FOR_DATE, statusMessage = "Waiting for a confirmed earnings date.", eventDate = null, nextDeliveryAt = null, nextDeliveryText = null)
            val base = r.copy(companyName = r.companyName ?: event.name, eventDate = event.date, earningsEventId = r.earningsEventId ?: if (specificEvent) event.id else r.earningsEventId)
            val notice = { type: EarningsNotificationType, extra: String, title: String, body: String ->
                PlannedNotification(key(uid, type, event.id, extra), r.reminderId, type, event, quiet(now.atZone(zone(prefs)), prefs).toInstant(), title, body)
            }
            if (event.sourceStatus == EarningsEventStatus.CANCELED) {
                if (prefs.cancellations) add(notice(EarningsNotificationType.EVENT_CANCELED, "", "Earnings Report Canceled", "${name(event)}'s scheduled earnings report was canceled, according to the data source."))
                return base.copy(scheduleStatus = ReminderScheduleStatus.CANCELED, statusMessage = "The data source says this report was canceled.", nextDeliveryAt = null, nextDeliveryText = null)
            }
            if (reported(event)) {
                val fresh = specificEvent || event.date >= localToday(now, event).minusDays(RESULTS_WINDOW_DAYS).toString()
                if (prefs.resultsAvailable && r.results && fresh) add(notice(EarningsNotificationType.RESULTS_AVAILABLE, "", "Earnings Results Available",
                    "${name(event)}'s quarterly earnings results are now available."))
                return base.copy(scheduleStatus = ReminderScheduleStatus.COMPLETED, statusMessage = "Results have been reported.", nextDeliveryAt = null, nextDeliveryText = null)
            }
            if (event.sourceStatus == EarningsEventStatus.POSTPONED || event.dateStatus == EarningsDateStatus.UNKNOWN)
                return base.copy(scheduleStatus = ReminderScheduleStatus.WAITING_FOR_DATE, statusMessage = "Waiting for a confirmed earnings date.", nextDeliveryAt = null, nextDeliveryText = null)
            val date = LocalDate.parse(event.date)
            val last = if (r.source == ReminderSource.WATCHLIST_AUTO) doc.autoDates[event.id] else r.lastKnownEventDate
            if (last != null && last != event.date && prefs.dateChanges)
                add(notice(EarningsNotificationType.DATE_CHANGED, event.date, "Earnings Date Updated", "${name(event)}'s expected earnings date has changed to ${LONG_DAY.format(date)}."))
            if (r.source == ReminderSource.WATCHLIST_AUTO) autoDates[event.id] = event.date
            val updated = base.copy(lastKnownEventDate = event.date)
            if (date < localToday(now, event)) return updated.copy(scheduleStatus = ReminderScheduleStatus.ACTIVE, nextDeliveryAt = null, nextDeliveryText = null,
                statusMessage = if (r.results && prefs.resultsAvailable) "The expected date has passed. You'll be notified when results are published." else "The expected date has passed.")
            val at = localInstant(date.minusDays(r.offsetDays.toLong()), prefs)
            if (at.plus(LATE_GRACE) < now) return updated.copy(scheduleStatus = ReminderScheduleStatus.ACTIVE, nextDeliveryAt = null, nextDeliveryText = null,
                statusMessage = "The reminder time for this report has passed" + if (r.results && prefs.resultsAvailable) ". You'll still be notified when results are published." else ".")
            add(PlannedNotification(key(uid, EarningsNotificationType.PRE_EARNINGS, event.id, "${r.offsetDays}d|${event.date}"), r.reminderId, EarningsNotificationType.PRE_EARNINGS,
                event, at, "Upcoming Earnings", preText(event, date, r.offsetDays)))
            val local = at.atZone(zone(prefs))
            return updated.copy(scheduleStatus = ReminderScheduleStatus.ACTIVE, statusMessage = null, nextDeliveryAt = at.toString(),
                nextDeliveryText = "${DAY.format(local)} around ${CLOCK.format(local)} (${prefs.timeZone})")
        }

        val manual = doc.reminders.map { r -> schedule(r, events[r.instrumentId]?.let { target(it, r.earningsEventId, now) }, r.earningsEventId != null) }
        val auto = if (!prefs.watchlistAuto) emptyList() else watched.sorted().map { symbol ->
            val event = events[symbol]?.let { target(it, null, now) }
            schedule(EarningsReminder("auto:$symbol", symbol, event?.id, ReminderSource.WATCHLIST_AUTO, prefs.defaultOffsetDays, true, true, event?.name, 0, 0), event, false)
        }
        return ReminderPlan(out.values.toList(), manual + auto, autoDates)
    }
}

// ---------- Service ----------

@Serializable
data class EarningsReminderReport(
    val startedAt: String, val skipped: Boolean = false, val users: Int = 0, val symbols: Int = 0, val planned: Int = 0, val created: Int = 0,
    val rescheduled: Int = 0, val canceled: Int = 0, val due: Int = 0, val submitted: Int = 0, val retried: Int = 0, val failed: Int = 0,
    val late: Int = 0, val invalidTokens: Int = 0, val dataErrors: Int = 0
)

/**
 * Earnings reminders: CRUD and preferences per signed-in user, a batched scheduler pass (one event fetch
 * per company per pass, whatever the number of users), reconciliation of event changes, and dispatch of
 * due notifications through the existing push stack (FCM/APNs in REAL, simulated in MOCK). Pre-Phase-4
 * per-company earnings alerts are converted once into company reminders, so a user never gets both.
 */
class EarningsReminderService(
    private val store: UserDataStore,
    private val earnings: EarningsService,
    private val sender: PushSender,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val maxUsers: Int = 2_000,
    private val batch: Int = 500,
    /** Phase 5: the StockSteps+ weekly digest uses this same pipeline (null disables it). */
    private val digests: EarningsDigestService? = null
) {
    private val log = LoggerFactory.getLogger("StockSteps.EarningsReminders")
    private val running = Mutex()

    companion object {
        const val MAX_REMINDERS = 200
        const val MAX_ATTEMPTS = 5
        const val LEASE_MILLIS = 60_000L
        fun backoff(attempt: Int): Long = minOf(30_000L shl (attempt - 1).coerceIn(0, 6), 30 * 60_000L)
    }

    private fun bad(code: String, message: String): Nothing = throw EarningsRequestException(400, code, message)
    private fun id(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(20)

    private fun validate(p: EarningsReminderPreferences) {
        if (p.defaultOffsetDays !in ReminderPolicy.OFFSETS) bad("INVALID_OFFSET", "Choose 1, 3 or 7 days before.")
        if (!ReminderPolicy.validTime(p.deliveryTime)) bad("INVALID_TIME", "Use a delivery time like 09:00.")
        if (listOfNotNull(p.quietStart, p.quietEnd).any { !ReminderPolicy.validTime(it) } || (p.quietStart == null) != (p.quietEnd == null)) bad("INVALID_TIME", "Quiet hours need a start and an end, like 22:00 and 07:00.")
        if (p.timeZone.length > 64 || runCatching { ZoneId.of(p.timeZone) }.isFailure || !p.timeZone.contains('/')) bad("INVALID_TIME_ZONE", "Use a time zone like America/Toronto.")
    }

    /** One-time conversion of per-company earnings alerts (pre-Phase 4) into company reminders. */
    private suspend fun migrate(uid: String) {
        val doc = store.updateEarningsReminders(uid) { it to it }
        if (doc?.migratedLegacy == true) return
        val legacy = store.updateAlerts(uid) { rules -> rules.filterNot { it.type == AlertType.EARNINGS } to rules.filter { it.type == AlertType.EARNINGS } }
        val now = clock.millis()
        store.updateEarningsReminders(uid) { current ->
            val base = current ?: EarningsReminderDocument(uid)
            if (base.migratedLegacy) return@updateEarningsReminders base to Unit
            val converted = legacy.filter { it.status == AlertStatus.ACTIVE }.distinctBy { it.instrument.symbol.uppercase() }
                .filter { rule -> base.reminders.none { it.instrumentId.equals(rule.instrument.symbol, true) && it.earningsEventId == null } }
                .map { rule ->
                    val lead = rule.earningsLeadDays ?: 1
                    EarningsReminder("legacy-${rule.id.take(16)}", rule.instrument.symbol.uppercase(), null, ReminderSource.MANUAL,
                        ReminderPolicy.OFFSETS.firstOrNull { it >= lead } ?: 7, true, rule.earningsResults ?: true, rule.instrument.name, now, now)
                }
            base.copy(reminders = base.reminders + converted, migratedLegacy = true, updatedAt = now) to Unit
        }
    }

    private suspend fun watched(uid: String): Set<String> = store.updateWatchlists(uid) { it to it }.watchlists
        .flatMap { list -> list.entries.map { it.instrument.symbol.trim().uppercase() } }.toSet()

    private suspend fun eventsFor(symbols: Set<String>): Pair<Map<String, List<EarningsEvent>>, Int> {
        var errors = 0
        val map = symbols.associateWith { s -> try { earnings.reminderEvents(s) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            errors++; null
        } }.filterValues { it != null }.mapValues { it.value!! }
        return map to errors
    }

    /** Plans one user, writes the reminder statuses and reconciles their deliveries. Returns counts. */
    private data class Counts(val created: Int = 0, val rescheduled: Int = 0, val canceled: Int = 0, val late: Int = 0)

    private suspend fun reconcile(uid: String, events: Map<String, List<EarningsEvent>>? = null): Counts {
        val doc = store.updateEarningsReminders(uid) { it to it } ?: return Counts()
        val watched = if (doc.preferences.watchlistAuto) watched(uid) else emptySet()
        val symbols = doc.reminders.map { it.instrumentId }.toSet() + watched
        val data = events ?: eventsFor(symbols).first
        val now = clock.instant()
        val plan = ReminderPlanner.plan(uid, doc, watched, data, now)
        store.updateEarningsReminders(uid) { current ->
            val c = current ?: return@updateEarningsReminders null to Unit
            val byId = plan.reminders.associateBy { it.reminderId }
            c.copy(reminders = c.reminders.map { r -> byId[r.reminderId]?.let { p -> r.copy(lastKnownEventDate = p.lastKnownEventDate, companyName = p.companyName ?: r.companyName,
                scheduleStatus = p.scheduleStatus, statusMessage = p.statusMessage, eventDate = p.eventDate, nextDeliveryAt = p.nextDeliveryAt, nextDeliveryText = p.nextDeliveryText) } ?: r },
                autoDates = c.autoDates + plan.autoDates) to Unit
        }
        var created = 0; var rescheduled = 0; var canceled = 0; var late = 0
        val desired = plan.notifications.associateBy { it.key }.mapValues { Unit }.toMutableMap()
        // Weekly digest: one delivery per ISO week; content and plan are re-verified at send time.
        digests?.plannedDelivery(doc, now)?.let { (week, at) ->
            val key = ReminderPlanner.key(uid, EarningsNotificationType.WEEKLY_DIGEST, "digest:$week")
            desired[key] = Unit
            store.updateEarningsDelivery(key) { existing ->
                when {
                    existing == null -> { created++; EarningsNotificationDelivery(key, uid, null, "digest:$week", null, "", EarningsNotificationType.WEEKLY_DIGEST, at.toEpochMilli(),
                        EarningsDigestService.TITLE, "Your weekly earnings digest is ready.", mapOf("type" to "earnings-digest", "digestWeek" to week, "notificationId" to hash(key),
                            "payloadVersion" to ReminderPolicy.PAYLOAD_VERSION), createdAt = now.toEpochMilli()) }
                    existing.status == NotificationDeliveryStatus.PENDING && existing.scheduledFor != at.toEpochMilli() -> { rescheduled++; existing.copy(scheduledFor = at.toEpochMilli()) }
                    existing.status == NotificationDeliveryStatus.CANCELED && existing.failureReason == RECONCILED && at.plus(ReminderPlanner.LATE_GRACE) > now -> { created++
                        existing.copy(status = NotificationDeliveryStatus.PENDING, scheduledFor = at.toEpochMilli(), failureReason = null) }
                    else -> null
                }
            }
        }
        for (n in plan.notifications) {
            store.updateEarningsDelivery(n.key) { existing ->
                when {
                    existing == null -> { created++; delivery(uid, n, now) }
                    existing.status == NotificationDeliveryStatus.PENDING && existing.scheduledFor != n.scheduledFor.toEpochMilli() -> { rescheduled++
                        existing.copy(scheduledFor = n.scheduledFor.toEpochMilli(), title = n.title, body = n.body) }
                    // Re-requested (e.g. a company re-added to a watchlist) before it was due: revive.
                    existing.status == NotificationDeliveryStatus.CANCELED && existing.failureReason == RECONCILED && n.scheduledFor.plus(ReminderPlanner.LATE_GRACE) > now -> { created++
                        existing.copy(status = NotificationDeliveryStatus.PENDING, scheduledFor = n.scheduledFor.toEpochMilli(), failureReason = null, title = n.title, body = n.body) }
                    else -> null
                }
            }
        }
        // Pending notifications the schedule no longer wants (removed reminder, moved date, turned off).
        for (d in store.earningsDeliveries(uid, 500).filter { it.status == NotificationDeliveryStatus.PENDING && it.key !in desired }) {
            val oneOff = d.type != EarningsNotificationType.PRE_EARNINGS && d.type != EarningsNotificationType.WEEKLY_DIGEST && doc.preferences.enabled
            if (oneOff) continue
            // A reminder whose time has passed is skipped (late policy), not "no longer requested".
            val missed = d.type == EarningsNotificationType.PRE_EARNINGS && now.toEpochMilli() > d.scheduledFor + ReminderPlanner.LATE_GRACE.toMillis()
            store.updateEarningsDelivery(d.idempotencyKey) { e -> e?.takeIf { it.status == NotificationDeliveryStatus.PENDING }?.copy(status = NotificationDeliveryStatus.CANCELED,
                failureReason = if (missed) "Delivery window passed" else RECONCILED) }
            if (missed) late++ else canceled++
        }
        return Counts(created, rescheduled, canceled, late)
    }

    private val EarningsNotificationDelivery.key get() = idempotencyKey
    private val RECONCILED = "No longer requested"

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    private fun delivery(uid: String, n: PlannedNotification, now: Instant): EarningsNotificationDelivery {
        val type = when (n.type) {
            EarningsNotificationType.PRE_EARNINGS -> "earnings-reminder"; EarningsNotificationType.RESULTS_AVAILABLE -> "earnings-results"
            EarningsNotificationType.DATE_CHANGED -> "earnings-date-changed"; EarningsNotificationType.EVENT_CANCELED -> "earnings-canceled"
            EarningsNotificationType.WEEKLY_DIGEST -> "earnings-digest"
        }
        val reportId = n.event.id.takeIf { n.type == EarningsNotificationType.RESULTS_AVAILABLE }
        // Versioned, identifiers only: no account data; the app re-fetches everything it shows.
        val data = listOfNotNull("type" to type, "eventId" to n.event.id, reportId?.let { "reportId" to it }, "instrumentId" to n.event.symbol,
            "notificationId" to hash(n.key), "payloadVersion" to ReminderPolicy.PAYLOAD_VERSION).toMap()
        return EarningsNotificationDelivery(n.key, uid, n.reminderId, n.event.id, reportId, n.event.symbol, n.type, n.scheduledFor.toEpochMilli(), n.title, n.body, data, createdAt = now.toEpochMilli())
    }

    // ---------- API ----------

    suspend fun get(uid: String): EarningsRemindersResponse {
        migrate(uid)
        reconcile(uid)
        val doc = store.updateEarningsReminders(uid) { it to it } ?: EarningsReminderDocument(uid)
        val watched = if (doc.preferences.watchlistAuto) watched(uid) else emptySet()
        val autos = if (watched.isEmpty()) emptyList() else ReminderPlanner.plan(uid, doc, watched, eventsFor(watched).first, clock.instant()).reminders.filter { it.source == ReminderSource.WATCHLIST_AUTO }
        val notes = store.earningsDeliveries(uid, 50).map { d ->
            EarningsNotificationView(d.idempotencyKey, d.type, d.earningsEventId, d.instrumentId, Instant.ofEpochMilli(d.scheduledFor).toString(), d.status, d.title, d.body, d.failureReason, d.attempts)
        }
        return EarningsRemindersResponse(doc.reminders + autos, doc.preferences, notes, clock.instant().toString(), sampleData)
    }

    suspend fun create(uid: String, request: CreateEarningsReminder): EarningsRemindersResponse {
        val (symbol, _, _) = EarningsReportMapper.parse(request.eventId) ?: bad("INVALID_EVENT", "Invalid earnings event id.")
        val offset = request.offsetDays ?: ReminderPolicy.DEFAULT_OFFSET
        if (offset !in ReminderPolicy.OFFSETS) bad("INVALID_OFFSET", "Choose 1, 3 or 7 days before.")
        if ((request.idempotencyKey?.length ?: 0) > 80) bad("INVALID_KEY", "Invalid idempotency key.")
        val event = earnings.reminderEvents(symbol).firstOrNull { it.id.equals(request.eventId, true) }
            ?: throw EarningsRequestException(404, "NOT_FOUND", "This earnings event isn't available.")
        if (event.actual != null) bad("ALREADY_REPORTED", "Results for this period are already published.")
        if (event.sourceStatus == EarningsEventStatus.CANCELED) bad("CANCELED", "The data source says this report was canceled.")
        migrate(uid)
        val now = clock.millis()
        store.updateEarningsReminders(uid) { current ->
            val doc = current ?: EarningsReminderDocument(uid, migratedLegacy = true)
            // Idempotent: one manual reminder per event; repeating the request updates it.
            val existing = doc.reminders.firstOrNull { it.earningsEventId == event.id && it.source == ReminderSource.MANUAL }
            val reminders = if (existing != null) doc.reminders.map { if (it === existing) it.copy(offsetDays = offset, results = request.results ?: it.results, enabled = true, updatedAt = now) else it }
                else {
                    if (doc.reminders.size >= MAX_REMINDERS) bad("TOO_MANY", "You can keep up to $MAX_REMINDERS earnings reminders.")
                    doc.reminders + EarningsReminder(id(), event.symbol, event.id, ReminderSource.MANUAL, offset, true, request.results ?: true, event.name, now, now)
                }
            doc.copy(reminders = reminders, updatedAt = now) to Unit
        }
        return get(uid)
    }

    suspend fun update(uid: String, reminderId: String, request: UpdateEarningsReminder): EarningsRemindersResponse {
        request.offsetDays?.let { if (it !in ReminderPolicy.OFFSETS) bad("INVALID_OFFSET", "Choose 1, 3 or 7 days before.") }
        val now = clock.millis()
        val found = store.updateEarningsReminders(uid) { current ->
            val doc = current ?: return@updateEarningsReminders current to false
            if (doc.reminders.none { it.reminderId == reminderId }) return@updateEarningsReminders doc to false
            doc.copy(reminders = doc.reminders.map { if (it.reminderId == reminderId) it.copy(offsetDays = request.offsetDays ?: it.offsetDays,
                enabled = request.enabled ?: it.enabled, results = request.results ?: it.results, updatedAt = now) else it }, updatedAt = now) to true
        }
        // Another user's id looks exactly like a missing one.
        if (!found) throw EarningsRequestException(404, "NOT_FOUND", "Reminder not found.")
        return get(uid)
    }

    suspend fun delete(uid: String, reminderId: String): EarningsRemindersResponse {
        val found = store.updateEarningsReminders(uid) { current ->
            val doc = current ?: return@updateEarningsReminders current to false
            if (doc.reminders.none { it.reminderId == reminderId }) return@updateEarningsReminders doc to false
            doc.copy(reminders = doc.reminders.filterNot { it.reminderId == reminderId }, updatedAt = clock.millis()) to true
        }
        if (!found) throw EarningsRequestException(404, "NOT_FOUND", "Reminder not found.")
        return get(uid)
    }

    suspend fun setPreferences(uid: String, preferences: EarningsReminderPreferences): EarningsRemindersResponse {
        validate(preferences)
        migrate(uid)
        store.updateEarningsReminders(uid) { current -> (current ?: EarningsReminderDocument(uid, migratedLegacy = true)).copy(preferences = preferences, updatedAt = clock.millis()) to Unit }
        return get(uid)
    }

    // ---------- Scheduler pass ----------

    /** Plans every active user (events fetched once per company), then dispatches due notifications. */
    suspend fun runPass(): EarningsReminderReport {
        val started = clock.instant()
        if (!running.tryLock()) return EarningsReminderReport(started.toString(), skipped = true)
        try {
            // Users with pre-Phase-4 earnings alerts are migrated even if they never opened reminders.
            store.activeAlerts(maxUsers).filter { it.rule.type == AlertType.EARNINGS }.map { it.uid }.distinct().forEach { migrate(it) }
            val users = store.earningsReminderUsers(maxUsers)
            users.forEach { migrate(it.uid) }
            val watchedByUser = users.associate { u -> u.uid to (if (u.preferences.watchlistAuto) watched(u.uid) else emptySet()) }
            val symbols = users.flatMap { u -> u.reminders.map { it.instrumentId } }.toSet() + watchedByUser.values.flatten()
            val (events, errors) = eventsFor(symbols)
            var report = EarningsReminderReport(started.toString(), users = users.size, symbols = symbols.size, dataErrors = errors)
            for (u in users) {
                try {
                    val c = reconcile(u.uid, events)
                    report = report.copy(created = report.created + c.created, rescheduled = report.rescheduled + c.rescheduled, canceled = report.canceled + c.canceled, late = report.late + c.late)
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    log.warn("Reminder reconciliation failed for one user: {}", cause::class.simpleName)
                    report = report.copy(dataErrors = report.dataErrors + 1)
                }
            }
            return dispatch(report).also {
                log.info("Earnings reminder pass users={} symbols={} created={} rescheduled={} canceled={} due={} submitted={} retried={} failed={} late={} invalidTokens={} dataErrors={}",
                    it.users, it.symbols, it.created, it.rescheduled, it.canceled, it.due, it.submitted, it.retried, it.failed, it.late, it.invalidTokens, it.dataErrors)
            }
        } finally { running.unlock() }
    }

    /** Sends due notifications. Claimed atomically with a lease, so concurrent workers can't both send one. */
    private suspend fun dispatch(report: EarningsReminderReport): EarningsReminderReport {
        var r = report
        val now = clock.millis()
        val prefs = HashMap<String, EarningsReminderPreferences?>()
        for (due in store.dueEarningsDeliveries(now, batch)) {
            val claimed = store.updateEarningsDelivery(due.idempotencyKey) { d ->
                val ready = d != null && ((d.status == NotificationDeliveryStatus.PENDING && d.dueAt <= now) || (d.status == NotificationDeliveryStatus.PROCESSING && d.leaseUntil <= now))
                if (!ready) null else d!!.copy(status = NotificationDeliveryStatus.PROCESSING, attempts = d.attempts + 1, leaseUntil = now + LEASE_MILLIS, attemptedAt = now)
            }?.takeIf { it.status == NotificationDeliveryStatus.PROCESSING && it.leaseUntil == now + LEASE_MILLIS } ?: continue
            r = r.copy(due = r.due + 1)
            suspend fun finish(status: NotificationDeliveryStatus, reason: String?, transform: (EarningsNotificationDelivery) -> EarningsNotificationDelivery = { it }) =
                store.updateEarningsDelivery(claimed.idempotencyKey) { d -> d?.let { transform(it).copy(status = status, failureReason = reason, leaseUntil = 0) } }
            val p = prefs.getOrPut(claimed.uid) { store.updateEarningsReminders(claimed.uid) { it to it }?.preferences }
            if (p == null || !p.enabled) { finish(NotificationDeliveryStatus.CANCELED, "Earnings notifications are turned off"); continue }
            if (claimed.type == EarningsNotificationType.PRE_EARNINGS && now > claimed.scheduledFor + ReminderPlanner.LATE_GRACE.toMillis()) {
                finish(NotificationDeliveryStatus.CANCELED, "Delivery window passed"); r = r.copy(late = r.late + 1); continue
            }
            // Weekly digest: StockSteps+ and the opt-in are re-checked now, and an empty week sends nothing.
            var body = claimed.body
            var data = claimed.data
            if (claimed.type == EarningsNotificationType.WEEKLY_DIGEST) {
                val ready = try { digests?.notification(claimed.uid, java.time.Instant.ofEpochMilli(claimed.scheduledFor)) } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    if (claimed.attempts < MAX_ATTEMPTS) { finish(NotificationDeliveryStatus.PENDING, "Digest couldn't be prepared") { it.copy(nextAttemptAt = now + backoff(claimed.attempts)) }; r = r.copy(retried = r.retried + 1) }
                    else { finish(NotificationDeliveryStatus.FAILED, "Digest couldn't be prepared"); r = r.copy(failed = r.failed + 1) }
                    continue
                }
                if (ready == null) { finish(NotificationDeliveryStatus.CANCELED, "Nothing to send (digest off, StockSteps+ ended, or nothing to report)"); continue }
                body = ready.first
                data = data + ("digestId" to ready.second.digestId)
                store.updateEarningsDelivery(claimed.idempotencyKey) { d -> d?.copy(body = body, data = data) }
            }
            val devices = store.devices(claimed.uid).filter { it.deviceId !in claimed.acceptedDevices }
            if (devices.isEmpty() && claimed.acceptedDevices.isEmpty()) { finish(NotificationDeliveryStatus.FAILED, "No registered device"); r = r.copy(failed = r.failed + 1); continue }
            val accepted = claimed.acceptedDevices.toMutableList()
            var messageId = claimed.providerMessageId
            var retry: String? = null
            var permanent: String? = null
            for (device in devices) {
                when (val result = sender.send(PushMessage(device.token, claimed.title, body, data, channel = "earnings_reminders"))) {
                    is PushResult.Accepted -> { accepted += device.deviceId; messageId = messageId ?: result.messageId }
                    PushResult.Simulated -> { accepted += device.deviceId; messageId = messageId ?: "simulated" }
                    PushResult.InvalidToken -> { store.removeToken(device.token); r = r.copy(invalidTokens = r.invalidTokens + 1) }
                    is PushResult.Retryable -> retry = result.reason
                    is PushResult.Failed -> permanent = result.reason
                }
            }
            when {
                retry != null && claimed.attempts < MAX_ATTEMPTS -> {
                    finish(NotificationDeliveryStatus.PENDING, retry) { it.copy(acceptedDevices = accepted, providerMessageId = messageId, nextAttemptAt = now + backoff(claimed.attempts)) }
                    r = r.copy(retried = r.retried + 1)
                }
                accepted.isNotEmpty() -> { finish(NotificationDeliveryStatus.SUBMITTED, null) { it.copy(acceptedDevices = accepted, providerMessageId = messageId) }; r = r.copy(submitted = r.submitted + 1) }
                else -> { finish(NotificationDeliveryStatus.FAILED, retry ?: permanent ?: "Every device token was invalid") { it.copy(acceptedDevices = accepted) }; r = r.copy(failed = r.failed + 1) }
            }
        }
        return r
    }

    /** MOCK/debug: the user's own planned and sent notifications. */
    suspend fun deliveries(uid: String) = store.earningsDeliveries(uid, 100)
}

// ---------- Routes ----------

/** Signed-in reminder routes (identity from the verified token only) and the scheduler trigger. */
fun Route.earningsReminderRoutes(service: EarningsReminderService, auth: UserAuthenticator, limiter: RequestRateLimiter, schedulerSecret: String?, mock: Boolean) {
    suspend fun RoutingContext.guarded(uid: String, block: suspend () -> Any) {
        if (!limiter.allow("reminders:$uid")) { call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return }
        if ((call.request.headers["Content-Length"]?.toLongOrNull() ?: 0) > 8_192) { call.respond(HttpStatusCode.PayloadTooLarge, ApiError("TOO_LARGE", "Request too large.")); return }
        try { call.respond(block()) } catch (cause: EarningsRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        } catch (cause: io.ktor.server.plugins.BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
        }
    }
    route("/api/v1/me/earnings/reminders") {
        get { user(auth) { uid -> guarded(uid) { service.get(uid) } } }
        post { user(auth) { uid -> guarded(uid) { service.create(uid, call.receive<CreateEarningsReminder>()) } } }
        put("/preferences") { user(auth) { uid -> guarded(uid) { service.setPreferences(uid, call.receive<EarningsReminderPreferences>()) } } }
        get("/deliveries") { user(auth) { uid -> guarded(uid) { service.deliveries(uid).map { d ->
            EarningsNotificationView(d.idempotencyKey, d.type, d.earningsEventId, d.instrumentId, Instant.ofEpochMilli(d.scheduledFor).toString(), d.status, d.title, d.body, d.failureReason, d.attempts)
        } } } }
        put("/{reminderId}") { user(auth) { uid -> guarded(uid) { service.update(uid, call.parameters["reminderId"].orEmpty(), call.receive<UpdateEarningsReminder>()) } } }
        delete("/{reminderId}") { user(auth) { uid -> guarded(uid) { service.delete(uid, call.parameters["reminderId"].orEmpty()) } } }
    }
    // Cloud Scheduler in REAL (shared secret header); local calls allowed in MOCK. Absent otherwise.
    if (schedulerSecret != null || mock) post("/internal/earnings-reminders/dispatch") {
        if (!mock && call.request.headers["X-StockSteps-Scheduler-Token"] != schedulerSecret) {
            call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Not allowed.")); return@post
        }
        call.respond(service.runPass())
    }
}

/**
 * MOCK only: wraps the simulated sender with deterministic debug outcomes by token prefix
 * ("invalid-" → invalid token, "ratelimit-" → retryable rate limit, "fail-" → permanent failure);
 * everything else is recorded locally. Never contacts FCM.
 */
class MockScenarioPushSender(private val inner: PushSender) : PushSender {
    override suspend fun send(message: PushMessage): PushResult = when {
        message.token.startsWith("invalid-") -> PushResult.InvalidToken
        message.token.startsWith("ratelimit-") -> PushResult.Retryable("Rate limited (sample)")
        message.token.startsWith("fail-") -> PushResult.Failed("Rejected (sample)")
        else -> inner.send(message)
    }
}
