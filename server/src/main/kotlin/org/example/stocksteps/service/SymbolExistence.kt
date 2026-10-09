package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.repository.StockProviderException

/**
 * Phase 4A existence gate: before an expensive per-symbol bundle (fundamentals ≈ 13 FMP requests, Company Details ≈ 15–17), confirm the
 * symbol exists with the cheapest evidence already cached by the app: the company profile (24 h, shared with Company Details, so a real
 * symbol costs nothing extra), else a quote (a listing without a profile). Only a successful "no profile and no quote" answer is
 * negative-cached ([negativeTtl]); provider failures return [Status.UNKNOWN] and are never remembered as "not found". Any syntactically
 * valid US/Canadian symbol (dots, hyphens, `.TO`) is accepted when the provider knows it — not only the screener universe, so new listings work.
 */
class SymbolExistence(
    private val stocks: StockService,
    private val negative: CompanyFinancialCache = CompanyFinancialCache(capacity = 20_000, name = "symbol-not-found"),
    private val negativeTtl: Long = 86_400_000L,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    enum class Status { EXISTS, NOT_FOUND, UNKNOWN }

    suspend fun check(symbol: String): Status {
        if (negative.lastValue<Boolean>(symbol, negativeTtl) == true) { meter.event("symbol.notFound.cached"); return Status.NOT_FOUND }
        try {
            if (stocks.getProfile(symbol) != null) return Status.EXISTS
            if (stocks.getStock(symbol) != null) return Status.EXISTS
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            if (cause is StockProviderException) { meter.event("symbol.check.unavailable"); return Status.UNKNOWN }
            throw cause
        }
        negative.getOrLoad(symbol, negativeTtl) { true }
        meter.event("symbol.notFound")
        return Status.NOT_FOUND
    }
}
