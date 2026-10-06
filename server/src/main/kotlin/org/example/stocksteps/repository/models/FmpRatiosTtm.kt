package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpRatiosTtm(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToEarningsRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToEarningsGrowthRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToSalesRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToBookRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToFreeCashFlowRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val enterpriseValueTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val enterpriseValueMultipleTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val grossProfitMarginTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val operatingProfitMarginTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val netProfitMarginTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val debtToEquityRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val currentRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val quickRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val interestCoverageRatioTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val dividendYieldTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val dividendPerShareTTM: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val dividendPayoutRatioTTM: Double? = null
)
