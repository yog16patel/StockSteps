package org.example.stocksteps.earnings

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.portfolio.Decimal

// Earnings Intelligence Lite, Phase 2: Earnings Results. A normalized report per fiscal period and
// insights calculated from it with exact decimal arithmetic. The server is authoritative: it builds
// both and the apps only format them.

/**
 * Exact decimal helpers shared by every earnings comparison.
 *
 * Precision policy: values are compared exactly as the source reported them (up to eight decimal
 * places, [Decimal.SCALE]). There is no percentage tolerance: MET means the actual equals the
 * estimate; any difference is a BEAT or MISS. Percentages are rounded only for display.
 */
object EarningsMath {
    private val HUNDRED = Decimal.parse("100")

    /** A provider number as an exact decimal (its shortest decimal form, so 1.45 stays 1.45); null when missing or not finite. */
    fun decimal(value: Double?): Decimal? {
        val v = value?.takeIf { it.isFinite() } ?: return null
        return runCatching { Decimal.parse(plain(v.toString())) }.getOrNull()
    }

    fun decimal(text: String?): Decimal? = text?.let { runCatching { Decimal.parse(it) }.getOrNull() }

    /** "1.775E11" → "177500000000", "1.0E-4" → "0.0001"; rounds beyond eight fractional digits half-up. */
    internal fun plain(text: String): String {
        val negative = text.startsWith('-')
        val body = text.removePrefix("-")
        val mantissa = body.substringBefore('E').substringBefore('e')
        val exponent = body.substringAfter('E', body.substringAfter('e', "0")).toIntOrNull() ?: 0
        val digits = mantissa.replace(".", "")
        var point = (mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length) + exponent
        val padded = when {
            point <= 0 -> "0".repeat(-point + 1) + digits.also { point = 1 }
            point > digits.length -> digits + "0".repeat(point - digits.length)
            else -> digits
        }
        val whole = padded.take(point).trimStart('0').ifEmpty { "0" }
        val fraction = padded.drop(point).trimEnd('0')
        val result = if (fraction.length <= Decimal.SCALE) whole + if (fraction.isEmpty()) "" else ".$fraction"
            else Decimal.parse("$whole.${fraction.take(Decimal.SCALE + 1).let { it.dropLast(1) }}").let { d ->
                if (fraction[Decimal.SCALE] >= '5') (d + Decimal.parse("0.00000001")).toString() else d.toString()
            }
        return if (negative && result.trim('0', '.').isNotEmpty()) "-$result" else result
    }

    fun abs(d: Decimal): Decimal = if (d < Decimal.ZERO) -d else d

    /** BEAT / MISS / MET from the exact difference; there is no hidden tolerance. */
    fun classify(actual: Decimal, estimate: Decimal): Classification = when {
        actual > estimate -> Classification.BEAT
        actual < estimate -> Classification.MISS
        else -> Classification.MET
    }

    /** (a − b) ÷ |b| × 100, exact to eight places; null when the base is zero. */
    fun percent(a: Decimal, b: Decimal): Decimal? = if (b == Decimal.ZERO) null else (a - b).multiplyDivide(HUNDRED, abs(b))
}

@Serializable enum class PeriodType { QUARTER, ANNUAL }

/** Where a number came from: provider, its record id, when it was published and when StockSteps fetched it. */
@Serializable
data class EarningsSource(
    val provider: String,
    /** "actual" or "estimate". */
    val role: String,
    val recordId: String? = null,
    val publishedAt: String? = null,
    val retrievedAt: String? = null,
    val basis: String? = null,
    val currency: String? = null,
    /** "Raw currency units", "per share". */
    val units: String? = null,
    val fiscalPeriod: String? = null
)

/** Revenue for a comparison period (same company, earlier fiscal period). */
@Serializable
data class ComparisonPeriod(
    val reportId: String,
    val fiscalYear: Int,
    val fiscalQuarter: Int,
    val periodEnd: String? = null,
    /** Exact decimal string, raw currency units; null when the source has none. */
    val revenue: String? = null,
    val currency: String? = null,
    val periodType: PeriodType = PeriodType.QUARTER
) { val label: String get() = "Q$fiscalQuarter FY$fiscalYear" }

