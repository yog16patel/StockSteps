package org.example.stocksteps.screener

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialPeriodStatement

/*
 * Company Comparison Phase 4: Guided Research Checklist. Free = the basic checklist, notes, progress,
 * three saved sessions and a basic summary; StockSteps+ = the advanced checklist, more sessions (fair
 * use), snapshots, a detailed summary and a PDF report. Everything is deterministic and built from data
 * the comparison already loads (Phases 1–3); nothing here calls a provider or an AI service, ranks
 * companies or recommends anything. Statuses are only ever set by the user.
 */

@Serializable enum class ResearchStatus(val label: String) { NOT_REVIEWED("Not reviewed"), REVIEWED("Reviewed"), NEEDS_MORE_RESEARCH("Needs more research") }

@Serializable enum class ResearchTier { FREE, PLUS }

@Serializable
data class ResearchCategory(val id: String, val title: String, val tier: ResearchTier, val description: String)

/**
 * One checklist question. [id] is stable forever (responses are keyed by it, so notes survive checklist
 * changes); [metrics] are Phase 1/2 metric ids and [history] Phase 3 metrics whose data gives context.
 */
@Serializable
data class ResearchQuestion(
    val id: String,
    val category: String,
    val tier: ResearchTier,
    val text: String,
    val help: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val metrics: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val history: List<HistoryMetric> = emptyList()
)

object ResearchChecklist {
    /** Bumped when questions change; ids are never reused, so older responses stay attached to their question. */
    const val VERSION = 1
    const val MAX_NOTE = 1_000
    const val MAX_TITLE = 60

    val categories = listOf(
        ResearchCategory("business", "Understanding the businesses", ResearchTier.FREE, "What each company does and whether the comparison is fair."),
        ResearchCategory("growth", "Growth", ResearchTier.FREE, "How sales have changed."),
        ResearchCategory("profitability", "Profitability", ResearchTier.FREE, "How much of each sale becomes profit."),
        ResearchCategory("health", "Financial health", ResearchTier.FREE, "How each company uses debt."),
        ResearchCategory("valuation", "Valuation", ResearchTier.FREE, "What investors currently pay, and what that does and doesn't mean."),
        ResearchCategory("risks", "Risks and unknowns", ResearchTier.FREE, "What's missing or needs context."),
        ResearchCategory("takeaways", "Research takeaways", ResearchTier.FREE, "What you've learned and what's next."),
        ResearchCategory("growthQuality", "Growth quality", ResearchTier.PLUS, "Multi-year growth, consistency and how growth relates to profit."),
        ResearchCategory("profitQuality", "Profitability quality", ResearchTier.PLUS, "Margin history, losses and consistency."),
        ResearchCategory("healthDeep", "Financial health in depth", ResearchTier.PLUS, "Debt, cash and cash flow over time."),
        ResearchCategory("valuationContext", "Valuation context", ResearchTier.PLUS, "Valuation ratios next to growth and margins."),
        ResearchCategory("gaps", "Risks and research gaps", ResearchTier.PLUS, "Missing history, mismatched periods and unusual changes.")
    )

    private fun free(id: String, category: String, text: String, help: String, metrics: List<String> = emptyList(), history: List<HistoryMetric> = emptyList()) =
        ResearchQuestion(id, category, ResearchTier.FREE, text, help, metrics, history)
    private fun plus(id: String, category: String, text: String, help: String, metrics: List<String> = emptyList(), history: List<HistoryMetric> = emptyList()) =
        ResearchQuestion(id, category, ResearchTier.PLUS, text, help, metrics, history)

