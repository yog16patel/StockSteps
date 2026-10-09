package org.example.stocksteps.screener

import kotlinx.serialization.Serializable
import org.example.stocksteps.companydetail.MetricEducation
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/*
 * Company Comparison Phase 2 (free): guided metric interpretation. Deterministic, shared by Android
 * and iOS, computed from the existing `/api/v1/compare` response (no AI, no extra provider calls).
 * It explains what a metric measures, what the selected companies' values show, what they don't show
 * and what to look at next. It never ranks companies, names a winner, uses universal "good/bad"
 * thresholds or industry averages, or claims why a difference exists.
 */

/** How far one metric's values can be compared across the selected companies. */
enum class Comparability(val label: String) {
    COMPARABLE("Comparable"),
    COMPARABLE_WITH_CAVEATS("Compare with care"),
    NOT_COMPARABLE("Not directly comparable"),
    INSUFFICIENT_DATA("Not enough data")
}

/** Broad business model, from the provider's sector/industry (never a benchmark). */
enum class IndustryKind { BANK, INSURER, OTHER_FINANCIAL, REIT, UTILITY, GENERAL, UNKNOWN }

/** A metric worth looking at next; [guided] ones have their own explanation, others an info sheet. */
data class RelatedMetric(val id: String, val label: String, val guided: Boolean)

/**
 * One metric's guided explanation (sections A–E). [observation] always describes the actual values
 * (or why there's nothing to compare); [headline] is the short form used in the learning summary and
 * is null when the values can't support a statement (not comparable, missing, equal or very close).
 */
data class MetricInterpretation(
    val metricId: String,
    val label: String,
    val title: String,
    /** A. What is this metric? */
    val definition: String,
    /** B. Why does it matter? */
    val whyItMatters: String,
    val comparability: Comparability,
    /** C. What do these numbers suggest? (traceable to the displayed values) */
    val observation: String,
    /** C (continued): what the difference means, carefully qualified. */
    val meaning: String?,
    /** D. What should I be careful about? Data-specific caveats first, then general ones. */
    val caveats: List<String>,
    /** Deeper education shown after "Learn more". */
    val learnMore: List<String>,
    val calculation: String?,
    /** E. What should I investigate next? */
    val related: List<RelatedMetric>,
    val questions: List<String>,
    val headline: String? = null
) {
    /** The caveats shown before "Learn more" (concise by default). */
    val keyCaveats: List<String> get() = caveats.take(2)
    val moreCaveats: List<String> get() = caveats.drop(2)
    val accessibility: String get() = "$title. ${comparability.label}. $observation" + (meaning?.let { " $it" } ?: "")
}

enum class InsightCategory(val title: String) {
    GROWTH("Growth"), PROFITABILITY("Profitability"), VALUATION("Valuation"), FINANCIAL_HEALTH("Financial health"),
    INDUSTRY("Industry context"), DATA("Data")
}

/** One grounded line for "What can we learn from this comparison?" (never a ranking). */
data class ComparisonInsight(val category: InsightCategory, val metricId: String?, val text: String)

data class ComparisonInterpretation(
    val metrics: Map<String, MetricInterpretation>,
    val summary: List<ComparisonInsight>,
    /** Cross-industry context when the companies' sectors differ (null otherwise). */
    val industryNote: String?
) {
    companion object { val EMPTY = ComparisonInterpretation(emptyMap(), emptyList(), null) }
}

/** A currency conversion the server applied (e.g. market cap to USD), with its date and source. */
@Serializable
data class FxConversion(val from: String, val to: String, val rate: Double, val date: String? = null, val source: String)

object ComparisonInterpretationEngine {
    /** Metrics with a guided explanation: the beginner rows of Phase 1. */
    val GUIDED = listOf("marketCap", "quarterRevenueGrowth", "revenueGrowth", "netMargin", "debtEquity", "pe", "priceSales", "dividendYield")
    /** Row labels shared with `ComparisonPresenter`, so explanations name metrics exactly as the table does. */
    val ROW_LABELS = mapOf(
        "marketCap" to "Market cap", "quarterRevenueGrowth" to "Revenue growth — latest quarter", "revenueGrowth" to "Revenue growth — fiscal year",
        "netMargin" to "Net profit margin", "debtEquity" to "Debt to equity", "pe" to "P/E ratio (trailing)", "priceSales" to "Price to sales",
        "dividendYield" to "Dividend yield"
    )
    const val SUMMARY_LIMIT = 3
    /**
     * Two percentages closer than this (percentage points) are described as "similar". This is about
     * what a difference can tell a beginner at display precision, not a judgement of either value.
     */
    const val CLOSE_PERCENT_POINTS = 0.5
    /** Two ratios or amounts within this relative gap are described as "similar". */
    const val CLOSE_RELATIVE = 0.05
    /** Percentages must also be within this relative gap to be "similar". */
    const val CLOSE_PERCENT_RELATIVE = 0.10
    /** Metrics whose typical values depend on the business model (cross-industry caveats apply). */
    private val SECTOR_SENSITIVE = setOf("quarterRevenueGrowth", "revenueGrowth", "netMargin", "debtEquity", "pe", "priceSales", "dividendYield")

