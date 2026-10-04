package org.example.stocksteps.data.watchlist

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.example.stocksteps.data.account.*
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineWatchlistRepositoryTest {
    private class Auth : AuthRepository {
        override val session = MutableStateFlow(AuthSession(initializing = false))
        override val currentUser = session.map { it.user }
        fun login(uid: String?) { session.value = AuthSession(uid?.let { User(it, "$it@example.com") }, false) }
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(email: String, password: String) {}
        override suspend fun signOut() { login(null) }
    }
    private class Store : WatchlistLocalStore {
        val rows = mutableMapOf<String, MutableStateFlow<List<WatchlistItem>>>()
        val outbox = mutableMapOf<String, MutableStateFlow<List<PendingWatchlistOperation>>>()
        var revision = 0L
        private fun rows(owner: String) = rows.getOrPut(owner) { MutableStateFlow(emptyList()) }
        private fun pending(owner: String) = outbox.getOrPut(owner) { MutableStateFlow(emptyList()) }
        override fun observe(owner: String) = rows(owner)
        override fun observePending(owner: String) = pending(owner)
        override suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean) {
            if (rows(owner).value.any { it.symbol == symbol }) return
            val item = WatchlistItem(symbol, now, now)
            rows(owner).value += item
            if (queue) enqueue(owner, symbol, item)
        }
        override suspend fun remove(owner: String, symbol: String, queue: Boolean) {
            rows(owner).value = rows(owner).value.filter { it.symbol != symbol }
            if (queue) enqueue(owner, symbol, null)
        }
        private fun enqueue(owner: String, symbol: String, item: WatchlistItem?) {
            pending(owner).value = pending(owner).value.filter { it.symbol != symbol } + PendingWatchlistOperation(++revision, symbol, item)
        }
        override suspend fun mergeGuest(owner: String) {
            rows(GUEST_OWNER).value.forEach { add(owner, it.symbol, it.addedAt, true) }
            rows(GUEST_OWNER).value = emptyList()
        }
        override suspend fun applySnapshot(owner: String, items: List<WatchlistItem>) {
            val dirty = pending(owner).value.map { it.symbol }.toSet()
            rows(owner).value = rows(owner).value.filter { it.symbol in dirty } + items.filter { it.symbol !in dirty }
        }
        override suspend fun acknowledge(owner: String, operation: PendingWatchlistOperation) {
            pending(owner).value = pending(owner).value.filter { it.revision != operation.revision }
        }
    }
    private class Cloud : PlatformWatchlistGateway {
        data class Call(val uid: String, val symbol: String, val item: WatchlistItem?, val complete: (String?) -> Unit)
        val calls = mutableListOf<Call>()
        val listeners = mutableMapOf<String, (List<WatchlistItem>?, Boolean, String?) -> Unit>()
        val cancelled = mutableSetOf<String>()
        override fun observe(uid: String, onSnapshot: (List<WatchlistItem>?, Boolean, String?) -> Unit): AccountSubscription {
            listeners[uid] = onSnapshot
            return object : AccountSubscription { override fun cancel() { cancelled += uid } }
        }
        override fun put(uid: String, item: WatchlistItem, completion: (String?) -> Unit) { calls += Call(uid, item.symbol, item, completion) }
        override fun delete(uid: String, symbol: String, completion: (String?) -> Unit) { calls += Call(uid, symbol, null, completion) }
    }

    @Test fun failedCloudWriteKeepsLocalDataAndManualRetryDrainsOutbox() = runTest {
        val auth = Auth(); val local = Store(); val cloud = Cloud()
        val repository = OfflineWatchlistRepository(auth, local, cloud, backgroundScope, now = { 100 })
        auth.login("alice"); runCurrent()
        repository.add("aapl"); runCurrent()
        assertEquals("AAPL", local.observe(watchlistOwner("alice")).value.single().symbol)
        cloud.calls.single().complete("Offline"); runCurrent()
        assertEquals(1, local.observePending(watchlistOwner("alice")).value.size)
        assertEquals("Offline", repository.syncState.value.error)
        repository.retrySync(); runCurrent()
        assertEquals(2, cloud.calls.size)
        cloud.calls.last().complete(null); runCurrent()
        assertTrue(local.observePending(watchlistOwner("alice")).value.isEmpty())
        assertEquals("AAPL", local.observe(watchlistOwner("alice")).value.single().symbol)
    }

    @Test fun cachedSnapshotCannotDeleteLocalRowsAndPendingRemovalSurvivesServerSnapshot() = runTest {
        val auth = Auth(); val local = Store(); val cloud = Cloud()
        val repository = OfflineWatchlistRepository(auth, local, cloud, backgroundScope, now = { 100 })
        local.add(watchlistOwner("alice"), "AAPL", 100, false)
        auth.login("alice"); runCurrent()
        cloud.listeners.getValue("alice")(emptyList(), false, null); runCurrent()
        assertEquals(1, local.observe(watchlistOwner("alice")).value.size)
        repository.remove("AAPL"); runCurrent()
        cloud.listeners.getValue("alice")(listOf(WatchlistItem("AAPL", 100, 100)), true, null); runCurrent()
        assertTrue(local.observe(watchlistOwner("alice")).value.isEmpty())
        assertNull(cloud.calls.single().item)
    }

    @Test fun inFlightAddAcknowledgmentDoesNotEraseNewerRemove() = runTest {
        val auth = Auth(); val local = Store(); val cloud = Cloud()
        val repository = OfflineWatchlistRepository(auth, local, cloud, backgroundScope, now = { 100 })
        auth.login("alice"); runCurrent()
        repository.add("AAPL"); runCurrent()
        repository.remove("AAPL"); runCurrent()
        cloud.calls.first().complete(null); runCurrent()
        assertEquals(2, cloud.calls.size)
        assertNull(cloud.calls.last().item)
        assertEquals(1, local.observePending(watchlistOwner("alice")).value.size)
        cloud.calls.last().complete(null); runCurrent()
        assertTrue(local.observePending(watchlistOwner("alice")).value.isEmpty())
    }

    @Test fun accountSwitchCancelsListenersAndLateOldSnapshotCannotAffectNewAccount() = runTest {
        val auth = Auth(); val local = Store(); val cloud = Cloud()
        val repository = OfflineWatchlistRepository(auth, local, cloud, backgroundScope, now = { 100 })
        val observed = mutableListOf<WatchlistSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.snapshot.collect { observed += it } }
        auth.login("alice"); runCurrent()
        val staleListener = cloud.listeners.getValue("alice")
        auth.login("bob"); runCurrent()
        staleListener(listOf(WatchlistItem("AAPL", 100, 100)), true, null); runCurrent()
        assertTrue("alice" in cloud.cancelled)
        assertEquals("bob", observed.last().userId)
        assertTrue(observed.last().items.isEmpty())
        assertTrue(local.observe(watchlistOwner("bob")).value.isEmpty())
        assertTrue(local.observe(watchlistOwner("alice")).value.isEmpty())
    }
}
