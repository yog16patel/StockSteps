package org.example.stocksteps.screener

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis

/** How a metric value is expressed. Percent values are stored as percents (12.5 = 12.5%). */
@Serializable enum class MetricUnit { PERCENT, MULTIPLE, MONEY, PRICE, COUNT, RATIO }

@Serializable enum class MetricGroup(val title: String) {
    MARKET("Market"), VALUATION("Valuation"), GROWTH("Growth"), PROFITABILITY("Profitability"),
    HEALTH("Financial health"), SHAREHOLDER("Shareholder returns")
}

/**
 * One screenable metric. [id] is the same key used by `CompanyFundamentals` (Company Details,
 * Financials, Valuation), so every screen shares one definition and one value per company.
 * Explanations come from `MetricEducation` by the same id; only [period] and [missing] live here.
 */
@Serializable
data class MetricDefinition(
    val id: String,
    val label: String,
    val group: MetricGroup,
    val unit: MetricUnit,
    /** Which reporting period the value describes (e.g. "Trailing twelve months"). */
    val period: String,
    /** What happens when a company has no valid value for this metric. */
    val missing: String,
    /** Typical range for a slider; not a recommendation. */
    val suggestedMin: Double? = null,
    val suggestedMax: Double? = null,
    /** Sectors where the metric isn't comparable (e.g. leverage for banks): the filter isn't applied to them. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notApplicableSectors: List<String> = emptyList(),
    /** False for comparison-only metrics (no reliable cross-universe definition for filtering). */
    val filterable: Boolean = true,
    /** Money values are in each company's reporting currency unless the label says otherwise. */
    val currencyNote: String? = null
)

/** Filterable company attributes (exact matches). */
@Serializable enum class ChoiceField(val label: String) { COUNTRY("Country"), EXCHANGE("Exchange"), SECTOR("Sector"), INDUSTRY("Industry"), SECURITY_TYPE("Security type") }

@Serializable data class RangeFilter(val metric: String, val min: Double? = null, val max: Double? = null)
@Serializable data class ChoiceFilter(val field: ChoiceField, val values: List<String>)

@Serializable enum class SortField { MARKET_CAP, NAME, CHANGE_PERCENT, METRIC }
@Serializable data class ScreenerSort(val field: SortField = SortField.MARKET_CAP, val metric: String? = null, val descending: Boolean = true)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenerQuery(
    val presetId: String? = null,
    @EncodeDefault val ranges: List<RangeFilter> = emptyList(),
    @EncodeDefault val choices: List<ChoiceFilter> = emptyList(),
    @EncodeDefault val sort: ScreenerSort = ScreenerSort(),
    val pageSize: Int = 20,
    /** Opaque, from the previous page's `nextCursor`. */
    val cursor: String? = null
) {
    /** Number of filters the user sees as active (each range and each attribute choice). */
    val activeFilterCount: Int get() = ranges.count { it.min != null || it.max != null } + choices.count { it.values.isNotEmpty() }
    /** The query without paging, used for caching and saved screens. */
    fun definition() = copy(cursor = null)
}

@Serializable
data class ScreenerPreset(
    val id: String,
    val name: String,
    val description: String,
    val ranges: List<RangeFilter>,
    val sort: ScreenerSort,
    /** Metrics shown on each result row for this preset (2–3). */
    val displayMetrics: List<String>,
    val dataPeriod: String,
    val missingData: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notes: List<String> = emptyList()
) {
    fun query(pageSize: Int = 20) = ScreenerQuery(id, ranges, emptyList(), sort, pageSize)
}

/** A metric value for one company: same shape as a `FinancialFact`, reduced to what screens need. */
@Serializable
data class MetricValue(
    val value: Double? = null,
    val availability: FinancialAvailability = FinancialAvailability.MISSING,
    val basis: FinancialBasis? = null,
    val note: String? = null
)

/**
 * The normalized, screenable view of one company, built server-side from the company's quote,
 * profile and `CompanyFundamentals`. Money metrics are in [currency] (never converted).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CompanyRecord(
    val symbol: String,
    val name: String,
    val exchange: String? = null,
    val country: String? = null,
    val currency: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val securityType: String = "Stock",
    val logoUrl: String? = null,
    val price: Double? = null,
    val changePercent: Double? = null,
    val marketCap: Double? = null,
    val volume: Double? = null,
    @EncodeDefault val metrics: Map<String, MetricValue> = emptyMap(),
    /** When the fundamentals were retrieved. */
    val fundamentalsAsOf: String? = null,
    /** True when the latest reported period is old enough that values may be out of date. */
    val stale: Boolean = false,
    /** False when fundamentals weren't loaded (REAL quota budget): financial filters can't evaluate it. */
    val hasFundamentals: Boolean = true
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenerResultRow(
    val company: CompanyRecord,
    /** Criteria that were not applied to this company (e.g. debt/equity for a bank), in words. */
    @EncodeDefault val notApplied: List<String> = emptyList()
)

