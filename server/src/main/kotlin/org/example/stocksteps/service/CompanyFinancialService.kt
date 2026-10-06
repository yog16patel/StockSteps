package org.example.stocksteps.service

import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderRepository

class CompanyFinancialService(private val provider: StockProviderRepository) {
    suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals {
        val result = provider.getFundamentals(symbol, period)
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
}
