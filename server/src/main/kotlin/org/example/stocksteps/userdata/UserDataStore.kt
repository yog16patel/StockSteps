package org.example.stocksteps.userdata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*

/** One user's watchlists plus whether the default list (and legacy import) has been created. */
data class UserWatchlists(val watchlists: List<Watchlist>, val initialized: Boolean)

/** An alert rule with its owner, for the evaluator (which works across users). */
data class OwnedAlert(val uid: String, val rule: AlertRule)

@Serializable enum class OutboxStatus { PENDING, SENDING, ACCEPTED, INVALID_TOKEN, FAILED }

/** One push message to one device for one alert event (durable; survives worker crashes). */
@Serializable
data class OutboxItem(
    /** "$eventId|$deviceId": one message per event and device, whatever the retries. */
    val id: String,
    val uid: String,
    val eventId: String,
    val deviceId: String,
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    /** A SENDING item whose lease expired is retried by the next worker (crash recovery). */
    val leaseUntil: Long = 0,
    val createdAt: Long,
    val lastError: String? = null
)

/** One user's earnings reminders and preferences (Phase 4); written atomically. */
@Serializable
data class EarningsReminderDocument(
    val uid: String,
    val reminders: List<org.example.stocksteps.earnings.EarningsReminder> = emptyList(),
    val preferences: org.example.stocksteps.earnings.EarningsReminderPreferences = org.example.stocksteps.earnings.EarningsReminderPreferences(),
    /** Automatic (watchlist) reminders have no stored row; their last scheduled event date lives here. */
    val autoDates: Map<String, String> = emptyMap(),
    /** Per-company earnings alerts from before Phase 4 have been converted to reminders. */
    val migratedLegacy: Boolean = false,
    val updatedAt: Long = 0,
    /** Phase 5: the StockSteps+ weekly digest (opt-in; kept when the plan lapses, delivery pauses). */
    val digest: org.example.stocksteps.earnings.EarningsDigestPreferences = org.example.stocksteps.earnings.EarningsDigestPreferences(),
    /** Recent digests (summaries only, newest first), so the user can see what was sent. */
    val digestHistory: List<org.example.stocksteps.earnings.DigestHistoryEntry> = emptyList()
) {
    /** Included in scheduler passes only when something can be scheduled. */
    val active: Boolean get() = preferences.enabled && (reminders.any { it.enabled } || preferences.watchlistAuto ||
        digest.cadence == org.example.stocksteps.earnings.DigestCadence.WEEKLY)
}

/**
 * One logical earnings notification. [idempotencyKey] is unique (user, event, type, offset, event date):
 * retries, repeated passes, duplicate watchlist entries and several workers can't create a second one.
 * It is sent to every registered device; [acceptedDevices] prevents resending to a device on retry.
 */
@Serializable
data class EarningsNotificationDelivery(
    val idempotencyKey: String,
    val uid: String,
    val reminderId: String? = null,
    val earningsEventId: String,
    val reportId: String? = null,
    val instrumentId: String,
    val type: org.example.stocksteps.earnings.EarningsNotificationType,
    val scheduledFor: Long,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val status: org.example.stocksteps.earnings.NotificationDeliveryStatus = org.example.stocksteps.earnings.NotificationDeliveryStatus.PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val leaseUntil: Long = 0,
    val attemptedAt: Long? = null,
    val providerMessageId: String? = null,
    val acceptedDevices: List<String> = emptyList(),
    val failureReason: String? = null,
    val createdAt: Long
) {
    /** When the dispatcher may next pick it up. */
    val dueAt: Long get() = maxOf(scheduledFor, nextAttemptAt)
}

@Serializable data class DeviceRecord(val uid: String, val deviceId: String, val token: String, val platform: String, val updatedAt: Long)

/**
 * The single source of truth for user data: watchlists (with notes), alert rules, alert events,
 * the notification outbox and push devices. REAL uses Firestore (server-only collections); MOCK
 * and tests use [InMemoryUserDataStore]. Every read-modify-write is atomic.
 */
