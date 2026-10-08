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

/** Semantic tone for badges and status text; never a buy/sell signal. */
enum class FactTone { POSITIVE, NEUTRAL, CAUTION, NEGATIVE }

/**
 * Factual assessment line. [badge] is a short descriptive label from [AssessmentRules]
 * (documented thresholds), never a quality score or recommendation.
 */
data class AssessmentRow(
    val id: String,
    val title: String,
    val detail: String,
    val comparison: String? = null,
    val badge: String? = null,
    val tone: FactTone = FactTone.NEUTRAL,
    val explanation: String? = null
)

/** Label/value line; [change] is an optional signed change shown beside the value. */
data class InfoRow(
    val label: String,
    val value: String,
    val helper: String? = null,
    val change: String? = null,
    val changeDirection: PriceDirection? = null
)

data class EvidenceRow(val label: String, val value: String)

/** Where a value sits between a low and a high (day range, 52-week range). */
data class RangeSummary(val low: String, val high: String, val position: Float)

enum class ValuationPosition { ABOVE, NEAR, BELOW, UNKNOWN }

data class ValuationSummary(
    val currentPe: String?,
    val historicalAverage: String?,
    val historicalLabel: String,
    val difference: String?,
    val position: ValuationPosition,
    /** One-line headline such as "Trading above its historical P/E valuation." */
    val headline: String?,
    val explanation: String
)

data class CompanyOverview(
    val symbol: String,
    val name: String,
    val shortName: String,
    val listing: String,
    val logoUrl: String?,
    /** Sector, industry and size band, only when known. */
    val tags: List<String>,
    val price: String?,
    val changeAmount: String?,
    val changePercent: String,
    val direction: PriceDirection,
    val marketStatus: MarketStatus,
    /** Quote time (epoch seconds) for "Updated …"; formatted by the platform. */
    val updatedAt: Long?,
    /** Open, High, Low, Volume, Market Cap, P/E in that order; "—" when not reported. */
    val quickStats: List<InfoRow>,
    val dayRange: RangeSummary?,
    val yearRange: RangeSummary?,
    val glance: List<GlanceMetric>,
    val assessment: List<AssessmentRow>,
    val insight: String?,
    val insightEvidence: List<EvidenceRow>,
    val about: String?,
    /** Full description when [about] was shortened; null otherwise. */
    val aboutFull: String?,
    val classification: String?,
    val sector: String?,
    val industry: String?,
    val country: String?,
    val website: String?,
    val highlights: List<InfoRow>,
    val highlightsBasis: String?,
    val keyRatios: List<InfoRow>,
    val valuation: ValuationSummary?,
    /** Sections the backend could not load (e.g. "fundamentals"); shown as compact unavailable states. */
    val unavailable: Set<String>
)

/**
 * Descriptive labels for the "How does it look?" rows. Each label restates a single fact
 * against a fixed, published threshold; none of them rate the company or suggest an action.
 *
 * | Row              | Label                  | Rule                                  |
 * |------------------|------------------------|---------------------------------------|
 * | Growth           | Growing fast           | revenue growth ≥ 10% vs prior year    |
 * |                  | Growing                | 0% ≤ growth < 10%                     |
 * |                  | Shrinking              | growth < 0%                           |
 * | Profitability    | High margin            | net margin ≥ 20%                      |
 * |                  | Profitable             | 5% ≤ net margin < 20%                 |
 * |                  | Thin margin            | 0% ≤ net margin < 5%                  |
 * |                  | Losing money           | net margin < 0%                       |
 * | Financial health | More cash than debt    | cash ≥ debt                           |
 * |                  | More debt than cash    | cash < debt                           |
 * | Valuation        | Above / Near / Below history | current P/E vs own average, ±5% band |
 */
object AssessmentRules {
    const val FAST_GROWTH_PERCENT = 10.0
    const val HIGH_MARGIN_PERCENT = 20.0
    const val THIN_MARGIN_PERCENT = 5.0
    const val NEAR_HISTORY_PERCENT = 5.0

    fun growth(percent: Double): Pair<String, FactTone> = when {
        percent >= FAST_GROWTH_PERCENT -> "Growing fast" to FactTone.POSITIVE
        percent >= 0 -> "Growing" to FactTone.POSITIVE
        else -> "Shrinking" to FactTone.NEGATIVE
    }

