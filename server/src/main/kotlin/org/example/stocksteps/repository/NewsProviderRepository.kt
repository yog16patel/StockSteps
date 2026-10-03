package org.example.stocksteps.repository

import org.example.stocksteps.model.NewsArticle

interface NewsProviderRepository {
    suspend fun getNews(page: Int, limit: Int): List<NewsArticle>
}
