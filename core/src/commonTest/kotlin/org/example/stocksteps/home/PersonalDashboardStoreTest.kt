package org.example.stocksteps.home

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalDashboardStoreTest {
    private class Harness(val test: TestScope, initializing: Boolean = false) {
        val session = MutableStateFlow(AuthSession(initializing = initializing))
        val environment = MutableStateFlow("mock")
        val snapshots = MutableStateFlow(WatchlistSnapshot(null, emptyList()))
        val cache = InMemoryUserDataCache()
        var clock = 2_000_000_000_000L
        var quoteCalls = 0
        var newsCalls = 0
        var personaCalls = 0
        var blockQuotes: CompletableDeferred<Unit>? = null
        var failNews = false
        val auth = object : AuthRepository {
            override val session = this@Harness.session
            override val currentUser = session.map { it.user }
            override suspend fun signUp(email: String, password: String) = Unit
            override suspend fun signIn(email: String, password: String) = Unit
            override suspend fun signOut() { session.value = AuthSession(initializing = false) }
            override suspend fun idToken(forceRefresh: Boolean) = "mock-user:${session.value.user?.id}"
        }
        private val guest = object : WatchlistRepository {
            override val snapshot = snapshots
            override val items = snapshots.map { it.items }
            override val syncState = MutableStateFlow(WatchlistSyncState())
            override suspend fun add(symbol: String) = Unit
            override suspend fun remove(symbol: String) = Unit
            override fun retrySync() = Unit
        }
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(test.testScheduler)
            addHandler { request ->
            val text = when {
                request.url.encodedPath.endsWith("/watch-data") -> {
                    quoteCalls++
                    blockQuotes?.await()
                    val symbols = request.url.parameters["symbols"]!!.split(',')
                    Json.encodeToString(WatchDataResponse.serializer(), WatchDataResponse(
                        quotes = symbols.map { WatchQuote(it, price = 100.0, changePercent = 4.0, asOf = "2033-05-18T12:00:00Z") },
                        session = MarketSession("US", MarketSessionStatus.OPEN, "America/New_York", "2033-05-18T12:00:00Z", sessionDate = "2033-05-18", source = "test"),
                        generatedAt = "2033-05-18T12:00:00Z", notice = "Test quotes"))
                }
                request.url.encodedPath.endsWith("/watchlists") -> {
                    val symbol = if (request.headers[HttpHeaders.Authorization]?.contains("two") == true) "MSFT" else "AAPL"
                    Json.encodeToString(WatchlistsResponse.serializer(), WatchlistsResponse(
                        listOf(Watchlist("default", "My Stocks", 0, 0, 0, entries = listOf(WatchlistEntry("entry", InstrumentRef(symbol), 0, 0)))), limits))
                }
                request.url.encodedPath.endsWith("/alerts") -> Json.encodeToString(AlertsResponse.serializer(), AlertsResponse(deliveryNote = "test", limits = limits))
                else -> error("Unexpected endpoint ${request.url}")
            }
            respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }})) { install(ContentNegotiation) { json() } }
        private val userApi = UserApi(client, { "http://${environment.value}" }, token = { auth.idToken(it) })
        val lists = UserWatchlistsRepository(auth, userApi, cache, environment, test.backgroundScope).also { it.start() }
        val alerts = AlertsRepository(auth, userApi, cache, environment, test.backgroundScope).also { it.start() }
        val store = PersonalDashboardStore(auth, lists, alerts, guest, environment,
            WatchDataRepository(StockStepsApi(client) { "http://${environment.value}" }, cache) { clock }, cache,
            companyNews = { symbol ->
                newsCalls++
                if (failNews) error("provider credential detail")
                listOf(NewsArticle("Company story", "https://example.com/$symbol", symbol = symbol))
            }, scope = test.backgroundScope, now = { clock }, mockPersona = { id ->
                personaCalls++
                HomePersonaFixture(id, date = "2033-05-18", now = clock)
            })
        fun guestStocks(vararg symbols: String) { snapshots.value = WatchlistSnapshot(null, symbols.map { WatchlistItem(it, 0, 0) }) }
        companion object { val limits = WatchlistLimits(20, 100, 40, 1000, 50) }
    }

    @Test fun restorationDoesNotShowOnboardingOrMakePersonalRequests() = runTest {
        val h = Harness(this, initializing = true)
        runCurrent()
        assertTrue(h.store.state.value.initializing)
        assertEquals(0, h.quoteCalls)
        assertEquals(0, h.newsCalls)
        h.session.value = AuthSession(initializing = false)
        runCurrent()
        assertFalse(h.store.state.value.initializing)
    }
    @Test fun quoteAndNewsFailuresAreIndependentAndSafe() = runTest {
        val h = Harness(this)
        h.failNews = true
        h.guestStocks("AAPL")
        runCurrent()
        assertNotNull(h.store.state.value.highlights.single().row.price)
        assertTrue(h.store.state.value.brief.isNotEmpty())
        assertNotNull(h.store.state.value.newsError)
        assertFalse(h.store.state.value.newsError!!.contains("credential"))
        h.failNews = false
        h.store.retryNews()
        runCurrent()
        assertEquals(1, h.store.state.value.stories.size)
        assertNull(h.store.state.value.newsError)
    }
    @Test fun watchDataBatchesAndStableMetadataDoesNotRefetch() = runTest {
        val h = Harness(this)
        h.guestStocks("AAPL", "MSFT", "NVDA")
        runCurrent()
        assertEquals(1, h.quoteCalls)
        assertEquals(3, h.newsCalls)
        h.snapshots.value = h.snapshots.value.copy(items = h.snapshots.value.items.map { it.copy(name = "Updated name") })
        runCurrent()
        h.store.onVisible()
        runCurrent()
        assertEquals(1, h.quoteCalls)
        assertEquals(3, h.newsCalls)
        assertEquals("Updated name", h.store.state.value.highlights.first().row.name)
    }
    @Test fun returnToHomeRefreshesOnlyStaleSectionsAndExplicitRetryBypassesTtl() = runTest {
        val h = Harness(this)
        h.guestStocks("AAPL"); runCurrent()
        h.clock += PersonalDashboardRules.QUOTE_TTL_MS + 1
        h.store.onVisible(); runCurrent()
        assertEquals(2, h.quoteCalls)
        assertEquals(1, h.newsCalls)
        h.store.retryNews(); runCurrent()
        assertEquals(2, h.newsCalls)
    }
    @Test fun accountChangeCancelsAndDoesNotPublishOldResponses() = runTest {
        val h = Harness(this)
        h.blockQuotes = CompletableDeferred()
        h.session.value = AuthSession(User("one", null), false)
        runCurrent()
        h.session.value = AuthSession(User("two", null), false)
        runCurrent()
        assertTrue(h.store.state.value.highlights.none { it.instrument.symbol == "AAPL" })
        h.blockQuotes!!.complete(Unit)
        runCurrent()
        assertEquals("MSFT", h.store.state.value.highlights.single().instrument.symbol)
    }
    @Test fun recentHistoryIsDeduplicatedBoundedAndEnvironmentIsolated() = runTest {
        val h = Harness(this); runCurrent()
        listOf("AAPL", "MSFT", "NVDA", "META", "GOOG", "AAPL").forEach { h.store.recordViewed(InstrumentRef(it)); runCurrent() }
        assertEquals(listOf("AAPL", "GOOG", "META", "NVDA"), h.store.state.value.recent.map { it.instrument.symbol })
        h.environment.value = "real"; runCurrent()
        assertTrue(h.store.state.value.recent.isEmpty())
        h.environment.value = "mock"; runCurrent()
        assertEquals("AAPL", h.store.state.value.recent.first().instrument.symbol)
        h.store.clearRecent(); runCurrent()
        assertTrue(h.store.state.value.recent.isEmpty())
    }
    @Test fun signOutClearsPersistedDashboardDataAndUi() = runTest {
        val h = Harness(this)
        h.session.value = AuthSession(User("one", null), false); runCurrent()
        h.store.recordViewed(InstrumentRef("AAPL")); runCurrent()
        assertNotNull(h.cache.read("mock|user:one", "home.recent"))
        SignOut(h.auth, cache = h.cache)(); runCurrent()
        assertTrue(h.store.state.value.recent.isEmpty())
        assertNull(h.cache.read("mock|user:one", "home.recent"))
        assertEquals(0, h.store.state.value.watchlistCount)
    }
    @Test fun mockPersonaCannotFetchInRealAndDoesNotAlterSavedList() = runTest {
        val h = Harness(this); h.guestStocks("AAPL"); runCurrent()
        h.store.recordViewed(InstrumentRef("AAPL")); runCurrent()
        h.store.showMockPersona("new-user"); runCurrent()
        assertEquals("new-user", h.store.state.value.mockPersona)
        assertEquals(0, h.store.state.value.watchlistCount)
        assertEquals("AAPL", h.snapshots.value.items.single().symbol)
        val quotesBeforeRetry = h.quoteCalls
        val newsBeforeRetry = h.newsCalls
        h.store.retryQuotes(); runCurrent()
        h.store.retryNews(); runCurrent()
        assertEquals(quotesBeforeRetry, h.quoteCalls, "persona retries never substitute saved-company quotes")
        assertEquals(newsBeforeRetry, h.newsCalls)
        h.store.clearRecent(); runCurrent()
        h.store.useSavedCompanies(); runCurrent()
        assertEquals(1, h.store.state.value.watchlistCount)
        assertEquals("AAPL", h.store.state.value.recent.single().instrument.symbol)
        h.environment.value = "real"; runCurrent()
        h.store.showMockPersona("new-user"); runCurrent()
        assertEquals(3, h.personaCalls)
        assertNull(h.store.state.value.mockPersona)
    }
}
