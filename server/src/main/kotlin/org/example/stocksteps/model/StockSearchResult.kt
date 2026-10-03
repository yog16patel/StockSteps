package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class StockSearchResult(
    val symbol: String,
    val name: String,
    val currency: String? = null,
    val exchange: String? = null,
    val exchangeFullName: String? = null
)
