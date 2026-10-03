package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.example.stocksteps.model.StockQuote

@Serializable
data class FinnhubQuote(
    @SerialName("c") val currentPrice: Double,
    @SerialName("d") val change: Double? = null,
    @SerialName("dp") val changePercent: Double? = null,
    @SerialName("h") val dayHigh: Double,
    @SerialName("l") val dayLow: Double,
    @SerialName("o") val open: Double,
    @SerialName("pc") val previousClose: Double,
    @SerialName("t") val timestamp: Long
)

fun FinnhubQuote.toStockQuote(symbol: String) = StockQuote(
    symbol = symbol,
    companyName = null,
    price = currentPrice,
    change = change,
    changePercent = changePercent,
    dayHigh = dayHigh,
    dayLow = dayLow,
    previousClose = previousClose,
    volume = null,
    timestamp = timestamp
)
