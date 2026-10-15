package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class StockSearchResult(
    val symbol: String,
    val name: String,
    val currency: String? = null,
    val exchange: String? = null,
    val exchangeFullName: String? = null,
    /** Company logo image (5D.5.5C logos): filled by the server from the ticker, no extra provider call; null shows ticker initials. */
    val logoUrl: String? = null
)
