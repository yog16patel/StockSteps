package org.example.stocksteps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class RemoteCompanyNewsRepositoryTest {
    private fun repository(handler: (io.ktor.client.request.HttpRequestData) -> Pair<HttpStatusCode, String>, block: suspend (RemoteCompanyNewsRepository) -> Unit) = runBlocking {
        HttpClient(MockEngine { request ->
            val (status, body) = handler(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { block(RemoteCompanyNewsRepository(StockStepsApi(it, "https://stocksteps.test"))) }
    }

    @Test fun feedRequestsTheWholeFeedOnce() = repository({ request ->
        assertEquals("/api/v1/stocks/MSFT/news", request.url.encodedPath)
        assertEquals("50", request.url.parameters["limit"])
        assertNull(request.url.parameters["category"])
        HttpStatusCode.OK to """[{"title":"Microsoft reports earnings","url":"https://n.example/1","id":"finnhub:1","category":"EARNINGS"}]"""
    }) { assertEquals(NewsCategory.EARNINGS, GetCompanyNewsFeed(it)(" msft ").single().category) }

    @Test fun insightAndMovementDecodeAndMissingIsNull() {
        repository({ request ->
            when (request.url.encodedPath) {
                "/api/v1/stocks/MSFT/news/finnhub:1/insight" -> HttpStatusCode.OK to
                    """{"articleId":"finnhub:1","availability":"AVAILABLE","simpleSummary":"Microsoft reported results.","explanationVersion":"v1","aiGenerated":true}"""
                "/api/v1/stocks/MSFT/news/gone/insight" -> HttpStatusCode.NotFound to """{"code":"ARTICLE_NOT_FOUND","message":"gone"}"""
                "/api/v1/stocks/MSFT/movement" -> {
                    assertEquals("1W", request.url.parameters["period"])
                    HttpStatusCode.OK to """{"symbol":"MSFT","period":"1W","summary":"S","noConfirmedCatalyst":true,"explanationVersion":"v"}"""
                }
                else -> HttpStatusCode.NotFound to """{"code":"MOVEMENT_UNAVAILABLE","message":"none"}"""
            }
        }) { repo ->
            assertTrue(GetArticleInsight(repo)("MSFT", "finnhub:1")!!.aiGenerated)
            assertNull(GetArticleInsight(repo)("MSFT", "gone"))
            assertEquals(MovementPeriod.ONE_WEEK, GetMovementExplanation(repo)("MSFT", MovementPeriod.ONE_WEEK)!!.period)
            assertNull(GetMovementExplanation(repo)("NONE", MovementPeriod.ONE_DAY))
        }
    }

    @Test fun otherFailuresStayFailures() = repository({ HttpStatusCode.BadGateway to """{"code":"PROVIDER_UNAVAILABLE","message":"News is temporarily unavailable."}""" }) { repo ->
        assertFailsWith<StockDataException> { GetArticleInsight(repo)("MSFT", "finnhub:1") }
        assertFailsWith<StockDataException> { GetMovementExplanation(repo)("MSFT", MovementPeriod.ONE_DAY) }
    }
}
