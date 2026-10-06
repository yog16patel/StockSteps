package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpKeyMetricsTtm(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val enterpriseValueTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val evToEBITDATTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val returnOnEquityTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val returnOnAssetsTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val returnOnInvestedCapitalTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val netDebtToEBITDATTM: Double? = null
)
