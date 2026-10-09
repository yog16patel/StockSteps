package org.example.stocksteps.security

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.service.ProviderUsageMeter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Route groups with their own admission policy (Phase 4A). */
enum class RouteGroup(val label: String) {
    MARKET_DATA("market-data"), SCREENER("screener"), HISTORY("comparison-history"), RESEARCH("comparison-research"),
    EARNINGS("earnings"), BRIEF("daily-brief"), PUBLIC_AI("public-ai"), PREMIUM_AI("premium-ai"), USER_DATA("user-data");

    companion object {
        private val publicAi = Regex("^/api/v1/stocks/[^/]+/(news/[^/]+/insight|movement)$")
        private val premiumAi = Regex("^/api/v1/me/(compare/ai/(summary|ask)|daily-brief/[^/]+/ai/(explain|ask)|earnings/reports/[^/]+/ai/(explain|ask)|earnings/digest/latest/ai|earnings/[^/]+/ask|research/[^/]+/ask)$")

        /** The group for a request path, or null for routes without provider cost (health, meta, internal jobs, MOCK personas). */
        fun of(path: String): RouteGroup? = when {
            path.startsWith("/internal/") || path == "/health" || path == "/api/v1/meta" || path.startsWith("/api/v1/home/") -> null
            publicAi.matches(path) -> PUBLIC_AI
            premiumAi.matches(path) -> PREMIUM_AI
            path.startsWith("/api/v1/compare/history") || path.startsWith("/api/v1/me/compare/history") -> HISTORY
            path.startsWith("/api/v1/screener") || path.startsWith("/api/v1/compare") || path.startsWith("/api/v1/me/screens") -> SCREENER
            path.startsWith("/api/v1/me/comparison-research") -> RESEARCH
            path.startsWith("/api/v1/earnings") || path.startsWith("/api/v1/me/earnings") -> EARNINGS
            path.startsWith("/api/v1/daily-brief") || path.startsWith("/api/v1/me/daily-brief") -> BRIEF
            path.startsWith("/api/v1/me/") -> USER_DATA
            path.startsWith("/api/v1/") || path.startsWith("/market/") -> MARKET_DATA
            else -> null
        }
    }
}

/**
 * Per-instance admission policy for one route group. Units: every request costs 1, plus 1 per upstream FMP/Finnhub/Bank of Canada request
 * and [AdmissionController.GEMINI_UNITS] per Gemini call it actually caused ([RequestCost]), so cache hits stay cheap and cold or AI-heavy
 * requests cost what they cost. **Per-instance limits are not global limits**: with N instances a client can reach N × these numbers;
 * the provider budgets (Phase 4C) are the provider-wide backstop.
 */
data class AdmissionPolicy(
    /** Units per rolling minute per verified identity (uid or trusted IP). */
    val unitsPerMinute: Int,
    /**
     * Upstream units per rolling minute shared by all unverified anonymous callers on this instance (aggregate, not per client; cache hits
     * are free in this pool).
     */
    val anonymousPoolPerMinute: Int,
    /** Concurrent requests per identity. */
    val maxInFlightPerIdentity: Int,
    /** Concurrent requests in this group on this instance. */
    val maxInFlight: Int,
    /** Largest accepted request body (bytes). */
    val maxBodyBytes: Long
) {
    companion object {
        val DEFAULTS: Map<RouteGroup, AdmissionPolicy> = mapOf(
            RouteGroup.MARKET_DATA to AdmissionPolicy(400, 6_000, 16, 256, 16_384),
            RouteGroup.SCREENER to AdmissionPolicy(400, 3_000, 8, 64, 32_768),
            RouteGroup.HISTORY to AdmissionPolicy(200, 2_000, 8, 64, 16_384),
            RouteGroup.RESEARCH to AdmissionPolicy(300, 1_000, 8, 32, 65_536),
            RouteGroup.EARNINGS to AdmissionPolicy(300, 3_000, 16, 128, 16_384),
            RouteGroup.BRIEF to AdmissionPolicy(300, 3_000, 16, 128, 16_384),
            RouteGroup.PUBLIC_AI to AdmissionPolicy(80, 600, 4, 32, 4_096),
            RouteGroup.PREMIUM_AI to AdmissionPolicy(120, 300, 4, 32, 16_384),
            RouteGroup.USER_DATA to AdmissionPolicy(600, 2_000, 16, 256, 262_144)
        )

        /** `ADMISSION_<GROUP>_UNITS_PER_MINUTE` / `_ANONYMOUS_POOL_PER_MINUTE` / `_MAX_IN_FLIGHT` override the defaults (positive integers). */
        fun fromEnvironment(env: (String) -> String? = System::getenv): Map<RouteGroup, AdmissionPolicy> = DEFAULTS.mapValues { (group, p) ->
            fun int(name: String, default: Int) = env("ADMISSION_${group.name}_$name")?.toIntOrNull()?.takeIf { it > 0 } ?: default
            p.copy(unitsPerMinute = int("UNITS_PER_MINUTE", p.unitsPerMinute), anonymousPoolPerMinute = int("ANONYMOUS_POOL_PER_MINUTE", p.anonymousPoolPerMinute),
                maxInFlight = int("MAX_IN_FLIGHT", p.maxInFlight))
        }
    }
}

