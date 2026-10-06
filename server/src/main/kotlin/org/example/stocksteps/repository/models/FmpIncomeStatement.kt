package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpIncomeStatement(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val revenue: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val netIncome: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val epsDiluted: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val grossProfit: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val operatingIncome: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val ebit: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val ebitda: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val interestExpense: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val weightedAverageShsOut: Double? = null
)
