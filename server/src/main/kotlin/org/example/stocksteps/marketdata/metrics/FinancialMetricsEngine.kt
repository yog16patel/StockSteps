package org.example.stocksteps.marketdata.metrics

import org.example.stocksteps.marketdata.CompanyFinancialHistory
import org.example.stocksteps.marketdata.FactIssue
import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.FinancialConcept.*
import org.example.stocksteps.marketdata.FinancialPeriod
import org.example.stocksteps.marketdata.PriceObservation
import org.example.stocksteps.marketdata.ReportedValue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 9B: provider-independent financial metrics from reported periods ([CompanyFinancialHistory], e.g. SEC EDGAR) and one price.
 * Pure and deterministic: exact decimals (`MathContext` 16 digits, results rounded half-even to 6 decimals), no clock, no I/O.
 * Every result carries its formula, period, inputs (with their source tags and filings) and a status; when a metric isn't supported
 * by the inputs, the value is **null** with the reason — never zero, never estimated. Formulas: `FINANCIAL_METRICS_ENGINE.md`.
 *
 * Percentages (growth, margins, ROE, ROA, yield) are expressed in percent (25 = 25 %), as the existing `FinancialCalculations`.
 */
enum class Metric(val formula: String, val percent: Boolean) {
    REVENUE_GROWTH("(revenue − prior revenue) ÷ prior revenue × 100; same fiscal period one year earlier", true),
    NET_INCOME_GROWTH("(net income − prior net income) ÷ prior net income × 100; prior must be > 0", true),
    EPS_GROWTH("(diluted EPS − prior diluted EPS) ÷ prior diluted EPS × 100; prior must be > 0", true),
    GROSS_MARGIN("gross profit ÷ revenue × 100 (gross profit = revenue − cost of revenue when not reported)", true),
    OPERATING_MARGIN("operating income ÷ revenue × 100", true),
    NET_MARGIN("net income attributable to the parent ÷ revenue × 100", true),
    EPS_BASIC("reported basic EPS", false),
    EPS_DILUTED("reported diluted EPS", false),
    EPS_DILUTED_TTM("diluted EPS over the latest four consecutive fiscal quarters (or the latest fiscal year when it is newest)", false),
    PE_RATIO("price ÷ trailing-twelve-month diluted EPS; EPS must be > 0, same currency, split-consistent", false),
    PS_RATIO("price × shares outstanding ÷ trailing-twelve-month revenue", false),
    DEBT_TO_EQUITY("(long-term debt incl. current portion + short-term borrowings when reported) ÷ stockholders' equity", false),
    CURRENT_RATIO("current assets ÷ current liabilities", false),
    RETURN_ON_EQUITY("fiscal-year net income ÷ average of opening and closing stockholders' equity × 100", true),
    RETURN_ON_ASSETS("fiscal-year net income ÷ average of opening and closing total assets × 100", true),
    FREE_CASH_FLOW("operating cash flow − purchases of property, plant and equipment (both reported for the period)", false),
    DIVIDEND_YIELD("trailing-twelve-month dividends declared per share ÷ price × 100", true)
}

enum class MetricStatus {
    AVAILABLE, MISSING_INPUT, NON_POSITIVE_DENOMINATOR, NOT_MEANINGFUL, PERIOD_MISMATCH, CURRENCY_MISMATCH, UNRELIABLE_COMPARISON,
    INSUFFICIENT_HISTORY, PRICE_UNUSABLE, SPLIT_UNVERIFIED, AMBIGUOUS
}

data class MetricInput(val name: String, val value: BigDecimal, val period: String, val concept: String?, val sourceRef: String?,
                       val derived: Boolean = false, val restated: Boolean = false)

data class MetricResult(
    val metric: Metric,
    val value: BigDecimal?,
    val status: MetricStatus,
    val period: String? = null,
    val asOf: LocalDate? = null,
    val inputs: List<MetricInput> = emptyList(),
    /** True when per-share quarters were combined (share counts differ slightly between quarters). */
    val approximate: Boolean = false,
    val note: String? = null
) {
    init { require((value != null) == (status == MetricStatus.AVAILABLE)) { "a value only when AVAILABLE" } }
}

