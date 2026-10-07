package org.example.stocksteps.companydetail

import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.roundToLong

/** One At-a-Glance metric: number → short context. [educationId] opens a plain-English explanation. */
data class GlanceMetric(
    val id: String,
    val label: String,
    val value: String,
    val helper: String,
    val direction: PriceDirection? = null,
    val educationId: String? = null
)

/** Factual assessment line; [comparison] is a deterministic comparison, never a rating. */
data class AssessmentRow(val title: String, val detail: String, val comparison: String? = null)

data class InfoRow(val label: String, val value: String, val helper: String? = null)

data class EvidenceRow(val label: String, val value: String)

data class ValuationSummary(
    val currentPe: String?,
    val historicalAverage: String?,
    val difference: String?,
    val explanation: String
)

data class CompanyOverview(
    val symbol: String,
    val name: String,
    val listing: String,
    val logoUrl: String?,
    val price: String?,
    val changeAmount: String?,
    val changePercent: String,
    val direction: PriceDirection,
    val marketStatus: MarketStatus,
    val glance: List<GlanceMetric>,
    val assessment: List<AssessmentRow>,
    val insight: String?,
    val insightEvidence: List<EvidenceRow>,
    val about: String?,
    val classification: String?,
    val highlights: List<InfoRow>,
    val valuation: ValuationSummary?,
    /** Sections the backend could not load (e.g. "fundamentals"); shown as compact unavailable states. */
    val unavailable: Set<String>
)

/**
 * Company Details presentation. Every sentence is assembled from normalized facts with fixed
 * rules: no scores, no AI judgement, no buy/sell language. Missing facts are omitted, never
 * replaced with zero.
 */
object CompanyOverviewPresenter {
    private const val ABOUT_LIMIT = 280
    private const val NEAR_HISTORY_PERCENT = 5.0

