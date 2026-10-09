package org.example.stocksteps.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.ApiError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** The feature that caused provider work (e.g. "comparison-history", "comparison-research"), for usage attribution. */
class ProviderFeature(val name: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ProviderFeature>
}

@Serializable data class UsageCounter(val provider: String, val endpoint: String, val feature: String, val event: String, val count: Long)
@Serializable data class UsageReport(val since: String, val counters: List<UsageCounter>, val events: Map<String, Long>, val notes: List<String>)

/**
 * Aggregate, non-sensitive usage counters for provider traffic and product events: upstream requests,
 * cache hits/misses, errors and rate-limit responses by provider, endpoint (dataset) and feature, plus
 * counts like "research.session.created". Never symbols-with-users, notes or any session content.
 * Counts only (no invented prices): compare `cacheHit` with `upstream` to see how much traffic caching saves.
 * Per process: on Cloud Run each instance counts its own traffic (logs/metrics aggregate across instances).
 */
class ProviderUsageMeter(private val started: String = java.time.Instant.now().toString()) {
    private val counters = ConcurrentHashMap<List<String>, AtomicLong>()
    private val events = ConcurrentHashMap<String, AtomicLong>()

    suspend fun feature(): String = currentCoroutineContext()[ProviderFeature]?.name ?: "other"

    fun record(provider: String, endpoint: String, feature: String, event: String) {
        counters.getOrPut(listOf(provider, endpoint, feature, event)) { AtomicLong() }.incrementAndGet()
    }
    fun event(name: String) { events.getOrPut(name) { AtomicLong() }.incrementAndGet() }

    fun count(provider: String? = null, event: String, feature: String? = null): Long = counters.entries
        .filter { (k, _) -> (provider == null || k[0] == provider) && k[3] == event && (feature == null || k[2] == feature) }.sumOf { it.value.get() }
    fun eventCount(name: String): Long = events[name]?.get() ?: 0

    fun report() = UsageReport(started,
        counters.entries.map { (k, v) -> UsageCounter(k[0], k[1], k[2], k[3], v.get()) }.sortedWith(compareBy({ it.provider }, { it.endpoint }, { it.feature }, { it.event })),
        events.mapValues { it.value.get() }.toSortedMap(),
        listOf("Counts since this server instance started. cacheHit/cacheMiss are application caches; upstream = requests sent to the provider.",
            "Provider plan terms decide what a request costs; no prices are estimated here."))

    companion object {
        /** The process-wide meter used by the running server (tests create their own). */
        val shared = ProviderUsageMeter()
    }
}

/**
 * Optional per-feature hourly caps on *upstream* loads (cache hits never count). Configured with
 * `PROVIDER_BUDGET_<FEATURE>_PER_HOUR` (feature upper-cased, '-' → '_'); unset = no cap. When a cap is
 * reached, the caller shows "temporarily unavailable" instead of calling the provider.
 */
class ProviderRequestBudget(private val limits: Map<String, Int>, private val now: () -> Long = System::currentTimeMillis) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()
    fun tryAcquire(feature: String): Boolean {
        val limit = limits[feature] ?: return true
        val window = windows.getOrPut(feature) { ArrayDeque() }
        synchronized(window) {
            val t = now()
            while (window.isNotEmpty() && t - window.first() >= 3_600_000L) window.removeFirst()
            if (window.size >= limit) return false
            window.addLast(t); return true
        }
    }
    companion object {
        fun fromEnvironment(features: List<String>, env: (String) -> String? = System::getenv) = ProviderRequestBudget(
            features.mapNotNull { f -> env("PROVIDER_BUDGET_${f.uppercase().replace('-', '_')}_PER_HOUR")?.toIntOrNull()?.takeIf { it > 0 }?.let { f to it } }.toMap())
        val UNLIMITED = ProviderRequestBudget(emptyMap())
    }
}

/** `GET /internal/metrics/usage`: aggregate counters for operators (scheduler token in REAL; open in MOCK). */
fun Route.usageMetricsRoutes(meter: ProviderUsageMeter, secret: String?, mock: Boolean) {
    if (secret == null && !mock) return
    get("/internal/metrics/usage") {
        if (!mock && call.request.headers["X-StockSteps-Scheduler-Token"] != secret) { call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Not allowed.")); return@get }
        call.respond(meter.report())
    }
}