/** Stock splits known for a listing: [verified] = the list is complete (from a licensed corporate-actions source). */
data class SplitHistory(val verified: Boolean, val splits: List<Split> = emptyList()) {
    /** [ratio] new shares per old share (2 for a 2-for-1 split, 0.1 for a 1-for-10 reverse split), effective on [date]. */
    data class Split(val date: LocalDate, val ratio: BigDecimal)
    /** Factor to bring a per-share value reported for a period ending [periodEnd] to the share basis on [asOf]. */
    fun factor(periodEnd: LocalDate, asOf: LocalDate): BigDecimal = splits.filter { it.date > periodEnd && it.date <= asOf }
        .fold(BigDecimal.ONE) { f, s -> f.multiply(s.ratio) }
    companion object { val UNKNOWN = SplitHistory(verified = false) }
}

object FinancialMetricsEngine {
    private val MC = MathContext(16, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)
    /** Fundamentals older than this relative to the price date make price ratios stale (two quarters plus filing time). */
    const val MAX_FUNDAMENTALS_AGE_DAYS = 200L
    private const val SPLIT_UP = 1.8
    private const val SPLIT_DOWN = 0.55

    private fun out(v: BigDecimal) = v.setScale(6, RoundingMode.HALF_EVEN)
    private fun div(n: BigDecimal, d: BigDecimal) = n.divide(d, MC)
    private fun label(p: FinancialPeriod) = "${p.fiscalPeriod}${p.fiscalYear ?: ""} (${p.end})"
    private fun input(name: String, p: FinancialPeriod, v: ReportedValue) =
        MetricInput(name, v.value, label(p), v.concept, v.sourceRef, v.derived, v.restated)
    private fun none(m: Metric, s: MetricStatus, note: String, period: FinancialPeriod? = null, inputs: List<MetricInput> = emptyList()) =
        MetricResult(m, null, s, period?.let(::label), period?.end, inputs, note = note)
    private fun ok(m: Metric, v: BigDecimal, p: FinancialPeriod?, inputs: List<MetricInput>, approximate: Boolean = false, note: String? = null,
                   periodLabel: String? = null, asOf: LocalDate? = null) =
        MetricResult(m, out(v), MetricStatus.AVAILABLE, periodLabel ?: p?.let(::label), asOf ?: p?.end, inputs, approximate, note)

    private fun issueOrMissing(m: Metric, p: FinancialPeriod, vararg cs: FinancialConcept): MetricResult {
        val ambiguous = cs.firstOrNull { p.issues[it] == FactIssue.AMBIGUOUS }
        return if (ambiguous != null) none(m, MetricStatus.AMBIGUOUS, "Several different values were filed for ${ambiguous.name.lowercase()} in this period.", p)
        else none(m, MetricStatus.MISSING_INPUT, "Not reported for this period: ${cs.filter { p[it] == null }.joinToString { it.name.lowercase() }}.", p)
    }

    // ---- Growth ----------------------------------------------------------------------------------------------------------------

    /** Year-over-year growth of [c] between [current] and [prior] (same fiscal period kind, about one year apart). */
    fun growth(m: Metric, c: FinancialConcept, current: FinancialPeriod, prior: FinancialPeriod?): MetricResult {
        if (prior == null) return none(m, MetricStatus.INSUFFICIENT_HISTORY, "No comparable earlier period.", current)
        val cur = current[c]; val pre = prior[c]
        if (cur == null || pre == null) return issueOrMissing(m, if (cur == null) current else prior, c)
        if (current.fiscalPeriod != prior.fiscalPeriod || ChronoUnit.DAYS.between(prior.end, current.end) !in 350..380)
            return none(m, MetricStatus.PERIOD_MISMATCH, "The periods aren't the same fiscal period one year apart.", current)
        if (current.currency != prior.currency || cur.unit != pre.unit) return none(m, MetricStatus.CURRENCY_MISMATCH, "Different reporting currencies.", current)
        val inputs = listOf(input("current", current, cur), input("prior", prior, pre))
        if (cur.concept != pre.concept) return none(m, MetricStatus.UNRELIABLE_COMPARISON,
            "The company reported these periods under different line items (${pre.concept} → ${cur.concept}).", current, inputs)
        if (pre.value.signum() <= 0) return none(m, MetricStatus.NON_POSITIVE_DENOMINATOR, "Growth from a zero or negative base isn't meaningful.", current, inputs)
        return ok(m, div(cur.value - pre.value, pre.value) * HUNDRED, current, inputs)
    }

