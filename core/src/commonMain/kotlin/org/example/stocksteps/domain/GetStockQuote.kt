package org.example.stocksteps.domain

class GetStockQuote(private val repository: StockRepository) {
    suspend operator fun invoke(symbol: String) = repository.getQuote(symbol.trim().uppercase())
}
