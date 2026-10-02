package repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpQuote(
    val symbol: String,
    val name: String? = null,
    val price: Double? = null,
    val changePercentage: Double? = null,
    val change: Double? = null,
    val volume: Long? = null,
    val dayLow: Double? = null,
    val dayHigh: Double? = null,
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
    val marketCap: Long? = null,
    val priceAvg50: Double? = null,
    val priceAvg200: Double? = null,
    val exchange: String? = null,
    val open: Double? = null,
    val previousClose: Double? = null,
    val timestamp: Long? = null
)