/** Upstream calls caused by the current request (counted by `ProviderCalls.record`, inherited by child coroutines). */
class RequestCost : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RequestCost> {
        suspend fun add(provider: String) { currentCoroutineContext()[RequestCost]?.let { if (provider == "gemini") it.gemini.incrementAndGet() else it.data.incrementAndGet() } }
    }
    val data = AtomicInteger()
    val gemini = AtomicInteger()
    val units get() = 1 + data.get() + AdmissionController.GEMINI_UNITS * gemini.get()
}

/**
 * Weighted, per-instance admission (Phase 4A): rolling-minute unit windows per (group, identity), in-flight caps per identity and group,
 * bounded state ([maxTracked] identities per group, oldest evicted). Thread-safe; no coroutines suspend under its lock.
 */
class AdmissionController(
    private val policies: Map<RouteGroup, AdmissionPolicy> = AdmissionPolicy.DEFAULTS,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxTracked: Int = 10_000,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    sealed interface Decision {
        class Admitted internal constructor(internal val group: RouteGroup, internal val key: String, internal val at: Long) : Decision
        data class Denied(val retryAfterSeconds: Long, val reason: String) : Decision
    }
    private class Window { val entries = ArrayDeque<LongArray>(); var inFlight = 0; var units = 0L }
    private val lock = Any()
    private val windows = HashMap<RouteGroup, LinkedHashMap<String, Window>>()
    private val groupInFlight = HashMap<RouteGroup, Int>()

    fun policy(group: RouteGroup) = policies[group] ?: AdmissionPolicy.DEFAULTS.getValue(group)

    fun enter(group: RouteGroup, identity: ClientIdentity): Decision = synchronized(lock) {
        val p = policy(group)
        val key = identity.key ?: ANONYMOUS_POOL
        val limit = if (identity.key == null) p.anonymousPoolPerMinute else p.unitsPerMinute
        val t = now()
        val map = windows.getOrPut(group) { LinkedHashMap(16, 0.75f, true) }
        val w = map.getOrPut(key) { Window() }
        while (w.entries.isNotEmpty() && t - w.entries.first()[0] >= WINDOW) w.units -= w.entries.removeFirst()[1]
        val denial = when {
            (groupInFlight[group] ?: 0) >= p.maxInFlight -> Decision.Denied(1, "group-in-flight")
            identity.key != null && w.inFlight >= p.maxInFlightPerIdentity -> Decision.Denied(1, "identity-in-flight")
            w.units >= limit -> Decision.Denied(((w.entries.firstOrNull()?.get(0) ?: t) + WINDOW - t).coerceAtLeast(1_000) / 1_000, if (identity.key == null) "anonymous-pool" else "identity")
            else -> null
        }
        if (denial != null) {
            meter.event("admission.${group.label}.denied")
            meter.event("admission.denied.${(denial as Decision.Denied).reason}")
            return denial
        }
        // Verified identities pre-charge one unit so concurrent requests count immediately; the rest is charged on exit. The unverified
        // anonymous pool is charged only for upstream work (cache hits are free there), so a flood of cheap requests can't exhaust it and
        // lock out every guest; the group in-flight cap still bounds concurrency.
        if (identity.key != null) { w.entries.addLast(longArrayOf(t, 1)); w.units += 1 }
        w.inFlight++
        groupInFlight[group] = (groupInFlight[group] ?: 0) + 1
        while (map.size > maxTracked) { val eldest = map.entries.iterator(); val e = eldest.next(); if (e.value.inFlight == 0) eldest.remove() else break }
        meter.event("admission.${group.label}.admitted")
        Decision.Admitted(group, key, t)
    }

    fun exit(ticket: Decision.Admitted, units: Int) = synchronized(lock) {
        groupInFlight[ticket.group] = ((groupInFlight[ticket.group] ?: 1) - 1).coerceAtLeast(0)
        val w = windows[ticket.group]?.get(ticket.key) ?: return@synchronized
        w.inFlight = (w.inFlight - 1).coerceAtLeast(0)
        val extra = (units - 1).coerceAtLeast(0)   // the request itself was pre-charged (identities) or is free (anonymous pool)
        if (extra > 0) { w.entries.addLast(longArrayOf(now(), extra.toLong())); w.units += extra }
        if (units > 1) meter.event("admission.${ticket.group.label}.units", units.toLong())
    }

    /** Tracked identities in a group (tests and diagnostics). */
    fun tracked(group: RouteGroup): Int = synchronized(lock) { windows[group]?.size ?: 0 }

    companion object {
        const val WINDOW = 60_000L
        const val GEMINI_UNITS = 20
        const val ANONYMOUS_POOL = "anonymous-pool"
    }
}

