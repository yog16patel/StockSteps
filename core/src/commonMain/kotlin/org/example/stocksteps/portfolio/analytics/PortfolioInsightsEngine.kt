package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.portfolio.Decimal

/**
 * Deterministic, rule-based observations derived only from verified metrics in [PortfolioAnalytics].
 * Rules describe what the numbers are; they never recommend buying, selling or rebalancing and never
 * predict. Each insight carries its inputs, period, completeness and methodology key.
 *
 * Ranking: rules have a fixed priority; at most one insight per category; 3–5 are shown.
 */
object PortfolioInsightsEngine {
    const val MAX_SHOWN = 5

    private data class Candidate(val priority: Int, val insight: PortfolioInsight)

    fun generate(analytics: PortfolioAnalytics): List<PortfolioInsight> {
        val asOf = analytics.asOf
        val candidates = mutableListOf<Candidate>()
        fun add(priority: Int, insight: PortfolioInsight) { candidates += Candidate(priority, insight) }
        fun pct(value: String, places: Int = 0) = Decimal.parse(value).display(places)

        val concentration = analytics.concentration
        concentration.largestHolding?.let { largest ->
            val share = Decimal.parse(largest.percent)
            if (concentration.holdingsCount == 1) {
                add(95, PortfolioInsight("concentration.single", InsightCategory.CONCENTRATION, "All your invested money is in one security",
                    "${largest.name} is your only holding, so its price moves determine your portfolio's investment results. ${pct(largest.percent)}% of your account value is in it.",
                    mapOf("largestHolding" to largest.name, "share" to largest.percent), null, asOf, concentration.availability, "concentration", "concentration"))
            } else if (share >= Decimal.parse("25")) {
                add(90, PortfolioInsight("concentration.largest", InsightCategory.CONCENTRATION, "${largest.name} is ${pct(largest.percent)}% of your portfolio",
                    "Your largest holding makes up about ${pct(largest.percent)}% of your account value. A 10% move in ${largest.name} would move your portfolio about ${Decimal.parse(largest.percent).multiplyDivide(Decimal.ONE, Decimal.parse("10")).display(1)}%.",
                    mapOf("largestHolding" to largest.name, "share" to largest.percent), null, asOf, concentration.availability, "concentration", "concentration"))
            }
        }
        concentration.largestSector?.let { sector ->
            if (concentration.holdingsCount > 1 && Decimal.parse(sector.percent) >= Decimal.parse("40")) {
                add(80, PortfolioInsight("sector.largest", InsightCategory.SECTOR, "${sector.name} is ${pct(sector.percent)}% of your portfolio",
                    "Companies in the same sector are often affected by similar news and conditions. This counts only holdings with known sector data; ETFs aren't broken down.",
                    mapOf("sector" to sector.name, "share" to sector.percent), null, asOf, concentration.availability, "sectors", "allocation"))
            }
        }

        analytics.performance?.takeIf { it.availability == Availability.AVAILABLE }?.let { perf ->
            val twr = perf.timeWeightedReturn ?: return@let
            val flows = Decimal.parse(perf.netExternalFlows ?: "0")
            val gain = Decimal.parse(perf.investmentGain ?: "0")
            add(70, PortfolioInsight("performance.period", InsightCategory.PERFORMANCE,
                "Your investments ${if (gain >= Decimal.ZERO) "earned" else "lost"} ${gain.abs().display()} over ${perf.period.label}",
                "Time-weighted return was ${pct(twr, 2)}%. " + if (flows != Decimal.ZERO) "Your net deposits of ${flows.display()} aren't counted as gains." else "There were no deposits or withdrawals in this period.",
                listOfNotNull("timeWeightedReturn" to twr, "investmentGain" to gain.toString(), "netExternalFlows" to flows.toString(),
                    perf.moneyWeightedReturn?.let { "moneyWeightedReturn" to it }).toMap(),
                perf.period, asOf, perf.availability, "twr", "performance"))
        }

        analytics.benchmark?.takeIf { it.availability == Availability.AVAILABLE }?.let { b ->
            val difference = Decimal.parse(b.difference!!)
            val direction = when {
                difference > Decimal.ZERO -> "ahead of"
                difference < Decimal.ZERO -> "behind"
                else -> "level with"
            }
            add(75, PortfolioInsight("benchmark.${b.benchmark.id.name.lowercase()}", InsightCategory.BENCHMARK,
                "${difference.abs().display(1)} points $direction the ${b.benchmark.name}",
                "Over ${analytics.period.label}, your time-weighted return was ${pct(b.portfolioReturn!!, 2)}% and the ${b.benchmark.name} returned ${pct(b.benchmarkReturn!!, 2)}% in ${b.currency.name}" +
                    (if (b.benchmark.basis == ReturnBasis.PRICE_RETURN) " (price only, without dividends)." else ".") + " Past differences don't indicate future results.",
                mapOf("portfolioReturn" to b.portfolioReturn, "benchmarkReturn" to b.benchmarkReturn, "difference" to b.difference),
                analytics.period, asOf, b.availability, "benchmark", "benchmark"))
        }

        analytics.contributors?.takeIf { it.availability != Availability.UNAVAILABLE }?.let { c ->
            val top = c.positive.firstOrNull() ?: c.negative.firstOrNull() ?: return@let
            val value = Decimal.parse(top.contribution!!)
            add(65, PortfolioInsight("contributor.${top.symbol}", InsightCategory.CONTRIBUTOR,
                if (value >= Decimal.ZERO) "${top.symbol} added the most over ${c.period.label}" else "${top.symbol} took away the most over ${c.period.label}",
                "${top.symbol} ${if (value >= Decimal.ZERO) "added" else "subtracted"} ${value.abs().display()} to your results in this period, including dividends and currency movement.",
                listOfNotNull("symbol" to top.symbol, "contribution" to top.contribution, top.fxPart?.let { "fxPart" to it }).toMap(),
                c.period, asOf, c.availability, "contribution", "contributors"))
        }

        analytics.currency.foreignShare?.let { foreign ->
            val share = Decimal.parse(foreign)
            if (share >= Decimal.parse("20")) {
                val fx = analytics.currency.fxEffect?.let(Decimal::parse)
                add(60, PortfolioInsight("currency.exposure", InsightCategory.CURRENCY, "${pct(foreign)}% of your portfolio is in another currency",
                    "Holdings priced in another currency change in ${analytics.reportingCurrency.name} terms when the exchange rate moves, even if their own price doesn't." +
                        (fx?.let { " Over ${analytics.period.label}, currency movement ${if (it >= Decimal.ZERO) "added" else "subtracted"} ${it.abs().display()}." } ?: ""),
                    listOfNotNull("foreignShare" to foreign, fx?.let { "fxEffect" to it.toString() }).toMap(),
                    if (fx != null) analytics.period else null, asOf, analytics.currency.availability, "currency", "currency"))
            }
        }

        analytics.dividends.takeIf { it.payments > 0 }?.let { d ->
            add(50, PortfolioInsight("dividends.total", InsightCategory.DIVIDENDS, "You've recorded ${Decimal.parse(d.total!!).display()} in dividends",
                "From ${d.payments} recorded payment${if (d.payments == 1) "" else "s"}" +
                    (d.reinvested?.let { if (Decimal.parse(it) > Decimal.ZERO) ", of which ${Decimal.parse(it).display()} was reinvested" else "" } ?: "") +
                    ". Future dividends aren't estimated.",
                listOfNotNull("total" to d.total, d.reinvested?.let { "reinvested" to it }).toMap(),
                null, asOf, d.availability, "dividends", "dividends"))
        }

        val incomplete = listOf(analytics.allocation.availability, analytics.concentration.availability, analytics.currency.availability).any { it == Availability.PARTIAL }
        if (incomplete) {
            add(100, PortfolioInsight("data.missing", InsightCategory.DATA, "Some current prices or exchange rates are missing",
                "Percentages that depend on them are hidden instead of being recalculated over part of your account. Pull to refresh to try again.",
                emptyMap(), null, asOf, Availability.PARTIAL, "data", "allocation"))
        }
        analytics.allocation.bySector.firstOrNull { it.key == "unclassified" }?.let { unclassified ->
            if (Decimal.parse(unclassified.percent) >= Decimal.parse("10")) {
                add(40, PortfolioInsight("data.sectors", InsightCategory.DATA, "${pct(unclassified.percent)}% of your portfolio has no sector data",
                    "These holdings are shown as Unclassified instead of being assigned a guessed sector, so sector percentages describe only part of your portfolio.",
                    mapOf("unclassifiedShare" to unclassified.percent), null, asOf, Availability.PARTIAL, "sectors", "allocation"))
            }
        }

        return candidates
            .sortedByDescending { it.priority }
            .distinctBy { it.insight.category }
            .take(MAX_SHOWN)
            .map { it.insight }
    }

