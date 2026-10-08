package org.example.stocksteps.portfolio.analytics

/**
 * Optional AI explanations of Insights — architecture only. No provider is wired and no request is
 * ever sent: [NoAiExplanations] is the only implementation. A future backend provider must:
 *
 * - run server-side (apps never call AI APIs directly) and only for StockSteps+ ("ai-explanations");
 * - explain an existing deterministic [PortfolioInsight]; it never computes or changes a number;
 * - receive only [ExplanationRequest], which by construction carries the insight's own verified
 *   metrics. Holdings (symbols, values, weights) are included only with [ExplanationConsent.INCLUDE_HOLDINGS];
 * - be validated like news explanations (no advice, no predictions, cite only supplied metrics).
 */
enum class ExplanationConsent {
    /** Default: nothing is sent anywhere. */
    NONE,
    /** Send the insight's aggregate metrics only (percentages and amounts, no security names). */
    METRICS_ONLY,
    /** Also send holding symbols and weights. Must be an explicit, revocable opt-in per user. */
    INCLUDE_HOLDINGS
}

/** Exactly what may leave the device for one explanation. Built only through [ExplanationRequest.from]. */
data class ExplanationRequest internal constructor(
    val insightId: String,
    val category: InsightCategory,
    val title: String,
    val metrics: Map<String, String>,
    val period: AnalyticsPeriod?,
    val methodology: String
) {
    companion object {
        private val HOLDING_KEYS = setOf("largestHolding", "symbol", "sector")

        /** Null without consent. With [ExplanationConsent.METRICS_ONLY], names and symbols are removed. */
        fun from(insight: PortfolioInsight, consent: ExplanationConsent): ExplanationRequest? = when (consent) {
            ExplanationConsent.NONE -> null
            ExplanationConsent.METRICS_ONLY -> ExplanationRequest(insight.id.substringBefore('.'), insight.category, insight.category.name.lowercase(),
                insight.metrics.filterKeys { it !in HOLDING_KEYS }, insight.period, insight.methodology)
            ExplanationConsent.INCLUDE_HOLDINGS -> ExplanationRequest(insight.id, insight.category, insight.title, insight.metrics, insight.period, insight.methodology)
        }
    }
}

sealed interface ExplanationResult {
    data class Explained(val text: String, val citedMetrics: List<String>) : ExplanationResult
    data class Unavailable(val reason: String) : ExplanationResult
}

fun interface PortfolioExplanationProvider {
    suspend fun explain(request: ExplanationRequest): ExplanationResult
}

/** The shipped provider: explanations stay deterministic ([PortfolioInsight.explanation] and [AnalyticsEducation]). */
object NoAiExplanations : PortfolioExplanationProvider {
    override suspend fun explain(request: ExplanationRequest) =
        ExplanationResult.Unavailable("AI explanations aren't available. Each insight already explains its numbers.")
}
