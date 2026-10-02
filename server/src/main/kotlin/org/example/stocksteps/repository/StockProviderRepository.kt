package org.example.stocksteps.repository

import org.example.stocksteps.model.StockQuote

interface StockProviderRepository {
    suspend fun getQuote(symbol: String): StockQuote?
}
