package org.example.stocksteps.presentation.screener

import kotlinx.serialization.Serializable

/** Discover Stocks; [preset] opens with a beginner preset applied. */
@Serializable internal data class ScreenerRoute(val preset: String? = null)
@Serializable internal data object ComparisonRoute
/** Company Comparison Phase 4: the research checklist (opened from Compare; reuses its comparison data). */
@Serializable internal data object ComparisonResearchRoute
/** Company Comparison Phase 5: the AI assistant (opened from Compare or a research question; reuses Compare's data and selection). */
@Serializable internal data class ComparisonAiRoute(val researchQuestionId: String? = null, val researchSessionId: String? = null, val hasNote: Boolean = false)
