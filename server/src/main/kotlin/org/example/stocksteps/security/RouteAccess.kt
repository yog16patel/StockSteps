package org.example.stocksteps.security

import io.ktor.http.HttpMethod

/** Who may call a route (Phase 9A, `docs/reviews/security_phase9a/ENDPOINT_ACCESS_MATRIX.md`). */
enum class AccessClass {
    /** No account needed (guest features: search, profiles, prices, news, Discover, earnings calendar, the public brief). */
    PUBLIC,
    /** A signed-in StockSteps account; the response depends on the account (tier, AI quota) but reads or writes no user document. */
    AUTHENTICATED,
    /** A signed-in account acting only on its own documents, located from the verified uid. */
    USER_OWNED,
    /** Scheduler and operator routes: Cloud Scheduler OIDC or the job's own secret (open only in MOCK). */
    ADMIN_INTERNAL,
    /** Cloud Run probes: process state only, no data, never rate limited. */
    HEALTH_INTERNAL
}

/** Rate-limit classes (Phase 9A). Limits per class live in [RateLimitPolicy.DEFAULTS]. */
enum class RateLimitClass {
    PUBLIC_LOW_COST, PUBLIC_MARKET_DATA, AUTHENTICATED_STANDARD, USER_WRITE, EXPENSIVE_OPERATION, ADMIN_INTERNAL
}

/**
 * One route: [pattern] is the Ktor route template (`{name}` = one path segment). [limit] null = not rate limited (health only).
 * [bucket] gives a route its own limit inside its class (e.g. `search`), counted in addition to the class limit.
 */
data class RouteRule(val method: HttpMethod, val pattern: String, val access: AccessClass, val limit: RateLimitClass?, val bucket: String? = null) {
    internal val regex = Regex("^" + pattern.split('/').joinToString("/") { if (it.startsWith('{') && it.endsWith('}')) "[^/]+" else Regex.escape(it) } + "$")
}

/**
 * The route table (Phase 9A): every registered route has exactly one rule (`RouteAccessTest` checks the real module, MOCK and REAL
 * registrations alike). Literal routes are listed before parameter routes that could match the same path, as in Ktor's own resolution.
 */
object RouteAccess {
    private val GET = HttpMethod.Get; private val POST = HttpMethod.Post; private val PUT = HttpMethod.Put
    private val PATCH = HttpMethod.Patch; private val DELETE = HttpMethod.Delete
    private fun public(m: HttpMethod, p: String, l: RateLimitClass = RateLimitClass.PUBLIC_MARKET_DATA, bucket: String? = null) = RouteRule(m, p, AccessClass.PUBLIC, l, bucket)
    private fun account(m: HttpMethod, p: String, l: RateLimitClass = RateLimitClass.AUTHENTICATED_STANDARD) = RouteRule(m, p, AccessClass.AUTHENTICATED, l)
    /** User-owned: reads are standard, writes are USER_WRITE unless stated. */
    private fun owned(m: HttpMethod, p: String, l: RateLimitClass = if (m == HttpMethod.Get) RateLimitClass.AUTHENTICATED_STANDARD else RateLimitClass.USER_WRITE) =
        RouteRule(m, p, AccessClass.USER_OWNED, l)
    private fun internal(m: HttpMethod, p: String) = RouteRule(m, p, AccessClass.ADMIN_INTERNAL, RateLimitClass.ADMIN_INTERNAL)
    private fun health(p: String) = RouteRule(HttpMethod.Get, p, AccessClass.HEALTH_INTERNAL, null)

    private val LOW = RateLimitClass.PUBLIC_LOW_COST
    private val EXPENSIVE = RateLimitClass.EXPENSIVE_OPERATION
    private val STANDARD = RateLimitClass.AUTHENTICATED_STANDARD