interface UserDataStore {
    suspend fun <T> updatePortfolio(uid: String, block: (org.example.stocksteps.portfolio.PortfolioLedger) -> Pair<org.example.stocksteps.portfolio.PortfolioLedger, T>): T

    suspend fun <T> updateWatchlists(uid: String, block: (UserWatchlists) -> Pair<UserWatchlists, T>): T
    /** Stocks saved with the previous single-list watchlist (imported once into "My Stocks"). */
    suspend fun legacyWatchlist(uid: String): List<InstrumentRef> = emptyList()

    suspend fun <T> updateAlerts(uid: String, block: (List<AlertRule>) -> Pair<List<AlertRule>, T>): T
    suspend fun activeAlerts(limit: Int): List<OwnedAlert>
    /** Arming changes made by the evaluator; ignored when the rule no longer exists. */
    suspend fun updateRule(uid: String, ruleId: String, update: (AlertRule) -> AlertRule?)

    /**
     * Atomically records a trigger: when no event with [event].id exists, stores the event, applies
     * [ruleUpdate] and queues [outbox]; returns false (doing nothing) if the event already exists.
     */
    suspend fun recordTrigger(uid: String, event: AlertEvent, ruleUpdate: (AlertRule) -> AlertRule, outbox: List<OutboxItem>): Boolean
    suspend fun history(uid: String, limit: Int): List<AlertEvent>
    suspend fun setEventDelivery(uid: String, eventId: String, delivery: DeliveryStatus)

    /** Items to send now: PENDING, or SENDING with an expired lease. */
    suspend fun dueOutbox(now: Long, limit: Int): List<OutboxItem>
    /** Takes a lease on an item; false if another worker holds it or it's finished. */
    suspend fun claimOutbox(id: String, now: Long, leaseMillis: Long): OutboxItem?
    suspend fun finishOutbox(id: String, status: OutboxStatus, error: String?, retryable: Boolean)
    suspend fun outboxForEvent(eventId: String): List<OutboxItem>

    suspend fun registerDevice(record: DeviceRecord)
    suspend fun unregisterDevice(uid: String, deviceId: String)
    suspend fun devices(uid: String): List<DeviceRecord>
    suspend fun removeToken(token: String)

    /** Server-written StockSteps+ record (billing integration or MOCK debug); null when never subscribed. */
    suspend fun entitlement(uid: String): StoredEntitlement?
    suspend fun setEntitlement(uid: String, value: StoredEntitlement?)

    /** Saved screener definitions (filters and sort, never results), atomically per user. */
    suspend fun <T> updateSavedScreens(uid: String, block: (List<org.example.stocksteps.screener.SavedScreen>) -> Pair<List<org.example.stocksteps.screener.SavedScreen>, T>): T

    // Daily Market Brief: global briefs (shared, never per-user data) and per-user notification preferences.
    suspend fun saveBrief(brief: org.example.stocksteps.brief.DailyBrief)
    suspend fun brief(id: String): org.example.stocksteps.brief.DailyBrief?
    suspend fun recentBriefs(limit: Int): List<org.example.stocksteps.brief.BriefSummary>
    suspend fun <T> updateBriefPreferences(uid: String, block: (org.example.stocksteps.brief.BriefPreferenceRecord?) -> Pair<org.example.stocksteps.brief.BriefPreferenceRecord?, T>): T
    suspend fun briefSubscribers(limit: Int): List<org.example.stocksteps.brief.BriefPreferenceRecord>

    /** Practice Portfolio ledger, trial and challenges: one atomic document per user (orders, trial and reset are race-free). */
    suspend fun <T> updatePractice(uid: String, block: (org.example.stocksteps.practice.PracticeAccountData) -> Pair<org.example.stocksteps.practice.PracticeAccountData, T>): T

    /** Guided Research progress (per company), atomically per user. */
    suspend fun <T> updateLearning(uid: String, block: (org.example.stocksteps.learning.LearningProgressDocument) -> Pair<org.example.stocksteps.learning.LearningProgressDocument, T>): T

