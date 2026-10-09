package org.example.stocksteps.screener

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.companydetail.MetricEducation
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import org.example.stocksteps.portfolio.analytics.EntitlementStatus
import kotlin.math.abs
import kotlin.math.round

/*
 * Company Comparison Phase 5 (StockSteps+): AI Comparison Assistant — shared models and the
 * deterministic, testable parts of the pipeline. The server builds a compact context from data it has
 * already validated (Phase 1 comparison, Phase 2 interpretation, Phase 3 history), registers every
 * citable fact as [FinancialEvidence] with a stable id, asks the model for a typed draft, and
 * [ComparisonAiValidator] checks that draft (evidence ids, numbers, advice, rankings, predictions,
 * unsupported causes, links, leakage) before anyone sees it. The apps only render the result.
 */

/** Where a fact comes from: provider-reported data, a StockSteps calculation, a company profile field, or education. */
@Serializable enum class EvidenceKind(val label: String) {
    REPORTED("Reported data"), CALCULATED("StockSteps calculation"), PROFILE("Company profile"), EDUCATION("StockSteps Learn")
}

/**
 * One verified fact the assistant may cite. [id] is stable for the same company, metric and period
 * ("AAPL.pe", "MSFT.h.revenue.Q3-FY2026", "I.netMargin", "L.pe"), so a citation always points at the
 * value the Compare screen shows. [value] is the display text exactly as the apps format it.
 */
@Serializable
data class FinancialEvidence(
    val id: String,
    val kind: EvidenceKind,
    val label: String,
    val value: String,
    val symbol: String? = null,
    val companyName: String? = null,
    /** Phase 1/2 metric id ("pe"), or a Phase 3 metric name ("REVENUE"); null for profile and summary facts. */
    val metricId: String? = null,
    val unit: String? = null,
    val currency: String? = null,
    val periodEnd: String? = null,
    /** "Trailing twelve months", "FY ended Sep 2025", "Q3 FY2026 vs Q3 FY2025". */
    val fiscalPeriod: String? = null,
    val source: String? = null,
    val retrievedAt: String? = null,
    val definitionVersion: String = ComparisonGrounding.DEFINITION_VERSION
) {
    /** What a screen shows on a citation chip. */
    val chip: String get() = listOfNotNull(symbol, label).joinToString(" · ")
    val accessibility: String get() = "${kind.label}: ${listOfNotNull(companyName ?: symbol, label).joinToString(", ")}, $value" + (fiscalPeriod?.let { ", $it" } ?: "")
}

/** What the assistant is asked to do. One request = one charged unit, whatever the type. */
@Serializable enum class ComparisonAiType(val label: String) {
    OVERVIEW("Explain the biggest differences"),
    HISTORY("What does their financial history show?"),
    QUESTION("Question"),
    METRIC("Explain a metric difference"),
    RESEARCH("Help with a research question");
    val summary: Boolean get() = this == OVERVIEW || this == HISTORY
}

/**
 * A request from the app. Financial figures are never accepted from the client: the server resolves
 * the companies' data itself. Notes are never sent either: with [includeNotes] the server reads only
 * the note for [researchQuestionId] in the caller's own [researchSessionId].
 */
@Serializable
data class ComparisonAiRequest(
    val symbols: List<String>,
    val type: ComparisonAiType = ComparisonAiType.OVERVIEW,
    val question: String? = null,
    val metricId: String? = null,
    val conversationId: String? = null,
    /** The context version the app last saw; a different current version starts a new conversation. */
    val contextVersion: String? = null,
    val historyRange: String? = null,
    val researchSessionId: String? = null,
    val researchQuestionId: String? = null,
    /** Explicit, per-request consent to send that one research note for AI processing. */
    val includeNotes: Boolean = false,
    /** Client-generated; a retry with the same key returns the same answer and is never charged twice. */
    val idempotencyKey: String
)

/** One observation: the fact (with its evidence) and why it matters for a beginner. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AiObservation(
    val category: String,
    val text: String,
    val whyItMatters: String? = null,
    @EncodeDefault val evidenceIds: List<String> = emptyList()
)

@Serializable enum class ComparisonAiScope {
    /** Answered from the verified data. */
    ANSWERED,
    /** The data can't answer it (e.g. "why"): says so. */
    INSUFFICIENT_DATA,
    /** Advice, predictions or out-of-scope requests: answered without AI, never charged. */
    DECLINED,
    /** The AI output couldn't be verified: StockSteps' deterministic observations instead, never charged. */
    FALLBACK
}

