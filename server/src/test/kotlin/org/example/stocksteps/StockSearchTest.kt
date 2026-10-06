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
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.StockService
import kotlin.test.*

class StockSearchTest {
    @Test
    fun searchKeepsOnlyUsdOnUsExchangesAndPreservesProviderOrder() = testApplication {
        val upstream = HttpClient(MockEngine { request ->
            assertTrue(request.url.encodedPath in listOf("/stable/search-name", "/stable/search-symbol"))
            assertEquals("apple", request.url.parameters["query"])
            assertEquals("test-key", request.url.parameters["apikey"])
            respond("""[
                {"symbol":"AAPL.MX","name":"Apple Inc.","currency":"MXN"},
                {"symbol":"UNKNOWN","name":"Unknown currency"},
                {"symbol":"AAPL.TO","name":"Apple Inc.","currency":"CAD","exchange":"TSX","exchangeFullName":"Toronto Stock Exchange"},
                {"symbol":"APC.DE","name":"Apple Inc.","currency":"EUR"},
                    {"symbol":"FOREIGN","name":"USD foreign listing","currency":"USD","exchange":"TSX"},
                    {"symbol":"UNKNOWN","name":"Unknown exchange","currency":"USD"},
                {"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ"}
            ]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { stockRoutes(StockService(FmpStockProviderRepositoryImpl(upstream, "test-key"))) }
            }
            val response = client.get("/api/v1/stocks/search") { parameter("query", "  apple  ") }
            assertEquals(HttpStatusCode.OK, response.status)
            val results = Json.decodeFromString<List<StockSearchResult>>(response.bodyAsText())
            assertEquals(listOf(
                StockSearchResult("AAPL", "Apple Inc.", "USD", "NASDAQ")
            ), results)
        } finally { upstream.close() }
    }

    @Test
    fun tickerSearchMergesDeduplicatesAndRanksExactMatchFirst() = testApplication {
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val upstream = HttpClient(MockEngine { request ->
            paths.add(request.url.encodedPath)
            assertEquals("aapl", request.url.parameters["query"])
            val body = when (request.url.encodedPath) {
                "/stable/search-symbol" -> """[
                    {"symbol":"AAPL.TO","name":"Apple Inc.","currency":"CAD"},
                    {"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ"}
                ]"""
                "/stable/search-name" -> """[
                    {"symbol":"aapl","name":"Duplicate Apple","currency":"USD","exchange":"NASDAQ"},
                    {"symbol":"APLE","name":"Apple Hospitality","currency":"USD","exchange":"NASDAQ"},
                    {"symbol":"APC.DE","name":"Apple Inc.","currency":"EUR"},
                    {"symbol":"FOREIGN","name":"USD foreign listing","currency":"USD","exchange":"TSX"},
                    {"symbol":"UNKNOWN","name":"Unknown exchange","currency":"USD"}
                ]"""
                else -> error("Unexpected endpoint")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { stockRoutes(StockService(FmpStockProviderRepositoryImpl(upstream, "test-key"))) }
            }
            val response = client.get("/api/v1/stocks/search?query=aapl")
            assertEquals(HttpStatusCode.OK, response.status)
            val results = Json.decodeFromString<List<StockSearchResult>>(response.bodyAsText())
            assertEquals(listOf("AAPL", "APLE"), results.map { it.symbol })
            assertEquals("Apple Inc.", results.first().name)
            assertEquals(setOf("/stable/search-name", "/stable/search-symbol"), paths.toSet())
        } finally { upstream.close() }
    }

    @Test
    fun validatesQueriesAndReturnsEmptyResultsOrProviderErrors() = testApplication {
        var calls = 0
        val upstream = HttpClient(MockEngine { request ->
            calls++
            val (body, status) = when (request.url.parameters["query"]) {
                "missing" -> "[]" to HttpStatusCode.OK
                "limit" -> "upstream-secret" to HttpStatusCode.TooManyRequests
                "broken" -> "{}" to HttpStatusCode.OK
                else -> error("Unexpected provider call")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { stockRoutes(StockService(FmpStockProviderRepositoryImpl(upstream, "test-key"))) }
            }
            for (query in listOf(null, "", "   ", "a".repeat(101), "apple\ninc")) {
                val response = client.get("/api/v1/stocks/search") {
                    if (query != null) parameter("query", query)
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("INVALID_QUERY", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
            }
            assertEquals(0, calls)
            val empty = client.get("/api/v1/stocks/search?query=missing")
            assertEquals(HttpStatusCode.OK, empty.status)
            assertEquals(emptyList(), Json.decodeFromString<List<StockSearchResult>>(empty.bodyAsText()))
            for ((query, status, code) in listOf(
                Triple("limit", 503, "PROVIDER_RATE_LIMITED"),
                Triple("broken", 502, "INVALID_PROVIDER_RESPONSE")
            )) {
                val response = client.get("/api/v1/stocks/search?query=$query")
                assertEquals(status, response.status.value)
                val body = response.bodyAsText()
                assertEquals(code, Json.decodeFromString<ApiError>(body).code)
                assertFalse(body.contains("upstream-secret"))
            }
        } finally { upstream.close() }
    }
}
