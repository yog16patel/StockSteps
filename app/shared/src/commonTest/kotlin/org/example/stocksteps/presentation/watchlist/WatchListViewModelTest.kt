package org.example.stocksteps.presentation.watchlist

import androidx.lifecycle.ViewModelStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import org.example.stocksteps.presentation.stocksearch.StockWatchlistViewModel
import kotlin.test.*

/** ViewModels against a fake backend (Ktor mock engine) in real time; no Firebase, no network. */
class WatchListViewModelTest {
    private class Auth(uid: String?) : AuthRepository {
        val mutable = MutableStateFlow(AuthSession(user = uid?.let { User(it, null) }, initializing = false))
        override val session: StateFlow<AuthSession> = mutable
        override val currentUser = mutable.map { it.user }
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signOut() = Unit
        override suspend fun idToken(forceRefresh: Boolean) = session.value.user?.let { "token-${it.id}" }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val limits = """"limits":{"maxWatchlists":20,"maxEntries":100,"maxNameLength":40,"maxNoteLength":1000,"maxAlerts":50}"""
    private val lists = """{"watchlists":[
        {"id":"default","name":"My Stocks","order":0,"createdAt":0,"updatedAt":0,"isDefault":true,"entries":[{"id":"e1","instrument":{"symbol":"AAPL","currency":"USD"},"order":0,"addedAt":0}]},
        {"id":"growth","name":"Growth","order":1,"createdAt":0,"updatedAt":0,"isDefault":false,"entries":[{"id":"e2","instrument":{"symbol":"NVDA"},"order":0,"addedAt":0}]}],$limits}"""
    private val session = """{"market":"US","status":"OPEN","timezone":"America/New_York","asOf":"2026-10-07T14:00:00Z","sessionDate":"2026-10-07","utcOffsetMinutes":-240,"source":"c"}"""
    val requests = mutableListOf<String>()

    private fun engine() = MockEngine { request ->
        val path = request.url.encodedPath
        requests += "${request.method.value} $path ${request.url.parameters["symbols"].orEmpty()}"
        when {
            path.endsWith("/watch-data") -> respond("""{"quotes":[{"symbol":"${request.url.parameters["symbols"]}","price":10.0,"changePercent":1.5,"currency":"USD","sessionDate":"2026-10-07"}],"session":$session,"generatedAt":"2026-10-07T14:00:00Z","notice":"n"}""", HttpStatusCode.OK, json)
            path.endsWith("/alerts") && request.method == HttpMethod.Post -> {
                val body = request.body.toByteArray().decodeToString()
                if ("whenAlreadyMet" !in body) respond("""{"code":"CONDITION_ALREADY_MET","message":"AAPL is already at $260.00, above $250.00."}""", HttpStatusCode.Conflict, json)
                else respond("""{"alerts":[],"history":[],"deliveryNote":"n",$limits}""", HttpStatusCode.OK, json)
            }
            path.endsWith("/alerts") -> respond("""{"alerts":[],"history":[],"deliveryNote":"n",$limits}""", HttpStatusCode.OK, json)
            path.contains("/entries/e1") && request.method == HttpMethod.Patch -> respond("""{"code":"NOTE_TOO_LONG","message":"Notes can be up to 1000 characters."}""", HttpStatusCode.BadRequest, json)
            path.endsWith("/watchlists/default/entries") -> respond(lists, HttpStatusCode.OK, json)
            else -> respond(lists, HttpStatusCode.OK, json)
        }
    }