    /** The same fiscal period one year before [p] in [periods] (FY → FY, Q2 → Q2), or null. */
    fun priorYear(p: FinancialPeriod, periods: List<FinancialPeriod>): FinancialPeriod? = periods.firstOrNull {
        it !== p && it.fiscalPeriod == p.fiscalPeriod && ChronoUnit.DAYS.between(it.end, p.end) in 350..380
    }

    // ---- Margins ---------------------------------------------------------------------------------------------------------------

    fun margin(m: Metric, numerator: FinancialConcept, p: FinancialPeriod): MetricResult {
        val revenue = p[REVENUE] ?: return issueOrMissing(m, p, REVENUE)
        var approximateNote: String? = null
        val num: Pair<BigDecimal, List<MetricInput>> = p[numerator]?.let { it.value to listOf(input(numerator.name.lowercase(), p, it)) }
            ?: if (numerator == GROSS_PROFIT && p[COST_OF_REVENUE] != null) {
                approximateNote = "Gross profit computed as revenue − cost of revenue (not reported directly)."
                (revenue.value - p[COST_OF_REVENUE]!!.value) to listOf(input("cost_of_revenue", p, p[COST_OF_REVENUE]!!))
            } else return issueOrMissing(m, p, numerator)
        val inputs = num.second + input("revenue", p, revenue)
        if (revenue.value.signum() <= 0) return none(m, MetricStatus.NON_POSITIVE_DENOMINATOR, "Revenue is zero or negative.", p, inputs)
        return ok(m, div(num.first, revenue.value) * HUNDRED, p, inputs, note = approximateNote)
    }

    // ---- Trailing twelve months ------------------------------------------------------------------------------------------------

    data class Ttm(val value: BigDecimal?, val status: MetricStatus, val end: LocalDate?, val start: LocalDate?, val inputs: List<MetricInput>,
                   val approximate: Boolean = false, val note: String? = null, val periods: List<FinancialPeriod> = emptyList())

    /**
     * Trailing twelve months of [c]: the latest fiscal year when no quarter ends after it; otherwise the latest **four consecutive
     * discrete quarters** (84–98 days each, no gaps or overlaps over 7 days, one currency). Year-to-date values are never summed.
     * A missing Q4 per-share value may be taken as FY − Q1 − Q2 − Q3 of that fiscal year (approximate: share counts change).
     */
    fun ttm(h: CompanyFinancialHistory, c: FinancialConcept): Ttm {
        val latestFy = h.annual.firstOrNull()
        val latestQ = h.quarterly.firstOrNull()
        if (latestFy != null && (latestQ == null || !latestQ.end.isAfter(latestFy.end))) {
            val v = latestFy[c] ?: return Ttm(null, if (latestFy.issues[c] == FactIssue.AMBIGUOUS) MetricStatus.AMBIGUOUS else MetricStatus.MISSING_INPUT,
                latestFy.end, latestFy.start, emptyList(), note = "Not reported for the latest fiscal year.")
            return Ttm(v.value, MetricStatus.AVAILABLE, latestFy.end, latestFy.start, listOf(input(c.name.lowercase(), latestFy, v)), periods = listOf(latestFy))
        }
        val qs = h.quarterly.take(4)
        if (qs.size < 4) return Ttm(null, MetricStatus.INSUFFICIENT_HISTORY, latestQ?.end, null, emptyList(), note = "Fewer than four quarters reported.")
        for (q in qs) if ((q.days ?: 0) !in 84..98) return Ttm(null, MetricStatus.PERIOD_MISMATCH, latestQ?.end, null, emptyList(), note = "A period isn't a discrete quarter.")
        for ((newer, older) in qs.zipWithNext()) {
            val gap = ChronoUnit.DAYS.between(older.end, newer.start!!)
            if (gap !in 1..7) return Ttm(null, MetricStatus.PERIOD_MISMATCH, latestQ?.end, null, emptyList(), note = "The latest four quarters aren't consecutive.")
        }
        if (qs.map { it.currency }.distinct().size != 1) return Ttm(null, MetricStatus.CURRENCY_MISMATCH, latestQ?.end, null, emptyList(), note = "Different reporting currencies.")
        var sum = BigDecimal.ZERO; var approximate = false
        val inputs = mutableListOf<MetricInput>()
        for (q in qs) {
            val v = q[c]
            if (v != null) { sum += v.value; inputs += input(c.name.lowercase(), q, v); continue }
            if (q.issues[c] == FactIssue.AMBIGUOUS) return Ttm(null, MetricStatus.AMBIGUOUS, latestQ?.end, null, inputs, note = "Several values filed for one quarter.")
            val derived = (if (q.fiscalPeriod == "Q4" && !c.summable) perShareQ4(h, q, c) else null)
                ?: return Ttm(null, MetricStatus.MISSING_INPUT, latestQ?.end, null, inputs, note = "Not reported for ${label(q)}.")
            sum += derived.first; inputs += derived.second; approximate = true
        }
        return Ttm(sum, MetricStatus.AVAILABLE, qs.first().end, qs.last().start, inputs, approximate,
            if (approximate) "Fourth-quarter per-share value derived as the fiscal year less three quarters." else null, qs)
    }

