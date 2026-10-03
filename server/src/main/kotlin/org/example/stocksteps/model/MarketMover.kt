package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketMover(
    val symbol: String,
    val name: String? = null,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val exchange: String? = null
)