    val RULES: List<RouteRule> = listOf(
        // Health and metadata
        health("/health"), health("/health/live"), health("/health/ready"),
        public(GET, "/api/v1/meta", LOW),
        public(GET, "/api/v1/home/personas/{id}", LOW),                                   // MOCK only
        // Stocks (guest market data)
        public(GET, "/api/v1/stocks/search", bucket = "search"),
        public(GET, "/api/v1/stocks/watch-data"),
        public(GET, "/api/v1/stocks/{symbol}/profile"),
        public(GET, "/api/v1/stocks/{symbol}/quote"),
        public(GET, "/api/v1/stocks/{symbol}/news"),
        public(GET, "/api/v1/stocks/{symbol}/news/{articleId}/insight", EXPENSIVE),       // AI explanation (Gemini in REAL), cached
        public(GET, "/api/v1/stocks/{symbol}/movement", EXPENSIVE),                       // AI wording of computed moves, cached
        public(GET, "/api/v1/stocks/{symbol}/sparkline"),
        public(GET, "/api/v1/stocks/{symbol}/valuation"),
        public(GET, "/api/v1/stocks/{symbol}/fundamentals"),
        public(GET, "/api/v1/stocks/{symbol}/details"),
        public(GET, "/api/v1/stocks/{symbol}/chart"),
        public(GET, "/api/v1/stocks/{symbol}/why-moving"),
        public(GET, "/api/v1/news"),
        // Markets (shared, cached)
        public(GET, "/api/v1/market/gainers", LOW), public(GET, "/api/v1/market/losers", LOW),
        public(GET, "/api/v1/market/snapshot", LOW), public(GET, "/market/snapshot", LOW),
        public(GET, "/api/v1/markets/overview", LOW),
        // Earnings (public)
        public(GET, "/api/v1/earnings/calendar"), public(GET, "/api/v1/earnings/calendar/search"),
        public(GET, "/api/v1/earnings/events/{eventId}"),
        public(GET, "/api/v1/earnings/company/{symbol}/next"), public(GET, "/api/v1/earnings/company/{symbol}/latest"),
        public(GET, "/api/v1/earnings/company/{symbol}/reports"),
        public(GET, "/api/v1/earnings/reports/{reportId}"), public(GET, "/api/v1/earnings/reports/{reportId}/price-reaction"),
        public(GET, "/api/v1/earnings/reports/{reportId}/price-history"), public(GET, "/api/v1/earnings/reports/{reportId}/insights"),
        public(GET, "/api/v1/earnings/{symbol}"),
        // Daily Brief (public edition)
        public(GET, "/api/v1/daily-brief/latest", LOW), public(GET, "/api/v1/daily-brief/history", LOW),
        public(GET, "/api/v1/daily-brief/{id}", LOW), public(GET, "/api/v1/daily-brief/{id}/sources", LOW),
        // Discover and comparison (public)
        public(GET, "/api/v1/screener/catalog", LOW), public(POST, "/api/v1/screener/search"),
        public(GET, "/api/v1/compare"), public(GET, "/api/v1/compare/performance"), public(GET, "/api/v1/compare/history"),

        // ---- Signed in ----
        account(GET, "/api/v1/me/entitlements"),
        owned(PUT, "/api/v1/me/entitlements/debug"),                                      // MOCK only
        // Earnings: reminders and following before the {symbol} routes
        owned(GET, "/api/v1/me/earnings/reminders"), owned(POST, "/api/v1/me/earnings/reminders"),
        owned(PUT, "/api/v1/me/earnings/reminders/preferences"), owned(GET, "/api/v1/me/earnings/reminders/deliveries"),
        owned(PUT, "/api/v1/me/earnings/reminders/{reminderId}"), owned(DELETE, "/api/v1/me/earnings/reminders/{reminderId}"),
        owned(GET, "/api/v1/me/earnings/following"),
        account(GET, "/api/v1/me/earnings/reports/{reportId}/premium"), account(GET, "/api/v1/me/earnings/reports/{reportId}/premium/history"),
        account(POST, "/api/v1/me/earnings/reports/{reportId}/ai/explain", EXPENSIVE), account(POST, "/api/v1/me/earnings/reports/{reportId}/ai/ask", EXPENSIVE),
        owned(GET, "/api/v1/me/earnings/ai/usage"),
        owned(GET, "/api/v1/me/earnings/digest/latest"), owned(POST, "/api/v1/me/earnings/digest/latest/ai", EXPENSIVE),
        owned(GET, "/api/v1/me/earnings/digest/history"), owned(GET, "/api/v1/me/earnings/digest/preferences"), owned(PUT, "/api/v1/me/earnings/digest/preferences"),
        account(GET, "/api/v1/me/earnings/{symbol}"), account(POST, "/api/v1/me/earnings/{symbol}/ask", EXPENSIVE),
        // Daily Brief (signed in)
        owned(GET, "/api/v1/me/daily-brief/history"), owned(GET, "/api/v1/me/daily-brief/preferences"), owned(PUT, "/api/v1/me/daily-brief/preferences"),
        account(GET, "/api/v1/me/daily-brief/{id}"), owned(GET, "/api/v1/me/daily-brief/{id}/personalized"),
        account(POST, "/api/v1/me/daily-brief/{id}/ai/explain", EXPENSIVE), account(POST, "/api/v1/me/daily-brief/{id}/ai/ask", EXPENSIVE),
        // Comparison (signed in)
        account(GET, "/api/v1/me/compare/history"),
        account(POST, "/api/v1/me/compare/ai/summary", EXPENSIVE), account(POST, "/api/v1/me/compare/ai/ask", EXPENSIVE),
        owned(GET, "/api/v1/me/compare/ai/usage"),
        owned(GET, "/api/v1/me/comparison-research"), owned(POST, "/api/v1/me/comparison-research"),
        owned(GET, "/api/v1/me/comparison-research/{id}"), owned(PATCH, "/api/v1/me/comparison-research/{id}"),
        owned(DELETE, "/api/v1/me/comparison-research/{id}"), owned(GET, "/api/v1/me/comparison-research/{id}/summary"),
        owned(GET, "/api/v1/me/comparison-research/{id}/snapshots"), owned(POST, "/api/v1/me/comparison-research/{id}/snapshots"),
        owned(DELETE, "/api/v1/me/comparison-research/{id}/snapshots/{snapshotId}"),
        owned(POST, "/api/v1/me/comparison-research/{id}/export", EXPENSIVE),             // PDF built on the server
        owned(GET, "/api/v1/me/screens"), owned(POST, "/api/v1/me/screens"), owned(PUT, "/api/v1/me/screens/{id}"), owned(DELETE, "/api/v1/me/screens/{id}"),
        // Guided research
        owned(GET, "/api/v1/me/learning"), owned(PUT, "/api/v1/me/learning"),
        account(POST, "/api/v1/me/research/{symbol}/ask", EXPENSIVE),
        // Practice (simulated ledger)
        owned(GET, "/api/v1/me/practice"), owned(POST, "/api/v1/me/practice", STANDARD),   // POST = overview (read)
        owned(GET, "/api/v1/me/practice/entitlement"), owned(POST, "/api/v1/me/practice/trial/activate"),
        owned(POST, "/api/v1/me/practice/notices/trial-expired"),
        owned(GET, "/api/v1/me/practice/transactions"), owned(GET, "/api/v1/me/practice/performance"),
        owned(POST, "/api/v1/me/practice/orders/preview", STANDARD),                         // read: prices an order, writes nothing
        owned(POST, "/api/v1/me/practice/orders/execute"), owned(POST, "/api/v1/me/practice/reset"),
        owned(GET, "/api/v1/me/practice/challenges"), owned(POST, "/api/v1/me/practice/challenges/{id}/complete"),
        owned(PUT, "/api/v1/me/practice/debug/scenario"),                                   // MOCK only
        // My Portfolio
        owned(GET, "/api/v1/me/portfolio"), owned(PUT, "/api/v1/me/portfolio/accounts"), owned(DELETE, "/api/v1/me/portfolio/accounts/{id}"),
        owned(GET, "/api/v1/me/portfolio/accounts/{id}/summary"), owned(GET, "/api/v1/me/portfolio/accounts/{id}/history"),
        owned(GET, "/api/v1/me/portfolio/accounts/{id}/analytics"),
        owned(POST, "/api/v1/me/portfolio/transactions"), owned(PUT, "/api/v1/me/portfolio/transactions"), owned(DELETE, "/api/v1/me/portfolio/transactions/{id}"),
        // Watchlists, alerts, devices
        owned(GET, "/api/v1/me/watchlists"), owned(POST, "/api/v1/me/watchlists"), owned(PUT, "/api/v1/me/watchlists/order"),
        owned(POST, "/api/v1/me/watchlists/import"), owned(PATCH, "/api/v1/me/watchlists/{id}"), owned(DELETE, "/api/v1/me/watchlists/{id}"),
        owned(POST, "/api/v1/me/watchlists/{id}/entries"), owned(PUT, "/api/v1/me/watchlists/{id}/entries/order"),
        owned(PATCH, "/api/v1/me/watchlists/{id}/entries/{entryId}"), owned(DELETE, "/api/v1/me/watchlists/{id}/entries/{entryId}"),
        owned(POST, "/api/v1/me/watchlists/{id}/entries/{entryId}/move"),
        owned(GET, "/api/v1/me/alerts"), owned(POST, "/api/v1/me/alerts"), owned(PATCH, "/api/v1/me/alerts/{id}"), owned(DELETE, "/api/v1/me/alerts/{id}"),
        owned(POST, "/api/v1/me/devices"), owned(DELETE, "/api/v1/me/devices/{deviceId}"),

        // ---- Internal ----
        internal(POST, "/internal/alerts/evaluate"), internal(POST, "/internal/earnings-reminders/dispatch"),
        internal(POST, "/internal/daily-brief/dispatch"), internal(GET, "/internal/metrics/usage")
    )

    /** The rule for a request, or null when no route matches (Ktor answers 404/405; [fallback] still limits such traffic). */
    fun of(method: HttpMethod, path: String): RouteRule? {
        val normalized = path.trimEnd('/').ifEmpty { "/" }
        return RULES.firstOrNull { it.method == method && it.regex.matches(normalized) }
    }

    /** Limits for requests that match no rule (unknown paths, wrong methods, HEAD/OPTIONS probes): by prefix, fail closed. */
    fun fallback(path: String): RateLimitClass? = when {
        path.startsWith("/health") -> null
        path.startsWith("/internal/") -> RateLimitClass.ADMIN_INTERNAL
        requiresAccount(path) -> RateLimitClass.AUTHENTICATED_STANDARD
        else -> RateLimitClass.PUBLIC_LOW_COST
    }
}
