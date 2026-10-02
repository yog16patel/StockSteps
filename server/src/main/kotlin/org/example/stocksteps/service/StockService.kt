package service

import model.StockQuote
import repository.StockProviderRepository

class StockService(
    val stockProvider: StockProviderRepository,
) {
    suspend fun getStock(symbol: String): StockQuote? {
        val quote = stockProvider.getQuote(symbol).firstOrNull() ?: return null

        return StockQuote (
            symbol = quote.symbol,
            companyName = quote.name,
            price = quote.price,
            change = quote.change,
            changePercent = quote.changePercentage,
            dayHigh = quote.dayHigh,
            dayLow = quote.dayLow,
        )
    }

}