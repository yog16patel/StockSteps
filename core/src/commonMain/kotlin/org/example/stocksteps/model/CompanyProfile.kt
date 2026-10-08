package org.example.stocksteps.model

import kotlinx.serialization.Serializable

@Serializable
data class CompanyProfile(
    val symbol: String,
    val companyName: String? = null,
    val description: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val website: String? = null,
    val country: String? = null,
    val currency: String? = null,
    val exchange: String? = null,
    val logoUrl: String? = null,
    /** True for ETFs and funds; null when the provider didn't say (never inferred from the name). */
    val isEtf: Boolean? = null
)