/**
 * One company's reported results for one fiscal period. [reportId] is "SYMBOL:YYYY-Qn" with the
 * company's own fiscal year and quarter (the same fiscal-period identity as the calendar event, so a
 * moved announcement date doesn't change it). Money is exact decimal strings; missing is null, never zero.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsReport(
    val reportId: String,
    /** The provider's exchange-qualified symbol ("TD" on NYSE ≠ "TD.TO" on TSX). */
    val instrumentId: String,
    val symbol: String,
    val exchange: String? = null,
    val country: String? = null,
    val companyName: String,
    val logoUrl: String? = null,
    val fiscalYear: Int,
    val fiscalQuarter: Int,
    val fiscalPeriodEnd: String? = null,
    val periodType: PeriodType = PeriodType.QUARTER,
    /** Exchange-local announcement date (yyyy-MM-dd). */
    val reportDate: String,
    /** When the source says the results were published; null when it doesn't say. */
    val publishedAt: String? = null,
    val reportingCurrency: String? = null,
    val epsActual: String? = null,
    val epsActualBasis: EpsBasis = EpsBasis.UNKNOWN,
    val epsEstimate: String? = null,
    val epsEstimateBasis: EpsBasis = EpsBasis.UNKNOWN,
    val revenueActual: String? = null,
    val revenueEstimate: String? = null,
    val estimateCurrency: String? = null,
    /** The period the consensus covers; an annual estimate is never compared with a quarter. */
    val estimatePeriodType: PeriodType = PeriodType.QUARTER,
    val analysts: Int? = null,
    val previousYear: ComparisonPeriod? = null,
    val previousQuarter: ComparisonPeriod? = null,
    /** The source revised the figures after first publication. */
    val revised: Boolean = false,
    @EncodeDefault val sources: List<EarningsSource> = emptyList(),
    val sourceUpdatedAt: String? = null,
    val fetchedAt: String? = null,
    /** Fields the source didn't provide ("epsEstimate", "revenueActual", "previousYearRevenue", …). */
    @EncodeDefault val unavailableFields: List<String> = emptyList()
) {
    val period: String get() = "Q$fiscalQuarter FY$fiscalYear"
}

/** EPS or revenue: actual vs consensus. Amounts are exact decimal strings. */
@Serializable
data class MetricComparison(
    val actual: String? = null,
    val estimate: String? = null,
    val surpriseAmount: String? = null,
    /** Exact percent (8 places); null when not valid or not useful for beginners. */
    val surprisePercent: String? = null,
    val classification: Classification = Classification.UNAVAILABLE,
    /** Beginner explanation of the result. */
    val explanation: String,
    /** Why the comparison or percentage isn't available. */
    val reason: String? = null,
    val basis: String? = null,
    val currency: String? = null
)

