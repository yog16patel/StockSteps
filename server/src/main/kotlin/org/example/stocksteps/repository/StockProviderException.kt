package org.example.stocksteps.repository

// Provider-neutral failures; never include provider URLs, bodies, or credentials.
class StockProviderException(
    val failure: Failure,
    val upstreamStatus: Int? = null
) : RuntimeException(
    if (upstreamStatus == null) failure.name else "${failure.name} (upstream HTTP $upstreamStatus)"
) {
    enum class Failure { TIMEOUT, RATE_LIMITED, UNAVAILABLE, INVALID_RESPONSE }
}
