package org.example.stocksteps.repository.models

import org.example.stocksteps.model.StockQuote

fun FmpQuote.toStockQuote(): StockQuote = StockQuote(
    symbol = symbol,
    companyName = name,
    price = price,
    change = change,
    changePercent = changePercentage,
    previousClose = previousClose,
    dayHigh = dayHigh,
    dayLow = dayLow,
    // FMP can return fractional volume. Truncate only valid, representable values.
    volume = volume?.takeIf {
        it.isFinite() && it >= 0.0 && it < Long.MAX_VALUE.toDouble()
    }?.toLong(),
    timestamp = timestamp,
    marketCap = marketCap?.takeIf { it >= 0 },
    open = open?.takeIf { it.isFinite() && it > 0 },
    yearHigh = yearHigh?.takeIf { it.isFinite() && it > 0 },
    yearLow = yearLow?.takeIf { it.isFinite() && it > 0 }
)
