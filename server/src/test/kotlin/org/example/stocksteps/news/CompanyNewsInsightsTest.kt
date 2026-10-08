package org.example.stocksteps.news

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.newsInsightRoutes
import org.example.stocksteps.newsRoutes
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class CompanyNewsInsightsTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    private class CountingProvider(val articles: List<NewsArticle>) : NewsProviderRepository {
        val calls = AtomicInteger()
        override suspend fun getCompanyNews(symbol: String): List<NewsArticle> { calls.incrementAndGet(); return articles }
        override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>()
    }

    private fun article(id: String, title: String, at: String?, description: String? = "A sufficiently long description of what happened at the company today.", url: String = "https://news.example/$id", image: String? = null) =
        NewsArticle(title = title, url = url, symbol = "ACME", source = "Wire", publishedAt = at, imageUrl = image, id = id, description = description)

    @Test fun feedIsNormalizedDeduplicatedClassifiedAndSorted() = runBlocking {
        val provider = CountingProvider(listOf(
            article("a", "Acme reports quarterly earnings above estimates", "2026-10-07T13:00:00Z", image = "http://insecure.example/i.png"),
            article("b", "Acme unveils new robot vacuum", "2026-10-07T18:00:00Z"),
            article("c", "ACME UNVEILS NEW ROBOT VACUUM!", "2026-10-07T18:05:00Z"), // same headline
            article("b", "Acme unveils new robot vacuum (update)", "2026-10-07T18:10:00Z"), // same id
            article("d", "Insecure link", "2026-10-07T10:00:00Z", url = "http://news.example/d"),
            article("e", "From the future", "2027-01-01T00:00:00Z"),
            article("f", "Acme names new chief designer", null, description = "  "),
            article("g", "  ", "2026-10-07T10:00:00Z")
        ))
        val service = NewsService(provider, now = { now })
        val feed = service.companyFeed("ACME")
        assertEquals(listOf("b", "a", "f"), feed.map { it.id }, "newest first, undated last, duplicates and invalid items dropped")
        assertEquals(NewsCategory.EARNINGS, feed[1].category)
        assertEquals(NewsCategory.PRODUCTS, feed[0].category)
        assertNull(feed[1].imageUrl, "non-https image removed")
        assertNull(feed[2].description, "blank summary becomes null (headline-only)")
        // Filters and pages are sliced from one cached provider call.
        assertEquals(listOf("a"), service.getCompanyNews("ACME", NewsCategory.EARNINGS, 0, 10).map { it.id })
        assertEquals(listOf("a"), service.getCompanyNews("ACME", null, 1, 1).map { it.id })
        service.getCompanyNews("ACME")
        assertEquals(1, provider.calls.get())
    }

    @Test fun validatorRejectsUngroundedOrUnsafeDrafts() {
        val source = article("x", "Acme revenue rose 12% in the third quarter", "2026-10-07T13:00:00Z",
            description = "Acme said revenue rose 12% to 4.5 billion dollars, and it will expand into Canada next year.")
        fun draft(summary: String = "Acme said its revenue grew 12% in the third quarter.", why: List<String> = listOf("Sales growth shows demand for its products."), ids: List<String> = listOf("x")) =
            InsightDraft(summary, why, listOf("The next quarterly report."), ids)
        assertNotNull(InsightValidator.validate(draft(), source))
        assertNotNull(InsightValidator.validate(draft(summary = "Acme said it will expand into Canada next year, and revenue rose 12%."), source), "wording quoted from the article is allowed")
        assertNull(InsightValidator.validate(draft(summary = "Acme's revenue grew 15% in the third quarter."), source), "invented number")
        assertNull(InsightValidator.validate(draft(summary = "Revenue rose 12%, see https://acme.example for details."), source), "link")
        assertNull(InsightValidator.validate(draft(why = listOf("You should buy the stock before it climbs.")), source), "advice")
        assertNull(InsightValidator.validate(draft(why = listOf("The shares will rise after this report.")), source), "price prediction")
        assertNull(InsightValidator.validate(draft(ids = listOf("x", "other")), source), "unknown source id")
        assertNull(InsightValidator.validate(draft(summary = "Too short."), source))
        assertNull(InsightValidator.validate(draft(why = emptyList()), source))
        assertNull(InsightValidator.validate(null, source))
    }

    @Test fun insightsAreGeneratedOnceAndCached() = runBlocking {
        val calls = AtomicInteger()
        val generator = ArticleInsightGenerator { article ->
            calls.incrementAndGet(); delay(50)
            InsightDraft("Acme reported its quarterly earnings today.", listOf("Earnings show how the business is doing."), sourceIds = listOf(article.id!!))
        }
        val news = NewsService(CountingProvider(listOf(article("a", "Acme reports quarterly earnings with higher EPS", "2026-10-07T13:00:00Z"))), now = { now })
        val service = ArticleInsightService(news, generator, "test-v1", now = { now })
        val results = coroutineScope { (1..5).map { async { service.insight("ACME", "a") } }.awaitAll() }
        assertTrue(results.all { it?.availability == InsightAvailability.AVAILABLE })
        service.insight("ACME", "a")
        assertEquals(1, calls.get(), "concurrent and repeated requests share one generation")
        val insight = results.first()!!
        assertEquals(listOf("a"), insight.sources.map { it.articleId })
        assertTrue(insight.terms.any { it.term.startsWith("Earnings per share") }, "glossary terms come from the article")
        assertTrue(insight.limitations.single().contains("headline and summary"))
        assertNull(service.insight("ACME", "missing"), "unknown articles are 404")
    }

    @Test fun insightFailuresDegradeToUnavailable() = runBlocking {
        val articles = listOf(
            article("slow", "Acme expands into Canada", "2026-10-07T13:00:00Z"),
            article("bad", "Acme signs partnership deal", "2026-10-07T12:00:00Z"),
            article("thin", "Acme names new chief designer", "2026-10-07T11:00:00Z", description = null),
            article("boom", "Acme opens new factory", "2026-10-07T10:00:00Z")
        )
        val generator = ArticleInsightGenerator { article ->
            when (article.id) {
                "slow" -> { delay(5_000); error("unreachable") }
                "bad" -> InsightDraft("Shares will rise 45% after this deal is signed.", listOf("You should buy now."), sourceIds = listOf("other"))
                else -> error("provider failed")
            }
        }
        val service = ArticleInsightService(NewsService(CountingProvider(articles), now = { now }), generator, "v", timeoutMillis = 100, now = { now })
        for (id in listOf("slow", "bad", "thin", "boom")) {
            val insight = assertNotNull(service.insight("ACME", id))
            assertEquals(InsightAvailability.UNAVAILABLE, insight.availability, id)
            assertNull(insight.simpleSummary)
            assertEquals(id, insight.sources.single().articleId)
            assertTrue(insight.limitations.isNotEmpty())
        }
        val none = ArticleInsightService(NewsService(CountingProvider(articles), now = { now }), generator = null, version = "v")
        assertEquals(InsightAvailability.UNAVAILABLE, none.insight("ACME", "boom")?.availability, "no generator configured")
    }

    @Test fun mockRoutesServeFilteredNewsAndTemplateInsights() = testApplication {
        val news = NewsService(FixtureMarketDataSource(sampleFallback = true), now = { now })
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                newsRoutes(news)
                newsInsightRoutes(ArticleInsightService(news, TemplateArticleInsightGenerator(slowDelayMillis = 2_000), "mock-template-v1", timeoutMillis = 200))
            }
        }
        val all = json.decodeFromString(ListSerializer(NewsArticle.serializer()), client.get("/api/v1/stocks/MSFT/news").bodyAsText())
        assertTrue(all.isNotEmpty() && all.all { it.url.startsWith("https://") && it.category != null })
        assertTrue(all.none { it.id == "sample:msft-insecure-link" || it.id == "sample:msft-future" || it.id == "sample:msft-duplicate" })
        assertEquals(all.sortedByDescending { it.publishedAt }.map { it.id }, all.map { it.id })
        assertEquals("sample:msft-old", all.last().id)
        val earnings = json.decodeFromString(ListSerializer(NewsArticle.serializer()), client.get("/api/v1/stocks/MSFT/news?category=earnings&limit=5").bodyAsText())
        assertTrue(earnings.isNotEmpty() && earnings.all { it.category == NewsCategory.EARNINGS })
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/MSFT/news?category=rumors").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/MSFT/news?limit=500").status)

        fun insight(id: String) = runBlocking { client.get("/api/v1/stocks/MSFT/news/$id/insight") }
        val okBody = insight("sample:msft-earnings").bodyAsText()
        assertTrue(Regex("\"aiGenerated\":\\s*false").containsMatchIn(okBody), "MOCK explanations are explicitly marked as not AI")
        val ok = json.decodeFromString(ArticleInsight.serializer(), okBody)
        assertEquals(InsightAvailability.AVAILABLE, ok.availability)
        assertTrue(ok.simpleSummary!!.contains("31%"), "grounded in the article's own numbers")
        for (id in listOf("sample:msft-headline-only", "sample:msft-insight-timeout", "sample:msft-insight-malformed")) {
            assertEquals(InsightAvailability.UNAVAILABLE, json.decodeFromString(ArticleInsight.serializer(), insight(id).bodyAsText()).availability, id)
        }
        assertEquals(HttpStatusCode.NotFound, insight("finnhub:0").status)
        assertEquals(HttpStatusCode.BadRequest, insight("bad%20id!").status)
    }

    @Test fun classifierAndCommentaryRulesAreConservative() {
        assertEquals(NewsCategory.EARNINGS, NewsClassifier.classify("Acme beats estimates as quarterly results improve", null))
        assertEquals(NewsCategory.ANALYST, NewsClassifier.classify("Analyst upgrades Acme to overweight", null))
        assertEquals(NewsCategory.OTHER, NewsClassifier.classify("Acme in focus", "Shares were active."))
        assertEquals(NewsCategory.PRODUCTS, NewsClassifier.eventCategory("Acme unveils new AI laptop"))
        assertNull(NewsClassifier.eventCategory("Is Acme's AI spending finally paying off?"))
        assertNull(NewsClassifier.eventCategory("Acme and Beta to unveil new laptop"))
        assertNull(NewsClassifier.eventCategory("Analyst upgrades Acme to overweight"))
    }
}
