package org.example.stocksteps.presentation.earnings

import kotlinx.serialization.Serializable

@Serializable internal data object EarningsCenterRoute
@Serializable internal data class EarningsDetailsRoute(val symbol: String)