/** Rolling 30-day and daily allowances (server-enforced; [plus] and limits come from the server only). */
@Serializable
data class ComparisonAiUsage(
    val plus: Boolean,
    val entitlementStatus: EntitlementStatus = EntitlementStatus.NONE,
    val dailyLimit: Int = 0,
    val dailyUsed: Int = 0,
    val dailyResetAt: String? = null,
    val windowDays: Int = 30,
    val windowLimit: Int = 0,
    val windowUsed: Int = 0,
    /** When the oldest request in the window drops out (null when none). */
    val windowResetAt: String? = null,
    val aiAvailable: Boolean = true,
    val asOf: String,
    val note: String? = null,
    val sample: Boolean = false
) {
    val dailyRemaining: Int get() = (dailyLimit - dailyUsed).coerceAtLeast(0)
    val windowRemaining: Int get() = (windowLimit - windowUsed).coerceAtLeast(0)
    val remaining: Int get() = minOf(dailyRemaining, windowRemaining)
    val exhausted: Boolean get() = plus && remaining == 0
    val label: String get() = if (!plus) "StockSteps+ feature" else "$remaining AI request${if (remaining == 1) "" else "s"} left · $dailyUsed of $dailyLimit today · $windowUsed of $windowLimit in $windowDays days"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComparisonAiResponse(
    val id: String,
    val conversationId: String? = null,
    val type: ComparisonAiType,
    val question: String? = null,
    @EncodeDefault val symbols: List<String> = emptyList(),
    val summary: String,
    @EncodeDefault val observations: List<AiObservation> = emptyList(),
    @EncodeDefault val caveats: List<String> = emptyList(),
    @EncodeDefault val researchQuestions: List<String> = emptyList(),
    /** Only the evidence this answer cites (validated server-side). */
    @EncodeDefault val evidence: List<FinancialEvidence> = emptyList(),
    @EncodeDefault val missingData: List<String> = emptyList(),
    @EncodeDefault val warnings: List<String> = emptyList(),
    val scope: ComparisonAiScope = ComparisonAiScope.ANSWERED,
    val dataAsOf: String? = null,
    val generatedAt: String,
    val contextVersion: String,
    val promptVersion: String,
    val model: String,
    val disclaimer: String,
    val usage: ComparisonAiUsage? = null,
    /** Served from a cache or an identical earlier request: no new AI call and no extra quota. */
    val cached: Boolean = false,
    /** True when the answer used the caller's own research note (never shared or cached across users). */
    val usedNote: Boolean = false,
    val researchQuestionId: String? = null,
    /** A new conversation started (companies or data changed). */
    val conversationReset: Boolean = false,
    /** Statements removed because they couldn't be checked against the data. */
    val removedStatements: Int = 0,
    val note: String? = null,
    /** MOCK: deterministic template, not an AI service. */
    val sample: Boolean = false
) {
    fun evidence(id: String): FinancialEvidence? = evidence.firstOrNull { it.id == id }
    val aiGenerated: Boolean get() = scope == ComparisonAiScope.ANSWERED || scope == ComparisonAiScope.INSUFFICIENT_DATA
    /** Plain text for "Copy to my notes" (always labelled as an AI suggestion, never the user's own words). */
    fun noteText(): String = buildString {
        append("AI suggestion (StockSteps AI, ${generatedAt.take(10)}): ").append(summary)
        observations.take(3).forEach { append("\n• ").append(it.text) }
    }.take(ResearchChecklist.MAX_NOTE)
}

/** The model's typed draft; never shown before [ComparisonAiValidator] accepts it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComparisonAiDraft(
    val summary: String,
    @EncodeDefault val observations: List<AiObservation> = emptyList(),
    @EncodeDefault val caveats: List<String> = emptyList(),
    @EncodeDefault val researchQuestions: List<String> = emptyList(),
    /** "answered" or "insufficient". */
    val scope: String = "answered",
    @EncodeDefault val summaryEvidenceIds: List<String> = emptyList()
)

// ---------- Grounding context ----------

@Serializable
data class AiCompany(val symbol: String, val name: String, val exchange: String? = null, val sector: String? = null, val industry: String? = null,
                     val currency: String? = null, val error: String? = null)

/**
 * Everything one request may use. Compact by design: only the metrics the question is about, history
 * points only when history is asked about, and gaps listed as unavailable (never zero or estimated).
 */
