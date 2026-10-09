package org.example.stocksteps.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import org.example.stocksteps.repository.StockProviderException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Who is waiting for a provider call (Phase 4C). Interactive requests are HIGH by default. */
enum class ProviderPriority { HIGH, NORMAL, LOW }

/** Marks a coroutine's provider calls as [priority] (screener warm-up: LOW; scheduled jobs: NORMAL). */
class ProviderPriorityElement(val priority: ProviderPriority) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ProviderPriorityElement>
}

/**
 * Plan-level limits for one provider. All values describe the **whole deployment**; each instance enforces
 * `value × safetyMargin ÷ maxInstances` ([ProviderBudgetConfig.local]). [dailySoftTarget] only raises an alert event.
 */
data class ProviderLimits(val perMinute: Int, val burst: Int = perMinute, val maxConcurrent: Int, val dailySoftTarget: Long? = null)

data class ProviderBudgetConfig(
    val limits: Map<String, ProviderLimits>,
    val safetyMargin: Double = 0.8,
    /** The deployment's verified maximum Cloud Run instance count; 1 when unknown (then local limits are per-instance only). */
    val maxInstances: Int = 1,
    /** Share of each bucket that only HIGH (interactive) requests may use; NORMAL may use half of it. */
    val interactiveReserve: Double = 0.3,
    val maxWaitMillis: Map<ProviderPriority, Long> = mapOf(ProviderPriority.HIGH to 2_000, ProviderPriority.NORMAL to 1_000, ProviderPriority.LOW to 0),
    /** Waiting for a free concurrency slot costs no budget, so background work may wait longer for one than for a token. */
    val slotWaitMillis: Map<ProviderPriority, Long> = mapOf(ProviderPriority.HIGH to 2_000, ProviderPriority.NORMAL to 5_000, ProviderPriority.LOW to 10_000),
    /** Whether the values came from verified plan limits and instance count (false = development defaults; production readiness blocked). */
    val verified: Boolean = false
) {
    init { require(safetyMargin in 0.1..1.0 && maxInstances >= 1 && interactiveReserve in 0.0..0.9) }

    fun local(l: ProviderLimits) = ProviderLimits(
        perMinute = (l.perMinute * safetyMargin / maxInstances).toInt().coerceAtLeast(1),
        burst = (l.burst * safetyMargin / maxInstances).toInt().coerceAtLeast(1),
        maxConcurrent = (l.maxConcurrent / maxInstances).coerceAtLeast(1),
        dailySoftTarget = l.dailySoftTarget?.let { (it / maxInstances).coerceAtLeast(1) }
    )

    companion object {
        /**
         * Development defaults (no plan limits configured): generous enough for normal use, finite so a bug or abuse can't make unlimited
         * calls. They are **not** derived from provider plans. Production must set `PROVIDER_<NAME>_PER_MINUTE`, `_BURST`, `_CONCURRENCY`,
         * `_DAILY_TARGET`, `PROVIDER_SAFETY_MARGIN` and `CLOUD_RUN_MAX_INSTANCES` (decisions D4/D5).
         */
        val DEVELOPMENT = mapOf(
            "fmp" to ProviderLimits(perMinute = 600, burst = 600, maxConcurrent = 24),
            "finnhub" to ProviderLimits(perMinute = 60, burst = 30, maxConcurrent = 8),
            "gemini" to ProviderLimits(perMinute = 60, burst = 20, maxConcurrent = 4),
            "boc" to ProviderLimits(perMinute = 30, burst = 10, maxConcurrent = 2)
        )

        fun fromEnvironment(env: (String) -> String? = System::getenv): ProviderBudgetConfig {
            var verified = true
            val limits = DEVELOPMENT.mapValues { (name, d) ->
                fun value(suffix: String) = env("PROVIDER_${name.uppercase()}_$suffix")?.toLongOrNull()?.takeIf { it > 0 }
                val perMinute = value("PER_MINUTE")
                if (perMinute == null) verified = false
                ProviderLimits(perMinute?.toInt() ?: d.perMinute, value("BURST")?.toInt() ?: perMinute?.toInt() ?: d.burst,
                    value("CONCURRENCY")?.toInt() ?: d.maxConcurrent, value("DAILY_TARGET"))
            }
            val instances = env("CLOUD_RUN_MAX_INSTANCES")?.toIntOrNull()?.takeIf { it >= 1 }
            if (instances == null) verified = false
            return ProviderBudgetConfig(limits, env("PROVIDER_SAFETY_MARGIN")?.toDoubleOrNull()?.takeIf { it in 0.1..1.0 } ?: 0.8,
                instances ?: 1, verified = verified)
        }
    }
}

