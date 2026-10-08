package org.example.stocksteps.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.stocksteps.data.watchlist.*
import org.example.stocksteps.local.WatchlistDatabase
import org.example.stocksteps.model.WatchlistItem
import kotlin.test.*

class SqlWatchlistStoreTest {
    private fun database() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WatchlistDatabase.Schema.create(it) }

    @Test fun listingMetadataSurvivesGuestMergeOutboxAndCloudSnapshot() = runTest {
        val driver = database()
        try {
            val store = SqlWatchlistStore(driver)
            val listing = org.example.stocksteps.model.StockSearchResult("SHOP.TO", "Shopify", "CAD", "TSX", "Toronto Stock Exchange")
            store.addListing(GUEST_OWNER, listing, 100, false)
            store.addListing(GUEST_OWNER, listing.copy(symbol = "SHOP", currency = "USD", exchange = "NYSE"), 101, false)
            val owner = watchlistOwner("alice")
            store.mergeGuest(owner)
            val items = store.observe(owner).first()
            assertEquals(2, items.size)
            assertEquals(listing, items.first { it.symbol == "SHOP.TO" }.listing())
            val pending = store.observePending(owner).first()
            assertEquals(listing, pending.first { it.symbol == "SHOP.TO" }.item!!.listing())
            pending.forEach { store.acknowledge(owner, it) }
            store.applySnapshot(owner, items)
            assertEquals(listing, store.observe(owner).first().first { it.symbol == "SHOP.TO" }.listing())
        } finally { driver.close() }
    }

    @Test fun clearingOneAccountLeavesOtherAccountsAndGuestUntouched() = runTest {
        val driver = database()
        try {
            val store = SqlWatchlistStore(driver)
            val alice = watchlistOwner("alice")
            val bob = watchlistOwner("bob")
            store.add(alice, "MSFT", 100, true)
            store.add(bob, "AAPL", 100, true)
            store.add(GUEST_OWNER, "TSLA", 100, false)
            store.clearOwner(alice)
            assertTrue(store.observe(alice).first().isEmpty())
            assertTrue(store.observePending(alice).first().isEmpty(), "unsent changes are cleared too")
            assertEquals(listOf("AAPL"), store.observe(bob).first().map { it.symbol })
            assertEquals(listOf("TSLA"), store.observe(GUEST_OWNER).first().map { it.symbol })
        } finally { driver.close() }
    }

    @Test fun versionOneMigrationPreservesExistingRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE watchlist(owner TEXT NOT NULL, symbol TEXT NOT NULL, addedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(owner, symbol))", 0)
            driver.execute(null, "CREATE TABLE pending_operation(revision INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, symbol TEXT NOT NULL, addedAt INTEGER, updatedAt INTEGER, UNIQUE(owner, symbol))", 0)
            driver.execute(null, "INSERT INTO watchlist VALUES ('guest', 'SHOP.TO', 100, 100)", 0)
            WatchlistDatabase.Schema.migrate(driver, 1, 2)
            val item = SqlWatchlistStore(driver).observe(GUEST_OWNER).first().single()
            assertEquals("SHOP.TO", item.symbol)
            assertNull(item.exchange)
            assertEquals("SHOP.TO", item.listing().symbol)
        } finally { driver.close() }
    }

    @Test fun guestMergeDeduplicatesAndTransfersOnlyOnce() = runTest {
        val driver = database()
        try {
            val store = SqlWatchlistStore(driver)
            store.add(GUEST_OWNER, "AAPL", 100, false)
            store.add(GUEST_OWNER, "NVDA", 120, false)
            store.add(watchlistOwner("alice"), "AAPL", 200, true)
            store.mergeGuest(watchlistOwner("alice"))
            assertEquals(listOf("AAPL", "NVDA"), store.observe(watchlistOwner("alice")).first().map { it.symbol })
            assertEquals(100, store.observe(watchlistOwner("alice")).first().first().addedAt)
            assertTrue(store.observe(GUEST_OWNER).first().isEmpty())
            store.mergeGuest(watchlistOwner("bob"))
            assertTrue(store.observe(watchlistOwner("bob")).first().isEmpty())
            assertEquals(2, store.observePending(watchlistOwner("alice")).first().size)
        } finally { driver.close() }
    }

    @Test fun oldAcknowledgmentCannotEraseNewerRemovalAndSnapshotsCannotResurrectIt() = runTest {
        val driver = database()
        try {
            val store = SqlWatchlistStore(driver)
            val owner = watchlistOwner("alice")
            store.add(owner, "SHOP.TO", 100, true)
            val add = store.observePending(owner).first().single()
            store.remove(owner, "SHOP.TO", true)
            store.acknowledge(owner, add)
            val remove = store.observePending(owner).first().single()
            assertNull(remove.item)
            assertNotEquals(add.revision, remove.revision)
            store.applySnapshot(owner, listOf(WatchlistItem("SHOP.TO", 50, 100)))
            assertTrue(store.observe(owner).first().isEmpty())
            store.acknowledge(owner, remove)
            assertTrue(store.observePending(owner).first().isEmpty())
        } finally { driver.close() }
    }

    @Test fun authoritativeSnapshotDeletesCleanRowsButPreservesPendingAddsAndOtherOwners() = runTest {
        val driver = database()
        try {
            val store = SqlWatchlistStore(driver)
            val alice = watchlistOwner("alice")
            val bob = watchlistOwner("bob")
            store.add(alice, "AAPL", 100, false)
            store.add(alice, "NVDA", 200, true)
            store.add(bob, "AAPL", 100, false)
            store.applySnapshot(alice, listOf(WatchlistItem("SHOP.TO", 300, 300)))
            assertEquals(setOf("NVDA", "SHOP.TO"), store.observe(alice).first().map { it.symbol }.toSet())
            assertEquals("AAPL", store.observe(bob).first().single().symbol)
        } finally { driver.close() }
    }

    @Test fun reopeningStoreRetainsRowsAndDurableRemovals() = runTest {
        val driver = database()
        try {
            val first = SqlWatchlistStore(driver)
            val owner = watchlistOwner("alice")
            first.add(owner, "AAPL", 100, true)
            first.add(owner, "NVDA", 200, true)
            first.remove(owner, "AAPL", true)
            val reopened = SqlWatchlistStore(driver)
            assertEquals("NVDA", reopened.observe(owner).first().single().symbol)
            assertNull(reopened.observePending(owner).first().first { it.symbol == "AAPL" }.item)
        } finally { driver.close() }
    }
}
