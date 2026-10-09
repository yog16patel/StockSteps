package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.round

enum class MetricAvailability { AVAILABLE, NOT_REPORTED, INSUFFICIENT_HISTORY, NOT_APPLICABLE, TEMPORARILY_UNAVAILABLE }
data class FinancialTile(
    val id: String,
    val label: String,
    val value: String,
    val explanation: String,
    val availability: MetricAvailability,
    val reportingPeriod: String? = null,
    val movement: String? = null,
    val direction: PriceDirection = PriceDirection.UNAVAILABLE
)
data class FinancialSectionState(
    val title: String,
    val status: SectionStatus,
    val metrics: List<FinancialTile> = emptyList(),
    val secondary: List<FinancialTile> = emptyList(),
    val insights: List<String> = emptyList(),
    val message: String? = null,
    val explanation: String? = null,
    val cashRelationship: Boolean = false
)
data class FinancialsUiState(
    val sections: List<FinancialSectionState> = emptyList(),
    val refreshMessage: String? = null
)

/** Display formatting only. Backend percentage facts already use percentage points. */
object FinancialsFormatter {
    private fun decimal(value: Double, digits: Int): String {
        val factor = if (digits == 0) 1 else if (digits == 1) 10 else 100
        val scaled = round(abs(value) * factor).toLong()
        val sign = if (value < 0 && scaled > 0) "−" else ""
        return sign + (scaled / factor) + if (digits == 0) "" else "." + (scaled % factor).toString().padStart(digits, '0')
    }
    fun compact(value: Double): String {
        val (scaled, suffix) = when {
            abs(value) >= 1e12 -> value / 1e12 to "T"
            abs(value) >= 1e9 -> value / 1e9 to "B"
            abs(value) >= 1e6 -> value / 1e6 to "M"
            abs(value) >= 1e3 -> value / 1e3 to "K"
            else -> value to ""
        }
        return decimal(scaled, if (suffix.isEmpty() || round(scaled) == scaled) 0 else 1) + suffix
    }
    fun value(id: String, unit: String, value: Double?, currency: String?): String {
        if (value == null || !value.isFinite()) return "—"
        val prefix = if (currency == "USD") "$" else if (currency == null) "" else "$currency "
        return when {
            id in listOf("eps", "dividendPerShare") -> prefix + decimal(value, 2)
            unit == "money" -> prefix + compact(value)
            unit == "count" -> compact(value)
            unit == "percent" -> decimal(value, 1) + "%"
            else -> decimal(value, 2)
        }
    }
    fun movement(value: Double): String = "${if (value > 0) "↑" else if (value < 0) "↓" else "→"} ${decimal(abs(value), 1)}% YoY"
    fun profitContext(margin: Double): String = when {
        margin > 0 -> "The company keeps about ${decimal(margin, 0)} in net profit for every 100 of revenue, in the same currency."
        margin < 0 -> "The company loses about ${decimal(abs(margin), 0)} for every 100 of revenue, in the same currency."
        else -> "The company breaks even on net profit for every 100 of revenue."
    }
}

