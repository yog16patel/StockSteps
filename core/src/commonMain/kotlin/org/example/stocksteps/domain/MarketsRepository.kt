package org.example.stocksteps.domain

import org.example.stocksteps.model.MarketsOverview

/** The Markets dashboard from the StockSteps backend (mock or real is decided by the base URL). */
interface MarketsRepository {
    suspend fun getOverview(): MarketsOverview
}

class GetMarketsOverview(private val repository: MarketsRepository) {
    suspend operator fun invoke() = repository.getOverview()
}
