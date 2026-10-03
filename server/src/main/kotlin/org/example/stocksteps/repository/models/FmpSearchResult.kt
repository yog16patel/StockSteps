package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.StockSearchResult

@Serializable
data class FmpSearchResult(
    val symbol: String,
    val name: String,
    val currency: String? = null,
    val exchange: String? = null,
    val exchangeFullName: String? = null
)

fun FmpSearchResult.toStockSearchResult() = StockSearchResult(
    symbol = symbol,
    name = name,
    currency = currency,
    exchange = exchange,
    exchangeFullName = exchangeFullName
)
