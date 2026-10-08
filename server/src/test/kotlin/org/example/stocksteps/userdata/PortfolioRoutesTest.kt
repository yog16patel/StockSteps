package org.example.stocksteps.userdata

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class PortfolioRoutesTest {
    @Test fun authenticatedCrudSummaryAndHistoryNeverExposeAnotherUsersAccount() = testApplication {
        val store = InMemoryUserDataStore()
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
        assertEquals(HttpStatusCode.OK, client.put("$path/accounts") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"id":"account","name":"My investments","reportingCurrency":"USD"}""")
        }.status)
        assertEquals(HttpStatusCode.OK, client.post("$path/transactions") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"id":"opening","accountId":"account","type":"OPENING_POSITION","tradeDate":"2026-10-01","currency":"USD","instrument":{"symbol":"AAPL","exchange":"NASDAQ"},"quantity":"0.5","unitPrice":"100"}""")
        }.status)
        val report = client.get("$path/accounts/account/summary") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.OK, report.status)
        val parsed = Json.decodeFromString<PortfolioReport>(report.bodyAsText())
        assertEquals("50", parsed.summary.investedCost)
        assertNotNull(parsed.summary.totalValue)
        assertEquals(HttpStatusCode.NotFound, client.get("$path/accounts/account/summary") { bearerAuth("mock-user:bob") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$path/accounts/account/history?range=bad") { bearerAuth("mock-user:alice") }.status)
        val history = client.get("$path/accounts/account/history?range=ALL") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.OK, history.status)
        assertTrue(Json.decodeFromString<PortfolioReport>(history.bodyAsText()).history.all { it.date >= "2026-10-01" })
        assertEquals(HttpStatusCode.BadRequest, client.post("$path/transactions") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"id":"sale","accountId":"account","type":"SELL","tradeDate":"2026-10-02","currency":"USD","instrument":{"symbol":"AAPL"},"quantity":"1","unitPrice":"100"}""")
        }.status)
    }
}