    fun label(id: String): String = ROW_LABELS[id] ?: ScreenerDefinitions.metric(id)?.label ?: id

    fun kind(record: CompanyRecord): IndustryKind {
        val sector = record.sector ?: return IndustryKind.UNKNOWN
        val industry = record.industry.orEmpty()
        return when {
            sector == "Real Estate" || industry.contains("REIT", ignoreCase = true) -> IndustryKind.REIT
            sector == "Financial Services" && industry.contains("Bank", ignoreCase = true) -> IndustryKind.BANK
            sector == "Financial Services" && industry.contains("Insurance", ignoreCase = true) -> IndustryKind.INSURER
            sector == "Financial Services" -> IndustryKind.OTHER_FINANCIAL
            sector == "Utilities" -> IndustryKind.UTILITY
            else -> IndustryKind.GENERAL
        }
    }

    fun interpret(companies: List<ComparedCompany>, fx: List<FxConversion> = emptyList()): ComparisonInterpretation {
        if (companies.size < 2) return ComparisonInterpretation.EMPTY
        val ctx = Context(companies, fx)
        val metrics = GUIDED.associateWith { interpretMetric(it, ctx) }
        val industry = industryInsight(ctx)
        val summary = mutableListOf<ComparisonInsight>()
        if (ctx.failed.isNotEmpty()) summary += ComparisonInsight(InsightCategory.DATA, null,
            "Data for ${join(ctx.failed.map { it.symbol })} isn't available right now, so ${if (ctx.failed.size == 1) "it's" else "they're"} left out of these explanations.")
        industry?.let { summary += ComparisonInsight(InsightCategory.INDUSTRY, null, it) }
        val candidates = listOf(InsightCategory.GROWTH to listOf("quarterRevenueGrowth", "revenueGrowth"), InsightCategory.PROFITABILITY to listOf("netMargin"),
            InsightCategory.VALUATION to listOf("pe"), InsightCategory.FINANCIAL_HEALTH to listOf("debtEquity"))
        for ((category, ids) in candidates) {
            if (summary.size >= SUMMARY_LIMIT) break
            ids.firstNotNullOfOrNull { id -> metrics.getValue(id).headline?.let { ComparisonInsight(category, id, it) } }?.let(summary::add)
        }
        if (summary.none { it.metricId != null }) summary += ComparisonInsight(InsightCategory.DATA, null,
            "There isn't enough comparable data here for a clear observation yet. Missing values are never filled in or estimated; open Explain on a metric to see why.")
        return ComparisonInterpretation(metrics, summary.take(SUMMARY_LIMIT).map { it.copy(text = tidy(it.text)) }, if (ctx.crossSector) industry else null)
    }

    // ---------- Context ----------

    private class Context(val companies: List<ComparedCompany>, val fx: List<FxConversion>) {
        val loaded = companies.mapNotNull { it.record }
        val failed = companies.filter { it.record == null }
        val crossSector = loaded.size >= 2 && loaded.all { it.sector != null } && loaded.map { it.sector }.distinct().size > 1
        val unknownSector = loaded.filter { it.sector == null }
    }

    private class Reading(val record: CompanyRecord, override val value: Double, override val text: String, val basis: FinancialBasis?, val noDividend: Boolean = false) : MetricGuides.ReadingView {
        override val name: String get() = record.name
    }
    private class Excluded(val name: String, val reason: String, val notMeaningful: Boolean = false, val industry: Boolean = false)

    private enum class PeriodKind { TTM, FISCAL_YEAR, QUARTER, QUOTE, UNKNOWN }
    private fun periodKind(basis: FinancialBasis?): PeriodKind = when (basis?.period?.lowercase()) {
        "ttm" -> PeriodKind.TTM
        "annual", "fy" -> PeriodKind.FISCAL_YEAR
        "quarter" -> PeriodKind.QUARTER
        "latest quote" -> PeriodKind.QUOTE
        else -> PeriodKind.UNKNOWN
    }

    // ---------- Industry ----------

    private fun industryInsight(ctx: Context): String? {
        val loaded = ctx.loaded
        if (loaded.size < 2) return null
        if (ctx.unknownSector.isNotEmpty()) return "Sector isn't reported for ${join(ctx.unknownSector.map { it.name })}, so industry differences can't be checked. Keep in mind that business models affect margins, debt and valuations."
        val sectors = loaded.map { it.sector!! }.distinct()
        val industries = loaded.mapNotNull { it.industry }.distinct()
        return when {
            sectors.size > 1 -> "These companies operate in different sectors (${join(sectors)}). Their margins, debt structures and typical valuations can differ for reasons related to their business models."
            industries.size == 1 && loaded.all { it.industry != null } ->
                "${if (loaded.size == 2) "Both companies are" else "All ${loaded.size} companies are"} in the same industry (${industries.single()}), which usually makes their ratios easier to compare. Each business can still differ."
            industries.size > 1 -> "These companies share a sector (${sectors.single()}) but work in different industries (${join(industries)}), so their business models can still differ."
            else -> null
        }
    }

