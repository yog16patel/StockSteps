package org.example.stocksteps.portfolio.analytics

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.portfolio.PortfolioCurrency

/** Whether a metric could be computed from the recorded ledger and available market data. */
@Serializable enum class Availability { AVAILABLE, PARTIAL, UNAVAILABLE, LOCKED }

@Serializable
enum class AnalyticsPeriod(val label: String) {
    ONE_DAY("1D"), ONE_WEEK("1W"), ONE_MONTH("1M"), THREE_MONTHS("3M"), ONE_YEAR("1Y"), THREE_YEARS("3Y"), FIVE_YEARS("5Y"), ALL("ALL");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.label.equals(value, ignoreCase = true) } }
}

@Serializable enum class BenchmarkId { SP500, TSX, NASDAQ }

/** PRICE return indices exclude dividends; the portfolio's return includes the dividends it received. */
@Serializable enum class ReturnBasis { PRICE_RETURN, TOTAL_RETURN }

@Serializable
data class BenchmarkInfo(
    val id: BenchmarkId,
    val name: String,
    val symbol: String,
    val currency: PortfolioCurrency,
    val basis: ReturnBasis,
    /** True when the series is a fund tracking the index rather than the index level itself. */
    @EncodeDefault val isProxy: Boolean = false
)

/** All amounts are decimal strings in the account's reporting currency unless stated. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PerformanceMetrics(
    val period: AnalyticsPeriod,
    val startDate: String,
    val endDate: String,
    val availability: Availability,
    val startValue: String? = null,
    val endValue: String? = null,
    /** Deposits minus withdrawals, plus in-kind transfers and opening imports at market value. */
    val netExternalFlows: String? = null,
    val deposits: String? = null,
    val withdrawals: String? = null,
    /** End − start − net external flows: what the investments earned, after fees, including dividends and FX. */
    val investmentGain: String? = null,
    val dividends: String? = null,
    /** Cumulative time-weighted return in percent (cash-flow neutral). */
    val timeWeightedReturn: String? = null,
    /** Annualized TWR in percent, only for periods of at least one year. */
    val annualizedTimeWeightedReturn: String? = null,
    /** Money-weighted return (XIRR), annualized percent; null with [xirrStatus] explaining why. */
    val moneyWeightedReturn: String? = null,
    val xirrStatus: String? = null,
    /** Daily-boundary chain-linked index (start = 100), aligned with [indexDates]. */
    @EncodeDefault val indexDates: List<String> = emptyList(),
    @EncodeDefault val portfolioIndex: List<String?> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BenchmarkComparison(
    val benchmark: BenchmarkInfo,
    val availability: Availability,
    val portfolioReturn: String? = null,
    val benchmarkReturn: String? = null,
    /** Percentage points (portfolio − benchmark). */
    val difference: String? = null,
    /** Benchmark normalized to 100 on the portfolio index's first date, same dates as the portfolio index. */
    @EncodeDefault val benchmarkIndex: List<String?> = emptyList(),
    /** Reporting currency used for both lines (a foreign index is converted at dated FX). */
    val currency: PortfolioCurrency,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable
data class AllocationSlice(val key: String, val label: String, val value: String, val percent: String)

@Serializable enum class AssetClass { STOCK, ETF, CASH, UNCLASSIFIED }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AllocationBreakdown(
    val availability: Availability,
    /** Holdings + positive cash: the denominator for every percentage here. */
    val longAssets: String? = null,
    /** Negative cash (borrowed/unfunded), excluded from the denominator. */
    val borrowedCash: String? = null,
    @EncodeDefault val byHolding: List<AllocationSlice> = emptyList(),
    @EncodeDefault val bySector: List<AllocationSlice> = emptyList(),
    @EncodeDefault val byAssetClass: List<AllocationSlice> = emptyList(),
    @EncodeDefault val byCurrency: List<AllocationSlice> = emptyList(),
    /** Share of holdings' value with a known sector (ETFs and unclassified holdings excluded). */
    val classifiedShare: String? = null,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable data class WeightedName(val name: String, val percent: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ConcentrationMetrics(
    val availability: Availability,
    val holdingsCount: Int = 0,
    val largestHolding: WeightedName? = null,
    val topThree: String? = null,
    val topFive: String? = null,
    val largestSector: WeightedName? = null,
    /** Herfindahl-Hirschman index over securities only (0–1). */
    val hhi: String? = null,
    /** 1 / HHI: how many equally sized holdings would give the same concentration. */
    val effectiveHoldings: String? = null,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable
data class ContributorRow(
    val symbol: String,
    val name: String? = null,
    /** Reporting-currency period contribution: Δmarket value − net amount invested + income received. */
    val contribution: String? = null,
    /** Part of [contribution] from the security's own price and income, at end-of-period FX. */
    val localPart: String? = null,
    /** Part of [contribution] from exchange-rate movement. */
    val fxPart: String? = null,
    /** Contribution ÷ Modified Dietz average capital, in percentage points. */
    val contributionPercent: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Contributors(
    val period: AnalyticsPeriod,
    val availability: Availability,
    @EncodeDefault val positive: List<ContributorRow> = emptyList(),
    @EncodeDefault val negative: List<ContributorRow> = emptyList(),
    /** Symbols whose contribution couldn't be calculated (missing prices or FX). */
    @EncodeDefault val unavailable: List<String> = emptyList(),
    /** Investment gain not attributed to a holding: standalone fees, cash FX, adjustments. */
    val other: String? = null,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable data class AmountByKey(val key: String, val amount: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DividendSummary(
    val availability: Availability,
    /** Recorded dividends, all time, converted at each payment date's FX. */
    val total: String? = null,
    val inPeriod: String? = null,
    val reinvested: String? = null,
    val payments: Int = 0,
    @EncodeDefault val byCompany: List<AmountByKey> = emptyList(),
    @EncodeDefault val byMonth: List<AmountByKey> = emptyList(),
    @EncodeDefault val byYear: List<AmountByKey> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CurrencyExposure(
    val availability: Availability,
    /** Listing/denomination currency slices (holdings and cash separately), share of long assets. */
    @EncodeDefault val slices: List<AllocationSlice> = emptyList(),
    /** Share denominated in a currency other than the reporting currency. */
    val foreignShare: String? = null,
    /** Period FX effect on holdings (sum of contributors' FX parts). */
    val fxEffect: String? = null,
    val localEffect: String? = null,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable data class HealthMetric(val id: String, val label: String, val value: String, val explanation: String)

@Serializable enum class InsightCategory { CONCENTRATION, SECTOR, PERFORMANCE, BENCHMARK, CONTRIBUTOR, DIVIDENDS, CURRENCY, DATA }

/** A deterministic observation computed from verified metrics; never generated by AI. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PortfolioInsight(
    /** Stable id: category plus subject (e.g. "concentration.largest"). */
    val id: String,
    val category: InsightCategory,
    val title: String,
    val explanation: String,
    @EncodeDefault val metrics: Map<String, String> = emptyMap(),
    val period: AnalyticsPeriod? = null,
    val asOf: String,
    val completeness: Availability,
    /** Key into the education catalogue explaining the method. */
    val methodology: String,
    /** Section to open for detail ("performance", "allocation", …). */
    val destination: String? = null
)

@Serializable enum class SubscriptionTier { FREE, PLUS }
@Serializable enum class EntitlementStatus { NONE, ACTIVE, EXPIRED }

/** Server-authoritative StockSteps+ state. Clients only display it; the backend enforces it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Entitlements(
    val tier: SubscriptionTier,
    val status: EntitlementStatus,
    val expiresAt: Long? = null,
    /** "none", "subscription" or "debug" (MOCK only). */
    val source: String = "none",
    @EncodeDefault val features: List<String> = emptyList()
) {
    val plus: Boolean get() = tier == SubscriptionTier.PLUS
}

@Serializable data class DebugEntitlementRequest(val tier: SubscriptionTier, val expired: Boolean = false)

/** The Insights dashboard for one account. Sections the tier doesn't include are LOCKED (no data sent). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PortfolioAnalytics(
    val accountId: String,
    val revision: Long,
    val reportingCurrency: PortfolioCurrency,
    val asOf: String,
    val tier: SubscriptionTier,
    val period: AnalyticsPeriod,
    @EncodeDefault val periods: List<AnalyticsPeriod> = emptyList(),
    @EncodeDefault val health: List<HealthMetric> = emptyList(),
    val performance: PerformanceMetrics? = null,
    val benchmark: BenchmarkComparison? = null,
    val allocation: AllocationBreakdown,
    val concentration: ConcentrationMetrics,
    val contributors: Contributors? = null,
    val dividends: DividendSummary,
    val currency: CurrencyExposure,
    @EncodeDefault val insights: List<PortfolioInsight> = emptyList(),
    /** Section ids withheld for this tier: performance, benchmark, sectors, concentration-detail, contributors, dividends-detail, currency-impact. */
    @EncodeDefault val locked: List<String> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList()
)
