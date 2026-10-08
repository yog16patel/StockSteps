package org.example.stocksteps.userdata

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.*
import kotlin.test.*

class PortfolioServiceTest {
    private val store = InMemoryUserDataStore()
    private val service = PortfolioService(store, now = { 1_791_504_000_000 })
    private val account = PortfolioAccount("a", "Personal", reportingCurrency = PortfolioCurrency.USD)
    private fun opening() = PortfolioTransaction("opening", "a", TransactionType.OPENING_POSITION, "2026-01-01", PortfolioCurrency.USD,
        InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"), "10", "100")

    @Test fun ownershipAndWatchlistsAreIndependent(): Unit = runBlocking {
        service.saveAccount("alice", account)
        service.saveTransaction("alice", opening(), false)
        assertTrue(service.get("bob").accounts.isEmpty())
        assertFailsWith<UserDataException> { service.saveTransaction("bob", opening(), false) }
        val watchlists = WatchlistsService(store)
        watchlists.get("alice")
        watchlists.add("alice", "default", opening().instrument!!)
        service.deleteAccount("alice", "a")
        assertEquals("AAPL", watchlists.get("alice").watchlists.single().entries.single().instrument.symbol)
        service.saveAccount("alice", account)
        service.saveTransaction("alice", opening(), false)
        watchlists.remove("alice", "default", watchlists.get("alice").watchlists.single().entries.single().id)
        assertEquals("10", PortfolioEngine.replay(service.get("alice"), "a").holdings.single().quantity)
    }
    @Test fun concurrentDuplicateSubmissionsCreateOneNormalizedTransaction(): Unit = runBlocking {
        service.saveAccount("alice", account)
        (1..12).map { async { service.saveTransaction("alice", opening().copy(grossAmount = "999999", netAmount = "999999"), false) } }.awaitAll()
        val ledger = service.get("alice")
        assertEquals(1, ledger.transactions.size)
        assertEquals("1000", ledger.transactions.single().grossAmount)
        assertEquals("1000", ledger.transactions.single().netAmount)
        assertEquals(2, ledger.revision)
        assertFailsWith<UserDataException> { service.saveTransaction("alice", opening().copy(quantity = "20"), false) }
    }
    @Test fun historicalEditsAndDeletesAreAtomicAndRejectOverselling(): Unit = runBlocking {
        service.saveAccount("alice", account)
        service.saveTransaction("alice", opening(), false)
        service.saveTransaction("alice", opening().copy(id = "sale", type = TransactionType.SELL, quantity = "8", unitPrice = "120", tradeDate = "2026-02-01"), false)
        val before = service.get("alice")
        assertFailsWith<UserDataException> { service.deleteTransaction("alice", "opening") }
        assertFailsWith<UserDataException> { service.saveTransaction("alice", opening().copy(quantity = "5"), true) }
        assertEquals(before, service.get("alice"))
        service.deleteTransaction("alice", "sale")
        service.deleteTransaction("alice", "opening")
        assertTrue(service.get("alice").transactions.isEmpty())
    }
    @Test fun archivedAccountsRejectNewTradesAndCannotBeAccessedByAnotherUser(): Unit = runBlocking {
        service.saveAccount("alice", account.copy(archived = true))
        assertFailsWith<UserDataException> { service.saveTransaction("alice", opening(), false) }
        assertFailsWith<UserDataException> { service.deleteAccount("bob", "a") }
    }
    @Test fun sameDayTradesKeepServerSubmissionOrderEvenWhenTimestampsMatch(): Unit = runBlocking {
        service.saveAccount("alice", account)
        service.saveTransaction("alice", opening().copy(id = "z-opening"), false)
        service.saveTransaction("alice", opening().copy(id = "a-sale", type = TransactionType.SELL, quantity = "10", unitPrice = "120"), false)
        val ledger = service.get("alice")
        assertEquals(listOf(1L, 2L), ledger.transactions.map { it.sequence })
        assertEquals("200", PortfolioEngine.replay(ledger, "a").holdings.single().realizedGain)
    }
}