    val questions: List<ResearchQuestion> = listOf(
        free("b-revenue-model", "business", "Do I understand how each company earns its revenue?",
            "Read each company's profile and latest annual report summary. If you can't explain it in a sentence, note what's unclear."),
        free("b-comparable", "business", "Are these companies directly comparable?",
            "Companies in different industries can have very different margins, debt and valuations for business-model reasons.", listOf("marketCap")),
        free("g-revenue-growth", "growth", "Which company reports higher revenue growth, and over which period?",
            "Compare the same kind of period. One year or quarter isn't a trend.", listOf("quarterRevenueGrowth", "revenueGrowth"), listOf(HistoryMetric.REVENUE)),
        free("g-quarters", "growth", "What do the latest four quarters show?",
            "Look at revenue, net income and diluted EPS in the historical comparison. Quarters can be seasonal.", history = listOf(HistoryMetric.REVENUE, HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED)),
        free("p-margins", "profitability", "How do their profit margins differ?",
            "A higher margin means more of each sale is kept as profit. Typical margins differ by industry.", listOf("netMargin")),
        free("p-consistency", "profitability", "Are earnings consistent across the available periods?",
            "Look for losses or big swings in net income and diluted EPS.", history = listOf(HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED)),
        free("h-debt", "health", "How does each company use debt?",
            "Debt to equity compares borrowing with shareholders' equity. It isn't comparable for banks and insurers.", listOf("debtEquity")),
        free("v-ratios", "valuation", "What do their valuation ratios suggest — and not suggest?",
            "P/E and price to sales show what investors pay today, not whether a stock is cheap or a good investment.", listOf("pe", "priceSales")),
        free("v-dividends", "valuation", "Do they pay dividends, and how do yields compare?",
            "A yield describes past payments relative to price. Dividends can change.", listOf("dividendYield")),
        free("r-context", "risks", "Which metrics need additional context?",
            "Open Explain on metrics marked “Compare with care” or “Not directly comparable”."),
        free("r-missing", "risks", "What important information is missing?",
            "N/A values, missing periods and different fiscal calendars limit what you can conclude."),
        free("t-observations", "takeaways", "What are my key research observations?",
            "Write what the numbers show in your own words. Separate facts from your opinions."),
        free("t-next", "takeaways", "What questions do I still need to investigate?",
            "List what you'd read next (annual reports, earnings calls, industry information)."),
        // StockSteps+ advanced layer (builds on the basic questions; uses Phase 3 3Y/5Y history).
        plus("gq-multi-year", "growthQuality", "How has revenue changed over three to five fiscal years?",
            "Use 3Y/5Y history and the revenue index to compare relative change across companies of different sizes.", history = listOf(HistoryMetric.REVENUE, HistoryMetric.REVENUE_INDEX)),
        plus("gq-consistency", "growthQuality", "Is revenue growth consistent, accelerating or slowing?",
            "Look at year-over-year revenue growth in each fiscal year, not just the latest one.", history = listOf(HistoryMetric.REVENUE_GROWTH)),
        plus("gq-profit-growth", "growthQuality", "Do revenue growth and profit growth move together?",
            "Compare revenue growth with EPS growth. Profit can grow faster or slower than sales.", history = listOf(HistoryMetric.REVENUE_GROWTH, HistoryMetric.EPS_GROWTH)),
        plus("gq-annual-vs-quarter", "growthQuality", "Do annual and quarterly growth tell the same story?",
            "Latest-quarter growth can differ from the fiscal year. Neither alone is a trend.", listOf("quarterRevenueGrowth", "revenueGrowth")),
        plus("pq-margin-history", "profitQuality", "Have net profit margins expanded or contracted?",
            "Use net profit margin history. One-time items can move a single year.", history = listOf(HistoryMetric.NET_MARGIN)),
        plus("pq-losses", "profitQuality", "Were there loss-making periods, and how often?",
            "Count negative net income periods in the 5Y history.", history = listOf(HistoryMetric.NET_INCOME)),
        plus("hd-leverage-trend", "healthDeep", "How has debt relative to equity changed over the years?",
            "Debt ÷ equity from each annual balance sheet. Not meaningful with zero or negative equity, or for banks and insurers."),
        plus("hd-cash-flow", "healthDeep", "Does operating cash flow support reported earnings?",
            "Compare operating cash flow with net income. Accounting profit and cash can differ."),
        plus("hd-fcf", "healthDeep", "Is free cash flow positive, and is it consistent?",
            "Free cash flow = operating cash flow − capital expenditure, as reported or calculated from the statements."),
        plus("vc-pe-growth", "valuationContext", "How does each P/E compare with that company's growth?",
            "A higher P/E alongside faster growth is a pattern to investigate, not a conclusion.", listOf("pe", "revenueGrowth")),
        plus("vc-ps-margin", "valuationContext", "How does price to sales relate to profit margins?",
            "Higher-margin businesses often have higher price-to-sales ratios.", listOf("priceSales", "netMargin")),
        plus("rg-gaps", "gaps", "Where are the gaps in the financial history?",
            "Missing years or quarters, currency changes and fiscal calendars that don't line up."),
        plus("rg-unusual", "gaps", "Were there unusual changes worth investigating?",
            "Very large swings in revenue, margins or EPS. The data shows that they happened, not why.", history = listOf(HistoryMetric.REVENUE_GROWTH, HistoryMetric.NET_MARGIN))
    )
    private val byId = questions.associateBy { it.id }
    fun question(id: String): ResearchQuestion? = byId[id]
    fun questions(plus: Boolean) = questions.filter { plus || it.tier == ResearchTier.FREE }
    fun category(id: String) = categories.first { it.id == id }
}

