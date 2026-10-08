package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.portfolio.Decimal

enum class Tone { NEUTRAL, POSITIVE, NEGATIVE }

/** A labelled value. [accessibility] spells out signs and units for TalkBack/VoiceOver. */
data class MetricRow(val label: String, val value: String, val detail: String? = null, val tone: Tone = Tone.NEUTRAL, val accessibility: String = "$label, $value")

data class AllocationRowView(val label: String, val value: String, val percent: String, val fraction: Float) {
    val accessibility get() = "$label, $percent, $value"
}
data class AllocationGroupView(val id: String, val title: String, val status: Availability, val rows: List<AllocationRowView>, val note: String? = null)

data class ComparisonChart(
    val dates: List<String>,
    val portfolio: List<Double?>,
    val benchmark: List<Double?>?,
    val portfolioLabel: String,
    val benchmarkLabel: String?,
    /** Text alternative for the chart: start, end and difference in words. */
    val description: String
)

data class PeriodOption(val period: AnalyticsPeriod, val label: String, val enabled: Boolean)

data class SectionView(
    val status: Availability,
    val rows: List<MetricRow> = emptyList(),
    val notes: List<String> = emptyList(),
    /** Why the section is locked or unavailable, in one sentence. */
    val message: String? = null
)

data class ContributorView(val symbol: String, val name: String?, val amount: String, val percent: String?, val fx: String?, val tone: Tone) {
    val accessibility get() = listOfNotNull("$symbol ${if (tone == Tone.NEGATIVE) "subtracted" else "added"} $amount", percent?.let { "$it of the return" }, fx?.let { "currency effect $it" }).joinToString(", ")
}

data class InsightsView(
    val accountId: String,
    val currency: String,
    val asOf: String,
    val tier: SubscriptionTier,
    val periods: List<PeriodOption>,
    val selectedPeriod: AnalyticsPeriod,
    val health: List<MetricRow>,
    val performance: SectionView,
    val chart: ComparisonChart?,
    val benchmark: SectionView,
    val allocation: List<AllocationGroupView>,
    val concentration: SectionView,
    val contributors: SectionView,
    val positiveContributors: List<ContributorView>,
    val negativeContributors: List<ContributorView>,
    val dividends: SectionView,
    val dividendsByCompany: List<MetricRow>,
    val dividendsByYear: List<MetricRow>,
    val currencyExposure: SectionView,
    val currencySlices: List<AllocationRowView>,
    val insights: List<PortfolioInsight>,
    val notes: List<String>
)

/** Turns verified [PortfolioAnalytics] into display text. No calculation happens here. */
object InsightsFormatter {
    private const val LOCKED_MESSAGE = "Part of StockSteps+. Your holdings, transactions and basic allocation stay free."
    private const val MAX_CHART_POINTS = 120