/** Deterministic, provider-independent presentation shared with native SwiftUI. */
object FinancialsPresenter {
    fun build(fundamentals: CompanyFundamentals?, loading: Boolean, failed: Boolean): FinancialsUiState {
        val facts = fundamentals?.financials?.metrics().orEmpty()
        fun numeric(id: String) = facts[id]?.numericValue()?.takeIf { it.isFinite() }
        fun availability(fact: FinancialFact?): MetricAvailability = when {
            fact?.numericValue()?.isFinite() == true -> MetricAvailability.AVAILABLE
            fact?.availability == FinancialAvailability.INSUFFICIENT_HISTORY -> MetricAvailability.INSUFFICIENT_HISTORY
            fact?.availability in listOf(FinancialAvailability.NO_DIVIDEND, FinancialAvailability.NON_POSITIVE_DENOMINATOR) -> MetricAvailability.NOT_APPLICABLE
            fact?.availability == FinancialAvailability.TEMPORARILY_UNAVAILABLE -> MetricAvailability.TEMPORARILY_UNAVAILABLE
            else -> MetricAvailability.NOT_REPORTED
        }
        fun sameBasis(first: String, second: String): Boolean {
            val a = facts[first]?.basis ?: return false
            val b = facts[second]?.basis ?: return false
            return a == b
        }
        fun tile(id: String): FinancialTile {
            val education = MetricEducation.metric(id)
            val fact = facts[id]
            val growthId = mapOf("revenue" to "revenueGrowth", "netIncome" to "netIncomeGrowth", "eps" to "epsGrowth")[id]
            val movement = if (growthId != null && sameBasis(id, growthId)) numeric(growthId) else fact?.changePercent?.takeIf { it.isFinite() }
            val report = fact?.basis?.let {
                val period = when (it.period.lowercase()) {
                    "annual", "fy" -> "Annual"
                    "ttm" -> "Trailing 12 months"
                    "quarter" -> "Quarterly"
                    "q1", "q2", "q3", "q4" -> it.period.uppercase()
                    else -> "Reported"
                }
                listOfNotNull(period, it.date, it.currency).joinToString(" · ")
            }
            return FinancialTile(id, if (id == "capex") "Capital spending" else education.label,
                FinancialsFormatter.value(id, education.unit, numeric(id), fact?.basis?.currency),
                education.explanation, availability(fact), report,
                movement?.let(FinancialsFormatter::movement), CompanyDetailPresenter.direction(movement))
        }
        val specs = listOf(
            Triple("Growth", listOf("revenue", "netIncome", "eps"), listOf("revenueCagr3", "revenueCagr5", "epsCagr3", "epsCagr5")),
            Triple("Profitability", listOf("grossMargin", "operatingMargin", "netMargin", "roic"), listOf("roe", "roa")),
            Triple("Financial health", listOf("cash", "debt", "debtEquity", "currentRatio"), listOf("quickRatio", "interestCoverage")),
            Triple("Cash flow", listOf("operatingCashFlow", "capex", "freeCashFlow", "fcfMargin"), emptyList()),
            Triple("Shareholders", listOf("dividendYield", "dividendGrowth", "buybacks", "shares"), listOf("dividendPerShare", "payoutRatio", "sharesChange5"))
        )
        return FinancialsUiState(specs.map { (title, primary, secondary) ->
            val metrics = primary.filter { numeric(it) != null && !(it == "dividendYield" && numeric(it) == 0.0) }.map(::tile)
            // Zero coverage from older servers can be a missing-data sentinel; omit this optional tile.
            val extras = secondary.filter { numeric(it) != null && !(it == "interestCoverage" && numeric(it) == 0.0) }.map(::tile)
            val insights = mutableListOf<String>()
            if (title == "Growth") {
                val revenue = numeric("revenueGrowth")
                val earnings = numeric("netIncomeGrowth")
                val eps = numeric("epsGrowth")
                if (revenue != null && earnings != null && revenue > 0 && earnings > 0 &&
                    sameBasis("revenueGrowth", "netIncomeGrowth") && numeric("netIncome")?.let { it > 0 } == true) {
                    insights += "Revenue and earnings both grew versus the same period last year."
                }
                if (revenue != null && eps != null && eps > 0 && eps > revenue &&
                    sameBasis("revenueGrowth", "epsGrowth") && numeric("eps")?.let { it > 0 } == true) {
                    insights += "Earnings per share grew faster than overall sales. Share count changes can also affect EPS."
                }
            }
            if (title == "Profitability") numeric("netMargin")?.let { insights += FinancialsFormatter.profitContext(it) }
            if (title == "Shareholders" && facts["dividendYield"]?.availability == FinancialAvailability.NO_DIVIDEND) {
                insights += "This company currently does not pay a dividend."
            }
            val available = metrics.isNotEmpty() || extras.isNotEmpty() || insights.isNotEmpty()
            FinancialSectionState(title,
                if (loading && !available) SectionStatus.LOADING else if (available) SectionStatus.SUCCESS else if (failed) SectionStatus.ERROR else SectionStatus.EMPTY,
                metrics, extras, insights,
                if (available) null else if (title == "Growth") "Historical financial data isn't available right now." else "These financial details aren't available right now.",
                when (title) {
                    "Growth" -> if (!available) "Some growth metrics require additional company financial history." else null
                    "Cash flow" -> "Free cash flow is the cash remaining after the investments needed to operate and grow the business."
                    else -> null
                },
                title == "Cash flow" && listOf("operatingCashFlow", "capex", "freeCashFlow").all { numeric(it) != null && sameBasis("operatingCashFlow", it) } &&
                    abs(numeric("operatingCashFlow")!! - numeric("capex")!! - numeric("freeCashFlow")!!) <= 2.0
            )
        }, refreshMessage = if (failed && fundamentals != null) "Couldn't refresh these figures. Showing previously loaded data."
            else FinancialStatementsPresenter.staleNotice(fundamentals))
    }
}
