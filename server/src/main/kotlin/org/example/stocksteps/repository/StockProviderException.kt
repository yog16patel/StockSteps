package org.example.stocksteps.repository

// Provider-neutral failures; never include provider URLs, bodies, or credentials.
class StockProviderException(val failure: Failure) : RuntimeException(failure.name) {
    enum class Failure { TIMEOUT, RATE_LIMITED, UNAVAILABLE, INVALID_RESPONSE }
}
