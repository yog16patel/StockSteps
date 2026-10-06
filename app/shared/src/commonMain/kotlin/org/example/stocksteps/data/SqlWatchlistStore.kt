package org.example.stocksteps.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.stocksteps.data.watchlist.*
import org.example.stocksteps.local.WatchlistDatabase
import org.example.stocksteps.model.WatchlistItem

class SqlWatchlistStore(driver: SqlDriver) : WatchlistLocalStore {
    private val queries = WatchlistDatabase(driver).watchlistQueries
    private val mutex = Mutex()

    override fun observe(owner: String): Flow<List<WatchlistItem>> = queries.items(owner)
        .asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { WatchlistItem(it.symbol, it.addedAt, it.updatedAt, it.name, it.exchange, it.currency, it.exchangeFullName) } }

    override fun observePending(owner: String): Flow<List<PendingWatchlistOperation>> = queries.pending(owner)
        .asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { row ->
            PendingWatchlistOperation(row.revision, row.symbol,
                row.addedAt?.let { WatchlistItem(row.symbol, it, requireNotNull(row.updatedAt), row.name, row.exchange, row.currency, row.exchangeFullName) })
        } }

    override suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean) =
        addListing(owner, org.example.stocksteps.model.StockSearchResult(symbol, symbol), now, queue)

    override suspend fun addListing(owner: String, stock: org.example.stocksteps.model.StockSearchResult, now: Long, queue: Boolean) = transaction {
        val old = queries.item(owner, stock.symbol).executeAsOneOrNull()
        val added = old?.addedAt ?: now
        queries.putItem(owner, stock.symbol, added, now, stock.name, stock.exchange, stock.currency, stock.exchangeFullName)
        if (queue) queries.queue(owner, stock.symbol, added, now, stock.name, stock.exchange, stock.currency, stock.exchangeFullName)
    }

    override suspend fun remove(owner: String, symbol: String, queue: Boolean) = transaction {
        queries.removeItem(owner, symbol)
        if (queue) queries.queue(owner, symbol, null, null, null, null, null, null)
    }

    override suspend fun mergeGuest(owner: String) = transaction {
        val blocked = queries.pending(owner).executeAsList().associateBy { it.symbol }
        queries.items(GUEST_OWNER).executeAsList().forEach { guest ->
            // A pending account removal wins over a guest copy of the same symbol.
            if (blocked[guest.symbol]?.addedAt == null && blocked.containsKey(guest.symbol)) return@forEach
            val old = queries.item(owner, guest.symbol).executeAsOneOrNull()
            val addedAt = minOf(guest.addedAt, old?.addedAt ?: guest.addedAt)
            val updatedAt = maxOf(guest.updatedAt, old?.updatedAt ?: guest.updatedAt)
            queries.putItem(owner, guest.symbol, addedAt, updatedAt, old?.name ?: guest.name, old?.exchange ?: guest.exchange, old?.currency ?: guest.currency, old?.exchangeFullName ?: guest.exchangeFullName)
            queries.queue(owner, guest.symbol, addedAt, updatedAt, old?.name ?: guest.name, old?.exchange ?: guest.exchange, old?.currency ?: guest.currency, old?.exchangeFullName ?: guest.exchangeFullName)
        }
        // Atomic ownership transfer: a later login to another account cannot re-import these rows.
        queries.clearGuest()
    }

    override suspend fun applySnapshot(owner: String, items: List<WatchlistItem>) = transaction {
        val pending = queries.pending(owner).executeAsList().map { it.symbol }.toSet()
        val remote = items.associateBy { it.symbol }
        queries.items(owner).executeAsList().forEach { row ->
            if (row.symbol !in remote && row.symbol !in pending) queries.removeItem(owner, row.symbol)
        }
        items.filter { it.symbol !in pending }.forEach {
            queries.putItem(owner, it.symbol, it.addedAt, it.updatedAt, it.name, it.exchange, it.currency, it.exchangeFullName)
        }
    }

    override suspend fun acknowledge(owner: String, operation: PendingWatchlistOperation) = transaction {
        queries.acknowledge(owner, operation.revision)
    }

    private suspend fun transaction(block: () -> Unit) = withContext(Dispatchers.Default) {
        mutex.withLock { queries.transaction { block() } }
    }
}