data class ComparisonAiContext(
    val symbols: List<String>,
    val companies: List<AiCompany>,
    val evidence: List<FinancialEvidence>,
    val missing: List<String>,
    val warnings: List<String>,
    val dataAsOf: String?,
    val historyRange: HistoryRange?,
    /** Changes whenever any fact, gap or warning in this context changes (shared answers are keyed by it). */
    val version: String,
    /** Changes whenever the companies' underlying data changes, whatever the question (conversations are bound to it). */
    val dataVersion: String,
    val sample: Boolean,
    /** The caller's own note, only after explicit consent (never part of [version] or shared caches). */
    val userNote: String? = null,
    val researchQuestion: ResearchQuestion? = null
) {
    private val byId = evidence.associateBy { it.id }
    fun evidence(id: String): FinancialEvidence? = byId[id]
    val ids: Set<String> get() = byId.keys
    /** All verified text (numbers in any part of an answer must come from here). */
    fun text(): String = (evidence.flatMap { listOfNotNull(it.label, it.value, it.fiscalPeriod, it.periodEnd, it.companyName, it.symbol) } +
        missing + warnings + companies.flatMap { listOfNotNull(it.name, it.sector, it.industry) } + listOfNotNull(researchQuestion?.text)).joinToString(" ")
}

/** What a request focuses on: which metrics and whether history points are included. */
data class ComparisonAiFocus(val metrics: List<String>, val history: Boolean, val historyMetrics: List<HistoryMetric>) {
    companion object {
        private val keywords = listOf(
            listOf("p/e", " pe ", "pe ratio", "price to earnings", "price-to-earnings", "earnings multiple", "valuation", "valued", "expensive", "cheap") to listOf("pe", "priceSales"),
            listOf("price to sales", "price-to-sales", "p/s") to listOf("priceSales"),
            listOf("forward") to listOf("forwardPe"),
            listOf("growth", "growing", "grow", "revenue", "sales") to listOf("quarterRevenueGrowth", "revenueGrowth"),
            listOf("profit", "margin", "profitab", "earn", "loss") to listOf("netMargin"),
            listOf("debt", "leverage", "borrow", "financial health", "balance sheet", "risk") to listOf("debtEquity"),
            listOf("dividend", "yield", "payout", "income") to listOf("dividendYield"),
            listOf("size", "market cap", "market value", "bigger", "larger", "smaller") to listOf("marketCap")
        )
        private val historyWords = listOf("history", "historical", "trend", "over time", "years", "quarters", "consistent", "consistency", "past", "improved", "improving", "long-term", "long term", "eps history")

        /** Deterministic keyword routing (no AI) so a simple P/E question doesn't ship whole histories. */
        fun of(type: ComparisonAiType, question: String?, metricId: String?, research: ResearchQuestion?): ComparisonAiFocus {
            val q = " " + question.orEmpty().lowercase() + " "
            val wantsHistory = type == ComparisonAiType.HISTORY || historyWords.any { it in q } || research?.history?.isNotEmpty() == true
            val historyMetrics = when {
                research?.history?.isNotEmpty() == true -> research.history
                "eps" in q -> listOf(HistoryMetric.EPS_DILUTED, HistoryMetric.EPS_GROWTH)
                "margin" in q || "profit" in q -> listOf(HistoryMetric.NET_INCOME, HistoryMetric.NET_MARGIN)
                "revenue" in q || "sales" in q || "growth" in q -> listOf(HistoryMetric.REVENUE, HistoryMetric.REVENUE_GROWTH)
                "net income" in q -> listOf(HistoryMetric.NET_INCOME)
                else -> HistoryMetric.entries
            }
            val metrics = when (type) {
                ComparisonAiType.OVERVIEW -> ComparisonInterpretationEngine.GUIDED
                ComparisonAiType.HISTORY -> emptyList()
                ComparisonAiType.METRIC -> listOfNotNull(metricId?.takeIf { it in ComparisonInterpretationEngine.GUIDED })
                ComparisonAiType.RESEARCH -> research?.metrics.orEmpty()
                ComparisonAiType.QUESTION -> keywords.filter { (words, _) -> words.any { it in q } }.flatMap { it.second }.distinct()
                    .ifEmpty { if (wantsHistory) emptyList() else ComparisonInterpretationEngine.GUIDED }
            }
            return ComparisonAiFocus(metrics, wantsHistory, historyMetrics)
        }
    }
}

object ComparisonGrounding {
    /** Bumped when evidence ids, labels or definitions change meaning. */
    const val DEFINITION_VERSION = "compare-evidence-v1"
    const val MAX_EVIDENCE = 72
    const val MAX_HISTORY_POINTS = 40

