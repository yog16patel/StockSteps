package org.example.stocksteps.userdata

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.*
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.IndexDataSource
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.sin

/** Daily index levels (date → close) in the index's own currency. Empty when unavailable. */
fun interface BenchmarkHistorySource {
    suspend fun levels(info: BenchmarkInfo): Map<String, String>
}

/** REAL: the provider's daily index closes through the shared chart cache (same quota as charts). */
class ChartBenchmarkHistory(private val dailyCloses: suspend (String) -> List<PricePoint>) : BenchmarkHistorySource {
    override suspend fun levels(info: BenchmarkInfo): Map<String, String> = try {
        dailyCloses(info.symbol).filter { it.close.isFinite() && it.close > 0 }
            .associate { it.time.take(10) to java.math.BigDecimal.valueOf(it.close).stripTrailingZeros().toPlainString() }
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        emptyMap()
    }
}

/**
 * MOCK: the fixture's recorded index history (about a month), extended backwards with a fixed
 * formula so long periods can be exercised offline. Responses label it as sample data.
 */
class MockBenchmarkHistory(private val indices: IndexDataSource) : BenchmarkHistorySource {
    override suspend fun levels(info: BenchmarkInfo): Map<String, String> {
        val recorded = indices.indexHistory(info.symbol).associate { it.time.take(10) to it.close }
        val earliest = recorded.keys.minOrNull() ?: return emptyMap()
        val anchor = recorded.getValue(earliest)
        val start = LocalDate.parse(earliest)
        val phase = info.symbol.sumOf { it.code } % 17
        val generated = (1..1_900).mapNotNull { back ->
            val date = start.minusDays(back.toLong())
            if (date.dayOfWeek.value > 5) null
            else date.toString() to anchor * exp(-0.00035 * back + 0.025 * (sin((back + phase) / 13.0) - sin(phase / 13.0)))
        }
        return (generated + recorded.toList()).associate { (date, close) ->
            date to Decimal.parse(java.math.BigDecimal.valueOf(close).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).toString()
        }
    }
}

/**
 * Server-authoritative StockSteps+ state. Clients display it; every premium section is computed (or
 * withheld) here. Expiry downgrades analytics only — ledgers, holdings and transactions are never
 * gated. Debug overrides exist only when [debugAllowed] (MOCK), and are labelled "debug".
 */
class EntitlementService(private val store: UserDataStore, private val now: () -> Long, val debugAllowed: Boolean) {
    /**
     * Paid (or canceled but not yet over) and billing grace periods are StockSteps+; a lapsed plan, a
     * failed payment without grace, or no record is free. A record that can't be read fails closed:
     * callers get 503 ENTITLEMENT_UNAVAILABLE, never premium access.
     */
    suspend fun get(uid: String): Entitlements {
        val stored = store.entitlement(uid)
        if (stored?.source == "debug" && !debugAllowed) return free(EntitlementStatus.NONE) // never honour a debug record outside MOCK
        if (stored?.state == "unavailable") throw UserDataException(503, "ENTITLEMENT_UNAVAILABLE", "Your StockSteps+ status can't be checked right now. Try again shortly.")
        if (stored?.plan != SubscriptionTier.PLUS) return free(EntitlementStatus.NONE)
        val time = now()
        val paid = stored.expiresAt == null || stored.expiresAt > time
        return when {
            stored.state == "payment-failed" -> free(EntitlementStatus.BILLING_ISSUE, stored.expiresAt, stored.source)
            paid && stored.state == "canceled" -> plus(EntitlementStatus.CANCELED, stored)
            paid -> plus(EntitlementStatus.ACTIVE, stored)
            stored.state == "grace" && (stored.graceUntil ?: 0) > time -> plus(EntitlementStatus.GRACE_PERIOD, stored)
            else -> free(EntitlementStatus.EXPIRED, stored.expiresAt, stored.source)
        }
    }

    suspend fun simulate(uid: String, request: DebugEntitlementRequest): Entitlements {
        if (!debugAllowed) throw UserDataException(404, "NOT_FOUND", "Not found.")
        val t = now()
        store.setEntitlement(uid, when {
            request.state == "unavailable" -> StoredEntitlement(SubscriptionTier.PLUS, null, "debug", state = "unavailable")
            request.tier == SubscriptionTier.PLUS && request.state == "canceled" -> StoredEntitlement(SubscriptionTier.PLUS, t + 7 * 86_400_000L, "debug", state = "canceled")
            request.tier == SubscriptionTier.PLUS && request.state == "grace" -> StoredEntitlement(SubscriptionTier.PLUS, t - 1, "debug", state = "grace", graceUntil = t + 3 * 86_400_000L)
            request.tier == SubscriptionTier.PLUS && request.state == "payment-failed" -> StoredEntitlement(SubscriptionTier.PLUS, t - 1, "debug", state = "payment-failed")
            request.tier == SubscriptionTier.PLUS && request.state == "restored" -> StoredEntitlement(SubscriptionTier.PLUS, null, "debug", state = "restored")
            request.tier == SubscriptionTier.PLUS && request.expired -> StoredEntitlement(SubscriptionTier.PLUS, t - 1, "debug")
            request.tier == SubscriptionTier.PLUS -> StoredEntitlement(SubscriptionTier.PLUS, null, "debug")
            else -> null
        })
        return get(uid)
    }

