package org.example.stocksteps.portfolio

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class PortfolioRepositoryTest {
    private class Auth : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(User("alice", "alice@example.test"), initializing = false))
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
        override suspend fun idToken(forceRefresh: Boolean) = mutable.value.user?.id
    }
    @Test fun accountAndEnvironmentChangesResetSelectionAndNeverMixCachedLedgers(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = Auth()
        val environment = MutableStateFlow("mock")
        val online = MutableStateFlow(true)
        val cache = InMemoryUserDataCache()
        val client = HttpClient(MockEngine { request ->
            if (!online.value) throw IllegalStateException("offline")
            val uid = request.headers[HttpHeaders.Authorization].orEmpty().removePrefix("Bearer ")
            val account = PortfolioAccount("${request.url.host}-$uid", "$uid ${request.url.host}")
            respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }
        val api = UserApi(client, { "http://${environment.value}" }, auth::idToken, { auth.session.value.user?.id })
        val repository = PortfolioRepository(auth, api, cache, environment, scope)
        suspend fun await(predicate: (PortfolioSnapshot) -> Boolean) = withTimeout(5_000) { repository.snapshot.first(predicate) }
        try {
            repository.start()
            await { it.selectedAccountId == "mock-alice" }
            // The ledger is cached asynchronously after it's shown; the end of this test relies on that copy.
            withTimeout(5_000) { while (cache.read(userCacheOwner("mock", "alice"), "portfolio.ledger.v1") == null) delay(10) }
            auth.mutable.value = AuthSession(User("bob", "bob@example.test"), initializing = false)
            await { it.selectedAccountId == "mock-bob" }
            environment.value = "real"
            await { it.selectedAccountId == "real-bob" }
            online.value = false
            repository.refresh()
            await { it.offline && it.selectedAccountId == "real-bob" }
            auth.signOut()
            await { !it.signedIn && it.ledger.accounts.isEmpty() && it.position == null }
            cache.clearAccount("bob")
            assertNull(cache.read(userCacheOwner("mock", "bob"), "portfolio.ledger.v1"))
            assertNull(cache.read(userCacheOwner("real", "bob"), "portfolio.ledger.v1"))
            assertNotNull(cache.read(userCacheOwner("mock", "alice"), "portfolio.ledger.v1"))
        } finally { scope.cancel(); client.close() }
    }

    @Test fun tokenRestorationCannotSendARequestAfterTheAccountChanges(): Unit = runBlocking {
        var uid = "alice"
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) }) { configureStockStepsClient() }
        try {
            val api = UserApi(client, { "http://mock" }, token = { uid = "bob"; "alice-token" }, identity = { uid })
            assertFailsWith<CancellationException> { api.portfolio() }
            assertEquals(0, requests)
        } finally { client.close() }
    }
}
