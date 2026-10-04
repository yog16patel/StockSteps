package org.example.stocksteps.domain

class SearchStocks(private val repository: StockRepository) {
    suspend operator fun invoke(query: String) =
        if (query.isBlank()) emptyList() else repository.searchStocks(query.trim())
}
