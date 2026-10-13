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
}
