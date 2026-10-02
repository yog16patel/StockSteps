package repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import repository.models.FmpQuote
import repository.StockProviderRepository

class FmpStockProviderRepositoryImpl(
    private val client: HttpClient,
    private val apiKey: String
): StockProviderRepository {
    override suspend fun getQuote(symbol: String): List<FmpQuote> {

        return client.get( "https://financialmodelingprep.com/stable/quote") {
            parameter("symbol", symbol)
            parameter("apikey", apiKey)
        }.body()
    }
}