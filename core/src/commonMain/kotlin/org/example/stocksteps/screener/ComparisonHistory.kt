package org.example.stocksteps.screener

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialPeriodStatement
import kotlin.math.abs

/*
 * Company Comparison Phase 3: historical financial comparison. Free = the latest four completed fiscal
 * quarters of revenue, net income and diluted EPS; StockSteps+ = 3- and 5-fiscal-year history plus
 * revenue growth, net profit margin, EPS growth and a revenue index. Everything here is deterministic
 * and runs on the server (the apps only format); it never interpolates, converts or estimates a value.
 */

@Serializable enum class HistoryGranularity(val label: String) { QUARTERLY("Fiscal quarters"), ANNUAL("Fiscal years") }

/** [periods] = observations shown. 1Y is "the latest four completed fiscal quarters", not exactly 365 days. */
@Serializable enum class HistoryRange(val label: String, val periods: Int, val granularity: HistoryGranularity, val premium: Boolean, val description: String) {
    ONE_YEAR("1Y", 4, HistoryGranularity.QUARTERLY, false, "Latest four completed fiscal quarters"),
    THREE_YEARS("3Y", 3, HistoryGranularity.ANNUAL, true, "Latest three completed fiscal years"),
    FIVE_YEARS("5Y", 5, HistoryGranularity.ANNUAL, true, "Latest five completed fiscal years");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } }
}

@Serializable enum class HistoryUnit { MONEY, PER_SHARE, PERCENT, INDEX }

@Serializable enum class HistoryMetric(val label: String, val unit: HistoryUnit, val premium: Boolean, val definition: String) {
    REVENUE("Revenue", HistoryUnit.MONEY, false, "Money earned from selling products and services in the period, before expenses (reported income statement)."),
    NET_INCOME("Net income", HistoryUnit.MONEY, false, "Profit left after all costs, interest and taxes in the period, as reported (the provider's net income attributable to the company). Negative means a loss."),
    EPS_DILUTED("Diluted EPS", HistoryUnit.PER_SHARE, false, "Reported diluted earnings per share: net income per share including shares that options and convertibles could add. Basic EPS is never substituted."),
    REVENUE_GROWTH("Revenue growth", HistoryUnit.PERCENT, true, "(Revenue − revenue in the same fiscal period a year earlier) ÷ |that earlier revenue| × 100. Not shown when the earlier revenue was zero or negative."),
    NET_MARGIN("Net profit margin", HistoryUnit.PERCENT, true, "Net income ÷ revenue × 100 for the same period. Not shown when revenue was zero or negative."),
    EPS_GROWTH("EPS growth", HistoryUnit.PERCENT, true, "(Diluted EPS − diluted EPS a year earlier) ÷ that earlier EPS × 100, only when the earlier EPS was positive."),
    REVENUE_INDEX("Revenue index", HistoryUnit.INDEX, true, "Revenue ÷ revenue in the first displayed period × 100, so companies of different sizes (and currencies) can be compared by relative change. Not an investment return.");
    val free: Boolean get() = !premium
}

/** How closely the companies' n-th periods line up in time. */
@Serializable enum class PeriodAlignment(val label: String) {
    ALIGNED("Periods end within a month of each other"),
    CLOSE("Periods end in different months"),
    DIFFERENT("Reporting windows differ substantially"),
    UNKNOWN("Period end dates aren't all reported")
}