    /** Why a company's value is left out for its industry (debt to equity for banks and insurers). */
    private fun industryExclusion(id: String, kind: IndustryKind): String = when {
        id == "debtEquity" && (kind == IndustryKind.BANK || kind == IndustryKind.INSURER) ->
            "Not compared: banks and insurers borrow and lend as their core business (customer deposits count as liabilities), so debt to equity isn't comparable with other companies."
        id == "debtEquity" -> "Not compared: financial companies' balance sheets work differently, so debt to equity isn't comparable with other companies."
        else -> "Not comparable for this type of company."
    }

    /** Business-model notes for one metric (qualitative only; no invented benchmarks). */
    private fun industryNotes(id: String, readings: List<CompanyRecord>, ctx: Context): List<String> {
        val byKind = readings.groupBy { kind(it) }
        fun names(k: IndustryKind) = join(byKind[k].orEmpty().map { it.name })
        val mixed = ctx.crossSector
        return buildList {
            if (IndustryKind.REIT in byKind) when (id) {
                "pe" -> add("${names(IndustryKind.REIT)} is a REIT. REITs are often assessed using funds from operations (FFO) rather than earnings, because large depreciation charges lower reported earnings. FFO isn't shown here.")
                "dividendYield" -> add("${names(IndustryKind.REIT)} is a REIT. REITs generally must pay out most of their taxable income, so higher yields are common by design.")
                "debtEquity" -> add("${names(IndustryKind.REIT)} is a REIT. REITs typically borrow substantially to buy property.")
            }
            if (IndustryKind.UTILITY in byKind) when (id) {
                "debtEquity" -> add("${names(IndustryKind.UTILITY)} is a utility. Utilities commonly carry more debt to fund long-lived infrastructure with steadier, often regulated income.")
                "dividendYield" -> add("${names(IndustryKind.UTILITY)} is a utility. Utilities often pay regular dividends from steady income.")
            }
            val banks = byKind[IndustryKind.BANK].orEmpty() + byKind[IndustryKind.INSURER].orEmpty()
            if (banks.isNotEmpty() && mixed && (id == "netMargin" || id == "priceSales"))
                add("${join(banks.map { it.name })} ${if (banks.size == 1) "is a bank or insurer" else "are banks or insurers"}: revenue is measured differently (largely interest and premiums), so ${if (id == "netMargin") "margins" else "price-to-sales ratios"} need extra care across industries.")
            if (banks.isNotEmpty() && id == "pe" && mixed)
                add("Bank earnings depend on interest rates and loan losses, so P/E levels for banks and other companies often differ for business-model reasons.")
        }
    }

    // ---------- Per metric ----------

