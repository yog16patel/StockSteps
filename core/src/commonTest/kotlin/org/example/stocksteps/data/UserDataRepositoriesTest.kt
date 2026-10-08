package org.example.stocksteps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.data.watchlist.watchlistOwner
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.SignOut
import org.example.stocksteps.domain.WatchlistRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class UserDataRepositoriesTest {
    /** Real-time scope: the Ktor mock engine completes on real threads, so virtual time can't wait for it. */
    private fun real(block: suspend (CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { block(scope) } finally { scope.cancel() }
    }
    private suspend fun settle() = delay(250)

    private class FakeAuth(uid: String?) : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(user = uid?.let { User(it, "$it@example.com") }, initializing = false))
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        var signedOut = false
        var tokenRefreshes = 0
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signOut() { signedOut = true; mutable.value = AuthSession(initializing = false) }
        override suspend fun idToken(forceRefresh: Boolean): String? { if (forceRefresh) tokenRefreshes++; return session.value.user?.let { "token-${it.id}-${if (forceRefresh) "fresh" else "old"}" } }
        fun switch(uid: String?) { mutable.value = AuthSession(user = uid?.let { User(it, "$it@example.com") }, initializing = false) }
    }

    /** A tiny fake backend: per-user watchlists, 401 for stale tokens when [expireFirst]. */
    private class FakeServer(var online: Boolean = true, var expireFirst: Boolean = false) {
        val lists = mutableMapOf<String, MutableList<String>>()
        val requests = mutableListOf<String>()
        val devices = mutableMapOf<String, String>()
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            if (!online) throw IllegalStateException("offline")
            val token = request.headers[HttpHeaders.Authorization].orEmpty().removePrefix("Bearer ")
            if (expireFirst && token.endsWith("-old")) return@MockEngine respond("""{"code":"SIGN_IN_REQUIRED","message":"x"}""", HttpStatusCode.Unauthorized, json)
            val uid = token.removePrefix("token-").substringBefore("-")
            val body = request.body.toByteArray().decodeToString()
            when {
                request.url.encodedPath.endsWith("/devices") -> { devices[uid] = body; respond("", HttpStatusCode.NoContent) }
                request.url.encodedPath.contains("/devices/") -> { devices.remove(uid); respond("", HttpStatusCode.NoContent) }
                request.url.encodedPath.endsWith("/import") -> {
                    Regex("\"symbol\":\"([A-Z]+)\"").findAll(body).forEach { m -> lists.getOrPut(uid) { mutableListOf() }.let { if (m.groupValues[1] !in it) it += m.groupValues[1] } }
                    respond(response(uid), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Post -> {
                    val symbol = Regex("\"symbol\":\"([A-Z]+)\"").find(body)!!.groupValues[1]
                    lists.getOrPut(uid) { mutableListOf() }.let { if (symbol !in it) it += symbol }
                    respond(response(uid), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Delete -> { lists[uid]?.removeAll { request.url.encodedPath.endsWith("e-$it") }; respond(response(uid), HttpStatusCode.OK, json) }
                else -> respond(response(uid), HttpStatusCode.OK, json)
            }
        }
        fun response(uid: String): String {
            val entries = lists[uid].orEmpty().mapIndexed { i, s -> """{"id":"e-$s","instrument":{"symbol":"$s"},"order":$i,"addedAt":$i}""" }.joinToString(",")
            return """{"watchlists":[{"id":"default","name":"My Stocks","order":0,"createdAt":0,"updatedAt":0,"isDefault":true,"entries":[$entries]}],
                "limits":{"maxWatchlists":20,"maxEntries":100,"maxNameLength":40,"maxNoteLength":1000,"maxAlerts":50}}"""
        }
        companion object { val json = headersOf(HttpHeaders.ContentType, "application/json") }
    }

    private class Setup(scope: CoroutineScope, uid: String?, val server: FakeServer = FakeServer(), env: String = "mock") {
        val auth = FakeAuth(uid)
        val environment = MutableStateFlow(env)
        val cache = InMemoryUserDataCache()
        val client = HttpClient(server.engine) { configureStockStepsClient() }
        val api = UserApi(client, { "https://stocksteps.test" }, auth::idToken)
        val lists = UserWatchlistsRepository(auth, api, cache, environment, scope).also { it.start() }
    }

    @Test fun accountsAndEnvironmentsNeverShareData() = real { backgroundScope ->
        val setup = Setup(backgroundScope, "alice")
        setup.server.lists["alice"] = mutableListOf("AAPL")
        setup.server.lists["bob"] = mutableListOf("TSLA")
        settle()
        assertEquals(listOf("AAPL"), setup.lists.state.value.value!!.watchlists.single().entries.map { it.instrument.symbol })
        setup.auth.switch("bob")
        settle()
        val shown = setup.lists.state.value
        assertEquals("bob", shown.uid)
        assertTrue(shown.value == null || shown.value!!.watchlists.single().entries.none { it.instrument.symbol == "AAPL" }, "never alice's data for bob")
        settle()
        assertEquals(listOf("TSLA"), setup.lists.state.value.value!!.watchlists.single().entries.map { it.instrument.symbol })
        setup.environment.value = "real"
        settle()
        assertEquals("real", setup.lists.state.value.environment)
        assertTrue(setup.server.requests.size >= 3, "switching environment reloads from that backend instead of reusing the mock copy")
    }

    @Test fun offlineShowsTheCachedCopyAndMutationsFailClearly() = real { backgroundScope ->
        val setup = Setup(backgroundScope, "alice")
        setup.server.lists["alice"] = mutableListOf("AAPL")
        settle()
        setup.server.online = false
        setup.auth.switch(null); settle()
        setup.auth.switch("alice"); settle()
        val state = setup.lists.state.value
        assertTrue(state.offline); assertNotNull(state.error)
        assertEquals(listOf("AAPL"), state.value!!.watchlists.single().entries.map { it.instrument.symbol }, "cached copy")
        val failure = assertFailsWith<UserDataRequestException> { setup.lists.add("default", InstrumentRef("MSFT")) }
        assertTrue(failure.message!!.contains("offline"))
        assertEquals(1, setup.lists.state.value.value!!.watchlists.single().entries.size, "not shown as saved")
    }

    @Test fun expiredTokensAreRefreshedOnce() = real { backgroundScope ->
        val setup = Setup(backgroundScope, "alice", FakeServer(expireFirst = true))
        settle()
        assertNotNull(setup.lists.state.value.value)
        assertEquals(1, setup.auth.tokenRefreshes)
    }

    @Test fun facadeUsesTheGuestListSignedOutAndImportsItOnSignIn() = real { backgroundScope ->
        val setup = Setup(backgroundScope, null)
        val local = InMemoryLocal()
        val guest = object : WatchlistRepository {
            override val snapshot = local.observe("guest").map { WatchlistSnapshot(null, it) }
            override val items = snapshot.map { it.items }
            override val syncState = MutableStateFlow(WatchlistSyncState())
            override suspend fun add(symbol: String) = local.add("guest", symbol, 1, false)
            override suspend fun remove(symbol: String) = local.remove("guest", symbol, false)
            override fun retrySync() = Unit
        }
        val facade = AccountWatchlistRepository(setup.auth, guest, setup.lists, local, backgroundScope)
        facade.add("NVDA")
        assertEquals(listOf("NVDA"), facade.snapshot.first().items.map { it.symbol })
        // Sign-in merges the guest list into the account's local copy (existing behaviour), then it's imported.
        local.add(watchlistOwner("alice"), "NVDA", 1, false)
        setup.auth.switch("alice")
        settle()
        assertEquals<List<String>?>(listOf("NVDA"), setup.server.lists["alice"])
        assertTrue(local.observe(watchlistOwner("alice")).first().isEmpty(), "imported rows are cleared locally")
        assertEquals(listOf("NVDA"), facade.snapshot.first { it.userId == "alice" && it.items.isNotEmpty() }.items.map { it.symbol })
        facade.add("AMD")
        settle()
        assertEquals<List<String>?>(listOf("NVDA", "AMD"), setup.server.lists["alice"])
        facade.remove("NVDA")
        settle()
        assertEquals<List<String>?>(listOf("AMD"), setup.server.lists["alice"])
    }

    @Test fun deviceRegistrationFollowsTheAccountAndSignOutCleansUp() = real { backgroundScope ->
        val setup = Setup(backgroundScope, "alice")
        val tokens = object : PushTokenSource { override val token = MutableStateFlow<String?>(null); override val platform = "android" }
        val registrar = DeviceRegistrar(setup.auth, setup.api, tokens, setup.environment, { "install-1" }, backgroundScope).also { it.start() }
        settle()
        assertTrue(setup.server.devices.isEmpty(), "no token yet (permission not granted)")
        tokens.token.value = "fcm-1"; settle()
        assertTrue(setup.server.devices.getValue("alice").contains("fcm-1"))
        tokens.token.value = "fcm-2"; settle()
        assertTrue(setup.server.devices.getValue("alice").contains("fcm-2"), "rotation re-registers")
        setup.cache.write(userCacheOwner("mock", "alice"), "watchlists", "{}", 1)
        val local = InMemoryLocal().also { it.add(watchlistOwner("alice"), "AAPL", 1, false) }
        SignOut(setup.auth, local, setup.cache, beforeSignOut = registrar::unregister)()
        assertTrue(setup.auth.signedOut)
        assertFalse("alice" in setup.server.devices, "device unlinked while still authenticated")
        assertNull(setup.cache.read(userCacheOwner("mock", "alice"), "watchlists"), "cached user data cleared")
        assertTrue(local.observe(watchlistOwner("alice")).first().isEmpty())
    }

    @Test fun watchDataFallsBackToTheCachedCopyWhenOffline() = real { backgroundScope ->
        var online = true
        val client = HttpClient(MockEngine { request ->
            if (!online) throw IllegalStateException("offline")
            assertEquals("AAPL,MSFT", request.url.parameters["symbols"])
            respond("""{"quotes":[{"symbol":"AAPL","price":1.0},{"symbol":"MSFT","price":2.0}],"session":{"market":"US","status":"OPEN","timezone":"x","asOf":"2026-10-07T14:00:00Z","source":"c"},"generatedAt":"2026-10-07T14:00:00Z","notice":"n"}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }
        val repo = WatchDataRepository(StockStepsApi(client, "https://stocksteps.test"), InMemoryUserDataCache(), now = { 42 })
        assertFalse(repo.load("o", listOf("AAPL", "MSFT")).fromCache)
        online = false
        val cached = repo.load("o", listOf("AAPL", "MSFT"))
        assertTrue(cached.fromCache); assertEquals(42, cached.savedAt)
        assertFails { repo.load("other", listOf("AAPL", "MSFT")) }
    }

    private class InMemoryLocal : org.example.stocksteps.data.watchlist.WatchlistLocalStore {
        private val rows = MutableStateFlow<Map<String, List<WatchlistItem>>>(emptyMap())
        override fun observe(owner: String) = rows.map { it[owner].orEmpty() }
        override fun observePending(owner: String) = flowOf(emptyList<org.example.stocksteps.data.watchlist.PendingWatchlistOperation>())
        override suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean) { rows.update { it + (owner to (it[owner].orEmpty().filter { r -> r.symbol != symbol } + WatchlistItem(symbol, now, now))) } }
        override suspend fun remove(owner: String, symbol: String, queue: Boolean) { rows.update { it + (owner to it[owner].orEmpty().filter { r -> r.symbol != symbol }) } }
        override suspend fun mergeGuest(owner: String) = Unit
        override suspend fun applySnapshot(owner: String, items: List<WatchlistItem>) = Unit
        override suspend fun acknowledge(owner: String, operation: org.example.stocksteps.data.watchlist.PendingWatchlistOperation) = Unit
        override suspend fun clearOwner(owner: String) { rows.update { it - owner } }
    }
}
