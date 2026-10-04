package org.example.stocksteps.domain

import org.example.stocksteps.model.MarketSnapshot

interface MarketSnapshotRepository { suspend fun getSnapshot(): MarketSnapshot }
class GetMarketSnapshot(private val repository: MarketSnapshotRepository) {
    suspend operator fun invoke() = repository.getSnapshot()
}
