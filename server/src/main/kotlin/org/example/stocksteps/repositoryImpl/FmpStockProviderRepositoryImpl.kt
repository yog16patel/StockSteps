package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.example.stocksteps.repository.models.FmpQuote
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.models.toStockQuote

class FmpStockProviderRepositoryImpl(
    private val client: HttpClient,
    private val apiKey: String
): StockProviderRepository {
    override suspend fun getQuote(symbol: String): StockQuote? {

        return client.get( "https://financialmodelingprep.com/stable/quote") {
            parameter("symbol", symbol)
            parameter("apikey", apiKey)
        }.body<List<FmpQuote>>().firstOrNull()?.toStockQuote()
    }
}
