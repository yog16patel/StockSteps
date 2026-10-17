package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.marketdata.AdjustmentBasis
import org.example.stocksteps.marketdata.CompanyFinancialHistory
import org.example.stocksteps.marketdata.Dataset
import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.FinancialPeriod
import org.example.stocksteps.marketdata.PriceDelay
import org.example.stocksteps.marketdata.PriceObservation
import org.example.stocksteps.marketdata.PriceSeriesRules
import org.example.stocksteps.marketdata.Provenance
import org.example.stocksteps.marketdata.ReportedValue
import org.example.stocksteps.marketdata.metrics.FinancialMetricsEngine
import org.example.stocksteps.marketdata.metrics.Metric
import org.example.stocksteps.marketdata.metrics.MetricResult
import org.example.stocksteps.marketdata.metrics.MetricStatus
import org.example.stocksteps.marketdata.metrics.SplitHistory
import org.example.stocksteps.model.CompanyFinancials
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyValuation
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import org.example.stocksteps.model.FinancialFact
import org.example.stocksteps.model.FinancialSource
import org.example.stocksteps.model.HistoricalComparison
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repository.StatementHistory
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.FinancialCalculations
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 9B.8: the `CompanyFundamentals` bundle (same contract and metric ids as the FMP mapper) from Alpha Vantage statements and dividends,
 * every ratio computed by [FinancialMetricsEngine] on verified inputs. Inputs that can't be verified from Alpha Vantage's documentation stay
 * **unavailable with a reason**, never estimated:
 * - EPS (diluted or basic), P/E, EPS growth: not in the statements; `EARNINGS.reportedEPS` and OVERVIEW EPS don't document GAAP vs
 *   adjusted or diluted vs basic;
 * - shares, P/S, P/B, P/FCF, EV, EV/EBITDA, PEG, forward P/E, valuation history: no share count with a verified basis; no estimates;
 * - quarterly figures from a statement whose quarters aren't verified standalone (some providers report year-to-date cash flow): checked
 *   per company ([quarterBasis]: quarters must add up to the fiscal year), else left out.
 */
object AlphaVantageFundamentalsMapper {
    enum class QuarterBasis { STANDALONE, YEAR_TO_DATE, UNVERIFIED }

    private val INCOME = mapOf(FinancialConcept.REVENUE to "totalRevenue", FinancialConcept.COST_OF_REVENUE to "costOfRevenue",
        FinancialConcept.GROSS_PROFIT to "grossProfit", FinancialConcept.OPERATING_INCOME to "operatingIncome", FinancialConcept.NET_INCOME to "netIncome")
    private val BALANCE = mapOf(FinancialConcept.TOTAL_ASSETS to "totalAssets", FinancialConcept.TOTAL_LIABILITIES to "totalLiabilities",
        FinancialConcept.EQUITY to "totalShareholderEquity", FinancialConcept.CURRENT_ASSETS to "totalCurrentAssets",
        FinancialConcept.CURRENT_LIABILITIES to "totalCurrentLiabilities", FinancialConcept.CASH to "cashAndCashEquivalentsAtCarryingValue",
        FinancialConcept.TOTAL_DEBT to "shortLongTermDebtTotal")
    private val CASH = mapOf(FinancialConcept.OPERATING_CASH_FLOW to "operatingCashflow", FinancialConcept.CAPITAL_EXPENDITURE to "capitalExpenditures")

    /** One fiscal year's check: its four quarters' sum vs the annual value (null when the year isn't complete). */
    data class YearCheck(val fiscalYearEnd: LocalDate, val quarters: Int, val sum: BigDecimal?, val annual: BigDecimal?, val verdict: QuarterBasis?) {
        /** Quarter sum ÷ annual (1.0 = standalone quarters), for diagnostics. */
        val ratio: BigDecimal? get() = if (sum != null && annual != null && annual.signum() != 0) sum.divide(annual, java.math.MathContext(6)) else null
    }

