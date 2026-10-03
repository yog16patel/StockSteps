package org.example.stocksteps.repository

import org.example.stocksteps.model.StockSearchResult

interface StockProviderRepository : StockQuoteProviderRepository {
    suspend fun searchStocks(query: String): List<StockSearchResult>
}
