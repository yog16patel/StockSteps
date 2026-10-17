package org.example.stocksteps.userdata

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.practice.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import org.junit.Assume.assumeTrue
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * Phase 7D: My Portfolio and Practice through the real Ktor routes and the real [FirestoreUserDataStore] on a **local Firestore
 * emulator** (demo project; never production credentials), with a controlled authenticator and deterministic closes and FX. Skipped
 * unless `FIRESTORE_EMULATOR_HOST` is set. Each test uses fresh random uids and deletes its documents afterwards.
 */
class FirestorePortfolioPracticeIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun database(): Firestore {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue("Run with the Firestore emulator", !host.isNullOrBlank())
        return FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("demo-stocksteps-7d").setEmulatorHost(host).build().service.also {
            it.collection("warmup").document("w").get().get(15, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    /** Tokens issued by this test only: "token-<uid>" → uid; anything else is rejected (as an invalid or expired ID token would be). */
    private val auth = UserAuthenticator { token -> token.removePrefix("token-").takeIf { token.startsWith("token-") && it.isNotBlank() } }

    private val marketClock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))   // Fri 18:00 ET: Friday's close expected
    private val clock = MutableClock(Instant.parse("2026-10-17T15:00:00Z"))
    private val market = FakeMarket().apply {
        history = mapOf("AAPL" to listOf(PricePoint("2026-10-15", 100.0), PricePoint("2026-10-16", 110.0)),
            "MSFT" to listOf(PricePoint("2026-10-15", 200.0), PricePoint("2026-10-16", 202.0)))
    }

