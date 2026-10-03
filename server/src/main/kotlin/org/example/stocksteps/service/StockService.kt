package org.example.stocksteps.service

import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.model.CompanyProfile
import java.util.Locale
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.StockProviderRepository

class StockService(
    val stockProvider: StockProviderRepository,
    private val quoteProvider: StockQuoteProviderRepository = stockProvider,
) {
    suspend fun searchStocks(query: String): List<StockSearchResult> =
        stockProvider.searchStocks(query)
            .filter { it.currency == "USD" || it.currency == "CAD" }
            .distinctBy { it.symbol.uppercase(Locale.ROOT) }
            .sortedByDescending { it.symbol.equals(query, ignoreCase = true) }

    suspend fun getProfile(symbol: String): CompanyProfile? = stockProvider.getProfile(symbol)

    suspend fun getStock(symbol: String): StockQuote? {
        return quoteProvider.getQuote(symbol)
    }

}
