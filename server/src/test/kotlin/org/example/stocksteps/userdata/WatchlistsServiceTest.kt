package org.example.stocksteps.userdata

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.InstrumentRef
import kotlin.test.*

class WatchlistsServiceTest {
    private var ids = 0
    private fun service(store: UserDataStore = InMemoryUserDataStore()) = WatchlistsService(store, now = { 1_000L }, newId = { "id${++ids}" })
    private val aapl = InstrumentRef("aapl", "Apple Inc.", "NASDAQ", "usd")
    private val shopTo = InstrumentRef("SHOP.TO", "Shopify", "TSX", "CAD")
    private val shop = InstrumentRef("SHOP", "Shopify", "NYSE", "USD")

    @Test fun defaultListIsCreatedOnceAndImportsTheLegacyList() = runBlocking {
        val store = InMemoryUserDataStore(legacy = mapOf("u1" to listOf(aapl, aapl, InstrumentRef("bad symbol!"))))
        val service = service(store)
        val results = coroutineScope { (1..5).map { async { service.get("u1") } }.awaitAll() }
        assertTrue(results.all { it.watchlists.size == 1 })
        val default = results.first().watchlists.single()
        assertEquals("My Stocks", default.name); assertTrue(default.isDefault); assertEquals("default", default.id)
        assertEquals(listOf("AAPL"), default.entries.map { it.instrument.symbol }, "legacy import deduplicated and validated")
        assertEquals("USD", default.entries.single().instrument.currency)
        assertEquals(1, service.get("u1").watchlists.size)
    }

    @Test fun createRenameDeleteWithValidation() = runBlocking {
        val service = service()
        val created = service.create("u1", "  Growth   stocks ")
        val growth = created.watchlists.last()
        assertEquals("Growth stocks", growth.name)
        assertEquals("DUPLICATE_NAME", assertFailsWith<UserDataException> { service.create("u1", "growth STOCKS") }.code)
        assertEquals("INVALID_NAME", assertFailsWith<UserDataException> { service.create("u1", "   ") }.code)
        assertEquals("INVALID_NAME", assertFailsWith<UserDataException> { service.create("u1", "x".repeat(41)) }.code)
        assertEquals("INVALID_NAME", assertFailsWith<UserDataException> { service.create("u1", "bad\u0007name") }.code)
        assertEquals("Dividends", service.rename("u1", growth.id, "Dividends").watchlists.last().name)
        val afterDelete = service.delete("u1", "default")
        assertEquals(listOf("Dividends"), afterDelete.watchlists.map { it.name })
        assertTrue(afterDelete.watchlists.single().isDefault, "another list becomes the default")
        assertEquals("LAST_WATCHLIST", assertFailsWith<UserDataException> { service.delete("u1", growth.id) }.code)
    }

    @Test fun entriesAddRemoveDuplicatesAndExchangeIdentity() = runBlocking {
        val service = service()
        service.add("u1", "default", aapl)
        val again = service.add("u1", "default", aapl.copy(symbol = " AAPL "))
        assertEquals(1, again.watchlists.single().entries.size, "no duplicate in the same list")
        val both = service.add("u1", "default", shopTo).let { service.add("u1", "default", shop) }
        assertEquals(listOf("AAPL", "SHOP.TO", "SHOP"), both.watchlists.single().entries.map { it.instrument.symbol }, "different listings are different stocks")
        val removed = service.remove("u1", "default", both.watchlists.single().entries.first().id)
        assertEquals(listOf("SHOP.TO", "SHOP"), removed.watchlists.single().entries.map { it.instrument.symbol })
        assertEquals("INVALID_SYMBOL", assertFailsWith<UserDataException> { service.add("u1", "default", InstrumentRef("not valid")) }.code)
        assertEquals("WATCHLIST_NOT_FOUND", assertFailsWith<UserDataException> { service.add("u1", "missing", aapl) }.code)
    }

    @Test fun moveCopyReorderAndNotes() = runBlocking {
        val service = service()
        service.add("u1", "default", aapl); service.add("u1", "default", shop)
        val growth = service.create("u1", "Growth").watchlists.last().id
        val entry = service.get("u1").watchlists.first().entries.first()
        service.updateNote("u1", "default", entry.id, "  Watching services growth.\nReview earnings.  ")
        val copied = service.move("u1", "default", entry.id, growth, copy = true)
        assertEquals(2, copied.watchlists.first().entries.size, "copy keeps the original")
        val copy = copied.watchlists.last().entries.single()
        assertEquals("Watching services growth.\nReview earnings.", copy.note, "notes travel with the entry; per-entry notes")
        assertNotEquals(entry.id, copy.id)
        val moved = service.move("u1", "default", entry.id, growth, copy = false)
        assertEquals(listOf("SHOP"), moved.watchlists.first().entries.map { it.instrument.symbol })
        assertEquals(1, moved.watchlists.last().entries.size, "moving onto an existing stock doesn't duplicate it")
        service.add("u1", growth, shop)
        val ids = service.get("u1").watchlists.last().entries.map { it.id }
        val reordered = service.reorderEntries("u1", growth, ids.reversed())
        assertEquals(listOf("SHOP", "AAPL"), reordered.watchlists.last().entries.map { it.instrument.symbol })
        assertEquals("INVALID_ORDER", assertFailsWith<UserDataException> { service.reorderEntries("u1", growth, ids.take(1)) }.code)
        val updated = service.updateNote("u1", growth, copy.id, "New reason")
        assertEquals("New reason", updated.watchlists.last().entries.first { it.id == copy.id }.note)
        assertEquals(1_000L, updated.watchlists.last().entries.first { it.id == copy.id }.noteUpdatedAt)
        val deleted = service.updateNote("u1", growth, copy.id, "   ")
        assertNull(deleted.watchlists.last().entries.first { it.id == copy.id }.note, "blank deletes the note")
        assertEquals("NOTE_TOO_LONG", assertFailsWith<UserDataException> { service.updateNote("u1", growth, copy.id, "x".repeat(1_001)) }.code)
    }

    @Test fun usersNeverSeeEachOthersLists() = runBlocking {
        val store = InMemoryUserDataStore()
        val service = service(store)
        service.add("alice", "default", aapl)
        service.updateNote("alice", "default", service.get("alice").watchlists.single().entries.single().id, "private")
        assertTrue(service.get("bob").watchlists.single().entries.isEmpty())
        assertEquals("ENTRY_NOT_FOUND", assertFailsWith<UserDataException> {
            service.updateNote("bob", "default", service.get("alice").watchlists.single().entries.single().id, "hack")
        }.code)
    }

    @Test fun guestImportSkipsDuplicatesAndLimits() = runBlocking {
        val service = service()
        service.add("u1", "default", aapl)
        val imported = service.import("u1", listOf(aapl, shop, InstrumentRef("???")))
        assertEquals(listOf("AAPL", "SHOP"), imported.watchlists.single().entries.map { it.instrument.symbol })
        repeat(UserDataLimits.MAX_WATCHLISTS - 1) { service.create("u1", "List $it") }
        assertEquals("WATCHLIST_LIMIT", assertFailsWith<UserDataException> { service.create("u1", "One more") }.code)
    }
}
