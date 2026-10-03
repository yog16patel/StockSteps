package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.MarketMover

@Serializable
data class FmpMarketMover(
    val symbol: String,
    val name: String? = null,
    val price: Double,
    val change: Double,
    val changesPercentage: Double,
    val exchange: String? = null
)

fun FmpMarketMover.toMarketMover() = MarketMover(
    symbol = symbol, name = name, price = price, change = change,
    changePercent = changesPercentage, exchange = exchange
)
