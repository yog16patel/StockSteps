package org.example.stocksteps.data

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.network.StockStepsApiException

internal suspend fun <T> stockDataRequest(block: suspend () -> T): T = try {
    block()
} catch (cause: Exception) {
    if (cause is CancellationException) throw cause
    val message = if (cause is StockStepsApiException) cause.error.message
        else "Could not reach StockSteps. Check the server connection and try again."
    throw StockDataException(message)
}
