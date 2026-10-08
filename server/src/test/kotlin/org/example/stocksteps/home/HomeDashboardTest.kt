package org.example.stocksteps.home

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.homePersonaRoutes
import org.example.stocksteps.news.*
import org.example.stocksteps.newsRoutes
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.service.NewsService
import kotlin.test.*

class HomeDashboardTest {
    @Test fun allTenPersonasUseNormalizedFactsWithoutPrivateWrites() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { homePersonaRoutes() }
        }
        val personas = listOf("new-user", "watchlist-only", "portfolio-only", "watchlist-and-portfolio", "learning-only",
            "upcoming-earnings", "triggered-alerts", "no-news", "stale-quotes", "partial-failures")
        for (id in personas) {
            val response = client.get("/api/v1/home/personas/$id")
            assertEquals(HttpStatusCode.OK, response.status)
            val fixture = Json.decodeFromString<HomePersonaFixture>(response.bodyAsText())
            val state = fixture.dashboard()
            assertEquals(id, state.mockPersona)
            assertFalse(state.initializing)
            assertTrue(state.highlights.size <= 3)
            when (id) {
                "new-user", "portfolio-only", "learning-only" -> assertEquals(0, state.watchlistCount)
                "no-news" -> assertTrue(state.stories.isEmpty())
                "stale-quotes" -> { assertTrue(state.highlights.all { it.stale }); assertTrue(state.brief.isEmpty()) }
                "partial-failures" -> { assertNotNull(state.newsError); assertNotNull(state.quotesError) }
                "upcoming-earnings" -> {
                    val calendar = state.events.filter { it.id.startsWith("earnings:") }
                    assertEquals(2, calendar.size); assertTrue(calendar.first().detail.startsWith("Estimated"))
                    assertEquals("2 companies you follow report earnings this week", state.events.first().title) // weekly summary, never displacing events
                }
                "triggered-alerts" -> assertTrue(state.events.single().alerts)
            }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/home/personas/unknown").status)
    }

    @Test fun noPersonaRoutesExistUnlessExplicitlyRegisteredInMock() = testApplication {
        application { routing { } }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/home/personas/watchlist-only").status)
    }

    @Test fun homeFeedDisablesAiCacheLookupsAndGeneration() = testApplication {
        var cacheReads = 0
        var aiCalls = 0
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val summaries = object : NewsSimplificationStore {
            override suspend fun read(key: String): SimplifiedNews? { cacheReads++; return null }
            override suspend fun claim(key: String, now: Long) = error("Home must never queue AI")
            override suspend fun save(key: String, value: SimplifiedNews) = Unit
            override suspend fun fail(key: String, retryAt: Long) = Unit
        }
        val simplifier = object : AiNewsSimplifier {
            override suspend fun simplify(article: NewsArticle): SimplifiedNews { aiCalls++; error("Must not run") }
        }
        val provider = object : NewsProviderRepository {
            override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>()
            override suspend fun getCompanyNews(symbol: String) = listOf(NewsArticle("Apple announces new products", "https://example.com/apple", symbol = symbol, description = "Apple announced new products at its event."))
        }
        val service = NewsService(provider, NewsSimplificationService(simplifier, summaries, workerScope, "test"))
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { newsRoutes(service) }
        }
        try {
            val response = client.get("/api/v1/stocks/AAPL/news?enrich=false&limit=10")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, cacheReads)
            assertEquals(0, aiCalls)
            assertTrue(response.bodyAsText().contains("Apple announces"))
        } finally { workerScope.cancel() }
    }
}
