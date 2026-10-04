package org.example.stocksteps.news

import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.SimplifiedNews

interface AiNewsSimplifier {
    suspend fun simplify(article: NewsArticle): SimplifiedNews
}

fun SimplifiedNews.validated(): SimplifiedNews {
    require(simpleHeadline.isNotBlank() && simpleHeadline.length <= 180)
    require(summary.isNotBlank() && summary.length <= 800)
    require(whyItMatters.isNotBlank() && whyItMatters.length <= 800)
    val score = confidence
    require(score == null || score.isFinite() && score in 0.0..1.0)
    return this
}
