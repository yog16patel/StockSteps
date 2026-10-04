package org.example.stocksteps.service

import org.example.stocksteps.model.ApiError
import org.example.stocksteps.repository.StockProviderException

internal fun snapshotError(cause: Exception): ApiError = when ((cause as? StockProviderException)?.failure) {
    StockProviderException.Failure.TIMEOUT -> ApiError("PROVIDER_TIMEOUT", "Market data took too long to respond.")
    StockProviderException.Failure.RATE_LIMITED -> ApiError("PROVIDER_RATE_LIMITED", "Market data is temporarily unavailable. Try again later.")
    StockProviderException.Failure.INVALID_RESPONSE -> ApiError("INVALID_PROVIDER_RESPONSE", "Market data could not be read.")
    else -> ApiError("PROVIDER_UNAVAILABLE", "Market data is temporarily unavailable.")
}