    private fun interpretMetric(id: String, ctx: Context): MetricInterpretation {
        val guide = MetricGuides.of(id)
        val definition = ScreenerDefinitions.metric(id)
        val excluded = ctx.failed.map { Excluded(it.symbol, it.error ?: "Company data isn't available right now.") }.toMutableList()
        if (id == "marketCap") return marketCap(guide, ctx, excluded)
        val readings = mutableListOf<Reading>()
        for (r in ctx.loaded) {
            val v = r.metrics[id]
            val notApplicable = r.sector != null && definition != null && r.sector in definition.notApplicableSectors
            when {
                notApplicable -> excluded += Excluded(r.name, industryExclusion(id, kind(r)), industry = true)
                v?.availability == FinancialAvailability.NO_DIVIDEND -> readings += Reading(r, 0.0, "None", v.basis, noDividend = true)
                v != null && v.availability == FinancialAvailability.AVAILABLE && v.value?.isFinite() == true ->
                    readings += Reading(r, v.value, MetricFormatter.cell(definition, v, r.currency).text, v.basis)
                else -> excluded += Excluded(r.name, MetricFormatter.explanation(id, v), v?.availability == FinancialAvailability.NON_POSITIVE_DENOMINATOR)
            }
        }
        val caveats = mutableListOf<String>()
        val learnMore = guide.learnMore.toMutableList()
        val questions = mutableListOf<String>()
        var comparability = Comparability.COMPARABLE
        fun care() { if (comparability == Comparability.COMPARABLE) comparability = Comparability.COMPARABLE_WITH_CAVEATS }

        excluded.forEach { caveats += "${it.name}: ${it.reason}" }
        if (id == "pe" && excluded.any { it.notMeaningful }) questions += "Compare price to sales and revenue growth, which can still be compared when a company reports a loss."
        if (id == "debtEquity" && excluded.any { it.notMeaningful }) questions += "Look at cash, total debt and interest coverage (under More metrics) when debt to equity isn't meaningful."

        var observation: String
        var meaning: String? = null
        var headline: String? = null
        val industryOnly = excluded.filter { it.industry }
        if (readings.size < 2 && industryOnly.isNotEmpty() && readings.size + industryOnly.size >= 2) {
            comparability = Comparability.NOT_COMPARABLE
            observation = "The ${guide.noun} isn't compared here because ${join(industryOnly.map { it.name })} ${if (industryOnly.size == 1) "is a financial company" else "are financial companies"} whose balance sheets work differently" +
                (readings.singleOrNull()?.let { " (${it.name}: ${it.text})." } ?: ".")
        } else if (readings.size < 2) {
            comparability = Comparability.INSUFFICIENT_DATA
            observation = when (readings.size) {
                1 -> "Only ${readings[0].name} has a value for ${guide.noun} (${readings[0].text}), so there's nothing to compare it with here."
                else -> "None of these companies has a value for ${guide.noun} here."
            }
        } else {
            val kinds = readings.map { periodKind(it.basis) }
            val known = kinds.filter { it != PeriodKind.UNKNOWN }.distinct()
            if (known.size > 1) {
                comparability = Comparability.NOT_COMPARABLE
                observation = "These values cover different kinds of periods (" + readings.joinToString("; ") { "${it.name}: ${it.text}, ${periodLabel(it.basis) ?: "period not reported"}" } +
                    "), so they aren't compared directly."
            } else {
                // Period metadata: missing, different months, or clearly different periods.
                val unknown = readings.filter { periodKind(it.basis) == PeriodKind.UNKNOWN }
                if (unknown.isNotEmpty() && known.isNotEmpty()) { caveats += "The reporting period for ${join(unknown.map { it.name })} isn't reported, so it can't be confirmed that the values cover the same time."; care() }
                if (known.singleOrNull() == PeriodKind.FISCAL_YEAR || known.singleOrNull() == PeriodKind.QUARTER) {
                    val dated = readings.filter { it.basis?.date != null }
                    val undated = readings - dated.toSet()
                    if (undated.isNotEmpty()) { caveats += "The period end date isn't reported for ${join(undated.map { it.name })}."; care() }
                    val days = dated.mapNotNull { r -> MarketsPresenter.dayNumber(r.basis!!.date!!.take(10))?.let { r to it } }
                    if (days.size >= 2) {
                        val spread = days.maxOf { it.second } - days.minOf { it.second }
                        val months = dated.map { it.basis!!.date!!.take(7) }.distinct()
                        val list = dated.joinToString("; ") { "${it.name}: ${periodLabel(it.basis) ?: it.basis!!.date}" }
                        when {
                            spread > ComparisonEngine.PERIOD_TOLERANCE_DAYS -> { caveats += "Reporting periods end on clearly different dates ($list), so these values describe different stretches of time."; care() }
                            months.size > 1 -> { caveats += "Reporting periods end in different months ($list), so they overlap but don't match exactly."; care() }
                        }
                    }
                }
                val stale = readings.filter { it.record.stale }
                if (stale.isNotEmpty()) { caveats += "${join(stale.map { it.name })}'s latest financial statements are more than 18 months old, so ${if (stale.size == 1) "its value" else "their values"} may be out of date."; care() }
                if (id in SECTOR_SENSITIVE) {
                    val sectors = readings.mapNotNull { it.record.sector }.distinct()
                    if (ctx.crossSector && sectors.size > 1) { caveats += "These companies are in different sectors (${join(sectors)}), so differences in ${guide.noun} can reflect different business models."; care() }
                    if (ctx.unknownSector.any { u -> readings.any { it.record.symbol == u.symbol } }) { caveats += "Sector isn't reported for ${join(ctx.unknownSector.map { it.name })}, so industry differences can't be checked."; care() }
                }
                if (excluded.isNotEmpty()) care()
                val currencies = readings.mapNotNull { it.basis?.currency ?: it.record.currency }.distinct()
                if (currencies.size > 1) {
                    if (id == "revenueGrowth" || id == "quarterRevenueGrowth") { caveats += "These companies report in different currencies (${join(currencies)}). Growth rates are percentages, so they can be compared, but exchange-rate moves can affect reported growth."; care() }
                    else learnMore += "Ratios and percentages don't depend on currency, so they can be compared across ${join(currencies)} even though the amounts behind them can't."
                }
                val statement = statement(id, guide, readings, definition?.unit ?: MetricUnit.RATIO, comparability)
                observation = statement.observation
                meaning = statement.meaning
                headline = statement.headline
            }
        }
        caveats += industryNotes(id, readings.map { it.record } + excluded.mapNotNull { e -> ctx.loaded.firstOrNull { it.name == e.name } }.filter { kind(it) != IndustryKind.GENERAL }, ctx)
            .filterNot { note -> caveats.any { it.contains(note) } }
        caveats += guide.cautions
        questions += guide.questions
        return MetricInterpretation(id, label(id), "Understanding ${guide.short}", guide.definition, guide.why, comparability, tidy(observation), meaning?.let(::tidy),
            caveats.map(::tidy).distinct(), learnMore.distinct(), MetricEducation.find(id)?.calculation,
            guide.related.map { RelatedMetric(it, label(it), it in GUIDED) }, questions.distinct(), headline?.let(::tidy))
    }

    private class Statement(val observation: String, val meaning: String?, val headline: String?)

    /** Percentages must be close in points and relative terms (0.3% vs 0.7% isn't "similar"). */
    private fun close(high: Reading, low: Reading, unit: MetricUnit): Boolean {
        val gap = abs(high.value - low.value)
        val size = max(abs(high.value), abs(low.value))
        return when (unit) {
            MetricUnit.PERCENT -> gap < CLOSE_PERCENT_POINTS && gap <= CLOSE_PERCENT_RELATIVE * size
            else -> gap <= CLOSE_RELATIVE * size
        }
    }

