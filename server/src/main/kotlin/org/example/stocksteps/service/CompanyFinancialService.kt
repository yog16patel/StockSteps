package org.example.stocksteps.service

import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderRepository

class CompanyFinancialService(private val provider: StockProviderRepository) {
    suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals = clean(provider.getFundamentals(symbol, period))

    /** Phase 3B-1: the screener's dataset set (see [StockProviderRepository.screenerFundamentals]). */
    suspend fun screenerFundamentals(symbol: String, listingCurrency: String?): CompanyFundamentals =
        clean(provider.screenerFundamentals(symbol, listingCurrency))

    private fun clean(result: CompanyFundamentals): CompanyFundamentals {
        // Diagnostics belong in server logs; clients receive numeric facts and neutral availability.
        fun clean(values: Map<String, FinancialFact>) = values.mapValues { (_, fact) -> fact.copy(note = null) }
        return result.copy(
            financials = result.financials.let {
                it.copy(growth = clean(it.growth), profitability = clean(it.profitability),
                    financialHealth = clean(it.financialHealth), cashFlow = clean(it.cashFlow),
                    shareholderReturns = clean(it.shareholderReturns))
            },
            valuation = result.valuation.copy(metrics = clean(result.valuation.metrics),
                historical = result.valuation.historical.mapValues { (_, history) -> history.copy(note = null) }),
            warnings = emptyList()
        )
    }

    /** Phase 3A: only the requested reported statements (see [StockProviderRepository.statementHistory]). */
    suspend fun statementHistory(symbol: String, period: String, statements: Set<org.example.stocksteps.repository.Statement>) =
        provider.statementHistory(symbol, period, statements)
}