    /**
     * Which quarterly values of [field] may be used. Phase 9B.9A (owner decision D-9B9A-6): the verdict comes from the [recentYears] most
     * recent **complete** fiscal years (annual value + four quarters ending in it): all standalone (quarters add up to the year within 1 %)
     * → STANDALONE; the fourth quarter equals the year → YEAR_TO_DATE; otherwise (or no complete year) → UNVERIFIED. Only with STANDALONE
     * are quarters trusted, and then only those of years that pass the check themselves (any age) plus the quarters of the fiscal year in
     * progress after the latest annual report (it can't be checked until it closes; it continues the verified series). An older year that
     * disagrees no longer refuses everything, but its own quarters stay untrusted.
     */
    data class QuarterTrust(val basis: QuarterBasis, val trusted: Set<LocalDate>, val years: List<YearCheck>, val recentChecked: List<LocalDate>)

    fun quarterTrust(s: AlphaVantageParsers.Statement?, field: String, recentYears: Int = RECENT_YEARS): QuarterTrust {
        s ?: return QuarterTrust(QuarterBasis.UNVERIFIED, emptySet(), emptyList(), emptyList())
        fun close(a: BigDecimal, b: BigDecimal) = (a - b).abs() <= b.abs().multiply(BigDecimal("0.01"))
        val windows = s.annual.sortedByDescending { it.fiscalDateEnding }.map { year ->
            val start = year.fiscalDateEnding.minusMonths(12)
            year to s.quarterly.filter { it.fiscalDateEnding.isAfter(start.plusDays(7)) && !it.fiscalDateEnding.isAfter(year.fiscalDateEnding.plusDays(7)) }
        }
        val years = windows.map { (year, qs) ->
            val total = year.values[field]
            val complete = total != null && qs.size == 4 && qs.all { it.values[field] != null }
            if (!complete) YearCheck(year.fiscalDateEnding, qs.size, null, total, null) else {
                val sum = qs.fold(BigDecimal.ZERO) { a, r -> a + r.values.getValue(field) }
                val last = qs.maxBy { it.fiscalDateEnding }.values.getValue(field)
                YearCheck(year.fiscalDateEnding, 4, sum, total, when { close(sum, total!!) -> QuarterBasis.STANDALONE; close(last, total) -> QuarterBasis.YEAR_TO_DATE; else -> QuarterBasis.UNVERIFIED })
            }
        }
        val recent = years.filter { it.verdict != null }.take(recentYears)
        val basis = when {
            recent.isEmpty() -> QuarterBasis.UNVERIFIED
            recent.all { it.verdict == QuarterBasis.STANDALONE } -> QuarterBasis.STANDALONE
            recent.any { it.verdict == QuarterBasis.YEAR_TO_DATE } -> QuarterBasis.YEAR_TO_DATE
            else -> QuarterBasis.UNVERIFIED
        }
        if (basis != QuarterBasis.STANDALONE) return QuarterTrust(basis, emptySet(), years, recent.map { it.fiscalYearEnd })
        val passing = years.filter { it.verdict == QuarterBasis.STANDALONE }.map { it.fiscalYearEnd }.toSet()
        val latestYearEnd = s.annual.maxOf { it.fiscalDateEnding }
        val trusted = windows.filter { (year, _) -> year.fiscalDateEnding in passing }.flatMap { (_, qs) -> qs.map { it.fiscalDateEnding } }.toSet() +
            s.quarterly.filter { it.fiscalDateEnding.isAfter(latestYearEnd.plusDays(7)) }.map { it.fiscalDateEnding }
        return QuarterTrust(basis, trusted, years, recent.map { it.fiscalYearEnd })
    }

    /** The statement-level verdict (see [quarterTrust]). */
    fun quarterBasis(s: AlphaVantageParsers.Statement?, field: String): QuarterBasis = quarterTrust(s, field).basis

    /** Phase 9B.9A: complete fiscal years checked for the quarterly basis (owner decision D-9B9A-6). */
    const val RECENT_YEARS = 3