    private fun plus(status: EntitlementStatus, stored: StoredEntitlement) =
        Entitlements(SubscriptionTier.PLUS, status, stored.expiresAt, stored.source, EntitlementFeatures.PLUS)

    private fun free(status: EntitlementStatus, expiresAt: Long? = null, source: String = "none") =
        Entitlements(SubscriptionTier.FREE, status, expiresAt, source, EntitlementFeatures.FREE)
}

/**
 * Portfolio Intelligence for one owned account. Inputs come from [PortfolioMarketService] (the same
 * ledger, dated prices and Bank of Canada FX as the Portfolio screen) and the calculation is
 * [PortfolioAnalyticsEngine]. Results are cached per user, account, ledger revision, period,
 * benchmark, tier and day; any ledger edit changes the revision and so the key.
 */
class PortfolioAnalyticsService(
    private val market: PortfolioMarketService,
    private val quotes: WatchMarketData,
    private val benchmarks: BenchmarkHistorySource,
    private val entitlements: EntitlementService,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256)
) {
    suspend fun analytics(uid: String, accountId: String, periodLabel: String?, benchmarkName: String?): PortfolioAnalytics {
        val period = if (periodLabel == null) AnalyticsPeriod.ONE_YEAR else AnalyticsPeriod.parse(periodLabel)
            ?: throw UserDataException(400, "INVALID_PERIOD", "Choose 1D, 1W, 1M, 3M, 1Y, 3Y, 5Y or ALL.")
        val requestedBenchmark = benchmarkName?.let { BenchmarkCatalog.parse(it) ?: throw UserDataException(400, "INVALID_BENCHMARK", "Choose SP500, TSX or NASDAQ.") }
        val tier = entitlements.get(uid).tier
        val revision = market.revision(uid)
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val key = listOf(uid, accountId, revision, period, requestedBenchmark, tier, today).joinToString("|")
        return cache.getOrLoad(key, CACHE_TTL) { compute(uid, accountId, period, requestedBenchmark, tier) }
    }

    private suspend fun compute(uid: String, accountId: String, period: AnalyticsPeriod, requested: BenchmarkId?, tier: SubscriptionTier): PortfolioAnalytics = coroutineScope {
        val data = market.analyticsMarket(uid, accountId)
        val benchmarkId = requested ?: BenchmarkCatalog.default(data.account.reportingCurrency)
        val info = BenchmarkCatalog.info(benchmarkId)
        // Free accounts never trigger benchmark history requests.
        val levels = if (tier == SubscriptionTier.PLUS) async { benchmarks.levels(info) } else null
        val metadata = data.instruments.keys.map { symbol -> async { symbol to metadata(symbol) } }.awaitAll().toMap()
        val series = levels?.await()?.takeIf { it.isNotEmpty() }?.let { BenchmarkSeries(info, it) }
        val inputs = AnalyticsInputs(data.ledger, accountId, data.today, data.closes, data.fxByDate, data.current, metadata, series,
            clock.instant().toString())
        val result = PortfolioAnalyticsEngine.analyze(inputs, period, tier)
        val withBenchmark = if (tier == SubscriptionTier.PLUS && series == null) result.copy(benchmark = BenchmarkComparison(info, Availability.UNAVAILABLE,
            currency = data.account.reportingCurrency, notes = listOf("${info.name} history isn't available right now. No benchmark return is shown instead of a guess.")))
            else result
        withBenchmark.copy(notes = withBenchmark.notes + listOfNotNull(
            "Calculated from your recorded transactions, daily closing prices and Bank of Canada exchange rates.",
            if (sampleData) "Sample market data for development: prices, exchange rates and index history aren't real." else null
        ))
    }

    private suspend fun metadata(symbol: String): SecurityMetadata {
        val profile = try { quotes.profile(symbol) } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }
        val assetClass = when {
            profile?.isEtf == true -> AssetClass.ETF
            profile?.isEtf == false || profile?.sector != null -> AssetClass.STOCK
            else -> AssetClass.UNCLASSIFIED
        }
        return SecurityMetadata(profile?.companyName, profile?.sector?.takeIf { it.isNotBlank() && assetClass == AssetClass.STOCK }, assetClass)
    }

    private companion object {
        const val CACHE_TTL = 10 * 60_000L
    }
}

fun Route.portfolioAnalyticsRoutes(auth: UserAuthenticator, analytics: PortfolioAnalyticsService, entitlements: EntitlementService) {
    route("/api/v1/me") {
        get("/entitlements") { user(auth) { uid -> call.respond(entitlements.get(uid)) } }
        // MOCK only: the route doesn't exist in REAL, and the service refuses it too.
        if (entitlements.debugAllowed) {
            put("/entitlements/debug") { user(auth) { uid -> call.respond(entitlements.simulate(uid, call.receive<DebugEntitlementRequest>())) } }
        }
        get("/portfolio/accounts/{id}/analytics") {
            user(auth) { uid ->
                call.respond(analytics.analytics(uid, call.parameters["id"].orEmpty(), call.request.queryParameters["period"], call.request.queryParameters["benchmark"]))
            }
        }
    }
}
