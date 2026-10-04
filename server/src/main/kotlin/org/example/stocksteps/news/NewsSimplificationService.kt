package org.example.stocksteps.news

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.example.stocksteps.model.NewsArticle
import java.net.URI
import java.security.MessageDigest

class NewsSimplificationService(
    private val ai: AiNewsSimplifier,
    private val store: NewsSimplificationStore,
    private val scope: CoroutineScope,
    private val version: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val jobTimeoutMillis: Long = 10_000
) {
    private data class Work(val key: String, val article: NewsArticle)
    private val queue = Channel<Work>(QUEUE_CAPACITY)
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    init {
        require(jobTimeoutMillis in 1..30_000)

        // One worker bounds spending/concurrency and keeps AI off the response path.
        scope.launch {
            for (work in queue) {
                try {
                    if (!store.claim(work.key, now())) continue
                    val result = withTimeout(jobTimeoutMillis) { ai.simplify(work.article).validated() }
                    store.save(work.key, result)
                } catch (cause: Exception) {
                    if (cause is CancellationException && cause !is TimeoutCancellationException) throw cause
                    runCatching { store.fail(work.key, now() + FAILURE_COOLDOWN_MILLIS) }
                } finally { pending.remove(work.key) }
            }
        }
    }
    suspend fun enrich(articles: List<NewsArticle>): List<NewsArticle> {
        var queued = 0
        return articles.distinctBy(::identity).map { article ->
            val id = identity(article)
            val key = digest("$id|${article.title}|${article.description.orEmpty()}|${article.symbol.orEmpty()}|${article.source.orEmpty()}|$version")
            try {
                val cached = store.read(key)
                if (cached != null) article.copy(id = id, explanation = cached)
                else {
                    if (queued < MAX_ARTICLES_PER_RESPONSE && relevant(article) && pending.add(key)) {
                        val sent = queue.trySend(Work(key, article)).isSuccess
                        if (sent) queued++ else pending.remove(key)
                    }
                    article.copy(id = id, explanation = null)
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                article.copy(id = id, explanation = null)
            }
        }
    }
    companion object {
        private const val QUEUE_CAPACITY = 20
        private const val MAX_ARTICLES_PER_RESPONSE = 5
        private const val FAILURE_COOLDOWN_MILLIS = 15 * 60_000L

        fun identity(article: NewsArticle): String = article.id?.takeIf { it.isNotBlank() }
            ?: digest(runCatching {
                val u = URI(article.url)
                val query = u.rawQuery?.split("&")?.filterNot {
                    val name = it.substringBefore("=").lowercase()
                    name.startsWith("utm_") || name in setOf("fbclid", "gclid")
                }?.sorted()?.joinToString("&")?.takeIf { it.isNotBlank() }
                buildString {
                    append(u.scheme?.lowercase()); append("://"); append(u.rawAuthority?.lowercase())
                    append(u.rawPath.orEmpty())
                    if (query != null) { append("?"); append(query) }
                }
            }.getOrDefault(article.url) + "|" + article.title.trim().lowercase())
        fun relevant(article: NewsArticle): Boolean {
            if (article.title.isBlank() || article.description.isNullOrBlank()) return false
            val text = "${article.title} ${article.description}"
            val symbol = article.symbol
            if (!symbol.isNullOrBlank()) return Regex("(?i)(?<![a-z0-9])${Regex.escape(symbol)}(?![a-z0-9])").containsMatchIn(text)
            return Regex("(?i)\\b(stock|shares|market|earnings|revenue|profit|inflation|economy|economic|interest rates|bonds|investors|federal reserve|bank|trading|tariff|oil|currency)\\b").containsMatchIn(text)
        }
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
