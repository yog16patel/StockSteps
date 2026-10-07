package org.example.stocksteps.domain

import org.example.stocksteps.model.Sparkline

interface SparklineRepository { suspend fun getSparkline(symbol: String): Sparkline }

class GetSparkline(private val repository: SparklineRepository) {
    suspend operator fun invoke(symbol: String) = repository.getSparkline(symbol)
}
