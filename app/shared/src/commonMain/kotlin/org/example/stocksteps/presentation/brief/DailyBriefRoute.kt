package org.example.stocksteps.presentation.brief

import kotlinx.serialization.Serializable

/** The Daily Market Brief reader; null [briefId] opens the latest brief (notifications pass an id). */
@Serializable internal data class DailyBriefRoute(val briefId: String? = null)

@Serializable internal data object DailyBriefHistoryRoute
