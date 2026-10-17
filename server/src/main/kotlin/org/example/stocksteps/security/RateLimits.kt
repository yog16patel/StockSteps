package org.example.stocksteps.security

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.example.stocksteps.model.ApiError

/**
 * Per-minute request limits for one rate-limit class or route bucket (Phase 9A). Requests are counted whether or not they hit a cache:
 * this layer bounds request rates and concurrency per caller; the cost-weighted Phase 4A admission (upstream calls count extra) and the
 * provider budgets (Phase 4C, shared by everyone) stay in front of the providers, unchanged.
 */
data class RateLimitPolicy(
    /** Per trusted client address (only when `TRUSTED_PROXY_HOPS` is configured; see [ClientIdentity.Address]). */
    val guestPerMinute: Int,
    /** Per verified uid. */
    val userPerMinute: Int,
    /**
     * Aggregate for callers that can't be told apart ([ClientIdentity.Unverified]: no account and no verified proxy configuration). Sized
     * for many guests together, never a small per-client number: one shared small bucket would let a single flood lock every guest out.
     */
    val unverifiedPoolPerMinute: Int,
    /** Concurrent requests per uid or trusted address in this class (excess is rejected at once, never queued). */
    val maxInFlightPerIdentity: Int
) {
    companion object {
        /**
         * Defaults (Phase 9A): derived from app request patterns measured on the Android emulator (`RATE_LIMIT_REVIEW.md` §2): a Company
         * Details open is 7 requests (6 public market data), a slowly typed 9-letter search sends 9 requests (300 ms debounce), tab switches
         * send none after each landing's first load. Headroom: ≈ 15 Company Details opens or 5 slow searches per minute per guest.
         * Each override: `RATE_LIMIT_<NAME>_GUEST_PER_MINUTE`, `_USER_PER_MINUTE`, `_POOL_PER_MINUTE`, `_MAX_IN_FLIGHT` (positive integers).
         */
        val DEFAULTS: Map<String, RateLimitPolicy> = mapOf(
            RateLimitClass.PUBLIC_LOW_COST.name to RateLimitPolicy(120, 240, 12_000, 16),
            RateLimitClass.PUBLIC_MARKET_DATA.name to RateLimitPolicy(90, 180, 9_000, 12),
            RateLimitClass.AUTHENTICATED_STANDARD.name to RateLimitPolicy(30, 120, 3_000, 12),
            RateLimitClass.USER_WRITE.name to RateLimitPolicy(10, 40, 600, 4),
            RateLimitClass.EXPENSIVE_OPERATION.name to RateLimitPolicy(6, 12, 300, 2),
            RateLimitClass.ADMIN_INTERNAL.name to RateLimitPolicy(30, 30, 120, 4),
            "search" to RateLimitPolicy(45, 90, 4_500, 4)
        )

        fun fromEnvironment(env: (String) -> String? = System::getenv): Map<String, RateLimitPolicy> = DEFAULTS.mapValues { (name, p) ->
            fun int(suffix: String, default: Int) = env("RATE_LIMIT_${name.uppercase()}_$suffix")?.toIntOrNull()?.takeIf { it > 0 } ?: default
            RateLimitPolicy(int("GUEST_PER_MINUTE", p.guestPerMinute), int("USER_PER_MINUTE", p.userPerMinute),
                int("POOL_PER_MINUTE", p.unverifiedPoolPerMinute), int("MAX_IN_FLIGHT", p.maxInFlightPerIdentity))
        }
    }
}

/**
 * Sliding-window limiter per (class or bucket, identity) with per-identity concurrency caps (Phase 9A). **Process-local**: with N
 * instances a caller can reach N × these limits (`RATE_LIMIT_REVIEW.md` §6 has the shared-limiter plan). State is bounded: windows
 * expire, idle entries are swept every [sweepEvery] decisions and the least recently used idle entries are evicted beyond [maxTracked].
 * Thread-safe; nothing suspends under its lock.
 */