    /** Fiscal periods for the metrics engine (annual and verified-standalone quarters), provider `alphavantage`, tags `av:<field>`. */
    fun history(symbol: String, income: AlphaVantageParsers.Statement?, balance: AlphaVantageParsers.Statement?, cash: AlphaVantageParsers.Statement?,
                retrievedAt: Instant): CompanyFinancialHistory {
        val fyMonth = income?.annual?.map { AlphaVantageStatements.periodMonth(it.fiscalDateEnding) }?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
        val incomeTrusted = quarterTrust(income, "totalRevenue").trusted
        val cashTrusted = quarterTrust(cash, "operatingCashflow").trusted
        fun rv(v: BigDecimal, field: String, currency: String?) = ReportedValue(v, currency ?: "", "av:$field", "alphavantage")
        fun periods(quarterly: Boolean): List<FinancialPeriod> {
            val inc = (if (quarterly) income?.quarterly else income?.annual).orEmpty()
            val bal = (if (quarterly) balance?.quarterly else balance?.annual).orEmpty().associateBy { it.fiscalDateEnding }
            val cf = (if (quarterly) cash?.quarterly else cash?.annual).orEmpty().associateBy { it.fiscalDateEnding }
            val sorted = inc.sortedBy { it.fiscalDateEnding }
            return sorted.mapIndexedNotNull { i, r ->
                val label = if (!quarterly) "FY" else AlphaVantageStatements.quarterLabel(r.fiscalDateEnding, fyMonth) ?: return@mapIndexedNotNull null
                val previousEnd = sorted.getOrNull(i - 1)?.fiscalDateEnding
                val start = (previousEnd?.takeIf { ChronoUnit.DAYS.between(it, r.fiscalDateEnding) in (if (quarterly) 80L..100L else 350L..380L) }
                    ?: r.fiscalDateEnding.minusMonths(if (quarterly) 3 else 12)).plusDays(1)
                val values = HashMap<FinancialConcept, ReportedValue>()
                if (!quarterly || r.fiscalDateEnding in incomeTrusted) INCOME.forEach { (c, f) -> r.values[f]?.let { values[c] = rv(it, f, r.currency) } }
                bal[r.fiscalDateEnding]?.takeIf { it.currency == r.currency }?.let { b -> BALANCE.forEach { (c, f) -> b.values[f]?.let { values[c] = rv(it, f, b.currency) } } }
                if (!quarterly || r.fiscalDateEnding in cashTrusted) cf[r.fiscalDateEnding]?.takeIf { it.currency == r.currency }?.let { c -> CASH.forEach { (k, f) ->
                    c.values[f]?.let { v -> values[k] = rv(if (k == FinancialConcept.CAPITAL_EXPENDITURE) v.abs() else v, f, c.currency) } } }
                val fy = if (!quarterly || fyMonth == null) r.fiscalDateEnding.year.let { if (r.fiscalDateEnding.dayOfMonth <= 7 && r.fiscalDateEnding.monthValue == 1) it - 1 else it }
                    else AlphaVantageStatements.fiscalYearOf(r.fiscalDateEnding, fyMonth)
                FinancialPeriod(fy, label, start, r.fiscalDateEnding, r.currency, values)
            }.sortedByDescending { it.end }
        }
        return CompanyFinancialHistory(symbol, periods(false), periods(true),
            Provenance("alphavantage", Dataset.REPORTED_FUNDAMENTALS, retrievedAt, (income?.annual.orEmpty() + income?.quarterly.orEmpty()).maxOfOrNull { it.fiscalDateEnding }?.toString()))
    }

