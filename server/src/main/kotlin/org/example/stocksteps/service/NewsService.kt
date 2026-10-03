package org.example.stocksteps.service

import org.example.stocksteps.repository.NewsProviderRepository

class NewsService(private val provider: NewsProviderRepository) {
    suspend fun getNews(page: Int, limit: Int) = provider.getNews(page, limit)
}
