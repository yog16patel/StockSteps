package org.example.stocksteps.userdata

import com.google.api.core.ApiFutures
import com.google.api.core.SettableApiFuture
import io.grpc.Status
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.portfolio.PortfolioLedger
import org.example.stocksteps.practice.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/**
 * REAL user data (Firestore) must answer like the in-memory store: a transaction's own rejection keeps its status, storage failures are
 * 503 USER_DATA_UNAVAILABLE, and nothing user-data related becomes 500 (docs/REAL_PORTFOLIO_PRACTICE_FIX.md).
 */
class FirestoreFailureMappingTest {
    /** Runs Portfolio and Practice transactions the way Firestore reports them: through an ApiFuture, failures wrapped in ExecutionException. */
    private class FirestoreLikeStore(private val delegate: InMemoryUserDataStore = InMemoryUserDataStore()) : UserDataStore by delegate {
        override suspend fun <T> updatePortfolio(uid: String, block: (PortfolioLedger) -> Pair<PortfolioLedger, T>): T =
            viaFuture { delegate.updatePortfolio(uid, block) }
        override suspend fun <T> updatePractice(uid: String, block: (PracticeAccountData) -> Pair<PracticeAccountData, T>): T =
            viaFuture { delegate.updatePractice(uid, block) }
        private suspend fun <T> viaFuture(block: suspend () -> T): T {
            val future = try { ApiFutures.immediateFuture(block()) } catch (cause: Exception) { ApiFutures.immediateFailedFuture(cause) }
            return awaitFirestore(future, 1)
        }
    }

    @Test fun transactionRejectionsAreRethrownUnwrapped() {
        val rejected = UserDataException(409, "PLUS_ACTIVE", "You already have StockSteps+.")
        assertSame(rejected, assertFailsWith<UserDataException> { awaitFirestore(ApiFutures.immediateFailedFuture<Unit>(rejected), 1) })
        val invalid = IllegalArgumentException("Maximum 100 distinct securities per portfolio ledger.")
        assertSame(invalid, assertFailsWith<IllegalArgumentException> { awaitFirestore(ApiFutures.immediateFailedFuture<Unit>(invalid), 1) })
        assertEquals("ok", awaitFirestore(ApiFutures.immediateFuture("ok"), 1))
    }

    @Test fun storageFailuresAre503UserDataUnavailable() {
        listOf(Status.PERMISSION_DENIED, Status.NOT_FOUND, Status.UNAUTHENTICATED, Status.UNAVAILABLE).forEach { status ->
            val error = assertFailsWith<UserDataException> {
                awaitFirestore(ApiFutures.immediateFailedFuture<Unit>(status.withDescription("projects/p/databases/(default)").asRuntimeException()), 1)
            }
            assertEquals(503, error.status, status.code.name)
            assertEquals("USER_DATA_UNAVAILABLE", error.code)
            assertFalse("projects/" in error.message, "storage details never reach the client")
        }
        val credentials = assertFailsWith<UserDataException> { awaitFirestore(ApiFutures.immediateFailedFuture<Unit>(java.io.IOException("token refresh failed")), 1) }
        assertEquals(503, credentials.status)
        val timeout = assertFailsWith<UserDataException> { awaitFirestore(SettableApiFuture.create<Unit>(), 0) }
        assertEquals("USER_DATA_UNAVAILABLE", timeout.code)
    }

    @Test fun portfolioCreateReadAndRejectionOverFirestoreLikeStorage() = testApplication {
        val store = FirestoreLikeStore()
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
        val service = PortfolioService(store, clock::millis)
        val market = PortfolioMarketService(service, fixture, WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture)), MockPortfolioFx, clock)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { portfolioRoutes(MockUserAuthenticator(), service, market) }
        }
        val path = "/api/v1/me/portfolio"
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.OK, client.get(path) { bearerAuth("mock-user:alice") }.status, "empty portfolio")
        assertEquals(HttpStatusCode.OK, client.put("$path/accounts") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"id":"account","name":"My investments","reportingCurrency":"USD"}""")
        }.status)
        assertTrue("My investments" in client.get(path) { bearerAuth("mock-user:alice") }.bodyAsText(), "created account reads back")
        assertFalse("My investments" in client.get(path) { bearerAuth("mock-user:bob") }.bodyAsText(), "other users never see it")
        // Selling more than is held is rejected inside the transaction: 400, not 500 (before the fix the ExecutionException escaped).
        val oversell = client.post("$path/transactions") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"id":"sale","accountId":"account","type":"SELL","tradeDate":"2026-10-02","currency":"USD","instrument":{"symbol":"AAPL"},"quantity":"1","unitPrice":"100"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, oversell.status)
        assertTrue("INVALID_PORTFOLIO" in oversell.bodyAsText())
    }

    @Test fun practiceLoadsAndKeepsTransactionRejectionsOverFirestoreLikeStorage() = testApplication {
        val store = FirestoreLikeStore()
        val clock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
        val svc = PracticeService(store, EntitlementService(store, clock::millis, false), FakeMarket(), FakeFx(), clock, MutableClock(Instant.parse("2026-10-07T21:15:00Z")), null, false)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { practiceRoutes(MockUserAuthenticator(), svc, mock = false) }
        }
        val overview = client.get("/api/v1/me/practice") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.OK, overview.status)
        assertTrue("\"cash\":\"10000\"" in overview.bodyAsText(), "initialized practice account")
        fun order(quantity: String, key: String) = """{"symbol":"AAPL","side":"BUY","quantity":"$quantity","idempotencyKey":"$key","reviewedPrice":"100"}"""
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/me/practice/orders/execute") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody(order("1", "order-key-000001"))
        }.status)
        // Cash is re-checked inside the transaction: 400 INSUFFICIENT_CASH, not 500 (before the fix the ExecutionException escaped).
        val tooLarge = client.post("/api/v1/me/practice/orders/execute") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody(order("1000", "order-key-000002"))
        }
        assertEquals(HttpStatusCode.BadRequest, tooLarge.status)
        assertTrue("INSUFFICIENT_CASH" in tooLarge.bodyAsText())
        assertTrue("\"holdings\":[{" in client.get("/api/v1/me/practice") { bearerAuth("mock-user:alice") }.bodyAsText(), "the first order persisted")
    }

    @Test fun withoutStorageEverySignedInRouteAnswers503WithNeutralCopy() = testApplication {
        val store = UnavailableUserDataStore
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
        val service = PortfolioService(store, clock::millis)
        val market = PortfolioMarketService(service, fixture, WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture)), MockPortfolioFx, clock)
        val practiceClock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
        val practice = PracticeService(store, EntitlementService(store, practiceClock::millis, false), FakeMarket(), FakeFx(), practiceClock, practiceClock, null, false)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                portfolioRoutes(MockUserAuthenticator(), service, market)
                practiceRoutes(MockUserAuthenticator(), practice, mock = false)
                // A route without its own UserDataException mapping (like Daily Brief's personalized overlay) relies on StatusPages.
                get("/api/v1/me/unmapped") { call.respond(store.brief("id") ?: "none") }
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/portfolio").status, "guests are still rejected first")
        listOf("/api/v1/me/portfolio", "/api/v1/me/practice", "/api/v1/me/unmapped").forEach { path ->
            val response = client.get(path) { bearerAuth("mock-user:alice") }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
            val body = response.bodyAsText()
            assertTrue("USER_DATA_UNAVAILABLE" in body, path)
            assertTrue(USER_DATA_UNAVAILABLE_MESSAGE in body, path)
            assertFalse("Watchlists and alerts" in body, "$path names no single feature")
        }
    }
}