    /** [readings] are in selection order; lists keep that order so they never read as a ranking. */
    private fun statement(id: String, guide: MetricGuides.Guide, readings: List<Reading>, unit: MetricUnit, comparability: Comparability): Statement {
        val sorted = readings.sortedWith(compareByDescending<Reading> { it.value }.thenBy { it.record.symbol })
        val care = if (comparability == Comparability.COMPARABLE_WITH_CAVEATS) " Compare with care (see Explain)." else ""
        if (id == "dividendYield") dividends(sorted)?.let { return it }
        val high = sorted.first(); val low = sorted.last()
        val losses = if (id == "netMargin") sorted.filter { it.value < 0 } else emptyList()
        val lossNote = if (losses.isEmpty()) null else "${join(losses.map { "${it.name} (${it.text})" })} reported a net loss for the period. A loss in one period doesn't mean a company will stay unprofitable."
        if (sorted.all { it.text == high.text }) {
            val who = if (sorted.size == 2) "${high.name} and ${low.name} have" else "All ${sorted.size} companies have"
            return Statement("$who the same ${guide.noun} as displayed (${high.text}).", listOfNotNull("Matching numbers don't mean the businesses are alike.", lossNote).joinToString(" "), null)
        }
        if (sorted.size == 2) {
            if (close(high, low, unit)) return Statement("${high.name} (${high.text}) and ${low.name} (${low.text}) have a similar ${guide.noun}.",
                listOfNotNull("A difference this small says little on its own.", lossNote).joinToString(" "), null)
            val observation = "${high.name} has ${guide.higher} than ${low.name} (${high.text} vs ${low.text})."
            val meaning = listOfNotNull(guide.pairMeaning(high, low), lossNote).joinToString(" ")
            return Statement(observation, meaning, guide.pairHeadline(high, low) + care)
        }
        val list = readings.joinToString(", ") { "${it.name} ${it.text}" }
        if (close(high, low, unit)) return Statement("All ${sorted.size} companies have a similar ${guide.noun} ($list).",
            listOfNotNull("Differences this small say little on their own.", lossNote).joinToString(" "), null)
        val observation = "Among these ${sorted.size} companies, ${guide.noun} ranges from ${low.text} (${low.name}) to ${high.text} (${high.name}). Values: $list."
        val headline = "${guide.noun.replaceFirstChar { it.uppercase() }} ranges from ${low.text} (${low.name}) to ${high.text} (${high.name}).$care"
        return Statement(observation, listOfNotNull(guide.manyMeaning, lossNote).joinToString(" "), headline)
    }

    /** Payers vs non-payers: "None" is a real 0%, unknown history never is. */
    private fun dividends(sorted: List<Reading>): Statement? {
        val none = sorted.filter { it.noDividend }
        if (none.isEmpty()) return null
        val payers = sorted - none.toSet()
        if (payers.isEmpty()) return Statement(
            if (sorted.size == 2) "Neither ${sorted[0].name} nor ${sorted[1].name} paid a dividend in the past year." else "None of these ${sorted.size} companies paid a dividend in the past year.",
            "Many companies reinvest profits instead of paying dividends; not paying one isn't good or bad on its own.", null)
        val observation = "${join(payers.map { "${it.name} (${it.text})" })} paid dividends over the past year; ${join(none.map { it.name })} paid none."
        return Statement(observation, "A company that doesn't pay a dividend may be reinvesting its cash, or may not have cash to spare. The yield alone doesn't say which.", observation)
    }

    // ---------- Market cap ----------