    /** Stable, order-independent hash (FNV-1a 64) for context versions and cache keys; not a security hash. */
    fun hash(parts: List<String>): String {
        var h = -0x340d631b7bdddcdbL
        parts.joinToString("\u0001").encodeToByteArray().forEach { b -> h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L }
        return h.toULong().toString(16).padStart(16, '0')
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun period(b: FinancialBasis?, note: String?): String? {
        b ?: return note
        val month = b.date?.let { d -> MONTHS.getOrNull((d.drop(5).take(2).toIntOrNull() ?: 0) - 1)?.let { "$it ${d.take(4)}" } }
        return when (b.period.lowercase()) {
            "ttm" -> "Trailing twelve months"
            "annual", "fy" -> month?.let { "FY ended $it" } ?: b.fiscalYear?.let { "FY$it" }
            "quarter" -> note ?: month?.let { "Quarter ended $it" }
            "latest quote" -> "Latest quote"
            else -> note
        }
    }

    private fun unit(d: MetricDefinition?) = when (d?.unit) {
        MetricUnit.PERCENT -> "percent"; MetricUnit.MULTIPLE -> "multiple"; MetricUnit.MONEY -> "money"; MetricUnit.PRICE -> "price"
        MetricUnit.COUNT -> "count"; else -> "ratio"
    }

    /**
     * Builds the context for one request from data the server already validated. [source] names the
     * provider (e.g. "Financial Modeling Prep" or "Sample fixture data"); nothing here calls a provider.
     */
    fun build(comparison: ComparisonResponse, history: HistoricalComparison?, focus: ComparisonAiFocus, source: String,
              research: ResearchQuestion? = null): ComparisonAiContext {
        val companies = comparison.companies
        val loaded = companies.mapNotNull { it.record }
        val interpretation = ComparisonInterpretationEngine.interpret(companies, comparison.fx)
        val evidence = ArrayList<FinancialEvidence>()
        val missing = ArrayList<String>()
        // Comparison-wide limits first (currencies, fiscal calendars, staleness), then industry, history and per-metric caveats.
        val warnings = ArrayList<String>(comparison.notes.filterNot { it.startsWith("Trailing (TTM)") }.take(5))
        if (loaded.any { it.stale }) warnings += "Some financial statements are more than 18 months old, so values may be out of date."
        val metricWarnings = ArrayList<String>()
        val aiCompanies = companies.map { c ->
            val r = c.record
            AiCompany(c.symbol, r?.name ?: c.symbol, r?.exchange, r?.sector, r?.industry, r?.currency, c.error)
        }
        companies.forEach { c -> c.error?.let { missing += "${c.symbol}: $it" } }
        // Profiles: identity, business and listing (what makes a comparison fair or not).
        loaded.forEach { r ->
            val text = listOfNotNull(r.sector, r.industry).joinToString(" · ").ifBlank { null }
            if (text != null) evidence += FinancialEvidence("${r.symbol}.profile", EvidenceKind.PROFILE, "Sector and industry", text, r.symbol, r.name, source = source, retrievedAt = r.fundamentalsAsOf)
            val listing = listOfNotNull(r.exchange, r.country, r.currency).joinToString(" · ").ifBlank { null }
            if (listing != null) evidence += FinancialEvidence("${r.symbol}.listing", EvidenceKind.PROFILE, "Listing and currency", listing, r.symbol, r.name, currency = r.currency, source = source)
        }
        val labels = ComparisonInterpretationEngine.ROW_LABELS
        for (id in focus.metrics) {
            val d = ScreenerDefinitions.metric(id)
            val label = labels[id] ?: d?.label ?: id
            for (r in loaded) {
                if (id == "marketCap") {
                    val local = r.marketCap?.takeIf { it > 0 && it.isFinite() }
                    if (local != null) evidence += FinancialEvidence("${r.symbol}.marketCap", EvidenceKind.REPORTED, label, MetricFormatter.money(local, r.currency, true),
                        r.symbol, r.name, id, "money", r.currency, fiscalPeriod = "Latest quote; listing currency", source = source)
                    else missing += "${r.symbol} market cap: not available right now."
                    continue
                }
                val v = r.metrics[id]
                val cell = MetricFormatter.cell(d, v, r.currency)
                if (cell.available && v != null) {
                    val money = d?.unit == MetricUnit.MONEY
                    // Latest-quarter growth is StockSteps' own Earnings Results calculation; other values come with the
                    // company's fundamentals (the record doesn't say per value whether the provider or the backend derived a ratio).
                    evidence += FinancialEvidence("${r.symbol}.$id", if (id == "quarterRevenueGrowth") EvidenceKind.CALCULATED else EvidenceKind.REPORTED, label,
                        if (money && v.value != null) MetricFormatter.money(v.value, v.basis?.currency ?: r.currency, true) else cell.text,
                        r.symbol, r.name, id, unit(d), v.basis?.currency ?: r.currency.takeIf { money }, v.basis?.date, period(v.basis, v.note) ?: d?.period, source, r.fundamentalsAsOf)
                } else missing += "${r.symbol} ${label.lowercase()}: ${MetricFormatter.explanation(id, v).trimEnd('.')}."
            }
            // Phase 2's deterministic reading of the same values (comparability, observation, caveat).
            interpretation.metrics[id]?.let { g ->
                evidence += FinancialEvidence("I.$id", EvidenceKind.CALCULATED, "$label — ${g.comparability.label.lowercase()}", g.observation, metricId = id, unit = "text", source = "StockSteps guided interpretation")
                if (g.comparability != Comparability.COMPARABLE) g.caveats.firstOrNull()?.let { metricWarnings += "$label: $it" }
            }
            MetricEducation.find(id)?.let { e -> evidence += FinancialEvidence("L.$id", EvidenceKind.EDUCATION, e.label, e.explanation, metricId = id, unit = "text", source = "StockSteps Learn") }
        }
        interpretation.industryNote?.let { warnings += it }
        interpretation.summary.forEachIndexed { i, s -> if (s.metricId == null || s.metricId in focus.metrics) evidence += FinancialEvidence("I.summary.$i", EvidenceKind.CALCULATED, s.category.title, s.text, metricId = s.metricId, unit = "text", source = "StockSteps guided interpretation") }
        // History (Phase 3): points only when asked about; otherwise just its observations.
        if (history != null && focus.history) {
            var points = 0
            val explicit = history.companies.mapNotNull { it.currency }.distinct().size > 1
            for (metric in focus.historyMetrics) {
                val data = history.metric(metric) ?: continue
                for (s in data.series) for (p in s.points) {
                    if (points >= MAX_HISTORY_POINTS) break
                    val value = p.value
                    if (value != null && p.availability == FinancialAvailability.AVAILABLE) {
                        points++
                        evidence += FinancialEvidence("${s.symbol}.h.${metric.name.lowercase()}.${p.label.replace(' ', '-')}", EvidenceKind.REPORTED.takeIf { metric.unit == HistoryUnit.MONEY || metric.unit == HistoryUnit.PER_SHARE } ?: EvidenceKind.CALCULATED,
                            metric.label, HistoricalComparisonEngine.format(metric, value, p.currency, explicit), s.symbol, s.name, metric.name, metric.unit.name.lowercase(), p.currency, p.periodEnd,
                            p.label + (if (history.granularity == HistoryGranularity.ANNUAL) " (fiscal year)" else " (fiscal quarter)"), history.source,
                            history.companies.firstOrNull { it.symbol == s.symbol }?.retrievedAt)
                    } else missing += "${s.symbol} ${metric.label.lowercase()} ${p.label}: ${(p.note ?: MetricFormatter.reason(p.availability)).trimEnd('.')}."
                }
                data.insights.take(2).forEachIndexed { i, text -> evidence += FinancialEvidence("H.${metric.name.lowercase()}.$i", EvidenceKind.CALCULATED, "${metric.label} history (${history.range.label})", text, metricId = metric.name, unit = "text", source = "StockSteps historical comparison") }
                evidence += FinancialEvidence("L.h.${metric.name.lowercase()}", EvidenceKind.EDUCATION, metric.label, metric.definition, metricId = metric.name, unit = "text", source = "StockSteps Learn")
            }
            history.insights.take(3).forEach { warnings += it }
            warnings += "${history.range.description}; ${if (history.granularity == HistoryGranularity.ANNUAL) "fiscal years" else "fiscal quarters"} can end in different months for each company."
        } else if (history != null) history.insights.take(2).forEachIndexed { i, text -> evidence += FinancialEvidence("H.summary.$i", EvidenceKind.CALCULATED, "History (${history.range.label})", text, unit = "text", source = "StockSteps historical comparison") }
        warnings += metricWarnings
        val bounded = evidence.distinctBy { it.id }.take(MAX_EVIDENCE)
        val dataAsOf = loaded.mapNotNull { it.fundamentalsAsOf }.maxOrNull() ?: comparison.asOf
        val version = hash(listOf(DEFINITION_VERSION) + companies.map { it.symbol } + bounded.map { "${it.id}=${it.value}|${it.fiscalPeriod}" } + missing + warnings + listOf(history?.range?.label.orEmpty()))
        val dataVersion = hash(listOf(DEFINITION_VERSION) + companies.flatMap { c -> listOf(c.symbol, c.error.orEmpty(), c.record?.fundamentalsAsOf.orEmpty(), c.record?.stale.toString()) +
            c.record?.metrics.orEmpty().entries.sortedBy { it.key }.map { (k, v) -> "$k=${v.value}|${v.availability}|${v.basis?.date}" } })
        return ComparisonAiContext(companies.map { it.symbol }, aiCompanies, bounded, missing.distinct().take(16), warnings.distinct().take(12), dataAsOf,
            history?.range, version, dataVersion, comparison.sampleData, researchQuestion = research)
    }
}

// ---------- Validation ----------

/**
 * Checks a draft against its context. Unverifiable observations, caveats and research questions are
 * removed (and counted); an invalid summary or nothing verifiable rejects the whole draft. Rules: cited
 * evidence must exist in the context; every number must come from the cited evidence (observations) or
 * the context (everything else); no advice, rankings, predictions, unhedged causes, links/HTML or
 * instruction/secret leakage. The user's question and note are untrusted: their numbers never count.
 */
object ComparisonAiValidator {
    class Rejected(val reason: String) : Exception(reason)
    data class Result(val draft: ComparisonAiDraft, val removed: Int, val reasons: List<String>)

