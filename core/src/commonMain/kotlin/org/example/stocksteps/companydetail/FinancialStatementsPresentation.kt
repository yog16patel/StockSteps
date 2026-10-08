package org.example.stocksteps.companydetail

import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.pow

/** Historical range for the Financials screen; quarterly ranges use 4 quarters per year. */
enum class FinancialRange(val label: String, val years: Int) { THREE_YEARS("3Y", 3), FIVE_YEARS("5Y", 5), TEN_YEARS("10Y", 10) }

/** Whether a section has data, was not reported by the company, or could not be loaded. */
enum class SectionAvailability { CONTENT, NOT_REPORTED, TEMPORARILY_UNAVAILABLE }

/** One bar; [value] null means not reported (drawn as a gap, never as zero). */
data class FinancialBar(val label: String, val value: Double?, val detail: String)

/** Bar chart for one or two series over the same periods (oldest → newest). */
data class FinancialChart(
    val bars: List<FinancialBar>,
    val secondary: List<FinancialBar>? = null,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    /** Screen-reader summary of the whole chart. */
    val description: String
)

/** Two amounts drawn as proportional horizontal bars (cash vs debt, assets vs liabilities). */
data class FinancialComparison(val title: String, val firstLabel: String, val first: Double, val firstText: String,
                               val secondLabel: String, val second: Double, val secondText: String, val caption: String)

/** Every section answers: what is it, what does this company's number mean, why it matters. */
data class FinancialStatementSection(
    val id: String,
    val title: String,
    val availability: SectionAvailability,
    val headline: String? = null,
    val headlineLabel: String? = null,
    val change: String? = null,
    val changeDirection: PriceDirection? = null,
    val rows: List<InfoRow> = emptyList(),
    val chart: FinancialChart? = null,
    val comparisons: List<FinancialComparison> = emptyList(),
    /** Extra rows shown only when expanded (progressive disclosure). */
    val moreRows: List<InfoRow> = emptyList(),
    val meaning: String? = null,
    val whatIsIt: String,
    val whyItMatters: String,
    val note: String? = null
)

data class FinancialSummaryLine(val title: String, val text: String, val tone: FactTone)

data class FinancialTable(val columns: List<String>, val rows: List<Pair<String, List<String>>>)

data class FinancialStatementsModel(
    val periodLabel: String?,
    val currency: String?,
    val rangeNote: String?,
    val sections: List<FinancialStatementSection>,
    val summary: List<FinancialSummaryLine>,
    val summaryLimitations: String?,
    val table: FinancialTable?,
    val advanced: List<InfoRow>,
    /** Screen-level message when there is no statement history at all. */
    val emptyMessage: String?
)

/** Deterministic financial math shared by every platform. Null in → null out; never a fake zero. */
object FinancialMath {
    /** Growth versus a comparable period; only meaningful when both values are positive. */
    fun growth(current: Double?, previous: Double?): Double? {
        if (current == null || previous == null || !current.isFinite() || !previous.isFinite()) return null
        if (previous <= 0 || current < 0) return null
        return (current - previous) / previous * 100
    }

    /** Change for values that can turn negative (net income, EPS, FCF): measured against |previous|. */
    fun signedGrowth(current: Double?, previous: Double?): Double? {
        if (current == null || previous == null || !current.isFinite() || !previous.isFinite() || previous == 0.0) return null
        if (previous < 0) return null // growth from a loss is not a meaningful percentage
        return (current - previous) / previous * 100
    }

    fun margin(amount: Double?, revenue: Double?): Double? =
        if (amount == null || revenue == null || revenue <= 0 || !amount.isFinite()) null else amount / revenue * 100

    fun ratio(numerator: Double?, denominator: Double?): Double? =
        if (numerator == null || denominator == null || denominator <= 0 || !numerator.isFinite()) null else numerator / denominator

    /** Compound annual growth; only for positive start and end values over a whole number of years. */
    fun cagr(ending: Double?, beginning: Double?, years: Int): Double? {
        if (ending == null || beginning == null || years <= 0 || ending <= 0 || beginning <= 0) return null
        return ((ending / beginning).pow(1.0 / years) - 1) * 100
    }

    /** The same period one fiscal year earlier (FY → previous FY; Q3 → previous year's Q3). */
    fun comparable(history: List<FinancialPeriodStatement>, current: FinancialPeriodStatement): FinancialPeriodStatement? {
        val year = current.fiscalYear ?: return null
        return history.firstOrNull { it.period == current.period && it.fiscalYear == year - 1 && it.currency == current.currency }
    }
}

