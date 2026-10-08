package org.example.stocksteps.companydetail

import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.*
import kotlin.math.abs

/** Historical window for the Valuation screen. */
enum class ValuationRange(val label: String, val years: Int) {
    ONE_YEAR("1Y", 1), THREE_YEARS("3Y", 3), FIVE_YEARS("5Y", 5), TEN_YEARS("10Y", 10)
}

/** P/E chart: one slot per month (or fiscal year); null slots are gaps, never drawn as values. */
data class ValuationChart(
    val values: List<Double?>,
    /** Detail for each slot when touched ("Mar 2025 • P/E 31.2x • 5Y avg 33.4x"). */
    val details: List<String>,
    val average: Double?,
    val xLabels: List<String>,
    val yLabels: List<String>,
    val description: String
)

/** Lowest / average / highest / current and where current sits among the actual observations. */
data class ValuationRangeSummary(
    val lowest: String, val average: String, val highest: String, val current: String?,
    /** 0..1 position of current between lowest and highest (clamped), null without a current P/E. */
    val position: Float?,
    val percentile: String?
)

data class ValuationExplainer(val title: String, val points: List<String>)

data class ValuationModel(
    val range: ValuationRange,
    val currentPe: String,
    val currentPeAvailable: Boolean,
    /** "Above / Near / Below Historical Average" when a reliable average exists. */
    val badge: String?,
    val position: ValuationPosition,
    /** "Investors are paying about $31.20 for every $1 of annual earnings." or the N/A reason. */
    val meaning: String,
    val averageLabel: String,
    val average: String?,
    val difference: String?,
    /** How the average was computed (observation type, count and dates) or why it isn't shown. */
    val methodology: String,
    val chart: ValuationChart?,
    val rangeSummary: ValuationRangeSummary?,
    val understanding: String,
    val highReasons: ValuationExplainer,
    val lowReasons: ValuationExplainer,
    val otherMetrics: List<InfoRow>,
    val growth: List<InfoRow>,
    val growthNote: String,
    val insight: String,
    val historyMessage: String?
)

/**
 * Valuation screen content from the normalized P/E history plus the latest fundamentals. All
 * statistics come from actual observations in the selected window; nothing is interpolated,
 * and no sentence recommends buying or selling.
 */
object ValuationPresenter {
    /** Monthly coverage below this share of the window makes the comparison unreliable. */
    private const val MIN_MONTHLY_COVERAGE = 0.6
    private const val MIN_MONTHLY_OBSERVATIONS = 6
    private const val MIN_ANNUAL_OBSERVATIONS = 3
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun defaultRange(history: ValuationHistory?): ValuationRange {
        val years = history?.let(::yearsCovered) ?: 0.0
        return ValuationRange.entries.lastOrNull { it.years <= 5 && it.years <= years + 0.25 } ?: ValuationRange.ONE_YEAR
    }