    private fun perShareQ4(h: CompanyFinancialHistory, q4: FinancialPeriod, c: FinancialConcept): Pair<BigDecimal, List<MetricInput>>? {
        val fy = h.annual.firstOrNull { it.end == q4.end } ?: return null
        val fyValue = fy[c] ?: return null
        val earlier = h.quarterly.filter { it.end < q4.end && it.end > fy.start && it.fiscalPeriod in setOf("Q1", "Q2", "Q3") }
        if (earlier.map { it.fiscalPeriod }.toSet() != setOf("Q1", "Q2", "Q3") || earlier.size != 3) return null
        val vals = earlier.map { it[c] ?: return null }
        val v = fyValue.value - vals.fold(BigDecimal.ZERO) { a, b -> a + b.value }
        return v to listOf(MetricInput(c.name.lowercase(), v, label(q4), fyValue.concept, "${fyValue.sourceRef} − Q1–Q3", derived = true))
    }

    // ---- Per share and price ratios -------------------------------------------------------------------------------------------

    fun reported(m: Metric, c: FinancialConcept, p: FinancialPeriod): MetricResult =
        p[c]?.let { ok(m, it.value, p, listOf(input(c.name.lowercase(), p, it))) } ?: issueOrMissing(m, p, c)

    /** A share-count jump of a split-like factor between the periods [ttm] covers (and the latest annual one). */
    private fun splitSuspected(h: CompanyFinancialHistory, periods: List<FinancialPeriod>): Boolean {
        val counts = (periods + listOfNotNull(h.annual.firstOrNull())).sortedBy { it.end }.mapNotNull { it[DILUTED_SHARES]?.value?.toDouble()?.takeIf { v -> v > 0 } }
        return counts.zipWithNext().any { (a, b) -> (b / a).let { it >= SPLIT_UP || it <= SPLIT_DOWN } }
    }

    private fun priceCheck(m: Metric, price: PriceObservation, currency: String?, end: LocalDate?): MetricResult? = when {
        !price.usableForRatios -> MetricResult(m, null, MetricStatus.PRICE_UNUSABLE, note = "The price is stale, unknown or not positive (${price.delay.name.lowercase()}).")
        currency == null || price.currency != currency -> MetricResult(m, null, MetricStatus.CURRENCY_MISMATCH, note = "The price and the financial statements are in different currencies.")
        end == null || end.isAfter(price.date) || ChronoUnit.DAYS.between(end, price.date) > MAX_FUNDAMENTALS_AGE_DAYS ->
            MetricResult(m, null, MetricStatus.PERIOD_MISMATCH, note = "The latest reported period is too old (or newer) for this price date.")
        else -> null
    }

