package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
enum class NewsSentiment { POSITIVE, NEGATIVE, NEUTRAL, MIXED }

@Serializable
data class SimplifiedNews(
    val simpleHeadline: String,
    val summary: String,
    val whyItMatters: String,
    val sentiment: NewsSentiment,
    val confidence: Double? = null
)
