package org.example.stocksteps.earnings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// Earnings Intelligence Lite, Phase 4: earnings reminders. The backend owns scheduling (user time zone,
// delivery time, quiet hours, event changes, deduplication); the apps only edit preferences and show
// status. Free for everyone. Never a trading signal.

@Serializable enum class ReminderSource { MANUAL, WATCHLIST_AUTO }

@Serializable enum class ReminderScheduleStatus(val label: String) {
    ACTIVE("Reminder scheduled"), PAUSED("Paused"), WAITING_FOR_DATE("Waiting for a confirmed earnings date"),
    CANCELED("Canceled"), COMPLETED("Completed"), FAILED("Couldn't be scheduled")
}

@Serializable enum class EarningsNotificationType(val label: String) {
    PRE_EARNINGS("Upcoming earnings"), RESULTS_AVAILABLE("Results available"), DATE_CHANGED("Date updated"), EVENT_CANCELED("Report canceled"),
    /** Phase 5, StockSteps+: the opt-in weekly digest (counts only; the app fetches the digest). */
    WEEKLY_DIGEST("Weekly earnings digest")
}

/** Gateway submission status. SUBMITTED means the push service accepted it, not that a device showed it. */
@Serializable enum class NotificationDeliveryStatus { PENDING, PROCESSING, SUBMITTED, FAILED, CANCELED }

object ReminderPolicy {
    val OFFSETS = listOf(1, 3, 7)
    const val DEFAULT_OFFSET = 1
    const val DEFAULT_DELIVERY_TIME = "09:00"
    /** Payload schema version for notification deep links. */
    const val PAYLOAD_VERSION = "1"
    fun validTime(text: String?): Boolean = text != null && Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(text)
}

/**
 * One reminder. [earningsEventId] is the stable fiscal-period event ("AAPL:2026-Q4"), so a moved date
 * keeps the reminder; null means every upcoming report for the company (reminders migrated from the
 * older per-company earnings alerts). Computed fields are filled by the server on every response.
 */
@Serializable
data class EarningsReminder(
    val reminderId: String,
    /** Canonical, exchange-qualified symbol ("TD" ≠ "TD.TO"). */
    val instrumentId: String,
    val earningsEventId: String? = null,
    val source: ReminderSource = ReminderSource.MANUAL,
    /** Calendar days before the event date. */
    val offsetDays: Int = ReminderPolicy.DEFAULT_OFFSET,
    val enabled: Boolean = true,
    /** Also notify when verified results are published. */
    val results: Boolean = true,
    val companyName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** The event date the last schedule used (to detect reschedules). */
    val lastKnownEventDate: String? = null,
    val scheduleStatus: ReminderScheduleStatus = ReminderScheduleStatus.WAITING_FOR_DATE,
    /** Exchange-local event date the reminder currently points to. */
    val eventDate: String? = null,
    /** UTC instant of the next planned notification. */
    val nextDeliveryAt: String? = null,
    /** User-local wording of the planned delivery ("Mon, Oct 12 around 9:00 AM (America/Toronto)"). */
    val nextDeliveryText: String? = null,
    val statusMessage: String? = null
)

/**
 * Hierarchy (each level can only narrow the one above): device notification permission → [enabled]
 * (all earnings notifications) → each reminder (manual or automatic) → [resultsAvailable] /
 * [dateChanges] / [cancellations] → quiet hours and [deliveryTime]. Nothing is re-enabled automatically.
 */
@Serializable
data class EarningsReminderPreferences(
    val enabled: Boolean = true,
    /** Opt-in: remind about every company on the user's watchlists. Off by default. */
    val watchlistAuto: Boolean = false,
    val defaultOffsetDays: Int = ReminderPolicy.DEFAULT_OFFSET,
    val resultsAvailable: Boolean = true,
    val dateChanges: Boolean = false,
    val cancellations: Boolean = true,
    /** Local "HH:mm" on the reminder date. */
    val deliveryTime: String = ReminderPolicy.DEFAULT_DELIVERY_TIME,
    /** IANA zone the delivery time is in (from the device; updated when the user travels). */
    val timeZone: String = "America/Toronto",
    /** Optional quiet hours, local "HH:mm"; a notification due inside them waits until they end. */
    val quietStart: String? = null,
    val quietEnd: String? = null
)