    /** Earnings reminders (Phase 4): one atomic document per user. */
    suspend fun <T> updateEarningsReminders(uid: String, block: (EarningsReminderDocument?) -> Pair<EarningsReminderDocument?, T>): T
    /** Users with anything schedulable (indexed by an `active` flag), for the batched scheduler pass. */
    suspend fun earningsReminderUsers(limit: Int): List<EarningsReminderDocument>
    /** Atomic read-modify-write of one delivery by its unique key (null result = leave absent). */
    suspend fun updateEarningsDelivery(key: String, block: (EarningsNotificationDelivery?) -> EarningsNotificationDelivery?): EarningsNotificationDelivery?
    /** PENDING deliveries due by [now], and PROCESSING ones whose lease expired (crash recovery). */
    suspend fun dueEarningsDeliveries(now: Long, limit: Int): List<EarningsNotificationDelivery>
    suspend fun earningsDeliveries(uid: String, limit: Int): List<EarningsNotificationDelivery>
}

/** What the backend stores per user; [EntitlementService] derives tier and status from it. */
@Serializable
data class StoredEntitlement(
    val plan: org.example.stocksteps.portfolio.analytics.SubscriptionTier,
    val expiresAt: Long? = null,
    /** "subscription" (billing) or "debug" (MOCK simulation only). */
    val source: String,
    /**
     * Billing state from store verification: null/"active", "canceled" (renewal off, paid until
     * [expiresAt]), "grace" (billing retry until [graceUntil], access kept), "payment-failed",
     * "restored", or (MOCK only) "unavailable".
     */
    val state: String? = null,
    val graceUntil: Long? = null
)

/** MOCK and tests: process memory, one lock (fine for a local server). */
class InMemoryUserDataStore(private val legacy: Map<String, List<InstrumentRef>> = emptyMap()) : UserDataStore {
    private val lock = Mutex()
    private val portfolios = HashMap<String, org.example.stocksteps.portfolio.PortfolioLedger>()
    override suspend fun <T> updatePortfolio(uid: String, block: (org.example.stocksteps.portfolio.PortfolioLedger) -> Pair<org.example.stocksteps.portfolio.PortfolioLedger, T>): T = lock.withLock {
        val (next, result) = block(portfolios[uid] ?: org.example.stocksteps.portfolio.PortfolioLedger())
        portfolios[uid] = next
        result
    }
    private val watchlists = HashMap<String, UserWatchlists>()
    private val alerts = HashMap<String, List<AlertRule>>()
    private val events = LinkedHashMap<String, Pair<String, AlertEvent>>()
    private val outbox = LinkedHashMap<String, OutboxItem>()
    private val devices = LinkedHashMap<String, DeviceRecord>()
    private val entitlements = HashMap<String, StoredEntitlement>()
    private val savedScreens = HashMap<String, List<org.example.stocksteps.screener.SavedScreen>>()
    override suspend fun <T> updateSavedScreens(uid: String, block: (List<org.example.stocksteps.screener.SavedScreen>) -> Pair<List<org.example.stocksteps.screener.SavedScreen>, T>): T = lock.withLock {
        val (next, result) = block(savedScreens[uid].orEmpty())
        savedScreens[uid] = next
        result
    }

