package org.example.stocksteps.service

import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.MarketMover
import java.util.Locale
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.repository.StockProviderException

class StockService(
    val stockProvider: StockProviderRepository,
    private val quoteProvider: StockQuoteProviderRepository = stockProvider,
    private val profileCache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512, name = "profiles"),
    /** Search results by normalized query (reference data; failures aren't cached). */
    private val searchCache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512, name = "search")
) {
    private class ProfileOutcome(val profile: CompanyProfile?, val failure: StockProviderException?)

    /** Equivalent queries ("Apple", " apple ") share one cached provider result for [FinancialCachePolicy.SEARCH]. */
    suspend fun searchStocks(query: String): List<StockSearchResult> =
        searchCache.getOrLoad(query.trim().lowercase(Locale.ROOT), FinancialCachePolicy.SEARCH) { search(query.trim()) }

    private suspend fun search(query: String): List<StockSearchResult> =
        stockProvider.searchStocks(query)
            .filter { it.currency.equals("USD", ignoreCase = true) &&
                it.exchange?.uppercase(Locale.ROOT) in US_EXCHANGES
            }
            .distinctBy { it.symbol.uppercase(Locale.ROOT) }
            .sortedByDescending { it.symbol.equals(query, ignoreCase = true) }

    private companion object {
        const val PROFILE_TTL = 86_400_000L
        val US_EXCHANGES = setOf("NASDAQ", "NYSE", "AMEX", "NYSEARCA", "NYSEAMERICAN", "BATS", "CBOE", "OTC", "OTCQX", "OTCQB", "PNK")
    }

    suspend fun getGainers(): List<MarketMover> = stockProvider.getGainers()
        .sortedByDescending { it.changePercent }

    suspend fun getLosers(): List<MarketMover> = stockProvider.getLosers()
        .sortedBy { it.changePercent }

    /**
     * Profiles change rarely and Home requests them for mover logos, so they are cached for a
     * day (including "not found"); provider rate limits cool down for 10 minutes.
     */
    suspend fun getProfile(symbol: String): CompanyProfile? {
        val outcome = profileCache.getOrLoad(symbol, PROFILE_TTL, resultTtl = { providerCooldown(it.failure, PROFILE_TTL) }) {
            try {
                ProfileOutcome(stockProvider.getProfile(symbol), null)
            } catch (cause: StockProviderException) {
                ProfileOutcome(null, cause)
            }
        }
        outcome.failure?.let { throw it }
        return outcome.profile
    }

    suspend fun getStock(symbol: String): StockQuote? {
        return quoteProvider.getQuote(symbol)
    }

}