    /** Real time on background threads (the blocked test thread can't serve Dispatchers.Main on iOS). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun scenario(uid: String?, block: suspend (Setup) -> Unit) = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = ViewModelStore()
        val setup = Setup(scope, uid, store)
        try { block(setup) } finally {
            store.clear()
            scope.coroutineContext[Job]?.cancelAndJoin()
            delay(100) // let cancelled ViewModel coroutines finish before Main is reset
            setup.client.close()
            Dispatchers.resetMain()
        }
    }

    private inner class Setup(val scope: CoroutineScope, uid: String?, val store: ViewModelStore) {
        val auth = Auth(uid)
        val environment = MutableStateFlow("mock")
        val client = HttpClient(engine()) { configureStockStepsClient() }
        val cache = InMemoryUserDataCache()
        val api = UserApi(client, { "https://stocksteps.test" }, auth::idToken)
        val lists = UserWatchlistsRepository(auth, api, cache, environment, scope).also { it.start() }
        val alerts = AlertsRepository(auth, api, cache, environment, scope).also { it.start() }
        val watchData = WatchDataRepository(StockStepsApi(client, "https://stocksteps.test"), cache)
        val guestItems = MutableStateFlow(listOf(WatchlistItem("TSLA", 0, 0)))
        val guest = object : WatchlistRepository {
            override val snapshot = combine(auth.session, guestItems) { s, items -> WatchlistSnapshot(s.user?.id, if (s.user == null) items else emptyList()) }
            override val items = snapshot.map { it.items }
            override val syncState = MutableStateFlow(WatchlistSyncState())
            override suspend fun add(symbol: String) { guestItems.update { it + WatchlistItem(symbol, 0, 0) } }
            override suspend fun remove(symbol: String) { guestItems.update { list -> list.filter { it.symbol != symbol } } }
            override fun retrySync() = Unit
        }
    }

    private suspend fun eventually(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(20) }

    @Test fun signedInListsSelectionAndQuotesForTheVisibleListOnly() = scenario("alice") { s ->
        val model = WatchListViewModel(s.auth, s.lists, s.alerts, s.watchData, s.guest, s.environment).also { s.store.put("w", it) }
        eventually { model.state.value.model?.rows?.firstOrNull()?.price != null }
        val ui = model.state.value.model!!
        assertEquals(listOf("My Stocks", "Growth"), ui.tabs.map { it.name })
        assertEquals("$10.00", ui.rows.single().price); assertEquals("USD", ui.rows.single().currency)
        model.select("growth")
        eventually { model.state.value.model?.rows?.singleOrNull()?.symbol == "NVDA" && model.state.value.data?.data?.quotes?.single()?.symbol == "NVDA" }
        assertTrue(requests.none { it.contains("AAPL,NVDA") }, "only the visible list's symbols are requested")
    }

    @Test fun failedMutationsShowAMessageAndKeepTheList() = scenario("alice") { s ->
        val model = WatchListViewModel(s.auth, s.lists, s.alerts, s.watchData, s.guest, s.environment).also { s.store.put("w", it) }
        eventually { model.state.value.model != null }
        model.saveNote("e1", "x")
        eventually { model.state.value.message != null }
        assertEquals("Notes can be up to 1000 characters.", model.state.value.message)
        assertEquals(1, model.state.value.model!!.rows.size)
        model.dismissMessage()
        eventually { model.state.value.message == null }
    }

    @Test fun guestsSeeTheDeviceListWithQuotes() = scenario(null) { s ->
        val model = WatchListViewModel(s.auth, s.lists, s.alerts, s.watchData, s.guest, s.environment).also { s.store.put("w", it) }
        eventually { model.state.value.guestModel.rows.singleOrNull()?.price != null }
        assertEquals("TSLA", model.state.value.guestModel.rows.single().symbol)
        assertNull(model.state.value.model, "no server lists while signed out")
        model.removeGuest("TSLA")
        eventually { model.state.value.guestItems.isEmpty() }
    }

    @Test fun alertCreationAsksWhenTheConditionIsAlreadyMet() = scenario("alice") { s ->
        val model = AlertsViewModel(s.auth, s.alerts, null).also { s.store.put("a", it) }
        val request = CreateAlertRequest(InstrumentRef("AAPL", currency = "USD"), AlertType.PRICE_ABOVE, 250.0, "USD")
        val first = model.submit(request)
        assertIs<AlertSubmitResult.AlreadyMet>(first)
        assertTrue(first.message.contains("already at"))
        assertEquals(AlertSubmitResult.Created, model.submit(request.copy(whenAlreadyMet = AlreadyMetChoice.WAIT_FOR_CROSS)))
    }

    @Test fun savingWithSeveralListsAsksForTheDestination() = scenario("alice") { s ->
        val facade = AccountWatchlistRepository(s.auth, s.guest, s.lists, object : org.example.stocksteps.data.watchlist.WatchlistLocalStore {
            override fun observe(owner: String) = flowOf(emptyList<WatchlistItem>())
            override fun observePending(owner: String) = flowOf(emptyList<org.example.stocksteps.data.watchlist.PendingWatchlistOperation>())
            override suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean) = Unit
            override suspend fun remove(owner: String, symbol: String, queue: Boolean) = Unit
            override suspend fun mergeGuest(owner: String) = Unit
            override suspend fun applySnapshot(owner: String, items: List<WatchlistItem>) = Unit
            override suspend fun acknowledge(owner: String, operation: org.example.stocksteps.data.watchlist.PendingWatchlistOperation) = Unit
        }, s.scope)
        val model = StockWatchlistViewModel(facade, s.auth, AddToWatchlist(facade), RemoveFromWatchlist(facade), s.lists).also { s.store.put("s", it) }
        val job = s.scope.launch { model.state.collect {} }
        try {
            eventually { model.state.value.lists.size == 2 && model.state.value.enabled }
            assertTrue("AAPL" in model.state.value.symbols)
            model.toggle(StockSearchResult("MSFT", "Microsoft"))
            eventually { model.state.value.choosing?.symbol == "MSFT" }
            model.choose("growth")
            eventually { requests.any { it.startsWith("POST /api/v1/me/watchlists/growth/entries") } }
            assertNull(model.state.value.choosing)
        } finally { job.cancel() }
    }
}
