package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*

@Serializable
data class FmpSnapshotMover(
    val symbol: String = "",
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changesPercentage: Double? = null,
    val changePercentage: Double? = null,
    val volume: Double? = null,
    val exchange: String? = null
)

@Serializable
data class FmpMarketHours(val exchange: String? = null, val isMarketOpen: Boolean? = null)

internal fun Double?.finiteValue(nonNegative: Boolean = false): Double? =
    this?.takeIf { it.isFinite() && (!nonNegative || it >= 0) }

fun FmpSnapshotMover.toSnapshotMover(): MarketMover? {
    val ticker = symbol.trim().uppercase(java.util.Locale.ROOT)
    if (!ticker.matches(Regex("[A-Z0-9][A-Z0-9.-]{0,19}"))) return null
    return MarketMover(
        symbol = ticker,
        name = name?.takeIf { it.isNotBlank() },
        logoUrl = fmpLogoUrl(ticker),
        price = price.finiteValue(nonNegative = true),
        change = change.finiteValue(),
        changePercent = (changesPercentage ?: changePercentage).finiteValue(),
        exchange = exchange,
        volume = volume.finiteValue(nonNegative = true)
    )
}

fun FmpMarketHours.toMarketStatus(): MarketStatus = when (isMarketOpen) {
    true -> MarketStatus.OPEN
    false -> MarketStatus.CLOSED
    null -> MarketStatus.UNKNOWN
}

fun FmpQuote.toMarketIndex(name: String): MarketIndex = MarketIndex(
    symbol = symbol,
    name = name,
    price = price.finiteValue(nonNegative = true),
    change = change.finiteValue(),
    changePercent = changePercentage.finiteValue()
)

/**
 * FMP's public logo image for a ticker: the same CDN path its company-profile `image`
 * field returns, so movers get logos without one profile request per row. Unknown
 * tickers return an error image response, which clients treat as "no logo".
 */
internal fun fmpLogoUrl(ticker: String): String = "https://images.financialmodelingprep.com/symbol/$ticker.png"
