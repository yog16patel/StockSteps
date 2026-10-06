package org.example.stocksteps.domain

class GetCompanyFundamentals(private val repository: StockRepository) {
    suspend operator fun invoke(symbol: String, period: String = "annual") = repository.getFundamentals(symbol.trim().uppercase(), period)
}
