package org.example.stocksteps.repository

import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.model.CompanyProfile

interface StockProviderRepository : StockQuoteProviderRepository {
    suspend fun getGainers(): List<MarketMover>
    suspend fun getLosers(): List<MarketMover>
    suspend fun getProfile(symbol: String): CompanyProfile?
    suspend fun searchStocks(query: String): List<StockSearchResult>
}