    private fun marketCap(guide: MetricGuides.Guide, ctx: Context, excluded: MutableList<Excluded>): MetricInterpretation {
        class Cap(val record: CompanyRecord, val local: Double, val usd: Double?)
        val caps = ctx.loaded.mapNotNull { r ->
            val local = r.marketCap?.takeIf { it > 0 && it.isFinite() }
            if (local == null) { excluded += Excluded(r.name, "Market value isn't available right now."); null }
            else Cap(r, local, r.metrics["marketCap"]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value?.takeIf { it > 0 && it.isFinite() })
        }
        val currencies = caps.map { it.record.currency }.distinct()
        val mixed = currencies.size > 1
        val caveats = excluded.map { "${it.name}: ${it.reason}" }.toMutableList()
        val learnMore = guide.learnMore.toMutableList()
        var comparability = if (excluded.isEmpty()) Comparability.COMPARABLE else Comparability.COMPARABLE_WITH_CAVEATS
        var meaning: String? = null
        var headline: String? = null
        fun text(c: Cap) = MetricFormatter.money(c.local, c.record.currency, mixed)
        /** Local amount plus the converted USD amount actually compared, e.g. "C$285.8B ≈ US$211.7B". */
        fun compared(c: Cap) = if (mixed && c.record.currency != "USD" && c.usd != null) "${text(c)} ≈ ${MetricFormatter.money(c.usd, "USD", true)}" else text(c)
        val observation: String
        when {
            caps.size < 2 -> {
                comparability = Comparability.INSUFFICIENT_DATA
                observation = caps.singleOrNull()?.let { "Only ${it.record.name} has a market value to show (${text(it)}), so there's nothing to compare it with here." }
                    ?: "None of these companies has a market value to show here."
            }
            mixed && caps.any { it.usd == null || it.record.currency == null } -> {
                comparability = Comparability.NOT_COMPARABLE
                observation = "These market values are in different currencies (" + caps.joinToString("; ") { "${it.record.name}: ${text(it)}" } +
                    ") and no exchange rate is available for all of them, so they aren't compared."
            }
            else -> {
                if (mixed) {
                    comparability = Comparability.COMPARABLE_WITH_CAVEATS
                    val rates = ctx.fx.filter { f -> caps.any { it.record.currency == f.from } }
                    val rate = if (rates.isEmpty()) " at the latest exchange rate the server had" else " at " + rates.joinToString("; ") {
                        "1 ${it.from} = ${rateText(it.rate)} ${it.to} (${it.source}${it.date?.let { d -> ", $d" } ?: ""})"
                    }
                    caveats.add(0, "Compared after converting to US dollars$rate: " +
                        caps.filter { it.record.currency != "USD" }.joinToString("; ") { "${it.record.name} ${text(it)} ≈ ${MetricFormatter.money(it.usd!!, "USD", true)}" } +
                        ". Converted amounts are approximate and move with the exchange rate.")
                }
                val value = { c: Cap -> if (mixed) c.usd!! else c.local }
                val sorted = caps.sortedWith(compareByDescending<Cap> { value(it) }.thenBy { it.record.symbol })
                val high = sorted.first(); val low = sorted.last()
                val similar = abs(value(high) - value(low)) <= CLOSE_RELATIVE * value(high)
                observation = when {
                    sorted.size == 2 && similar -> "${high.record.name} (${compared(high)}) and ${low.record.name} (${compared(low)}) have a similar market value."
                    sorted.size == 2 -> "${high.record.name} has a larger market capitalization than ${low.record.name} (${compared(high)} vs ${compared(low)})."
                    similar -> "All ${sorted.size} companies have a similar market value (" + caps.joinToString(", ") { "${it.record.name} ${compared(it)}" } + ")."
                    else -> "Among these ${sorted.size} companies, market cap ranges from ${compared(low)} (${low.record.name}) to ${compared(high)} (${high.record.name})."
                }
                meaning = if (similar) "Similar size says nothing about how the businesses perform." else if (sorted.size == 2)
                    "This indicates a higher current stock-market valuation of ${high.record.name}'s shares, not necessarily higher sales, stronger profitability or more cash."
                    else "A larger market cap means a higher current market value of the shares, not necessarily a stronger business."
                headline = null // size is context, not a learning summary point
            }
        }
        caveats += guide.cautions
        return MetricInterpretation("marketCap", label("marketCap"), "Understanding ${guide.short}", guide.definition, guide.why, comparability, tidy(observation), meaning?.let(::tidy),
            caveats.map(::tidy).distinct(), learnMore, MetricEducation.find("marketCap")?.calculation,
            guide.related.map { RelatedMetric(it, label(it), it in GUIDED) }, guide.questions, headline)
    }

    // ---------- Formatting ----------

    /** Company names can end in "." ("Apple Inc."): never end a sentence with "..". */
    private fun tidy(text: String): String = text.replace("..", ".")

    private fun rateText(rate: Double): String {
        val scaled = round(rate * 10_000).toLong()
        return "${scaled / 10_000}.${(scaled % 10_000).toString().padStart(4, '0')}"
    }

    internal fun join(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** The period a value covers, in words ("FY ended Sep 2025", "Trailing twelve months"). Shared with the table. */
    fun periodLabel(b: FinancialBasis?): String? {
        b ?: return null
        val month = b.date?.let { d -> MONTHS.getOrNull((d.drop(5).take(2).toIntOrNull() ?: 0) - 1)?.let { "$it ${d.take(4)}" } }
        return when (b.period.lowercase()) {
            "ttm" -> "Trailing twelve months"
            "annual", "fy" -> month?.let { "FY ended $it" } ?: b.fiscalYear?.let { "FY$it" }
            "quarter" -> month?.let { "Quarter ended $it" }
            "latest quote" -> "Latest quote"
            else -> null
        }
    }
}

/**
 * The beginner education for each guided metric (one place, ready for localization). Wording rules:
 * describe, never judge; "higher/lower" not "better/worse"; no thresholds; no causes stated as fact.
 */
internal object MetricGuides {
    class Guide(
        val short: String,
        /** Lower-case name used inside sentences. */
        val noun: String,
        val definition: String,
        val why: String,
        /** "a higher trailing P/E" — used as "A has … than B". */
        val higher: String,
        val pairMeaning: (high: ReadingView, low: ReadingView) -> String,
        val pairHeadline: (high: ReadingView, low: ReadingView) -> String,
        val manyMeaning: String,
        val cautions: List<String>,
        val learnMore: List<String>,
        val related: List<String>,
        val questions: List<String>
    )

    /** What guide text may read from a value: the company name, the displayed text and the number's sign. */
    interface ReadingView { val name: String; val text: String; val value: Double }

