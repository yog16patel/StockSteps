package org.example.stocksteps.repository

import org.example.stocksteps.model.Sparkline

interface PriceHistoryProvider {
    /** Latest session's intraday closes, oldest first; empty when the provider has none. */
    suspend fun getIntradaySparkline(symbol: String): Sparkline
}
