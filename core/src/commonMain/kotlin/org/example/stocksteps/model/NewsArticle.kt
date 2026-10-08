package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class NewsArticle(
    val title: String,
    val url: String,
    val symbol: String? = null,
    val source: String? = null,
    val publishedAt: String? = null,
    val imageUrl: String? = null,
    val id: String? = null,
    val description: String? = null,
    @kotlinx.serialization.EncodeDefault
    val explanation: SimplifiedNews? = null,
    /** Deterministic keyword classification; OTHER when the evidence isn't clear. */
    val category: NewsCategory? = null
)
