package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpCashFlowStatement(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val operatingCashFlow: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val netCashProvidedByOperatingActivities: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val capitalExpenditure: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val freeCashFlow: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val commonStockRepurchased: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val commonDividendsPaid: Double? = null
)
