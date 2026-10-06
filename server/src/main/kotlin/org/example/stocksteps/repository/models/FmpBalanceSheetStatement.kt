package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpBalanceSheetStatement(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val cashAndCashEquivalents: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalDebt: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalAssets: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalLiabilities: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalStockholdersEquity: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalCurrentAssets: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val totalCurrentLiabilities: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val netDebt: Double? = null
)
