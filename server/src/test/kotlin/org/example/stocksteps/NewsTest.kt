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
import org.example.stocksteps.repositoryImpl.FinnhubNewsProviderRepositoryImpl
import org.example.stocksteps.service.NewsService
import kotlin.test.*

class NewsTest {
    @Test fun companyFeedFiltersBeforeLimitAndCachesIdentityWithSafeFallback() = kotlinx.coroutines.runBlocking<Unit> {
        var profileCalls = 0
        var failProfile = false
        val upstream = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/stock/profile2") {
                profileCalls++
                respond(if (failProfile) "private failure" else """{"name":"Apple Inc.","ticker":"AAPL"}""",
                    if (failProfile) HttpStatusCode.Forbidden else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                val unrelated = (1..21).joinToString(",") { """{"headline":"Nvidia record $it","summary":"Foxconn supplier sales","related":"AAPL,NVDA","id":$it,"url":"https://example.com/$it","datetime":1700000100}""" }
                respond("""[$unrelated,{"headline":"Apple product update","related":"AAPL","id":22,"url":"https://example.com/apple","datetime":1700000000},{"headline":"AAPL earnings","related":"AAPL","id":23,"url":"https://example.com/earnings","datetime":1699999999}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { install(ClientContentNegotiation) { json() } }
        upstream.use {
            val provider = FinnhubNewsProviderRepositoryImpl(it, "test-key")
            assertEquals(listOf("Apple product update", "AAPL earnings"), provider.getCompanyNews("AAPL").map { article -> article.title })
            provider.getCompanyNews("AAPL")
            assertEquals(1, profileCalls)
            failProfile = true
            val conservative = FinnhubNewsProviderRepositoryImpl(it, "test-key").getCompanyNews("AAPL")
            assertEquals(listOf("AAPL earnings"), conservative.map { article -> article.title })
        }
    }

    @Test fun companyNewsUsesExactSymbolAndHasIndependentFailure() = testApplication {
        var fail = false
        var clock = 0L
        val upstream = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/stock/profile2") {
                return@MockEngine respond("""{"name":"Shopify Inc.","ticker":"SHOP.TO"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            assertEquals("/api/v1/company-news", request.url.encodedPath)
            assertEquals("SHOP.TO", request.url.parameters["symbol"])
            assertEquals("test-key", request.headers["X-Finnhub-Token"])
            assertNotNull(request.url.parameters["from"])
            assertNotNull(request.url.parameters["to"])
            respond(if (fail) "private upstream text" else """[{"headline":"Shopify company update","url":"https://example.com/update","datetime":1700000000}]""",
                if (fail) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { newsRoutes(NewsService(FinnhubNewsProviderRepositoryImpl(upstream, "test-key"),
                    cache = org.example.stocksteps.service.CompanyFinancialCache(now = { clock }))) }
            }
            val response = client.get("/api/v1/stocks/SHOP.TO/news")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("SHOP.TO", Json.decodeFromString<List<NewsArticle>>(response.bodyAsText()).single().symbol)
            fail = true
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/SHOP.TO/news").status, "served from the 10-minute cache")
            clock += 11 * 60_000L
            val error = client.get("/api/v1/stocks/SHOP.TO/news")
            assertEquals(HttpStatusCode.BadGateway, error.status)
            assertFalse(error.bodyAsText().contains("private upstream text"))
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/BAD_SYMBOL/news").status)
        } finally { upstream.close() }
    }

    @Test
    fun slicesNewestFirstAndPreservesNullableMetadata() = kotlinx.coroutines.runBlocking<Unit> {
        val upstream = HttpClient(MockEngine {
            respond("""[
                {"headline":"Old","url":"https://example.com/old","datetime":1700000000},
                {"headline":"New","url":"https://example.com/new","datetime":1700000002,"image":""},
                {"headline":"Middle","url":"https://example.com/middle","datetime":1700000001}
            ]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json() } }
        upstream.use {
            val provider = FinnhubNewsProviderRepositoryImpl(it, "test-key")
            assertEquals(listOf("New", "Middle"), provider.getNews(0, 2).map { article -> article.title })
            assertEquals(listOf("Old"), provider.getNews(1, 2).map { article -> article.title })
            assertTrue(provider.getNews(2, 2).isEmpty())
            assertNull(provider.getNews(0, 1).first().symbol)
            assertNull(provider.getNews(0, 1).first().imageUrl)
        }
    }

    @Test
    fun newsMapsAndValidatesPaginationAndHandlesFailures() = testApplication {
        var calls = 0
        var mode = "valid"
        val upstream = HttpClient(MockEngine { request ->
            calls++
            assertEquals("/api/v1/news", request.url.encodedPath)
            assertEquals("test-key", request.headers["X-Finnhub-Token"])
            assertEquals("general", request.url.parameters["category"])
            assertNull(request.url.parameters["apikey"])
            assertNull(request.url.parameters["page"])
            val body = when (mode) {
                "valid", "paged" -> """[{"headline":"Apple news","url":"https://example.com/article","source":"Example","datetime":1700000000,"image":"https://example.com/image.png","summary":"Unused provider content"}]"""
                "empty" -> "[]"
                "bad-url" -> """[{"headline":"Bad link","url":"javascript:alert(1)","datetime":1700000000}]"""
                "broken" -> "{}"
                else -> "secret upstream body"
            }
            respond(body, when(mode) {
                "limit" -> HttpStatusCode.TooManyRequests
                "down" -> HttpStatusCode.ServiceUnavailable
                else -> HttpStatusCode.OK
            }, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            application {
                install(ContentNegotiation) { json() }
                configureApiErrors()
                routing { newsRoutes(NewsService(FinnhubNewsProviderRepositoryImpl(upstream, "test-key"))) }
            }
            for (query in listOf("page=-1", "page=101", "page=abc", "limit=0", "limit=101", "limit=", "page=999999999999")) {
                val response = client.get("/api/v1/news?$query")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("INVALID_NEWS_QUERY", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
            }
            assertEquals(0, calls)
            for (value in listOf("valid", "paged")) {
                mode = value
                val response = client.get(if(value == "paged") "/api/v1/news?page=2&limit=5" else "/api/v1/news")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(if (value == "paged") emptyList() else listOf(NewsArticle("Apple news", "https://example.com/article", null, "Example",
                    "2023-11-14T22:13:20Z", "https://example.com/image.png", description = "Unused provider content")),
                    Json.decodeFromString<List<NewsArticle>>(response.bodyAsText()))
            }
            mode = "empty"
            val empty = client.get("/api/v1/news")
            assertEquals(HttpStatusCode.OK, empty.status)
            assertEquals(emptyList(), Json.decodeFromString<List<NewsArticle>>(empty.bodyAsText()))
            for ((value, status, code) in listOf(
                Triple("bad-url", 502, "INVALID_PROVIDER_RESPONSE"),
                Triple("broken", 502, "INVALID_PROVIDER_RESPONSE"),
                Triple("limit", 503, "PROVIDER_RATE_LIMITED"),
                Triple("down", 502, "PROVIDER_UNAVAILABLE")
            )) {
                mode = value
                val response = client.get("/api/v1/news")
                assertEquals(status, response.status.value)
                val body = response.bodyAsText()
                assertEquals(code, Json.decodeFromString<ApiError>(body).code)
                assertFalse(body.contains("secret"))
            }
        } finally { upstream.close() }
    }
}
