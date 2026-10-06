package org.example.stocksteps.domain

import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

interface StockRepository {
    suspend fun getFundamentals(symbol: String, period: String = "annual"): org.example.stocksteps.model.CompanyFundamentals =
        org.example.stocksteps.model.CompanyFundamentals(symbol)
    suspend fun searchStocks(query: String): List<StockSearchResult>
    suspend fun getQuote(symbol: String): StockQuote
    suspend fun getProfile(symbol: String): CompanyProfile
}

class StockDataException(message: String) : Exception(message)
