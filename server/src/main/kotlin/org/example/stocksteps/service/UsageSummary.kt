package org.example.stocksteps.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/** One provider counter delta: requests, outcomes, cache events or budget denials by bounded labels. */
@Serializable data class UsageLine(val provider: String, val endpoint: String, val feature: String, val event: String, val count: Long)

/**
 * A per-instance usage summary for one interval (Phase 4D), written as a single JSON line so Cloud Logging stores it as `jsonPayload` and
 * logs-based metrics can sum it across instances. Only bounded labels: provider, endpoint path (no query, no symbols), feature, event names
 * and gauges. Never API keys, tokens, uids, IP addresses, symbols, prompts, portfolio data or URLs.
 */
@Serializable
data class UsageSummary(
    val kind: String = "stocksteps.usage",
    val severity: String = "INFO",
    val instance: String,
    val from: String,
    val to: String,
    val counters: List<UsageLine>,
    val events: Map<String, Long>,
    val gauges: Map<String, Long>
)

/**
 * Emits [UsageSummary] deltas of [meter] every interval (default 60 s; env `USAGE_SUMMARY_SECONDS`, 0 disables) through the
 * `StockSteps.Usage` logger (plain JSON line, see `logback.xml`). [instance] is a random id per process (not the host or a user).
 */
class UsageSummaryReporter(
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared,
    private val now: () -> Instant = Instant::now,
    val instance: String = java.util.UUID.randomUUID().toString().take(8),
    private val emit: (String) -> Unit = { LoggerFactory.getLogger("StockSteps.Usage").info(it) }
) {
    private val json = Json { encodeDefaults = true }
    private var lastCounters: Map<List<String>, Long> = emptyMap()
    private var lastEvents: Map<String, Long> = emptyMap()
    private var lastAt: Instant = now()

    /** The delta since the previous call (null when nothing happened), and moves the baseline. */
    @Synchronized fun next(): UsageSummary? {
        val (counters, events, gauges) = meter.snapshot()
        val at = now()
        val lines = counters.mapNotNull { (k, v) -> (v - (lastCounters[k] ?: 0)).takeIf { it > 0 }?.let { UsageLine(k[0], k[1], k[2], k[3], it) } }
            .sortedWith(compareBy({ it.provider }, { it.endpoint }, { it.feature }, { it.event }))
        val eventDeltas = events.mapNotNull { (k, v) -> (v - (lastEvents[k] ?: 0)).takeIf { it > 0 }?.let { k to it } }.toMap().toSortedMap()
        val summary = if (lines.isEmpty() && eventDeltas.isEmpty()) null else UsageSummary(instance = instance, from = lastAt.toString(), to = at.toString(),
            counters = lines, events = eventDeltas, gauges = gauges.toSortedMap())
        lastCounters = counters; lastEvents = events; lastAt = at
        return summary
    }

    fun encode(summary: UsageSummary): String = json.encodeToString(UsageSummary.serializer(), summary)

    fun start(scope: CoroutineScope, intervalMillis: Long = 60_000): Job = scope.launch {
        while (isActive) {
            delay(intervalMillis)
            try { next()?.let { emit(encode(it)) } } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                LoggerFactory.getLogger("StockSteps.Usage").warn("Usage summary failed: {}", cause.javaClass.simpleName)
            }
        }
    }
}
