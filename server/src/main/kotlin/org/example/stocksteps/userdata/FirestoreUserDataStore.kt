package org.example.stocksteps.userdata

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import java.util.concurrent.TimeUnit

/**
 * REAL user data in Firestore, written only by the backend (rules deny these paths to clients):
 *
 * - `users/{uid}/watchlists/{watchlistId}` — `data`: Watchlist JSON (entries and notes inside).
 * - `users/{uid}/meta/watchlists` — `initialized`: default list / legacy import done.
 * - `users/{uid}/meta/screens` — `data`: JSON list of SavedScreen (filter definitions only).
 * - `dailyBriefs/{briefId}` — `generatedAt`, `data`: DailyBrief JSON (global, shared by everyone; no user data).
 * - `briefPreferences/{uid}` — `enabled`, `data`: BriefPreferenceRecord JSON (notification settings).
 * - `users/{uid}/practice/account` — `data`: PracticeAccountData JSON (simulated ledger, trial, challenges, idempotency keys).
 * - `users/{uid}/meta/learning` — `data`: LearningProgressDocument JSON (Guided Research progress).
 * - `users/{uid}/meta/entitlements` — `plan`, `expiresAt`, `data`: StoredEntitlement JSON (billing writes it).
 * - `alertRules/{ruleId}` — `ownerUid`, `status`, `data`: AlertRule JSON.
 * - `alertEvents/{eventId}` — `ownerUid`, `triggeredAt`, `data`: AlertEvent JSON (id = idempotency key).
 * - `notificationOutbox/{id}` — `status`, `leaseUntil`, `eventId`, `data`: OutboxItem JSON.
 * - `pushDevices/{uid|deviceId}` — `ownerUid`, `token`, `data`: DeviceRecord JSON.
 *
 * Records are stored as JSON strings plus the few fields queries need, so the schema stays exactly
 * the shared models. Legacy `users/{uid}/watchlist/{symbol}` documents are only read (import).
 */
