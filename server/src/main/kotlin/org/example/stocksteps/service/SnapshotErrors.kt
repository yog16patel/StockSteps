package org.example.stocksteps.service

import org.example.stocksteps.model.ApiError
import org.example.stocksteps.repository.StockProviderException

/**
 * Phase 7A: the one mapping from a provider failure to the error clients see (routes through `configureApiErrors`, and section errors in
 * Markets, Company Details and the snapshot). HTTP statuses are unchanged from before; only the codes and messages say why:
 * - a plan restriction (402/403) is `PROVIDER_RESTRICTED`, "trying again won't help" (the pattern of Discover's `SCREENER_PROVIDER_RESTRICTED`);
 * - this server's own daily allowance is `PROVIDER_DAILY_LIMIT_REACHED`;
 * - a provider 429 stays `PROVIDER_RATE_LIMITED` (it doesn't say which limit was hit), everything else temporary stays as it was.
 * Messages never contain provider URLs, bodies, statuses or keys.
 */
object ProviderErrors {
    const val RESTRICTED = "PROVIDER_RESTRICTED"
    const val DAILY_LIMIT = "PROVIDER_DAILY_LIMIT_REACHED"
    const val RESTRICTED_MESSAGE = "This market data isn't included in this server's market data plan. Trying again won't help."
    const val DAILY_LIMIT_MESSAGE = "This server has used today's market data allowance. Market data will be available again after the daily reset."

    /** HTTP status (as before Phase 7A) and the client error for [cause]. */
    fun of(cause: StockProviderException): Pair<Int, ApiError> = when {
        cause.dailyLimitReached -> 503 to ApiError(DAILY_LIMIT, DAILY_LIMIT_MESSAGE)
        cause.restricted -> 502 to ApiError(RESTRICTED, RESTRICTED_MESSAGE)
        else -> when (cause.failure) {
            StockProviderException.Failure.TIMEOUT -> 504 to ApiError("PROVIDER_TIMEOUT", "Market data took too long to respond.")
            StockProviderException.Failure.RATE_LIMITED -> 503 to ApiError("PROVIDER_RATE_LIMITED", "Market data is temporarily unavailable. Try again later.")
            StockProviderException.Failure.UNAVAILABLE -> 502 to ApiError("PROVIDER_UNAVAILABLE", "Market data is temporarily unavailable.")
            StockProviderException.Failure.INVALID_RESPONSE -> 502 to ApiError("INVALID_PROVIDER_RESPONSE", "Market data could not be read.")
        }
    }
}

internal fun snapshotError(cause: Exception): ApiError =
    (cause as? StockProviderException)?.let { ProviderErrors.of(it).second } ?: ApiError("PROVIDER_UNAVAILABLE", "Market data is temporarily unavailable.")
