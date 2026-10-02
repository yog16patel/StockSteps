package model

import kotlinx.serialization.Serializable

@Serializable
data class StockQuote(
    val symbol: String,
    val companyName: String?,
    val price: Double?,
    val change: Double?,
    val changePercent: Double?,
    val dayHigh: Double?,
    val dayLow: Double?
)