/**
 * Installs Phase 4A admission in front of routing: identity, request-size and query-length guards, App Check monitoring/enforcement and
 * weighted per-group limits. Responses use the existing `ApiError` shape; 429s carry `Retry-After`.
 */
fun Application.installAdmission(resolver: ClientIdentityResolver, controller: AdmissionController, appCheck: AppCheckGuard? = null,
                                 maxQueryLength: Int = 2_048) {
    intercept(ApplicationCallPipeline.Plugins) {
        val group = RouteGroup.of(call.request.path()) ?: return@intercept
        val policy = controller.policy(group)
        if (call.request.queryString().length > maxQueryLength) {
            call.respond(HttpStatusCode.fromValue(414), ApiError("URI_TOO_LONG", "The request is too long.")); finish(); return@intercept
        }
        if (call.request.httpMethod in setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) {
            val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            val chunked = call.request.headers[HttpHeaders.TransferEncoding]?.contains("chunked", ignoreCase = true) == true
            if (length == null && chunked) {
                call.respond(HttpStatusCode.LengthRequired, ApiError("LENGTH_REQUIRED", "Send the request with a Content-Length.")); finish(); return@intercept
            }
            if (length != null && length > policy.maxBodyBytes) {
                call.respond(HttpStatusCode.PayloadTooLarge, ApiError("PAYLOAD_TOO_LARGE", "The request is too large.")); finish(); return@intercept
            }
        }
        if (appCheck != null && !appCheck.admit(call)) {
            call.respond(HttpStatusCode.Unauthorized, ApiError("APP_CHECK_REQUIRED", "Please update the app and try again.")); finish(); return@intercept
        }
        val identity = resolver.resolve(call)
        when (val decision = controller.enter(group, identity)) {
            is AdmissionController.Decision.Denied -> {
                call.response.header(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again."))
                finish()
            }
            is AdmissionController.Decision.Admitted -> {
                val cost = RequestCost()
                try { withContext(cost) { proceed() } } finally { controller.exit(decision, cost.units) }
            }
        }
    }
}
