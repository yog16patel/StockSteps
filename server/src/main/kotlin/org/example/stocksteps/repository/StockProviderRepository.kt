package org.example.stocksteps.repository

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

interface StockProviderRepository {
    suspend fun searchStocks(query: String): List<StockSearchResult>
    suspend fun getQuote(symbol: String): StockQuote?
}