class FirestoreUserDataStore(private val db: Firestore) : UserDataStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun <T> updatePortfolio(uid: String, block: (org.example.stocksteps.portfolio.PortfolioLedger) -> Pair<org.example.stocksteps.portfolio.PortfolioLedger, T>): T = io {
        val reference = db.collection("users").document(uid).collection("portfolio").document("ledger")
        db.runTransaction { tx ->
            val doc = tx.get(reference).get()
            val serializer = org.example.stocksteps.portfolio.PortfolioLedger.serializer()
            val current = doc.getString("data")?.let { decode(serializer, it) } ?: org.example.stocksteps.portfolio.PortfolioLedger()
            val (next, result) = block(current)
            if (next != current) tx.set(reference, mapOf("data" to encode(serializer, next), "revision" to next.revision))
            result
        }.await()
    }

    override suspend fun <T> updateSavedScreens(uid: String, block: (List<org.example.stocksteps.screener.SavedScreen>) -> Pair<List<org.example.stocksteps.screener.SavedScreen>, T>): T = io {
        val reference = db.collection("users").document(uid).collection("meta").document("screens")
        val serializer = kotlinx.serialization.builtins.ListSerializer(org.example.stocksteps.screener.SavedScreen.serializer())
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(serializer, it) }.orEmpty()
            val (next, result) = block(current)
            if (next != current) tx.set(reference, mapOf("data" to encode(serializer, next)))
            result
        }.await()
    }

    override suspend fun saveBrief(brief: org.example.stocksteps.brief.DailyBrief) = io {
        db.collection("dailyBriefs").document(brief.id).set(mapOf("generatedAt" to brief.generatedAt, "data" to encode(org.example.stocksteps.brief.DailyBrief.serializer(), brief))).await()
        Unit
    }
    override suspend fun brief(id: String): org.example.stocksteps.brief.DailyBrief? = io {
        db.collection("dailyBriefs").document(id).get().await().getString("data")?.let { decode(org.example.stocksteps.brief.DailyBrief.serializer(), it) }
    }
    override suspend fun recentBriefs(limit: Int): List<org.example.stocksteps.brief.BriefSummary> = io {
        db.collection("dailyBriefs").orderBy("generatedAt", com.google.cloud.firestore.Query.Direction.DESCENDING).limit(limit).get().await().documents
            .mapNotNull { it.getString("data")?.let { text -> runCatching { decode(org.example.stocksteps.brief.DailyBrief.serializer(), text).summary() }.getOrNull() } }
    }
    override suspend fun <T> updateBriefPreferences(uid: String, block: (org.example.stocksteps.brief.BriefPreferenceRecord?) -> Pair<org.example.stocksteps.brief.BriefPreferenceRecord?, T>): T = io {
        val reference = db.collection("briefPreferences").document(uid)
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(org.example.stocksteps.brief.BriefPreferenceRecord.serializer(), it) }
            val (next, result) = block(current)
            if (next == null) { if (current != null) tx.delete(reference) }
            else if (next != current) tx.set(reference, mapOf("enabled" to next.preferences.notificationsEnabled, "data" to encode(org.example.stocksteps.brief.BriefPreferenceRecord.serializer(), next)))
            result
        }.await()
    }
    override suspend fun briefSubscribers(limit: Int): List<org.example.stocksteps.brief.BriefPreferenceRecord> = io {
        db.collection("briefPreferences").whereEqualTo("enabled", true).limit(limit).get().await().documents
            .mapNotNull { it.getString("data")?.let { text -> runCatching { decode(org.example.stocksteps.brief.BriefPreferenceRecord.serializer(), text) }.getOrNull() } }
    }

    // Earnings reminders (Phase 4). `earningsReminders/{uid}`: active flag + JSON; `earningsDeliveries/{sha(key)}`:
    // status, dueAt, uid + JSON. Needs a composite index on earningsDeliveries (status ASC, dueAt ASC).
    private val reminderDocs get() = db.collection("earningsReminders")
    private val deliveryDocs get() = db.collection("earningsDeliveries")
    private fun deliveryDoc(d: EarningsNotificationDelivery) = mapOf("uid" to d.uid, "status" to d.status.name, "dueAt" to d.dueAt,
        "leaseUntil" to d.leaseUntil, "data" to encode(EarningsNotificationDelivery.serializer(), d))
    override suspend fun <T> updateEarningsReminders(uid: String, block: (EarningsReminderDocument?) -> Pair<EarningsReminderDocument?, T>): T = io {
        val reference = reminderDocs.document(uid)
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(EarningsReminderDocument.serializer(), it) }
            val (next, result) = block(current)
            if (next == null) { if (current != null) tx.delete(reference) }
            else if (next != current) tx.set(reference, mapOf("active" to next.active, "data" to encode(EarningsReminderDocument.serializer(), next)))
            result
        }.await()
    }
    override suspend fun earningsReminderUsers(limit: Int): List<EarningsReminderDocument> = io {
        reminderDocs.whereEqualTo("active", true).limit(limit).get().await().documents
            .mapNotNull { it.getString("data")?.let { text -> runCatching { decode(EarningsReminderDocument.serializer(), text) }.getOrNull() } }
    }
    override suspend fun updateEarningsDelivery(key: String, block: (EarningsNotificationDelivery?) -> EarningsNotificationDelivery?): EarningsNotificationDelivery? = io {
        val reference = deliveryDocs.document(docId(key))
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(EarningsNotificationDelivery.serializer(), it) }
            val next = block(current)
            if (next != null && next != current) tx.set(reference, deliveryDoc(next))
            next ?: current
        }.await()
    }
    override suspend fun dueEarningsDeliveries(now: Long, limit: Int): List<EarningsNotificationDelivery> = io {
        val pending = deliveryDocs.whereEqualTo("status", "PENDING").whereLessThanOrEqualTo("dueAt", now).limit(limit).get().await().documents
        val stuck = deliveryDocs.whereEqualTo("status", "PROCESSING").whereLessThanOrEqualTo("leaseUntil", now).limit(limit).get().await().documents
        (pending + stuck).mapNotNull { it.getString("data")?.let { text -> runCatching { decode(EarningsNotificationDelivery.serializer(), text) }.getOrNull() } }.take(limit)
    }
    override suspend fun earningsDeliveries(uid: String, limit: Int): List<EarningsNotificationDelivery> = io {
        deliveryDocs.whereEqualTo("uid", uid).limit(limit).get().await().documents
            .mapNotNull { it.getString("data")?.let { text -> runCatching { decode(EarningsNotificationDelivery.serializer(), text) }.getOrNull() } }
            .sortedByDescending { it.scheduledFor }
    }

    override suspend fun <T> updatePractice(uid: String, block: (org.example.stocksteps.practice.PracticeAccountData) -> Pair<org.example.stocksteps.practice.PracticeAccountData, T>): T = io {
        val reference = db.collection("users").document(uid).collection("practice").document("account")
        val serializer = org.example.stocksteps.practice.PracticeAccountData.serializer()
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(serializer, it) } ?: org.example.stocksteps.practice.PracticeAccountData()
            val (next, result) = block(current)
            if (next != current) tx.set(reference, mapOf("data" to encode(serializer, next), "revision" to next.revision))
            result
        }.await()
    }

    override suspend fun <T> updateLearning(uid: String, block: (org.example.stocksteps.learning.LearningProgressDocument) -> Pair<org.example.stocksteps.learning.LearningProgressDocument, T>): T = io {
        val reference = db.collection("users").document(uid).collection("meta").document("learning")
        val serializer = org.example.stocksteps.learning.LearningProgressDocument.serializer()
        db.runTransaction { tx ->
            val current = tx.get(reference).get().getString("data")?.let { decode(serializer, it) } ?: org.example.stocksteps.learning.LearningProgressDocument()
            val (next, result) = block(current)
            if (next != current) tx.set(reference, mapOf("data" to encode(serializer, next)))
            result
        }.await()
    }

    private fun entitlementDoc(uid: String) = db.collection("users").document(uid).collection("meta").document("entitlements")
    override suspend fun entitlement(uid: String): StoredEntitlement? = io {
        entitlementDoc(uid).get().await().getString("data")?.let { decode(StoredEntitlement.serializer(), it) }
    }
    override suspend fun setEntitlement(uid: String, value: StoredEntitlement?) = io {
        if (value == null) entitlementDoc(uid).delete().await()
        else entitlementDoc(uid).set(mapOf("plan" to value.plan.name, "expiresAt" to value.expiresAt, "data" to encode(StoredEntitlement.serializer(), value))).await()
        Unit
    }

    private fun watchlistsOf(uid: String) = db.collection("users").document(uid).collection("watchlists")
    private fun meta(uid: String) = db.collection("users").document(uid).collection("meta").document("watchlists")
    private val rules get() = db.collection("alertRules")
    private val events get() = db.collection("alertEvents")
    private val outbox get() = db.collection("notificationOutbox")
    private val devices get() = db.collection("pushDevices")

    override suspend fun <T> updateWatchlists(uid: String, block: (UserWatchlists) -> Pair<UserWatchlists, T>): T = io {
        db.runTransaction { tx ->
            val docs = tx.get(watchlistsOf(uid)).get().documents
            val current = docs.mapNotNull { it.getString("data")?.let { data -> decode(Watchlist.serializer(), data) } }
            val initialized = tx.get(meta(uid)).get().getBoolean("initialized") == true
            val (next, result) = block(UserWatchlists(current.sortedBy { it.order }, initialized))
            val keep = next.watchlists.map { it.id }.toSet()
            docs.filter { it.id !in keep }.forEach { tx.delete(it.reference) }
            next.watchlists.forEach { list ->
                if (current.find { it.id == list.id } != list) tx.set(watchlistsOf(uid).document(list.id), mapOf("data" to encode(Watchlist.serializer(), list)))
            }
            if (next.initialized != initialized) tx.set(meta(uid), mapOf("initialized" to next.initialized))
            result
        }.await()
    }

    override suspend fun legacyWatchlist(uid: String): List<InstrumentRef> = io {
        db.collection("users").document(uid).collection("watchlist").get().await().documents.mapNotNull { doc ->
            val symbol = doc.getString("symbol") ?: return@mapNotNull null
            InstrumentRef(symbol, doc.getString("name"), doc.getString("exchange"), doc.getString("currency")) to (doc.getLong("addedAt") ?: 0L)
        }.sortedBy { it.second }.map { it.first }
    }

    override suspend fun <T> updateAlerts(uid: String, block: (List<AlertRule>) -> Pair<List<AlertRule>, T>): T = io {
        db.runTransaction { tx ->
            val docs = tx.get(rules.whereEqualTo("ownerUid", uid)).get().documents
            val current = docs.mapNotNull { it.getString("data")?.let { data -> decode(AlertRule.serializer(), data) } }
            val (next, result) = block(current)
            val keep = next.map { it.id }.toSet()
            docs.filter { it.id !in keep }.forEach { tx.delete(it.reference) }
            next.forEach { rule -> if (current.find { it.id == rule.id } != rule) tx.set(rules.document(rule.id), ruleDoc(uid, rule)) }
            result
        }.await()
    }

    override suspend fun activeAlerts(limit: Int): List<OwnedAlert> = io {
        rules.whereEqualTo("status", AlertStatus.ACTIVE.name).limit(limit).get().await().documents.mapNotNull { doc ->
            val uid = doc.getString("ownerUid") ?: return@mapNotNull null
            doc.getString("data")?.let { OwnedAlert(uid, decode(AlertRule.serializer(), it)) }
        }
    }

    override suspend fun updateRule(uid: String, ruleId: String, update: (AlertRule) -> AlertRule?) = io {
        db.runTransaction { tx ->
            val doc = tx.get(rules.document(ruleId)).get()
            if (doc.exists() && doc.getString("ownerUid") == uid) {
                val rule = decode(AlertRule.serializer(), doc.getString("data")!!)
                val next = update(rule)
                if (next == null) tx.delete(doc.reference) else if (next != rule) tx.set(doc.reference, ruleDoc(uid, next))
            }
        }.await()
        Unit
    }

    override suspend fun recordTrigger(uid: String, event: AlertEvent, ruleUpdate: (AlertRule) -> AlertRule, outbox: List<OutboxItem>): Boolean = io {
        db.runTransaction { tx ->
            val eventRef = events.document(event.id)
            val ruleRef = rules.document(event.ruleId)
            val existing = tx.get(eventRef).get()
            val ruleDoc = tx.get(ruleRef).get()
            if (existing.exists() || !ruleDoc.exists() || ruleDoc.getString("ownerUid") != uid) return@runTransaction false
            val rule = decode(AlertRule.serializer(), ruleDoc.getString("data")!!)
            tx.set(eventRef, mapOf("ownerUid" to uid, "triggeredAt" to event.triggeredAt, "data" to encode(AlertEvent.serializer(), event)))
            tx.set(ruleRef, ruleDoc(uid, ruleUpdate(rule)))
            outbox.forEach { item -> tx.set(this.outbox.document(docId(item.id)), outboxDoc(item)) }
            true
        }.await()
    }

    override suspend fun history(uid: String, limit: Int): List<AlertEvent> = io {
        events.whereEqualTo("ownerUid", uid).get().await().documents
            .mapNotNull { it.getString("data")?.let { data -> decode(AlertEvent.serializer(), data) } }
            .sortedByDescending { it.triggeredAt }.take(limit)
    }

    override suspend fun setEventDelivery(uid: String, eventId: String, delivery: DeliveryStatus) = io {
        db.runTransaction { tx ->
            val doc = tx.get(events.document(eventId)).get()
            if (doc.exists() && doc.getString("ownerUid") == uid) {
                val event = decode(AlertEvent.serializer(), doc.getString("data")!!)
                tx.update(doc.reference, "data", encode(AlertEvent.serializer(), event.copy(delivery = delivery)))
            }
        }.await()
        Unit
    }

    override suspend fun dueOutbox(now: Long, limit: Int): List<OutboxItem> = io {
        val pending = outbox.whereEqualTo("status", OutboxStatus.PENDING.name).limit(limit).get().await().documents
        // Single-field equality queries only (no composite index needed); expired leases filtered here.
        val stuck = outbox.whereEqualTo("status", OutboxStatus.SENDING.name).limit(limit).get().await().documents
            .filter { (it.getLong("leaseUntil") ?: 0L) <= now }
        (pending + stuck).mapNotNull { it.getString("data")?.let { data -> decode(OutboxItem.serializer(), data) } }.take(limit)
    }

    override suspend fun claimOutbox(id: String, now: Long, leaseMillis: Long): OutboxItem? = io {
        db.runTransaction { tx ->
            val doc = tx.get(outbox.document(docId(id))).get()
            if (!doc.exists()) return@runTransaction null
            val item = decode(OutboxItem.serializer(), doc.getString("data")!!)
            val due = item.status == OutboxStatus.PENDING || (item.status == OutboxStatus.SENDING && item.leaseUntil <= now)
            if (!due) return@runTransaction null
            val claimed = item.copy(status = OutboxStatus.SENDING, attempts = item.attempts + 1, leaseUntil = now + leaseMillis)
            tx.set(doc.reference, outboxDoc(claimed))
            claimed
        }.await()
    }

    override suspend fun finishOutbox(id: String, status: OutboxStatus, error: String?, retryable: Boolean) = io {
        db.runTransaction { tx ->
            val doc = tx.get(outbox.document(docId(id))).get()
            if (doc.exists()) {
                val item = decode(OutboxItem.serializer(), doc.getString("data")!!)
                tx.set(doc.reference, outboxDoc(item.copy(status = if (retryable) OutboxStatus.PENDING else status, lastError = error, leaseUntil = 0)))
            }
        }.await()
        Unit
    }

    override suspend fun outboxForEvent(eventId: String): List<OutboxItem> = io {
        outbox.whereEqualTo("eventId", eventId).get().await().documents.mapNotNull { it.getString("data")?.let { data -> decode(OutboxItem.serializer(), data) } }
    }

    override suspend fun registerDevice(record: DeviceRecord) = io {
        db.runTransaction { tx ->
            val sameToken = tx.get(devices.whereEqualTo("token", record.token)).get().documents
            val id = docId("${record.uid}|${record.deviceId}")
            sameToken.filter { it.id != id }.forEach { tx.delete(it.reference) }
            tx.set(devices.document(id), mapOf("ownerUid" to record.uid, "token" to record.token, "data" to encode(DeviceRecord.serializer(), record)))
        }.await()
        Unit
    }

    override suspend fun unregisterDevice(uid: String, deviceId: String) = io {
        devices.document(docId("$uid|$deviceId")).delete().await()
        Unit
    }

    override suspend fun devices(uid: String): List<DeviceRecord> = io {
        devices.whereEqualTo("ownerUid", uid).get().await().documents.mapNotNull { it.getString("data")?.let { data -> decode(DeviceRecord.serializer(), data) } }
    }

    override suspend fun removeToken(token: String) = io {
        devices.whereEqualTo("token", token).get().await().documents.forEach { it.reference.delete().await() }
    }

    private fun ruleDoc(uid: String, rule: AlertRule) = mapOf("ownerUid" to uid, "status" to rule.status.name, "data" to encode(AlertRule.serializer(), rule))
    private fun outboxDoc(item: OutboxItem) = mapOf("status" to item.status.name, "leaseUntil" to item.leaseUntil, "eventId" to item.eventId, "data" to encode(OutboxItem.serializer(), item))
    /** Firestore ids can't contain '/'; ids here are built from safe characters, '|' is replaced. */
    private fun docId(id: String) = id.replace('/', '_')

    private fun <T> encode(serializer: KSerializer<T>, value: T) = json.encodeToString(serializer, value)
    private fun <T> decode(serializer: KSerializer<T>, value: String) = json.decodeFromString(serializer, value)

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
    private fun <T> ApiFuture<T>.await(): T = get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private companion object { const val OPERATION_TIMEOUT_SECONDS = 15L }
}

