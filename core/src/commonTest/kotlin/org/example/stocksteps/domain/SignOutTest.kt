package org.example.stocksteps.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.example.stocksteps.data.watchlist.PendingWatchlistOperation
import org.example.stocksteps.data.watchlist.WatchlistLocalStore
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.model.User
import org.example.stocksteps.model.WatchlistItem
import kotlin.test.*

class SignOutTest {
    private class FakeAuth(var fail: Boolean = false) : AuthRepository {
        override val session = MutableStateFlow(AuthSession(User("alice", "a@example.com"), initializing = false))
        override val currentUser: Flow<User?> = session.map { it.user }
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signOut() {
            if (fail) throw AccountException("Could not complete the account request. Try again.")
            session.value = AuthSession(null, initializing = false)
        }
    }

    private class FakeStore : WatchlistLocalStore {
        val cleared = mutableListOf<String>()
        override fun observe(owner: String): Flow<List<WatchlistItem>> = emptyFlow()
        override fun observePending(owner: String): Flow<List<PendingWatchlistOperation>> = emptyFlow()
        override suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean) = Unit
        override suspend fun remove(owner: String, symbol: String, queue: Boolean) = Unit
        override suspend fun mergeGuest(owner: String) = Unit
        override suspend fun applySnapshot(owner: String, items: List<WatchlistItem>) = Unit
        override suspend fun acknowledge(owner: String, operation: PendingWatchlistOperation) = Unit
        override suspend fun clearOwner(owner: String) { cleared += owner }
    }

    @Test fun signOutEndsTheSessionThenClearsThatAccountsLocalCopy() = runTest {
        val auth = FakeAuth()
        val store = FakeStore()
        SignOut(auth, store)()
        assertNull(auth.session.value.user)
        assertEquals(listOf("user:alice"), store.cleared, "only the signed-out account; guest and other accounts untouched")
    }

    @Test fun failedSignOutKeepsEverything() = runTest {
        val store = FakeStore()
        assertFailsWith<AccountException> { SignOut(FakeAuth(fail = true), store)() }
        assertTrue(store.cleared.isEmpty(), "nothing is cleared unless Firebase sign-out succeeded")
    }
}