/**
 * Phase 4C provider-wide admission, shared by every route and job that uses a provider: one token bucket (requests per minute, burst),
 * one concurrency limit and a circuit breaker per provider on this instance. Every real upstream attempt (including retries) must
 * [acquire] a permit first and [complete] it with its outcome; nothing else counts `upstream`. Interactive (HIGH) work may use the whole
 * bucket and waits briefly; NORMAL and LOW (screener warm-up) can't dip into the interactive reserve and don't wait (LOW) — they're
 * deferred instead. A denial is a [StockProviderException] (`UNAVAILABLE`, no upstream status), so callers fall back exactly as for a
 * provider outage: cached/stale data, honest unavailability, AI fallbacks — never invented values.
 *
 * Circuit breaker: a 429 opens the provider for `Retry-After` (else 30 s, doubling while it repeats, ≤ 5 min); five consecutive 5xx/timeouts
 * open that endpoint the same way. After the pause one trial call is allowed (half-open); success closes it. 402/403 don't trip it
 * (dataset cooldowns handle access denial). With N instances, totals are bounded by N × local limits = the configured plan values only
 * when [ProviderBudgetConfig.maxInstances] matches the deployed maximum.
 */
class ProviderGuard(val config: ProviderBudgetConfig, private val now: () -> Long = System::currentTimeMillis,
                    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared,
                    /** Waiting for a token (tests advance a fake clock instead). */
                    private val sleep: suspend (Long) -> Unit = { delay(it) }) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ProviderGuard> {
        /** The process-wide guard installed at startup (REAL); null in tests unless a guard is put in the coroutine context. */
        @Volatile var installed: ProviderGuard? = null
        suspend fun current(): ProviderGuard? = currentCoroutineContext()[ProviderGuard] ?: installed
    }

    internal class Breaker {
        var failures = 0; var openUntil = 0L; var pause = 0L; var trialInFlight = false
    }
    private inner class Lane(val limits: ProviderLimits) {
        val permits = Semaphore(limits.maxConcurrent)
        var tokens = limits.burst.toDouble(); var refilledAt = now()
        val breaker = Breaker()
        val endpointBreakers = ConcurrentHashMap<String, Breaker>()
        var day = ""; val today = AtomicLong(); var warned = false
        fun refill(t: Long) {
            tokens = (tokens + (t - refilledAt) * limits.perMinute / 60_000.0).coerceAtMost(limits.burst.toDouble()); refilledAt = t
        }
    }
    private val lanes = ConcurrentHashMap<String, Lane>()
    private fun lane(provider: String): Lane? = config.limits[provider]?.let { l -> lanes.getOrPut(provider) { Lane(config.local(l)) } }

    class Permit internal constructor(val provider: String, val endpoint: String, internal val trial: Breaker?, internal val hasSlot: Boolean)

    /** Admits one upstream attempt or throws (denied: budget, concurrency or open circuit). Providers without limits pass. */
    suspend fun acquire(provider: String, endpoint: String): Permit {
        val lane = lane(provider) ?: return Permit(provider, endpoint, null, false)
        val priority = currentCoroutineContext()[ProviderPriorityElement]?.priority ?: ProviderPriority.HIGH
        val deadline = now() + (config.maxWaitMillis[priority] ?: 0)
        val endpointBreaker = lane.endpointBreakers.getOrPut(endpoint) { Breaker() }
        var trial: Breaker? = null
        // Circuit breakers first: an open circuit fails fast (no waiting, no token used).
        synchronized(lane) {
            for (b in listOf(lane.breaker, endpointBreaker)) {
                val t = now()
                if (b.openUntil > t) return deny(provider, endpoint, "circuitOpen")
                if (b.pause > 0) { if (b.trialInFlight) return deny(provider, endpoint, "circuitHalfOpen"); b.trialInFlight = true; trial = b }
            }
        }
        val reserve = when (priority) {
            ProviderPriority.HIGH -> 0.0
            ProviderPriority.NORMAL -> lane.limits.burst * config.interactiveReserve / 2
            ProviderPriority.LOW -> lane.limits.burst * config.interactiveReserve
        }
        while (true) {
            val waitMillis = synchronized(lane) {
                val t = now()
                lane.refill(t)
                if (lane.tokens - 1 >= reserve) { lane.tokens -= 1; 0L } else (((reserve + 1 - lane.tokens) * 60_000.0 / lane.limits.perMinute).toLong() + 1)
            }
            if (waitMillis == 0L) break
            if (now() + waitMillis > deadline) { trial?.let { synchronized(lane) { it.trialInFlight = false } }; return deny(provider, endpoint, if (priority == ProviderPriority.HIGH) "rate" else "deferred.${priority.name.lowercase()}") }
            sleep(waitMillis)
        }
        if (!lane.permits.tryAcquire()) {
            val waited = kotlinx.coroutines.withTimeoutOrNull(config.slotWaitMillis[priority] ?: 0) { lane.permits.acquire() }
            if (waited == null) { trial?.let { synchronized(lane) { it.trialInFlight = false } }; return deny(provider, endpoint, "concurrency") }
        }
        countDay(provider, lane)
        meter.event("provider.$provider.budget.admitted.${priority.name.lowercase()}")
        return Permit(provider, endpoint, trial, true)
    }

    private fun deny(provider: String, endpoint: String, reason: String): Nothing {
        meter.event("provider.$provider.budget.denied.$reason")
        meter.record(provider, endpoint, "budget", "budgetDenied")
        throw StockProviderException(StockProviderException.Failure.UNAVAILABLE)
    }

    private fun countDay(provider: String, lane: Lane) {
        val day = Instant.ofEpochMilli(now()).atZone(ZoneOffset.UTC).toLocalDate().toString()
        synchronized(lane) { if (lane.day != day) { lane.day = day; lane.today.set(0); lane.warned = false } }
        val n = lane.today.incrementAndGet()
        val target = lane.limits.dailySoftTarget ?: return
        if (n > target && synchronized(lane) { !lane.warned.also { lane.warned = true } }) meter.event("provider.$provider.dailyTargetExceeded")
    }

    /**
     * Ends an attempt: [status] is the HTTP status (null for a transport failure), [timedOut] a timeout, [retryAfterSeconds] the provider's
     * `Retry-After`. Always called (also on cancellation) so the concurrency slot is released.
     */
    fun complete(permit: Permit, status: Int?, timedOut: Boolean = false, retryAfterSeconds: Long? = null, cancelled: Boolean = false) {
        val lane = lanes[permit.provider] ?: return
        if (permit.hasSlot) lane.permits.release()
        if (cancelled) { permit.trial?.let { synchronized(lane) { it.trialInFlight = false } }; return }
        val endpointBreaker = lane.endpointBreakers.getOrPut(permit.endpoint) { Breaker() }
        synchronized(lane) {
            permit.trial?.trialInFlight = false
            val t = now()
            when {
                status == 429 -> trip(lane.breaker, t, retryAfterSeconds, permit.provider, "rateLimited")
                timedOut || status == null || status >= 500 -> {
                    endpointBreaker.failures++
                    if (endpointBreaker.failures >= 5 || permit.trial === endpointBreaker) trip(endpointBreaker, t, null, permit.provider, "failures")
                }
                else -> {   // 2xx–4xx (incl. 402/403/404): the provider answered; close any half-open breaker.
                    listOf(lane.breaker, endpointBreaker).forEach { b -> if (b.pause > 0 && b.openUntil <= t) { b.pause = 0; b.failures = 0 } }
                    endpointBreaker.failures = 0
                }
            }
        }
    }

    private fun trip(b: Breaker, t: Long, retryAfterSeconds: Long?, provider: String, why: String) {
        b.pause = retryAfterSeconds?.let { (it * 1_000).coerceIn(1_000, 300_000) } ?: (if (b.pause == 0L) 30_000L else (b.pause * 2).coerceAtMost(300_000))
        b.openUntil = t + b.pause
        b.failures = 0
        meter.event("provider.$provider.circuitOpened.$why")
    }

    /** Diagnostics: whether [provider] (or one of its endpoints) is currently refusing calls. */
    fun open(provider: String, endpoint: String? = null): Boolean {
        val lane = lanes[provider] ?: return false
        val t = now()
        return synchronized(lane) { lane.breaker.openUntil > t || (endpoint != null && (lane.endpointBreakers[endpoint]?.openUntil ?: 0) > t) }
    }
}
