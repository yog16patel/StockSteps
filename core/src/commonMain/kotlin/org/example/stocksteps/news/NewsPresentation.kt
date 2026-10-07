package org.example.stocksteps.news

import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.NewsSentiment
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Display model for a news card on any screen; original text is never rewritten here. */
data class NewsUiModel(
    val id: String,
    val source: String?,
    val publishedLabel: String?,
    val headline: String,
    val summary: String?,
    val whyItMatters: String?,
    val sentiment: NewsSentiment?,
    val aiSimplified: Boolean,
    val url: String?,
    val imageUrl: String?
)

@OptIn(ExperimentalTime::class)
object NewsPresentation {
    fun model(article: NewsArticle): NewsUiModel = model(article, Clock.System.now().toEpochMilliseconds())

    fun model(article: NewsArticle, nowEpochMillis: Long): NewsUiModel {
        val explanation = article.explanation
        return NewsUiModel(
            id = article.id ?: article.url,
            source = article.source?.takeIf { it.isNotBlank() },
            publishedLabel = article.publishedAt?.let { relativeTime(it, nowEpochMillis) },
            headline = explanation?.simpleHeadline ?: article.title,
            summary = explanation?.summary ?: article.description?.trim()?.takeIf { it.isNotBlank() && !repeatsTitle(it, article.title) },
            whyItMatters = explanation?.whyItMatters,
            sentiment = explanation?.sentiment,
            aiSimplified = explanation != null,
            url = article.url.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            imageUrl = article.imageUrl?.takeIf { it.startsWith("https://") }
        )
    }

    /** Providers often echo the headline (plus publisher) as the description; that adds nothing. */
    private fun repeatsTitle(description: String, title: String): Boolean {
        fun normalized(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
        val headline = normalized(title.substringBeforeLast(" - "))
        return headline.isNotEmpty() && normalized(description).startsWith(headline)
    }

    /** "Just now", "12m ago", "3h ago", "2d ago"; null when the timestamp cannot be read. */
    fun relativeTime(iso: String, nowEpochMillis: Long): String? {
        val published = runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull() ?: return null
        val minutes = ((nowEpochMillis - published) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }
}
