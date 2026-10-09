package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.example.stocksteps.userdata.AiCharge
import org.example.stocksteps.userdata.AiUsageDocument
import org.example.stocksteps.userdata.UserDataStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One AI feature's allowance (Phase 4B). [dailyLimit] per UTC calendar day; optional [windowLimit] per rolling [windowDays].
 * [feature] tags the charges in the shared `users/{uid}/meta/aiUsage` document.
 */
data class AiQuotaPolicy(val feature: String, val dailyLimit: Int, val windowLimit: Int? = null, val windowDays: Int = 30) {
    /** How long this feature's charges must be kept: its rolling window, or two days for daily-only limits. */
    val retentionMillis: Long get() = (if (windowLimit != null) windowDays.toLong() else 2L) * 86_400_000L
}

/** Optional combined StockSteps+ daily cap across [features] (Phase 4B; **disabled unless the owner configures it**, decision D6). */
data class CombinedAiCap(val features: Set<String>, val dailyLimit: Int)

class AiQuotaDenied(val code: String, val limit: Int, val nextAvailable: Instant, val window: Boolean, val combined: Boolean = false) : Exception(code)

/** The quota store couldn't be read or written: premium AI fails closed (no reservation → no provider call). */
class AiQuotaUnavailable(cause: Throwable) : Exception("AI quota storage unavailable", cause)

/**
 * Durable, cross-instance AI quotas (Phase 4B), generalising `ComparisonAiQuota`: every reservation is a Firestore transaction on the
 * user's `aiUsage` document (an in-memory store in MOCK/tests), so restarts, other Cloud Run instances, concurrent requests and other
 * devices all see the same count. Lifecycle: entitlement and validation happen first (callers); [reserve] appends a charge **before** the
 * provider call (an idempotency [key] already charged returns the same charge: retries are free); on an eligible failure [settleFailure]
 * removes it; a crash after reserving keeps the charge (never grants extra). Timeouts and cancellations keep the charge, because the
 * provider may already have produced (and billed) a result.
 */