    private val briefs = LinkedHashMap<String, org.example.stocksteps.brief.DailyBrief>()
    private val briefPrefs = HashMap<String, org.example.stocksteps.brief.BriefPreferenceRecord>()
    override suspend fun saveBrief(brief: org.example.stocksteps.brief.DailyBrief) = lock.withLock { briefs[brief.id] = brief; Unit }
    override suspend fun brief(id: String) = lock.withLock { briefs[id] }
    override suspend fun recentBriefs(limit: Int) = lock.withLock { briefs.values.sortedByDescending { it.generatedAt }.take(limit).map { it.summary() } }
    override suspend fun <T> updateBriefPreferences(uid: String, block: (org.example.stocksteps.brief.BriefPreferenceRecord?) -> Pair<org.example.stocksteps.brief.BriefPreferenceRecord?, T>): T = lock.withLock {
        val (next, result) = block(briefPrefs[uid])
        if (next == null) briefPrefs.remove(uid) else briefPrefs[uid] = next
        result
    }
    override suspend fun briefSubscribers(limit: Int) = lock.withLock { briefPrefs.values.filter { it.preferences.notificationsEnabled }.take(limit) }
    private val practice = HashMap<String, org.example.stocksteps.practice.PracticeAccountData>()
    override suspend fun <T> updatePractice(uid: String, block: (org.example.stocksteps.practice.PracticeAccountData) -> Pair<org.example.stocksteps.practice.PracticeAccountData, T>): T = lock.withLock {
        val (next, result) = block(practice[uid] ?: org.example.stocksteps.practice.PracticeAccountData())
        practice[uid] = next
        result
    }
    private val learning = HashMap<String, org.example.stocksteps.learning.LearningProgressDocument>()
    override suspend fun <T> updateLearning(uid: String, block: (org.example.stocksteps.learning.LearningProgressDocument) -> Pair<org.example.stocksteps.learning.LearningProgressDocument, T>): T = lock.withLock {
        val (next, result) = block(learning[uid] ?: org.example.stocksteps.learning.LearningProgressDocument())
        learning[uid] = next
        result
    }

    override suspend fun entitlement(uid: String) = lock.withLock { entitlements[uid] }
    override suspend fun setEntitlement(uid: String, value: StoredEntitlement?) = lock.withLock {
        if (value == null) entitlements.remove(uid) else entitlements[uid] = value
        Unit
    }

    override suspend fun <T> updateWatchlists(uid: String, block: (UserWatchlists) -> Pair<UserWatchlists, T>): T = lock.withLock {
        val (next, result) = block(watchlists[uid] ?: UserWatchlists(emptyList(), initialized = false))
        watchlists[uid] = next
        result
    }

    override suspend fun legacyWatchlist(uid: String) = legacy[uid].orEmpty()

    override suspend fun <T> updateAlerts(uid: String, block: (List<AlertRule>) -> Pair<List<AlertRule>, T>): T = lock.withLock {
        val (next, result) = block(alerts[uid].orEmpty())
        alerts[uid] = next
        result
    }

    override suspend fun activeAlerts(limit: Int) = lock.withLock {
        alerts.flatMap { (uid, rules) -> rules.filter { it.status == AlertStatus.ACTIVE }.map { OwnedAlert(uid, it) } }.take(limit)
    }

    override suspend fun updateRule(uid: String, ruleId: String, update: (AlertRule) -> AlertRule?) = lock.withLock {
        alerts[uid] = alerts[uid].orEmpty().mapNotNull { if (it.id == ruleId) update(it) else it }
    }

    override suspend fun recordTrigger(uid: String, event: AlertEvent, ruleUpdate: (AlertRule) -> AlertRule, outbox: List<OutboxItem>) = lock.withLock {
        if (event.id in events) return@withLock false
        val rules = alerts[uid].orEmpty()
        if (rules.none { it.id == event.ruleId }) return@withLock false
        events[event.id] = uid to event
        alerts[uid] = rules.map { if (it.id == event.ruleId) ruleUpdate(it) else it }
        outbox.forEach { this.outbox.putIfAbsent(it.id, it) }
        true
    }

    override suspend fun history(uid: String, limit: Int) = lock.withLock {
        events.values.filter { it.first == uid }.map { it.second }.sortedByDescending { it.triggeredAt }.take(limit)
    }

    override suspend fun setEventDelivery(uid: String, eventId: String, delivery: DeliveryStatus) = lock.withLock {
        events[eventId]?.let { (owner, event) -> if (owner == uid) events[eventId] = owner to event.copy(delivery = delivery) }
        Unit
    }

    override suspend fun dueOutbox(now: Long, limit: Int) = lock.withLock {
        outbox.values.filter { it.status == OutboxStatus.PENDING || (it.status == OutboxStatus.SENDING && it.leaseUntil <= now) }.take(limit)
    }

