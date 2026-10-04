package org.example.stocksteps.data

import org.example.stocksteps.domain.MarketRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteMarketRepository(private val api: StockStepsApi) : MarketRepository {
    override suspend fun getGainers() = stockDataRequest { api.getGainers() }
    override suspend fun getLosers() = stockDataRequest { api.getLosers() }
    override suspend fun getNews() = stockDataRequest { api.getNews() }
}
