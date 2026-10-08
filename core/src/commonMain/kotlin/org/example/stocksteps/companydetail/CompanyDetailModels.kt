package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.round

enum class CompanyDetailTab(val title: String) { OVERVIEW("Overview"), FINANCIALS("Financials"), VALUATION("Valuation"), NEWS("News") }
enum class ChartPeriod(val label: String) { DAY("1D"), WEEK("1W"), MONTH("1M"), QUARTER("3M"), YEAR("1Y"), FIVE_YEARS("5Y") }
enum class FinancialPeriod(val label: String) { ANNUAL("Annual"), QUARTERLY("Quarterly") }
enum class SectionStatus { LOADING, SUCCESS, EMPTY, ERROR }
enum class PriceDirection { UP, DOWN, UNCHANGED, UNAVAILABLE }
data class ChartPoint(val label: String, val value: Double)
data class FinancialChartState(
    val period: ChartPeriod = ChartPeriod.MONTH,
    val status: SectionStatus = SectionStatus.EMPTY,
    val points: List<ChartPoint> = emptyList(),
    val message: String = "Price history is not available from the current integration."
)
data class SnapshotScore(val category: String, val score: Double? = null, val context: String? = null)
data class CompanySnapshot(val scores: List<SnapshotScore> = listOf("Growth", "Profitability", "Financial health", "Valuation", "Cash flow").map { SnapshotScore(it) }, val overall: Double? = null)
data class CompanyRisk(val title: String, val explanation: String, val source: String)
data class FinancialMetric(val id: String, val label: String, val explanation: String, val unit: String = "ratio", val value: Double? = null, val historicalAverage: Double? = null, val industryAverage: Double? = null, val context: String? = null, val historicalLabel: String = "5Y average", val historicalRange: String? = null, val calculation: String? = null, val why: String? = null)
data class FinancialSection(val title: String, val metrics: List<FinancialMetric>, val explanation: String? = null)
data class CompanyDetailUiState(
    val name: String = "",
    val symbol: String = "",
    val listing: String = "",
    val sector: String? = null,
    val industry: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val price: String = "Unavailable",
    val priceChange: String = "Change unavailable",
    val direction: PriceDirection = PriceDirection.UNAVAILABLE,
    val keyMetrics: List<FinancialMetric> = emptyList(),
    val financials: List<FinancialSection> = emptyList(),
    val valuation: List<FinancialMetric> = emptyList(),
    val snapshot: CompanySnapshot = CompanySnapshot(),
    val risks: List<CompanyRisk> = emptyList()
)