    private val url = Regex("(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|org|net|io|ca)\\b)")
    private val html = Regex("<\\s*/?\\s*[a-zA-Z][^>]*>|\\[[^\\]]+]\\([^)]+\\)")
    /** Always rejected (unless the sentence is StockSteps' own verified text). */
    private val imperative = Regex("(?i)\\b(you should (buy|sell|hold|invest|avoid|short|consider buying|consider selling)|we recommend|i recommend|i'd recommend|consider (buying|selling)|(buy|sell|hold|avoid|short) (the|this|these|its|their) (stock|shares)|good time to (buy|sell|invest)|strong buy|must[- ]own|price target|worth buying|buy now|sell now|top pick)\\b")
    /** Rankings and judgements: allowed only when negated ("isn't proof it's undervalued") or verbatim from verified text. */
    private val judgement = Regex("(?i)\\b(is (a|the) (better|best|smarter|safer|stronger) (investment|buy|stock|pick|choice)|(better|best|smarter|safer) (investment|stock pick)|clear winner|the winner|outperform(s|ed)?|undervalued|overvalued|bargain|guaranteed|risk[- ]free|sure thing|can't lose)\\b")
    private val prediction = Regex("(?i)\\b(will|is going to|are going to|is (likely|expected) to|are (likely|expected) to) (rise|fall|grow|decline|increase|decrease|soar|plunge|go up|go down|double|recover|beat|miss|outperform|underperform|continue to (grow|rise|fall|decline))\\b|\\bpredict(s|ed|ion)?\\b|\\bforecast(s)? (that|the price|the stock)\\b")
    private val negation = Regex("(?i)(\\b(not|no|never|without|cannot)\\b|n't\\b)")
    private val causal = Regex("(?i)\\b(because|due to|driven by|as a result of|caused by|thanks to|on the back of|the reason (is|was))\\b")
    private val hedge = Regex("(?i)\\b(may|might|could|possibl[ey]|can|one (possible )?(reason|explanation)|to investigate|worth (checking|investigating|researching)|doesn't (establish|show|explain)|does not (establish|show|explain)|isn't (in|shown|available)|not (in|shown by) the (data|figures)|unknown)\\b")
    private val leak = Regex("(?i)(system prompt|system instruction|api[ _-]?key|secret|password|ignore (all |any |the )?(previous|prior|above) (instructions|rules)|these rules|developer message|bearer )")
    private val number = Regex("\\d+(?:[.,]\\d+)*")
    private val smallCounts = (0..12).map { it.toString() }.toSet()

