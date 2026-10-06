package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class StockQuote(
    val symbol: String,
    val companyName: String?,
    val price: Double?,
    val change: Double?,
    val changePercent: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val previousClose: Double? = null,
    val volume: Long? = null,
    val timestamp: Long? = null,
    val marketCap: Long? = null
)
