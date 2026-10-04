package org.example.stocksteps.presentation.stocksearch

import kotlinx.serialization.Serializable

@Serializable
internal data class StockSearchRoute(val initialSymbol: String? = null)
