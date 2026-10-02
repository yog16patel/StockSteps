package repository

import repository.models.FmpQuote

interface StockProviderRepository {
    suspend fun getQuote(symbol: String): List<FmpQuote>
}