    fun view(analytics: PortfolioAnalytics): InsightsView {
        val code = analytics.reportingCurrency.name
        val locked = analytics.locked.toSet()
        fun lockedSection(id: String) = if (id in locked) SectionView(Availability.LOCKED, message = LOCKED_MESSAGE) else null

        val perf = analytics.performance
        val performance = lockedSection("performance") ?: perf?.let { p ->
            if (p.availability != Availability.AVAILABLE) SectionView(p.availability, notes = p.notes, message = p.notes.firstOrNull() ?: "Not enough history for this period.")
            else SectionView(Availability.AVAILABLE, listOfNotNull(
                p.timeWeightedReturn?.let { signedPercentRow("Time-weighted return", it, "Your investments' performance, ignoring when you added or withdrew money.") },
                p.annualizedTimeWeightedReturn?.let { signedPercentRow("Annualized", it, "Average yearly rate over this period.") },
                p.moneyWeightedReturn?.let { signedPercentRow("Money-weighted return (XIRR)", it, "Yearly rate including the timing of your deposits and withdrawals.") }
                    ?: MetricRow("Money-weighted return (XIRR)", "Not shown", p.xirrStatus),
                p.investmentGain?.let { signedMoneyRow("Investment gain", it, "After fees, including dividends and currency movement.") },
                p.netExternalFlows?.let { MetricRow("Net deposits", signedMoney(it), "Deposits and transfers in, minus withdrawals. Not counted as gain.") },
                p.dividends?.let { MetricRow("Dividends received", money(it)) },
                p.startValue?.let { MetricRow("Value on ${p.startDate}", money(it)) },
                p.endValue?.let { MetricRow("Value now", money(it)) }
            ), p.notes)
        } ?: SectionView(Availability.UNAVAILABLE, message = "Performance isn't available.")

        val b = analytics.benchmark
        val benchmark = lockedSection("benchmark") ?: b?.let { c ->
            if (c.availability != Availability.AVAILABLE) SectionView(c.availability, notes = c.notes, message = c.notes.firstOrNull())
            else SectionView(Availability.AVAILABLE, listOf(
                signedPercentRow("Your portfolio", c.portfolioReturn!!),
                signedPercentRow(c.benchmark.name, c.benchmarkReturn!!, "${if (c.benchmark.basis == ReturnBasis.PRICE_RETURN) "Price return" else "Total return"} in ${c.currency.name}"),
                Decimal.parse(c.difference!!).let { diff ->
                    MetricRow("Difference", "${sign(diff)}${abs(diff).display(2)} pts", if (diff >= Decimal.ZERO) "Ahead of the index" else "Behind the index", tone(diff),
                        "Difference, ${abs(diff).display(2)} percentage points ${if (diff >= Decimal.ZERO) "ahead of" else "behind"} ${c.benchmark.name}")
                }
            ), c.notes)
        } ?: SectionView(Availability.UNAVAILABLE)

        val chart = perf?.takeIf { it.availability == Availability.AVAILABLE && it.indexDates.size >= 2 && "performance" !in locked }?.let { p ->
            val indices = sample(p.indexDates.indices.toList())
            val bench = b?.takeIf { it.availability == Availability.AVAILABLE }?.benchmarkIndex
            ComparisonChart(
                indices.map { p.indexDates[it] },
                indices.map { p.portfolioIndex[it]?.toDouble() },
                bench?.let { values -> indices.map { values.getOrNull(it)?.toDouble() } },
                "Your portfolio", b?.takeIf { bench != null }?.benchmark?.name,
                buildString {
                    append("Growth of 100 from ${p.indexDates.first()} to ${p.indexDates.last()}. Your portfolio ${direction(p.timeWeightedReturn)}")
                    if (bench != null) append("; ${b.benchmark.name} ${direction(b.benchmarkReturn)}")
                    append(".")
                }
            )
        }

        val a = analytics.allocation
        val allocation = buildList {
            fun group(id: String, title: String, slices: List<AllocationSlice>, lockId: String? = null, note: String? = null) {
                if (lockId != null && lockId in locked) add(AllocationGroupView(id, title, Availability.LOCKED, emptyList(), LOCKED_MESSAGE))
                else add(AllocationGroupView(id, title, if (slices.isEmpty() && a.availability == Availability.AVAILABLE) Availability.UNAVAILABLE else a.availability,
                    slices.map { AllocationRowView(it.label, money(it.value), percent(it.percent), (it.percent.toDouble() / 100).toFloat().coerceIn(0f, 1f)) },
                    note ?: a.notes.firstOrNull { a.availability != Availability.AVAILABLE }))
            }
            group("holding", "Holdings", a.byHolding)
            group("sector", "Sectors", a.bySector, "sectors", a.notes.firstOrNull { it.startsWith("ETFs") })
            group("asset", "Asset class", a.byAssetClass, "sectors")
            group("currency", "Currency", a.byCurrency)
        }

        val c = analytics.concentration
        val concentration = if (c.availability != Availability.AVAILABLE) SectionView(c.availability, notes = c.notes, message = c.notes.firstOrNull())
        else SectionView(Availability.AVAILABLE, listOfNotNull(
            c.largestHolding?.let { MetricRow("Largest holding", "${it.name} · ${percent(it.percent)}") },
            c.topThree?.let { MetricRow("Top 3 holdings", percent(it)) },
            c.topFive?.let { MetricRow("Top 5 holdings", percent(it)) },
            c.largestSector?.let { MetricRow("Largest sector", "${it.name} · ${percent(it.percent)}") },
            c.effectiveHoldings?.let { MetricRow("Effective holdings", Decimal.parse(it).display(1), "Holdings: ${c.holdingsCount}. ${Decimal.parse(it).display(1)} equal-sized holdings would be as concentrated.") },
            c.hhi?.let { MetricRow("Concentration index (HHI)", Decimal.parse(it).display(3), "0 is evenly spread; 1 is a single holding.") }
        ) + if ("concentration-detail" in locked) listOf(MetricRow("Top holdings, sectors and HHI", "StockSteps+")) else emptyList(), c.notes)

        val k = analytics.contributors
        fun row(r: ContributorRow) = Decimal.parse(r.contribution!!).let { amount ->
            ContributorView(r.symbol, r.name, signedMoney(r.contribution), r.contributionPercent?.let { signedPercent(it) }, r.fxPart?.takeIf { Decimal.parse(it) != Decimal.ZERO }?.let { signedMoney(it) }, tone(amount))
        }
        val contributors = lockedSection("contributors") ?: k?.let {
            if (it.availability == Availability.UNAVAILABLE) SectionView(it.availability, notes = it.notes, message = it.notes.firstOrNull() ?: "Contributions need dated prices for this period.")
            else SectionView(it.availability, listOfNotNull(it.other?.takeIf { o -> Decimal.parse(o) != Decimal.ZERO }?.let { o -> MetricRow("Cash, fees and other", signedMoney(o), "Not tied to a single holding: cash currency changes and standalone fees.", tone(Decimal.parse(o))) }),
                it.notes + if (it.unavailable.isNotEmpty()) listOf("Not calculated: ${it.unavailable.joinToString()}") else emptyList())
        } ?: SectionView(Availability.UNAVAILABLE)

        val d = analytics.dividends
        val dividends = SectionView(d.availability, listOfNotNull(
            d.total?.let { MetricRow("Recorded dividends", money(it), "${d.payments} payment${if (d.payments == 1) "" else "s"}") },
            d.inPeriod?.let { MetricRow("In ${analytics.period.label}", money(it)) },
            d.reinvested?.takeIf { Decimal.parse(it) > Decimal.ZERO }?.let { MetricRow("Reinvested", money(it)) }
        ) + if ("dividends-detail" in locked) listOf(MetricRow("By company, month and year", "StockSteps+")) else emptyList(), d.notes)

        val x = analytics.currency
        val currency = SectionView(x.availability, listOfNotNull(
            x.foreignShare?.let { MetricRow("In other currencies", percent(it)) },
            x.localEffect?.let { signedMoneyRow("From holdings' own prices", it) },
            x.fxEffect?.let { signedMoneyRow("From exchange rates", it, "Over ${analytics.period.label}") }
        ) + if ("currency-impact" in locked) listOf(MetricRow("Currency impact on returns", "StockSteps+")) else emptyList(), x.notes,
            if (x.availability != Availability.AVAILABLE) x.notes.firstOrNull() else null)

        return InsightsView(
            analytics.accountId, code, analytics.asOf, analytics.tier,
            AnalyticsPeriod.entries.map { PeriodOption(it, it.label, it in analytics.periods) }, analytics.period,
            analytics.health.map { MetricRow(it.label, it.value, it.explanation) },
            performance, chart, benchmark, allocation, concentration, contributors,
            k?.positive.orEmpty().map(::row), k?.negative.orEmpty().map(::row),
            dividends,
            d.byCompany.map { MetricRow(it.key, money(it.amount)) },
            d.byYear.reversed().map { MetricRow(it.key, money(it.amount)) },
            currency,
            x.slices.map { AllocationRowView(it.label, money(it.value), percent(it.percent), (it.percent.toDouble() / 100).toFloat().coerceIn(0f, 1f)) },
            analytics.insights, analytics.notes
        )
    }

