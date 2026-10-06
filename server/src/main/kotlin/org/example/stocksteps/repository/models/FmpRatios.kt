package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpRatios(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToEarningsRatio: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToSalesRatio: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToBookRatio: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val priceToFreeCashFlowRatio: Double? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val enterpriseValueMultiple: Double? = null
)