    private fun practice(store: UserDataStore) = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true), market, FakeFx(), clock,
        marketClock, null, sampleData = false, eod = EodSessions(marketClock))

    private fun ApplicationTestBuilder.app(store: UserDataStore, practice: PracticeService = practice(store)): HttpClient {
        val portfolios = PortfolioService(store, clock::millis)
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val charts = PriceChartService(object : org.example.stocksteps.repository.PriceHistoryProvider {
            override suspend fun getIntradaySparkline(symbol: String) = org.example.stocksteps.model.Sparkline(symbol, emptyList())
            override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
            override suspend fun getDailyCloses(symbol: String) = market.history[symbol].orEmpty()
        }, eod = EodSessions(marketClock))
        val portfolioMarket = PortfolioMarketService(portfolios, fixture, WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture)), FakeFx(),
            marketClock, dailyCloses = charts::getDailyCloses, eod = EodSessions(marketClock))
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(Json { encodeDefaults = true }) }
            configureApiErrors()
            routing { practiceRoutes(auth, practice, mock = false); portfolioRoutes(auth, portfolios, portfolioMarket) }
        }
        return createClient { }
    }

    private suspend fun HttpClient.call(method: HttpMethod, path: String, uid: String?, body: Any? = null): HttpResponse = request(path) {
        this.method = method
        uid?.let { header(HttpHeaders.Authorization, "Bearer token-$it") }
        body?.let { contentType(ContentType.Application.Json); setBody(it.toString()) }
    }
    private fun order(symbol: String, side: String, qty: String, key: String, reviewed: String? = null) =
        """{"symbol":"$symbol","side":"$side","quantity":"$qty","idempotencyKey":"$key"${reviewed?.let { ",\"reviewedPrice\":\"$it\"" } ?: ""}}"""
    private fun dec(s: String?) = BigDecimal(s!!)
    private fun cleanup(db: Firestore, vararg uids: String) = uids.forEach { uid ->
        listOf("practice/account", "portfolio/ledger", "meta/entitlements").forEach { runCatching { db.collection("users").document(uid).collection(it.substringBefore('/')).document(it.substringAfter('/')).delete().get() } }
    }

    @Test fun practiceLedgerPersistsAcrossRecreationWithCorrectCashAndRealizedGain() = testApplication {
        val db = database(); val uid = "p-" + UUID.randomUUID()
        try {
            val client = app(FirestoreUserDataStore(db))
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Get, "/api/v1/me/practice", uid).status)
            val preview = json.decodeFromString(PracticeOrderPreview.serializer(), client.call(HttpMethod.Post, "/api/v1/me/practice/orders/preview", uid, """{"symbol":"AAPL","side":"BUY","quantity":"2"}""").bodyAsText())
            assertEquals("110", preview.price); assertEquals(PracticeService.EOD_BASIS, preview.priceBasis); assertEquals("2026-10-16T20:00:00Z", preview.quoteAsOf)
            val buy = client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", uid, order("AAPL", "BUY", "2", "buy-1-abcdef", "110"))
            assertEquals(HttpStatusCode.OK, buy.status)
            assertEquals(0, dec(json.decodeFromString(PracticeOrderResult.serializer(), buy.bodyAsText()).cashAfter).compareTo(BigDecimal("9703")), "10,000 − 2 × 110 × 1.35")
            // A new store and service on the same database (a restarted server) read the same ledger.
            val reloaded = client
            val o1 = practice(FirestoreUserDataStore(db)).overview(uid)
            assertEquals(0, dec(o1.cash).compareTo(BigDecimal("9703"))); assertEquals("2", o1.holdings.single().quantity)
            market.history = market.history + ("AAPL" to listOf(PricePoint("2026-10-15", 110.0), PricePoint("2026-10-16", 120.0)))
            assertEquals(HttpStatusCode.OK, reloaded.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", uid, order("AAPL", "SELL", "1", "sell-1-abcdef", "120")).status)
            val o2 = json.decodeFromString(PracticeOverview.serializer(), reloaded.call(HttpMethod.Get, "/api/v1/me/practice", uid).bodyAsText())
            assertEquals(0, dec(o2.cash).compareTo(BigDecimal("9865")), "9,703 + 1 × 120 × 1.35 = 9,865")
            assertEquals(0, dec(o2.realizedGain).compareTo(BigDecimal("13.5")), "(120 − 110) × 1.35 per share at the same FX")
            assertEquals(HttpStatusCode.OK, reloaded.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", uid, order("AAPL", "SELL", "1", "sell-2-abcdef", "120")).status)
            val final = practice(FirestoreUserDataStore(db)).overview(uid)
            assertEquals(final.cash, json.decodeFromString(PracticeOverview.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice", uid).bodyAsText()).cash, "HTTP and a recreated service agree")
            assertTrue(final.holdings.isEmpty())
            assertEquals(0, dec(final.cash).compareTo(BigDecimal("10027")), "10,000 + 2 × (120 − 110) × 1.35")
            val history = reloaded.call(HttpMethod.Get, "/api/v1/me/practice/transactions", uid).bodyAsText()
            assertEquals(3, Regex("\"idempotencyKey\"").findAll(history).count(), "buy, sell, sell")
        } finally { cleanup(db, uid); db.close() }
    }

    @Test fun concurrentOrdersOnFirestoreNeverOverspendOversellOrFillTwice() = runBlocking<Unit> {
        val db = database(); val uid = "c-" + UUID.randomUUID()
        try {
            val store = FirestoreUserDataStore(db); val svc = practice(store)
            svc.overview(uid)
            // Two buys of 40 AAPL (40 × 110 × 1.35 = 5,940 CAD each) compete for 10,000 CAD.
            val two = (1..2).map { i -> async(Dispatchers.IO) { runCatching { svc.execute(uid, PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "40", idempotencyKey = "two-$i-abcdef", reviewedPrice = "110")) } } }.awaitAll()
            assertEquals(1, two.count { it.isSuccess }, "exactly one of two competing buys fills")
            assertEquals("INSUFFICIENT_CASH", (two.single { it.isFailure }.exceptionOrNull() as UserDataException).code)
            // Two sells of 30 compete for 40 shares.
            val sells = (1..2).map { i -> async(Dispatchers.IO) { runCatching { svc.execute(uid, PracticeOrderRequest("AAPL", OrderSide.SELL, quantity = "30", idempotencyKey = "sell-$i-abcdef", reviewedPrice = "110")) } } }.awaitAll()
            assertEquals(1, sells.count { it.isSuccess }); assertEquals("INSUFFICIENT_SHARES", (sells.single { it.isFailure }.exceptionOrNull() as UserDataException).code)
            // Ten identical requests (one key) fill once; ten distinct keys never overspend.
            val same = (1..10).map { async(Dispatchers.IO) { runCatching { svc.execute(uid, PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "1", idempotencyKey = "same-key-abcdef", reviewedPrice = "202")) } } }.awaitAll()
            assertTrue(same.all { it.isSuccess }); assertEquals(1, same.map { it.getOrThrow().transaction.id }.distinct().size)
            val order = { i: Int -> PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "5", idempotencyKey = "many-$i-abcdef", reviewedPrice = "202") }
            val distinct = (1..10).map { i -> async(Dispatchers.IO) { i to runCatching { svc.execute(uid, order(i)) } } }.awaitAll()
            // Under this contention Firestore may abort or time out a transaction: 503 USER_DATA_UNAVAILABLE, an unknown outcome. Those are
            // retried with the same key (as the app now does); everything else must be a fill or INSUFFICIENT_CASH.
            val codes = distinct.mapNotNull { (_, r) -> (r.exceptionOrNull() as? UserDataException)?.code }
            assertTrue(codes.all { it in setOf("INSUFFICIENT_CASH", "USER_DATA_UNAVAILABLE") }, "only these failures: $codes")
            distinct.filter { (_, r) -> (r.exceptionOrNull() as? UserDataException)?.code == "USER_DATA_UNAVAILABLE" }.forEach { (i, _) ->
                runCatching { svc.execute(uid, order(i)) }.exceptionOrNull()?.let { assertEquals("INSUFFICIENT_CASH", (it as UserDataException).code) }
            }
            val o = practice(FirestoreUserDataStore(db)).overview(uid)   // recreated service and store
            assertTrue(dec(o.cash) >= BigDecimal.ZERO, "no negative cash")
            o.holdings.forEach { assertTrue(dec(it.quantity) > BigDecimal.ZERO) }
            val txs = practice(FirestoreUserDataStore(db)).transactions(uid, null).items
            assertEquals(txs.size, txs.mapNotNull { it.idempotencyKey }.distinct().size, "no request id filled twice")
            val filled = txs.count { it.idempotencyKey?.startsWith("many-") == true }
            assertEquals(BigDecimal(1 + 5 * filled), dec(o.holdings.single { it.instrument.symbol == "MSFT" }.quantity), "every fill is in the ledger, none lost")
            assertEquals(1 + 1 + 1 + filled, txs.size, "buy + sell + one same-key fill + distinct fills")
            // Retry after a commit whose response was lost: the same key replays.
            val replay = svc.execute(uid, PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "1", idempotencyKey = "same-key-abcdef", reviewedPrice = "202"))
            assertTrue(replay.replayed)
        } finally { cleanup(db, uid); db.close() }
    }

    @Test fun aFailedWriteIsReportedAndTheRetryFillsOnce() = testApplication {
        val db = database(); val uid = "f-" + UUID.randomUUID()
        try {
            val real = FirestoreUserDataStore(db)
            var failNext = false
            val flaky = object : UserDataStore by real {
                override suspend fun <T> updatePractice(uid: String, block: (PracticeAccountData) -> Pair<PracticeAccountData, T>): T {
                    // Fails a write (not a read) once, as a transient Firestore outage would.
                    if (failNext) { val probe = real.updatePractice(uid) { it to it }; val (next, _) = block(probe)
                        if (next != probe) { failNext = false; throw UserDataException(503, "USER_DATA_UNAVAILABLE", "Your data couldn't be reached. Try again.") } }
                    return real.updatePractice(uid, block)
                }
            }
            val client = app(flaky, practice(flaky))
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Get, "/api/v1/me/practice", uid).status)   // initialized normally
            failNext = true   // the order's write fails once
            val first = client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", uid, order("AAPL", "BUY", "1", "flaky-1-abcdef", "110"))
            val firstBody = first.bodyAsText()
            assertEquals(HttpStatusCode.ServiceUnavailable, first.status)
            assertFalse("transaction" in firstBody, "a failed write never looks like a fill")
            assertTrue(practice(FirestoreUserDataStore(db)).overview(uid).holdings.isEmpty(), "nothing was written")
            val retry = client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", uid, order("AAPL", "BUY", "1", "flaky-1-abcdef", "110"))
            assertEquals(HttpStatusCode.OK, retry.status)
            val o = practice(FirestoreUserDataStore(db)).overview(uid)
            assertEquals("1", o.holdings.single().quantity, "one fill after a failed attempt and its retry")
            assertFalse(firstBody.contains("Exception") || firstBody.contains("firestore", ignoreCase = true))
        } finally { cleanup(db, uid); db.close() }
    }

    @Test fun myPortfolioPersistsAndIsIsolatedPerUser() = testApplication {
        val db = database(); val a = "a-" + UUID.randomUUID(); val b = "b-" + UUID.randomUUID()
        try {
            val client = app(FirestoreUserDataStore(db))
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Put, "/api/v1/me/portfolio/accounts", a, """{"id":"acc","name":"CAD account"}""").status)
            val tx = """{"id":"t1","accountId":"acc","type":"OPENING_POSITION","tradeDate":"2026-10-01","currency":"USD","instrument":{"symbol":"AAPL","currency":"USD"},"quantity":"2","unitPrice":"100"}"""
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Post, "/api/v1/me/portfolio/transactions", a, tx).status)
            val summary = json.decodeFromString(PortfolioReport.serializer(), client.call(HttpMethod.Get, "/api/v1/me/portfolio/accounts/acc/summary", a).bodyAsText())
            // A recreated store reads the same ledger.
            assertEquals(1, PortfolioService(FirestoreUserDataStore(db), clock::millis).get(a).transactions.size)
            assertEquals(0, dec(summary.summary.totalValue).compareTo(BigDecimal("297")), "2 × 110 × 1.35 at the latest close")
            assertEquals(0, dec(summary.summary.investedCost).compareTo(BigDecimal("270")), "2 × 100 × 1.35 (FakeFx's rate on the trade date)")
            assertEquals("2026-10-16", summary.pricesAsOf)
            // User B sees an empty ledger and can't reach A's account.
            assertFalse(client.call(HttpMethod.Get, "/api/v1/me/portfolio", b).bodyAsText().contains("AAPL"))
            assertEquals(HttpStatusCode.NotFound, client.call(HttpMethod.Get, "/api/v1/me/portfolio/accounts/acc/summary", b).status)
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Get, "/api/v1/me/practice", b).status)
            assertTrue(practice(FirestoreUserDataStore(db)).overview(b).holdings.isEmpty())
            // Persisted user-entered data is unchanged after valuation.
            val ledger = json.decodeFromString(PortfolioLedger.serializer(), client.call(HttpMethod.Get, "/api/v1/me/portfolio", a).bodyAsText())
            assertEquals("100", ledger.transactions.single().unitPrice); assertEquals("2", ledger.transactions.single().quantity)
        } finally { cleanup(db, a, b); db.close() }
    }

    @Test fun missingOrInvalidTokensAreRejectedWithoutLeakingDetails() = testApplication {
        val db = database()
        try {
            val client = app(FirestoreUserDataStore(db))
            for ((path, method) in listOf("/api/v1/me/practice" to HttpMethod.Get, "/api/v1/me/portfolio" to HttpMethod.Get, "/api/v1/me/practice/orders/execute" to HttpMethod.Post)) {
                val none = client.request(path) { this.method = method }
                assertEquals(HttpStatusCode.Unauthorized, none.status)
                val bad = client.request(path) { this.method = method; header(HttpHeaders.Authorization, "Bearer expired-or-forged") }
                assertEquals(HttpStatusCode.Unauthorized, bad.status)
                assertEquals("SIGN_IN_REQUIRED", json.decodeFromString(org.example.stocksteps.model.ApiError.serializer(), bad.bodyAsText()).code)
                assertFalse(bad.bodyAsText().contains("expired-or-forged"), "the token is never echoed")
            }
        } finally { db.close() }
    }
}
