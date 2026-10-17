package org.example.stocksteps.marketdata

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 9B: provider-independent reported fundamentals (the input of [org.example.stocksteps.marketdata.metrics.FinancialMetricsEngine]).
 * Values are exact decimals in their reported unit; nothing here is converted, estimated or defaulted to zero.
 */
enum class FinancialConcept(val kind: Kind, val unit: String) {
    REVENUE(Kind.FLOW, "USD"), COST_OF_REVENUE(Kind.FLOW, "USD"), GROSS_PROFIT(Kind.FLOW, "USD"), OPERATING_INCOME(Kind.FLOW, "USD"),
    NET_INCOME(Kind.FLOW, "USD"), EPS_BASIC(Kind.PER_SHARE, "USD/shares"), EPS_DILUTED(Kind.PER_SHARE, "USD/shares"),
    DILUTED_SHARES(Kind.FLOW_SHARES, "shares"), OPERATING_CASH_FLOW(Kind.FLOW, "USD"), CAPITAL_EXPENDITURE(Kind.FLOW, "USD"),
    DIVIDENDS_PER_SHARE(Kind.PER_SHARE, "USD/shares"),
    TOTAL_ASSETS(Kind.INSTANT, "USD"), TOTAL_LIABILITIES(Kind.INSTANT, "USD"), EQUITY(Kind.INSTANT, "USD"), CURRENT_ASSETS(Kind.INSTANT, "USD"),
    CURRENT_LIABILITIES(Kind.INSTANT, "USD"), CASH(Kind.INSTANT, "USD"), LONG_TERM_DEBT(Kind.INSTANT, "USD"),
    LONG_TERM_DEBT_CURRENT(Kind.INSTANT, "USD"), LONG_TERM_DEBT_NONCURRENT(Kind.INSTANT, "USD"), SHORT_TERM_BORROWINGS(Kind.INSTANT, "USD"),
    SHARES_OUTSTANDING(Kind.INSTANT_SHARES, "shares");

    /** FLOW values can be summed over periods (and Q4 derived as FY − 9-month YTD); PER_SHARE and share counts can't be subtracted. */
    enum class Kind { FLOW, PER_SHARE, FLOW_SHARES, INSTANT, INSTANT_SHARES }
    val summable: Boolean get() = kind == Kind.FLOW
    val instant: Boolean get() = kind == Kind.INSTANT || kind == Kind.INSTANT_SHARES
}

/**
 * One reported value with its origin. [concept] is the source's own tag (e.g. `us-gaap:Revenues`) so a series that switches tags is
 * detectable; [originalValue] is the first-filed value when a later filing restated it; [derived] marks values StockSteps computed
 * from reported ones (Q4 = FY − 9-month YTD), never presented as reported.
 */
data class ReportedValue(
    val value: BigDecimal,
    val unit: String,
    val concept: String,
    val provider: String,
    val sourceRef: String? = null,
    val filed: LocalDate? = null,
    val form: String? = null,
    val originalValue: BigDecimal? = null,
    val amended: Boolean = false,
    val derived: Boolean = false
) {
    val restated: Boolean get() = originalValue != null && originalValue.compareTo(value) != 0
}

/** Why a value for a period isn't usable (kept instead of a value; never replaced by a guess). */
enum class FactIssue { AMBIGUOUS, NON_COMPARABLE_UNIT }

/**
 * One fiscal period: "FY", "Q1"…"Q4" (or "TTM" for a computed trailing period). [start] is null for a balance-sheet-only period.
 * [currency] is the reporting currency of its monetary values (SEC US-GAAP companyfacts: the `USD` unit).
 */
data class FinancialPeriod(
    val fiscalYear: Int?,
    val fiscalPeriod: String,
    val start: LocalDate?,
    val end: LocalDate,
    val currency: String?,
    val values: Map<FinancialConcept, ReportedValue>,
    val issues: Map<FinancialConcept, FactIssue> = emptyMap()
) {
    val days: Long? get() = start?.let { ChronoUnit.DAYS.between(it, end) + 1 }
    val annual: Boolean get() = fiscalPeriod == "FY"
    val quarter: Boolean get() = fiscalPeriod.length == 2 && fiscalPeriod[0] == 'Q'
    operator fun get(c: FinancialConcept): ReportedValue? = values[c]
}

/** A company's reported periods (newest first within each list) from one provider, with the provenance of the whole set. */
data class CompanyFinancialHistory(
    val symbol: String,
    val annual: List<FinancialPeriod>,
    val quarterly: List<FinancialPeriod>,
    val provenance: Provenance,
    /** Instant facts that aren't tied to a statement period (e.g. cover-page shares outstanding), newest first. */
    val sharesOutstanding: List<Pair<LocalDate, ReportedValue>> = emptyList(),
    val sharesOutstandingIssue: FactIssue? = null
)