/** What the results were evaluated against: never imply full-market coverage that didn't happen. */
@Serializable
data class UniverseInfo(
    val description: String,
    /** Companies in the defined universe. */
    val size: Int,
    /** Companies whose fundamentals were available to evaluate. */
    val evaluated: Int,
    val complete: Boolean
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenerPage(
    @EncodeDefault val rows: List<ScreenerResultRow> = emptyList(),
    /** Matches across the whole evaluated universe (all pages), already sorted server-side. */
    val total: Int = 0,
    val nextCursor: String? = null,
    val universe: UniverseInfo,
    val query: ScreenerQuery,
    /** Companies left out because a filtered metric was missing or not meaningful for them. */
    val excludedForMissingData: Int = 0,
    @EncodeDefault val displayMetrics: List<String> = emptyList(),
    @EncodeDefault val warnings: List<String> = emptyList(),
    val asOf: String,
    val sampleData: Boolean = false
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenerCatalog(
    @EncodeDefault val presets: List<ScreenerPreset> = emptyList(),
    @EncodeDefault val metrics: List<MetricDefinition> = emptyList(),
    /** Available values per attribute (from the universe), for selection sheets. */
    @EncodeDefault val choices: Map<ChoiceField, List<String>> = emptyMap(),
    val universe: UniverseInfo,
    val updatedAt: String
)

// ---------- Saved screens ----------

@Serializable
data class SavedScreen(
    val id: String,
    val name: String,
    /** The filter definition, never a list of results: reapplying gives current matches. */
    val query: ScreenerQuery,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SavedScreensResponse(
    @EncodeDefault val screens: List<SavedScreen> = emptyList(),
    /** How many this account may keep (free tier or StockSteps+). */
    val limit: Int,
    val plus: Boolean = false
)

@Serializable data class SaveScreenRequest(val name: String, val query: ScreenerQuery)
@Serializable data class UpdateScreenRequest(val name: String? = null, val query: ScreenerQuery? = null)

// ---------- Comparison ----------

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComparedCompany(
    val record: CompanyRecord? = null,
    val symbol: String,
    /** Null when the company loaded; otherwise why it couldn't be compared. */
    val error: String? = null,
    /** Fiscal-year amounts for growth charts (newest first), in [CompanyRecord.currency]. */
    @EncodeDefault val annual: List<AnnualFigures> = emptyList()
)

@Serializable
data class AnnualFigures(
    val fiscalYear: Int,
    val date: String? = null,
    val currency: String? = null,
    val revenue: Double? = null,
    val netIncome: Double? = null,
    val freeCashFlow: Double? = null
)

/** A deterministic, sourced observation about the selected companies. Never a ranking or advice. */
@Serializable
data class ComparisonObservation(val metric: String, val text: String, val caveat: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComparisonResponse(
    @EncodeDefault val companies: List<ComparedCompany> = emptyList(),
    @EncodeDefault val metrics: List<MetricDefinition> = emptyList(),
    @EncodeDefault val observations: List<ComparisonObservation> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList(),
    val asOf: String,
    val sampleData: Boolean = false,
    /** Exchange rates the server used for converted amounts (market cap ≈ USD), with date and source. Additive (Phase 2). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fx: List<FxConversion> = emptyList()
)

@Serializable enum class PerformancePeriod(val label: String, val months: Int) {
    ONE_MONTH("1M", 1), THREE_MONTHS("3M", 3), ONE_YEAR("1Y", 12), THREE_YEARS("3Y", 36), FIVE_YEARS("5Y", 60);
    companion object { fun parse(value: String?) = entries.firstOrNull { it.label.equals(value, ignoreCase = true) } }
}

@Serializable enum class ReturnKind { PRICE_RETURN, TOTAL_RETURN }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PerformanceSeries(
    val symbol: String,
    /** Growth of 100, aligned with [PerformanceComparison.dates]; null = no observation (gap). */
    @EncodeDefault val values: List<Double?> = emptyList(),
    /** Percent change over the period, or null when history doesn't cover it. */
    val change: Double? = null,
    val currency: String? = null,
    val error: String? = null,
    /** A caveat that doesn't prevent drawing (e.g. history ends before the period does). */
    val note: String? = null,
    /** Date of the last real close used for [change]. */
    val lastDate: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PerformanceComparison(
    val period: PerformancePeriod,
    val kind: ReturnKind,
    @EncodeDefault val dates: List<String> = emptyList(),
    @EncodeDefault val series: List<PerformanceSeries> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList(),
    /** The common start date every line is rebased to 100 on (the latest first trading day among the companies). */
    val baseDate: String? = null
)

const val MAX_COMPARED_COMPANIES = 4