    private fun splitAdjusted(m: Metric, h: CompanyFinancialHistory, ttm: Ttm, price: PriceObservation, splits: SplitHistory): Pair<BigDecimal?, MetricResult?> {
        if (!splits.verified && splitSuspected(h, ttm.periods))
            return null to MetricResult(m, null, MetricStatus.SPLIT_UNVERIFIED, note = "The share count changed by a split-like factor and no verified split history is available.")
        // Bring each period's per-share value to the price date's share basis.
        var total = BigDecimal.ZERO
        for (p in ttm.periods) {
            val v = ttm.inputs.firstOrNull { it.period == label(p) }?.value ?: return null to MetricResult(m, null, MetricStatus.MISSING_INPUT, note = "Missing input.")
            total += div(v, splits.factor(p.end, price.date))
        }
        return total to null
    }

    fun epsTtm(h: CompanyFinancialHistory): MetricResult {
        val t = ttm(h, EPS_DILUTED)
        if (t.value == null) return MetricResult(Metric.EPS_DILUTED_TTM, null, t.status, note = t.note, inputs = t.inputs)
        return MetricResult(Metric.EPS_DILUTED_TTM, out(t.value), MetricStatus.AVAILABLE, "TTM to ${t.end}", t.end, t.inputs, t.approximate, t.note)
    }

    /** P/E = price ÷ TTM diluted EPS. Negative or zero EPS → NOT_MEANINGFUL (never a negative P/E). */
    fun pe(h: CompanyFinancialHistory, price: PriceObservation, splits: SplitHistory = SplitHistory.UNKNOWN): MetricResult {
        val m = Metric.PE_RATIO
        val t = ttm(h, EPS_DILUTED)
        if (t.value == null) return MetricResult(m, null, t.status, inputs = t.inputs, note = t.note)
        priceCheck(m, price, h.annual.firstOrNull()?.currency ?: h.quarterly.firstOrNull()?.currency, t.end)?.let { return it }
        val (eps, problem) = splitAdjusted(m, h, t, price, splits)
        if (problem != null) return problem.copy(inputs = t.inputs)
        val inputs = t.inputs + MetricInput("price", price.value, price.date.toString(), null, price.provenance.provider)
        if (eps!!.signum() <= 0) return MetricResult(m, null, MetricStatus.NOT_MEANINGFUL, "TTM to ${t.end}", t.end, inputs, note = "Earnings are zero or negative, so P/E isn't meaningful.")
        return ok(m, div(price.value, eps), null, inputs, t.approximate,
            listOfNotNull(t.note, if (!splits.verified) "Split history not verified." else null).joinToString(" ").ifBlank { null }, "TTM to ${t.end}", price.date)
    }

    /** P/S = price × latest cover-page shares outstanding ÷ TTM revenue (single share class only). */
    fun ps(h: CompanyFinancialHistory, price: PriceObservation): MetricResult {
        val m = Metric.PS_RATIO
        if (h.sharesOutstandingIssue == FactIssue.AMBIGUOUS) return MetricResult(m, null, MetricStatus.AMBIGUOUS, note = "Several share classes are reported; one share count can't be used.")
        val shares = h.sharesOutstanding.firstOrNull { !it.first.isAfter(price.date) }
            ?: return MetricResult(m, null, MetricStatus.MISSING_INPUT, note = "No shares-outstanding figure on or before the price date.")
        if (ChronoUnit.DAYS.between(shares.first, price.date) > MAX_FUNDAMENTALS_AGE_DAYS) return MetricResult(m, null, MetricStatus.PERIOD_MISMATCH, note = "The share count is too old.")
        val t = ttm(h, REVENUE)
        if (t.value == null) return MetricResult(m, null, t.status, inputs = t.inputs, note = t.note)
        priceCheck(m, price, h.annual.firstOrNull()?.currency ?: h.quarterly.firstOrNull()?.currency, t.end)?.let { return it }
        val inputs = t.inputs + MetricInput("shares_outstanding", shares.second.value, shares.first.toString(), shares.second.concept, shares.second.sourceRef) +
            MetricInput("price", price.value, price.date.toString(), null, price.provenance.provider)
        if (t.value.signum() <= 0) return MetricResult(m, null, MetricStatus.NON_POSITIVE_DENOMINATOR, inputs = inputs, note = "Revenue is zero or negative.")
        return ok(m, div(price.value * shares.second.value, t.value), null, inputs, t.approximate, t.note, "TTM to ${t.end}", price.date)
    }

