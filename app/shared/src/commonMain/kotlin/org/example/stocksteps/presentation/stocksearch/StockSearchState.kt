package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

internal data class StockSearchState(
    val query: String = "",
    val results: List<StockSearchResult> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val selected: StockSearchResult? = null,
    val quote: StockQuote? = null,
    val quoteLoading: Boolean = false,
    val quoteError: String? = null
)