    override suspend fun claimOutbox(id: String, now: Long, leaseMillis: Long) = lock.withLock {
        val item = outbox[id] ?: return@withLock null
        val due = item.status == OutboxStatus.PENDING || (item.status == OutboxStatus.SENDING && item.leaseUntil <= now)
        if (!due) return@withLock null
        item.copy(status = OutboxStatus.SENDING, attempts = item.attempts + 1, leaseUntil = now + leaseMillis).also { outbox[id] = it }
    }

    override suspend fun finishOutbox(id: String, status: OutboxStatus, error: String?, retryable: Boolean) = lock.withLock {
        outbox[id]?.let { outbox[id] = it.copy(status = if (retryable) OutboxStatus.PENDING else status, lastError = error, leaseUntil = 0) }
        Unit
    }

    override suspend fun outboxForEvent(eventId: String) = lock.withLock { outbox.values.filter { it.eventId == eventId } }

    override suspend fun registerDevice(record: DeviceRecord) = lock.withLock {
        // A token belongs to one account: re-registering it under another user removes the old link.
        devices.entries.removeAll { it.value.token == record.token && it.key != key(record.uid, record.deviceId) }
        devices[key(record.uid, record.deviceId)] = record
    }

    override suspend fun unregisterDevice(uid: String, deviceId: String) = lock.withLock { devices.remove(key(uid, deviceId)); Unit }
    override suspend fun devices(uid: String) = lock.withLock { devices.values.filter { it.uid == uid } }
    override suspend fun removeToken(token: String) = lock.withLock { devices.entries.removeAll { it.value.token == token }; Unit }

    private val reminderDocs = HashMap<String, EarningsReminderDocument>()
    private val deliveries = LinkedHashMap<String, EarningsNotificationDelivery>()
    override suspend fun <T> updateEarningsReminders(uid: String, block: (EarningsReminderDocument?) -> Pair<EarningsReminderDocument?, T>): T = lock.withLock {
        val (next, result) = block(reminderDocs[uid])
        if (next == null) reminderDocs.remove(uid) else reminderDocs[uid] = next
        result
    }
    override suspend fun earningsReminderUsers(limit: Int) = lock.withLock { reminderDocs.values.filter { it.active }.take(limit) }
    override suspend fun updateEarningsDelivery(key: String, block: (EarningsNotificationDelivery?) -> EarningsNotificationDelivery?) = lock.withLock {
        block(deliveries[key])?.also { deliveries[key] = it } ?: deliveries[key]
    }
    override suspend fun dueEarningsDeliveries(now: Long, limit: Int) = lock.withLock {
        deliveries.values.filter { (it.status == org.example.stocksteps.earnings.NotificationDeliveryStatus.PENDING && it.dueAt <= now) ||
            (it.status == org.example.stocksteps.earnings.NotificationDeliveryStatus.PROCESSING && it.leaseUntil <= now) }.sortedBy { it.dueAt }.take(limit)
    }
    override suspend fun earningsDeliveries(uid: String, limit: Int) = lock.withLock { deliveries.values.filter { it.uid == uid }.sortedByDescending { it.scheduledFor }.take(limit) }
    /** Test/diagnostic view of the outbox. */
    suspend fun outboxSnapshot(): List<OutboxItem> = lock.withLock { outbox.values.toList() }

    private fun key(uid: String, deviceId: String) = "$uid|$deviceId"
}

/**
 * REAL without storage credentials: every user-data request fails with 503 USER_DATA_UNAVAILABLE.
 * Deliberately not an in-memory fallback, which would silently lose users' watchlists and alerts.
 */
