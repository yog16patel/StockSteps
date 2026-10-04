package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketMover(
    val symbol: String,
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val exchange: String? = null,
    val volume: Double? = null
)
