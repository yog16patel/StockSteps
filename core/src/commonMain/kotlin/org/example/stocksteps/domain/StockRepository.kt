package org.example.stocksteps.domain

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

interface StockRepository {
    suspend fun searchStocks(query: String): List<StockSearchResult>
    suspend fun getQuote(symbol: String): StockQuote
}

class StockDataException(message: String) : Exception(message)
