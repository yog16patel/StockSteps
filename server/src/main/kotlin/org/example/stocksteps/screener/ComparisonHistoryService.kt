package org.example.stocksteps.screener

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.UserDataException
import org.example.stocksteps.userdata.user
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.ZoneOffset

/**
 * Company Comparison Phase 3: historical financial comparison.
 *
 * - The public route serves only the free view (latest four completed fiscal quarters of revenue, net
 *   income and diluted EPS). It never reads an Authorization header, so nothing it returns depends on a plan.
 * - The signed-in route resolves StockSteps+ from the stored entitlement ([EntitlementService]) *before*
 *   any provider call: 3Y/5Y without StockSteps+ is 403 PLUS_REQUIRED; an unreadable plan is 503 for
 *   3Y/5Y and falls back to the free 1Y view. Clients can't send a plan, tier or "premium" flag.
 * - Statements are cached per symbol and frequency (public data, the same for everyone); each response
 *   is assembled per request for the caller's tier and isn't cached, so premium content can't be reused
 *   for a free caller or another account.
 */
class ComparisonHistoryService(
    private val stocks: StockService,
    /** (symbol, "quarter" | "annual") → the same statements Company Details shows (`CompanyFinancialService`). */
    private val fundamentalsOf: suspend (String, String) -> CompanyFundamentals,
    private val entitlements: EntitlementService,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val source: String,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256, name = "comparison-history"),
    private val ttl: Long = 21_600_000L,
    private val timeoutMillis: Long = 20_000L,
    private val meter: org.example.stocksteps.service.ProviderUsageMeter = org.example.stocksteps.service.ProviderUsageMeter.shared,
    /** Optional hourly caps on statement loads (cache misses) per feature. */
    private val budget: org.example.stocksteps.service.ProviderRequestBudget = org.example.stocksteps.service.ProviderRequestBudget.UNLIMITED
) {
    private val log = LoggerFactory.getLogger("StockSteps.ComparisonHistory")
    private fun today() = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString()

    private fun range(raw: String?): HistoryRange = HistoryRange.parse(raw ?: "1Y")
        ?: throw ScreenerRequestException(400, "INVALID_HISTORY_RANGE", "Choose 1Y, 3Y or 5Y.")

    /** Free view for everyone (guests included). 3Y/5Y need a signed-in StockSteps+ account. */
    suspend fun publicHistory(rawSymbols: String?, rawRange: String?): HistoricalComparison = withContext(org.example.stocksteps.service.ProviderFeature("comparison-history")) { publicHistoryIn(rawSymbols, rawRange) }
    private suspend fun publicHistoryIn(rawSymbols: String?, rawRange: String?): HistoricalComparison {
        val symbols = parseComparisonSymbols(rawSymbols)
        val range = range(rawRange)
        if (range.premium) throw ScreenerRequestException(401, "SIGN_IN_REQUIRED", "Sign in with StockSteps+ to see ${range.label} history. The 1Y view is free.")
        return build(symbols, range, HistoryAccess(plus = false, signedIn = false, message = "Sign in and upgrade to StockSteps+ for 3- and 5-year history."))
    }

    /** Signed-in view: the plan comes only from the stored entitlement for [uid]. */
    suspend fun userHistory(uid: String, rawSymbols: String?, rawRange: String?): HistoricalComparison = withContext(org.example.stocksteps.service.ProviderFeature("comparison-history")) { userHistoryIn(uid, rawSymbols, rawRange) }
    private suspend fun userHistoryIn(uid: String, rawSymbols: String?, rawRange: String?): HistoricalComparison {
        val symbols = parseComparisonSymbols(rawSymbols)
        val range = range(rawRange)
        val plan = try { entitlements.get(uid) } catch (cause: UserDataException) {
            if (range.premium) throw cause                       // fail closed: 503 ENTITLEMENT_UNAVAILABLE, no provider call
            null
        }
        val plus = plan?.plus == true
        if (range.premium && !plus) throw ScreenerRequestException(403, "PLUS_REQUIRED",
            "${range.label} history is part of StockSteps+. The latest four quarters stay free.")
        val access = if (plus) HistoryAccess(true, true, HistoryRange.entries, HistoryMetric.entries)
            else HistoryAccess(false, true, message = if (plan == null) "Your StockSteps+ status can't be checked right now, so the free view is shown."
                else "StockSteps+ adds 3- and 5-year history, growth, margins and a revenue index.")
        return build(symbols, range, access)
    }

    /**
     * The same history for internal callers that already decided the tier (Phase 4 research summaries/exports),
     * reusing this service's statement cache: no separate provider requests for the same data.
     */
    suspend fun data(symbols: List<String>, range: HistoryRange, plus: Boolean): HistoricalComparison =
        build(symbols, range, HistoryAccess(plus, true, if (plus) HistoryRange.entries else listOf(HistoryRange.ONE_YEAR),
            if (plus) HistoryMetric.entries else HistoryMetric.entries.filter { it.free }))

    /** Cached statements for one company (null when unavailable). */
    suspend fun statements(symbol: String, period: String): List<org.example.stocksteps.model.FinancialPeriodStatement>? = input(symbol, period).statements

    private suspend fun build(symbols: List<String>, range: HistoryRange, access: HistoryAccess): HistoricalComparison = coroutineScope {
        val period = if (range.granularity == HistoryGranularity.QUARTERLY) "quarter" else "annual"
        val inputs = symbols.map { symbol -> async { input(symbol, period) } }.awaitAll()
        if (inputs.all { it.statements == null }) throw ScreenerRequestException(503, "HISTORICAL_DATA_UNAVAILABLE", "Financial history isn't available right now. Try again shortly.")
        val result = HistoricalComparisonEngine.compute(inputs, range, access.plus, today())
        HistoricalComparison(range, range.granularity, result.companies, result.metrics, result.periods, result.insights,
            buildList {
                add("${range.description}. ${if (range.granularity == HistoryGranularity.QUARTERLY) "Each company's own fiscal quarters" else "Each company's own fiscal years"} are shown; fiscal calendars can differ.")
                add("Reported figures only: missing periods stay empty and nothing is estimated, interpolated or converted between currencies.")
                result.companies.mapNotNull { c -> c.retrievedAt?.take(10)?.let { "${c.symbol} $it" } }.takeIf { it.isNotEmpty() }?.let { add("Financial statements retrieved: ${it.joinToString(", ")}.") }
                if (sampleData) add("Sample fixture data for development; not live financial data.")
                add("Education, not investment advice. Past financial results don't predict future results.")
            }, access, source, clock.instant().toString(), sampleData)
    }

    private suspend fun input(symbol: String, period: String): HistoryInput {
        val name = try { stocks.getProfile(symbol)?.companyName ?: stocks.getStock(symbol)?.companyName } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            null
        } ?: symbol
        val feature = meter.feature()
        return try {
            var loaded = false
            val data = withTimeout(timeoutMillis) { cache.getOrLoad("$symbol|$period", ttl) {
                loaded = true
                if (!budget.tryAcquire(feature)) throw BudgetExceeded()
                fundamentalsOf(symbol, period)
            } }
            meter.record("statements", period, feature, if (loaded) "cacheMiss" else "cacheHit")
            HistoryInput(symbol, name, data.history, retrievedAt = data.retrievedAt)
        } catch (cause: TimeoutCancellationException) {
            meter.record("statements", period, feature, "error")
            HistoryInput(symbol, name, null, "the financial data provider didn't respond in time")
        } catch (cause: BudgetExceeded) {
            meter.record("statements", period, feature, "budgetExceeded")
            HistoryInput(symbol, name, null, "financial data is temporarily unavailable (request limit reached); try again later")
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            meter.record("statements", period, feature, "error")
            log.warn("History load failed for {} ({}): {}", symbol, period, cause.javaClass.simpleName)
            HistoryInput(symbol, name, null, "financial history isn't available right now")
        }
    }
}

private class BudgetExceeded : Exception()

/**
 * `GET /api/v1/compare/history` (public, free view only) and `GET /api/v1/me/compare/history` (signed in;
 * 3Y/5Y and advanced metrics for StockSteps+). Same errors as the other comparison routes.
 */
fun Route.comparisonHistoryRoutes(service: ComparisonHistoryService, auth: UserAuthenticator, limiter: RequestRateLimiter) {
    suspend fun RoutingContext.guarded(client: String, block: suspend () -> Any) {
        if (!limiter.allow(client)) { call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return }
        try { call.respond(block()) } catch (cause: ScreenerRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        } catch (cause: UserDataException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
        }
    }
    get("/api/v1/compare/history") {
        guarded(call.request.origin.remoteHost) { service.publicHistory(call.request.queryParameters["symbols"], call.request.queryParameters["range"]) }
    }
    get("/api/v1/me/compare/history") {
        user(auth) { uid -> guarded("history:$uid") { service.userHistory(uid, call.request.queryParameters["symbols"], call.request.queryParameters["range"]) } }
    }
}
