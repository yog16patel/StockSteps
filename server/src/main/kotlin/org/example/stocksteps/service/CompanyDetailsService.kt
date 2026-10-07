package org.example.stocksteps.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.MarketDataProvider
import kotlin.coroutines.cancellation.CancellationException

/**
 * Aggregates the core Company Details data in one response. Every part reuses an existing
 * cached service (profile 24h, quote 30s, fundamentals 5min–24h); market status gets its own
 * 60s cache so opening a stock never refreshes the whole market snapshot.
 */
class CompanyDetailsService(
    private val stocks: StockService,
    private val financials: CompanyFinancialService,
    private val marketData: MarketDataProvider,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 4)
) {
    suspend fun getDetails(symbol: String): CompanyDetails = coroutineScope {
        val profile = async { section("profile") { stocks.getProfile(symbol) } }
        val quote = async { section("quote") { stocks.getStock(symbol) } }
        val status = async { section("marketStatus") { cache.getOrLoad("status", STATUS_TTL) { marketData.getMarketStatus() } } }
        val fundamentals = async { section("fundamentals") { financials.getFundamentals(symbol, "annual") } }
        val results = listOf(profile, quote, status, fundamentals).map { it.await() }
        CompanyDetails(
            symbol = symbol,
            profile = profile.await().value,
            quote = quote.await().value,
            marketStatus = status.await().value ?: MarketStatus.UNKNOWN,
            fundamentals = fundamentals.await().value,
            errors = results.mapNotNull { it.error }
        )
    }

    private class Section<T>(val value: T?, val error: SnapshotSectionError?)

    private suspend fun <T> section(name: String, load: suspend () -> T?): Section<T> = try {
        Section(load(), null)
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        Section(null, SnapshotSectionError(name, snapshotError(cause)))
    }

    private companion object { const val STATUS_TTL = 60_000L }
}
