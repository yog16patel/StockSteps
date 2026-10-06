package org.example.stocksteps.presentation.stocksearch

import kotlinx.serialization.Serializable

@Serializable
internal data class StockSearchRoute(
    val initialSymbol: String? = null,
    val name: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    val exchangeFullName: String? = null
)