    /** Engine status → the contract's availability (no new enum values for older apps). */
    fun availability(s: MetricStatus): FinancialAvailability = when (s) {
        MetricStatus.AVAILABLE -> FinancialAvailability.AVAILABLE
        MetricStatus.MISSING_INPUT -> FinancialAvailability.MISSING
        MetricStatus.NON_POSITIVE_DENOMINATOR, MetricStatus.NOT_MEANINGFUL -> FinancialAvailability.NON_POSITIVE_DENOMINATOR
        MetricStatus.PERIOD_MISMATCH -> FinancialAvailability.PERIOD_MISMATCH
        MetricStatus.CURRENCY_MISMATCH, MetricStatus.UNRELIABLE_COMPARISON, MetricStatus.SPLIT_UNVERIFIED -> FinancialAvailability.UNRELIABLE_COMPARISON
        MetricStatus.INSUFFICIENT_HISTORY -> FinancialAvailability.INSUFFICIENT_HISTORY
        MetricStatus.PRICE_UNUSABLE -> FinancialAvailability.TEMPORARILY_UNAVAILABLE
        MetricStatus.AMBIGUOUS -> FinancialAvailability.INVALID_VALUE
    }

    private fun basis(p: FinancialPeriod?, label: String? = null) =
        p?.let { FinancialBasis(label ?: if (it.annual) "annual" else it.fiscalPeriod, it.end.toString(), it.fiscalYear, it.currency) }

    private fun fromMetric(r: MetricResult, p: FinancialPeriod?, money: Boolean = false, basisLabel: String? = null): FinancialFact {
        val b = r.asOf?.let { d -> FinancialBasis(basisLabel ?: p?.let { if (it.annual) "annual" else it.fiscalPeriod } ?: (r.period ?: "TTM"), d.toString(), p?.fiscalYear, p?.currency) } ?: basis(p, basisLabel)
        if (r.value == null) return FinancialFact(availability = availability(r.status), basis = b, note = r.note)
        val v = r.value.toDouble()
        return if (money) FinancialFact(amount = r.value.toLong(), source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE, basis = b, note = r.note)
        else FinancialFact(value = v, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE, basis = b, note = r.note)
    }

    private fun reported(p: FinancialPeriod?, c: FinancialConcept): FinancialFact {
        val v = p?.get(c) ?: return FinancialFact(availability = FinancialAvailability.MISSING, basis = basis(p))
        return FinancialFact(amount = v.value.toLong(), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = basis(p))
    }

    private fun unavailable(note: String, p: FinancialPeriod? = null) = FinancialFact(availability = FinancialAvailability.MISSING, basis = basis(p), note = note)

    private const val NO_EPS = "Not available from this source: Alpha Vantage statements carry no EPS, and its EPS figures don't state GAAP vs adjusted or diluted vs basic."
    private const val NO_SHARES = "Not available from this source: needs a share count with a verified basis."
    private const val NO_ESTIMATES = "Not available from this source (no analyst estimates are used)."