    private fun Decimal.abs() = if (this < Decimal.ZERO) -this else this
}

/** Plain-language methodology entries referenced by [PortfolioInsight.methodology] and the UI's info buttons. */
object AnalyticsEducation {
    data class Entry(val key: String, val title: String, val body: String)

    val entries: List<Entry> = listOf(
        Entry("twr", "Time-weighted return",
            "Measures how your investments performed, without being affected by when you deposited or withdrew money. Each day's return is (value at close − that day's deposits) ÷ previous day's value − 1, and the days are linked together. It's the standard way to compare with a market index."),
        Entry("xirr", "Money-weighted return (XIRR)",
            "The yearly rate that makes all your deposits, withdrawals and your current value balance out. Unlike time-weighted return, it's affected by timing: adding money just before a rise increases it. It isn't shown for periods under a month, or when the cash flows allow more than one answer."),
        Entry("gain", "Investment gain",
            "Ending value − starting value − net deposits. Deposits and withdrawals aren't profit or loss. It includes price changes, dividends, fees and currency movement."),
        Entry("benchmark", "Benchmark comparison",
            "Both lines start at 100 on the same date and use the same dates. A foreign index is converted to your reporting currency at each date's exchange rate. Price-return indexes exclude dividends, so they aren't a perfect match for a portfolio that receives dividends. A comparison describes the past, not the future."),
        Entry("concentration", "Concentration",
            "How much of your portfolio is in your largest holdings. HHI adds up each holding's squared weight (0 to 1). Effective holdings is 1 ÷ HHI: the number of equal-sized holdings with the same concentration. Concentration is one aspect of risk and doesn't say whether a portfolio is suitable."),
        Entry("sectors", "Sectors",
            "Based on each company's reported sector. ETFs are funds that hold many companies; their sectors aren't estimated because reliable holdings data isn't available here."),
        Entry("contribution", "Contribution",
            "How much each holding added to or subtracted from your results in the period, in your reporting currency: change in market value − money invested + dividends received. Lifetime gains aren't used for shorter periods."),
        Entry("dividends", "Dividends",
            "Dividends you recorded, converted at each payment date's exchange rate. Reinvested dividends count once. Future dividends aren't estimated."),
        Entry("currency", "Currency exposure",
            "The currency your holdings and cash are denominated in. A company can still earn revenue in other currencies. The currency effect is the part of your gain caused by exchange-rate changes."),
        Entry("data", "Data completeness",
            "Prices use each market's last close, up to $PRICE_CARRY_DAYS_TEXT days old for holidays. Exchange rates are Bank of Canada daily rates. When data is missing, StockSteps shows the metric as unavailable instead of estimating it.")
    )

    private const val PRICE_CARRY_DAYS_TEXT = "5"

    fun entry(key: String) = entries.firstOrNull { it.key == key }
}
