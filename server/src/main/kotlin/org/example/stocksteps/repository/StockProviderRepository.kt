package org.example.stocksteps.repository

import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialPeriodStatement
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.model.CompanyProfile

interface StockProviderRepository : StockQuoteProviderRepository {
    suspend fun getFundamentals(symbol: String, period: String): org.example.stocksteps.model.CompanyFundamentals =
        org.example.stocksteps.model.CompanyFundamentals(symbol)

    /**
     * Phase 3A: reported statements only — the [statements] requested for one frequency ("annual" | "quarter"),
     * merged into periods exactly as [getFundamentals] builds `history`. Income is always required (it defines the
     * periods); balance-sheet and cash-flow fields stay null when not requested. Default: derived from
     * [getFundamentals] (fixtures; no network).
     */
    suspend fun statementHistory(symbol: String, period: String, statements: Set<Statement>): StatementHistory =
        StatementHistory.from(getFundamentals(symbol, period), period)

    /**
     * Phase 3B-1: the fundamentals the screener uses (no historical annual ratios), priced in the listing's
     * [listingCurrency] when known. Default: the full annual set.
     */
    suspend fun screenerFundamentals(symbol: String, listingCurrency: String?): CompanyFundamentals = getFundamentals(symbol, "annual")

    suspend fun getGainers(): List<MarketMover>
    suspend fun getLosers(): List<MarketMover>
    suspend fun getProfile(symbol: String): CompanyProfile?
    suspend fun searchStocks(query: String): List<StockSearchResult>
}

/** A reported financial statement type (Phase 3A selective loading). */
enum class Statement(val dataset: String) { INCOME("income"), BALANCE("balance"), CASH_FLOW("cashFlow") }

/**
 * Reported periods of one frequency, newest first, built only from the requested statements. Each row keeps its
 * period, fiscal year, period-end date and reported currency; [availability] says per statement whether it loaded
 * (TEMPORARILY_UNAVAILABLE = provider failure, never stored as data; INVALID_VALUE = rows for another symbol).
 */
data class StatementHistory(
    val symbol: String,
    val period: String,
    val rows: List<FinancialPeriodStatement>,
    val availability: Map<Statement, FinancialAvailability>,
    val retrievedAt: String?,
    val source: String,
    /** Phase 3E: at least one statement is an older copy served because the provider failed ([retrievedAt] is its date). */
    val stale: Boolean = false
) {
    companion object {
        fun from(f: CompanyFundamentals, period: String, source: String = "fundamentals") = StatementHistory(f.symbol, period, f.history,
            Statement.entries.associateWith { s -> f.datasets[s.dataset] ?: if (f.history.isEmpty()) FinancialAvailability.MISSING else FinancialAvailability.AVAILABLE },
            f.retrievedAt, source, f.freshness == org.example.stocksteps.earnings.DataFreshness.STALE)
    }
}