    /**
     * The bundle for [period] ("annual" | "quarter"). Ratios for a quarter request that need a full year (ROE, ROA, payout) use the latest
     * fiscal year and say so in their basis. [price] (dated, labelled) feeds the dividend yield only; null → price ratios unavailable.
     */
    fun map(symbol: String, period: String, income: AlphaVantageParsers.Statement?, balance: AlphaVantageParsers.Statement?, cash: AlphaVantageParsers.Statement?,
            dividends: List<AlphaVantageParsers.Dividend>?, splits: List<AlphaVantageParsers.Split>?, price: PriceObservation?, today: LocalDate, retrievedAt: Instant,
            availability: Map<String, FinancialAvailability>): CompanyFundamentals {
        val h = history(symbol, income, balance, cash, retrievedAt)
        val quarterly = period == "quarter"
        val list = if (quarterly) h.quarterly else h.annual
        val latest = list.firstOrNull { !it.end.isAfter(today) }
        val prior = latest?.let { FinancialMetricsEngine.priorYear(it, list) }
        val fy = h.annual.firstOrNull { !it.end.isAfter(today) }
        val fyPrior = fy?.let { FinancialMetricsEngine.priorYear(it, h.annual) }
        val E = FinancialMetricsEngine
        val annualRevenue = h.annual.sortedByDescending { it.end }
        fun cagr(n: Int): FinancialFact {
            val first = annualRevenue.firstOrNull() ?: return unavailable("No annual revenue.")
            val base = annualRevenue.firstOrNull { ChronoUnit.DAYS.between(it.end, first.end) in (365L * n - 10)..(365L * n + 10) }
            val r = if (base != null && base.currency == first.currency && base[FinancialConcept.REVENUE]?.concept == first[FinancialConcept.REVENUE]?.concept)
                FinancialCalculations.cagr(first[FinancialConcept.REVENUE]?.value?.toDouble(), base[FinancialConcept.REVENUE]?.value?.toDouble(), n) else null
            return if (r != null) FinancialFact(value = r, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE, basis = basis(first, "annual"))
                else FinancialFact(availability = FinancialAvailability.INSUFFICIENT_HISTORY, basis = basis(first, "annual"))
        }
        val growth = linkedMapOf(
            "revenue" to reported(latest, FinancialConcept.REVENUE),
            "revenueGrowth" to (latest?.let { fromMetric(E.growth(Metric.REVENUE_GROWTH, FinancialConcept.REVENUE, it, prior), it) } ?: unavailable("No reported period.")),
            "revenueCagr3" to cagr(3), "revenueCagr5" to cagr(5),
            "netIncome" to reported(latest, FinancialConcept.NET_INCOME),
            "netIncomeGrowth" to (latest?.let { fromMetric(E.growth(Metric.NET_INCOME_GROWTH, FinancialConcept.NET_INCOME, it, prior), it) } ?: unavailable("No reported period.")),
            "eps" to unavailable(NO_EPS, latest), "epsGrowth" to unavailable(NO_EPS, latest), "epsCagr3" to unavailable(NO_EPS), "epsCagr5" to unavailable(NO_EPS)
        )
        val profitability = linkedMapOf(
            "grossMargin" to (latest?.let { fromMetric(E.margin(Metric.GROSS_MARGIN, FinancialConcept.GROSS_PROFIT, it), it) } ?: unavailable("No reported period.")),
            "operatingMargin" to (latest?.let { fromMetric(E.margin(Metric.OPERATING_MARGIN, FinancialConcept.OPERATING_INCOME, it), it) } ?: unavailable("No reported period.")),
            "netMargin" to (latest?.let { fromMetric(E.margin(Metric.NET_MARGIN, FinancialConcept.NET_INCOME, it), it) } ?: unavailable("No reported period.")),
            "roe" to (fy?.let { fromMetric(E.returnOn(Metric.RETURN_ON_EQUITY, it, fyPrior), it, basisLabel = "annual") } ?: unavailable("No fiscal year.")),
            "roa" to (fy?.let { fromMetric(E.returnOn(Metric.RETURN_ON_ASSETS, it, fyPrior), it, basisLabel = "annual") } ?: unavailable("No fiscal year.")),
            "roic" to unavailable("Not available from this source (invested capital isn't reported).")
        )
        val sheet = list.firstOrNull { !it.end.isAfter(today) && (it[FinancialConcept.TOTAL_ASSETS] != null || it[FinancialConcept.EQUITY] != null) }
        val debt = sheet?.get(FinancialConcept.TOTAL_DEBT)?.value; val cashValue = sheet?.get(FinancialConcept.CASH)?.value
        val ebit = income?.let { s -> (if (quarterly) s.quarterly else s.annual).firstOrNull { it.fiscalDateEnding == latest?.end } }
        val interest = FinancialCalculations.divide(ebit?.values?.get("ebit")?.toDouble(), ebit?.values?.get("interestExpense")?.toDouble())
        val health = linkedMapOf(
            "cash" to reported(sheet, FinancialConcept.CASH), "debt" to reported(sheet, FinancialConcept.TOTAL_DEBT),
            "assets" to reported(sheet, FinancialConcept.TOTAL_ASSETS), "liabilities" to reported(sheet, FinancialConcept.TOTAL_LIABILITIES),
            "equity" to reported(sheet, FinancialConcept.EQUITY),
            "netDebt" to if (debt != null && cashValue != null) FinancialFact(amount = (debt - cashValue).toLong(), source = FinancialSource.BACKEND_CALCULATED,
                availability = FinancialAvailability.AVAILABLE, basis = basis(sheet), note = "Reported total debt less cash and cash equivalents.") else unavailable("Debt or cash not reported.", sheet),
            "debtEquity" to (sheet?.let { fromMetric(E.debtToEquity(it), it) } ?: unavailable("No balance sheet.")),
            "currentRatio" to (sheet?.let { fromMetric(E.currentRatio(it), it) } ?: unavailable("No balance sheet.")),
            "quickRatio" to unavailable("Not computed from this source."),
            "interestCoverage" to if (interest != null) FinancialFact(value = interest, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE,
                basis = basis(latest), note = "EBIT / interest expense for the period; unavailable for non-positive interest expense.")
                else FinancialFact(availability = FinancialAvailability.MISSING, basis = basis(latest), note = "EBIT or a positive interest expense isn't reported."),
            "netDebtEbitda" to unavailable("Not computed from this source.")
        )
        val flowPeriod = latest?.takeIf { it[FinancialConcept.OPERATING_CASH_FLOW] != null }
        val fcf = flowPeriod?.let { E.freeCashFlow(it) }
        val quarterNote = if (quarterly && latest != null && latest[FinancialConcept.OPERATING_CASH_FLOW] == null)
            "Quarterly cash flow from this source isn't verified as standalone quarters for recent fiscal years, so it isn't shown." else null
        val cashFlow = linkedMapOf(
            "operatingCashFlow" to (flowPeriod?.let { reported(it, FinancialConcept.OPERATING_CASH_FLOW) } ?: unavailable(quarterNote ?: "Not reported.", latest)),
            "capex" to (flowPeriod?.let { reported(it, FinancialConcept.CAPITAL_EXPENDITURE).copy(note = "Shown as cash spent.") } ?: unavailable(quarterNote ?: "Not reported.", latest)),
            "freeCashFlow" to (fcf?.let { fromMetric(it, flowPeriod, money = true) } ?: unavailable(quarterNote ?: "Not reported.", latest)),
            "fcfMargin" to (fcf?.value?.let { v -> FinancialCalculations.margin(v.toDouble(), flowPeriod?.get(FinancialConcept.REVENUE)?.value?.toDouble()) }
                ?.let { FinancialFact(value = it, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE, basis = basis(flowPeriod)) }
                ?: unavailable(quarterNote ?: "Free cash flow or revenue not available.", latest))
        )
        // Dividends (DIVIDENDS endpoint) and the dated price; split history from SPLITS (verified only when that request succeeded).
        val asOf = price?.date ?: today
        val records = dividends.orEmpty().filter { !it.exDate.isAfter(today) }.map { FinancialMetricsEngine.DividendRecord(it.exDate, it.amount) }
        val splitHistory = splits?.let { s -> SplitHistory(true, s.map { SplitHistory.Split(it.effectiveDate, it.ratio) }) } ?: SplitHistory.UNKNOWN
        val dps = if (dividends == null) MetricResult(Metric.DIVIDEND_PER_SHARE_TTM, null, MetricStatus.MISSING_INPUT, note = "Dividend data unavailable.")
            else E.trailingDividendPerShare(records, asOf, splitHistory)
        val dividendCurrency = fy?.currency
        val yieldResult = price?.let { E.trailingDividendYield(dps, dividendCurrency, it) }
        fun calendarTotal(year: Int): Double? = if (records.none { it.exDate.year < year }) null else records.filter { it.exDate.year == year }.sumOf { it.amount.toDouble() }
        val dividendGrowth = FinancialCalculations.yoy(calendarTotal(today.year - 1), calendarTotal(today.year - 2))
        val payout = fy?.let { p -> income?.annual?.firstOrNull { it.fiscalDateEnding == p.end } }?.let { _ ->
            val paid = cash?.annual?.firstOrNull { it.fiscalDateEnding == fy.end }?.values?.get("dividendPayout")?.abs()?.toDouble()
            val ni = fy[FinancialConcept.NET_INCOME]?.value?.toDouble()?.takeIf { it > 0 }
            FinancialCalculations.divide(paid, ni)?.times(100)
        }
        val buyback = cash?.let { s -> (if (quarterly) null else s.annual.firstOrNull { it.fiscalDateEnding == latest?.end }) }?.values?.get("paymentsForRepurchaseOfCommonStock")?.abs()
        val shareholder = linkedMapOf(
            "dividendYield" to (yieldResult?.let { fromMetric(it, null, basisLabel = "TTM") } ?: unavailable("No usable dated price.")),
            "dividendPerShare" to fromMetric(dps, null, basisLabel = "TTM"),
            "dividendGrowth" to (dividendGrowth?.let { FinancialFact(value = it, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE,
                basis = FinancialBasis("calendar year", fiscalYear = today.year - 1), note = "Dividend totals by calendar year, as paid; coverage must be complete.") }
                ?: FinancialFact(availability = FinancialAvailability.INSUFFICIENT_HISTORY, basis = FinancialBasis("calendar year", fiscalYear = today.year - 1))),
            "payoutRatio" to (payout?.let { FinancialFact(value = it, source = FinancialSource.BACKEND_CALCULATED, availability = FinancialAvailability.AVAILABLE, basis = basis(fy, "annual"),
                note = "Fiscal-year dividends paid / net income.") } ?: unavailable("Dividends paid or positive net income not reported.", fy)),
            "buybacks" to (buyback?.let { FinancialFact(amount = it.toLong(), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = basis(latest),
                note = "Gross common share repurchase spending.") } ?: unavailable("Not reported.", latest)),
            "shares" to unavailable(NO_SHARES),
            "sharesChange5" to unavailable(NO_SHARES)
        )
        val valuation = linkedMapOf(
            "pe" to unavailable(NO_EPS), "peg" to unavailable(NO_EPS), "priceSales" to unavailable(NO_SHARES), "priceBook" to unavailable(NO_SHARES),
            "priceFcf" to unavailable(NO_SHARES), "enterpriseValue" to unavailable(NO_SHARES), "evEbitda" to unavailable(NO_SHARES), "forwardPe" to unavailable(NO_ESTIMATES)
        )
        val historic = linkedMapOf<String, HistoricalComparison>()
        for (id in listOf("pe", "priceSales", "priceBook", "priceFcf", "evEbitda"))
            historic[id] = HistoricalComparison(note = if (id == "pe") NO_EPS else NO_SHARES)
        return CompanyFundamentals(symbol, CompanyFinancials(growth, profitability, health, cashFlow, shareholder), CompanyValuation(valuation, historic),
            datasets = availability, warnings = listOf(
                "Source: Alpha Vantage statements and dividends; ratios computed by StockSteps from reported values. Amounts use their reported currency and dates.",
                "EPS, P/E and price multiples aren't available from this source; they are left empty rather than estimated."),
            retrievedAt = retrievedAt.toString(),
            history = AlphaVantageStatements.merge(listOfNotNull(income?.let { Statement.INCOME to it }, balance?.let { Statement.BALANCE to it },
                cash?.let { Statement.CASH_FLOW to it }).toMap(), quarterly, verifiedQuarters = true))
    }
}

