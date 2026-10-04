package org.example.stocksteps.data

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.domain.StockRepository
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException

class RemoteStockRepository(private val api: StockStepsApi) : StockRepository {
    override suspend fun searchStocks(query: String) = request { api.searchStocks(query) }
    override suspend fun getQuote(symbol: String) = request { api.getQuote(symbol) }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        val message = if (cause is StockStepsApiException) cause.error.message
            else "Could not reach StockSteps. Check the server connection and try again."
        throw StockDataException(message)
    }
}
