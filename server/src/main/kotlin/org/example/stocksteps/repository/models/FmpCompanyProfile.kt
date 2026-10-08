package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.CompanyProfile

@Serializable
data class FmpCompanyProfile(
    val symbol: String,
    val companyName: String? = null,
    val description: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val website: String? = null,
    val country: String? = null,
    val currency: String? = null,
    val exchange: String? = null,
    val image: String? = null,
    val isEtf: Boolean? = null,
    val isFund: Boolean? = null
)

fun FmpCompanyProfile.toCompanyProfile() = CompanyProfile(
    symbol = symbol,
    companyName = companyName,
    description = description,
    sector = sector,
    industry = industry,
    website = website,
    country = country,
    currency = currency,
    exchange = exchange,
    logoUrl = image,
    isEtf = if (isEtf == null && isFund == null) null else isEtf == true || isFund == true
)
