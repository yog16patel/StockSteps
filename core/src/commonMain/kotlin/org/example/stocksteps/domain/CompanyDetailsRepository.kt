package org.example.stocksteps.domain

import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.CompanyDetails
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.ValuationHistory
import org.example.stocksteps.model.ValuationMethod
import org.example.stocksteps.model.WhyMoving

/** Company Details data from the StockSteps backend (mock or real is decided by the base URL). */
interface CompanyDetailsRepository {
    suspend fun getDetails(symbol: String): CompanyDetails
    suspend fun getChart(symbol: String, range: ChartRange): PriceChart
    /** Null when the backend has no source-backed explanation (a normal state, not a failure). */
    suspend fun getWhyMoving(symbol: String): WhyMoving?

    /** Historical P/E for the Valuation screen. */
    suspend fun getValuationHistory(symbol: String): ValuationHistory = ValuationHistory(symbol, ValuationMethod.UNAVAILABLE)
}

class GetValuationHistory(private val repository: CompanyDetailsRepository) {
    suspend operator fun invoke(symbol: String) = repository.getValuationHistory(symbol.trim().uppercase())
}

class GetCompanyDetails(private val repository: CompanyDetailsRepository) {
    suspend operator fun invoke(symbol: String) = repository.getDetails(symbol)
}

class GetPriceChart(private val repository: CompanyDetailsRepository) {
    suspend operator fun invoke(symbol: String, range: ChartRange) = repository.getChart(symbol, range)
}

class GetWhyMoving(private val repository: CompanyDetailsRepository) {
    suspend operator fun invoke(symbol: String) = repository.getWhyMoving(symbol)
}