@Serializable
data class ResearchResponse(val questionId: String, val status: ResearchStatus = ResearchStatus.NOT_REVIEWED, val note: String = "", val updatedAt: Long = 0)

@Serializable
data class ResearchProgress(val total: Int, val reviewed: Int, val needsMore: Int) {
    val notReviewed: Int get() = total - reviewed - needsMore
    val percent: Int get() = if (total == 0) 0 else reviewed * 100 / total
    companion object {
        /** Over the questions this tier sees; responses to unknown or hidden questions are kept but not counted. */
        fun of(responses: Map<String, ResearchResponse>, plus: Boolean): ResearchProgress {
            val visible = ResearchChecklist.questions(plus)
            return ResearchProgress(visible.size, visible.count { responses[it.id]?.status == ResearchStatus.REVIEWED },
                visible.count { responses[it.id]?.status == ResearchStatus.NEEDS_MORE_RESEARCH })
        }
    }
}

/** A small, display-ready reference to a value at snapshot time (never a dataset). */
@Serializable
data class SnapshotMetric(val symbol: String, val metric: String, val value: String, val period: String? = null)

/** A dated, user-created copy of research progress and notes; values are as of [dataAsOf], not live. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchSnapshot(
    val id: String,
    val label: String,
    val createdAt: Long,
    val dataAsOf: String? = null,
    @EncodeDefault val symbols: List<String> = emptyList(),
    @EncodeDefault val responses: Map<String, ResearchResponse> = emptyMap(),
    val progress: ResearchProgress,
    @EncodeDefault val metrics: List<SnapshotMetric> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchSession(
    val id: String,
    val title: String,
    @EncodeDefault val symbols: List<String> = emptyList(),
    val checklistVersion: Int = ResearchChecklist.VERSION,
    @EncodeDefault val responses: Map<String, ResearchResponse> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Incremented on every write; clients send it back to detect edits from another device. */
    val revision: Int = 0,
    @EncodeDefault val snapshots: List<ResearchSnapshot> = emptyList()
)

/** The list entry stored in the per-user index (counted atomically for the session limit). */
@Serializable
data class ResearchSessionInfo(val id: String, val title: String, val symbols: List<String>, val createdAt: Long, val updatedAt: Long,
                               val reviewed: Int = 0, val snapshotCount: Int = 0)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchIndex(@EncodeDefault val sessions: List<ResearchSessionInfo> = emptyList())

