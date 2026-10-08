package org.example.stocksteps.userdata

import org.example.stocksteps.model.*
import java.util.Locale
import java.util.UUID

/** A rejected user-data request: [status] is the HTTP status, [code] the stable error code. */
class UserDataException(val status: Int, val code: String, override val message: String) : Exception(message)

/** Operational limits for V1 (abuse protection, not subscription tiers). */
object UserDataLimits {
    const val MAX_WATCHLISTS = 20
    const val MAX_ENTRIES = 100
    const val MAX_NAME_LENGTH = 40
    const val MAX_NOTE_LENGTH = 1_000
    const val MAX_ALERTS = 50
    val value = WatchlistLimits(MAX_WATCHLISTS, MAX_ENTRIES, MAX_NAME_LENGTH, MAX_NOTE_LENGTH, MAX_ALERTS)
}

/**
 * Watchlists and notes for one signed-in user. The first read creates "My Stocks" exactly once
 * (deterministic id, re-checked inside the transaction) and imports the previous single-list
 * watchlist into it. Every mutation is one atomic read-modify-write and returns the new state.
 */
class WatchlistsService(
    private val store: UserDataStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun get(uid: String): WatchlistsResponse {
        val legacy = store.updateWatchlists(uid) { it to it.initialized }.let { initialized ->
            if (initialized) null else runCatching { store.legacyWatchlist(uid) }.getOrDefault(emptyList())
        }
        return mutate(uid) { state ->
            if (state.initialized) state
            else {
                val time = now()
                val entries = legacy.orEmpty().mapNotNull { runCatching { validInstrument(it) }.getOrNull() }
                    .distinctBy { it.symbol }.take(UserDataLimits.MAX_ENTRIES)
                    .mapIndexed { index, instrument -> WatchlistEntry(newId(), instrument, index, time) }
                val default = Watchlist(DEFAULT_ID, DEFAULT_NAME, 0, time, time, isDefault = true, entries = entries)
                UserWatchlists(listOf(default) + state.watchlists.filter { it.id != DEFAULT_ID }.map { it.copy(isDefault = false) }, initialized = true)
            }
        }
    }

    suspend fun create(uid: String, name: String) = mutateInitialized(uid) { state ->
        val clean = validName(name, state.watchlists, except = null)
        if (state.watchlists.size >= UserDataLimits.MAX_WATCHLISTS) fail(409, "WATCHLIST_LIMIT", "You can have up to ${UserDataLimits.MAX_WATCHLISTS} watchlists.")
        val time = now()
        state.copy(watchlists = state.watchlists + Watchlist(newId(), clean, (state.watchlists.maxOfOrNull { it.order } ?: -1) + 1, time, time))
    }

    suspend fun rename(uid: String, id: String, name: String) = mutateInitialized(uid) { state ->
        val list = find(state, id)
        val clean = validName(name, state.watchlists, except = id)
        state.replace(list.copy(name = clean, updatedAt = now()))
    }

    /** Deletes a list (alerts are per user and stock, so they stay). The last list can't be deleted. */
    suspend fun delete(uid: String, id: String) = mutateInitialized(uid) { state ->
        val list = find(state, id)
        if (state.watchlists.size == 1) fail(409, "LAST_WATCHLIST", "Keep at least one watchlist.")
        val rest = state.watchlists.filter { it.id != list.id }
        val withDefault = if (list.isDefault) rest.mapIndexed { index, it -> it.copy(isDefault = index == 0) } else rest
        state.copy(watchlists = withDefault)
    }

    suspend fun reorderWatchlists(uid: String, ids: List<String>) = mutateInitialized(uid) { state ->
        if (ids.toSet() != state.watchlists.map { it.id }.toSet() || ids.size != state.watchlists.size) fail(400, "INVALID_ORDER", "Send every watchlist id exactly once.")
        state.copy(watchlists = ids.mapIndexed { index, id -> state.watchlists.first { it.id == id }.copy(order = index) })
    }

    /** Adds a stock; adding one that's already in the list is a no-op (same entry returned). */
    suspend fun add(uid: String, listId: String, instrument: InstrumentRef) = mutateInitialized(uid) { state ->
        val list = find(state, listId)
        val clean = validInstrument(instrument)
        if (list.entries.any { it.instrument.symbol == clean.symbol }) return@mutateInitialized state
        if (list.entries.size >= UserDataLimits.MAX_ENTRIES) fail(409, "WATCHLIST_FULL", "A watchlist can hold up to ${UserDataLimits.MAX_ENTRIES} stocks.")
        val time = now()
        state.replace(list.copy(entries = list.entries + WatchlistEntry(newId(), clean, nextOrder(list), time), updatedAt = time))
    }

    /** Guest-list import after sign-in: adds missing stocks to the default list, skipping duplicates. */
    suspend fun import(uid: String, instruments: List<InstrumentRef>): WatchlistsResponse {
        get(uid)
        return mutate(uid) { state ->
            val default = state.watchlists.firstOrNull { it.isDefault } ?: state.watchlists.first()
            val time = now()
            var entries = default.entries
            instruments.take(UserDataLimits.MAX_ENTRIES).mapNotNull { runCatching { validInstrument(it) }.getOrNull() }.forEach { instrument ->
                if (entries.size < UserDataLimits.MAX_ENTRIES && entries.none { it.instrument.symbol == instrument.symbol }) {
                    entries = entries + WatchlistEntry(newId(), instrument, (entries.maxOfOrNull { it.order } ?: -1) + 1, time)
                }
            }
            if (entries == default.entries) state else state.replace(default.copy(entries = entries, updatedAt = time))
        }
    }

    suspend fun remove(uid: String, listId: String, entryId: String) = mutateInitialized(uid) { state ->
        val list = find(state, listId)
        if (list.entries.none { it.id == entryId }) return@mutateInitialized state
        state.replace(list.copy(entries = list.entries.filter { it.id != entryId }, updatedAt = now()))
    }

    /** Sets, replaces or (null/blank) deletes the private note on one entry. */
    suspend fun updateNote(uid: String, listId: String, entryId: String, note: String?) = mutateInitialized(uid) { state ->
        val list = find(state, listId)
        val entry = list.entries.firstOrNull { it.id == entryId } ?: fail(404, "ENTRY_NOT_FOUND", "That stock isn't in this watchlist.")
        val clean = note?.let(::validNote)
        val time = now()
        val updated = entry.copy(note = clean, noteUpdatedAt = clean?.let { time })
        state.replace(list.copy(entries = list.entries.map { if (it.id == entryId) updated else it }, updatedAt = time))
    }

    /** Moves or copies an entry (with its note) to another list; a stock already there isn't duplicated. */
    suspend fun move(uid: String, listId: String, entryId: String, targetId: String, copy: Boolean) = mutateInitialized(uid) { state ->
        val source = find(state, listId)
        val target = find(state, targetId)
        if (source.id == target.id) return@mutateInitialized state
        val entry = source.entries.firstOrNull { it.id == entryId } ?: fail(404, "ENTRY_NOT_FOUND", "That stock isn't in this watchlist.")
        val time = now()
        val alreadyThere = target.entries.any { it.instrument.symbol == entry.instrument.symbol }
        if (!alreadyThere && target.entries.size >= UserDataLimits.MAX_ENTRIES) fail(409, "WATCHLIST_FULL", "A watchlist can hold up to ${UserDataLimits.MAX_ENTRIES} stocks.")
        val newTarget = if (alreadyThere) target else target.copy(
            entries = target.entries + entry.copy(id = newId(), order = nextOrder(target), addedAt = time), updatedAt = time
        )
        val newSource = if (copy) source else source.copy(entries = source.entries.filter { it.id != entryId }, updatedAt = time)
        state.replace(newSource).replace(newTarget)
    }

    suspend fun reorderEntries(uid: String, listId: String, ids: List<String>) = mutateInitialized(uid) { state ->
        val list = find(state, listId)
        if (ids.toSet() != list.entries.map { it.id }.toSet() || ids.size != list.entries.size) fail(400, "INVALID_ORDER", "Send every stock in the watchlist exactly once.")
        state.replace(list.copy(entries = ids.mapIndexed { index, id -> list.entries.first { it.id == id }.copy(order = index) }, updatedAt = now()))
    }

    private suspend fun mutate(uid: String, block: (UserWatchlists) -> UserWatchlists): WatchlistsResponse =
        store.updateWatchlists(uid) { state -> block(state).let { next -> next to response(next) } }

    /** Mutations other than the first read require the default list to exist (the app always reads first). */
    private suspend fun mutateInitialized(uid: String, block: (UserWatchlists) -> UserWatchlists): WatchlistsResponse {
        get(uid)
        return mutate(uid, block)
    }

    private fun response(state: UserWatchlists) = WatchlistsResponse(
        state.watchlists.sortedBy { it.order }.map { list -> list.copy(entries = list.entries.sortedBy { it.order }) },
        UserDataLimits.value
    )

    private fun UserWatchlists.replace(list: Watchlist) = copy(watchlists = watchlists.map { if (it.id == list.id) list else it })
    private fun find(state: UserWatchlists, id: String) = state.watchlists.firstOrNull { it.id == id } ?: fail(404, "WATCHLIST_NOT_FOUND", "That watchlist doesn't exist.")
    private fun nextOrder(list: Watchlist) = (list.entries.maxOfOrNull { it.order } ?: -1) + 1

    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "My Stocks"
        private val SYMBOL = Regex("[A-Z0-9][A-Z0-9.^-]{0,31}")
        private val CURRENCY = Regex("[A-Z]{3}")

        fun fail(status: Int, code: String, message: String): Nothing = throw UserDataException(status, code, message)

        fun validName(name: String, existing: List<Watchlist>, except: String?): String {
            val clean = name.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) fail(400, "INVALID_NAME", "Enter a watchlist name.")
            if (clean.length > UserDataLimits.MAX_NAME_LENGTH) fail(400, "INVALID_NAME", "Use at most ${UserDataLimits.MAX_NAME_LENGTH} characters.")
            if (clean.any { it.isISOControl() }) fail(400, "INVALID_NAME", "The name contains characters that aren't allowed.")
            if (existing.any { it.id != except && it.name.equals(clean, ignoreCase = true) }) fail(409, "DUPLICATE_NAME", "You already have a watchlist with that name.")
            return clean
        }

        fun validNote(note: String): String? {
            val clean = note.replace("\r\n", "\n").filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim()
            if (clean.isEmpty()) return null
            if (clean.length > UserDataLimits.MAX_NOTE_LENGTH) fail(400, "NOTE_TOO_LONG", "Notes can be up to ${UserDataLimits.MAX_NOTE_LENGTH} characters.")
            return clean
        }

        fun validInstrument(instrument: InstrumentRef): InstrumentRef {
            val symbol = instrument.symbol.trim().uppercase(Locale.ROOT)
            if (!SYMBOL.matches(symbol)) fail(400, "INVALID_SYMBOL", "Use a valid stock symbol.")
            val currency = instrument.currency?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            if (currency != null && !CURRENCY.matches(currency)) fail(400, "INVALID_CURRENCY", "Use a 3-letter currency code.")
            fun text(value: String?, max: Int) = value?.trim()?.filterNot { it.isISOControl() }?.take(max)?.takeIf { it.isNotEmpty() }
            return InstrumentRef(symbol, text(instrument.name, 200), text(instrument.exchange, 40)?.uppercase(Locale.ROOT), currency)
        }
    }
}
