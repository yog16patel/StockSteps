package org.example.stocksteps.domain

import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

interface StockRepository {
    suspend fun searchStocks(query: String): List<StockSearchResult>
    suspend fun getQuote(symbol: String): StockQuote
    suspend fun getProfile(symbol: String): CompanyProfile
}

class StockDataException(message: String) : Exception(message)
