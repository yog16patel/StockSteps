package org.example.stocksteps.data

import org.example.stocksteps.domain.BackendInfoRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteBackendInfoRepository(private val api: StockStepsApi) : BackendInfoRepository {
    override suspend fun getBackendInfo() = stockDataRequest { api.getBackendInfo() }
}