    private fun norm(n: String): String {
        var s = n.replace(",", "")
        if ('.' in s) s = s.trimEnd('0').trimEnd('.')
        s = s.trimStart('0')
        return if (s.isEmpty() || s.startsWith(".")) "0$s" else s
    }

    /** A verified number and its display roundings (0–2 places): output numbers must match one exactly. */
    private fun variants(n: String): Set<String> {
        val clean = n.replace(",", "")
        val d = clean.toDoubleOrNull() ?: return setOf(norm(clean))
        return buildSet {
            add(norm(clean))
            add(norm(round(d).toLong().toString()))
            add(norm(MetricFormatter.decimals(abs(d), 1))); add(norm(MetricFormatter.decimals(abs(d), 2)))
        }
    }
    fun numbers(text: String): Set<String> = number.findAll(text).flatMap { variants(it.value) }.toSet()
    private fun allowed(text: String) = numbers(text) + smallCounts

    private fun sentences(text: String) = text.split(Regex("[.!?]\\s+")).filter { it.isNotBlank() }

    /**
     * Throws [Rejected] for unsafe text. [numbersFrom] is the verified text its numbers must come from; a sentence
     * found verbatim in [verified] (StockSteps' own caveats and explanations) is exempt from the phrase checks.
     */
    fun check(text: String, numbersFrom: Set<String>, field: String, verified: String = "") {
        if (text.isBlank()) throw Rejected("$field: empty")
        if (url.containsMatchIn(text) || html.containsMatchIn(text)) throw Rejected("$field: link or markup")
        if (leak.containsMatchIn(text)) throw Rejected("$field: instruction or secret leakage")
        sentences(text).forEach { raw ->
            val s = raw.trim()
            if (s.length >= 25 && verified.contains(s.trimEnd('.', '!', '?'), ignoreCase = true)) return@forEach
            if (imperative.containsMatchIn(s)) throw Rejected("$field: advice")
            if (judgement.containsMatchIn(s) && !negation.containsMatchIn(s)) throw Rejected("$field: ranking or judgement")
            if (prediction.containsMatchIn(s) && !negation.containsMatchIn(s)) throw Rejected("$field: prediction")
            if (causal.containsMatchIn(s) && !hedge.containsMatchIn(s)) throw Rejected("$field: unsupported cause")
        }
        number.findAll(text).forEach { m -> if (norm(m.value) !in numbersFrom) throw Rejected("$field: number ${m.value} isn't in the verified data") }
    }