/**
 * Builds the Financials screen from normalized statement history plus the latest facts. Every
 * sentence states a fact with a fixed template; nothing rates the company or advises an action.
 */
object FinancialStatementsPresenter {
    fun defaultRange(frequency: FinancialPeriod, periods: Int): FinancialRange {
        val perYear = if (frequency == FinancialPeriod.ANNUAL) 1 else 4
        return if (periods >= FinancialRange.FIVE_YEARS.years * perYear) FinancialRange.FIVE_YEARS else FinancialRange.THREE_YEARS
    }

    fun build(fundamentals: CompanyFundamentals, frequency: FinancialPeriod, range: FinancialRange, companyName: String): FinancialStatementsModel {
        val name = companyName
        val all = fundamentals.history.sortedWith(compareByDescending<FinancialPeriodStatement> { it.date }.thenByDescending { it.fiscalYear })
        val latest = all.firstOrNull()
        if (latest == null) {
            val failed = fundamentals.datasets["income"]?.let { it != FinancialAvailability.AVAILABLE && it != FinancialAvailability.MISSING } == true
            return FinancialStatementsModel(null, null, null, emptyList(), emptyList(), null, null, emptyList(),
                if (failed) "We couldn't load the financial statements. Try again." else "Historical data isn't available for this period.")
        }
        // Never mix currencies: keep the latest reporting currency only.
        val sameCurrency = all.filter { it.currency == latest.currency }
        val perYear = if (frequency == FinancialPeriod.ANNUAL) 1 else 4
        val window = sameCurrency.take(range.years * perYear)
        val chronological = window.reversed()
        val unit = if (frequency == FinancialPeriod.ANNUAL) "fiscal years" else "quarters"
        val rangeNote = listOfNotNull(
            if (sameCurrency.size < range.years * perYear) "Only ${sameCurrency.size} $unit of history are available." else null,
            if (sameCurrency.size < all.size) "Earlier periods reported in another currency are not shown." else null
        ).joinToString(" ").ifEmpty { null }
        val currency = latest.currency
        fun money(value: Double?) = value?.let { formatMoney(it, currency) } ?: "—"
        val previous = FinancialMath.comparable(all, latest)
        val comparison = previous?.let { "vs ${label(it)}" }
        val datasets = fundamentals.datasets
        fun unavailable(dataset: String) = datasets[dataset]?.let { it != FinancialAvailability.AVAILABLE && it != FinancialAvailability.MISSING } == true
        val facts = fundamentals.financials.metrics()
        fun fact(id: String) = facts[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.let { it.value ?: it.amount?.toDouble() }
        fun bars(get: (FinancialPeriodStatement) -> Double?, format: (Double) -> String, growthOf: (Double?, Double?) -> Double? = FinancialMath::growth) =
            chronological.map { row ->
                val value = get(row)
                val change = FinancialMath.comparable(all, row)?.let { growthOf(value, get(it))?.let { g -> " • ${signed(g)} vs ${label(it)}" } }.orEmpty()
                FinancialBar(shortLabel(row), value, "${label(row)}: ${value?.let(format) ?: "not reported"}$change")
            }
        fun describe(title: String, series: List<FinancialBar>) =
            "$title by ${if (frequency == FinancialPeriod.ANNUAL) "fiscal year" else "quarter"}: " +
                series.joinToString("; ") { it.detail }

        // Revenue
        val revenue = latest.revenue
        val revenueGrowth = FinancialMath.growth(revenue, previous?.revenue)
        val revenueBars = bars({ it.revenue }, { formatMoney(it, currency) })
        val revenueSection = FinancialStatementSection(
            id = "revenue", title = "Revenue",
            availability = if (revenue != null) SectionAvailability.CONTENT else if (unavailable("income")) SectionAvailability.TEMPORARILY_UNAVAILABLE else SectionAvailability.NOT_REPORTED,
            headline = revenue?.let(::money), headlineLabel = label(latest),
            change = revenueGrowth?.let { "${signed(it)} $comparison" }, changeDirection = revenueGrowth?.let(HomePresentation::direction),
            chart = FinancialChart(revenueBars, primaryLabel = "Revenue", description = describe("Revenue", revenueBars)).takeIf { revenueBars.count { it.value != null } >= 2 },
            meaning = when {
                revenue == null -> null
                revenue < 0 -> "$name reported negative revenue for ${label(latest)}, which is unusual, so growth isn't calculated."
                revenueGrowth == null && previous?.revenue == 0.0 -> "Growth can't be calculated because revenue in ${label(previous)} was zero."
                revenueGrowth == null -> "No comparable earlier period is available to measure growth."
                revenueGrowth >= 0 -> "Revenue increased ${percent(revenueGrowth)} compared with ${label(previous!!)}."
                else -> "Revenue declined ${percent(abs(revenueGrowth))} compared with ${label(previous!!)}."
            },
            whatIsIt = "Revenue is the total money a company earns from selling products and services before subtracting expenses.",
            whyItMatters = "Revenue growth can help show whether demand for a company's products and services is expanding."
        )

        // Profitability
        val netIncome = latest.netIncome
        val netGrowth = FinancialMath.signedGrowth(netIncome, previous?.netIncome)
        val netMargin = FinancialMath.margin(netIncome, revenue)
        val operatingMargin = FinancialMath.margin(latest.operatingIncome, revenue)
        val grossMargin = FinancialMath.margin(latest.grossProfit, revenue)
        val incomeBars = bars({ it.netIncome }, { formatMoney(it, currency) }, FinancialMath::signedGrowth)
        val profitability = FinancialStatementSection(
            id = "profitability", title = "Profitability",
            availability = if (netIncome != null || netMargin != null) SectionAvailability.CONTENT else if (unavailable("income")) SectionAvailability.TEMPORARILY_UNAVAILABLE else SectionAvailability.NOT_REPORTED,
            rows = listOfNotNull(
                netIncome?.let { InfoRow("Net Income", money(it), "Profit left after expenses, interest and taxes.", netGrowth?.let(::signed), netGrowth?.let(HomePresentation::direction)) },
                netMargin?.let { InfoRow("Net Margin", percent(it), "How much profit is kept from each dollar of revenue.") },
                operatingMargin?.let { InfoRow("Operating Margin", percent(it), "Profit from core operations before interest and taxes.") },
                grossMargin?.let { InfoRow("Gross Margin", percent(it), "Revenue left after the direct costs of products and services.") }
            ),
            chart = FinancialChart(revenueBars, incomeBars, "Revenue", "Net Income",
                describe("Revenue", revenueBars) + ". " + describe("Net income", incomeBars)).takeIf { incomeBars.count { it.value != null } >= 2 },
            meaning = when {
                netIncome == null -> "Net income wasn't reported for ${label(latest)}."
                netIncome < 0 -> "$name reported a net loss of ${money(abs(netIncome))} for ${label(latest)}: it spent more than it earned."
                netMargin != null -> "For every ${currencySymbol(currency)}100 of revenue in ${label(latest)}, $name kept about ${currencySymbol(currency)}${netMargin.toInt()} as net profit."
                else -> "$name reported net income of ${money(netIncome)} for ${label(latest)}."
            },
            whatIsIt = "Net income is the profit remaining after all expenses, interest and taxes. Margins divide profit by revenue for the same period.",
            whyItMatters = "Margins show how much of each sale turns into profit, which makes companies of different sizes easier to compare.",
            note = if (netGrowth == null && netIncome != null && previous?.netIncome?.let { it < 0 } == true)
                "Growth isn't shown because ${label(previous)} was a loss; a percentage would be misleading." else null
        )

        // Cash flow
        val operating = latest.operatingCashFlow
        val capex = latest.capitalExpenditure
        val fcf = latest.freeCashFlow
        val fcfBars = bars({ it.freeCashFlow }, { formatMoney(it, currency) }, FinancialMath::signedGrowth)
        val cashFlow = FinancialStatementSection(
            id = "cashFlow", title = "Cash Flow",
            availability = if (operating != null || fcf != null) SectionAvailability.CONTENT else if (unavailable("cashFlow")) SectionAvailability.TEMPORARILY_UNAVAILABLE else SectionAvailability.NOT_REPORTED,
            rows = listOfNotNull(
                operating?.let { InfoRow("Operating Cash Flow", money(it), "Cash generated or used by normal business operations.") },
                capex?.let { InfoRow("Capital Expenditure", money(it), "Money spent on long-term assets such as equipment and buildings.") },
                fcf?.let { InfoRow("Free Cash Flow", money(it), "Operating cash flow minus capital expenditure.") }
            ),
            chart = FinancialChart(fcfBars, primaryLabel = "Free Cash Flow", description = describe("Free cash flow", fcfBars)).takeIf { fcfBars.count { it.value != null } >= 2 },
            meaning = when {
                fcf == null -> null
                fcf >= 0 -> "Positive free cash flow means $name generated more operating cash than it spent on capital investments during ${label(latest)}."
                else -> "Negative free cash flow means $name spent more on capital investments than its operations generated during ${label(latest)}."
            },
            whatIsIt = "Free cash flow is the cash left from operations after spending on long-term assets.",
            whyItMatters = "Cash pays for investment, debt repayment and dividends. Profit and cash can differ because of timing and non-cash items.",
            note = "Capital expenditure is shown as money spent; free cash flow subtracts it once."
        )

        // Financial health (balance sheet: point-in-time snapshot, never summed across quarters)
        val cash = latest.cash
        val debt = latest.totalDebt
        val assets = latest.totalAssets
        val liabilities = latest.totalLiabilities
        val equity = latest.equity
        val currentRatio = FinancialMath.ratio(latest.currentAssets, latest.currentLiabilities)
        val debtToEquity = FinancialMath.ratio(debt, equity)
        val coverage = fact("interestCoverage")
        val health = FinancialStatementSection(
            id = "health", title = "Financial Health",
            availability = if (listOf(cash, debt, assets, liabilities, equity).any { it != null }) SectionAvailability.CONTENT
                else if (unavailable("balance")) SectionAvailability.TEMPORARILY_UNAVAILABLE else SectionAvailability.NOT_REPORTED,
            headlineLabel = latest.date?.let { "As of $it" },
            rows = listOfNotNull(
                cash?.let { InfoRow("Cash & Equivalents", money(it), "Money and highly liquid resources; excludes longer-term investments.") },
                debt?.let { InfoRow("Total Debt", money(it), "Short- and long-term borrowings.") },
                assets?.let { InfoRow("Total Assets", money(it)) },
                liabilities?.let { InfoRow("Total Liabilities", money(it)) },
                equity?.let { InfoRow("Shareholders' Equity", money(it), if (it < 0) "Negative: liabilities exceed assets." else null) }
            ),
            comparisons = listOfNotNull(
                if (cash != null && debt != null && (cash > 0 || debt > 0)) FinancialComparison("Cash vs Debt", "Cash", cash, money(cash), "Debt", debt, money(debt),
                    when {
                        debt == 0.0 -> "$name reported no debt."
                        cash >= debt -> "Cash is larger than total debt."
                        else -> "Cash covers about ${percent(cash / debt * 100, 0)} of total debt."
                    }) else null,
                if (assets != null && liabilities != null && assets > 0) FinancialComparison("Assets vs Liabilities", "Assets", assets, money(assets), "Liabilities", liabilities, money(liabilities),
                    "Liabilities equal ${percent(liabilities / assets * 100, 0)} of total assets.") else null
            ),
            moreRows = listOfNotNull(
                currentRatio?.let { InfoRow("Current Ratio", decimal(it, 2), "Short-term assets ÷ short-term liabilities.") },
                debtToEquity?.let { InfoRow("Debt to Equity", decimal(it, 2), "Total debt ÷ shareholders' equity.") },
                coverage?.let { InfoRow("Interest Coverage", "${decimal(it, 1)}x", "Operating profit ÷ interest expense (trailing 12 months).") }
            ),
            meaning = if (cash != null && debt != null) "These are balance-sheet amounts on ${latest.date ?: label(latest)}, a single point in time." else null,
            whatIsIt = "The balance sheet lists what a company owns (assets), what it owes (liabilities) and the difference (equity).",
            whyItMatters = "Cash and debt levels affect how much flexibility a company has. Debt isn't automatically bad; what's normal differs by industry.",
            note = "Debt to equity uses total debt (short- and long-term borrowings)."
        )

        // EPS (diluted only)
        val eps = latest.epsDiluted
        val epsGrowth = FinancialMath.signedGrowth(eps, previous?.epsDiluted)
        val epsBars = bars({ it.epsDiluted }, { formatPerShare(it, currency) }, FinancialMath::signedGrowth)
        val epsSection = FinancialStatementSection(
            id = "eps", title = "Earnings Per Share (EPS)",
            availability = if (eps != null) SectionAvailability.CONTENT else if (unavailable("income")) SectionAvailability.TEMPORARILY_UNAVAILABLE else SectionAvailability.NOT_REPORTED,
            headline = eps?.let { formatPerShare(it, currency) }, headlineLabel = "Diluted EPS • ${label(latest)}",
            change = epsGrowth?.let { "${signed(it)} $comparison" }, changeDirection = epsGrowth?.let(HomePresentation::direction),
            chart = FinancialChart(epsBars, primaryLabel = "Diluted EPS", description = describe("Diluted EPS", epsBars)).takeIf { epsBars.count { it.value != null } >= 2 },
            meaning = eps?.let { if (it < 0) "$name lost about ${formatPerShare(abs(it), currency)} per share in ${label(latest)}." else "$name earned about ${formatPerShare(it, currency)} per share in ${label(latest)}." },
            whatIsIt = "EPS shows how much profit a company earns for each share, based on its reported share count.",
            whyItMatters = "EPS can grow differently from net income because the number of shares changes over time (for example, through buybacks).",
            note = "Diluted EPS counts shares that options and convertible securities could add."
        )

        // Dividends (trailing-twelve-month facts; NO_DIVIDEND differs from missing)
        val dividendFact = fundamentals.financials.shareholderReturns["dividendYield"]
        val noDividend = dividendFact?.availability == FinancialAvailability.NO_DIVIDEND
        val dividendRows = listOfNotNull(
            fact("dividendPerShare")?.let { InfoRow("Dividend per Share (TTM)", formatPerShare(it, currency)) },
            fact("dividendYield")?.let { InfoRow("Dividend Yield (TTM)", percent(it), "Yearly dividends ÷ current share price.") },
            fact("payoutRatio")?.let { InfoRow("Payout Ratio (TTM)", percent(it), "Share of earnings paid out as dividends.") },
            latest.dividendsPaid?.let { InfoRow("Dividends Paid • ${label(latest)}", money(it)) }
        )
        val dividends = FinancialStatementSection(
            id = "dividends", title = "Dividends",
            availability = if (dividendRows.isNotEmpty() && !noDividend) SectionAvailability.CONTENT else SectionAvailability.NOT_REPORTED,
            rows = if (noDividend) emptyList() else dividendRows,
            meaning = when {
                noDividend -> "$name does not currently pay a regular dividend."
                dividendRows.isEmpty() -> "Dividend information isn't available for $name."
                else -> null
            },
            whatIsIt = "A dividend is cash a company pays to shareholders, usually from profits.",
            whyItMatters = "Some companies pay dividends; others reinvest earnings in growth instead. Neither is automatically better."
        )

        // Summary: one factual line per area; limitations when data is missing.
        val summary = listOfNotNull(
            revenueGrowth?.let { FinancialSummaryLine("Revenue", if (it >= 0) "Grew ${percent(it)} $comparison" else "Declined ${percent(abs(it))} $comparison", if (it >= 0) FactTone.POSITIVE else FactTone.NEGATIVE) },
            netIncome?.let {
                if (it < 0) FinancialSummaryLine("Profitability", "Net loss in ${label(latest)}", FactTone.NEGATIVE)
                else FinancialSummaryLine("Profitability", netMargin?.let { m -> "Positive net margin of ${percent(m)}" } ?: "Positive net income", FactTone.POSITIVE)
            },
            fcf?.let { FinancialSummaryLine("Cash Flow", if (it >= 0) "Positive free cash flow" else "Negative free cash flow", if (it >= 0) FactTone.POSITIVE else FactTone.CAUTION) },
            if (cash != null && debt != null) FinancialSummaryLine("Financial Position", when {
                debt == 0.0 -> "No debt reported"
                cash >= debt -> "More cash than total debt"
                cash / debt >= 0.75 -> "Cash and debt are relatively close"
                else -> "More debt than cash"
            }, FactTone.NEUTRAL) else null
        )
        val missing = listOfNotNull(
            "revenue growth".takeIf { revenueGrowth == null }, "net income".takeIf { netIncome == null },
            "free cash flow".takeIf { fcf == null }, "cash and debt".takeIf { cash == null || debt == null }
        )

        val columns = window.map(::label)
        fun row(title: String, get: (FinancialPeriodStatement) -> Double?, format: (Double) -> String = { formatMoney(it, currency) }) =
            title to window.map { get(it)?.let(format) ?: "—" }
        val table = FinancialTable(columns, listOf(
            row("Revenue", { it.revenue }), row("Gross Profit", { it.grossProfit }), row("Operating Income", { it.operatingIncome }),
            row("Net Income", { it.netIncome }), row("Diluted EPS", { it.epsDiluted }, { formatPerShare(it, currency) }),
            row("Operating Cash Flow", { it.operatingCashFlow }), row("Capital Expenditure", { it.capitalExpenditure }),
            row("Free Cash Flow", { it.freeCashFlow }), row("Cash & Equivalents", { it.cash }), row("Total Debt", { it.totalDebt }),
            row("Total Assets", { it.totalAssets }), row("Total Liabilities", { it.totalLiabilities }), row("Shareholders' Equity", { it.equity })
        ).filter { (_, values) -> values.any { it != "—" } }).takeIf { columns.isNotEmpty() }

        val annualRows = sameCurrency.filter { it.period == "FY" }
        fun historicalCagr(get: (FinancialPeriodStatement) -> Double?): Pair<Int, Double>? {
            val end = annualRows.firstOrNull() ?: return null
            val start = annualRows.lastOrNull()?.takeIf { it !== end } ?: return null
            val years = (end.fiscalYear ?: return null) - (start.fiscalYear ?: return null)
            return FinancialMath.cagr(get(end), get(start), years)?.let { years to it }
        }
        val advanced = listOfNotNull(
            fact("roe")?.let { InfoRow("Return on Equity", percent(it)) },
            fact("roa")?.let { InfoRow("Return on Assets", percent(it)) },
            fact("roic")?.let { InfoRow("Return on Invested Capital", percent(it)) },
            fact("revenueCagr3")?.let { InfoRow("Revenue CAGR (3Y)", signed(it)) },
            fact("revenueCagr5")?.let { InfoRow("Revenue CAGR (5Y)", signed(it)) },
            fact("epsCagr3")?.let { InfoRow("EPS CAGR (3Y)", signed(it)) },
            fact("epsCagr5")?.let { InfoRow("EPS CAGR (5Y)", signed(it)) },
            if (frequency == FinancialPeriod.ANNUAL) historicalCagr { it.freeCashFlow }?.let { (years, value) -> InfoRow("Free Cash Flow CAGR (${years}Y)", signed(value)) } else null
        )

        return FinancialStatementsModel(
            periodLabel = periodDescription(latest),
            currency = currency,
            rangeNote = rangeNote,
            sections = listOf(revenueSection, profitability, cashFlow, health, epsSection, dividends),
            summary = summary,
            summaryLimitations = missing.takeIf { it.isNotEmpty() }?.let { "Not enough data to describe ${it.joinToString(", ")}." },
            table = table,
            advanced = advanced,
            emptyMessage = null
        )
    }

    /** "FY2025" or "Q3 FY2025". */
    fun label(row: FinancialPeriodStatement): String {
        val year = row.fiscalYear?.toString() ?: row.date?.take(4) ?: "?"
        return if (row.period == "FY") "FY$year" else "${row.period} FY$year"
    }

    private fun shortLabel(row: FinancialPeriodStatement): String {
        val year = (row.fiscalYear?.toString() ?: row.date?.take(4) ?: "").takeLast(2)
        return if (row.period == "FY") "FY$year" else "${row.period} '$year"
    }

    private fun periodDescription(row: FinancialPeriodStatement): String =
        (if (row.period == "FY") "Fiscal year ${row.fiscalYear ?: ""}" else "${row.period} of fiscal ${row.fiscalYear ?: ""}") +
            (row.date?.let { ", ended $it" } ?: "") + (row.currency?.let { " • $it" } ?: "")

    fun currencySymbol(currency: String?): String = when (currency?.uppercase()) {
        null, "", "USD" -> "$"
        "CAD" -> "C$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> "${currency.uppercase()} "
    }

    fun formatMoney(value: Double, currency: String?): String =
        "${if (value < 0) "-" else ""}${currencySymbol(currency)}${CompanyOverviewPresenter.compact(abs(value))}"

    private fun formatPerShare(value: Double, currency: String?) =
        "${if (value < 0) "-" else ""}${currencySymbol(currency)}${decimal(abs(value), 2)}"

    private fun percent(value: Double, digits: Int = 1) = "${decimal(value, digits)}%"
    private fun signed(value: Double) = "${if (value > 0) "+" else if (value < 0) "-" else ""}${decimal(abs(value), 1)}%"
    private fun decimal(value: Double, digits: Int) = CompanyOverviewPresenter.decimal(value, digits)
}