    /** Dividend yield = TTM dividends declared per share ÷ price. No dividend facts → MISSING_INPUT (absence isn't "no dividend"). */
    fun dividendYield(h: CompanyFinancialHistory, price: PriceObservation, splits: SplitHistory = SplitHistory.UNKNOWN): MetricResult {
        val m = Metric.DIVIDEND_YIELD
        val t = ttm(h, DIVIDENDS_PER_SHARE)
        if (t.value == null) return MetricResult(m, null, t.status, inputs = t.inputs, note = t.note ?: "No dividend per share reported.")
        priceCheck(m, price, h.annual.firstOrNull()?.currency ?: h.quarterly.firstOrNull()?.currency, t.end)?.let { return it }
        val (dps, problem) = splitAdjusted(m, h, t, price, splits)
        if (problem != null) return problem.copy(inputs = t.inputs)
        if (dps!!.signum() < 0) return MetricResult(m, null, MetricStatus.NOT_MEANINGFUL, inputs = t.inputs, note = "Negative dividends per share.")
        return ok(m, div(dps, price.value) * HUNDRED, null, t.inputs + MetricInput("price", price.value, price.date.toString(), null, price.provenance.provider),
            t.approximate, t.note, "TTM to ${t.end}", price.date)
    }

    // ---- Balance sheet, returns, cash flow ------------------------------------------------------------------------------------

    fun debtToEquity(p: FinancialPeriod): MetricResult {
        val m = Metric.DEBT_TO_EQUITY
        val equity = p[EQUITY] ?: return issueOrMissing(m, p, EQUITY)
        val longTerm: List<Pair<String, ReportedValue>> = p[LONG_TERM_DEBT]?.let { listOf("long_term_debt" to it) }
            ?: if (p[LONG_TERM_DEBT_CURRENT] != null && p[LONG_TERM_DEBT_NONCURRENT] != null)
                listOf("long_term_debt_current" to p[LONG_TERM_DEBT_CURRENT]!!, "long_term_debt_noncurrent" to p[LONG_TERM_DEBT_NONCURRENT]!!)
            else return none(m, MetricStatus.MISSING_INPUT, "Long-term debt isn't reported in a form that can be totalled (absence isn't zero debt).", p)
        val parts = longTerm + listOfNotNull(p[SHORT_TERM_BORROWINGS]?.let { "short_term_borrowings" to it })
        val inputs = parts.map { (n, v) -> input(n, p, v) } + input("equity", p, equity)
        if (equity.value.signum() <= 0) return none(m, MetricStatus.NON_POSITIVE_DENOMINATOR, "Stockholders' equity is zero or negative.", p, inputs)
        val debt = parts.fold(BigDecimal.ZERO) { a, (_, v) -> a + v.value }
        return ok(m, div(debt, equity.value), p, inputs, note = if (p[SHORT_TERM_BORROWINGS] == null) "Short-term borrowings not reported separately." else null)
    }

    fun currentRatio(p: FinancialPeriod): MetricResult {
        val m = Metric.CURRENT_RATIO
        val ca = p[CURRENT_ASSETS]; val cl = p[CURRENT_LIABILITIES]
        if (ca == null || cl == null) return issueOrMissing(m, p, CURRENT_ASSETS, CURRENT_LIABILITIES)
        val inputs = listOf(input("current_assets", p, ca), input("current_liabilities", p, cl))
        if (cl.value.signum() <= 0) return none(m, MetricStatus.NON_POSITIVE_DENOMINATOR, "Current liabilities are zero or negative.", p, inputs)
        return ok(m, div(ca.value, cl.value), p, inputs)
    }

