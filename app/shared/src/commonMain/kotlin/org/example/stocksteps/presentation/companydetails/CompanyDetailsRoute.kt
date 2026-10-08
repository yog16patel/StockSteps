package org.example.stocksteps.presentation.companydetails

import kotlinx.serialization.Serializable

/**
 * The one Company Details destination. The provider symbol (e.g. AAPL, SHOP.TO) is the
 * canonical identifier; everything else is loaded from the backend.
 */
@Serializable
internal data class CompanyDetailsRoute(val symbol: String)

/** Full statements and valuation history (Annual/Quarterly), opened from "See Financials/Valuation". */
@Serializable
internal data class CompanyFinancialsRoute(val symbol: String)

/** All recent company news, opened from "View All News". */
@Serializable
internal data class CompanyNewsRoute(val symbol: String)

/** P/E history, ranges and other valuation ratios, opened from Company Details "See Valuation". */
@Serializable
internal data class CompanyValuationRoute(val symbol: String)
