package org.example.stocksteps.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded per-dataset cache. Identical misses coalesce; different keys do not block each other. */
class CompanyFinancialCache(
    private val capacity: Int = 512,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private class Entry {
        val mutex = Mutex()
        var value: Any? = null
        var expiresAt = 0L
    }
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    init { require(capacity > 0) }
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getOrLoad(key: String, ttlMillis: Long, resultTtl: ((T) -> Long)? = null, loader: suspend () -> T): T {
        require(ttlMillis > 0)
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also {
                if (entries.size > capacity) entries.remove(entries.keys.first())
            }
        }
        return entry.mutex.withLock {
            val cached = entry.value
            if (cached != null && now() < entry.expiresAt) return@withLock cached as T
            val value = loader()
            entry.value = value
            entry.expiresAt = now() + (resultTtl?.invoke(value) ?: ttlMillis).also { require(it > 0) }
            value
        }
    }
}

object FinancialCachePolicy {
    const val QUOTE = 30_000L
    const val RATIOS = 300_000L
    const val STATEMENTS = 86_400_000L
    const val TTM_STATEMENTS = 21_600_000L
    const val ESTIMATES = 21_600_000L
    const val ACCESS_COOLDOWN = 3_600_000L
    const val FAILURE = 30_000L
}