/** A planned or sent notification (the user's own; tokens are never included). */
@Serializable
data class EarningsNotificationView(
    val idempotencyKey: String,
    val type: EarningsNotificationType,
    val earningsEventId: String,
    val instrumentId: String,
    val scheduledFor: String,
    val status: NotificationDeliveryStatus,
    val title: String,
    val body: String,
    val failureReason: String? = null,
    val attempts: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsRemindersResponse(
    @EncodeDefault val reminders: List<EarningsReminder> = emptyList(),
    val preferences: EarningsReminderPreferences = EarningsReminderPreferences(),
    @EncodeDefault val notifications: List<EarningsNotificationView> = emptyList(),
    val asOf: String,
    val sampleData: Boolean = false
)

@Serializable data class CreateEarningsReminder(val eventId: String, val offsetDays: Int? = null, val results: Boolean? = null, val idempotencyKey: String? = null)
@Serializable data class UpdateEarningsReminder(val offsetDays: Int? = null, val enabled: Boolean? = null, val results: Boolean? = null)

// ---------- Client state (shared by Android and iOS) ----------

/** The reminder control for one event. */
enum class ReminderControl { OFF, ON, SAVING, FAILED }

data class EarningsRemindersState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val response: EarningsRemindersResponse? = null,
    /** Events with a save in flight / whose last save failed. */
    val saving: Set<String> = emptySet(),
    val failed: Map<String, String> = emptyMap(),
    /** Platform permission, refreshed from the OS (never stored). */
    val permissionGranted: Boolean? = null,
    val message: String? = null,
    val offline: Boolean = false
) {
    val preferences: EarningsReminderPreferences get() = response?.preferences ?: EarningsReminderPreferences()
    fun reminderFor(eventId: String): EarningsReminder? = response?.reminders?.firstOrNull { it.earningsEventId == eventId && it.source == ReminderSource.MANUAL }
    fun autoFor(eventId: String): EarningsReminder? = response?.reminders?.firstOrNull { it.earningsEventId == eventId && it.source == ReminderSource.WATCHLIST_AUTO }
    fun control(eventId: String): ReminderControl = when {
        eventId in saving -> ReminderControl.SAVING
        eventId in failed -> ReminderControl.FAILED
        reminderFor(eventId)?.enabled == true || autoFor(eventId) != null -> ReminderControl.ON
        else -> ReminderControl.OFF
    }
    /** Shown next to any "Reminder On" when push can't currently reach this device. */
    val deliveryWarning: String? get() = when {
        !preferences.enabled -> "Earnings notifications are turned off in Earnings Reminders settings."
        permissionGranted == false -> "Notifications are off for StockSteps on this device, so reminders are saved but won't appear until you allow them."
        else -> null
    }
}

/** Remote calls (signed in; identity comes from the verified token on the server). */
interface EarningsRemindersRemote {
    suspend fun reminders(): EarningsRemindersResponse
    suspend fun create(request: CreateEarningsReminder): EarningsRemindersResponse
    suspend fun update(reminderId: String, request: UpdateEarningsReminder): EarningsRemindersResponse
    suspend fun delete(reminderId: String): EarningsRemindersResponse
    suspend fun preferences(preferences: EarningsReminderPreferences): EarningsRemindersResponse
}

/**
 * Account-wide reminder state: one per signed-in user (cleared on sign-out/switch). Success is shown
 * only after the server confirms; a failure leaves the previous state and marks the event FAILED.
 * The device time zone is sent with preference saves, so travel updates future schedules.
 */
