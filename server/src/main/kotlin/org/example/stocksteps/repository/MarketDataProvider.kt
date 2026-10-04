package org.example.stocksteps.repository

import org.example.stocksteps.model.*

interface MarketDataProvider {
    suspend fun getMarketIndices(): List<MarketIndex>
    suspend fun getGainers(): List<MarketMover>
    suspend fun getLosers(): List<MarketMover>
    suspend fun getMostActive(): List<MarketMover>
    suspend fun getMarketStatus(): MarketStatus
}
