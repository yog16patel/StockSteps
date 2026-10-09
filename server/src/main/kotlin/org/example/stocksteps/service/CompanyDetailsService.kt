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
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 4),
    /** When set, the P/E comparison uses the same monthly series as the Valuation screen. */
    private val valuation: ValuationService? = null,
    /**
     * Market status without a provider call (REAL wires `UsMarketCalendar`, the same rules the Markets screen
     * uses). Null keeps the provider's status (cached 60 s).
     */
    private val marketStatus: (suspend () -> MarketStatus)? = null
) {
    suspend fun getDetails(symbol: String): CompanyDetails = coroutineScope {
        val profile = async { section("profile") { stocks.getProfile(symbol) } }
        val quote = async { section("quote") { stocks.getStock(symbol) } }
        val status = async { section("marketStatus") { marketStatus?.invoke() ?: cache.getOrLoad("status", STATUS_TTL) { marketData.getMarketStatus() } } }
        val fundamentals = async { section("fundamentals") { financials.getFundamentals(symbol, "annual") } }
        val history = async { valuation?.let { service -> section("valuation") { service.history(symbol) }.value } }
        val results = listOf(profile, quote, status, fundamentals).map { it.await() }
        CompanyDetails(
            symbol = symbol,
            profile = profile.await().value,
            quote = quote.await().value,
            marketStatus = status.await().value ?: MarketStatus.UNKNOWN,
            fundamentals = fundamentals.await().value?.let { withMonthlyPe(it, history.await()) },
            errors = results.mapNotNull { it.error }
        )
    }

    /**
     * Replaces the provider's annual P/E comparison with the monthly one when it's available, so
     * Company Details and Valuation show the same current P/E and 5-year average.
     */
    private fun withMonthlyPe(fundamentals: CompanyFundamentals, history: ValuationHistory?): CompanyFundamentals {
        if (history == null || history.method != ValuationMethod.MONTHLY_TTM) return fundamentals
        val current = history.currentPe ?: return fundamentals
        val latest = history.observations.lastOrNull()?.date ?: return fundamentals
        val start = "${latest.take(4).toInt() - COMPARISON_YEARS}${latest.drop(4)}"
        val window = history.observations.filter { it.date > start }
        if (window.size < COMPARISON_YEARS * 12 * 0.6) return fundamentals
        val average = window.map { it.pe }.average()
        val pe = fundamentals.valuation.metrics["pe"]
        return fundamentals.copy(valuation = fundamentals.valuation.copy(
            metrics = fundamentals.valuation.metrics + ("pe" to (pe ?: FinancialFact()).copy(value = current, availability = FinancialAvailability.AVAILABLE,
                source = FinancialSource.BACKEND_CALCULATED, basis = FinancialBasis("TTM", history.observations.last().date, currency = history.epsCurrency))),
            historical = fundamentals.valuation.historical + ("pe" to HistoricalComparison(
                observations = emptyList(), average = average, median = null,
                minimum = window.minOf { it.pe }, maximum = window.maxOf { it.pe },
                validCount = COMPARISON_YEARS, requestedYears = COMPARISON_YEARS,
                differencePercent = (current / average - 1) * 100, reliable = true,
                note = "Average of ${window.size} month-end P/E values over $COMPARISON_YEARS years."
            ))
        ))
    }

    private class Section<T>(val value: T?, val error: SnapshotSectionError?)

    private suspend fun <T> section(name: String, load: suspend () -> T?): Section<T> = try {
        Section(load(), null)
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        Section(null, SnapshotSectionError(name, snapshotError(cause)))
    }

    private companion object {
        const val STATUS_TTL = 60_000L
        const val COMPARISON_YEARS = 5
    }
}

/** The regular US session from the exchange calendar (holidays, early closes, pre-market, after-hours); no provider call. */
fun UsMarketCalendar.marketStatusAt(at: java.time.Instant): MarketStatus = when (session(at).status) {
    org.example.stocksteps.model.MarketSessionStatus.OPEN -> MarketStatus.OPEN
    org.example.stocksteps.model.MarketSessionStatus.PRE_MARKET -> MarketStatus.PRE_MARKET
    org.example.stocksteps.model.MarketSessionStatus.AFTER_HOURS -> MarketStatus.AFTER_HOURS
    org.example.stocksteps.model.MarketSessionStatus.CLOSED, org.example.stocksteps.model.MarketSessionStatus.WEEKEND,
    org.example.stocksteps.model.MarketSessionStatus.HOLIDAY -> MarketStatus.CLOSED
    org.example.stocksteps.model.MarketSessionStatus.UNKNOWN -> MarketStatus.UNKNOWN
}
