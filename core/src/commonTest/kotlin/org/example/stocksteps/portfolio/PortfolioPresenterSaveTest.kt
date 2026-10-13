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
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

/** Portfolio Phase D1: saving shows progress, ignores repeated taps while busy, and surfaces a server rejection without losing it. */
class PortfolioPresenterSaveTest {
    private class Auth : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(User("alice", "alice@example.test"), initializing = false))
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
        override suspend fun idToken(forceRefresh: Boolean) = mutable.value.user?.id
    }

    @Test fun repeatedTapsSaveOnceAndRejectionsAreShownUntilTheNextSave(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val posts = MutableStateFlow(0)
        val account = PortfolioAccount("acc-1", "TFSA", reportingCurrency = PortfolioCurrency.CAD)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath.endsWith("portfolio/transactions")) {
                val count = posts.updateAndGet { it + 1 }
                gate.await()
                if (count == 1) return@MockEngine respond("""{"code":"REJECTED","message":"Selling more shares than owned."}""", HttpStatusCode.Conflict, json)
            }
            respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK, json)
        }) { configureStockStepsClient() }
        val auth = Auth()
        val cache = InMemoryUserDataCache()
        val repository = PortfolioRepository(auth, UserApi(client, { "http://mock" }, auth::idToken, { auth.session.value.user?.id }), cache,
            MutableStateFlow("mock"), scope)
        val presenter = PortfolioPresenter(repository, WatchDataRepository(StockStepsApi(client) { "http://mock" }, cache), scope)
        try {
            repository.start()
            withTimeout(5_000) { presenter.state.first { it.selectedAccountId == "acc-1" } }
            // This mock has no valuation data, so the presenter's background valuation fails once at start; let it settle and clear it so
            // only the save's own outcome is asserted below (a rejection doesn't change the ledger revision, so no new valuation is fetched).
            withTimeoutOrNull(5_000) { presenter.state.first { it.error?.startsWith("Market valuation unavailable") == true } }
            presenter.clearError()
            val deposit = PortfolioTransaction("tx-1", "acc-1", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD, grossAmount = "100")
            presenter.saveTransaction(deposit)
            presenter.saveTransaction(deposit) // a second tap while the first save is in flight is ignored
            withTimeout(5_000) { presenter.state.first { it.busy } }
            presenter.saveTransaction(deposit)
            gate.complete(Unit)
            val failed = withTimeout(5_000) { presenter.state.first { !it.busy && it.error != null } }
            assertEquals(1, posts.value)
            assertEquals("Selling more shares than owned.", failed.error)
            delay(100)
            assertEquals("Selling more shares than owned.", presenter.state.value.error) // the message stays until the next attempt
            presenter.saveTransaction(deposit)
            withTimeout(5_000) { while (posts.value < 2 || presenter.state.value.busy || presenter.state.value.error != null) delay(10) }
            assertEquals(2, posts.value)
        } finally { scope.cancel(); client.close() }
    }

    /** Phase D3: a confirmed add / edit / delete sets one increasing action result; a rejection, an ignored tap or a sample never does. */
    @Test fun actionResultsOnlyFollowConfirmedChanges(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reject = MutableStateFlow(false)
        val gate = MutableStateFlow(true)
        val writes = MutableStateFlow(0)
        val account = PortfolioAccount("acc-1", "TFSA", reportingCurrency = PortfolioCurrency.CAD)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("portfolio/transactions")) {
                writes.update { it + 1 }
                while (!gate.value) delay(5)
                if (reject.value) return@MockEngine respond("""{"code":"REJECTED","message":"Selling more shares than owned."}""", HttpStatusCode.Conflict, json)
            }
            respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK, json)
        }) { configureStockStepsClient() }
        val auth = Auth()
        val cache = InMemoryUserDataCache()
        val repository = PortfolioRepository(auth, UserApi(client, { "http://mock" }, auth::idToken, { auth.session.value.user?.id }), cache,
            MutableStateFlow("mock"), scope)
        val presenter = PortfolioPresenter(repository, WatchDataRepository(StockStepsApi(client) { "http://mock" }, cache), scope)
        suspend fun settled() = withTimeout(5_000) { presenter.state.first { !it.busy } }
        // Waits on the request counter (not on error text: this mock's background valuation also reports an error).
        suspend fun reached(count: Int) = withTimeout(5_000) { while (writes.value < count) delay(5) }
        try {
            repository.start()
            withTimeout(5_000) { presenter.state.first { it.selectedAccountId == "acc-1" } }
            withTimeoutOrNull(5_000) { presenter.state.first { it.error?.startsWith("Market valuation unavailable") == true } }
            presenter.clearError()
            assertNull(presenter.state.value.actionResult)
            val deposit = PortfolioTransaction("tx-1", "acc-1", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD, grossAmount = "100")

            presenter.saveTransaction(deposit)
            val added = withTimeout(5_000) { presenter.state.first { it.actionResult != null } }.actionResult!!
            assertEquals(PortfolioActionResult(1, PortfolioActionKind.ADDED, "tx-1"), added)
            settled()

            reject.value = true
            presenter.saveTransaction(deposit, edit = true)
            reached(2)
            withTimeout(5_000) { presenter.state.first { !it.busy && it.error == "Selling more shares than owned." } }
            assertEquals(added, presenter.state.value.actionResult) // a rejection leaves the last confirmed result alone
            reject.value = false

            gate.value = false
            presenter.saveTransaction(deposit, edit = true)
            reached(3) // the request is held at the gate, so the presenter is busy
            presenter.deleteTransaction("tx-1") // ignored while busy: no request, no result
            gate.value = true
            val updated = withTimeout(5_000) { presenter.state.first { it.actionResult?.id == 2L } }.actionResult!!
            assertEquals(PortfolioActionKind.UPDATED, updated.kind)
            settled()

            presenter.deleteTransaction("tx-1")
            val deleted = withTimeout(5_000) { presenter.state.first { it.actionResult?.id == 3L } }.actionResult!!
            assertEquals(PortfolioActionResult(3, PortfolioActionKind.DELETED, "tx-1"), deleted)
            settled()
            assertEquals(4, writes.value) // add, rejected edit, edit, delete — the ignored delete never reached the server

            presenter.showScenario(PortfolioFixtureCatalog.ids.first { it == "multiple-buys" })
            withTimeout(5_000) { presenter.state.first { it.mockScenario != null } }
            presenter.deleteTransaction("sample-1") // samples are read-only: ignored
            delay(200)
            assertEquals(4, writes.value)
            presenter.showScenario(null)
            assertEquals(3L, withTimeout(5_000) { presenter.state.first { it.mockScenario == null } }.actionResult?.id)
        } finally { scope.cancel(); client.close() }
    }

    /** Phase D3.1: errors say which action failed; a response that lands after the signed-in user changed is never reported as a success. */
    @Test fun errorsNameTheirActionAndSessionChangesNeverReportSuccess(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reject = MutableStateFlow(true)
        val gate = MutableStateFlow(true)
        val writes = MutableStateFlow(0)
        val account = PortfolioAccount("acc-1", "TFSA", reportingCurrency = PortfolioCurrency.CAD)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.contains("portfolio/transactions")) {
                writes.update { it + 1 }
                while (!gate.value) delay(5)
                if (reject.value) return@MockEngine respond("""{"code":"REJECTED","message":"Selling more shares than owned."}""", HttpStatusCode.Conflict, json)
            }
            respond(Json.encodeToString(PortfolioLedger.serializer(), PortfolioLedger(listOf(account))), HttpStatusCode.OK, json)
        }) { configureStockStepsClient() }
        val auth = Auth()
        val cache = InMemoryUserDataCache()
        val repository = PortfolioRepository(auth, UserApi(client, { "http://mock" }, auth::idToken, { auth.session.value.user?.id }), cache,
            MutableStateFlow("mock"), scope)
        val presenter = PortfolioPresenter(repository, WatchDataRepository(StockStepsApi(client) { "http://mock" }, cache), scope)
        suspend fun reached(count: Int) = withTimeout(5_000) { while (writes.value < count) delay(5) }
        val deposit = PortfolioTransaction("tx-1", "acc-1", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD, grossAmount = "100")
        try {
            repository.start()
            withTimeout(5_000) { presenter.state.first { it.selectedAccountId == "acc-1" } }
            // The mock's background valuation failure is a refresh problem.
            withTimeoutOrNull(5_000) { presenter.state.first { it.error?.startsWith("Market valuation unavailable") == true } }
            assertEquals(PortfolioErrorSource.REFRESH, presenter.state.value.errorSource)
            presenter.clearError()
            assertNull(withTimeout(5_000) { presenter.state.first { it.error == null } }.errorSource)

            presenter.saveTransaction(deposit)
            reached(1)
            val saveFailed = withTimeout(5_000) { presenter.state.first { !it.busy && it.error == "Selling more shares than owned." } }
            assertEquals(PortfolioErrorSource.SAVE_TRANSACTION, saveFailed.errorSource)

            presenter.deleteTransaction("tx-1")
            reached(2)
            val deleteFailed = withTimeout(5_000) { presenter.state.first { !it.busy && it.errorSource == PortfolioErrorSource.DELETE_TRANSACTION } }
            assertEquals("Selling more shares than owned.", deleteFailed.error) // the server's text, unchanged
            assertNull(deleteFailed.actionResult)

            reject.value = false
            gate.value = false
            presenter.saveTransaction(deposit)
            reached(3)
            auth.mutable.value = AuthSession(User("bob", "bob@example.test"), initializing = false) // signed-in user changes mid-request
            withTimeout(5_000) { repository.state.first { it.uid == "bob" } }
            gate.value = true
            withTimeout(5_000) { presenter.state.first { !it.busy } }
            delay(200)
            assertNull(presenter.state.value.actionResult) // the response wasn't applied for bob, so no "Transaction added"
        } finally { scope.cancel(); client.close() }
    }
}
