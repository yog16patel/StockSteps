package org.example.stocksteps.service

import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException
import java.time.LocalDate

/**
 * Price charts for every range from at most two provider datasets per symbol: today's 5-minute
 * bars for 1D, and one daily history (cached 6h) that is sliced locally for 1W…ALL.
 */
class PriceChartService(
    private val provider: PriceHistoryProvider,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256)
) {
    private class Outcome(val points: List<PricePoint>, val failure: StockProviderException?)

    suspend fun getChart(symbol: String, range: ChartRange): PriceChart? {
        val intraday = range == ChartRange.ONE_DAY
        val key = if (intraday) "intraday:$symbol" else "daily:$symbol"
        val outcome = cache.getOrLoad(key, if (intraday) INTRADAY_TTL else DAILY_TTL,
            resultTtl = { providerCooldown(it.failure, if (intraday) INTRADAY_TTL else DAILY_TTL) }) {
            try {
                Outcome(if (intraday) provider.getIntradayPoints(symbol) else provider.getDailyCloses(symbol), null)
            } catch (cause: StockProviderException) {
                Outcome(emptyList(), cause)
            }
        }
        outcome.failure?.let { throw it }
        val points = if (intraday) outcome.points else slice(outcome.points, range)
        return points.takeIf { it.size >= MIN_POINTS }?.let {
            PriceChart(symbol, range, it, if (intraday) "5min" else "1day")
        }
    }

    /** Shared daily observations for dated portfolio valuation; uses the same provider cache. */
    suspend fun getDailyCloses(symbol: String): List<PricePoint> {
        val outcome = cache.getOrLoad("daily:$symbol", DAILY_TTL,
            resultTtl = { providerCooldown(it.failure, DAILY_TTL) }) {
            try { Outcome(provider.getDailyCloses(symbol), null) }
            catch (cause: StockProviderException) { Outcome(emptyList(), cause) }
        }
        outcome.failure?.let { throw it }
        return outcome.points
    }

    /** Daily points (oldest first) within the range, measured back from the latest point. */
    internal fun slice(points: List<PricePoint>, range: ChartRange): List<PricePoint> {
        val latest = points.lastOrNull()?.time?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return points
        val from = when (range) {
            ChartRange.ONE_DAY, ChartRange.ALL -> return points
            ChartRange.ONE_WEEK -> latest.minusWeeks(1)
            ChartRange.ONE_MONTH -> latest.minusMonths(1)
            ChartRange.THREE_MONTHS -> latest.minusMonths(3)
            ChartRange.ONE_YEAR -> latest.minusYears(1)
            ChartRange.FIVE_YEARS -> latest.minusYears(5)
        }
        return points.filter { point -> runCatching { !LocalDate.parse(point.time.take(10)).isBefore(from) }.getOrDefault(false) }
    }

    private companion object {
        const val INTRADAY_TTL = 300_000L
        const val DAILY_TTL = 21_600_000L
        const val MIN_POINTS = 2
    }
}
