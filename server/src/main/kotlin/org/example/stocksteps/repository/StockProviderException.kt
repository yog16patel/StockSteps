package org.example.stocksteps.repository

// Provider-neutral failures; never include provider URLs, bodies, or credentials.
class StockProviderException(
    val failure: Failure,
    val upstreamStatus: Int? = null,
    /**
     * Phase 7A: refused by this server's own configured daily allowance (`PROVIDER_<NAME>_DAILY_LIMIT`, counter exhausted), with no
     * upstream request. Never set from a provider 429 alone: a provider 429 doesn't say whether a minute or a daily limit was hit.
     */
    val dailyLimitReached: Boolean = false
) : RuntimeException(
    when {
        dailyLimitReached -> "${failure.name} (daily limit)"
        upstreamStatus == null -> failure.name
        else -> "${failure.name} (upstream HTTP $upstreamStatus)"
    }
) {
    enum class Failure { TIMEOUT, RATE_LIMITED, UNAVAILABLE, INVALID_RESPONSE }

    /** Phase 7A: the provider refused access for this plan (402/403) — retrying soon won't help. */
    val restricted: Boolean get() = upstreamStatus == 402 || upstreamStatus == 403
}
