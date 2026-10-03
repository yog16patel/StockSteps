package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.StockService
import kotlin.test.*

class CompanyProfileTest {
    @Test
    fun profileMapsFieldsAndHandlesMissingDataAndErrors() = testApplication {
        var calls = 0
        val upstream = HttpClient(MockEngine { request ->
            calls++
            assertEquals("/stable/profile", request.url.encodedPath)
            assertEquals("test-key", request.url.parameters["apikey"])
            val (body, status) = when (request.url.parameters["symbol"]) {
                "AAPL" -> """[{"symbol":"AAPL","companyName":"Apple Inc.","description":"Makes devices","sector":"Technology","industry":"Consumer Electronics","website":"https://www.apple.com","country":"US","currency":"USD","exchange":"NASDAQ","image":"https://example.com/apple.png","price":333.0}]""" to HttpStatusCode.OK
                "MINIMAL" -> """[{"symbol":"MINIMAL"}]""" to HttpStatusCode.OK
                "MISSING" -> "[]" to HttpStatusCode.OK
                "WRONG" -> """[{"symbol":"MSFT"}]""" to HttpStatusCode.OK
                "MULTIPLE" -> """[{"symbol":"MULTIPLE"},{"symbol":"MULTIPLE"}]""" to HttpStatusCode.OK
                "BROKEN" -> "{}" to HttpStatusCode.OK
                "LIMIT" -> "secret upstream body" to HttpStatusCode.TooManyRequests
                "DOWN" -> "secret upstream body" to HttpStatusCode.ServiceUnavailable
                else -> error("Unexpected provider request")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { stockRoutes(StockService(FmpStockProviderRepositoryImpl(upstream, "test-key"))) }
            }
            val invalid = client.get("/api/v1/stocks/%24BAD/profile")
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertEquals(0, calls)
            val response = client.get("/api/v1/stocks/aapl/profile")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(CompanyProfile("AAPL", "Apple Inc.", "Makes devices", "Technology",
                "Consumer Electronics", "https://www.apple.com", "US", "USD", "NASDAQ",
                "https://example.com/apple.png"), Json.decodeFromString<CompanyProfile>(response.bodyAsText()))
            val minimal = client.get("/api/v1/stocks/MINIMAL/profile")
            assertEquals(CompanyProfile("MINIMAL"), Json.decodeFromString<CompanyProfile>(minimal.bodyAsText()))
            for ((symbol, status, code) in listOf(
                Triple("MISSING", 404, "PROFILE_NOT_FOUND"),
                Triple("WRONG", 502, "INVALID_PROVIDER_RESPONSE"),
                Triple("MULTIPLE", 502, "INVALID_PROVIDER_RESPONSE"),
                Triple("BROKEN", 502, "INVALID_PROVIDER_RESPONSE"),
                Triple("LIMIT", 503, "PROVIDER_RATE_LIMITED"),
                Triple("DOWN", 502, "PROVIDER_UNAVAILABLE")
            )) {
                val error = client.get("/api/v1/stocks/$symbol/profile")
                assertEquals(status, error.status.value)
                val body = error.bodyAsText()
                assertEquals(code, Json.decodeFromString<ApiError>(body).code)
                assertFalse(body.contains("secret"))
            }
        } finally { upstream.close() }
    }
}
