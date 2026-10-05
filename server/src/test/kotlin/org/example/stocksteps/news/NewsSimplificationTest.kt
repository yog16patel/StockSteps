package org.example.stocksteps.news

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.service.NewsService
import java.nio.file.Files
import kotlin.test.*

class NewsSimplificationTest {
    private val article = NewsArticle(
        "Apple shares rise after demand report", "https://example.com/news/1",
        source = "Publisher", publishedAt = "2026-10-04T12:00:00Z", id = "finnhub:1",
        description = "Apple shares rose after reports of stronger iPhone demand."
    )
    private val explanation = SimplifiedNews("Apple shares rise after stronger iPhone demand", "Shares rose after a demand report.", "The report may matter to investors; sales effects are not established.", NewsSentiment.POSITIVE)
    private fun client(text: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient = HttpClient(MockEngine {
        assertEquals("generativelanguage.googleapis.com", it.url.host)
        assertEquals("test-key", it.headers["x-goog-api-key"])
        assertNull(it.url.parameters["key"])
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
    })
    private fun envelope(text: String): String = buildJsonObject {
        put("candidates", buildJsonArray { add(buildJsonObject {
            put("finishReason", "STOP")
            putJsonObject("content") { put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) }) }
        }) })
    }.toString()
    @Test fun validStructuredResponse() = runBlocking {
        val c = client(envelope(Json.encodeToString(explanation)))
        try { assertEquals(explanation, GeminiNewsSimplifier(c, "test-key", "test-model").simplify(article)) }
        finally { c.close() }
    }
    @Test fun rejectsMalformedMissingFieldsAndUnsupportedSentiment() = runBlocking {
        val outputs = listOf("not json", "{}", Json.encodeToString(explanation).replace("POSITIVE", "BUY"))
        for (output in outputs) {
            val c = client(envelope(output))
            try { assertFails { GeminiNewsSimplifier(c, "test-key", "test-model").simplify(article) } }
            finally { c.close() }
        }
    }
    @Test fun rejectsProviderErrorAndIncompleteOutput() = runBlocking {
        for ((body, status) in listOf("Quota exceeded" to HttpStatusCode.TooManyRequests, envelope(Json.encodeToString(explanation)).replace("STOP", "MAX_TOKENS") to HttpStatusCode.OK)) {
            val c = client(body, status)
            try { assertFails { GeminiNewsSimplifier(c, "test-key", "test-model").simplify(article) } }
            finally { c.close() }
        }
    }
    @Test fun dtoPreservesDescriptionIdAndAttribution() {
        val mapped = FinnhubNewsArticle("Headline", "https://example.com", 1L, "Publisher", id = 42L, summary = "Description").toNewsArticle()
        assertEquals("finnhub:42", mapped.id)
        assertEquals("Description", mapped.description)
        assertEquals("Publisher", mapped.source)
        assertNull(mapped.explanation)
    }
    @Test fun identityAndRelevanceAreDeterministic() {
        assertEquals("finnhub:1", NewsSimplificationService.identity(article))
        assertEquals(NewsSimplificationService.identity(article.copy(id = null)), NewsSimplificationService.identity(article.copy(id = null, url = article.url + "#fragment")))
        assertEquals(NewsSimplificationService.identity(article.copy(id = null)), NewsSimplificationService.identity(article.copy(id = null, url = article.url + "?utm_source=test&fbclid=ignored")))
        assertTrue(NewsSimplificationService.relevant(article))
        assertFalse(NewsSimplificationService.relevant(article.copy(description = null)))
        assertFalse(NewsSimplificationService.relevant(article.copy(title = "Celebrity wedding", description = "Wedding photos")))
        assertFalse(NewsSimplificationService.relevant(article.copy(symbol = "NVDA")))
    }
    @Test fun persistentStoreCoalescesClaimsAndSurvivesRestart() = runBlocking {
        val path = Files.createTempDirectory("news-test").resolve("news.db")
        val a = SqliteNewsSimplificationStore(path)
        val b = SqliteNewsSimplificationStore(path)
        val claims = coroutineScope { (1..10).map { async { a.claim("key", 1_000) } }.awaitAll() }
        assertEquals(1, claims.count { it })
        assertFalse(b.claim("key", 1_001))
        a.save("key", explanation)
        assertEquals(explanation, b.read("key"))
        assertFalse(b.claim("key", 100_000))
        assertTrue(a.claim("failed", 1_000))
        a.fail("failed", 100_000)
        assertFalse(b.claim("failed", 99_999))
        assertTrue(b.claim("failed", 100_000))
    }
    @Test fun uncachedDuplicateArticlesGenerateOnceAndCachedReturnWithoutAi() = runBlocking {
        val store = SqliteNewsSimplificationStore(Files.createTempDirectory("news-worker").resolve("news.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val ai = object : AiNewsSimplifier {
            override suspend fun simplify(article: NewsArticle): SimplifiedNews {
                calls++; started.complete(Unit); release.await(); return explanation
            }
        }
        try {
            val service = NewsSimplificationService(ai, store, scope, "v1")
            assertEquals(1, service.enrich(listOf(article, article)).size)
            withTimeout(2_000) { started.await() }
            repeat(10) { assertNull(service.enrich(listOf(article)).single().explanation) }
            release.complete(Unit)
            withTimeout(2_000) {
                while (service.enrich(listOf(article)).single().explanation == null) delay(10)
            }
            assertEquals(explanation, service.enrich(listOf(article)).single().explanation)
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }
    @Test fun failuresAndTimeoutKeepOriginalNewsAndDoNotRetryImmediately() = runBlocking {
        for (timeout in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = SqliteNewsSimplificationStore(Files.createTempDirectory("news-failure").resolve("news.db"))
            val failed = CompletableDeferred<Unit>()
            var calls = 0
            val ai = object : AiNewsSimplifier {
                override suspend fun simplify(article: NewsArticle): SimplifiedNews {
                    calls++; failed.complete(Unit)
                    if (timeout) awaitCancellation() else error("Provider failure")
                }
            }
            try {
                val enrich = NewsSimplificationService(ai, store, scope, "v1", jobTimeoutMillis = 30)
                val provider = object : NewsProviderRepository {
                    override suspend fun getNews(page: Int, limit: Int) = listOf(article)
                }
                val news = NewsService(provider, enrich)
                assertEquals(article.title, news.getNews(0, 1).single().title)
                withTimeout(2_000) { failed.await() }
                delay(100)
                assertNull(news.getNews(0, 1).single().explanation)
                delay(100)
                assertEquals(1, calls)
            } finally { scope.cancel() }
        }
    }
    @Test fun cachedSummariesRemainAvailableWithoutAiProvider() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = SqliteNewsSimplificationStore(Files.createTempDirectory("news-readonly").resolve("news.db"))
        try {
            val ai = object : AiNewsSimplifier {
                override suspend fun simplify(article: NewsArticle) = explanation
            }
            val writer = NewsSimplificationService(ai, store, scope, "v1")
            writer.enrich(listOf(article))
            withTimeout(2_000) {
                while (writer.enrich(listOf(article)).single().explanation == null) delay(10)
            }
            val reader = NewsSimplificationService(null, store, scope, "v1")
            assertEquals(explanation, reader.enrich(listOf(article)).single().explanation)
            assertNull(reader.enrich(listOf(article.copy(id = "new"))).single().explanation)
        } finally { scope.cancel() }
    }

    @Test fun slowCloudCacheDoesNotHoldNewsResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = object : NewsSimplificationStore {
            override suspend fun read(key: String): SimplifiedNews? { awaitCancellation() }
            override suspend fun claim(key: String, now: Long) = false
            override suspend fun save(key: String, value: SimplifiedNews) = Unit
            override suspend fun fail(key: String, retryAt: Long) = Unit
        }
        try {
            val service = NewsSimplificationService(null, store, scope, "v1")
            val result = withTimeout(3_000) { service.enrich(listOf(article)) }.single()
            assertEquals(article.title, result.title)
            assertNull(result.explanation)
        } finally { scope.cancel() }
    }

    @Test fun earlierLocalSummariesMigrateWithoutAnotherAiCall() = runBlocking {
        val local = SqliteNewsSimplificationStore(Files.createTempDirectory("news-migrate-local").resolve("news.db"))
        val destination = SqliteNewsSimplificationStore(Files.createTempDirectory("news-migrate-destination").resolve("news.db"))
        local.claim("key", 1_000)
        local.save("key", explanation)
        val migrating = MigratingNewsSimplificationStore(destination, local)
        assertEquals(explanation, migrating.read("key"))
        assertEquals(explanation, destination.read("key"))
        assertFalse(destination.claim("key", Long.MAX_VALUE - 60_000))
    }

    @Test fun slowCachePreservesExplanationsAlreadyLoaded() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var reads = 0
        val store = object : NewsSimplificationStore {
            override suspend fun read(key: String): SimplifiedNews? {
                if (reads++ == 0) return explanation
                awaitCancellation()
            }
            override suspend fun claim(key: String, now: Long) = false
            override suspend fun save(key: String, value: SimplifiedNews) = Unit
            override suspend fun fail(key: String, retryAt: Long) = Unit
        }
        try {
            val result = withTimeout(3_000) {
                NewsSimplificationService(null, store, scope, "v1").enrich(listOf(article, article.copy(id = "second")))
            }
            assertEquals(explanation, result.first().explanation)
            assertNull(result.last().explanation)
        } finally { scope.cancel() }
    }

    @Test fun irrelevantArticlesNeverCallAi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = SqliteNewsSimplificationStore(Files.createTempDirectory("news-irrelevant").resolve("news.db"))
        val ai = object : AiNewsSimplifier {
            override suspend fun simplify(article: NewsArticle): SimplifiedNews = error("Must not call AI")
        }
        try {
            val irrelevant = article.copy(title = "Celebrity wedding", description = "Wedding photos")
            assertNull(NewsSimplificationService(ai, store, scope, "v1").enrich(listOf(irrelevant)).single().explanation)
        } finally { scope.cancel() }
    }
}
