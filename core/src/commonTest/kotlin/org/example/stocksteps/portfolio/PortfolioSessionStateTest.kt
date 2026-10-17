package org.example.stocksteps.portfolio

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

/**
 * Portfolio Phase E.2: a failed refresh keeps what kind of failure it was, so the banner can offer the recovery that works — an ended session
 * (token refresh failed, or 401) is SESSION and sets `sessionExpired`; a server error is SERVER; no answer is NETWORK. The cached ledger stays.
 */
class PortfolioSessionStateTest {
    private class Auth : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(User("alice", "alice@example.test"), initializing = false))
        @kotlin.concurrent.Volatile var tokenFails = false // read by the presenter's coroutines on Dispatchers.Default
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
        override suspend fun idToken(forceRefresh: Boolean): String? {
            if (tokenFails) throw AccountException("Your session has ended. Sign in again to continue.")
            return mutable.value.user?.id
        }
    }

    @Test fun failuresAreClassifiedByCause() {
        assertEquals(UserDataErrorKind.SESSION, userDataErrorKind(AccountException("Your session has ended.")))
        assertEquals(UserDataErrorKind.SESSION, userDataErrorKind(StockStepsApiException(401, ApiError("SIGN_IN_REQUIRED", "Sign in."))))
        assertEquals(UserDataErrorKind.SERVER, userDataErrorKind(StockStepsApiException(503, ApiError("UNAVAILABLE", "Try later."))))
        assertEquals(UserDataErrorKind.SERVER, userDataErrorKind(StockStepsApiException(409, ApiError("REJECTED", "No."))))
        assertEquals(UserDataErrorKind.NETWORK, userDataErrorKind(IllegalStateException("socket closed")))
        // A rejected change is classified by what caused it.
        assertEquals(UserDataErrorKind.SESSION, userDataErrorKind(UserDataRequestException("Your session has ended.", AccountException("x"))))
        assertEquals(UserDataErrorKind.NETWORK, userDataErrorKind(UserDataRequestException("Offline.", IllegalStateException("x"))))
        assertEquals(UserDataErrorKind.SERVER, userDataErrorKind(UserDataRequestException("No cause.")))
    }

    @Test fun anEndedSessionIsSessionExpiredUntilTheNextSuccessfulSyncAndKeepsTheCachedLedger(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serverDown = MutableStateFlow(false)
        val account = PortfolioAccount("acc-1", "TFSA", reportingCurrency = PortfolioCurrency.CAD)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            if (serverDown.value && request.url.encodedPath.endsWith("/portfolio")) respond("""{"code":"UNAVAILABLE","message":"The server is busy."}""", HttpStatusCode.ServiceUnavailable, json)
            else respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK, json)
        }) { configureStockStepsClient() }
        val auth = Auth()
        val cache = InMemoryUserDataCache()
        val repository = PortfolioRepository(auth, UserApi(client, { "http://mock" }, auth::idToken, { auth.session.value.user?.id }), cache,
            MutableStateFlow("mock"), scope)
        val presenter = PortfolioPresenter(repository, WatchDataRepository(StockStepsApi(client) { "http://mock" }, cache), scope)
        try {
            repository.start()
            withTimeout(5_000) { presenter.state.first { it.selectedAccountId == "acc-1" && !it.loading } }
            // The presenter's market report for the selected account is fetched on its own coroutine (this mock answers it with a ledger, so it
            // fails). Wait for that attempt first: if it only reached the token after `tokenFails` below, it would correctly report SESSION and keep
            // `sessionExpired` until the next command — the full suite under load hit this race.
            val report = withTimeout(5_000) { presenter.state.first { it.error != null } }
            assertNotEquals(UserDataErrorKind.SESSION, report.errorKind)
            assertFalse(presenter.state.value.sessionExpired)

            auth.tokenFails = true
            repository.refresh()
            val expired = withTimeout(5_000) { presenter.state.first { it.sessionExpired } }
            assertEquals(UserDataErrorKind.SESSION, repository.state.value.errorKind)
            assertTrue(expired.offline)
            assertEquals("acc-1", expired.selectedAccountId) // the cached ledger stays on screen
            assertEquals("Your session has ended. Sign in again to continue.", repository.state.value.error)

            auth.tokenFails = false
            serverDown.value = true
            repository.refresh()
            withTimeout(5_000) { presenter.state.first { !it.sessionExpired && repository.state.value.errorKind == UserDataErrorKind.SERVER } }

            serverDown.value = false
            repository.refresh()
            withTimeout(5_000) { presenter.state.first { !it.offline && repository.state.value.errorKind == null } }
            assertFalse(presenter.state.value.sessionExpired)
        } finally { scope.cancel(); client.close() }
    }

    /** A change that fails because the session ended also marks it expired (Add is then unavailable until signing in again). */
    @Test fun aChangeThatFailsWithAnEndedSessionMarksItExpired(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val account = PortfolioAccount("acc-1", "TFSA", reportingCurrency = PortfolioCurrency.CAD)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { _ ->
            respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK, json)
        }) { configureStockStepsClient() }
        val auth = Auth()
        val cache = InMemoryUserDataCache()
        val repository = PortfolioRepository(auth, UserApi(client, { "http://mock" }, auth::idToken, { auth.session.value.user?.id }), cache,
            MutableStateFlow("mock"), scope)
        val presenter = PortfolioPresenter(repository, WatchDataRepository(StockStepsApi(client) { "http://mock" }, cache), scope)
        try {
            repository.start()
            withTimeout(5_000) { presenter.state.first { it.selectedAccountId == "acc-1" && !it.loading } }
            withTimeoutOrNull(2_000) { presenter.state.first { it.error != null } }
            presenter.clearError()
            auth.tokenFails = true
            presenter.saveTransaction(PortfolioTransaction("tx-1", "acc-1", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD, grossAmount = "100"))
            val failed = withTimeout(5_000) { presenter.state.first { !it.busy && it.errorSource == PortfolioErrorSource.SAVE_TRANSACTION } }
            assertTrue(failed.sessionExpired)
            assertEquals(UserDataErrorKind.SESSION, failed.errorKind)
            assertEquals("Your session has ended. Sign in again to continue.", failed.error)
            // Dismissing the rejected save keeps the session state: the sync was re-checked and is SESSION too.
            withTimeout(5_000) { repository.state.first { it.errorKind == UserDataErrorKind.SESSION } }
            presenter.clearError()
            val after = withTimeout(5_000) { presenter.state.first { it.errorSource == PortfolioErrorSource.REFRESH } }
            assertTrue(after.sessionExpired)
            assertEquals(UserDataErrorKind.SESSION, after.errorKind)
        } finally { scope.cancel(); client.close() }
    }
}
