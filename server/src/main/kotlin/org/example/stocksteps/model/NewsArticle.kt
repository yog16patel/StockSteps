package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val title: String,
    val url: String,
    val symbol: String? = null,
    val source: String? = null,
    val publishedAt: String? = null,
    val imageUrl: String? = null
)
