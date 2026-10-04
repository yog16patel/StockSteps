package org.example.stocksteps.data

import org.example.stocksteps.domain.StockRepository
import org.example.stocksteps.network.StockStepsApi

class RemoteStockRepository(private val api: StockStepsApi) : StockRepository {
    override suspend fun searchStocks(query: String) = stockDataRequest { api.searchStocks(query) }
    override suspend fun getProfile(symbol: String) = stockDataRequest { api.getProfile(symbol) }
    override suspend fun getQuote(symbol: String) = stockDataRequest { api.getQuote(symbol) }

}
