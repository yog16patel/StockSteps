package org.example.stocksteps.presentation.portfolio

import kotlinx.serialization.Serializable

@Serializable internal data object PortfolioSearchRoute
@Serializable internal data object PortfolioRoute
@Serializable internal data class HoldingDetailsRoute(val accountId: String, val symbol: String)
@Serializable internal data class PortfolioEntryRoute(val symbol: String = "", val name: String? = null, val exchange: String? = null, val currency: String? = null)
