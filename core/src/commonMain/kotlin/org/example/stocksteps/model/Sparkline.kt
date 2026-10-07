package org.example.stocksteps.model

import kotlinx.serialization.Serializable

/**
 * Most recent regular-session intraday closes (oldest first) for a small trend line.
 * `sessionDate` is the exchange-local trading day the points belong to.
 */
@Serializable
data class Sparkline(
    val symbol: String,
    val closes: List<Double>,
    val sessionDate: String? = null
)