    private fun growth(short: String, noun: String, definition: String, period: String, cautions: List<String>, related: List<String>, questions: List<String>) = Guide(
        short, noun, definition,
        "Growing sales can come from more customers, higher prices or new products; shrinking sales can be an early sign worth investigating.",
        "higher $noun",
        { h, l -> when {
            h.value > 0 && l.value < 0 -> "${h.name}'s revenue grew while ${l.name}'s declined over $period."
            h.value <= 0 -> "Both companies' revenue declined over $period; ${l.name}'s fell more."
            else -> "${h.name}'s revenue grew faster than ${l.name}'s over $period."
        } + " Faster growth isn't automatically better." },
        { h, l -> when {
            h.value > 0 && l.value < 0 -> "${h.name}'s revenue grew (${h.text}) while ${l.name}'s declined (${l.text}) over $period."
            h.value <= 0 -> "Both companies reported lower revenue over $period (${h.text} and ${l.text})."
            else -> "${h.name} reported faster year-over-year revenue growth than ${l.name} for $period (${h.text} vs ${l.text})."
        } },
        "Higher growth means sales rose faster over the period shown; it doesn't show whether that growth was profitable or will continue.",
        cautions, listOf(
            "Growth that costs a lot to achieve may not lead to more profit, so look at margins too.",
            "Business cycles affect industries differently: some companies' sales rise and fall with the economy.",
            "Currency moves can make reported sales grow or shrink even when the business sells the same amount.",
            "A weak or unusual prior period can make growth look high or low (the \"base effect\")."
        ), related, questions
    )