    // ---------- Formatting ----------

    private fun abs(value: Decimal) = if (value < Decimal.ZERO) -value else value
    private fun sign(value: Decimal) = if (value > Decimal.ZERO) "+" else if (value < Decimal.ZERO) "−" else ""
    private fun tone(value: Decimal) = if (value > Decimal.ZERO) Tone.POSITIVE else if (value < Decimal.ZERO) Tone.NEGATIVE else Tone.NEUTRAL

    /** "$12,345.67" (amounts are in the account's reporting currency, labelled once in the header). */
    fun money(value: String): String {
        val amount = Decimal.parse(value)
        return (if (amount < Decimal.ZERO) "−" else "") + "$" + grouped(abs(amount).display(2))
    }
    fun signedMoney(value: String): String = Decimal.parse(value).let { "${sign(it)}$${grouped(abs(it).display(2))}" }
    fun percent(value: String, places: Int = 1) = "${Decimal.parse(value).display(places)}%"
    fun signedPercent(value: String, places: Int = 2) = Decimal.parse(value).let { "${sign(it)}${abs(it).display(places)}%" }

    private fun grouped(plain: String): String {
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val groupedWhole = whole.reversed().chunked(3).joinToString(",").reversed()
        return if (fraction.isEmpty()) groupedWhole else "$groupedWhole.$fraction"
    }

    private fun signedPercentRow(label: String, value: String, detail: String? = null) = Decimal.parse(value).let { v ->
        MetricRow(label, signedPercent(value), detail, tone(v), "$label, ${if (v < Decimal.ZERO) "down" else if (v > Decimal.ZERO) "up" else "unchanged"} ${abs(v).display(2)} percent")
    }
    private fun signedMoneyRow(label: String, value: String, detail: String? = null) = Decimal.parse(value).let { v ->
        MetricRow(label, signedMoney(value), detail, tone(v), "$label, ${if (v < Decimal.ZERO) "loss of" else if (v > Decimal.ZERO) "gain of" else ""} ${grouped(abs(v).display(2))} dollars")
    }
    private fun direction(value: String?) = value?.let { Decimal.parse(it) }?.let { if (it >= Decimal.ZERO) "rose ${abs(it).display(1)}%" else "fell ${abs(it).display(1)}%" } ?: "is unavailable"

    private fun sample(indices: List<Int>): List<Int> {
        if (indices.size <= MAX_CHART_POINTS) return indices
        val stride = (indices.size + MAX_CHART_POINTS - 1) / MAX_CHART_POINTS
        return (indices.filterIndexed { i, _ -> i % stride == 0 } + indices.last()).distinct()
    }
}
