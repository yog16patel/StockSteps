package org.example.stocksteps.data

import org.example.stocksteps.domain.CompanyDetailsRepository
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.network.StockStepsApi

class RemoteCompanyDetailsRepository(private val api: StockStepsApi) : CompanyDetailsRepository {
    override suspend fun getDetails(symbol: String) = stockDataRequest { api.getCompanyDetails(symbol) }
    override suspend fun getChart(symbol: String, range: ChartRange) = stockDataRequest { api.getPriceChart(symbol, range) }
    override suspend fun getWhyMoving(symbol: String) = stockDataRequest { api.getWhyMoving(symbol) }
}
