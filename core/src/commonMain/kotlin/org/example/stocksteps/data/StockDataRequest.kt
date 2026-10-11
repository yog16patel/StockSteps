package org.example.stocksteps.data

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.network.StockStepsApiException

internal suspend fun <T> stockDataRequest(block: suspend () -> T): T = try {
    block()
} catch (cause: Exception) {
    if (cause is CancellationException) throw cause
    throw if (cause is StockStepsApiException) StockDataException(cause.error.message, cause.status, cause.error.code)
        else StockDataException("Could not reach StockSteps. Check the server connection and try again.")
}
