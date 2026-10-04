package org.example.stocksteps.service

import org.example.stocksteps.repository.NewsProviderRepository

class NewsService(
    private val provider: NewsProviderRepository,
    private val simplification: org.example.stocksteps.news.NewsSimplificationService? = null
) {
    suspend fun getNews(page: Int, limit: Int): List<org.example.stocksteps.model.NewsArticle> {
        val articles = provider.getNews(page, limit)
        return simplification?.enrich(articles) ?: articles
    }
}
