package org.example.stocksteps.userdata

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.example.stocksteps.learning.LearningService
import org.example.stocksteps.learning.learningRoutes
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.PortfolioLedger
import org.example.stocksteps.practice.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.security.TokenVerification
import org.example.stocksteps.security.securedForTest
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.StockService
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpRequest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.*

/**
 * Phase 9A: two **independent Firebase Auth emulator accounts** against the real Ktor routes, the production security stack
 * (`securedForTest`: admission, rate limits, central gate) and the real [FirestoreUserDataStore] on the **Firestore emulator** (demo
 * project; never production). Skipped unless `FIREBASE_AUTH_EMULATOR_HOST` and `FIRESTORE_EMULATOR_HOST` are set, e.g.
 * `firebase emulators:exec --only auth,firestore --project demo-stocksteps-9a "./gradlew :server:test --tests '*TwoUserIsolation*'"`.
 *
 * Emulator ID tokens are unsigned (`alg: none`), so they're checked by [EmulatorTokenAuthenticator] (claims + the emulator's own account
 * lookup) — a test double for [FirebaseIdTokenAuthenticator], which refuses these exact tokens (asserted below).
 */
class FirebaseEmulatorTwoUserIsolationTest {
    private val project = "demo-stocksteps-9a"
    private val json = Json { ignoreUnknownKeys = true }
    private val authHost: String? = System.getenv("FIREBASE_AUTH_EMULATOR_HOST")
    private val http = java.net.http.HttpClient.newHttpClient()

    private fun requireEmulators() = assumeTrue("Run inside firebase emulators:exec (auth + firestore)",
        !authHost.isNullOrBlank() && !System.getenv("FIRESTORE_EMULATOR_HOST").isNullOrBlank())

    private fun emulatorPost(path: String, body: String): Pair<Int, JsonObject> {
        val request = HttpRequest.newBuilder(URI("http://$authHost/identitytoolkit.googleapis.com/v1/$path?key=fake-api-key"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to Json.parseToJsonElement(response.body()).jsonObject
    }

    private data class Account(val uid: String, val token: String)
    private fun signUp(): Account {
        val (status, body) = emulatorPost("accounts:signUp", """{"email":"u-${UUID.randomUUID()}@example.test","password":"test-only-${UUID.randomUUID()}","returnSecureToken":true}""")
        check(status == 200) { "emulator sign-up failed: $status" }
        return Account(body.getValue("localId").jsonPrimitive.content, body.getValue("idToken").jsonPrimitive.content)
    }

    /** Emulator-only verification: project claims, expiry and the emulator's account lookup (so deleted accounts stop working). */
    private inner class EmulatorTokenAuthenticator : UserAuthenticator {
        override suspend fun uid(bearerToken: String) = (verify(bearerToken) as? TokenVerification.Valid)?.uid
        override suspend fun verify(bearerToken: String): TokenVerification {
            val claims = runCatching { Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(bearerToken.split('.')[1].let { it.padEnd((it.length + 3) / 4 * 4, '=') }))).jsonObject }
                .getOrNull() ?: return TokenVerification.Failure.MALFORMED
            if (claims["aud"]?.jsonPrimitive?.content != project || claims["iss"]?.jsonPrimitive?.content != "https://securetoken.google.com/$project") return TokenVerification.Failure.WRONG_PROJECT
            if ((claims["exp"]?.jsonPrimitive?.long ?: 0) <= System.currentTimeMillis() / 1000) return TokenVerification.Failure.EXPIRED
            val (status, body) = emulatorPost("accounts:lookup", """{"idToken":"$bearerToken"}""")
            val uid = body["users"]?.jsonArray?.firstOrNull()?.jsonObject?.get("localId")?.jsonPrimitive?.content
            return if (status == 200 && uid != null && uid == claims["sub"]?.jsonPrimitive?.content) TokenVerification.Valid(uid, "password") else TokenVerification.Failure.INVALID
        }
    }

    private fun database(): Firestore = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId(project)
        .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().service

