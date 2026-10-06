package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.NewsArticle
import java.time.Instant

@Serializable
data class FinnhubNewsArticle(
    val headline: String,
    val url: String,
    val datetime: Long,
    val source: String? = null,
    val image: String? = null,
    val id: Long? = null,
    val summary: String? = null,
    val related: String? = null
)

fun FinnhubNewsArticle.toNewsArticle() = NewsArticle(
    title = headline, url = url, symbol = null, source = source,
    publishedAt = Instant.ofEpochSecond(datetime).toString(),
    imageUrl = image?.takeIf { it.isNotBlank() },
    id = id?.takeIf { it > 0L }?.let { "finnhub:$it" },
    description = summary?.takeIf { it.isNotBlank() }
)
