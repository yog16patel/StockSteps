package org.example.stocksteps.presentation.earnings

import kotlinx.serialization.Serializable

/** Earnings Calendar, optionally opened on a date (yyyy-MM-dd), filter ("ALL" | "WATCHLIST") and tab ("UPCOMING" | "REPORTED"). */
@Serializable internal data class EarningsCalendarRoute(val selectedDate: String? = null, val filter: String? = null, val tab: String? = null)
/** Earnings Event Details for one calendar event ("AAPL:2026-Q4"). */
@Serializable internal data class EarningsEventRoute(val eventId: String)
/** Earnings Results for one company's fiscal period (the report id is "SYMBOL:YYYY-Qn"). */
@Serializable internal data class EarningsResultsRoute(val symbol: String, val fiscalYear: Int, val fiscalQuarter: Int) {
    val reportId: String get() = org.example.stocksteps.earnings.EarningsReportMapper.reportId(symbol, fiscalYear, fiscalQuarter)
    companion object {
        fun of(reportId: String): EarningsResultsRoute? = org.example.stocksteps.earnings.EarningsReportMapper.parse(reportId)?.let { (s, y, q) -> EarningsResultsRoute(s, y, q) }
    }
}
/** Earnings Details (results, history) for one company. */
@Serializable internal data class EarningsDetailsRoute(val symbol: String)
/** StockSteps+ personalized earnings digest (Phase 5). */
@Serializable internal data object EarningsDigestRoute
/** Earnings Digest & AI settings (digest cadence, learning interests, AI usage). */
@Serializable internal data object EarningsDigestSettingsRoute
