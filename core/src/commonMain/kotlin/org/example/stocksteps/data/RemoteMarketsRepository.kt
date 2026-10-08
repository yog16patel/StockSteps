package org.example.stocksteps.data

import org.example.stocksteps.domain.MarketsRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteMarketsRepository(private val api: StockStepsApi) : MarketsRepository {
    override suspend fun getOverview() = stockDataRequest { api.getMarketsOverview() }
}