@Serializable
data class HistoryPoint(
    /** "Q3 FY2026" or "FY2025": the company's own fiscal label. */
    val label: String,
    val fiscalYear: Int? = null,
    /** "Q1"…"Q4" or "FY". */
    val fiscalPeriod: String? = null,
    val periodEnd: String? = null,
    val currency: String? = null,
    val value: Double? = null,
    val availability: FinancialAvailability = FinancialAvailability.MISSING,
    val note: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistorySeries(
    val symbol: String,
    val name: String,
    /** The latest period's reporting currency (each point keeps its own). */
    val currency: String? = null,
    /** Oldest → newest; one point per expected period (missing periods are explicit). */
    @EncodeDefault val points: List<HistoryPoint> = emptyList(),
    val note: String? = null,
    /** Revenue index only: "FY2021 = 100". */
    val base: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoryMetricData(
    val metric: HistoryMetric,
    @EncodeDefault val series: List<HistorySeries> = emptyList(),
    @EncodeDefault val insights: List<String> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList()
)

/** One row of the values table: the n-th period of every company, with how well they line up. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoryPeriodRow(val index: Int, @EncodeDefault val labels: List<String?> = emptyList(), @EncodeDefault val ends: List<String?> = emptyList(), val alignment: PeriodAlignment, val note: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoryCompany(val symbol: String, val name: String, val currency: String? = null, val error: String? = null, val retrievedAt: String? = null)

/** What this caller may see; decided by the server from the verified account and stored plan. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoryAccess(
    val plus: Boolean = false,
    val signedIn: Boolean = false,
    @EncodeDefault val ranges: List<HistoryRange> = listOf(HistoryRange.ONE_YEAR),
    @EncodeDefault val metrics: List<HistoryMetric> = HistoryMetric.entries.filter { it.free },
    val message: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoricalComparison(
    val range: HistoryRange,
    val granularity: HistoryGranularity,
    @EncodeDefault val companies: List<HistoryCompany> = emptyList(),
    @EncodeDefault val metrics: List<HistoryMetricData> = emptyList(),
    @EncodeDefault val periods: List<HistoryPeriodRow> = emptyList(),
    /** Observations about the comparison as a whole (alignment, currencies, coverage). */
    @EncodeDefault val insights: List<String> = emptyList(),
    @EncodeDefault val notes: List<String> = emptyList(),
    val access: HistoryAccess = HistoryAccess(),
    val source: String,
    val asOf: String,
    val sampleData: Boolean = false
) {
    fun metric(metric: HistoryMetric): HistoryMetricData? = metrics.firstOrNull { it.metric == metric }
}

/** Statements for one company, as loaded by the server (null [statements] = couldn't be loaded). */
data class HistoryInput(
    val symbol: String,
    val name: String,
    val statements: List<FinancialPeriodStatement>?,
    val error: String? = null,
    val retrievedAt: String? = null
)

object HistoricalComparisonEngine {
    /** Period ends this close are "aligned"; within [CLOSE_DAYS] they're in different months; beyond, the windows differ. */
    const val ALIGNED_DAYS = 31
    const val CLOSE_DAYS = 92

    data class Result(val companies: List<HistoryCompany>, val metrics: List<HistoryMetricData>, val periods: List<HistoryPeriodRow>, val insights: List<String>)

    private data class Slot(val fiscalYear: Int, val period: String) {
        val label get() = if (period == "FY") "FY$fiscalYear" else "$period FY$fiscalYear"
        fun previous(): Slot = when (period) {
            "FY" -> Slot(fiscalYear - 1, "FY")
            "Q1" -> Slot(fiscalYear - 1, "Q4")
            else -> Slot(fiscalYear, "Q${period.drop(1).toInt() - 1}")
        }
        fun yearEarlier() = Slot(fiscalYear - 1, period)
        val order get() = fiscalYear * 10 + (if (period == "FY") 0 else period.drop(1).toInt())
    }

    private class Company(val input: HistoryInput, val slots: List<Slot>, val bySlot: Map<Slot, FinancialPeriodStatement>) {
        val symbol get() = input.symbol
        val name get() = input.name
        fun statement(slot: Slot) = bySlot[slot]
        val latestCurrency get() = slots.asReversed().firstNotNullOfOrNull { bySlot[it]?.currency }
    }

    /**
     * Builds the comparison for [range]. Free callers ([plus] = false) get only the free metrics and basic
     * observations; premium metrics are never computed for them, so they can't leak into a response.
     */
    fun compute(inputs: List<HistoryInput>, range: HistoryRange, plus: Boolean, today: String): Result {
        val companies = inputs.map { select(it, range, today) }
        val metrics = HistoryMetric.entries.filter { plus || it.free }.map { metric -> metricData(metric, companies, range, plus) }
        val periods = periodRows(companies, range)
        val loaded = companies.filter { it.input.statements != null }
        val insights = buildList {
            companies.filter { it.input.statements == null }.forEach { add("${it.name}: ${it.input.error ?: "financial history isn't available right now"}. It's left out of the observations.") }
            companies.filter { it.input.statements != null && it.bySlot.isEmpty() }.forEach {
                add("${it.name} has no reported ${if (range.granularity == HistoryGranularity.QUARTERLY) "quarterly" else "annual"} figures from the data source for this view.")
            }
            alignmentInsight(loaded.filter { it.bySlot.isNotEmpty() }, range)?.let(::add)
            val currencies = loaded.flatMap { c -> c.bySlot.values.mapNotNull { it.currency } }.distinct()
            if (currencies.size > 1) add("Amounts are in each company's reporting currency (${ComparisonInterpretationEngine.join(currencies)}) and aren't converted, so compare how each company changed rather than the amounts themselves." +
                if (plus) " The revenue index compares relative change without converting currencies." else "")
            loaded.filter { c -> c.bySlot.values.mapNotNull { it.currency }.distinct().size > 1 }.forEach {
                add("${it.name} changed its reporting currency within these periods; growth isn't calculated across that change.")
            }
            if (range.granularity == HistoryGranularity.QUARTERLY) add("Quarterly figures can be seasonal (for example, holiday sales), so a change from one quarter to the next isn't a trend on its own.")
        }
        return Result(companies.map { HistoryCompany(it.symbol, it.name, it.latestCurrency, it.input.error.takeIf { _ -> it.input.statements == null }, it.input.retrievedAt) },
            metrics, periods, insights)
    }

    // ---------- Periods ----------

    /** The expected window ending at the company's latest completed period; gaps stay as explicit slots. */
    private fun select(input: HistoryInput, range: HistoryRange, today: String): Company {
        val quarterly = range.granularity == HistoryGranularity.QUARTERLY
        val usable = input.statements.orEmpty().filter { s ->
            s.fiscalYear != null && (if (quarterly) s.period in setOf("Q1", "Q2", "Q3", "Q4") else s.period == "FY") &&
                (s.date == null || s.date.take(10) <= today)                                   // completed periods only
        }
        val bySlot = usable.groupBy { Slot(it.fiscalYear!!, it.period) }.mapValues { it.value.first() }
        val latest = bySlot.keys.maxByOrNull { it.order } ?: return Company(input, emptyList(), emptyMap())
        val slots = generateSequence(latest) { it.previous() }.take(range.periods).toList().reversed()
        return Company(input, slots, bySlot)
    }

    private fun day(date: String?) = date?.let { MarketsPresenter.dayNumber(it.take(10)) }

    private fun periodRows(companies: List<Company>, range: HistoryRange): List<HistoryPeriodRow> = (0 until range.periods).map { i ->
        val labels = companies.map { c -> c.slots.getOrNull(i)?.label }
        val ends = companies.map { c -> c.slots.getOrNull(i)?.let { c.statement(it)?.date?.take(10) } }
        val present = companies.indices.filter { labels[it] != null && companies[it].slots.getOrNull(i)?.let { s -> companies[it].statement(s) } != null }
        val days = present.mapNotNull { day(ends[it]) }
        val alignment = when {
            present.size < 2 || days.size < present.size -> PeriodAlignment.UNKNOWN
            days.max() - days.min() <= ALIGNED_DAYS -> PeriodAlignment.ALIGNED
            days.max() - days.min() <= CLOSE_DAYS -> PeriodAlignment.CLOSE
            else -> PeriodAlignment.DIFFERENT
        }
        HistoryPeriodRow(i, labels, ends, alignment, when (alignment) {
            PeriodAlignment.DIFFERENT -> "These periods end more than three months apart, so they cover different stretches of time."
            PeriodAlignment.CLOSE -> "These periods end in different months, so they overlap but don't match exactly."
            PeriodAlignment.UNKNOWN -> if (present.size >= 2) "Not every period end date is reported." else null
            PeriodAlignment.ALIGNED -> null
        })
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun month(date: String?) = date?.drop(5)?.take(2)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) }

    private fun alignmentInsight(companies: List<Company>, range: HistoryRange): String? {
        if (companies.size < 2) return null
        val ends = companies.map { c -> c to month(c.slots.lastOrNull()?.let { c.statement(it)?.date }) }
        if (ends.any { it.second == null }) return "Not every company reports its period end dates here, so it can't be confirmed that the periods line up."
        if (ends.map { it.second }.distinct().size == 1) return null
        val what = if (range.granularity == HistoryGranularity.ANNUAL) "fiscal years end" else "latest fiscal quarters end"
        return "These companies' $what in different months (" + ends.joinToString("; ") { "${it.first.name}: ${it.second}" } +
            "), so their reporting periods don't align exactly. Each period's own label and end date are shown in the table."
    }

    // ---------- Values ----------

    private fun point(slot: Slot, s: FinancialPeriodStatement?, value: Double?, availability: FinancialAvailability, note: String? = null) =
        HistoryPoint(slot.label, slot.fiscalYear, slot.period, s?.date?.take(10), s?.currency, value?.takeIf { availability == FinancialAvailability.AVAILABLE }, availability, note)

    private fun reported(slot: Slot, s: FinancialPeriodStatement?, value: Double?, what: String): HistoryPoint = when {
        s == null -> point(slot, null, null, FinancialAvailability.MISSING, "The data source has no ${if (slot.period == "FY") "report" else "quarterly report"} for ${slot.label}.")
        value == null || !value.isFinite() -> point(slot, s, null, FinancialAvailability.MISSING, "$what wasn't reported for ${slot.label}.")
        else -> point(slot, s, value, FinancialAvailability.AVAILABLE)
    }

    private fun growth(slot: Slot, current: FinancialPeriodStatement?, prior: FinancialPeriodStatement?, get: (FinancialPeriodStatement) -> Double?, what: String, eps: Boolean): HistoryPoint {
        val now = current?.let(get)?.takeIf { it.isFinite() }
        val before = prior?.let(get)?.takeIf { it.isFinite() }
        val earlier = slot.yearEarlier().label
        return when {
            now == null -> point(slot, current, null, FinancialAvailability.MISSING, "$what for ${slot.label} wasn't reported.")
            before == null -> point(slot, current, null, FinancialAvailability.INSUFFICIENT_HISTORY, "$what for $earlier (a year earlier) isn't available.")
            current.currency != null && prior.currency != null && current.currency != prior.currency ->
                point(slot, current, null, FinancialAvailability.PERIOD_MISMATCH, "Reporting currency changed (${prior.currency} in $earlier, ${current.currency} in ${slot.label}), so growth isn't calculated.")
            before <= 0 && eps -> point(slot, current, null, FinancialAvailability.UNRELIABLE_COMPARISON,
                "Diluted EPS went from ${MetricFormatter.decimals(before, 2)} in $earlier to ${MetricFormatter.decimals(now, 2)} " +
                    (if (now > 0) "(from a loss to a profit)" else if (now > before) "(a smaller loss)" else if (now < before) "(a larger loss)" else "(unchanged)") +
                    ". Growth isn't a meaningful percentage when the earlier EPS was zero or negative.")
            before <= 0 -> point(slot, current, null, FinancialAvailability.NON_POSITIVE_DENOMINATOR, "$what in $earlier was zero or negative, so growth isn't a meaningful percentage.")
            else -> point(slot, current, (now - before) / abs(before) * 100, FinancialAvailability.AVAILABLE)
        }
    }

    private fun series(metric: HistoryMetric, c: Company): HistorySeries {
        if (c.input.statements == null) return HistorySeries(c.symbol, c.name, null, emptyList(), c.input.error ?: "Financial history isn't available right now.")
        if (c.slots.isEmpty()) return HistorySeries(c.symbol, c.name, null, emptyList(), "No reported figures for this view.")
        val points = when (metric) {
            HistoryMetric.REVENUE -> c.slots.map { s -> reported(s, c.statement(s), c.statement(s)?.revenue, "Revenue") }
            HistoryMetric.NET_INCOME -> c.slots.map { s -> reported(s, c.statement(s), c.statement(s)?.netIncome, "Net income") }
            HistoryMetric.EPS_DILUTED -> c.slots.map { s -> reported(s, c.statement(s), c.statement(s)?.epsDiluted, "Diluted EPS") }
            HistoryMetric.REVENUE_GROWTH -> c.slots.map { s -> growth(s, c.statement(s), c.statement(s.yearEarlier()), { it.revenue }, "Revenue", eps = false) }
            HistoryMetric.EPS_GROWTH -> c.slots.map { s -> growth(s, c.statement(s), c.statement(s.yearEarlier()), { it.epsDiluted }, "Diluted EPS", eps = true) }
            HistoryMetric.NET_MARGIN -> c.slots.map { s ->
                val st = c.statement(s); val revenue = st?.revenue?.takeIf { it.isFinite() }; val income = st?.netIncome?.takeIf { it.isFinite() }
                when {
                    st == null -> point(s, null, null, FinancialAvailability.MISSING, "The data source has no report for ${s.label}.")
                    revenue == null || income == null -> point(s, st, null, FinancialAvailability.MISSING, "Revenue or net income wasn't reported for ${s.label}.")
                    revenue <= 0 -> point(s, st, null, FinancialAvailability.NON_POSITIVE_DENOMINATOR, "Revenue was zero or negative in ${s.label}, so a margin isn't meaningful.")
                    else -> point(s, st, income / revenue * 100, FinancialAvailability.AVAILABLE)
                }
            }
            HistoryMetric.REVENUE_INDEX -> {
                val first = c.slots.first(); val base = c.statement(first)
                val baseRevenue = base?.revenue?.takeIf { it.isFinite() && it > 0 }
                if (baseRevenue == null) return HistorySeries(c.symbol, c.name, c.latestCurrency, c.slots.map { s ->
                    point(s, c.statement(s), null, FinancialAvailability.INSUFFICIENT_HISTORY, "No index: revenue in ${first.label} (the base) is missing, zero or negative.") },
                    "The index needs positive revenue in the first displayed period (${first.label}).")
                c.slots.map { s ->
                    val st = c.statement(s); val revenue = st?.revenue?.takeIf { it.isFinite() }
                    when {
                        st == null || revenue == null -> point(s, st, null, FinancialAvailability.MISSING, "Revenue wasn't reported for ${s.label}.")
                        st.currency != null && base.currency != null && st.currency != base.currency ->
                            point(s, st, null, FinancialAvailability.PERIOD_MISMATCH, "Reported in ${st.currency}, not ${base.currency} like the base period, so it isn't indexed.")
                        else -> point(s, st, revenue / baseRevenue * 100, FinancialAvailability.AVAILABLE)
                    }
                }
            }
        }
        return HistorySeries(c.symbol, c.name, c.latestCurrency, points, base = if (metric == HistoryMetric.REVENUE_INDEX) "${c.slots.first().label} = 100" else null)
    }

    // ---------- Observations (deterministic; describe, never explain why) ----------

    fun format(metric: HistoryMetric, value: Double, currency: String?, explicit: Boolean = false): String = when (metric.unit) {
        HistoryUnit.MONEY -> MetricFormatter.money(value, currency, explicit)
        HistoryUnit.PER_SHARE -> (if (value < 0) "−" else "") + MetricFormatter.price(abs(value), currency)
        HistoryUnit.PERCENT -> (if (value > 0 && metric != HistoryMetric.NET_MARGIN) "+" else if (value < 0) "−" else "") + MetricFormatter.decimals(abs(value), 1) + "%"
        HistoryUnit.INDEX -> MetricFormatter.decimals(value, 1)
    }

    private fun metricData(metric: HistoryMetric, companies: List<Company>, range: HistoryRange, plus: Boolean): HistoryMetricData {
        val series = companies.map { series(metric, it) }
        val explicit = series.mapNotNull { it.currency }.distinct().size > 1
        val annual = range.granularity == HistoryGranularity.ANNUAL
        val insights = mutableListOf<String>()
        for (s in series) {
            val available = s.points.filter { it.availability == FinancialAvailability.AVAILABLE && it.value != null }
            fun f(p: HistoryPoint) = format(metric, p.value!!, p.currency, explicit)
            val missing = s.points.count { it.availability != FinancialAvailability.AVAILABLE }
            if (s.points.isNotEmpty() && available.isEmpty()) { insights += "${s.name}: no ${metric.label.replaceFirstChar { it.lowercase() }} values to show for these periods."; continue }
            if (available.size < 2) continue
            val first = available.first(); val last = available.last()
            val consecutive = s.points.all { it.availability == FinancialAvailability.AVAILABLE }
            val steps = available.zipWithNext()
            when (metric) {
                HistoryMetric.REVENUE, HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED -> {
                    val noun = if (metric == HistoryMetric.EPS_DILUTED) "diluted EPS" else metric.label.replaceFirstChar { it.lowercase() }
                    val currencies = available.mapNotNull { it.currency }.distinct()
                    if (currencies.size > 1) {
                        // Amounts in different currencies are never compared, even within one company.
                        insights += "${s.name} reported in more than one currency over these periods (${ComparisonInterpretationEngine.join(currencies)}), so its ${noun} amounts aren't compared across them."
                    } else if (annual && consecutive && steps.all { it.second.value!! > it.first.value!! }) insights += "${s.name}'s $noun increased in each displayed fiscal year (${f(first)} in ${first.label} to ${f(last)} in ${last.label})."
                    else if (annual && consecutive && steps.all { it.second.value!! < it.first.value!! }) insights += "${s.name}'s $noun decreased in each displayed fiscal year (${f(first)} in ${first.label} to ${f(last)} in ${last.label})."
                    else if (last.value!! != first.value!!) insights += "${s.name}'s $noun was ${if (last.value > first.value) "higher" else "lower"} in ${last.label} than in ${first.label} (${f(last)} vs ${f(first)})" +
                        (if (annual) "." else "; neighbouring quarters can differ for seasonal reasons.")
                    if (metric != HistoryMetric.REVENUE) {
                        val negative = available.count { it.value!! < 0 }
                        if (negative > 0) insights += "${s.name} reported ${if (metric == HistoryMetric.NET_INCOME) "negative net income" else "negative diluted EPS"} in $negative of the ${available.size} displayed ${if (annual) "fiscal years" else "quarters"} with figures."
                        steps.firstOrNull { (a, b) -> (a.value!! >= 0) != (b.value!! >= 0) }?.let { (a, b) ->
                            insights += "${s.name}'s ${if (metric == HistoryMetric.NET_INCOME) "net income" else "diluted EPS"} changed from ${if (a.value!! >= 0) "positive" else "negative"} in ${a.label} to ${if (b.value!! >= 0) "positive" else "negative"} in ${b.label}."
                        }
                    }
                    // StockSteps+: the size of the change over the window (only from a positive base).
                    val from = first.value!!; val to = last.value!!
                    if (plus && annual && metric == HistoryMetric.REVENUE && from > 0 && currencies.size == 1)
                        insights += "${s.name}'s revenue changed by ${format(HistoryMetric.REVENUE_GROWTH, (to - from) / from * 100, null)} from ${first.label} to ${last.label}."
                }
                HistoryMetric.NET_MARGIN -> insights += if (f(last) == f(first)) "${s.name}'s net profit margin was the same in ${last.label} as in ${first.label} (${f(last)})."
                    else "${s.name}'s net profit margin was ${if (last.value!! > first.value!!) "higher" else "lower"} in ${last.label} than in ${first.label} (${f(last)} vs ${f(first)})."
                HistoryMetric.REVENUE_GROWTH, HistoryMetric.EPS_GROWTH -> {
                    val values = available.map { it.value!! }
                    val noun = if (metric == HistoryMetric.REVENUE_GROWTH) "revenue growth" else "EPS growth"
                    insights += when {
                        values.all { it > 0 } -> "${s.name}'s year-over-year $noun was positive in every displayed period with a value (from ${f(available.minBy { it.value!! })} to ${f(available.maxBy { it.value!! })})."
                        values.all { it < 0 } -> "${s.name}'s year-over-year $noun was negative in every displayed period with a value."
                        else -> "${s.name}'s year-over-year $noun ranged from ${f(available.minBy { it.value!! })} to ${f(available.maxBy { it.value!! })}."
                    }
                }
                HistoryMetric.REVENUE_INDEX -> Unit
            }
            if (missing > 0 && metric.free) insights += "${s.name}: $missing of ${s.points.size} periods ${if (missing == 1) "has" else "have"} no value here; nothing is filled in."
        }
        if (metric == HistoryMetric.EPS_GROWTH) series.forEach { s ->
            val turnarounds = s.points.count { it.availability == FinancialAvailability.UNRELIABLE_COMPARISON }
            if (turnarounds > 0) insights += "${s.name}: $turnarounds period${if (turnarounds == 1) "" else "s"} had zero or negative EPS a year earlier, so growth is described in words instead of a percentage."
        }
        if (metric == HistoryMetric.REVENUE_INDEX) {
            val ends = series.mapNotNull { s -> s.points.lastOrNull { it.availability == FinancialAvailability.AVAILABLE }?.let { s to it } }
            if (ends.size >= 2) insights += "Starting from 100 in each company's first displayed period, revenue reached " +
                ComparisonInterpretationEngine.join(ends.map { (s, p) -> "${format(metric, p.value!!, null)} for ${s.name} (${p.label})" }) +
                ". This compares relative change in revenue, not company size or investment return."
        }
        val notes = buildList {
            if (metric.unit == HistoryUnit.MONEY || metric.unit == HistoryUnit.PER_SHARE) add(if (explicit) "Amounts are in each company's own reporting currency and aren't converted." else "Amounts are in the reporting currency shown.")
            add(if (annual) "Fiscal-year figures (annual reports); quarterly and annual values are never mixed." else "Fiscal-quarter figures (quarterly reports); quarterly and annual values are never mixed.")
            if (metric == HistoryMetric.EPS_DILUTED) add("Reported diluted EPS; share splits are as adjusted by the data source.")
        }
        return HistoryMetricData(metric, series, insights.map { it.replace("..", ".") }, notes)
    }
}
