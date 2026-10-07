package org.example.stocksteps.service

import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException

/**
 * Cached intraday sparklines. Home requests a few symbols per visit, so successes are
 * reused for five minutes, provider access denials for an hour and other failures 30s.
 */
class SparklineService(
    private val provider: PriceHistoryProvider,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256)
) {
    private class Outcome(val sparkline: Sparkline?, val failure: StockProviderException?)

    suspend fun getSparkline(symbol: String): Sparkline? {
        val outcome = cache.getOrLoad(symbol, SUCCESS_TTL, resultTtl = { providerCooldown(it.failure, SUCCESS_TTL) }) {
            try {
                Outcome(provider.getIntradaySparkline(symbol).takeIf { it.closes.size >= MIN_POINTS }, null)
            } catch (cause: StockProviderException) {
                Outcome(null, cause)
            }
        }
        outcome.failure?.let { throw it }
        return outcome.sparkline
    }

    private companion object {
        const val SUCCESS_TTL = 300_000L
        const val MIN_POINTS = 2
    }
}

/**
 * How long to remember a provider outcome: [success] for data, an hour for access denials,
 * 10 minutes for rate limits (retrying sooner only burns the remaining quota), else 30s.
 */
internal fun providerCooldown(failure: StockProviderException?, success: Long): Long = when {
    failure == null -> success
    failure.upstreamStatus == 402 || failure.upstreamStatus == 403 -> FinancialCachePolicy.ACCESS_COOLDOWN
    failure.failure == StockProviderException.Failure.RATE_LIMITED -> RATE_LIMIT_COOLDOWN
    else -> FinancialCachePolicy.FAILURE
}

private const val RATE_LIMIT_COOLDOWN = 600_000L
