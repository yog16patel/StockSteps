package org.example.stocksteps.repository

import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.Sparkline

interface PriceHistoryProvider {
    /** Latest session's intraday closes, oldest first; empty when the provider has none. */
    suspend fun getIntradaySparkline(symbol: String): Sparkline

    /** Latest session's intraday points (oldest first) for the 1D chart. */
    suspend fun getIntradayPoints(symbol: String): List<PricePoint>

    /** Daily closes (oldest first), as much history as the provider returns in one call. */
    suspend fun getDailyCloses(symbol: String): List<PricePoint>
}