class RateLimiter(
    private val policies: Map<String, RateLimitPolicy> = RateLimitPolicy.DEFAULTS,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxTracked: Int = 20_000,
    private val sweepEvery: Int = 512
) {
    sealed interface Decision {
        class Admitted internal constructor(internal val keys: List<String>) : Decision
        data class Denied(val retryAfterSeconds: Long, val reason: String, val bucket: String) : Decision
    }
    private class Window { val hits = ArrayDeque<Long>(); var inFlight = 0 }
    private val lock = Any()
    private val windows = LinkedHashMap<String, Window>(64, 0.75f, true)
    private var decisions = 0

    fun policy(name: String): RateLimitPolicy = policies[name] ?: RateLimitPolicy.DEFAULTS[name] ?: RateLimitPolicy.DEFAULTS.getValue(RateLimitClass.PUBLIC_LOW_COST.name)

    /** Checks the class and the optional route bucket together; charges both only when both admit. */
    fun enter(rateClass: RateLimitClass, bucket: String?, identity: ClientIdentity): Decision = synchronized(lock) {
        val t = now()
        if (++decisions % sweepEvery == 0) sweep(t)
        val names = listOfNotNull(rateClass.name, bucket)
        val checks = names.map { name ->
            val p = policy(name)
            val (limit, key) = when (identity) {
                is ClientIdentity.User -> p.userPerMinute to "$name|${identity.key}"
                is ClientIdentity.Address -> p.guestPerMinute to "$name|${identity.key}"
                ClientIdentity.Unverified -> p.unverifiedPoolPerMinute to "$name|pool"
            }
            val w = windows.getOrPut(key) { Window() }
            while (w.hits.isNotEmpty() && t - w.hits.first() >= WINDOW) w.hits.removeFirst()
            val denial = when {
                identity !is ClientIdentity.Unverified && w.inFlight >= p.maxInFlightPerIdentity -> Decision.Denied(1, "in-flight", name)
                w.hits.size >= limit -> Decision.Denied(((w.hits.first() + WINDOW - t + 999) / 1_000).coerceAtLeast(1), if (identity is ClientIdentity.Unverified) "pool" else "identity", name)
                else -> null
            }
            Triple(key, w, denial)
        }
        checks.firstNotNullOfOrNull { it.third }?.let { return it }
        checks.forEach { (_, w, _) -> w.hits.addLast(t); w.inFlight++ }
        evict()
        Decision.Admitted(checks.map { it.first })
    }

    fun exit(ticket: Decision.Admitted) = synchronized(lock) {
        ticket.keys.forEach { key -> windows[key]?.let { it.inFlight = (it.inFlight - 1).coerceAtLeast(0) } }
    }

    /** Entries currently held (tests and diagnostics). */
    fun tracked(): Int = synchronized(lock) { windows.size }

    /** Drops windows with no recent hits and nothing in flight (also runs automatically). */
    fun sweep(t: Long = now()) = synchronized(lock) {
        val it = windows.values.iterator()
        while (it.hasNext()) {
            val w = it.next()
            while (w.hits.isNotEmpty() && t - w.hits.first() >= WINDOW) w.hits.removeFirst()
            if (w.hits.isEmpty() && w.inFlight == 0) it.remove()
        }
    }

    private fun evict() {
        if (windows.size <= maxTracked) return
        val it = windows.entries.iterator()
        while (windows.size > maxTracked && it.hasNext()) { if (it.next().value.inFlight == 0) it.remove() }
    }

    companion object { const val WINDOW = 60_000L }
}

/**
 * Installs the Phase 9A per-class limits in front of routing (after admission, before central authentication, so floods of bad tokens
 * are limited too). The class comes from [RouteAccess]; unmatched paths use [RouteAccess.fallback]. Denials answer 429 `RATE_LIMITED`
 * with `Retry-After` (the existing contract) and never reveal limits, quotas or provider state.
 */
fun Application.installRateLimits(resolver: ClientIdentityResolver, limiter: RateLimiter) {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        val rule = RouteAccess.of(call.request.httpMethod, path)
        val rateClass = (if (rule != null) rule.limit else RouteAccess.fallback(path)) ?: return@intercept
        val identity = call.clientIdentity() ?: resolver.resolve(call)
        when (val decision = limiter.enter(rateClass, rule?.bucket, identity)) {
            is RateLimiter.Decision.Denied -> {
                SecurityLog.event(call, if (rateClass == RateLimitClass.EXPENSIVE_OPERATION) SecurityEvent.EXPENSIVE_BLOCKED else SecurityEvent.RATE_LIMITED,
                    "${decision.bucket.lowercase()}-${decision.reason}", rateClass)
                call.response.header(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again."))
                finish()
            }
            is RateLimiter.Decision.Admitted -> {
                try { proceed() } finally { limiter.exit(decision) }
            }
        }
    }
}
