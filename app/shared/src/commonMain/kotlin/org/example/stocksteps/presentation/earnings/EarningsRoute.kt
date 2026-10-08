package org.example.stocksteps.presentation.earnings

import kotlinx.serialization.Serializable

/** Earnings Calendar, optionally opened on a date (yyyy-MM-dd) and filter ("ALL" | "WATCHLIST"). */
@Serializable internal data class EarningsCalendarRoute(val selectedDate: String? = null, val filter: String? = null)
/** Earnings Event Details for one calendar event ("AAPL:2026-Q4"). */
@Serializable internal data class EarningsEventRoute(val eventId: String)
/** Earnings Details (results, history) for one company. */
@Serializable internal data class EarningsDetailsRoute(val symbol: String)
