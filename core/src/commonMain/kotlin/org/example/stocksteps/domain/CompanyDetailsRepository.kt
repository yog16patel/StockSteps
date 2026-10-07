package org.example.stocksteps.domain

import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.CompanyDetails
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.WhyMoving

/** Company Details data from the StockSteps backend (mock or real is decided by the base URL). */
interface CompanyDetailsRepository {
    suspend fun getDetails(symbol: String): CompanyDetails
    suspend fun getChart(symbol: String, range: ChartRange): PriceChart
    suspend fun getWhyMoving(symbol: String): WhyMoving
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
