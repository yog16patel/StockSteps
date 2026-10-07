package org.example.stocksteps.data

import org.example.stocksteps.domain.SparklineRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteSparklineRepository(private val api: StockStepsApi) : SparklineRepository {
    override suspend fun getSparkline(symbol: String) = stockDataRequest { api.getSparkline(symbol) }
}