    /** ROE / ROA on a fiscal year: net income ÷ average of the opening (prior year end) and closing balance. Both balances required. */
    fun returnOn(m: Metric, current: FinancialPeriod, prior: FinancialPeriod?): MetricResult {
        require(m == Metric.RETURN_ON_EQUITY || m == Metric.RETURN_ON_ASSETS)
        val base = if (m == Metric.RETURN_ON_EQUITY) EQUITY else TOTAL_ASSETS
        if (!current.annual) return none(m, MetricStatus.PERIOD_MISMATCH, "Returns are computed on fiscal years only (quarters aren't annualized).", current)
        val ni = current[NET_INCOME] ?: return issueOrMissing(m, current, NET_INCOME)
        val closing = current[base] ?: return issueOrMissing(m, current, base)
        if (prior == null || ChronoUnit.DAYS.between(prior.end, current.end) !in 350..380) return none(m, MetricStatus.INSUFFICIENT_HISTORY, "No opening balance from the prior fiscal year.", current)
        val opening = prior[base] ?: return issueOrMissing(m, prior, base)
        if (current.currency != prior.currency) return none(m, MetricStatus.CURRENCY_MISMATCH, "Different reporting currencies.", current)
        val inputs = listOf(input("net_income", current, ni), input("opening_${base.name.lowercase()}", prior, opening), input("closing_${base.name.lowercase()}", current, closing))
        val avg = div(opening.value + closing.value, BigDecimal(2))
        if (avg.signum() <= 0) return none(m, MetricStatus.NON_POSITIVE_DENOMINATOR, "The average ${base.name.lowercase()} is zero or negative.", current, inputs)
        return ok(m, div(ni.value, avg) * HUNDRED, current, inputs)
    }

    /** FCF = operating cash flow − capital expenditure, both reported for [p]. Negative FCF is a valid value. */
    fun freeCashFlow(p: FinancialPeriod): MetricResult {
        val m = Metric.FREE_CASH_FLOW
        val ocf = p[OPERATING_CASH_FLOW]; val capex = p[CAPITAL_EXPENDITURE]
        if (ocf == null || capex == null) return issueOrMissing(m, p, OPERATING_CASH_FLOW, CAPITAL_EXPENDITURE)
        if (capex.value.signum() < 0) return none(m, MetricStatus.NOT_MEANINGFUL, "Capital expenditure is reported as a negative payment.", p)
        return ok(m, ocf.value - capex.value, p, listOf(input("operating_cash_flow", p, ocf), input("capital_expenditure", p, capex)))
    }

    /** Every metric for the latest fiscal year (and TTM price ratios when [price] is given). */
    fun all(h: CompanyFinancialHistory, price: PriceObservation? = null, splits: SplitHistory = SplitHistory.UNKNOWN): Map<Metric, MetricResult> {
        val fy = h.annual.firstOrNull() ?: return emptyMap()
        val prior = priorYear(fy, h.annual)
        val r = linkedMapOf(
            Metric.REVENUE_GROWTH to growth(Metric.REVENUE_GROWTH, REVENUE, fy, prior),
            Metric.NET_INCOME_GROWTH to growth(Metric.NET_INCOME_GROWTH, NET_INCOME, fy, prior),
            Metric.EPS_GROWTH to growth(Metric.EPS_GROWTH, EPS_DILUTED, fy, prior),
            Metric.GROSS_MARGIN to margin(Metric.GROSS_MARGIN, GROSS_PROFIT, fy),
            Metric.OPERATING_MARGIN to margin(Metric.OPERATING_MARGIN, OPERATING_INCOME, fy),
            Metric.NET_MARGIN to margin(Metric.NET_MARGIN, NET_INCOME, fy),
            Metric.EPS_BASIC to reported(Metric.EPS_BASIC, EPS_BASIC, fy),
            Metric.EPS_DILUTED to reported(Metric.EPS_DILUTED, EPS_DILUTED, fy),
            Metric.EPS_DILUTED_TTM to epsTtm(h),
            Metric.DEBT_TO_EQUITY to debtToEquity(fy),
            Metric.CURRENT_RATIO to currentRatio(fy),
            Metric.RETURN_ON_EQUITY to returnOn(Metric.RETURN_ON_EQUITY, fy, prior),
            Metric.RETURN_ON_ASSETS to returnOn(Metric.RETURN_ON_ASSETS, fy, prior),
            Metric.FREE_CASH_FLOW to freeCashFlow(fy)
        )
        if (price != null) {
            r[Metric.PE_RATIO] = pe(h, price, splits)
            r[Metric.PS_RATIO] = ps(h, price)
            r[Metric.DIVIDEND_YIELD] = dividendYield(h, price, splits)
        }
        return r
    }
}
