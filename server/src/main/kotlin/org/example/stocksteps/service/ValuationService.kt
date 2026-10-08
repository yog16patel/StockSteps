package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Historical P/E for the Valuation screen. Preferred: a monthly series from cached daily closes
 * (shared with the price chart) and quarterly diluted EPS (one cached statement request). If that
 * can't be built reliably — no statements, too few months, or prices and statements in different
 * currencies — it falls back to the provider's fiscal-year P/E ratios already loaded for the
 * fundamentals, labelled ANNUAL_REPORTED. Nothing is reconstructed from the current P/E.
 */
class ValuationService(
    private val provider: StockProviderRepository,
    private val charts: PriceChartService,
    private val earnings: QuarterlyEarningsSource,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(),
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }
) {
    suspend fun history(symbol: String): ValuationHistory = cache.getOrLoad("valuation:$symbol", TTL) { build(symbol) }

    private suspend fun build(symbol: String): ValuationHistory {
        val fundamentals = attempt { provider.getFundamentals(symbol, "annual") }
        val profile = attempt { provider.getProfile(symbol) }
        val priceCurrency = profile?.currency?.uppercase()
        val quarters = attempt { earnings.quarterlyEarnings(symbol) }.orEmpty()
        val epsCurrency = quarters.mapNotNull { it.currency }.distinct().singleOrNull()?.uppercase()
        val sameCurrency = epsCurrency != null && (priceCurrency == null || priceCurrency == epsCurrency)
        val closes = if (quarters.size >= 4 && sameCurrency) attempt { charts.getChart(symbol, ChartRange.ALL)?.points }.orEmpty() else emptyList()
        val series = if (closes.isNotEmpty()) ValuationCalculations.monthlyPe(closes, quarters, today()) else null
        val asOf = Instant.now().toString()
        if (series != null && series.observations.size >= MIN_MONTHLY_OBSERVATIONS) {
            return ValuationHistory(
                symbol = symbol,
                method = ValuationMethod.MONTHLY_TTM,
                observations = series.observations,
                currentPe = series.currentPe,
                currentPeAvailability = when {
                    series.currentPe != null -> FinancialAvailability.AVAILABLE
                    series.ttmEps != null -> FinancialAvailability.NON_POSITIVE_DENOMINATOR
                    else -> FinancialAvailability.MISSING
                },
                ttmEps = series.ttmEps,
                epsCurrency = epsCurrency,
                splitAdjusted = series.splitAdjusted,
                asOf = asOf
            )
        }
        // Fallback: provider-reported fiscal-year P/E (already fetched with the fundamentals).
        val pe = fundamentals?.valuation?.metrics?.get("pe")
        val annual = fundamentals?.valuation?.historical?.get("pe")?.observations.orEmpty()
            .mapNotNull { observation -> observation.value?.takeIf { it > 0 }?.let { ValuationObservation(observation.date, it) } }
            .sortedBy { it.date }
        return ValuationHistory(
            symbol = symbol,
            method = if (annual.size >= MIN_ANNUAL_OBSERVATIONS) ValuationMethod.ANNUAL_REPORTED else ValuationMethod.UNAVAILABLE,
            observations = if (annual.size >= MIN_ANNUAL_OBSERVATIONS) annual else emptyList(),
            currentPe = pe?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value?.takeIf { it > 0 },
            currentPeAvailability = when (pe?.availability) {
                FinancialAvailability.AVAILABLE -> if ((pe.value ?: 0.0) > 0) FinancialAvailability.AVAILABLE else FinancialAvailability.NON_POSITIVE_DENOMINATOR
                FinancialAvailability.NON_POSITIVE_DENOMINATOR -> FinancialAvailability.NON_POSITIVE_DENOMINATOR
                else -> FinancialAvailability.MISSING
            },
            epsCurrency = epsCurrency,
            asOf = asOf
        )
    }

    /** A failed input only removes that input; the screen still gets whatever else is available. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }

    companion object {
        /** Statements change quarterly; prices behind the series are cached separately (6h). */
        const val TTL = 21_600_000L
        const val MIN_MONTHLY_OBSERVATIONS = 6
        const val MIN_ANNUAL_OBSERVATIONS = 3
    }
}