    fun profitability(margin: Double): Pair<String, FactTone> = when {
        margin >= HIGH_MARGIN_PERCENT -> "High margin" to FactTone.POSITIVE
        margin >= THIN_MARGIN_PERCENT -> "Profitable" to FactTone.POSITIVE
        margin >= 0 -> "Thin margin" to FactTone.CAUTION
        else -> "Losing money" to FactTone.NEGATIVE
    }

    fun health(cash: Double, debt: Double): Pair<String, FactTone> =
        if (cash >= debt) "More cash than debt" to FactTone.POSITIVE else "More debt than cash" to FactTone.CAUTION

    fun valuation(difference: Double): ValuationPosition = when {
        abs(difference) < NEAR_HISTORY_PERCENT -> ValuationPosition.NEAR
        difference > 0 -> ValuationPosition.ABOVE
        else -> ValuationPosition.BELOW
    }
}

/**
 * Company Details presentation. Every sentence is assembled from normalized facts with fixed
 * rules: no scores, no AI judgement, no buy/sell language. Missing facts are omitted, never
 * replaced with zero.
 */
object CompanyOverviewPresenter {
    private const val ABOUT_LIMIT = 280
    private const val UNAVAILABLE = "—"

    fun build(details: CompanyDetails): CompanyOverview {
        val profile = details.profile
        val quote = details.quote
        val facts = details.fundamentals?.financials?.metrics().orEmpty() + details.fundamentals?.valuation?.metrics.orEmpty()
        fun number(id: String) = facts[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.let { it.value ?: it.amount?.toDouble() }
        val name = profile?.companyName?.takeIf { it.isNotBlank() } ?: quote?.companyName?.takeIf { it.isNotBlank() } ?: details.symbol
        val shortName = shortName(name)
        val currency = profile?.currency ?: "USD"
        fun price(value: Double?) = value?.takeIf { it.isFinite() && it > 0 }?.let { HomePresentation.price(it, currency) }

        val revenueGrowth = number("revenueGrowth")
        val netMargin = number("netMargin")
        val pe = number("pe")
        val history = details.fundamentals?.valuation?.historical?.get("pe")?.takeIf { it.reliable && it.average != null }
        val difference = history?.differencePercent
        val marketCap = quote?.marketCap?.takeIf { it > 0 }?.toDouble()
        val about = profile?.description?.let(::concise)
        val revenueBasis = facts["revenue"]?.basis

        return CompanyOverview(
            symbol = details.symbol,
            name = name,
            shortName = shortName,
            listing = listOfNotNull(details.symbol, profile?.exchange).joinToString(" • "),
            logoUrl = profile?.logoUrl,
            tags = listOfNotNull(profile?.sector?.takeIf { it.isNotBlank() }, profile?.industry?.takeIf { it.isNotBlank() }, marketCap?.let(::sizeTag)).distinct(),
            price = quote?.price?.let { HomePresentation.price(it, currency) },
            changeAmount = quote?.change?.takeIf { it.isFinite() }?.let { signedMoney(it, currency) },
            changePercent = HomePresentation.percent(quote?.changePercent),
            direction = HomePresentation.direction(quote?.changePercent),
            marketStatus = details.marketStatus,
            updatedAt = quote?.timestamp?.takeIf { it > 0 },
            quickStats = listOf(
                InfoRow("Open", price(quote?.open) ?: UNAVAILABLE),
                InfoRow("High", price(quote?.dayHigh) ?: UNAVAILABLE),
                InfoRow("Low", price(quote?.dayLow) ?: UNAVAILABLE),
                InfoRow("Volume", quote?.volume?.takeIf { it > 0 }?.let { compact(it.toDouble()) } ?: UNAVAILABLE),
                InfoRow("Market Cap", marketCap?.let(::money) ?: UNAVAILABLE),
                InfoRow("P/E", pe?.let { decimal(it, 1) } ?: "N/A")
            ),
            dayRange = range(quote?.dayLow, quote?.dayHigh, quote?.price, ::price),
            yearRange = range(quote?.yearLow, quote?.yearHigh, quote?.price, ::price),
            glance = glance(marketCap, facts, pe, revenueGrowth, number("dividendYield")),
            assessment = assessment(shortName, revenueGrowth, netMargin, number("cash"), number("debt"), pe, history, difference),
            insight = insight(shortName, revenueGrowth, netMargin, pe, difference),
            insightEvidence = listOfNotNull(
                number("revenue")?.let { EvidenceRow("Revenue (latest year)", money(it)) },
                revenueGrowth?.let { EvidenceRow("Revenue growth vs prior year", signedPercent(it)) },
                netMargin?.let { EvidenceRow("Net margin", percent(it)) },
                pe?.let { EvidenceRow("Current P/E", decimal(it, 1)) },
                history?.average?.let { EvidenceRow("P/E ${history.validCount}-year average", decimal(it, 1)) },
                difference?.let { EvidenceRow("Difference from average", signedPercent(it, 0)) }
            ),
            about = about,
            aboutFull = profile?.description?.trim()?.takeIf { about != null && it != about },
            classification = listOfNotNull(profile?.sector, profile?.industry).joinToString(" • ").takeIf { it.isNotBlank() },
            sector = profile?.sector?.takeIf { it.isNotBlank() },
            industry = profile?.industry?.takeIf { it.isNotBlank() },
            country = profile?.country?.takeIf { it.isNotBlank() }?.let(::countryName),
            website = profile?.website?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            highlights = listOfNotNull(
                number("revenue")?.let { InfoRow("Revenue", money(it), change = revenueGrowth?.let(::signedPercent), changeDirection = revenueGrowth?.let(HomePresentation::direction)) },
                number("netIncome")?.let {
                    val growth = number("netIncomeGrowth")
                    InfoRow("Net Income", money(it), change = growth?.let(::signedPercent), changeDirection = growth?.let(HomePresentation::direction))
                },
                netMargin?.let { InfoRow("Net Margin", percent(it), helper = "$shortName keeps about $${it.roundToLong()} in profit for every $100 of revenue.") },
                number("freeCashFlow")?.let { InfoRow("Free Cash Flow", money(it)) }
            ),
            highlightsBasis = revenueBasis?.let { basis ->
                val period = if (basis.period.equals("quarter", ignoreCase = true)) "Latest quarter" else "Latest fiscal year"
                listOfNotNull(basis.fiscalYear?.let { "$period ($it)" } ?: period, "Change vs prior year").joinToString(" • ")
            },
            keyRatios = listOfNotNull(
                pe?.let { InfoRow("P/E Ratio", decimal(it, 1)) },
                number("priceSales")?.let { InfoRow("Price to Sales", decimal(it, 1)) },
                number("eps")?.let { InfoRow("Earnings per Share (EPS)", HomePresentation.price(it, currency) ?: decimal(it, 2)) },
                number("grossMargin")?.let { InfoRow("Gross Margin", percent(it)) },
                number("operatingMargin")?.let { InfoRow("Operating Margin", percent(it)) },
                number("debtEquity")?.let { InfoRow("Debt to Equity", decimal(it, 2)) },
                number("currentRatio")?.let { InfoRow("Current Ratio", decimal(it, 2)) },
                when {
                    facts["dividendYield"]?.availability == FinancialAvailability.NO_DIVIDEND -> InfoRow("Dividend Yield", "No dividend")
                    else -> number("dividendYield")?.let { InfoRow("Dividend Yield", percent(it)) }
                }
            ),
            valuation = valuation(shortName, pe, history, difference, details.fundamentals != null),
            unavailable = details.errors.map { it.section }.toSet()
        )
    }

    private fun glance(marketCap: Double?, facts: Map<String, FinancialFact>, pe: Double?, growth: Double?, dividend: Double?): List<GlanceMetric> {
        val dividendFact = facts["dividendYield"]
        return listOf(
            GlanceMetric("marketCap", "Market Cap", marketCap?.let(::money) ?: UNAVAILABLE, marketCap?.let(::sizeLabel) ?: "Not available", educationId = "marketCap"),
            GlanceMetric("pe", "P/E Ratio", pe?.let { decimal(it, 1) } ?: "N/A",
                pe?.let { "${it.roundToLong()}x earnings" } ?: "No positive earnings or data", educationId = "pe"),
            GlanceMetric("revenueGrowth", "Revenue Growth", growth?.let { "${signedPercent(it)} (YoY)" } ?: UNAVAILABLE,
                growth?.let { if (it >= 0) "Growing revenue" else "Shrinking revenue" } ?: "Not available",
                HomePresentation.direction(growth).takeIf { growth != null }, "revenueGrowth"),
            when {
                dividendFact?.availability == FinancialAvailability.NO_DIVIDEND ->
                    GlanceMetric("dividendYield", "Dividend Yield", "None", "No dividend", educationId = "dividendYield")
                dividend != null -> GlanceMetric("dividendYield", "Dividend Yield", percent(dividend), "Pays dividend", educationId = "dividendYield")
                else -> GlanceMetric("dividendYield", "Dividend Yield", UNAVAILABLE, "Not available", educationId = "dividendYield")
            }
        )
    }

    private fun assessment(
        name: String, growth: Double?, margin: Double?, cash: Double?, debt: Double?,
        pe: Double?, history: HistoricalComparison?, difference: Double?
    ) = listOfNotNull(
        growth?.let {
            val (badge, tone) = AssessmentRules.growth(it)
            AssessmentRow("growth", "Growth", "Revenue ${signedPercent(it)} YoY", badge = badge, tone = tone,
                explanation = "Revenue ${if (it >= 0) "grew" else "fell"} ${percent(abs(it))} compared with the prior year.")
        },
        margin?.let {
            val (badge, tone) = AssessmentRules.profitability(it)
            AssessmentRow("profitability", "Profitability", "Net Margin ${percent(it)}", badge = badge, tone = tone,
                explanation = if (it >= 0) "$name keeps about $${it.roundToLong()} in profit for every $100 of revenue." else "$name spent more than it earned over the period.")
        },
        if (cash != null || debt != null) {
            val (badge, tone) = if (cash != null && debt != null) AssessmentRules.health(cash, debt) else null to FactTone.NEUTRAL
            AssessmentRow("health", "Financial Health",
                listOfNotNull(cash?.let { "Cash ${money(it)}" }, debt?.let { "Debt ${money(it)}" }).joinToString(" • "), badge = badge, tone = tone,
                explanation = if (cash != null && debt != null) "Cash on hand compared with total debt." else null)
        } else null,
        pe?.let {
            val position = difference?.let(AssessmentRules::valuation) ?: ValuationPosition.UNKNOWN
            AssessmentRow("valuation", "Valuation",
                listOfNotNull("P/E ${decimal(it, 1)}", history?.average?.let { avg -> "${history.validCount}Y Avg ${decimal(avg, 1)}" }).joinToString(" • "),
                comparison = historyComparison(position),
                badge = historyComparison(position),
                tone = if (position == ValuationPosition.ABOVE) FactTone.CAUTION else FactTone.NEUTRAL,
                explanation = valuationHeadline(position))
        }
    )

    /** Factual sentences only; each clause appears only when its facts exist. */
    private fun insight(name: String, growth: Double?, margin: Double?, pe: Double?, difference: Double?): String? {
        val parts = buildList {
            if (growth != null && margin != null) add("Over the latest year, $name's revenue ${if (growth >= 0) "grew" else "fell"} ${percent(abs(growth))} and it kept about $${margin.roundToLong()} of every $100 in revenue as profit.")
            else if (growth != null) add("Over the latest year, $name's revenue ${if (growth >= 0) "grew" else "fell"} ${percent(abs(growth))}.")
            else if (margin != null) add("$name kept about $${margin.roundToLong()} of every $100 in revenue as profit.")
            if (pe != null && difference != null) {
                val position = when (AssessmentRules.valuation(difference)) {
                    ValuationPosition.NEAR -> "close to its own recent average"
                    ValuationPosition.ABOVE -> "${percent(difference, 0)} above its own recent average, so investors are paying more for each dollar of earnings than they have recently"
                    else -> "${percent(abs(difference), 0)} below its own recent average, so investors are paying less for each dollar of earnings than they have recently"
                }
                add("Its P/E of ${decimal(pe, 1)} is $position.")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")?.plus(" This is context, not a recommendation.")
    }

    private fun valuation(name: String, pe: Double?, history: HistoricalComparison?, difference: Double?, fundamentalsLoaded: Boolean): ValuationSummary? {
        if (!fundamentalsLoaded) return null
        val position = if (pe == null) ValuationPosition.UNKNOWN else difference?.let(AssessmentRules::valuation) ?: ValuationPosition.UNKNOWN
        val explanation = when {
            pe == null -> "P/E isn't shown when earnings are negative or the data is unavailable."
            difference == null -> "A comparison with $name's own history isn't available yet."
            position == ValuationPosition.NEAR -> "Investors are paying about what they have on average over recent years for each dollar of $name's earnings."
            position == ValuationPosition.ABOVE -> "Investors are currently paying more for each dollar of earnings than $name's own ${history?.validCount ?: 5}-year average P/E."
            else -> "Investors are currently paying less for each dollar of earnings than $name's own ${history?.validCount ?: 5}-year average P/E."
        }
        return ValuationSummary(
            currentPe = pe?.let { decimal(it, 1) },
            historicalAverage = history?.average?.let { decimal(it, 1) },
            historicalLabel = "${history?.validCount?.takeIf { it > 0 } ?: 5}Y Average",
            difference = difference?.let { signedPercent(it, 0) },
            position = position,
            headline = valuationHeadline(position),
            explanation = explanation
        )
    }

    private fun valuationHeadline(position: ValuationPosition) = when (position) {
        ValuationPosition.ABOVE -> "Trading above its historical P/E valuation."
        ValuationPosition.BELOW -> "Trading below its historical P/E valuation."
        ValuationPosition.NEAR -> "Trading near its historical P/E valuation."
        ValuationPosition.UNKNOWN -> null
    }

    private fun historyComparison(position: ValuationPosition) = when (position) {
        ValuationPosition.NEAR -> "Near History"
        ValuationPosition.ABOVE -> "Above History"
        ValuationPosition.BELOW -> "Below History"
        ValuationPosition.UNKNOWN -> null
    }

    private fun range(low: Double?, high: Double?, current: Double?, format: (Double?) -> String?): RangeSummary? {
        if (low == null || high == null || !low.isFinite() || !high.isFinite() || high <= low) return null
        val position = current?.takeIf { it.isFinite() }?.let { ((it - low) / (high - low)).coerceIn(0.0, 1.0).toFloat() } ?: return null
        return RangeSummary(format(low) ?: return null, format(high) ?: return null, position)
    }

    /** Standard market-cap size bands (descriptive, not a judgement). */
    private fun sizeLabel(cap: Double) = when {
        cap >= 200e9 -> "Mega company"
        cap >= 10e9 -> "Large company"
        cap >= 2e9 -> "Mid-size company"
        cap >= 300e6 -> "Small company"
        else -> "Micro company"
    }

    private fun sizeTag(cap: Double) = when {
        cap >= 200e9 -> "Mega Cap"
        cap >= 10e9 -> "Large Cap"
        cap >= 2e9 -> "Mid Cap"
        cap >= 300e6 -> "Small Cap"
        else -> "Micro Cap"
    }

    private fun countryName(code: String) = when (code.uppercase()) {
        "US" -> "United States"
        "CA" -> "Canada"
        "GB" -> "United Kingdom"
        "CN" -> "China"
        "JP" -> "Japan"
        "DE" -> "Germany"
        "NL" -> "Netherlands"
        "TW" -> "Taiwan"
        "IL" -> "Israel"
        "IE" -> "Ireland"
        else -> code
    }

    /** "Microsoft Corporation" → "Microsoft"; used wherever a sentence names the company. */
    fun shortName(name: String) =
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
        val sign = if (value < 0) "-" else ""
        return "$sign$${compact(abs(value))}"
    }

    /** 18.4M, 3.16T, 245B, 950; no currency symbol. */
    fun compact(value: Double): String {
        val absolute = abs(value)
        val (scaled, suffix, digits) = when {
            absolute >= 1e12 -> Triple(absolute / 1e12, "T", 2)
            absolute >= 1e9 -> Triple(absolute / 1e9, "B", 1)
            absolute >= 1e6 -> Triple(absolute / 1e6, "M", 1)
            absolute >= 1e3 -> Triple(absolute / 1e3, "K", 1)
            else -> Triple(absolute, "", 2)
        }
        return "${if (value < 0) "-" else ""}${decimal(scaled, digits).removeSuffix(".0").removeSuffix(".00")}$suffix"
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
    private fun signedPercent(value: Double): String = signedPercent(value, 1)
    private fun signedPercent(value: Double, digits: Int) = "${if (value > 0) "+" else if (value < 0) "-" else ""}${decimal(abs(value), digits)}%"

    fun decimal(value: Double, digits: Int): String {
        val factor = when (digits) { 0 -> 1L; 1 -> 10L; else -> 100L }
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor
        val sign = if (value < 0 && scaled > 0) "-" else ""
        return if (digits == 0) "$sign$whole" else "$sign$whole.${(scaled % factor).toString().padStart(digits, '0')}"
    }
}