    fun validate(draft: ComparisonAiDraft, context: ComparisonAiContext): Result {
        val verified = context.text()
        val all = allowed(verified)
        val reasons = ArrayList<String>()
        if (draft.summary.trim().length !in 20..900) throw Rejected("summary: length")
        draft.summaryEvidenceIds.firstOrNull { it !in context.ids }?.let { throw Rejected("summary: unknown evidence $it") }
        check(draft.summary, all, "summary", verified)
        if (draft.scope !in setOf("answered", "insufficient")) throw Rejected("scope")
        if (draft.observations.size > 6 || draft.caveats.size > 6 || draft.researchQuestions.size > 5) throw Rejected("lists: size")
        val observations = draft.observations.filter { o ->
            try {
                if (o.text.length > 500 || (o.whyItMatters?.length ?: 0) > 400) throw Rejected("observation: length")
                if (o.evidenceIds.isEmpty()) throw Rejected("observation: no evidence")
                o.evidenceIds.firstOrNull { it !in context.ids }?.let { throw Rejected("observation: unknown evidence $it") }
                val cited = o.evidenceIds.mapNotNull(context::evidence)
                if (cited.all { it.kind == EvidenceKind.EDUCATION } && number.containsMatchIn(o.text)) throw Rejected("observation: numbers cite only education")
                // Numbers in an observation must come from the evidence it cites (plus its periods and labels).
                val citedText = cited.flatMap { listOfNotNull(it.value, it.label, it.fiscalPeriod, it.periodEnd) }.joinToString(" ")
                check(o.text, allowed(citedText), "observation", verified)
                o.whyItMatters?.let { check(it, all, "whyItMatters", verified) }
                true
            } catch (cause: Rejected) { reasons += cause.reason; false }
        }
        val caveats = draft.caveats.filter { c -> try { if (c.length > 400) throw Rejected("caveat: length"); check(c, all, "caveat", verified); true } catch (cause: Rejected) { reasons += cause.reason; false } }
        val questions = draft.researchQuestions.filter { q -> try { if (q.length > 240) throw Rejected("question: length"); check(q, all, "research question", verified); true } catch (cause: Rejected) { reasons += cause.reason; false } }
        if (draft.scope == "answered" && draft.observations.isNotEmpty() && observations.isEmpty()) throw Rejected("no verifiable observations (${reasons.firstOrNull()})")
        val words = (listOf(draft.summary) + observations.flatMap { listOfNotNull(it.text, it.whyItMatters) } + caveats + questions).sumOf { t -> t.split(Regex("\\s+")).count { it.isNotBlank() } }
        if (words > 650) throw Rejected("too long ($words words)")
        val cleaned = draft.copy(summary = draft.summary.trim(), observations = observations.map { it.copy(text = it.text.trim(), whyItMatters = it.whyItMatters?.trim(), evidenceIds = it.evidenceIds.distinct()) },
            caveats = caveats.map { it.trim() }, researchQuestions = questions.map { it.trim() })
        return Result(cleaned, reasons.size, reasons)
    }
}

/**
 * Questions answered without any AI call (never charged): prompt-injection attempts and requests for
 * advice, picks or predictions. Advice gets a grounded trade-offs answer instead of a flat refusal.
 */
object ComparisonQuestionScreen {
    private val injection = Regex("(?i)(ignore (all |any |the |your )?(previous|prior|above|earlier) (instructions|rules|prompt)|system prompt|developer (mode|message)|you are now|pretend (to be|you are)|act as|jailbreak|reveal (your|the) (prompt|instructions|rules)|api[ _-]?key|password|other users?'?s? (notes|data|research)|execute (a )?trade|place (an? )?order)")
    private val advice = Regex("(?i)\\b(should i (buy|sell|hold|invest|short)|which (one|stock|company|share|of them|of these)( should i| to| would you)? (buy|pick|choose|invest)|(better|best|smarter|safer) (investment|buy|stock|pick)|is it (a )?(good|bad|smart) (time|idea) to (buy|sell|invest)|(buy|sell) or (hold|sell|buy)|price target|will (it|the stock|the price|shares|they|either|.{1,20} stock) (go|rise|fall|drop|climb|beat|miss|outperform|grow)|predict|how much will|worth buying|good investment|make (me )?money|guarantee)\\b")
    enum class Kind { INJECTION, ADVICE }

