package org.example.stocksteps.data

import org.example.stocksteps.domain.MarketSnapshotRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteMarketSnapshotRepository(private val api: StockStepsApi) : MarketSnapshotRepository {
    override suspend fun getSnapshot() = stockDataRequest { api.getMarketSnapshot() }
}
