package org.example.stocksteps.presentation.practice

import kotlinx.serialization.Serializable

/** The Practice Portfolio (simulated, virtual money); never part of the real portfolio. */
@Serializable internal data object PracticeRoute

/** A simulated order for one instrument; [side] is "BUY" or "SELL". */
@Serializable internal data class PracticeOrderRoute(val symbol: String, val side: String = "BUY")

/** Search from Practice: picking a company opens its simulated order directly. */
@Serializable internal data object PracticeSearchRoute