/** Revenue growth against an earlier fiscal period. */
@Serializable
data class GrowthComparison(
    val current: String? = null,
    val prior: String? = null,
    val currentLabel: String,
    val priorLabel: String? = null,
    val percent: String? = null,
    val explanation: String,
    val reason: String? = null,
    val currency: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsResultInsights(
    val reportId: String,
    val eps: MetricComparison,
    val revenue: MetricComparison,
    val yearOverYear: GrowthComparison,
    val quarterOverQuarter: GrowthComparison,
    val takeaway: String,
    @EncodeDefault val comparisonWarnings: List<String> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsResultsResponse(
    val report: EarningsReport,
    val insights: EarningsResultInsights,
    val asOf: String,
    val freshness: DataFreshness = DataFreshness.FRESH,
    val fetchedAt: String? = null,
    @EncodeDefault val notes: List<String> = emptyList(),
    val sampleData: Boolean = false
)

/** A reported period in a company's list of results. */
@Serializable
data class EarningsReportSummary(
    val reportId: String,
    val fiscalYear: Int,
    val fiscalQuarter: Int,
    val reportDate: String,
    val eps: Classification = Classification.UNAVAILABLE,
    val revenue: Classification = Classification.UNAVAILABLE
) { val period: String get() = "Q$fiscalQuarter FY$fiscalYear" }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsReportsPage(
    val symbol: String,
    @EncodeDefault val reports: List<EarningsReportSummary> = emptyList(),
    val asOf: String,
    val freshness: DataFreshness = DataFreshness.FRESH,
    val sampleData: Boolean = false
)

/** Builds the normalized report from the provider's per-period records (one provider per event). */
object EarningsReportMapper {
    fun reportId(symbol: String, fiscalYear: Int, fiscalQuarter: Int) = "${symbol.uppercase()}:$fiscalYear-Q$fiscalQuarter"

    /** "AAPL:2026-Q4" → ("AAPL", 2026, 4); null when malformed. */
    fun parse(reportId: String): Triple<String, Int, Int>? {
        val m = Regex("([A-Za-z0-9][A-Za-z0-9.-]{0,19}):(\\d{4})-Q([1-4])").matchEntire(reportId) ?: return null
        return Triple(m.groupValues[1].uppercase(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    private fun comparison(e: EarningsEvent?) = e?.let {
        ComparisonPeriod(it.id, it.fiscalYear, it.fiscalQuarter, it.periodEnd, EarningsMath.decimal(it.actual?.revenue)?.toString(), it.actual?.currency)
    }

    /** Null when [event] has no reported figures (a scheduled date isn't a report). */
    fun report(event: EarningsEvent, history: List<EarningsEvent>, fetchedAt: String?): EarningsReport? {
        val actual = event.actual?.takeIf { it.eps != null || it.revenue != null } ?: return null
        val estimate = event.estimate
        val reported = history.filter { it.actual != null && it.id != event.id }
        val yearAgo = EarningsCalculator.yearAgo(event, reported)
        val previous = EarningsCalculator.previousQuarter(event, reported)
        val epsActual = EarningsMath.decimal(actual.eps)
        val epsEstimate = EarningsMath.decimal(estimate?.eps)
        val revActual = EarningsMath.decimal(actual.revenue)
        val revEstimate = EarningsMath.decimal(estimate?.revenue)
        val missing = buildList {
            if (epsActual == null) add("epsActual"); if (epsEstimate == null) add("epsEstimate")
            if (revActual == null) add("revenueActual"); if (revEstimate == null) add("revenueEstimate")
            if (yearAgo?.actual?.revenue == null) add("previousYearRevenue"); if (previous?.actual?.revenue == null) add("previousQuarterRevenue")
            if (actual.reportedAt == null) add("publishedAt")
        }
        return EarningsReport(
            event.id, event.symbol, event.symbol, event.exchange, event.country, event.name, event.logoUrl, event.fiscalYear, event.fiscalQuarter,
            event.periodEnd, PeriodType.QUARTER, event.date, actual.reportedAt, actual.currency,
            epsActual?.toString(), actual.epsBasis, epsEstimate?.toString(), estimate?.epsBasis ?: EpsBasis.UNKNOWN,
            revActual?.toString(), revEstimate?.toString(), estimate?.currency, estimate?.periodType ?: PeriodType.QUARTER, estimate?.analysts,
            comparison(yearAgo), comparison(previous), actual.restated,
            listOfNotNull(
                EarningsSource(actual.source, "actual", event.id, actual.reportedAt, fetchedAt, actual.epsBasis.label, actual.currency, "EPS per share; revenue in raw currency units", event.period),
                estimate?.let { EarningsSource(it.source, "estimate", event.id, it.asOf, fetchedAt, it.epsBasis.label, it.currency, "EPS per share; revenue in raw currency units",
                    if (it.periodType == PeriodType.ANNUAL) "FY${event.fiscalYear}" else event.period) }
            ),
            event.sourceUpdatedAt, fetchedAt, missing
        )
    }
}

/**
 * Phase 2 insights from a normalized report: independent EPS and revenue comparisons, year-over-year
 * and quarter-over-quarter growth, and a deterministic beginner takeaway. Never a recommendation.
 */
object EarningsResultsCalculator {
    private fun basisLabel(a: EpsBasis, e: EpsBasis) = if (a == e) a.label else null

    fun eps(r: EarningsReport): MetricComparison {
        val a = EarningsMath.decimal(r.epsActual)
        val e = EarningsMath.decimal(r.epsEstimate)
        val currency = r.reportingCurrency ?: r.estimateCurrency
        fun unavailable(reason: String) = MetricComparison(a?.toString(), e?.toString(), null, null, Classification.UNAVAILABLE, reason, reason,
            basisLabel(r.epsActualBasis, r.epsEstimateBasis), currency)
        when {
            a == null && e == null -> return unavailable("Neither reported EPS nor an analyst estimate is available.")
            a == null -> return unavailable("Reported EPS isn't available from the data source.")
            e == null -> return unavailable("No analyst EPS estimate is available, so there's nothing to compare with.")
            r.estimatePeriodType != r.periodType -> return unavailable("The estimate covers a full year but the result covers one quarter, so they can't be compared.")
            r.estimateCurrency != null && r.reportingCurrency != null && r.estimateCurrency != r.reportingCurrency ->
                return unavailable("The estimate (${r.estimateCurrency}) and the result (${r.reportingCurrency}) are in different currencies.")
            r.epsActualBasis == EpsBasis.UNKNOWN || r.epsEstimateBasis == EpsBasis.UNKNOWN || r.epsActualBasis != r.epsEstimateBasis ->
                return unavailable("The estimate (${r.epsEstimateBasis.label}) and the result (${r.epsActualBasis.label}) aren't measured the same way, so they aren't compared.")
        }
        a!!; e!!
        val diff = a - e
        val c = EarningsMath.classify(a, e)
        val reason = when {
            e == Decimal.ZERO -> "The estimate was zero, so only the dollar difference is shown."
            e < Decimal.ZERO -> "Analysts expected a loss; a percentage would be confusing here, so only the dollar difference is shown."
            else -> null
        }
        val loss = e < Decimal.ZERO || a < Decimal.ZERO
        val explanation = when (c) {
            Classification.BEAT -> if (loss && a < Decimal.ZERO) "The company lost less per share than analysts expected. A smaller-than-expected loss still counts as an EPS beat, but the company didn't make a profit."
                else "The company earned more per share than analysts expected. This is called an EPS beat. It does not guarantee that the stock price will rise."
            Classification.MISS -> if (loss) "The company lost more per share than analysts expected. This is called an EPS miss. It does not guarantee that the stock price will fall."
                else "The company earned less per share than analysts expected. This is called an EPS miss. It does not guarantee that the stock price will fall."
            Classification.MET -> "Earnings per share matched the analyst estimate exactly."
            Classification.UNAVAILABLE -> ""
        }
        return MetricComparison(a.toString(), e.toString(), diff.toString(), if (reason == null) EarningsMath.percent(a, e)?.toString() else null, c, explanation, reason,
            r.epsActualBasis.label, currency)
    }

    fun revenue(r: EarningsReport): MetricComparison {
        val a = EarningsMath.decimal(r.revenueActual)
        val e = EarningsMath.decimal(r.revenueEstimate)
        val currency = r.reportingCurrency ?: r.estimateCurrency
        fun unavailable(reason: String) = MetricComparison(a?.toString(), e?.toString(), null, null, Classification.UNAVAILABLE, reason, reason, null, currency)
        when {
            a == null && e == null -> return unavailable("Neither reported revenue nor an analyst estimate is available.")
            a == null -> return unavailable("Reported revenue isn't available from the data source.")
            e == null -> return unavailable("No analyst revenue estimate is available, so there's nothing to compare with.")
            r.estimatePeriodType != r.periodType -> return unavailable("The estimate covers a full year but the result covers one quarter, so they can't be compared.")
            r.estimateCurrency != null && r.reportingCurrency != null && r.estimateCurrency != r.reportingCurrency ->
                return unavailable("The estimate (${r.estimateCurrency}) and the result (${r.reportingCurrency}) are in different currencies.")
            e <= Decimal.ZERO -> return unavailable("The revenue estimate isn't a positive amount, so a surprise can't be calculated.")
        }
        a!!; e!!
        val c = EarningsMath.classify(a, e)
        val explanation = when (c) {
            Classification.BEAT -> "The company generated more revenue than analysts expected. Revenue represents money earned from business activities before deducting expenses."
            Classification.MISS -> "The company generated less revenue than analysts expected. Revenue represents money earned from business activities before deducting expenses."
            Classification.MET -> "Revenue matched the analyst estimate exactly."
            Classification.UNAVAILABLE -> ""
        }
        return MetricComparison(a.toString(), e.toString(), (a - e).toString(), EarningsMath.percent(a, e)?.toString(), c, explanation, null, null, currency)
    }

    private fun spacingOk(current: String?, prior: String?, range: IntRange): Boolean {
        if (current == null || prior == null) return true // fiscal ids already match; nothing more to check
        val days = (org.example.stocksteps.markets.MarketsPresenter.dayNumber(current) ?: return true) - (org.example.stocksteps.markets.MarketsPresenter.dayNumber(prior) ?: return true)
        return days in range
    }

    private fun growth(r: EarningsReport, prior: ComparisonPeriod?, yearOverYear: Boolean): GrowthComparison {
        val current = EarningsMath.decimal(r.revenueActual)
        val label = r.period
        val priorLabel = prior?.label
        val currency = r.reportingCurrency
        fun none(reason: String) = GrowthComparison(current?.toString(), prior?.revenue, label, priorLabel, null, "Not enough comparable data.", reason, currency)
        if (current == null) return none("This report doesn't include revenue.")
        prior ?: return none(if (yearOverYear) "Revenue for the same quarter last year isn't available." else "Revenue for the previous quarter isn't available.")
        val before = EarningsMath.decimal(prior.revenue) ?: return none("Revenue for ${prior.label} isn't available.")
        if (prior.periodType != PeriodType.QUARTER) return none("${prior.label} covers a different length of time, so it isn't compared.")
        if (prior.currency != null && currency != null && prior.currency != currency) return none("${prior.label} is reported in ${prior.currency}, this quarter in $currency.")
        if (!spacingOk(r.fiscalPeriodEnd, prior.periodEnd, if (yearOverYear) 350..380 else 80..100))
            return none("The company's fiscal calendar changed between these periods, so the comparison could be misleading.")
        if (before <= Decimal.ZERO) return none("${prior.label} revenue was ${if (before == Decimal.ZERO) "zero" else "negative"}, so a percentage change isn't meaningful.")
        val pct = EarningsMath.percent(current, before)!!
        val direction = when { pct > Decimal.ZERO -> "increased"; pct < Decimal.ZERO -> "decreased"; else -> "was unchanged" }
        val explanation = if (yearOverYear) {
            (if (pct == Decimal.ZERO) "Revenue was the same as in the same quarter last year." else "Revenue $direction compared with the same quarter last year.") +
                " Comparing the same quarter helps account for seasonal business patterns."
        } else {
            (if (pct == Decimal.ZERO) "Revenue was the same as in the previous quarter." else "Revenue $direction compared with the previous quarter.") +
                " Quarter-to-quarter changes can reflect seasonality, such as holiday sales, so they don't always mean the business is improving or weakening."
        }
        return GrowthComparison(current.toString(), before.toString(), label, priorLabel, pct.toString(), explanation, null, currency)
    }

    fun yearOverYear(r: EarningsReport) = growth(r, r.previousYear, true)
    fun quarterOverQuarter(r: EarningsReport) = growth(r, r.previousQuarter, false)

    /** A deterministic summary of the two independent classifications; never advice or a prediction. */
    fun takeaway(eps: Classification, revenue: Classification): String {
        val b = Classification.BEAT; val m = Classification.MISS; val u = Classification.UNAVAILABLE
        return when {
            eps == b && revenue == b -> "The company reported EPS and revenue above expectations. This suggests the reported results exceeded consensus estimates on both measures, but investors may also consider future guidance and other information."
            eps == b && revenue == m -> "The company earned more per share than expected but generated less revenue than expected. Profitability and sales performance can tell different stories."
            eps == m && revenue == b -> "Revenue exceeded expectations, but earnings per share were below estimates. Higher sales do not always translate into higher earnings."
            eps == m && revenue == m -> "The company reported EPS and revenue below expectations. Results can fall short of estimates for many reasons, and one quarter doesn't describe the whole business."
            eps == u && revenue == u -> "The company reported results, but comparable analyst estimates are unavailable. You can still examine revenue growth and previous-period performance."
            else -> {
                fun phrase(metric: String, c: Classification) = when (c) {
                    Classification.BEAT -> "$metric came in above expectations"; Classification.MISS -> "$metric came in below expectations"
                    Classification.MET -> "$metric matched expectations"; Classification.UNAVAILABLE -> null
                }
                val parts = listOfNotNull(phrase("EPS", eps), phrase("revenue", revenue))
                val missing = listOfNotNull("EPS".takeIf { eps == u }, "revenue".takeIf { revenue == u })
                parts.joinToString(", while ").replaceFirstChar { it.uppercase() } + "." +
                    (if (missing.isNotEmpty()) " A comparable estimate for ${missing.single()} isn't available." else "") +
                    " Each measure tells part of the story; neither one alone describes the quarter."
            }
        }
    }

    fun insights(r: EarningsReport): EarningsResultInsights {
        val eps = eps(r)
        val revenue = revenue(r)
        val yoy = yearOverYear(r)
        val qoq = quarterOverQuarter(r)
        val warnings = buildList {
            if (r.revised) add("The data source revised these figures after they were first published.")
            if (r.publishedAt == null) add("The data source didn't provide a publication time for this report.")
            if (r.estimatePeriodType != r.periodType) add("The available estimate covers a different period length, so it isn't compared.")
            if (r.epsActualBasis != r.epsEstimateBasis && r.epsActual != null && r.epsEstimate != null) add("EPS estimate and result use different accounting measures.")
            if (r.estimateCurrency != null && r.reportingCurrency != null && r.estimateCurrency != r.reportingCurrency) add("Estimates and results use different currencies.")
            if (r.revenueActual == null || r.epsActual == null) add("The data source provided only part of this report.")
        }
        return EarningsResultInsights(r.reportId, eps, revenue, yoy, qoq, takeaway(eps.classification, revenue.classification), warnings)
    }
}

// ---------- Earnings Results presenter (shared by Android and iOS; formatting only) ----------

/** A saved copy of reports this device opened (public data only), for offline viewing. */
interface EarningsResultsCache {
    suspend fun read(reportId: String): Pair<String, Long>?
    suspend fun write(reportId: String, json: String, savedAt: Long)
}

/** EPS or revenue card. Meaning is in words ("EPS Beat"), never colour alone. */
data class ComparisonCardView(
    val key: String,
    val title: String,
    val classification: Classification,
    /** "EPS Beat", "Revenue Miss", "EPS Met Estimates", "Comparison unavailable". */
    val classificationText: String,
    val lines: List<MetricLine>,
    val explanation: String?,
    val reason: String?,
    val basisNote: String?,
    val infoTitle: String,
    val infoBody: String
) {
    val accessibility: String get() = "$title. $classificationText. " + lines.joinToString(" ") { "${it.label}: ${it.value}." } +
        (explanation?.let { " $it" } ?: "") + (reason?.takeIf { it != explanation }?.let { " $it" } ?: "")
}

data class GrowthCardView(val key: String, val title: String, val lines: List<MetricLine>, val explanation: String, val reason: String?) {
    val accessibility: String get() = "$title. " + lines.joinToString(" ") { "${it.label}: ${it.value}." } + " $explanation" + (reason?.let { " $it" } ?: "")
}

/** A Learn link: an existing lesson, or (when none exists) a safe fallback to the Learn tab. */
data class LearnLink(val title: String, val body: String?)

data class EarningsResultsState(
    val reportId: String,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** True when the period has no published report (never a fake result). */
    val notPublished: Boolean = false,
    val response: EarningsResultsResponse? = null,
    val companyName: String = "",
    val symbolLine: String = "",
    val logoUrl: String? = null,
    /** "Q3 FY2026 Earnings Results". */
    val title: String = "",
    val periodLines: List<String> = emptyList(),
    val eps: ComparisonCardView? = null,
    val revenue: ComparisonCardView? = null,
    val yearOverYear: GrowthCardView? = null,
    val quarterOverQuarter: GrowthCardView? = null,
    val takeaway: String? = null,
    val warnings: List<String> = emptyList(),
    val learn: List<LearnLink> = emptyList(),
    val sources: List<String> = emptyList(),
    val freshnessText: String? = null,
    val stale: Boolean = false,
    /** Showing a copy saved on this device because the server couldn't be reached. */
    val offline: Boolean = false,
    val sampleData: Boolean = false,
    val unavailableFields: List<String> = emptyList(),
    val expanded: Set<String> = emptySet()
)

object EarningsResultsFormat {
    private fun dbl(text: String?) = text?.toDoubleOrNull()
    fun eps(text: String?, currency: String?) = EarningsFormatter.eps(dbl(text), currency)
    fun revenue(text: String?, currency: String?) = EarningsFormatter.revenue(dbl(text), currency)
    private fun signed(text: String?, money: (String?, String?) -> String, currency: String?): String? = text?.let {
        val d = EarningsMath.decimal(it) ?: return null
        (if (d > org.example.stocksteps.portfolio.Decimal.ZERO) "+" else "") + money(it, currency)
    }
    /** "+20.8%" from an exact percent; rounding only here, for display. */
    fun percent(text: String?): String? = EarningsMath.decimal(text)?.let { d ->
        val shown = d.display(1)
        (if (d > org.example.stocksteps.portfolio.Decimal.ZERO) "+" else "") + shown.replace("-", "−") + "%"
    }

    fun card(key: String, m: MetricComparison, eps: Boolean): ComparisonCardView {
        val money: (String?, String?) -> String = if (eps) ::eps else ::revenue
        val name = if (eps) "EPS" else "Revenue"
        val text = when (m.classification) {
            Classification.BEAT -> "$name Beat"; Classification.MISS -> "$name Miss"; Classification.MET -> "$name Met Estimates"
            Classification.UNAVAILABLE -> "Comparison unavailable"
        }
        val lines = listOfNotNull(
            MetricLine("Actual", money(m.actual, m.currency)),
            MetricLine("Expected", money(m.estimate, m.currency), if (m.estimate != null) "Analysts' consensus estimate" else null),
            m.surpriseAmount?.let { MetricLine("Surprise", signed(it, money, m.currency)!!) },
            percent(m.surprisePercent)?.let { MetricLine("Surprise %", it) }
        )
        return ComparisonCardView(key, if (eps) "Earnings Per Share (EPS)" else "Revenue", m.classification, text, lines,
            m.explanation.takeIf { m.classification != Classification.UNAVAILABLE }, m.reason,
            if (eps) m.basis?.let { "Measured as $it EPS." } else null,
            if (eps) "What is EPS?" else "What is revenue?",
            if (eps) "EPS stands for Earnings Per Share. It represents a company's profit attributable to each share under the relevant accounting measure. Diluted EPS counts shares that could be created (for example from stock options); adjusted EPS leaves out items the company considers one-time. StockSteps only compares an estimate and a result measured the same way."
            else "Revenue is the money a company earns from selling its products and services, before any expenses are taken out. Profit is what's left after expenses.")
    }

    fun growth(key: String, title: String, g: GrowthComparison): GrowthCardView = GrowthCardView(key, title, listOfNotNull(
        MetricLine(g.currentLabel, revenue(g.current, g.currency)),
        g.priorLabel?.let { MetricLine(it, revenue(g.prior, g.currency)) },
        percent(g.percent)?.let { MetricLine(if (key == "yoy") "Year-over-year growth" else "Change from previous quarter", it) }
    ), g.explanation, g.reason)

    fun learn(): List<LearnLink> = listOf(
        org.example.stocksteps.learning.BeginnerEducation.entry("eps").let { LearnLink("What is EPS?", it?.let { e -> "${e.short} ${e.why}" }) },
        org.example.stocksteps.learning.BeginnerEducation.entry("netIncome").let { LearnLink("Revenue vs Profit", it?.let { e ->
            "Revenue is money from sales before expenses. ${e.short} ${e.why}" }) },
        org.example.stocksteps.learning.BeginnerEducation.entry("earningsBeat").let { LearnLink("What is an Earnings Beat?", it?.let { e -> "${e.short} ${e.limitations}" }) },
        org.example.stocksteps.learning.BeginnerEducation.entry("revenueGrowth").let { LearnLink("Understanding Revenue Growth", it?.let { e -> "${e.short} ${e.why}" }) },
        LearnLink("Why Stock Prices React to Earnings", EarningsEducation.topic("fall-after-beat")?.body)
    )

    fun state(base: EarningsResultsState, r: EarningsResultsResponse): EarningsResultsState {
        val report = r.report
        val i = r.insights
        return base.copy(
            response = r, notPublished = false, companyName = report.companyName,
            symbolLine = listOfNotNull(report.symbol, report.exchange).joinToString(" · "), logoUrl = report.logoUrl,
            title = "${report.period} Earnings Results",
            periodLines = listOfNotNull(
                "Fiscal Q${report.fiscalQuarter} of fiscal year ${report.fiscalYear}" + (report.fiscalPeriodEnd?.let { " · period ended ${EarningsFormatter.date(it)}, ${it.take(4)}" } ?: ""),
                "Reported ${EarningsFormatter.date(report.reportDate)}, ${report.reportDate.take(4)}",
                report.publishedAt?.let { "Published by the source ${EarningsFormatter.date(it.take(10))}, ${it.drop(11).take(5)} UTC" } ?: "Publication time not provided by the source",
                if (report.revised) "Revised by the source after first publication" else null
            ),
            eps = card("eps", i.eps, true), revenue = card("revenue", i.revenue, false),
            yearOverYear = growth("yoy", "Is the Business Growing?", i.yearOverYear),
            quarterOverQuarter = growth("qoq", "Compared with the Previous Quarter", i.quarterOverQuarter),
            takeaway = i.takeaway, warnings = i.comparisonWarnings, learn = learn(),
            sources = report.sources.map { s ->
                "${if (s.role == "actual") "Results" else "Estimates"}: ${s.provider}" + (s.basis?.let { " · $it" } ?: "") + (s.currency?.let { " · $it" } ?: "") +
                    (s.fiscalPeriod?.let { " · $it" } ?: "")
            } + listOfNotNull(r.fetchedAt?.let { "Fetched ${EarningsFormatter.date(it.take(10))}, ${it.drop(11).take(5)} UTC" }) + r.notes,
            freshnessText = EarningsCalendarRules.freshnessText(r.freshness, r.fetchedAt), stale = r.freshness == DataFreshness.STALE,
            sampleData = r.sampleData, unavailableFields = report.unavailableFields
        )
    }
}

/**
 * Earnings Results for one report. Calculations come from the server; this only formats them. A
 * successful response is saved on the device (public data), so the screen can show it offline,
 * clearly labelled. Loads are cancelled with the screen; retries never duplicate in-flight requests.
 */
class EarningsResultsPresenter(
    val reportId: String,
    private val remote: EarningsRemote,
    private val scope: CoroutineScope,
    private val cache: EarningsResultsCache? = null,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(EarningsResultsState(reportId))
    val state: kotlinx.coroutines.flow.StateFlow<EarningsResultsState> = mutable
    private val refreshes = kotlinx.coroutines.flow.MutableStateFlow(0)

    init { scope.launch { refreshes.collectLatest { load(it > 0) } } }

    private suspend fun load(refresh: Boolean) {
        mutable.update { it.copy(loading = it.response == null, refreshing = refresh && it.response != null, error = null) }
        try {
            val r = remote.results(reportId)
            mutable.update { EarningsResultsFormat.state(it.copy(loading = false, refreshing = false, offline = false), r) }
            runCatching { cache?.write(reportId, json.encodeToString(EarningsResultsResponse.serializer(), r), now()) }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            val api = cause as? org.example.stocksteps.network.StockStepsApiException
            if (api?.status == 404) {
                mutable.update { it.copy(loading = false, refreshing = false, notPublished = true, error = api.error.message) }
                return
            }
            val saved = if (mutable.value.response == null) runCatching { cache?.read(reportId) }.getOrNull()?.let { (text, at) ->
                runCatching { json.decodeFromString(EarningsResultsResponse.serializer(), text) }.getOrNull()?.let { it to at }
            } else null
            val message = api?.error?.message ?: "Earnings results couldn't be loaded. Check your connection and try again."
            mutable.update { s ->
                when {
                    saved != null -> EarningsResultsFormat.state(s.copy(loading = false, refreshing = false, offline = true,
                        error = "You're seeing a copy saved on this device. $message"), saved.first)
                    else -> s.copy(loading = false, refreshing = false, error = message)
                }
            }
        }
    }

    fun refresh() { refreshes.value++ }
    fun toggle(key: String) = mutable.update { it.copy(expanded = if (key in it.expanded) it.expanded - key else it.expanded + key) }
}

/** [EarningsResultsCache] on the device's existing user-data cache, in a public (not per-account) namespace per environment. */
class UserDataResultsCache(private val cache: org.example.stocksteps.data.userdata.UserDataCache, private val environment: () -> String) : EarningsResultsCache {
    override suspend fun read(reportId: String) = cache.read("${environment()}|public", "earnings-results:$reportId")
    override suspend fun write(reportId: String, json: String, savedAt: Long) = cache.write("${environment()}|public", "earnings-results:$reportId", json, savedAt)
}
