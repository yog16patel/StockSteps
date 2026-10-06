package org.example.stocksteps.service

import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.MarketMover
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
            .filter { it.currency.equals("USD", ignoreCase = true) &&
                it.exchange?.uppercase(Locale.ROOT) in US_EXCHANGES
            }
            .distinctBy { it.symbol.uppercase(Locale.ROOT) }
            .sortedByDescending { it.symbol.equals(query, ignoreCase = true) }

    private companion object {
        val US_EXCHANGES = setOf("NASDAQ", "NYSE", "AMEX", "NYSEARCA", "NYSEAMERICAN", "BATS", "CBOE", "OTC", "OTCQX", "OTCQB", "PNK")
    }

    suspend fun getGainers(): List<MarketMover> = stockProvider.getGainers()
        .sortedByDescending { it.changePercent }

    suspend fun getLosers(): List<MarketMover> = stockProvider.getLosers()
        .sortedBy { it.changePercent }

    suspend fun getProfile(symbol: String): CompanyProfile? = stockProvider.getProfile(symbol)

    suspend fun getStock(symbol: String): StockQuote? {
        return quoteProvider.getQuote(symbol)
    }

}
