package org.example.stocksteps.userdata

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.mockDataSources
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.service.UsMarketCalendar
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.*

class UserRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun jwt(uid: String) = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString("""{"user_id":"$uid"}""".toByteArray()) + ".sig"

    @Test fun mockAuthenticatorReadsUidsButRejectsGarbage() = runBlocking {
        val auth = MockUserAuthenticator()
        assertEquals("alice", auth.uid(jwt("alice")))
        assertEquals("bob", auth.uid("mock-user:bob"))
        assertNull(auth.uid("not-a-token"))
        assertNull(auth.uid(jwt("bad uid/../x")))
    }

    @Test fun routesRequireSignInAndKeepUsersApart() = testApplication {
        val fixtures = FixtureMarketDataSource(sampleFallback = true)
        val store = InMemoryUserDataStore()
        val clock = Clock.fixed(Instant.parse("2026-10-07T21:15:00Z"), ZoneOffset.UTC)
        val market = WatchMarketData(StockService(fixtures, fixtures), fixtures, NewsService(fixtures))
        val rules = AlertRules()
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                userRoutes(MockUserAuthenticator(), WatchlistsService(store, now = clock::millis), AlertsService(store, market, rules, "note", now = clock::millis), store, clock::millis)
                watchDataRoutes(WatchDataService(market, rules, UsMarketCalendar(), clock, "Sample"))
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/watchlists").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/watchlists") { bearerAuth("garbage") }.status)
        val alice = jwt("alice")
        val lists = json.decodeFromString(WatchlistsResponse.serializer(), client.get("/api/v1/me/watchlists") { bearerAuth(alice) }.bodyAsText())
        assertEquals("My Stocks", lists.watchlists.single().name)
        val added = client.post("/api/v1/me/watchlists/default/entries") {
            bearerAuth(alice); contentType(ContentType.Application.Json); setBody("""{"instrument":{"symbol":"MSFT","name":"Microsoft","exchange":"NASDAQ","currency":"USD"}}""")
        }
        assertEquals(HttpStatusCode.OK, added.status)
        val entry = json.decodeFromString(WatchlistsResponse.serializer(), added.bodyAsText()).watchlists.single().entries.single()
        // Bob can't touch Alice's entry even with its id.
        val bob = jwt("bob")
        assertEquals(HttpStatusCode.NotFound, client.patch("/api/v1/me/watchlists/default/entries/${entry.id}") {
            bearerAuth(bob); contentType(ContentType.Application.Json); setBody("""{"note":"hi"}""")
        }.status)
        assertTrue(client.get("/api/v1/me/watchlists") { bearerAuth(bob) }.bodyAsText().contains("\"entries\": []") ||
            json.decodeFromString(WatchlistsResponse.serializer(), client.get("/api/v1/me/watchlists") { bearerAuth(bob) }.bodyAsText()).watchlists.single().entries.isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/me/watchlists") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody("""{"name":""}""") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/me/watchlists") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody("{not json") }.status)

        // Alerts: already met → 409, then created with a choice.
        val price = fixtures.getQuote("MSFT")!!.price!!
        val create = """{"instrument":{"symbol":"MSFT","currency":"USD"},"type":"PRICE_ABOVE","threshold":${price - 10},"currency":"USD"}"""
        val conflict = client.post("/api/v1/me/alerts") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody(create) }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertTrue(conflict.bodyAsText().contains("CONDITION_ALREADY_MET"))
        val created = client.post("/api/v1/me/alerts") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody(create.dropLast(1) + ""","whenAlreadyMet":"WAIT_FOR_CROSS"}""") }
        assertEquals(HttpStatusCode.OK, created.status)
        assertTrue(json.decodeFromString(AlertsResponse.serializer(), client.get("/api/v1/me/alerts") { bearerAuth(bob) }.bodyAsText()).alerts.isEmpty())

        assertEquals(HttpStatusCode.NoContent, client.post("/api/v1/me/devices") {
            bearerAuth(alice); contentType(ContentType.Application.Json); setBody("""{"deviceId":"install-1","token":"fcm-token","platform":"android"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/me/devices") {
            bearerAuth(alice); contentType(ContentType.Application.Json); setBody("""{"deviceId":"x","token":"t","platform":"windows"}""")
        }.status)
        assertEquals(1, store.devices("alice").size)

        val data = json.decodeFromString(WatchDataResponse.serializer(), client.get("/api/v1/stocks/watch-data?symbols=msft,NVDA,ZZZZ").bodyAsText())
        val msft = data.quotes.first { it.symbol == "MSFT" }
        assertEquals("USD", msft.currency); assertFalse(msft.stale); assertNotNull(msft.changePercent)
        assertTrue(data.earnings.any { it.symbol == "MSFT" && it.status == EarningsDateStatus.ESTIMATED })
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/watch-data?symbols=").status)
    }

    @Test fun mockModeUsesOnlyLocalUserDataAndSimulatedPush() {
        val sources = mockDataSources()
        assertIs<InMemoryUserDataStore>(sources.userData())
        assertIs<MockUserAuthenticator>(sources.userAuth)
        assertIs<SimulatedPushSender>(sources.pushSender())
        assertIs<FixtureMarketDataSource>(sources.earningsCalendar)
        assertTrue(sources.alertsDeliveryNote.contains("simulated"))
    }

    @Test fun missingStorageAnswers503InsteadOfCrashing() = testApplication {
        val fixtures = FixtureMarketDataSource(sampleFallback = true)
        val market = WatchMarketData(StockService(fixtures, fixtures), fixtures, NewsService(fixtures))
        val store = UnavailableUserDataStore
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                userRoutes(MockUserAuthenticator(), WatchlistsService(store), AlertsService(store, market, deliveryNote = "n"), store)
                alertEvaluationRoutes(AlertEvaluator(store, market, SimulatedPushSender()), secret = null, mock = true)
            }
        }
        val response = client.get("/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("USER_DATA_UNAVAILABLE"))
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/me/alerts") { bearerAuth("mock-user:alice") }.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/internal/alerts/evaluate").status)
    }
}
