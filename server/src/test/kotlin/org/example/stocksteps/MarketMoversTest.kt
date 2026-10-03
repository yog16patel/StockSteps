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

class MarketMoversTest {
    @Test
    fun mapsRanksAndHandlesEmptyResultsAndProviderFailures() = testApplication {
        var mode = "valid"
        val upstream = HttpClient(MockEngine { request ->
            assertEquals("test-key", request.url.parameters["apikey"])
            assertNull(request.url.parameters["symbol"])
            val gainers = when (request.url.encodedPath) {
                "/stable/biggest-gainers" -> true
                "/stable/biggest-losers" -> false
                else -> error("Unexpected endpoint")
            }
            val sign = if (gainers) "" else "-"
            val body = when (mode) {
                "valid" -> """[
                    {"symbol":"AAA","name":"Company A","price":12.0,"change":${sign}1.0,"changesPercentage":${sign}5.0,"exchange":"NASDAQ"},
                    {"symbol":"BBB","price":20.0,"change":${sign}2.0,"changesPercentage":${sign}10.0}
                ]"""
                "empty" -> "[]"
                "wrong-sign" -> """[{"symbol":"AAA","price":12,"change":${if (gainers) "-" else ""}1,"changesPercentage":${if (gainers) "-" else ""}5}]"""
                "malformed" -> "{}"
                else -> "secret-provider-body"
            }
            val status = when (mode) {
                "limit" -> HttpStatusCode.TooManyRequests
                "down" -> HttpStatusCode.ServiceUnavailable
                else -> HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { marketRoutes(StockService(FmpStockProviderRepositoryImpl(upstream, "test-key"))) }
            }
            for (endpoint in listOf("gainers", "losers")) {
                mode = "valid"
                val response = client.get("/api/v1/market/$endpoint")
                assertEquals(HttpStatusCode.OK, response.status)
                val results = Json.decodeFromString<List<MarketMover>>(response.bodyAsText())
                assertEquals(listOf("BBB", "AAA"), results.map { it.symbol })
                assertEquals(if (endpoint == "gainers") 10.0 else -10.0, results.first().changePercent)
                assertEquals("NASDAQ", results.last().exchange)
                assertEquals("Company A", results.last().name)
                mode = "empty"
                val empty = client.get("/api/v1/market/$endpoint")
                assertEquals(HttpStatusCode.OK, empty.status)
                assertEquals(emptyList(), Json.decodeFromString<List<MarketMover>>(empty.bodyAsText()))
                for ((failure, status, code) in listOf(
                    Triple("malformed", 502, "INVALID_PROVIDER_RESPONSE"),
                    Triple("wrong-sign", 502, "INVALID_PROVIDER_RESPONSE"),
                    Triple("limit", 503, "PROVIDER_RATE_LIMITED"),
                    Triple("down", 502, "PROVIDER_UNAVAILABLE")
                )) {
                    mode = failure
                    val error = client.get("/api/v1/market/$endpoint")
                    assertEquals(status, error.status.value)
                    val body = error.bodyAsText()
                    assertEquals(code, Json.decodeFromString<ApiError>(body).code)
                    assertFalse(body.contains("secret"))
                }
            }
        } finally { upstream.close() }
    }
}
