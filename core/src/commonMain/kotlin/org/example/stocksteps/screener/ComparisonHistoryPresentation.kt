package org.example.stocksteps.screener

import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.network.StockStepsApi

// ---------- Phase 3 client: data access, StockSteps+ preview and formatting (no financial formulas) ----------

/** Where history comes from; the server decides what the caller may see. */
fun interface ComparisonHistorySource {
    suspend fun history(symbols: List<String>, range: HistoryRange): HistoricalComparison
}

/**
 * Guests use the public free view; signed-in users use the account route, where the server checks
 * StockSteps+ for 3Y/5Y and advanced metrics. The request is tied to the account that made it.
 */
class RemoteComparisonHistory(private val public: StockStepsApi, private val user: UserApi?, private val uid: () -> String?) : ComparisonHistorySource {
    override suspend fun history(symbols: List<String>, range: HistoryRange): HistoricalComparison {
        val owner = uid()
        return if (owner != null && user != null) user.compareHistory(symbols, range, owner) else public.compareHistory(symbols, range)
    }
}

/** The StockSteps+ preview for a locked range or metric. Honest: no prices, no purchase claims. */
data class HistoryUpsell(val title: String, val body: String, val benefits: List<String>, val signIn: Boolean) {
    val footnote: String get() = "The latest four quarters of revenue, net income and diluted EPS stay free. In-app purchase isn't available yet; your StockSteps+ status is in Settings."
    companion object {
        val BENEFITS = listOf("3- and 5-year financial history", "Revenue growth, net profit margin and EPS growth over time",
            "A revenue index to compare companies of different sizes", "More detailed historical observations")
        fun forRange(range: HistoryRange, signIn: Boolean) = HistoryUpsell("See ${range.label} history with StockSteps+",
            "${range.description} for each company, as reported, side by side." + if (signIn) " Sign in first to use StockSteps+." else "", BENEFITS, signIn)
        fun forMetric(metric: HistoryMetric, signIn: Boolean) = HistoryUpsell("${metric.label} history is part of StockSteps+",
            metric.definition + if (signIn) " Sign in first to use StockSteps+." else "", BENEFITS, signIn)
    }
}

data class HistoryChartSeries(val symbol: String, val label: String, val values: List<Double?>)
data class HistoryCell(val text: String, val period: String?, val explanation: String?) {
    val available: Boolean get() = explanation == null
}
data class HistoryRow(val title: String, val cells: List<HistoryCell>, val alignment: PeriodAlignment, val note: String?)

/**
 * One metric ready to draw: chart series (oldest → newest, null = gap), the values table (newest first,
 * each company's own fiscal label and end date), observations and notes. Values come from the server.
 */
data class HistoryView(
    val metric: HistoryMetric,
    val title: String,
    val definition: String,
    val unitNote: String,
    val xLabels: List<String>,
    val series: List<HistoryChartSeries>,
    /** One line per slot for chart touch details ("Q4 FY2025 · AAPL $114.2B · MSFT $77.3B"). */
    val details: List<String>,
    val description: String,
    val rows: List<HistoryRow>,
    val symbols: List<String>,
    val insights: List<String>,
    val notes: List<String>,
    /** Draw a zero line when values cross zero (losses), and the 100 line for the index. */
    val reference: Double?,
    val referenceLabel: String?,
    val empty: Boolean
) {
    companion object {
        fun of(history: HistoricalComparison, metric: HistoryMetric): HistoryView? {
            val data = history.metric(metric) ?: return null
            val drawable = data.series.filter { it.points.isNotEmpty() }
            val explicit = drawable.mapNotNull { it.currency }.distinct().size > 1
            fun text(p: HistoryPoint) = p.value?.let { HistoricalComparisonEngine.format(metric, it, p.currency, explicit) }
            val slots = history.periods.size
            val annual = history.granularity == HistoryGranularity.ANNUAL
            val labels = (0 until slots).map { i -> drawable.firstNotNullOfOrNull { it.points.getOrNull(i)?.label } ?: "" }
            val values = drawable.map { s -> HistoryChartSeries(s.symbol, s.symbol + (s.base?.let { " ($it)" } ?: ""), (0 until slots).map { i -> s.points.getOrNull(i)?.value }) }
            val details = (0 until slots).map { i ->
                drawable.joinToString(" · ") { s -> val p = s.points.getOrNull(i); "${s.symbol} ${p?.label ?: ""} ${p?.let(::text) ?: "—"}".replace("  ", " ") }
            }
            val rows = (slots - 1 downTo 0).map { i ->
                val period = history.periods.getOrNull(i)
                HistoryRow(if (i == slots - 1) "Latest" else "${slots - 1 - i} ${if (annual) "year" else "quarter"}${if (slots - 1 - i == 1) "" else "s"} earlier",
                    data.series.map { s ->
                        val p = s.points.getOrNull(i)
                        when {
                            p == null -> HistoryCell("N/A", null, history.companies.firstOrNull { it.symbol == s.symbol }?.error ?: s.note ?: "No figures for this period.")
                            p.value != null && p.availability == FinancialAvailability.AVAILABLE -> HistoryCell(text(p)!!, listOfNotNull(p.label, p.periodEnd?.let { "ended $it" }).joinToString(", "), null)
                            else -> HistoryCell("N/A", p.label, p.note ?: MetricFormatter.reason(p.availability))
                        }
                    }, period?.alignment ?: PeriodAlignment.UNKNOWN, period?.note)
            }
            val all = values.flatMap { it.values.filterNotNull() }
            val reference = when {
                metric == HistoryMetric.REVENUE_INDEX -> 100.0
                all.any { it < 0 } && all.any { it > 0 } -> 0.0
                else -> null
            }
            val description = "${data.metric.label}, ${if (annual) "fiscal years" else "fiscal quarters"}: " + drawable.joinToString("; ") { s ->
                s.symbol + " " + s.points.joinToString(", ") { p -> "${p.label} ${text(p) ?: "not available"}" }
            }
            return HistoryView(metric, metric.label, metric.definition,
                when (metric.unit) {
                    HistoryUnit.MONEY -> if (explicit) "Each company's own reporting currency (not converted)" else "Reporting currency"
                    HistoryUnit.PER_SHARE -> "Per share, reporting currency"
                    HistoryUnit.PERCENT -> "Percent"
                    HistoryUnit.INDEX -> "Index: first displayed period = 100 (not a return)"
                },
                labels.let { if (it.size >= 2) listOf(it.first(), it.last()) else it }, values, details, description, rows,
                data.series.map { it.symbol }, data.insights, data.notes, reference,
                if (reference == 100.0) "Base (100)" else if (reference == 0.0) "Zero" else null,
                all.isEmpty())
        }
    }
}