/** Shared presentation mapping. No thresholds, inferred scores, or fabricated financial facts. */
object CompanyDetailPresenter {
    fun build(stock: StockSearchResult, quote: StockQuote?, profile: CompanyProfile?, fundamentals: CompanyFundamentals? = null): CompanyDetailUiState {
        val marketCap = MetricEducation.metric("marketCap").copy(value = quote?.marketCap?.toDouble()?.takeIf { it >= 0 })
        val facts = fundamentals?.financials?.metrics().orEmpty() + fundamentals?.valuation?.metrics.orEmpty()
        fun populate(metric: FinancialMetric): FinancialMetric {
            val fact = facts[metric.id] ?: return metric
            val history = fundamentals?.valuation?.historical?.get(metric.id)
            val report = fact.basis
            val context = listOfNotNull(
                report?.let { listOfNotNull(it.period, it.date, it.currency).joinToString(" · ") },
                history?.differencePercent?.let { "${number(abs(it))}% ${if (it >= 0) "above" else "below"} historical average." },
            ).joinToString(" ").takeIf { it.isNotBlank() }
            return metric.copy(
                value = fact.numericValue()?.takeIf { it.isFinite() },
                historicalAverage = history?.average?.takeIf { history.reliable },
                historicalLabel = if (history != null && history.validCount < 5) "${history.validCount} valid-year average" else "5Y average",
                historicalRange = history?.minimum?.let { "Fiscal-year range: ${number(it)}–${number(history.maximum)} (${history.validCount}/5 valid years)" },
                context = context
            )
        }
        val all = facts.mapNotNull { (id, fact) -> fact.numericValue()?.let { id to it } }.toMap()
        val snapshots = listOf(
            "Growth" to listOf("revenueGrowth", "epsGrowth"),
            "Profitability" to listOf("netMargin", "roic"),
            "Financial health" to listOf("debtEquity", "currentRatio"),
            "Valuation" to listOf("pe", "priceFcf"),
            "Cash flow" to listOf("freeCashFlow", "fcfMargin")
        ).map { (category, ids) -> SnapshotScore(category, context = ids.mapNotNull { id ->
            all[id]?.let { populate(MetricEducation.metric(id)) }?.let { "${it.label}: ${metricValue(it)}" }
        }.joinToString(" · ").takeIf { it.isNotBlank() }) }
        return CompanyDetailUiState(
            name = profile?.companyName?.takeIf { it.isNotBlank() } ?: quote?.companyName?.takeIf { it.isNotBlank() } ?: stock.name,
            symbol = stock.symbol,
            listing = listOfNotNull(profile?.exchange ?: stock.exchange, profile?.currency ?: stock.currency).joinToString(" · "),
            sector = profile?.sector,
            industry = profile?.industry,
            logoUrl = profile?.logoUrl,
            description = profile?.description,
            price = money(quote?.price, profile?.currency ?: stock.currency),
            priceChange = changeText(quote?.change, quote?.changePercent, profile?.currency ?: stock.currency),
            direction = direction(quote?.changePercent ?: quote?.change),
            keyMetrics = listOf(marketCap) + listOf("pe", "forwardPe", "revenueGrowth", "epsGrowth", "roic").map { populate(MetricEducation.metric(it)) },
            financials = MetricEducation.financialSections().map { it.copy(metrics = it.metrics.map(::populate)) },
            valuation = listOf("pe", "forwardPe", "peg", "priceSales", "priceBook", "priceFcf", "enterpriseValue", "evEbitda").map { populate(MetricEducation.metric(it)) },
            snapshot = CompanySnapshot(scores = snapshots)
        )
    }
    fun direction(value: Double?): PriceDirection = when {
        value == null || !value.isFinite() -> PriceDirection.UNAVAILABLE
        value > 0 -> PriceDirection.UP
        value < 0 -> PriceDirection.DOWN
        else -> PriceDirection.UNCHANGED
    }
    fun number(value: Double?): String {
        if (value == null || !value.isFinite()) return "Unavailable"
        val scaled = round(abs(value) * 100).toLong()
        val sign = if (value < 0 && scaled > 0) "−" else ""
        return "$sign${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
    }
    fun money(value: Double?, currency: String?): String = if (value == null || !value.isFinite()) "Unavailable" else "${number(value)} ${currency.orEmpty()}".trim()
    fun percentage(value: Double?): String = if (value == null || !value.isFinite()) "Unavailable" else "${if (value > 0) "+" else ""}${number(value)}%"
    fun changeText(change: Double?, percent: Double?, currency: String?): String {
        val prefix = when (direction(percent ?: change)) { PriceDirection.UP -> "↑ "; PriceDirection.DOWN -> "↓ "; PriceDirection.UNCHANGED -> "→ "; else -> "" }
        return prefix + (change?.takeIf { it.isFinite() }?.let { "${if (it > 0) "+" else ""}${money(it, currency)}" } ?: "Change unavailable") + " (${percentage(percent)})"
    }
    fun metricValue(metric: FinancialMetric): String = when (metric.unit) {
        "percent" -> percentage(metric.value)
        "money", "count" -> metric.value?.takeIf { it.isFinite() }?.let { compact(it) } ?: "Unavailable"
        else -> number(metric.value)
    }
    private fun compact(value: Double): String = when {
        abs(value) >= 1e12 -> "${number(value / 1e12)}T"
        abs(value) >= 1e9 -> "${number(value / 1e9)}B"
        abs(value) >= 1e6 -> "${number(value / 1e6)}M"
        else -> number(value)
    }
    fun historicalContext(metric: FinancialMetric): String? {
        val current = metric.value?.takeIf { it.isFinite() } ?: return null
        val historical = metric.historicalAverage?.takeIf { it.isFinite() && it > 0 } ?: return null
        if (current <= 0) return null
        val difference = (current / historical - 1) * 100
        return if (abs(difference) < 0.005) "Current ${metric.label} matches its ${metric.historicalLabel.lowercase()}." else
            "Current ${metric.label} is ${number(abs(difference))}% ${if (difference > 0) "above" else "below"} its ${metric.historicalLabel.lowercase()}. This comparison alone does not establish fair value."
    }
}
