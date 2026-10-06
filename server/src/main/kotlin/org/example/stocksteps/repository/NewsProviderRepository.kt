package org.example.stocksteps.repository

import org.example.stocksteps.model.NewsArticle

interface NewsProviderRepository {
    suspend fun getCompanyNews(symbol: String): List<NewsArticle> = emptyList()
    suspend fun getNews(page: Int, limit: Int): List<NewsArticle>
}
