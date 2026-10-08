package org.example.stocksteps.data

import org.example.stocksteps.domain.CompanyDetailsRepository
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.WhyMoving
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException

class RemoteCompanyDetailsRepository(private val api: StockStepsApi) : CompanyDetailsRepository {
    override suspend fun getDetails(symbol: String) = stockDataRequest { api.getCompanyDetails(symbol) }
    override suspend fun getChart(symbol: String, range: ChartRange) = stockDataRequest { api.getPriceChart(symbol, range) }
    override suspend fun getValuationHistory(symbol: String) = stockDataRequest { api.getValuationHistory(symbol) }
    override suspend fun getWhyMoving(symbol: String): WhyMoving? = stockDataRequest {
        try {
            api.getWhyMoving(symbol)
        } catch (cause: StockStepsApiException) {
            if (cause.error.code == WHY_MOVING_UNAVAILABLE) null else throw cause
        }
    }

    private companion object { const val WHY_MOVING_UNAVAILABLE = "WHY_MOVING_UNAVAILABLE" }
}