    fun classify(question: String): Kind? = when {
        injection.containsMatchIn(question) -> Kind.INJECTION
        advice.containsMatchIn(question) -> Kind.ADVICE
        else -> null
    }

    const val INJECTION_ANSWER = "I can only help you understand the financial differences between the selected companies, using StockSteps' verified data."

    /** "Which should I buy?": trade-offs from the verified, deterministic observations; no pick, no prediction. */
    fun adviceAnswer(context: ComparisonAiContext): Pair<String, List<FinancialEvidence>> {
        val tradeOffs = context.evidence.filter { it.id.startsWith("I.summary.") || (it.id.startsWith("I.") && it.metricId in setOf("pe", "netMargin", "revenueGrowth", "debtEquity")) }.take(4)
        return "StockSteps can't choose a stock for you or say which will do better. What the verified data can show is how these companies differ" +
            (if (tradeOffs.isEmpty()) "; open the metrics above to see each one." else " — the trade-offs are listed below.") +
            " Which matters more depends on your own goals, time horizon and comfort with risk; many investors spread money across several companies." to tradeOffs
    }
}

/** Suggested questions for the selected companies (deterministic; never a ranking). */
data class SuggestedAiQuestion(val text: String, val type: ComparisonAiType, val metricId: String? = null)

object ComparisonAiSuggestions {
    fun forComparison(state: ComparisonUiState?): List<SuggestedAiQuestion> {
        val base = mutableListOf(
            SuggestedAiQuestion("Explain the biggest differences", ComparisonAiType.OVERVIEW),
            SuggestedAiQuestion("Compare their revenue growth", ComparisonAiType.QUESTION),
            SuggestedAiQuestion("Why are their P/E ratios different?", ComparisonAiType.METRIC, "pe"),
            SuggestedAiQuestion("Compare their profitability", ComparisonAiType.QUESTION),
            SuggestedAiQuestion("What does their financial history tell us?", ComparisonAiType.HISTORY),
            SuggestedAiQuestion("What should I research next?", ComparisonAiType.QUESTION)
        )
        state ?: return base
        val pe = state.guide("pe")
        if (pe != null && pe.comparability == Comparability.INSUFFICIENT_DATA) base[2] = SuggestedAiQuestion("Why isn't a P/E shown for every company?", ComparisonAiType.METRIC, "pe")
        if (state.industryNote != null) base.add(1, SuggestedAiQuestion("How do their business models affect comparability?", ComparisonAiType.QUESTION))
        if (state.guide("debtEquity")?.comparability == Comparability.NOT_COMPARABLE) base.add(SuggestedAiQuestion("Why isn't debt to equity comparable here?", ComparisonAiType.METRIC, "debtEquity"))
        return base.distinctBy { it.text }.take(7)
    }
}