    private fun ApplicationTestBuilder.server(store: UserDataStore): HttpClient {
        val auth = EmulatorTokenAuthenticator()
        val marketClock = org.example.stocksteps.practice.MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
        val clock = org.example.stocksteps.practice.MutableClock(Instant.parse("2026-10-17T15:00:00Z"))
        val market = FakeMarket().apply { history = mapOf("AAPL" to listOf(PricePoint("2026-10-15", 100.0), PricePoint("2026-10-16", 110.0))) }
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = false)
        val practice = PracticeService(store, entitlements, market, FakeFx(), clock, marketClock, null, sampleData = false, eod = EodSessions(marketClock))
        val fixtures = FixtureMarketDataSource(sampleFallback = true)
        val watch = WatchMarketData(StockService(fixtures, fixtures), fixtures, NewsService(fixtures))
        val portfolios = PortfolioService(store, clock::millis)
        val charts = PriceChartService(fixtures, eod = EodSessions(marketClock))
        val portfolioMarket = PortfolioMarketService(portfolios, fixtures, watch, FakeFx(), marketClock, dailyCloses = charts::getDailyCloses, eod = EodSessions(marketClock))
        application { securedForTest(auth) {
            practiceRoutes(auth, practice, mock = false)
            portfolioRoutes(auth, portfolios, portfolioMarket)
            userRoutes(auth, WatchlistsService(store, now = clock::millis), AlertsService(store, watch, AlertRules(), "note", now = clock::millis), store, clock::millis)
            learningRoutes(auth, LearningService(store, entitlements, { error("not used") }, null, 0, clock))
        } }
        return createClient { }
    }

    private suspend fun HttpClient.call(method: HttpMethod, path: String, who: Account?, body: String? = null): HttpResponse = request(path) {
        this.method = method
        who?.let { bearerAuth(it.token) }
        body?.let { contentType(ContentType.Application.Json); setBody(it) }
    }

    private fun cleanup(db: Firestore, vararg uids: String) = uids.forEach { uid ->
        val user = db.collection("users").document(uid)
        listOf("practice/account", "portfolio/ledger", "meta/entitlements", "meta/learning", "meta/watchlists").forEach { runCatching { user.collection(it.substringBefore('/')).document(it.substringAfter('/')).delete().get() } }
        runCatching { user.collection("watchlists").get().get().documents.forEach { it.reference.delete().get() } }
        runCatching { db.collection("alertRules").whereEqualTo("ownerUid", uid).get().get().documents.forEach { it.reference.delete().get() } }
    }

    @Test fun realVerifierRefusesEmulatorTokens() {
        requireEmulators()
        val token = signUp().token
        val verdict = runBlocking { FirebaseIdTokenAuthenticator(project).verify(token) }
        assertTrue(verdict is TokenVerification.Failure, "an unsigned emulator token never passes the REAL verifier: $verdict")
    }

    @Test fun twoAccountsCannotReadOrChangeEachOthersData() = testApplication {
        requireEmulators()
        val db = database(); val a = signUp(); val b = signUp()
        try {
            val client = server(FirestoreUserDataStore(db))
            // Guests: every protected read and write is refused and writes nothing.
            for ((m, p) in listOf(HttpMethod.Get to "/api/v1/me/portfolio", HttpMethod.Get to "/api/v1/me/practice", HttpMethod.Get to "/api/v1/me/watchlists",
                HttpMethod.Get to "/api/v1/me/alerts", HttpMethod.Get to "/api/v1/me/learning", HttpMethod.Put to "/api/v1/me/portfolio/accounts"))
                assertEquals(HttpStatusCode.Unauthorized, client.call(m, p, null, if (m == HttpMethod.Put) """{"id":"acc-guest","name":"Guest"}""" else null).status, "guest $m $p")

            // My Portfolio: A's account and transaction.
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Put, "/api/v1/me/portfolio/accounts", a, """{"id":"acc-1","name":"A TFSA"}""").status)
            val tx = """{"id":"tx-a-1","accountId":"acc-1","type":"CASH_DEPOSIT","tradeDate":"2026-10-01","grossAmount":"1000","currency":"CAD"}"""
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Post, "/api/v1/me/portfolio/transactions", a, tx).status)
            val bLedger = json.decodeFromString(PortfolioLedger.serializer(), client.call(HttpMethod.Get, "/api/v1/me/portfolio", b).bodyAsText())
            assertTrue(bLedger.accounts.isEmpty() && bLedger.transactions.isEmpty(), "B sees nothing of A's ledger")
            assertNotEquals(HttpStatusCode.OK, client.call(HttpMethod.Get, "/api/v1/me/portfolio/accounts/acc-1/summary", b).status, "A's account id means nothing to B")
            assertEquals(HttpStatusCode.BadRequest, client.call(HttpMethod.Delete, "/api/v1/me/portfolio/transactions/tx-a-1", b).status, "B can't delete A's transaction")
            assertEquals(HttpStatusCode.BadRequest, client.call(HttpMethod.Put, "/api/v1/me/portfolio/transactions", b, tx.replace("1000", "1")).status, "B can't edit A's transaction")
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Put, "/api/v1/me/portfolio/accounts", b, """{"id":"acc-1","name":"B own"}""").status, "the same id creates B's own account")
            val aLedger = json.decodeFromString(PortfolioLedger.serializer(), client.call(HttpMethod.Get, "/api/v1/me/portfolio", a).bodyAsText())
            assertEquals("A TFSA", aLedger.accounts.single().name); assertEquals("1000", aLedger.transactions.single().grossAmount.removeSuffix(".00").removeSuffix(".0"))

            // Practice: A's fill never shows up for B; B reusing A's idempotency key is B's own order.
            val order = """{"symbol":"AAPL","side":"BUY","quantity":"2","idempotencyKey":"shared-key-abcdef","reviewedPrice":"110"}"""
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", a, order).status)
            assertTrue(json.decodeFromString(PracticeTransactions.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice/transactions", b).bodyAsText()).items.isEmpty())
            assertTrue(json.decodeFromString(PracticeOverview.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice", b).bodyAsText()).holdings.isEmpty())
            val bOrder = client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", b, order)
            assertEquals(HttpStatusCode.OK, bOrder.status)
            assertFalse(json.decodeFromString(PracticeOrderResult.serializer(), bOrder.bodyAsText()).replayed, "keys are per account: B's order isn't A's replay")
            assertEquals(1, json.decodeFromString(PracticeTransactions.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice/transactions", a).bodyAsText()).items.size)

            // Watchlists and alerts: ids from A's documents can't be used by B.
            val added = client.call(HttpMethod.Post, "/api/v1/me/watchlists/default/entries", a, """{"instrument":{"symbol":"MSFT","name":"Microsoft","exchange":"NASDAQ","currency":"USD"}}""")
            assertEquals(HttpStatusCode.OK, added.status)
            val entry = json.decodeFromString(WatchlistsResponse.serializer(), added.bodyAsText()).watchlists.single().entries.single()
            assertEquals(HttpStatusCode.NotFound, client.call(HttpMethod.Patch, "/api/v1/me/watchlists/default/entries/${entry.id}", b, """{"note":"mine now"}""").status)
            // Deletes are idempotent on the caller's own documents: B's "delete" of A's id is a no-op on B's list (200 or 404), never A's.
            assertTrue(client.call(HttpMethod.Delete, "/api/v1/me/watchlists/default/entries/${entry.id}", b).status in setOf(HttpStatusCode.OK, HttpStatusCode.NotFound))
            val alert = client.call(HttpMethod.Post, "/api/v1/me/alerts", a, """{"instrument":{"symbol":"MSFT","currency":"USD"},"type":"PRICE_ABOVE","threshold":100000,"currency":"USD"}""")
            assertEquals(HttpStatusCode.OK, alert.status)
            val alertId = json.decodeFromString(AlertsResponse.serializer(), alert.bodyAsText()).alerts.single().id
            assertTrue(client.call(HttpMethod.Delete, "/api/v1/me/alerts/$alertId", b).status in setOf(HttpStatusCode.OK, HttpStatusCode.NotFound))
            assertEquals(HttpStatusCode.NotFound, client.call(HttpMethod.Patch, "/api/v1/me/alerts/$alertId", b, """{"status":"PAUSED"}""").status)
            assertTrue(json.decodeFromString(AlertsResponse.serializer(), client.call(HttpMethod.Get, "/api/v1/me/alerts", b).bodyAsText()).alerts.isEmpty())
            assertEquals(1, json.decodeFromString(AlertsResponse.serializer(), client.call(HttpMethod.Get, "/api/v1/me/alerts", a).bodyAsText()).alerts.size)
            assertEquals(1, json.decodeFromString(WatchlistsResponse.serializer(), client.call(HttpMethod.Get, "/api/v1/me/watchlists", a).bodyAsText()).watchlists.single().entries.size)

            // Concurrent orders from both accounts land in their own ledgers.
            coroutineScope {
                listOf(a, b).map { who -> async { client.call(HttpMethod.Post, "/api/v1/me/practice/orders/execute", who, order.replace("shared-key-abcdef", "parallel-${who.uid.take(6)}-abcdef")) } }.awaitAll()
                    .forEach { assertEquals(HttpStatusCode.OK, it.status) }
            }
            assertEquals(2, json.decodeFromString(PracticeTransactions.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice/transactions", a).bodyAsText()).items.size)
            assertEquals(2, json.decodeFromString(PracticeTransactions.serializer(), client.call(HttpMethod.Get, "/api/v1/me/practice/transactions", b).bodyAsText()).items.size)

            // A deleted account's token stops working where the verifier checks the account (see AUTHENTICATION_REVIEW.md for REAL).
            assertEquals(200, emulatorPost("accounts:delete", """{"idToken":"${a.token}"}""").first)
            assertEquals(HttpStatusCode.Unauthorized, client.call(HttpMethod.Get, "/api/v1/me/portfolio", a).status)
        } finally { cleanup(db, a.uid, b.uid); db.close() }
    }
}
