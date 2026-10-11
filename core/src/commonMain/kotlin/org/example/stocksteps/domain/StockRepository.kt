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

/**
 * A failed StockSteps request with a user-safe [message]. [status] and [code] are the backend's HTTP status and error code; both are null when
 * the backend could not be reached (connection failure), so screens can tell "no data" apart from "no connection" (Phase 4C.2).
 */
class StockDataException(message: String, val status: Int? = null, val code: String? = null) : Exception(message)
