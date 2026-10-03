package org.example.stocksteps.service

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.StockProviderRepository

class StockService(
    val stockProvider: StockProviderRepository,
) {
    suspend fun searchStocks(query: String): List<StockSearchResult> =
        stockProvider.searchStocks(query).filter { it.currency == "USD" || it.currency == "CAD" }

    suspend fun getStock(symbol: String): StockQuote? {
        return stockProvider.getQuote(symbol)
    }

}
