package org.example.stocksteps.domain

import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.NewsArticle

interface MarketRepository {
    suspend fun getGainers(): List<MarketMover>
    suspend fun getLosers(): List<MarketMover>
    suspend fun getCompanyNews(symbol: String): List<NewsArticle> = emptyList()
    suspend fun getNews(): List<NewsArticle>
}

class GetMarketGainers(private val repository: MarketRepository) {
    suspend operator fun invoke() = repository.getGainers()
}
class GetMarketLosers(private val repository: MarketRepository) {
    suspend operator fun invoke() = repository.getLosers()
}
class GetMarketNews(private val repository: MarketRepository) {
    suspend operator fun invoke() = repository.getNews()
}

class GetCompanyNews(private val repository: MarketRepository) {
    suspend operator fun invoke(symbol: String) = repository.getCompanyNews(symbol)
}
