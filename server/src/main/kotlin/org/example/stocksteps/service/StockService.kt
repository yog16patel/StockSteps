package org.example.stocksteps.service

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.StockProviderRepository

class StockService(
    val stockProvider: StockProviderRepository,
) {
    suspend fun getStock(symbol: String): StockQuote? {
        return stockProvider.getQuote(symbol)
    }

}