/**
 * Phase 9B.8: fundamentals from Alpha Vantage over any [base] provider (FMP or Alpha Vantage for everything else). Selected by
 * `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage`. Shares the client's cached statements with `statementHistory`, so Company Details, Comparison,
 * Discover and AI inputs read the same validated figures. Income is required; other datasets degrade to unavailable. An Alpha Vantage refusal
 * is returned as the typed failure — never answered by [base] (no budget or plan bypass).
 */
class AlphaVantageFundamentalsProvider(
    private val av: AlphaVantageClient,
    private val base: StockProviderRepository,
    private val sessions: EodSessions,
    private val clock: Clock = Clock.systemUTC(),
    /** Computed bundles per symbol and period (15 min): repeat opens don't recompute or re-read the quote. Failures aren't stored. */
    private val bundles: org.example.stocksteps.service.CompanyFinancialCache = org.example.stocksteps.service.CompanyFinancialCache(capacity = 1_024, name = "av-fundamentals")
) : StockProviderRepository {
    override suspend fun getQuote(symbol: String): StockQuote? = base.getQuote(symbol)
    override suspend fun getGainers(): List<MarketMover> = base.getGainers()
    override suspend fun getLosers(): List<MarketMover> = base.getLosers()
    override suspend fun getProfile(symbol: String): CompanyProfile? = base.getProfile(symbol)
    override suspend fun searchStocks(query: String): List<StockSearchResult> = base.searchStocks(query)

    private val statements = AlphaVantageStockProvider(av, base, sessions, clock)
    override suspend fun statementHistory(symbol: String, period: String, statements: Set<Statement>): StatementHistory =
        this.statements.statementHistory(symbol, period, statements)

    /** The latest quote as a dated, labelled price (US listings in USD only; anything else has no currency → no price ratios). */
    private suspend fun price(symbol: String): PriceObservation? {
        val q = runCatching { base.getQuote(symbol) }.getOrNull() ?: return null
        val p = q.price?.takeIf { it.isFinite() && it > 0 } ?: return null
        val at = q.timestamp?.let { Instant.ofEpochSecond(it) } ?: return null
        val market = sessions.market(symbol) ?: return null
        val delay = PriceSeriesRules.quoteDelay(symbol, at, sessions, clock.instant())
        return PriceObservation(symbol, BigDecimal(p.toString()), if (market == EodSessions.Market.US && '.' !in symbol) "USD" else null,
            at.atZone(java.time.ZoneId.of("America/New_York")).toLocalDate(), AdjustmentBasis.UNADJUSTED, delay,
            Provenance("quote", Dataset.QUOTES, clock.instant(), at.toString()))
    }

    override suspend fun getFundamentals(symbol: String, period: String): org.example.stocksteps.model.CompanyFundamentals =
        bundles.getOrLoad("${symbol.uppercase()}:$period", BUNDLE_TTL) { build(symbol, period) }

    private suspend fun build(symbol: String, period: String): org.example.stocksteps.model.CompanyFundamentals {
        val income = av.statement("INCOME_STATEMENT", symbol)   // required: a failure propagates (typed)
        val availability = linkedMapOf("income" to if (income == null) FinancialAvailability.MISSING else FinancialAvailability.AVAILABLE)
        suspend fun <T> optional(name: String, load: suspend () -> T?): T? = try {
            load().also { availability[name] = if (it == null) FinancialAvailability.MISSING else FinancialAvailability.AVAILABLE }
        } catch (_: StockProviderException) { availability[name] = FinancialAvailability.TEMPORARILY_UNAVAILABLE; null }
        val balance = optional("balance") { av.statement("BALANCE_SHEET", symbol) }
        val cash = optional("cashFlow") { av.statement("CASH_FLOW", symbol) }
        val dividends = optional("dividends") { av.dividends(symbol) }
        val splits = optional("splits") { av.splits(symbol) }
        val price = price(symbol)?.takeIf { it.delay != PriceDelay.UNKNOWN }
        return AlphaVantageFundamentalsMapper.map(symbol, period, income, balance, cash, dividends, splits, price, LocalDate.now(clock), clock.instant(), availability)
    }

    override suspend fun screenerFundamentals(symbol: String, listingCurrency: String?): org.example.stocksteps.model.CompanyFundamentals = getFundamentals(symbol, "annual")

    companion object { const val BUNDLE_TTL = 15 * 60_000L }
}
