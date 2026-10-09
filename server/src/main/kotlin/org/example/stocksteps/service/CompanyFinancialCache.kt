package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded, per-instance cache with per-key single flight (the shared building block for every provider
 * cache in the server).
 *
 * - **Single flight**: concurrent callers for the same missing or expired key share one [loader] call;
 *   different keys never wait for each other (no global lock while loading).
 * - **Failures are shared, never stored**: callers that joined an attempt that fails receive that same
 *   failure (no sequential retry stampede); nothing is cached, so the next request after it tries again.
 *   Callers that want a cooldown cache an outcome value with a short `resultTtl` (see [providerCooldown]).
 * - **Cancellation**: if the caller running the load is cancelled (e.g. its client disconnected), waiting
 *   callers take over the load instead of failing; no in-flight entry is left behind.
 * - **Bounds**: at most [capacity] entries; expired entries are purged first, then least-recently-used ones
 *   that aren't loading.
 * - **Stats**: hit / miss / join / expired / evict counts, also sent to [meter] as `cache.<name>.<event>`
 *   when [name] is set (fixed names only, never symbols or users).
 */
class CompanyFinancialCache(
    private val capacity: Int = 512,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val name: String? = null,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    private class Entry {
        var value: Any? = null
        var expiresAt = 0L
        var loading: CompletableDeferred<Any>? = null
    }
    /** Tells joined callers that the loading caller was cancelled, so one of them loads instead. */
    private class OwnerCancelled : Exception()

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    init { require(capacity > 0) }

    private fun stat(event: String) {
        counters.getOrPut(event) { AtomicLong() }.incrementAndGet()
        name?.let { meter.event("cache.$it.$event") }
    }

    /** hit, miss, join, expired, evict counts since creation (tests and diagnostics). */
    fun stats(): Map<String, Long> = counters.mapValues { it.value.get() }
    val size: Int get() = synchronized(entries) { entries.size }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getOrLoad(key: String, ttlMillis: Long, resultTtl: ((T) -> Long)? = null, loader: suspend () -> T): T {
        require(ttlMillis > 0)
        while (true) {
            var owner = false
            lateinit var entry: Entry
            val flight: CompletableDeferred<Any> = synchronized(entries) {
                val existing = entries[key]
                val cached = existing?.value
                if (existing != null && cached != null && now() < existing.expiresAt) { stat("hit"); return cached as T }
                entry = existing ?: Entry().also { entries[key] = it }
                if (cached != null && existing?.loading == null) stat("expired")
                val result = entry.loading?.also { stat("join") } ?: CompletableDeferred<Any>().also { entry.loading = it; owner = true; stat("miss") }
                // Trim only after the new entry is marked as loading, so it can't be purged before its value arrives.
                if (existing == null) trim()
                result
            }
            if (!owner) {
                try { return flight.await() as T } catch (_: OwnerCancelled) { continue }
            }
            val value = try { loader() } catch (cause: Throwable) {
                synchronized(entries) { if (entry.loading === flight) entry.loading = null }
                flight.completeExceptionally(if (cause is CancellationException) OwnerCancelled() else cause)
                throw cause
            }
            val ttl = try { (resultTtl?.invoke(value) ?: ttlMillis).also { require(it > 0) } } catch (cause: Throwable) {
                synchronized(entries) { if (entry.loading === flight) entry.loading = null }
                flight.completeExceptionally(cause); throw cause
            }
            synchronized(entries) {
                entry.value = value
                entry.expiresAt = now() + ttl
                if (entry.loading === flight) entry.loading = null
            }
            flight.complete(value)
            return value
        }
    }

    /** Drops one key (e.g. after a provider correction); an in-flight load still completes for its callers. */
    fun invalidate(key: String) = synchronized(entries) {
        val entry = entries[key] ?: return@synchronized
        if (entry.loading == null) entries.remove(key) else entry.expiresAt = 0
    }

    /** Removes expired entries that aren't loading. Called automatically when the cache is full. */
    fun purgeExpired(): Int = synchronized(entries) { purgeLocked() }

    private fun purgeLocked(): Int {
        val time = now()
        var removed = 0
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next().value
            if (e.loading == null && (e.value == null || time >= e.expiresAt)) { iterator.remove(); removed++ }
        }
        return removed
    }

    /** Must hold the lock. Expired first, then least recently used entries that aren't loading. */
    private fun trim() {
        if (entries.size <= capacity) return
        purgeLocked()
        val iterator = entries.entries.iterator()
        while (entries.size > capacity && iterator.hasNext()) {
            if (iterator.next().value.loading == null) { iterator.remove(); stat("evict") }
        }
    }
}

/**
 * Freshness for FMP datasets (centralized; each can be overridden where a feature needs otherwise).
 * Session-aware and earnings-aware TTLs are planned for Phase 3 (`docs/FINANCIAL_API_OPTIMIZATION_ROADMAP.md`).
 */
object FinancialCachePolicy {
    const val QUOTE = 30_000L
    const val RATIOS = 300_000L
    const val STATEMENTS = 86_400_000L
    const val TTM_STATEMENTS = 21_600_000L
    const val ESTIMATES = 21_600_000L
    const val ACCESS_COOLDOWN = 3_600_000L
    const val FAILURE = 30_000L
    /** Company search results (symbol, name, exchange, currency): static reference data. */
    const val SEARCH = 6 * 3_600_000L
    /** Raw 5-minute bars shared by the 1D chart and sparklines. */
    const val INTRADAY = 300_000L
}
