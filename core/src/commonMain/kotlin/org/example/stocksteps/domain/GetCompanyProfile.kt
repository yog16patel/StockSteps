package org.example.stocksteps.domain

class GetCompanyProfile(private val repository: StockRepository) {
    suspend operator fun invoke(symbol: String) = repository.getProfile(symbol.trim().uppercase())
}
