package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable

@Serializable
data class FmpSharesFloat(
    val symbol: String = "",
    val date: String? = null,
    @Serializable(with = FmpFiscalYearSerializer::class)
    val fiscalYear: String? = null,
    val period: String? = null,
    val reportedCurrency: String? = null,
    val acceptedDate: String? = null,
    @Serializable(with = FmpFinancialNumberSerializer::class)
    val outstandingShares: Double? = null
)
