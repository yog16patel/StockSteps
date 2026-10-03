package org.example.stocksteps.service

import java.util.Locale
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.StockProviderRepository

class StockService(
    val stockProvider: StockProviderRepository,
) {
    suspend fun searchStocks(query: String): List<StockSearchResult> =
        stockProvider.searchStocks(query)
            .filter { it.currency == "USD" || it.currency == "CAD" }
            .distinctBy { it.symbol.uppercase(Locale.ROOT) }
            .sortedByDescending { it.symbol.equals(query, ignoreCase = true) }

    suspend fun getStock(symbol: String): StockQuote? {
        return stockProvider.getQuote(symbol)
    }

}