    fun build(history: ValuationHistory?, fundamentals: CompanyFundamentals?, range: ValuationRange, companyName: String, priceCurrency: String? = null): ValuationModel {
        val name = companyName
        val current = history?.currentPe?.takeIf { it > 0 && history.currentPeAvailability == FinancialAvailability.AVAILABLE }
            ?: fundamentals?.valuation?.metrics?.get("pe")?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value?.takeIf { it > 0 }
        val negative = history?.currentPeAvailability == FinancialAvailability.NON_POSITIVE_DENOMINATOR ||
            fundamentals?.valuation?.metrics?.get("pe")?.availability == FinancialAvailability.NON_POSITIVE_DENOMINATOR
        val method = history?.method ?: ValuationMethod.UNAVAILABLE
        val window = history?.let { window(it, range) }.orEmpty()
        val values = window.map { it.pe }
        val enough = when (method) {
            ValuationMethod.MONTHLY_TTM -> values.size >= MIN_MONTHLY_OBSERVATIONS && values.size >= range.years * 12 * MIN_MONTHLY_COVERAGE
            ValuationMethod.ANNUAL_REPORTED -> values.size >= MIN_ANNUAL_OBSERVATIONS && range.years >= MIN_ANNUAL_OBSERVATIONS
            ValuationMethod.UNAVAILABLE -> false
        }
        val average = if (enough) values.average() else null
        val difference = if (average != null && current != null) (current / average - 1) * 100 else null
        val position = difference?.let(AssessmentRules::valuation) ?: ValuationPosition.UNKNOWN
        val symbol = FinancialStatementsPresenter.currencySymbol(priceCurrency)
        val windowLabel = "${range.years}-Year"

        val methodology = when {
            method == ValuationMethod.UNAVAILABLE -> "Historical valuation data isn't available for this company."
            !enough && method == ValuationMethod.ANNUAL_REPORTED && range.years < MIN_ANNUAL_OBSERVATIONS ->
                "Only fiscal-year P/E ratios are available, so a ${range.label} average isn't shown. Try 3Y or longer."
            !enough -> "Not enough valid observations in the last ${range.years} year${if (range.years > 1) "s" else ""} for a reliable average" +
                " (${values.size} available)."
            method == ValuationMethod.MONTHLY_TTM -> "Average of ${values.size} month-end P/E values (${monthYear(window.first().date)} – ${monthYear(window.last().date)}): " +
                "price ÷ trailing-12-month diluted EPS, using only earnings already reported at each date" +
                (if (history?.splitAdjusted == true) ", adjusted for stock splits." else ".") +
                (if (values.size < range.years * 12) " Months with negative earnings or missing filings are left out." else "")
            else -> "Average of ${values.size} fiscal-year P/E ratios reported by the data provider (one per year), not a monthly average."
        }

        val chart = if (values.size >= 2) chart(window, method, average, range) else null
        val rangeSummary = if (enough) {
            val low = values.min()
            val high = values.max()
            ValuationRangeSummary(
                lowest = multiple(low), average = multiple(average!!), highest = multiple(high), current = current?.let(::multiple),
                position = current?.let { c -> if (high > low) ((c - low) / (high - low)).toFloat().coerceIn(0f, 1f) else 0.5f },
                percentile = current?.let { c ->
                    when {
                        c > high -> "Above the highest P/E observed in this period."
                        c < low -> "Below the lowest P/E observed in this period."
                        else -> "Higher than ${(values.count { it < c } * 100.0 / values.size).toInt()}% of the ${values.size} observations in this period."
                    }
                }
            )
        } else null

        val metrics = fundamentals?.valuation?.metrics.orEmpty()
        val returns = fundamentals?.financials?.shareholderReturns.orEmpty()
        fun value(id: String, map: Map<String, FinancialFact> = metrics) = map[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value?.takeIf { it.isFinite() }
        val otherMetrics = listOfNotNull(
            value("priceSales")?.takeIf { it > 0 }?.let { InfoRow("Price to Sales (P/S)", multiple(it), "Market value ÷ annual revenue.") },
            value("priceBook")?.takeIf { it > 0 }?.let { InfoRow("Price to Book (P/B)", multiple(it), "Market value ÷ shareholders' equity (book value).") },
            value("evEbitda")?.takeIf { it > 0 }?.let { InfoRow("EV / EBITDA", multiple(it), "Enterprise value ÷ earnings before interest, taxes, depreciation and amortization.") },
            value("peg")?.takeIf { it > 0 }?.let { InfoRow("PEG Ratio", decimal(it, 2), "P/E ÷ expected earnings growth, as defined by the data provider; growth periods differ between sources.") },
            if (returns["dividendYield"]?.availability == FinancialAvailability.NO_DIVIDEND) InfoRow("Dividend Yield", "No dividend", "The company doesn't currently pay a regular dividend.")
            else value("dividendYield", returns)?.let { InfoRow("Dividend Yield", "${decimal(it, 2)}%", "Yearly dividends ÷ share price. Yield alone doesn't show whether a stock is cheap or expensive.") }
        )

        val growthFacts = fundamentals?.financials?.growth.orEmpty()
        val revenueGrowth = value("revenueGrowth", growthFacts)
        val epsGrowth = value("epsGrowth", growthFacts)
        val growth = listOfNotNull(
            current?.let { InfoRow("P/E Ratio", multiple(it)) },
            revenueGrowth?.let { InfoRow("Revenue Growth (latest year)", signed(it), changeDirection = HomePresentation.direction(it)) },
            epsGrowth?.let { InfoRow("EPS Growth (latest year)", signed(it), changeDirection = HomePresentation.direction(it)) }
        )
        val growthNote = when {
            epsGrowth == null -> "EPS growth isn't available, so earnings growth can't be compared with the P/E."
            epsGrowth < 0 -> "Earnings per share declined over the latest year, which can make the P/E higher even if the price didn't change."
            else -> "A higher multiple may reflect expectations of future growth, but growth doesn't guarantee that a valuation is justified."
        }

        val historyMessage = when {
            history == null -> "We couldn't load the valuation history right now."
            method == ValuationMethod.UNAVAILABLE -> "Historical valuation data isn't available for this company."
            else -> null
        }

        return ValuationModel(
            range = range,
            currentPe = current?.let(::multiple) ?: "N/A",
            currentPeAvailable = current != null,
            badge = when (position) {
                ValuationPosition.ABOVE -> "Above Historical Average"
                ValuationPosition.BELOW -> "Below Historical Average"
                ValuationPosition.NEAR -> "Near Historical Average"
                ValuationPosition.UNKNOWN -> null
            },
            position = position,
            meaning = when {
                current != null -> "Investors are paying about $symbol${decimal(current, 2)} for every ${symbol}1 of annual earnings per share."
                negative -> "P/E is not meaningful because earnings are currently zero or negative."
                else -> "A current P/E isn't available for $name."
            },
            averageLabel = "$windowLabel Average P/E",
            average = average?.let(::multiple),
            difference = difference?.let { signed(it) },
            methodology = methodology,
            chart = chart,
            rangeSummary = rangeSummary,
            understanding = "P/E stands for Price-to-Earnings ratio. It compares a company's share price with its earnings per share over the last year." +
                (current?.let { " For $name, a P/E of ${multiple(it)} means investors pay about $symbol${decimal(it, 2)} for each ${symbol}1 of yearly earnings per share." } ?: ""),
            highReasons = ValuationExplainer("Why can P/E be high?", listOf(
                "Investors may expect earnings to grow in the future.",
                "Earnings may be steady and predictable.",
                "Market sentiment and interest rates can raise what investors pay.",
                "Earnings may be temporarily low, which pushes the ratio up."
            )),
            lowReasons = ValuationExplainer("Why can P/E be low?", listOf(
                "Investors may expect slower growth.",
                "The business may face risks or uncertainty.",
                "Earnings may be cyclical or temporarily high.",
                "Investor sentiment may have changed. A low P/E doesn't automatically mean a stock is cheap."
            )),
            otherMetrics = otherMetrics,
            growth = growth,
            growthNote = growthNote,
            insight = insight(name, current, negative, average, position, range),
            historyMessage = historyMessage
        )
    }

    /** Observations inside the last [range] years, measured back from the latest observation. */
    fun window(history: ValuationHistory, range: ValuationRange): List<ValuationObservation> {
        val latest = history.observations.maxByOrNull { it.date } ?: return emptyList()
        val start = shiftYears(latest.date, -range.years)
        return history.observations.filter { it.date > start }.sortedBy { it.date }
    }

    private fun insight(name: String, current: Double?, negative: Boolean, average: Double?, position: ValuationPosition, range: ValuationRange): String {
        if (current == null) {
            return if (negative) "$name's earnings are currently zero or negative, so the P/E ratio can't describe its valuation. Other measures such as price-to-sales can still give context."
            else "$name's current P/E isn't available, so its valuation can't be compared with its history right now."
        }
        val comparison = when (position) {
            ValuationPosition.ABOVE -> "$name's current P/E is higher than its ${range.years}-year average. Investors are paying a higher multiple of earnings than they typically paid during this period."
            ValuationPosition.BELOW -> "$name's current P/E is lower than its ${range.years}-year average. Investors are paying a lower multiple of earnings than they typically paid during this period."
            ValuationPosition.NEAR -> "$name's current P/E is close to its ${range.years}-year average, so investors are paying about what they typically paid during this period."
            ValuationPosition.UNKNOWN -> "$name's P/E is ${multiple(current)}, but there isn't enough history to compare it with a ${range.years}-year average."
        }
        return "$comparison Changes in growth, profitability and business conditions can affect what investors are willing to pay. This is context, not a recommendation."
    }

    private fun chart(window: List<ValuationObservation>, method: ValuationMethod, average: Double?, range: ValuationRange): ValuationChart {
        // Monthly series: one slot per calendar month so missing months show as gaps.
        val slots: List<Pair<String, Double?>> = if (method == ValuationMethod.MONTHLY_TTM) {
            val byMonth = window.associate { it.date.take(7) to it.pe }
            months(window.first().date.take(7), window.last().date.take(7)).map { it to byMonth[it] }
        } else window.map { it.date to it.pe }
        val labelOf = { key: String -> if (method == ValuationMethod.MONTHLY_TTM) monthYear("$key-01") else "FY${key.take(4)}" }
        val values = slots.map { it.second }
        val valid = values.filterNotNull()
        val high = maxOf(valid.max(), average ?: valid.max())
        val low = minOf(valid.min(), average ?: valid.min())
        val avgText = average?.let { " • ${range.label} avg ${multiple(it)}" }.orEmpty()
        val step = (slots.size - 1).coerceAtLeast(1) / 3.0
        return ValuationChart(
            values = values,
            details = slots.map { (key, pe) -> "${labelOf(key)} • ${pe?.let { "P/E ${multiple(it)}" } ?: "no valid P/E"}$avgText" },
            average = average,
            xLabels = (0..3).map { slots[(it * step).toInt().coerceAtMost(slots.lastIndex)].first }.distinct().map(labelOf),
            yLabels = listOf(high, (high + low) / 2, low).map(::multiple),
            description = "Historical P/E, ${range.label}: ${valid.size} valid observations from ${multiple(valid.min())} to ${multiple(valid.max())}" +
                (average?.let { ", average ${multiple(it)}" } ?: "") + (if (valid.size < values.size) ", with gaps where P/E wasn't meaningful" else "") + "."
        )
    }

    private fun yearsCovered(history: ValuationHistory): Double {
        val dates = history.observations.map { it.date }.sorted()
        if (dates.size < 2) return 0.0
        fun months(date: String) = date.take(4).toInt() * 12 + date.substring(5, 7).toInt()
        return (months(dates.last()) - months(dates.first())) / 12.0
    }

    private fun months(from: String, to: String): List<String> {
        var year = from.take(4).toInt()
        var month = from.substring(5, 7).toInt()
        val result = mutableListOf<String>()
        while ("$year-${month.toString().padStart(2, '0')}" <= to) {
            result += "$year-${month.toString().padStart(2, '0')}"
            month++
            if (month > 12) { month = 1; year++ }
        }
        return result
    }

    private fun shiftYears(date: String, years: Int) = "${date.take(4).toInt() + years}${date.drop(4)}"
    private fun monthYear(date: String) = "${MONTHS[date.substring(5, 7).toInt() - 1]} ${date.take(4)}"
    fun multiple(value: Double) = "${decimal(value, 1)}x"
    private fun signed(value: Double) = "${if (value > 0) "+" else if (value < 0) "-" else ""}${decimal(abs(value), 1)}%"
    private fun decimal(value: Double, digits: Int) = CompanyOverviewPresenter.decimal(value, digits)
}
