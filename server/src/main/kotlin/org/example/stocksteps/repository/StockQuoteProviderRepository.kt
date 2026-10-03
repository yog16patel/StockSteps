package org.example.stocksteps.repository

import org.example.stocksteps.model.StockQuote

interface StockQuoteProviderRepository {
    suspend fun getQuote(symbol: String): StockQuote?
}