    private val guides: Map<String, Guide> = mapOf(
        "marketCap" to Guide("market cap", "market cap",
            "Market capitalization is the total market value of a company's shares: share price × shares outstanding.",
            "It shows how large the stock market values the company today, which helps put other numbers, like revenue and profit, in context.",
            "a larger market capitalization", { h, _ -> "${h.name}'s shares have a higher total market value right now." }, { h, l -> "${h.name} has a larger market cap than ${l.name}." },
            "A larger market cap means a higher current market value of the shares, not necessarily a stronger business.",
            listOf("Market cap isn't revenue, profit or cash; it changes every time the share price moves.",
                "A larger company isn't automatically a better business or a better investment."),
            listOf("Share price alone doesn't show size: a company with more shares can have a lower share price and still be larger.",
                "Very large companies can find it harder to grow quickly simply because of their size.",
                "Amounts in different currencies (such as CAD and USD) have to be converted before they're compared."),
            listOf("revenueGrowth", "priceSales", "pe"),
            listOf("How much revenue and profit does each company produce for its size?",
                "Has the market value changed mostly because of the share price or because of new shares?")),
        "pe" to Guide("P/E", "trailing P/E",
            "P/E (price to earnings) compares a company's share price with its earnings per share over the last twelve months.",
            "It helps show how much investors currently pay for each dollar of a company's reported earnings.",
            "a higher trailing P/E",
            { h, l -> "Investors currently pay more per dollar of reported earnings for ${h.name} than for ${l.name}, based on these measurements. That doesn't make ${l.name} cheaper in any absolute sense." },
            { h, l -> "${l.name} has a lower trailing P/E than ${h.name} (${l.text} vs ${h.text}), but differences in earnings, growth expectations and business risk may affect the comparison." },
            "A higher P/E means investors currently pay more per dollar of reported earnings; it isn't a measure of quality or value on its own.",
            listOf("A lower P/E can reflect slower expected growth, business risks, different accounting results or earnings that were unusually high this year. It isn't proof that a stock is undervalued.",
                "A higher P/E can reflect expectations of faster growth, or earnings that were temporarily low.",
                "P/E isn't meaningful when a company has a loss (zero or negative earnings), so it isn't shown then."),
            listOf("One-time gains or charges change earnings, and so P/E, without changing the business.",
                "Typical P/E levels differ widely between industries, so cross-industry comparisons need extra care.",
                "Trailing P/E uses reported earnings; forward P/E (under More metrics) uses analyst estimates that can be wrong. They're never mixed."),
            listOf("revenueGrowth", "netMargin", "priceSales"),
            listOf("Is either company's latest-year profit unusually high or low compared with earlier years?",
                "Do differences in revenue growth or profit margins help explain the gap in P/E?")),
        "priceSales" to Guide("price to sales", "price-to-sales ratio",
            "Price to sales (P/S) compares a company's market value with its revenue over the last twelve months.",
            "It shows how much investors pay for each dollar of sales, and it can still be used when a company has no profit.",
            "a higher price-to-sales ratio",
            { h, l -> "Investors currently pay more per dollar of sales for ${h.name} than for ${l.name}. Sales aren't profit, so this doesn't show which business earns more from those sales." },
            { h, l -> "${l.name} has a lower price-to-sales ratio than ${h.name} (${l.text} vs ${h.text}); how much of those sales becomes profit matters too." },
            "A higher P/S means investors pay more per dollar of sales; profit margins and growth help explain why ratios differ.",
            listOf("A company that keeps more of each sale as profit (a higher margin) may have a higher P/S for that reason.",
                "A low P/S doesn't mean a stock is undervalued; it can reflect thin margins, slowing sales or business risks."),
            listOf("Revenue quality matters: recurring subscription sales and one-off sales aren't the same.",
                "Businesses with naturally thin margins, such as retailers, often have lower P/S than software companies."),
            listOf("netMargin", "revenueGrowth", "pe"),
            listOf("How much of each company's revenue turns into profit?",
                "Is each company's revenue growing, steady or shrinking?")),
        "revenueGrowth" to growth("revenue growth (fiscal year)", "revenue growth",
            "Revenue growth shows how much a company's sales changed in its latest fiscal year compared with the year before.",
            "their latest fiscal years",
            listOf("Faster growth isn't automatically better: it can come from acquisitions, price increases, currency moves or a weak prior year.",
                "One year doesn't establish a long-term trend."),
            listOf("quarterRevenueGrowth", "netMargin", "pe"),
            listOf("Did growth come from the existing business or from acquisitions?",
                "Has growth been steady over several years, or is this year unusual? (See Fiscal-year figures under More metrics.)")),
        "quarterRevenueGrowth" to growth("quarterly revenue growth", "latest-quarter revenue growth",
            "Latest-quarter revenue growth compares sales in the most recent reported quarter with the same quarter a year earlier.",
            "their latest reported quarters",
            listOf("A single quarter doesn't establish a trend; one strong or weak quarter can reverse.",
                "Companies' quarters can end in different months, so these quarters may not cover the same time."),
            listOf("revenueGrowth", "netMargin"),
            listOf("Is the latest quarter in line with the fiscal-year growth, or different?",
                "Did anything unusual (an acquisition, a product launch, a currency move) affect this quarter?")),
        "netMargin" to Guide("net profit margin", "net profit margin",
            "Net profit margin is the share of revenue left as profit after all costs, interest and taxes: net income ÷ revenue.",
            "It shows how much of each dollar of sales a company keeps as profit.",
            "a higher net profit margin",
            { h, l -> "${h.name} kept more of each dollar of sales as profit than ${l.name} over the period shown." },
            { h, l -> "${h.name} reported a higher net profit margin than ${l.name} (${h.text} vs ${l.text})." },
            "A higher margin means a larger share of sales was kept as profit over the period shown.",
            listOf("Industries have very different typical margins, so compare margins carefully across industries.",
                "One-time gains or losses (such as selling a business or a large write-down) can make one period's margin unusually high or low."),
            listOf("A lower margin isn't automatically worse: some businesses deliberately sell large volumes at low margins.",
                "Young companies often report losses while they invest to grow.",
                "Margins should cover the same kind of period (trailing twelve months or a fiscal year) to be compared."),
            listOf("priceSales", "revenueGrowth", "pe"),
            listOf("Was either company's profit affected by one-time items?",
                "Has each company's margin been rising or falling over several years?")),
        "debtEquity" to Guide("debt to equity", "debt-to-equity ratio",
            "Debt to equity compares a company's total debt with its shareholders' equity (what would be left for shareholders after paying all liabilities).",
            "It shows how much a company relies on borrowing compared with shareholders' money.",
            "more debt relative to equity",
            { h, l -> "${h.name} reports more debt relative to shareholders' equity than ${l.name} under this definition. This ratio alone doesn't determine financial strength." },
            { h, l -> "${h.name} reports more debt relative to equity than ${l.name} under the displayed definition (${h.text} vs ${l.text}). This ratio alone doesn't determine financial strength." },
            "A higher ratio means more borrowing relative to shareholders' equity; whether that's manageable depends on cash flow and interest costs.",
            listOf("Higher debt isn't automatically dangerous: some stable businesses borrow more, and what matters is whether cash flow can cover interest and repayments.",
                "When equity is zero or negative (for example after large share buybacks), the ratio isn't meaningful."),
            listOf("Data providers define \"debt\" differently (some include leases), so ratios from different sources may not match.",
                "Banks and insurers borrow and lend as their core business, so debt to equity isn't comparable for them.",
                "Interest coverage and cash (under More metrics) show how comfortably debt is being serviced."),
            listOf("interestCoverage", "currentRatio", "netMargin"),
            listOf("Does each company generate enough cash to cover its interest and repayments?",
                "Has debt been rising or falling in recent years, and why?")),
        "dividendYield" to Guide("dividend yield", "dividend yield",
            "Dividend yield compares the dividends paid per share over the past year with the current share price.",
            "It shows how much cash the shares paid out relative to their price over the past year.",
            "a higher dividend yield",
            { h, l -> "${h.name}'s shares paid more in dividends relative to their current price than ${l.name}'s over the past year. That's a description of past payments, not guaranteed future income." },
            { h, l -> "${h.name} has a higher dividend yield than ${l.name} (${h.text} vs ${l.text}); dividends aren't guaranteed." },
            "A higher yield means more was paid out relative to today's price over the past year, not guaranteed future income.",
            listOf("A dividend isn't guaranteed: companies can cut, pause or stop it.",
                "A yield can rise simply because the share price fell, which can be a sign of trouble rather than generosity."),
            listOf("Many companies, especially growing ones, reinvest profits instead of paying dividends.",
                "Dividend habits differ by industry: utilities, banks and REITs often pay more; technology companies often pay less.",
                "Yield describes past payments relative to price, not total return."),
            listOf("payoutRatio", "netMargin", "debtEquity"),
            listOf("Does each company earn enough to cover its dividend? (See payout ratio under More metrics.)",
                "Has each dividend been raised, held or cut in recent years?"))
    )

    fun of(id: String): Guide = guides.getValue(id)
}