class DurableAiQuota(
    private val store: UserDataStore,
    private val clock: Clock,
    private val combined: CombinedAiCap? = null,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    class Reservation internal constructor(val uid: String, val feature: String, val chargeId: String, val duplicate: Boolean) { @Volatile var settled = false }

    private sealed interface Outcome {
        class Ok(val r: Reservation) : Outcome
        class Denied(val e: AiQuotaDenied) : Outcome
    }

    private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    private fun nextMidnight(now: Long): Instant = day(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    /** Keeps every charge still inside its feature's retention (unknown features: the longest known window), so one feature never prunes another. */
    private fun live(doc: AiUsageDocument, now: Long) = doc.charges.filter { now - it.at < (RETENTION[it.feature] ?: MAX_RETENTION) }

    private suspend fun <T> transact(uid: String, block: (AiUsageDocument) -> Pair<AiUsageDocument, T>): T = try {
        store.updateAiUsage(uid, block)
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        meter.event("ai.quota.storeError")
        throw AiQuotaUnavailable(cause)
    }

    suspend fun reserve(uid: String, policy: AiQuotaPolicy, key: String = newKey()): Reservation {
        require(key.length in 1..80)
        val now = clock.millis()
        val id = "c" + now.toString(36) + java.util.UUID.randomUUID().toString().take(8)
        val outcome = transact(uid) { doc ->
            val charges = live(doc, now)
            val existing = charges.firstOrNull { it.key == key && it.feature == policy.feature }
            val mine = charges.filter { it.feature == policy.feature }
            val today = mine.count { day(it.at) == day(now) }
            val window = policy.windowLimit?.let { mine.filter { now - it.at < policy.windowDays * 86_400_000L } }
            val combinedToday = combined?.takeIf { policy.feature in it.features }?.let { cap -> charges.count { it.feature in cap.features && day(it.at) == day(now) } }
            when {
                existing != null -> AiUsageDocument(charges) to Outcome.Ok(Reservation(uid, policy.feature, existing.id, duplicate = true))
                today >= policy.dailyLimit -> AiUsageDocument(charges) to Outcome.Denied(AiQuotaDenied("AI_DAILY_LIMIT", policy.dailyLimit, nextMidnight(now), window = false))
                window != null && window.size >= policy.windowLimit -> AiUsageDocument(charges) to Outcome.Denied(AiQuotaDenied("AI_QUOTA_EXCEEDED", policy.windowLimit,
                    Instant.ofEpochMilli(window.minOf { it.at } + policy.windowDays * 86_400_000L), window = true))
                combined != null && combinedToday != null && combinedToday >= combined.dailyLimit -> AiUsageDocument(charges) to
                    Outcome.Denied(AiQuotaDenied("AI_DAILY_LIMIT", combined.dailyLimit, nextMidnight(now), window = false, combined = true))
                else -> AiUsageDocument(charges + AiCharge(id, policy.feature, now, key)) to Outcome.Ok(Reservation(uid, policy.feature, id, duplicate = false))
            }
        }
        return when (outcome) {
            is Outcome.Denied -> { meter.event("ai.quota.denied.${policy.feature}"); throw outcome.e }
            is Outcome.Ok -> outcome.r.also { meter.event(if (it.duplicate) "ai.quota.retry.${policy.feature}" else "ai.quota.reserved.${policy.feature}") }
        }
    }

    /** Removes the charge (a failure that produced nothing: provider error, invalid output, coalesced or cached answer). */
    suspend fun release(r: Reservation) {
        if (r.duplicate || r.settled) return
        r.settled = true
        try { transact(r.uid) { doc -> doc.copy(charges = doc.charges.filterNot { it.id == r.chargeId }) to Unit } } catch (cause: AiQuotaUnavailable) {
            // The charge stays: the user loses one request rather than gaining one.
            org.slf4j.LoggerFactory.getLogger("StockSteps.AiQuota").warn("Quota release failed for {}", r.feature)
        }
    }

    /**
     * Failure policy: a timeout or cancellation keeps the charge (the provider may have produced a billed result); anything else that
     * [refundable] accepts is released. Returns whether it was refunded.
     */
    suspend fun settleFailure(r: Reservation, cause: Throwable, refundable: (Throwable) -> Boolean = { true }): Boolean {
        val ambiguous = cause is TimeoutCancellationException || cause is CancellationException || !refundable(cause)
        if (ambiguous) { r.settled = true; meter.event("ai.quota.kept.${r.feature}"); return false }
        release(r); return true
    }

    /** Charges for [features] today and inside each feature's window (read in a transaction without writing). */
    suspend fun usage(uid: String, policies: List<AiQuotaPolicy>): Map<String, Pair<Int, Int>> {
        val now = clock.millis()
        val charges = transact(uid) { doc -> doc to live(doc, now) }
        return policies.associate { p ->
            val mine = charges.filter { it.feature == p.feature }
            p.feature to (mine.count { day(it.at) == day(now) } to mine.count { now - it.at < p.windowDays * 86_400_000L })
        }
    }

    /** MOCK scenario: uses the rest of today's allowance for [policy]. */
    suspend fun exhaust(uid: String, policy: AiQuotaPolicy) {
        val now = clock.millis()
        transact(uid) { doc ->
            val charges = live(doc, now)
            val today = charges.count { it.feature == policy.feature && day(it.at) == day(now) }
            AiUsageDocument(charges + (today until policy.dailyLimit).map { AiCharge("mock-$it-$now", policy.feature, now) }) to Unit
        }
    }

    fun resetAt(): Instant = nextMidnight(clock.millis())

    companion object {
        private fun newKey() = "k" + java.util.UUID.randomUUID().toString().replace("-", "").take(30)
        /** Retention of every known feature (Comparison AI keeps 30 days for its rolling window). */
        val RETENTION: Map<String, Long> = mapOf("comparison-ai" to 30 * 86_400_000L, "brief-ai" to 2 * 86_400_000L,
            "earnings-ai-explanation" to 2 * 86_400_000L, "earnings-ai-question" to 2 * 86_400_000L, "earnings-ai-digest" to 2 * 86_400_000L)
        const val MAX_RETENTION = 31 * 86_400_000L
        /** A client idempotency key: 8–64 letters, digits, '-' or '_'; anything else is ignored (a fresh key is used). */
        fun clientKey(raw: String?): String? = raw?.takeIf { Regex("[A-Za-z0-9_-]{8,64}").matches(it) }
    }
}
