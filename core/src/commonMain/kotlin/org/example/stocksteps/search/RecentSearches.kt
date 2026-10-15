package org.example.stocksteps.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.StockSearchResult

/** One string value in the platform's local key-value store (Android SharedPreferences, iOS UserDefaults). Never synced anywhere. */
interface StringStorage {
    fun read(): String?
    fun write(value: String?)
}

/** Non-persistent storage for previews, tests and hosts without platform storage. */
class InMemoryStringStorage(private var value: String? = null) : StringStorage {
    override fun read(): String? = value
    override fun write(value: String?) { this.value = value }
}

/**
 * Recent company searches (Phase 5D.5.5C, owner decision D6): the companies the user *selected* from search results — never typed queries —
 * newest first, at most [MAX], one entry per listing ([identity]: GOOG and GOOGL stay separate; BRK.B and BRK-B are the same listing).
 * Stored only on this device, works in guest mode, and has no server, account or Firestore involvement.
 */
object RecentSearches {
    const val MAX = 10
    /** Key shared by every platform store (iOS reads the same UserDefaults key). */
    const val KEY = "stocksteps.recentSearches"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StockSearchResult.serializer())

    fun identity(result: StockSearchResult): String = SearchRanking.ticker(result.symbol)

    /** [result] first; an earlier entry for the same listing moves to the top (its stored details are replaced); capped at [MAX]. */
    fun add(list: List<StockSearchResult>, result: StockSearchResult): List<StockSearchResult> =
        (listOf(result) + list.filterNot { identity(it) == identity(result) }).take(MAX)

    fun encode(list: List<StockSearchResult>): String = json.encodeToString(serializer, list)

    /** Corrupt or unexpected stored data is treated as no history (it's rewritten on the next selection), never a crash. */
    fun decode(stored: String?): List<StockSearchResult> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, stored) }.getOrDefault(emptyList())
            .filter { it.symbol.isNotBlank() && it.name.isNotBlank() }
            .distinctBy(::identity)
            .take(MAX)
    }
}

/** The recent-searches list over one [StringStorage] value. */
class RecentSearchStore(private val storage: StringStorage) {
    private val state = MutableStateFlow(RecentSearches.decode(storage.read()))
    val items: StateFlow<List<StockSearchResult>> = state.asStateFlow()

    /** Snapshot for SwiftUI callers. */
    fun current(): List<StockSearchResult> = state.value

    fun record(result: StockSearchResult) {
        val next = RecentSearches.add(state.value, result)
        storage.write(RecentSearches.encode(next))
        state.value = next
    }

    fun clear() {
        storage.write(null)
        state.value = emptyList()
    }
}