    fun build(details: CompanyDetails): CompanyOverview {
        val profile = details.profile
        val quote = details.quote
        val facts = details.fundamentals?.financials?.metrics().orEmpty() + details.fundamentals?.valuation?.metrics.orEmpty()
        fun number(id: String) = facts[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.let { it.value ?: it.amount?.toDouble() }
        val name = profile?.companyName?.takeIf { it.isNotBlank() } ?: quote?.companyName?.takeIf { it.isNotBlank() } ?: details.symbol
        val shortName = shortName(name)
        val currency = profile?.currency ?: "USD"

        val revenueGrowth = number("revenueGrowth")
        val netMargin = number("netMargin")
        val pe = number("pe")
        val history = details.fundamentals?.valuation?.historical?.get("pe")?.takeIf { it.reliable && it.average != null }
        val difference = history?.differencePercent

        return CompanyOverview(
            symbol = details.symbol,
            name = name,
            listing = listOfNotNull(details.symbol, profile?.exchange).joinToString(" • "),
            logoUrl = profile?.logoUrl,
            price = quote?.price?.let { HomePresentation.price(it, currency) },
            changeAmount = quote?.change?.takeIf { it.isFinite() }?.let { signedMoney(it, currency) },
            changePercent = HomePresentation.percent(quote?.changePercent),
            direction = HomePresentation.direction(quote?.changePercent),
            marketStatus = details.marketStatus,
            glance = glance(quote, facts, pe, revenueGrowth, number("dividendYield")),
            assessment = assessment(revenueGrowth, netMargin, number("cash"), number("debt"), pe, history, difference),
            insight = insight(shortName, revenueGrowth, netMargin, pe, difference),
            insightEvidence = listOfNotNull(
                number("revenue")?.let { EvidenceRow("Revenue (latest year)", money(it)) },
                revenueGrowth?.let { EvidenceRow("Revenue growth vs prior year", signedPercent(it)) },
                netMargin?.let { EvidenceRow("Net margin", percent(it)) },
                pe?.let { EvidenceRow("Current P/E", decimal(it, 1)) },
                history?.average?.let { EvidenceRow("P/E ${history.validCount}-year average", decimal(it, 1)) },
                difference?.let { EvidenceRow("Difference from average", signedPercent(it, 0)) }
            ),
            about = profile?.description?.let(::concise),
            classification = listOfNotNull(profile?.sector, profile?.industry).joinToString(" • ").takeIf { it.isNotBlank() },
            highlights = listOfNotNull(
                number("revenue")?.let { InfoRow("Revenue", money(it)) },
                revenueGrowth?.let { InfoRow("Revenue growth", signedPercent(it)) },
                number("netIncome")?.let { InfoRow("Net income", money(it)) },
                number("netIncomeGrowth")?.let { InfoRow("Net income growth", signedPercent(it)) },
                netMargin?.let { InfoRow("Net margin", percent(it), "$shortName keeps about $${it.roundToLong()} in profit for every $100 of revenue.") },
                number("freeCashFlow")?.let { InfoRow("Free cash flow", money(it)) }
            ),
            valuation = valuation(shortName, pe, history, difference, details.fundamentals != null),
            unavailable = details.errors.map { it.section }.toSet()
        )
    }

    private fun glance(quote: StockQuote?, facts: Map<String, FinancialFact>, pe: Double?, growth: Double?, dividend: Double?): List<GlanceMetric> {
        val marketCap = quote?.marketCap?.takeIf { it > 0 }?.toDouble()
        val dividendFact = facts["dividendYield"]
        return listOf(
            GlanceMetric("marketCap", "Market Cap", marketCap?.let(::money) ?: "—", marketCap?.let(::sizeLabel) ?: "Not available", educationId = "marketCap"),
            GlanceMetric("pe", "P/E", pe?.let { decimal(it, 1) } ?: "N/A",
                pe?.let { "${it.roundToLong()}x earnings" } ?: "No positive earnings or data", educationId = "pe"),
            GlanceMetric("revenueGrowth", "Revenue Growth", growth?.let { signedPercent(it) } ?: "—",
                if (growth != null) "vs last year" else "Not available", HomePresentation.direction(growth).takeIf { growth != null }, "revenueGrowth"),
            when {
                dividendFact?.availability == FinancialAvailability.NO_DIVIDEND ->
                    GlanceMetric("dividendYield", "Dividend Yield", "No dividend", "Doesn't pay one", educationId = "dividendYield")
                dividend != null -> GlanceMetric("dividendYield", "Dividend Yield", percent(dividend), "per year", educationId = "dividendYield")
                else -> GlanceMetric("dividendYield", "Dividend Yield", "—", "Not available", educationId = "dividendYield")
            }
        )
    }

    private fun assessment(growth: Double?, margin: Double?, cash: Double?, debt: Double?, pe: Double?, history: HistoricalComparison?, difference: Double?) =
        listOfNotNull(
            growth?.let { AssessmentRow("Growth", "Revenue ${signedPercent(it)} vs last year") },
            margin?.let { AssessmentRow("Profitability", "Net margin ${percent(it)}") },
            if (cash != null || debt != null) AssessmentRow("Financial health",
                listOfNotNull(cash?.let { "Cash ${money(it)}" }, debt?.let { "Debt ${money(it)}" }).joinToString(" • ")) else null,
            pe?.let {
                AssessmentRow("Valuation", listOfNotNull("P/E ${decimal(it, 1)}", history?.average?.let { avg -> "${history.validCount}Y avg ${decimal(avg, 1)}" }).joinToString(" • "),
                    difference?.let(::historyComparison))
            }
        )

    /** Factual sentences only; each clause appears only when its facts exist. */
    private fun insight(name: String, growth: Double?, margin: Double?, pe: Double?, difference: Double?): String? {
        val parts = buildList {
            if (growth != null && margin != null) add("Over the latest year, $name's revenue ${if (growth >= 0) "grew" else "fell"} ${percent(abs(growth))} and it kept about $${margin.roundToLong()} of every $100 in revenue as profit.")
            else if (growth != null) add("Over the latest year, $name's revenue ${if (growth >= 0) "grew" else "fell"} ${percent(abs(growth))}.")
            else if (margin != null) add("$name kept about $${margin.roundToLong()} of every $100 in revenue as profit.")
            if (pe != null && difference != null) {
                val position = when {
                    abs(difference) < NEAR_HISTORY_PERCENT -> "close to its own recent average"
                    difference > 0 -> "${percent(difference, 0)} above its own recent average, so investors are paying more for each dollar of earnings than they have recently"
                    else -> "${percent(abs(difference), 0)} below its own recent average, so investors are paying less for each dollar of earnings than they have recently"
                }
                add("Its P/E of ${decimal(pe, 1)} is $position.")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")?.plus(" This is context, not a recommendation.")
    }

    private fun valuation(name: String, pe: Double?, history: HistoricalComparison?, difference: Double?, fundamentalsLoaded: Boolean): ValuationSummary? {
        if (!fundamentalsLoaded) return null
        val explanation = when {
            pe == null -> "P/E isn't shown when earnings are negative or the data is unavailable."
            difference == null -> "A comparison with $name's own history isn't available yet."
            abs(difference) < NEAR_HISTORY_PERCENT -> "$name is trading close to its own historical P/E."
            difference > 0 -> "$name is currently trading above its own historical P/E."
            else -> "$name is currently trading below its own historical P/E."
        }
        return ValuationSummary(
            currentPe = pe?.let { decimal(it, 1) },
            historicalAverage = history?.average?.let { decimal(it, 1) },
            difference = difference?.let { signedPercent(it, 0) },
            explanation = explanation
        )
    }

    private fun historyComparison(difference: Double) = when {
        abs(difference) < NEAR_HISTORY_PERCENT -> "Near its history"
        difference > 0 -> "Above its history"
        else -> "Below its history"
    }

    /** Standard market-cap size bands (descriptive, not a judgement). */
    private fun sizeLabel(cap: Double) = when {
        cap >= 200e9 -> "Mega-cap company"
        cap >= 10e9 -> "Large-cap company"
        cap >= 2e9 -> "Mid-cap company"
        cap >= 300e6 -> "Small-cap company"
        else -> "Micro-cap company"
    }

    private fun shortName(name: String) =
        name.substringBefore(",").removeSuffix(" Corporation").removeSuffix(" Inc.").removeSuffix(" Inc").trim().ifEmpty { name }

    /** First sentences up to [ABOUT_LIMIT] characters; never cuts mid-sentence unless one sentence is too long. */
    private fun concise(description: String): String? {
        val text = description.trim().takeIf { it.isNotEmpty() } ?: return null
        if (text.length <= ABOUT_LIMIT) return text
        val sentences = Regex("(?<=[.!?])\\s+").split(text)
        val kept = StringBuilder()
        for (sentence in sentences) {
            if (kept.length + sentence.length + 1 > ABOUT_LIMIT) break
            if (kept.isNotEmpty()) kept.append(' ')
            kept.append(sentence)
        }
        return kept.toString().ifEmpty { text.take(ABOUT_LIMIT - 1).trimEnd() + "…" }
    }

    fun money(value: Double): String {
        val absolute = abs(value)
        val (scaled, suffix, digits) = when {
            absolute >= 1e12 -> Triple(absolute / 1e12, "T", 2)
            absolute >= 1e9 -> Triple(absolute / 1e9, "B", 1)
            absolute >= 1e6 -> Triple(absolute / 1e6, "M", 1)
            else -> Triple(absolute, "", 2)
        }
        return "${if (value < 0) "-" else ""}$${decimal(scaled, digits).removeSuffix(".0")}$suffix"
    }

    private fun signedMoney(value: Double, currency: String): String {
        val amount = HomePresentation.price(abs(value), currency) ?: return ""
        return when {
            value > 0 -> "+$amount"
            value < 0 -> "-$amount"
            else -> amount
        }
    }

    private fun percent(value: Double, digits: Int = 1) = "${decimal(value, digits)}%"
    private fun signedPercent(value: Double, digits: Int = 1) = "${if (value > 0) "+" else if (value < 0) "-" else ""}${decimal(abs(value), digits)}%"

    private fun decimal(value: Double, digits: Int): String {
        val factor = when (digits) { 0 -> 1L; 1 -> 10L; else -> 100L }
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor
        val sign = if (value < 0 && scaled > 0) "-" else ""
        return if (digits == 0) "$sign$whole" else "$sign$whole.${(scaled % factor).toString().padStart(digits, '0')}"
    }
}
