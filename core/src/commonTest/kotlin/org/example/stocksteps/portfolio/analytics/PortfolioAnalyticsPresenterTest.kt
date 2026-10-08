package org.example.stocksteps.portfolio.analytics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.configureStockStepsClient
import org.example.stocksteps.portfolio.*
import kotlin.test.*

class PortfolioAnalyticsPresenterTest {
    private class Auth : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(User("alice", "alice@example.test"), initializing = false))
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
        override suspend fun idToken(forceRefresh: Boolean) = mutable.value.user?.id
    }

    /** A fake `/api/v1/me` server: the plan per user is server state; analytics come from fixtures. */
    private class Server {
        val plans = mutableMapOf("alice" to SubscriptionTier.PLUS)
        val analyticsRequests = mutableListOf<String>()
        val debugRequests = mutableListOf<String>()
        var failAnalytics = false
        private val json = Json { encodeDefaults = true }
        val ledger = PortfolioLedger(listOf(PortfolioAccount("tfsa", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD)),
            listOf(PortfolioTransaction("d1", "tfsa", TransactionType.CASH_DEPOSIT, "2026-01-05", PortfolioCurrency.CAD, grossAmount = "100")), 4)

        fun engine() = MockEngine { request ->
            val uid = request.headers[HttpHeaders.Authorization].orEmpty().removePrefix("Bearer ")
            val path = request.url.encodedPath.removePrefix("/api/v1/me/")
            fun ok(body: String) = respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            when {
                path == "portfolio" -> ok(json.encodeToString(PortfolioLedger.serializer(), ledger))
                path == "entitlements" -> ok(json.encodeToString(Entitlements.serializer(), entitlements(uid)))
                path == "entitlements/debug" -> {
                    val body = (request.body as io.ktor.http.content.TextContent).text
                    debugRequests += body
                    plans[uid] = json.decodeFromString(DebugEntitlementRequest.serializer(), body).tier
                    ok(json.encodeToString(Entitlements.serializer(), entitlements(uid)))
                }
                path.endsWith("/analytics") -> {
                    analyticsRequests += "${request.url.host}:$uid:${request.url.parameters["period"]}:${request.url.parameters["benchmark"]}"
                    if (failAnalytics) respond("""{"code":"X","message":"boom"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
                    else {
                        val tier = plans[uid] ?: SubscriptionTier.FREE
                        val fixture = AnalyticsFixtureCatalog.get(if (tier == SubscriptionTier.PLUS) "plus" else "free")
                        val result = fixture.analytics(AnalyticsPeriod.parse(request.url.parameters["period"])!!, request.url.parameters["benchmark"]?.let(BenchmarkCatalog::parse))
                        ok(json.encodeToString(PortfolioAnalytics.serializer(), result.copy(accountId = "tfsa", revision = 4)))
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        fun entitlements(uid: String) = if (plans[uid] == SubscriptionTier.PLUS) Entitlements(SubscriptionTier.PLUS, EntitlementStatus.ACTIVE, source = "debug")
            else Entitlements(SubscriptionTier.FREE, EntitlementStatus.NONE)
    }

    private class Harness(val auth: Auth, val environment: MutableStateFlow<String>, val server: Server, val presenter: PortfolioAnalyticsPresenter, val cache: UserDataCache)

    private fun harness(block: suspend Harness.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = Auth()
        val environment = MutableStateFlow("mock")
        val server = Server()
        val client = HttpClient(server.engine()) { configureStockStepsClient() }
        val cache = InMemoryUserDataCache()
        val api = UserApi(client, { "http://${environment.value}" }, auth::idToken, { auth.session.value.user?.id })
        val repository = PortfolioRepository(auth, api, cache, environment, scope)
        val entitlements = EntitlementsRepository(auth, api, environment, scope)
        try {
            repository.start(); entitlements.start()
            Harness(auth, environment, server, PortfolioAnalyticsPresenter(repository, entitlements, scope), cache).block()
        } finally { scope.cancel(); client.close() }
    }

    private suspend fun Harness.await(predicate: (InsightsUiState) -> Boolean) = withTimeout(10_000) { presenter.state.first(predicate) }

    @Test fun loadsSelectedAccountAndReloadsForPeriodAndBenchmark() = harness {
        val loaded = await { it.view?.performance?.status == Availability.AVAILABLE }
        assertEquals("tfsa", loaded.selectedAccountId)
        assertEquals(BenchmarkId.TSX, loaded.benchmark) // CAD account default
        assertTrue(loaded.view!!.periods.any { it.enabled })
        presenter.selectPeriod(AnalyticsPeriod.THREE_MONTHS)
        await { it.view?.selectedPeriod == AnalyticsPeriod.THREE_MONTHS && !it.loading }
        presenter.selectBenchmark(BenchmarkId.SP500)
        await { it.view?.benchmark?.rows?.any { row -> row.label == "S&P 500" } == true }
        assertTrue(server.analyticsRequests.any { it.endsWith(":3M:SP500") })
    }

    @Test fun benchmarkChoiceIsRememberedPerUserAndEnvironment() = harness {
        await { it.view != null }
        presenter.selectBenchmark(BenchmarkId.NASDAQ)
        withTimeout(5_000) { while (cache.read(userCacheOwner("mock", "alice"), "portfolio.benchmark") == null) delay(20) }
        auth.mutable.value = AuthSession(User("bob", "bob@example.test"), initializing = false)
        await { it.benchmark == BenchmarkId.TSX && it.view != null } // bob gets his own default, not alice's choice
        withTimeout(5_000) { while (server.analyticsRequests.none { it.startsWith("mock:bob:") }) delay(20) }
        assertTrue(server.analyticsRequests.filter { it.startsWith("mock:bob:") }.none { it.endsWith("NASDAQ") }, server.analyticsRequests.toString())
        auth.mutable.value = AuthSession(User("alice", "alice@example.test"), initializing = false)
        await { it.benchmark == BenchmarkId.NASDAQ && it.view != null }
    }

    @Test fun freePlanShowsLockedSectionsFromTheServer() = harness {
        server.plans["alice"] = SubscriptionTier.FREE
        val state = await { it.view != null }
        assertEquals(Availability.LOCKED, state.view!!.performance.status)
        assertEquals(Availability.LOCKED, state.view!!.benchmark.status)
        assertTrue(state.view!!.allocation.first { it.id == "holding" }.rows.isNotEmpty()) // own holdings stay free
        assertFalse(state.plus)
    }

    @Test fun planSimulationIsMockOnlyAndUnlocksAfterServerConfirms() = harness {
        server.plans["alice"] = SubscriptionTier.FREE
        await { it.view?.performance?.status == Availability.LOCKED }
        presenter.simulatePlan(SubscriptionTier.PLUS)
        await { it.view?.performance?.status == Availability.AVAILABLE }
        assertEquals(1, server.debugRequests.size)
        environment.value = "real"
        await { !it.mockAvailable && it.view != null }
        presenter.simulatePlan(SubscriptionTier.FREE)
        delay(300)
        assertEquals(1, server.debugRequests.size) // never sent against REAL
        presenter.showScenario("concentrated")
        delay(200)
        assertNull(presenter.state.value.scenario)
    }

    @Test fun mockScenariosRenderWithoutCallingTheServer() = harness {
        await { it.view != null }
        val before = server.analyticsRequests.size
        presenter.showScenario("concentrated")
        val state = await { it.scenario == "concentrated" && it.view?.concentration?.rows?.any { row -> row.value.startsWith("SHOP.TO") } == true }
        assertTrue(state.accountName.contains("Concentrated"))
        assertEquals(before, server.analyticsRequests.size)
        presenter.showScenario(null)
        await { it.scenario == null && it.view?.accountId == "tfsa" }
    }

    @Test fun failureIsReportedWithoutTouchingTheLedgerAndSignOutClearsEverything() = harness {
        await { it.view != null }
        server.failAnalytics = true
        presenter.refresh() // same key: the last saved result for this revision and plan is shown, labelled
        await { it.view?.notes?.firstOrNull()?.contains("last saved") == true }
        presenter.selectPeriod(AnalyticsPeriod.THREE_YEARS) // nothing saved for this key
        val failed = await { it.error != null }
        assertTrue(failed.error!!.contains("holdings and transactions are unaffected"))
        auth.signOut()
        val cleared = await { !it.signedIn }
        assertNull(cleared.view)
        assertTrue(cleared.accounts.isEmpty())
    }
}