// ---------- API ----------

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchSessionsResponse(
    @EncodeDefault val sessions: List<ResearchSessionInfo> = emptyList(),
    val limit: Int,
    val plus: Boolean,
    val canCreate: Boolean,
    val message: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchSessionResponse(
    val session: ResearchSession,
    val plus: Boolean,
    val progress: ResearchProgress,
    /** Advanced questions exist but are read-only for a free account (their notes are kept). */
    val advancedLocked: Boolean,
    val snapshotLimit: Int,
    val checklistVersion: Int = ResearchChecklist.VERSION
)

@Serializable data class CreateResearchRequest(val symbols: List<String>, val title: String? = null)
@Serializable data class ResearchResponseUpdate(val questionId: String, val status: ResearchStatus? = null, val note: String? = null)
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class UpdateResearchRequest(val title: String? = null, @EncodeDefault val responses: List<ResearchResponseUpdate> = emptyList(), val expectedRevision: Int? = null)
@Serializable data class CreateSnapshotRequest(val label: String? = null)

// ---------- Summary ----------

/** What a summary line is, so user notes are never presented as facts. */
@Serializable enum class SummaryKind(val label: String) {
    FACT("Reported data"), CALCULATION("Calculated from reported data"), USER_NOTE("Your note"), EDUCATION("Education"), LIMITATION("Data limitation")
}

@Serializable data class SummaryItem(val kind: SummaryKind, val text: String, val attribution: String? = null)
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class SummarySection(val title: String, @EncodeDefault val items: List<SummaryItem> = emptyList())

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchSummary(
    val sessionId: String,
    val title: String,
    val detailed: Boolean,
    val generatedAt: String,
    val dataAsOf: String? = null,
    @EncodeDefault val symbols: List<String> = emptyList(),
    val progress: ResearchProgress,
    @EncodeDefault val sections: List<SummarySection> = emptyList(),
    val sampleData: Boolean = false
)

/** Balance-sheet and cash-flow observations for the advanced financial-health questions (annual statements). */
object ResearchHealthEngine {
    /**
     * Per company, from the same annual statements as the historical comparison (no extra requests):
     * debt ÷ equity per fiscal year (`totalDebt` ÷ `equity`, equity > 0), operating cash flow vs net income
     * (`operatingCashFlow`, `netIncome`), free cash flow (`freeCashFlow`: reported, or OCF − capex as mapped).
     */
    fun observations(name: String, statements: List<FinancialPeriodStatement>, financial: Boolean): List<SummaryItem> {
        val years = statements.filter { it.period == "FY" && it.fiscalYear != null }.sortedBy { it.fiscalYear }.takeLast(5)
        if (years.isEmpty()) return listOf(SummaryItem(SummaryKind.LIMITATION, "$name: no annual balance-sheet or cash-flow figures are available here."))
        val items = mutableListOf<SummaryItem>()
        fun money(v: Double, c: String?) = MetricFormatter.money(v, c)
        if (financial) items += SummaryItem(SummaryKind.LIMITATION, "$name is a financial company: debt to equity isn't comparable with other industries, so it isn't calculated.")
        else {
            val ratios = years.mapNotNull { y -> val d = y.totalDebt; val e = y.equity
                if (d != null && e != null && e > 0 && d.isFinite() && e.isFinite()) y to d / e else null }
            val skipped = years.count { (it.equity ?: 1.0) <= 0 }
            if (ratios.size >= 2) items += SummaryItem(SummaryKind.CALCULATION,
                "$name's debt to equity went from ${MetricFormatter.decimals(ratios.first().second, 2)} (FY${ratios.first().first.fiscalYear}) to ${MetricFormatter.decimals(ratios.last().second, 2)} (FY${ratios.last().first.fiscalYear}) (total debt ÷ shareholders' equity).")
            else items += SummaryItem(SummaryKind.LIMITATION, "$name: fewer than two fiscal years have both total debt and positive equity, so a debt-to-equity trend isn't shown.")
            if (skipped > 0) items += SummaryItem(SummaryKind.LIMITATION, "$name had zero or negative equity in $skipped fiscal year${if (skipped == 1) "" else "s"}, where debt to equity isn't meaningful.")
        }
        val cash = years.filter { it.operatingCashFlow != null && it.netIncome != null }
        if (cash.isNotEmpty()) {
            val below = cash.count { it.operatingCashFlow!! < it.netIncome!! }
            val latest = cash.last()
            items += SummaryItem(SummaryKind.FACT, "$name reported operating cash flow of ${money(latest.operatingCashFlow!!, latest.currency)} and net income of ${money(latest.netIncome!!, latest.currency)} in FY${latest.fiscalYear}.")
            if (cash.size >= 2) items += SummaryItem(SummaryKind.CALCULATION, "$name's operating cash flow was below net income in $below of ${cash.size} fiscal years with both figures.")
        } else items += SummaryItem(SummaryKind.LIMITATION, "$name: operating cash flow isn't available with net income for the same years.")
        val fcf = years.filter { it.freeCashFlow != null }
        if (fcf.isNotEmpty()) {
            val positive = fcf.count { it.freeCashFlow!! > 0 }
            items += SummaryItem(SummaryKind.CALCULATION, "$name's free cash flow was positive in $positive of ${fcf.size} fiscal years with a value (latest FY${fcf.last().fiscalYear}: ${money(fcf.last().freeCashFlow!!, fcf.last().currency)}).")
        } else items += SummaryItem(SummaryKind.LIMITATION, "$name: free cash flow isn't available for these years.")
        if (years.mapNotNull { it.currency }.distinct().size > 1) items += SummaryItem(SummaryKind.LIMITATION, "$name changed reporting currency over these years; amounts aren't converted.")
        return items
    }
}

/**
 * Deterministic research summaries. Basic (free): companies, key differences (Phase 2 summary), latest
 * four quarters (Phase 3 free), notes, open questions, limitations. Detailed (StockSteps+): adds every
 * metric observation, 3Y/5Y history observations, financial health in depth and valuation context.
 */
object ResearchSummaryEngine {
    data class Inputs(
        val session: ResearchSession,
        val comparison: ComparisonResponse?,
        val history: HistoricalComparison?,
        /** Annual statements per symbol (StockSteps+ financial-health section); empty for free. */
        val statements: Map<String, List<FinancialPeriodStatement>> = emptyMap(),
        val detailed: Boolean,
        val generatedAt: String
    )

    fun build(inputs: Inputs): ResearchSummary {
        val session = inputs.session
        val companies = inputs.comparison?.companies.orEmpty()
        val records = companies.mapNotNull { it.record }
        val interpretation = inputs.comparison?.let { ComparisonInterpretationEngine.interpret(it.companies, it.fx) }
        val sections = mutableListOf<SummarySection>()

        sections += SummarySection("Companies compared", companies.map { c ->
            val r = c.record
            if (r == null) SummaryItem(SummaryKind.LIMITATION, "${c.symbol}: ${c.error ?: "data isn't available right now"}.")
            else SummaryItem(SummaryKind.FACT, "${r.name} (${r.symbol}) — " + listOfNotNull(r.exchange, r.currency, r.sector, r.industry).joinToString(" · ") +
                (r.fundamentalsAsOf?.let { "; financial statements retrieved ${it.take(10)}" } ?: ""))
        })

        if (interpretation != null) {
            val key = interpretation.summary.map { SummaryItem(if (it.metricId == null) SummaryKind.EDUCATION else SummaryKind.CALCULATION, it.text) }
            sections += SummarySection("Key differences", key)
            if (inputs.detailed) {
                val groups = listOf("Growth" to listOf("quarterRevenueGrowth", "revenueGrowth"), "Profitability" to listOf("netMargin"),
                    "Financial health" to listOf("debtEquity"), "Valuation context" to listOf("pe", "priceSales", "dividendYield"), "Size" to listOf("marketCap"))
                val pe = interpretation.metrics["pe"]
                for ((title, ids) in groups) {
                    val items = ids.mapNotNull { interpretation.metrics[it] }.flatMap { m ->
                        listOfNotNull(SummaryItem(if (m.comparability == Comparability.COMPARABLE || m.comparability == Comparability.COMPARABLE_WITH_CAVEATS) SummaryKind.CALCULATION else SummaryKind.LIMITATION,
                            "${m.label}: ${m.observation}"), m.meaning?.let { SummaryItem(SummaryKind.EDUCATION, it) })
                    }.toMutableList()
                    if (title == "Valuation context" && pe != null && pe.comparability != Comparability.INSUFFICIENT_DATA && pe.comparability != Comparability.NOT_COMPARABLE)
                        items += SummaryItem(SummaryKind.EDUCATION, "Read P/E next to revenue growth and margins: different growth or profitability can be one reason valuations differ, but the data doesn't show why.")
                    sections += SummarySection(title, items)
                }
            }
        } else sections += SummarySection("Key differences", listOf(SummaryItem(SummaryKind.LIMITATION, "Comparison data couldn't be loaded, so no metric observations are included.")))

        inputs.history?.let { h ->
            val title = if (h.range == HistoryRange.ONE_YEAR) "Latest four quarters" else "Financial history (${h.range.label})"
            val metricInsights = h.metrics.filter { inputs.detailed || it.metric.free }.flatMap { m -> m.insights.map { SummaryItem(SummaryKind.CALCULATION, it) } }
            sections += SummarySection(title, metricInsights.take(if (inputs.detailed) 30 else 8) + h.insights.map { SummaryItem(SummaryKind.LIMITATION, it) })
        }

        if (inputs.detailed && inputs.statements.isNotEmpty()) sections += SummarySection("Financial health in depth", inputs.statements.flatMap { (symbol, statements) ->
            val record = records.firstOrNull { it.symbol == symbol }
            ResearchHealthEngine.observations(record?.name ?: symbol, statements, record?.sector == "Financial Services")
        })

        val visible = ResearchChecklist.questions(inputs.detailed)
        val notes = visible.mapNotNull { q -> session.responses[q.id]?.note?.takeIf { it.isNotBlank() }?.let { SummaryItem(SummaryKind.USER_NOTE, it, "Your note on: ${q.text}") } }
        sections += SummarySection("Your notes", notes.ifEmpty { listOf(SummaryItem(SummaryKind.EDUCATION, "You haven't written any notes yet. Notes are yours; they're never shown as data.")) })
        val open = visible.filter { session.responses[it.id]?.status == ResearchStatus.NEEDS_MORE_RESEARCH }
        val unreviewed = visible.count { (session.responses[it.id]?.status ?: ResearchStatus.NOT_REVIEWED) == ResearchStatus.NOT_REVIEWED }
        sections += SummarySection("Questions needing more research", open.map { SummaryItem(SummaryKind.USER_NOTE, it.text, "You marked this as needing more research") } +
            listOfNotNull(if (unreviewed > 0) SummaryItem(SummaryKind.EDUCATION, "$unreviewed question${if (unreviewed == 1) " hasn't" else "s haven't"} been reviewed yet.") else null))

        val limitations = buildList {
            interpretation?.metrics?.values?.filter { it.comparability == Comparability.NOT_COMPARABLE || it.comparability == Comparability.INSUFFICIENT_DATA }
                ?.forEach { add(SummaryItem(SummaryKind.LIMITATION, "${it.label}: ${it.comparability.label.lowercase()}. ${it.caveats.firstOrNull().orEmpty()}".trim())) }
            interpretation?.industryNote?.let { add(SummaryItem(SummaryKind.LIMITATION, it)) }
            inputs.comparison?.notes?.forEach { add(SummaryItem(SummaryKind.LIMITATION, it)) }
            if (!inputs.detailed) add(SummaryItem(SummaryKind.EDUCATION, "The detailed summary (StockSteps+) adds every metric observation, 3- and 5-year history and financial health in depth."))
        }
        sections += SummarySection("Data sources and limitations", limitations.distinctBy { it.text })
        sections += SummarySection("About this summary", listOf(SummaryItem(SummaryKind.EDUCATION,
            "Education, not investment advice. This summary describes reported figures and your own notes; it doesn't rank the companies, predict results or recommend buying or selling.")))

        val sample = inputs.comparison?.sampleData == true || inputs.history?.sampleData == true
        return ResearchSummary(session.id, session.title, inputs.detailed, inputs.generatedAt,
            records.mapNotNull { it.fundamentalsAsOf }.maxOrNull() ?: inputs.comparison?.asOf, session.symbols,
            ResearchProgress.of(session.responses, inputs.detailed), sections.filter { it.items.isNotEmpty() }, sample)
    }

    /** Small value references kept with a snapshot (what the table showed then), never statement datasets. */
    fun snapshotMetrics(comparison: ComparisonResponse?): List<SnapshotMetric> = comparison?.companies.orEmpty().mapNotNull { it.record }.flatMap { r ->
        ComparisonInterpretationEngine.GUIDED.filter { it != "marketCap" }.mapNotNull { id ->
            r.metrics[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE || it.availability == FinancialAvailability.NO_DIVIDEND }?.let { v ->
                SnapshotMetric(r.symbol, id, MetricFormatter.cell(ScreenerDefinitions.metric(id), v, r.currency).text, ComparisonInterpretationEngine.periodLabel(v.basis))
            }
        }
    }
}