class EarningsRemindersPresenter(
    private val remote: EarningsRemindersRemote,
    private val scope: CoroutineScope,
    private val session: Flow<String?>,
    private val deviceZone: () -> String,
    private val permission: suspend () -> Boolean? = { null }
) {
    private val mutable = MutableStateFlow(EarningsRemindersState())
    val state: StateFlow<EarningsRemindersState> = mutable.asStateFlow()
    private var uid: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.distinctUntilChanged().collectLatest { id ->
                uid = id
                mutable.value = EarningsRemindersState(signedIn = id != null, permissionGranted = permission())
                if (id != null) load(id)
            }
        }
    }

    private suspend fun load(id: String) {
        mutable.update { it.copy(loading = true) }
        try {
            var r = remote.reminders()
            // Keep the schedule in the user's current zone (e.g. after travelling); only when it changed.
            val zone = deviceZone()
            if (zone.isNotBlank() && r.preferences.timeZone != zone) r = remote.preferences(r.preferences.copy(timeZone = zone))
            if (uid == id) mutable.update { it.copy(loading = false, response = r, offline = false, message = null) }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            if (uid == id) mutable.update { it.copy(loading = false, offline = true, message = message(cause, "Reminders couldn't be loaded.")) }
        }
    }

    private fun message(cause: Exception, fallback: String) = (cause as? org.example.stocksteps.network.StockStepsApiException)?.error?.message ?: fallback

    private fun write(key: String, request: suspend () -> EarningsRemindersResponse) {
        val owner = uid ?: run { mutable.update { it.copy(message = "Sign in to set earnings reminders.") }; return }
        scope.launch {
            mutable.update { it.copy(saving = it.saving + key, failed = it.failed - key) }
            try {
                val r = request()
                if (uid == owner) mutable.update { it.copy(response = r, saving = it.saving - key, offline = false) }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val text = message(cause, "The reminder couldn't be saved. Check your connection and try again.")
                if (uid == owner) mutable.update { it.copy(saving = it.saving - key, failed = it.failed + (key to text), message = text) }
            }
        }
    }

    /** Turns on (or edits) the reminder for one event. */
    fun remind(eventId: String, offsetDays: Int, results: Boolean = true) {
        val existing = mutable.value.reminderFor(eventId)
        write(eventId) {
            if (existing != null) remote.update(existing.reminderId, UpdateEarningsReminder(offsetDays, enabled = true, results = results))
            else remote.create(CreateEarningsReminder(eventId, offsetDays, results, idempotencyKey = "create:$eventId"))
        }
    }

    fun turnOff(eventId: String) {
        val existing = mutable.value.reminderFor(eventId) ?: return
        write(eventId) { remote.delete(existing.reminderId) }
    }

    fun delete(reminderId: String) { write(reminderId) { remote.delete(reminderId) } }

    fun updatePreferences(transform: (EarningsReminderPreferences) -> EarningsReminderPreferences) {
        val next = transform(mutable.value.preferences).copy(timeZone = deviceZone().ifBlank { mutable.value.preferences.timeZone })
        write("preferences") { remote.preferences(next) }
    }

    fun refresh() { uid?.let { id -> scope.launch { mutable.update { it.copy(permissionGranted = permission()) }; load(id) } } }
    fun setPermission(granted: Boolean) = mutable.update { it.copy(permissionGranted = granted) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
}

/** [EarningsRemindersRemote] over the signed-in API. */
class RemoteEarningsReminders(private val user: org.example.stocksteps.data.userdata.UserApi) : EarningsRemindersRemote {
    override suspend fun reminders() = user.earningsReminders()
    override suspend fun create(request: CreateEarningsReminder) = user.createEarningsReminder(request)
    override suspend fun update(reminderId: String, request: UpdateEarningsReminder) = user.updateEarningsReminder(reminderId, request)
    override suspend fun delete(reminderId: String) = user.deleteEarningsReminder(reminderId)
    override suspend fun preferences(preferences: EarningsReminderPreferences) = user.earningsReminderPreferences(preferences)
}