object UnavailableUserDataStore : UserDataStore {
    override suspend fun <T> updatePortfolio(uid: String, block: (org.example.stocksteps.portfolio.PortfolioLedger) -> Pair<org.example.stocksteps.portfolio.PortfolioLedger, T>): T = unavailable()
    private fun unavailable(): Nothing =
        throw UserDataException(503, "USER_DATA_UNAVAILABLE", "Watchlists and alerts are temporarily unavailable.")
    override suspend fun <T> updateWatchlists(uid: String, block: (UserWatchlists) -> Pair<UserWatchlists, T>): T = unavailable()
    override suspend fun entitlement(uid: String): StoredEntitlement? = unavailable()
    override suspend fun saveBrief(brief: org.example.stocksteps.brief.DailyBrief) = unavailable()
    override suspend fun brief(id: String): org.example.stocksteps.brief.DailyBrief? = unavailable()
    override suspend fun recentBriefs(limit: Int): List<org.example.stocksteps.brief.BriefSummary> = unavailable()
    override suspend fun <T> updateBriefPreferences(uid: String, block: (org.example.stocksteps.brief.BriefPreferenceRecord?) -> Pair<org.example.stocksteps.brief.BriefPreferenceRecord?, T>): T = unavailable()
    override suspend fun briefSubscribers(limit: Int): List<org.example.stocksteps.brief.BriefPreferenceRecord> = unavailable()
    override suspend fun <T> updatePractice(uid: String, block: (org.example.stocksteps.practice.PracticeAccountData) -> Pair<org.example.stocksteps.practice.PracticeAccountData, T>): T = unavailable()
    override suspend fun <T> updateLearning(uid: String, block: (org.example.stocksteps.learning.LearningProgressDocument) -> Pair<org.example.stocksteps.learning.LearningProgressDocument, T>): T = unavailable()
    override suspend fun <T> updateSavedScreens(uid: String, block: (List<org.example.stocksteps.screener.SavedScreen>) -> Pair<List<org.example.stocksteps.screener.SavedScreen>, T>): T = unavailable()
    override suspend fun setEntitlement(uid: String, value: StoredEntitlement?) = unavailable()
    override suspend fun <T> updateAlerts(uid: String, block: (List<AlertRule>) -> Pair<List<AlertRule>, T>): T = unavailable()
    override suspend fun activeAlerts(limit: Int): List<OwnedAlert> = unavailable()
    override suspend fun updateRule(uid: String, ruleId: String, update: (AlertRule) -> AlertRule?) = unavailable()
    override suspend fun recordTrigger(uid: String, event: AlertEvent, ruleUpdate: (AlertRule) -> AlertRule, outbox: List<OutboxItem>): Boolean = unavailable()
    override suspend fun history(uid: String, limit: Int): List<AlertEvent> = unavailable()
    override suspend fun setEventDelivery(uid: String, eventId: String, delivery: DeliveryStatus) = unavailable()
    override suspend fun dueOutbox(now: Long, limit: Int): List<OutboxItem> = unavailable()
    override suspend fun claimOutbox(id: String, now: Long, leaseMillis: Long): OutboxItem? = unavailable()
    override suspend fun finishOutbox(id: String, status: OutboxStatus, error: String?, retryable: Boolean) = unavailable()
    override suspend fun outboxForEvent(eventId: String): List<OutboxItem> = unavailable()
    override suspend fun registerDevice(record: DeviceRecord) = unavailable()
    override suspend fun unregisterDevice(uid: String, deviceId: String) = unavailable()
    override suspend fun devices(uid: String): List<DeviceRecord> = unavailable()
    override suspend fun removeToken(token: String) = unavailable()
    override suspend fun <T> updateEarningsReminders(uid: String, block: (EarningsReminderDocument?) -> Pair<EarningsReminderDocument?, T>): T = unavailable()
    override suspend fun earningsReminderUsers(limit: Int): List<EarningsReminderDocument> = unavailable()
    override suspend fun updateEarningsDelivery(key: String, block: (EarningsNotificationDelivery?) -> EarningsNotificationDelivery?): EarningsNotificationDelivery? = unavailable()
    override suspend fun dueEarningsDeliveries(now: Long, limit: Int): List<EarningsNotificationDelivery> = unavailable()
    override suspend fun earningsDeliveries(uid: String, limit: Int): List<EarningsNotificationDelivery> = unavailable()
}
