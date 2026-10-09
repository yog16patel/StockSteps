package org.example.stocksteps.repositoryImpl

import java.time.LocalDate
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.service.FinancialCalculations as Calc

/** Provider field names end here. Pure mapping/calculations make fixtures deterministic. */
internal object FmpFundamentalsMapper {
    fun map(
        symbol: String,
        period: String,
        income: List<FmpIncomeStatement>,
        annualIncome: List<FmpIncomeStatement>,
        balance: List<FmpBalanceSheetStatement>,
        cash: List<FmpCashFlowStatement>,
        ratios: FmpRatiosTtm?,
        keys: FmpKeyMetricsTtm?,
        history: List<FmpRatios>,
        trailingIncome: FmpIncomeStatement?,
        trailingCash: FmpCashFlowStatement?,
        estimates: List<FmpAnalystEstimate>,
        dividends: List<FmpDividend>,
        shares: FmpSharesFloat?,
        price: Double?,
        quoteCurrency: String?,
        today: LocalDate
    ): CompanyFundamentals {
        val rows = income.filter { it.date != null && it.date <= today.toString() && if (period == "annual") it.period == "FY" else it.period in listOf("Q1", "Q2", "Q3", "Q4") }
            .sortedWith(compareByDescending<FmpIncomeStatement> { it.date }.thenByDescending { it.acceptedDate })
            .distinctBy { it.date }
        val latest = rows.firstOrNull()
        val prior = latest?.let { current -> rows.firstOrNull {
            it.fiscalYear?.toIntOrNull() == current.fiscalYear?.toIntOrNull()?.minus(1) && it.period == current.period
        } }
        val years = annualIncome.filter { it.period == "FY" && it.date != null && it.date <= today.toString() }
            .sortedWith(compareByDescending<FmpIncomeStatement> { it.fiscalYear?.toIntOrNull() }.thenByDescending { it.acceptedDate })
            .distinctBy { it.fiscalYear }
        val annual = years.firstOrNull()
        val basis = FinancialBasis(if (period == "quarter") latest?.period ?: period else period, latest?.date, latest?.fiscalYear?.toIntOrNull(), latest?.reportedCurrency)
        val ttmBasis = FinancialBasis("TTM", trailingIncome?.date, trailingIncome?.fiscalYear?.toIntOrNull(), trailingIncome?.reportedCurrency)
        fun fact(value: Double?, calculated: Boolean = false, money: Boolean = false, note: String? = null,
                 report: FinancialBasis? = basis, reason: FinancialAvailability = FinancialAvailability.MISSING): FinancialFact {
            val valid = Calc.finite(value)
            val amount = valid?.takeIf { it >= Long.MIN_VALUE.toDouble() && it < Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toLong()
            val available = valid != null && (!money || amount != null)
            return FinancialFact(
                value = valid.takeIf { available && !money },
                amount = amount.takeIf { available && money },
                source = if (!available) FinancialSource.NOT_AVAILABLE else if (calculated) FinancialSource.BACKEND_CALCULATED else FinancialSource.PROVIDER_DIRECT,
                availability = if (available) FinancialAvailability.AVAILABLE else if (value != null) FinancialAvailability.INVALID_VALUE else reason,
                basis = report,
                note = note
            )
        }
        fun comparable(a: FmpIncomeStatement?, b: FmpIncomeStatement?) = a != null && b != null &&
            a.reportedCurrency != null && a.reportedCurrency == b.reportedCurrency
        fun growth(get: (FmpIncomeStatement) -> Double?) = if (comparable(latest, prior)) Calc.yoy(get(latest!!), get(prior!!)) else null
        // Growth from a loss (or zero) to anything isn't a meaningful percentage.
        fun profitGrowth(get: (FmpIncomeStatement) -> Double?) = prior?.let(get)?.takeIf { it > 0 }?.let { growth(get) }
        fun turnaround(get: (FmpIncomeStatement) -> Double?) = prior?.let(get)?.let { it <= 0 } == true
        fun cagr(n: Int, get: (FmpIncomeStatement) -> Double?): Double? {
            val first = annual ?: return null
            val previous = years.firstOrNull { it.fiscalYear?.toIntOrNull() == first.fiscalYear?.toIntOrNull()?.minus(n) } ?: return null
            return if (comparable(first, previous)) Calc.cagr(get(first), get(previous), n) else null
        }
        val annualBasis = FinancialBasis("annual", annual?.date, annual?.fiscalYear?.toIntOrNull(), annual?.reportedCurrency)
        val growth = linkedMapOf(
            "revenue" to fact(latest?.revenue, money = true),
            "revenueGrowth" to fact(growth { it.revenue }, true),
            "revenueCagr3" to fact(cagr(3) { it.revenue }, true, report = annualBasis, reason = FinancialAvailability.INSUFFICIENT_HISTORY),
            "revenueCagr5" to fact(cagr(5) { it.revenue }, true, report = annualBasis, reason = FinancialAvailability.INSUFFICIENT_HISTORY),
            "netIncome" to fact(latest?.netIncome, money = true),
            "netIncomeGrowth" to fact(profitGrowth { it.netIncome }, true, note = if (turnaround { it.netIncome }) "Prior period was a loss or zero, so growth isn't shown as a percentage." else null,
                reason = if (turnaround { it.netIncome }) FinancialAvailability.UNRELIABLE_COMPARISON else FinancialAvailability.MISSING),
            "eps" to fact(latest?.epsDiluted, note = "Diluted EPS."),
            "epsGrowth" to fact(profitGrowth { it.epsDiluted }, true, note = if (turnaround { it.epsDiluted }) "Prior EPS was zero or negative, so growth isn't shown as a percentage." else null,
                reason = if (turnaround { it.epsDiluted }) FinancialAvailability.UNRELIABLE_COMPARISON else FinancialAvailability.MISSING),
            "epsCagr3" to fact(cagr(3) { it.epsDiluted }, true, report = annualBasis, reason = FinancialAvailability.INSUFFICIENT_HISTORY),
            "epsCagr5" to fact(cagr(5) { it.epsDiluted }, true, report = annualBasis, reason = FinancialAvailability.INSUFFICIENT_HISTORY)
        )
        val profitability = linkedMapOf<String, FinancialFact>()
        fun margin(id: String, direct: Double?, numerator: Double?) {
            val standardized = Calc.finite(direct)
            profitability[id] = if (standardized != null) fact(standardized * 100, report = ttmBasis)
                else fact(Calc.margin(numerator, latest?.revenue), true)
        }
        margin("grossMargin", ratios?.grossProfitMarginTTM, latest?.grossProfit)
        margin("operatingMargin", ratios?.operatingProfitMarginTTM, latest?.operatingIncome)
        margin("netMargin", ratios?.netProfitMarginTTM, latest?.netIncome)
        profitability["roe"] = fact(keys?.returnOnEquityTTM?.times(100), report = ttmBasis)
        profitability["roa"] = fact(keys?.returnOnAssetsTTM?.times(100), report = ttmBasis)
        profitability["roic"] = fact(keys?.returnOnInvestedCapitalTTM?.times(100), report = ttmBasis)
        val sheet = balance.filter { it.date != null && it.date <= today.toString() && if (period == "annual") it.period == "FY" else it.period in listOf("Q1", "Q2", "Q3", "Q4") }.maxByOrNull { it.date!! }
        val sheetBasis = FinancialBasis(period, sheet?.date, sheet?.fiscalYear?.toIntOrNull(), sheet?.reportedCurrency)
        val health = linkedMapOf(
            "cash" to fact(sheet?.cashAndCashEquivalents, money = true, report = sheetBasis),
            "debt" to fact(sheet?.totalDebt, money = true, report = sheetBasis),
            "assets" to fact(sheet?.totalAssets, money = true, report = sheetBasis),
            "liabilities" to fact(sheet?.totalLiabilities, money = true, report = sheetBasis),
            "equity" to fact(sheet?.totalStockholdersEquity, money = true, report = sheetBasis),
            "netDebt" to fact(sheet?.netDebt ?: if (sheet?.totalDebt != null && sheet.cashAndCashEquivalents != null) sheet.totalDebt - sheet.cashAndCashEquivalents else null,
                calculated = sheet?.netDebt == null, money = true, report = sheetBasis),
            // Total debt ÷ total shareholders' equity; not meaningful (never a negative ratio) when equity is zero or negative.
            "debtEquity" to if (sheet?.totalStockholdersEquity?.let { it <= 0 } == true) fact(null, report = sheetBasis, reason = FinancialAvailability.NON_POSITIVE_DENOMINATOR,
                note = "Not meaningful: shareholders' equity is zero or negative on the latest balance sheet.")
                else fact(ratios?.debtToEquityRatioTTM ?: Calc.divide(sheet?.totalDebt, sheet?.totalStockholdersEquity), calculated = ratios?.debtToEquityRatioTTM == null,
                    report = if (ratios?.debtToEquityRatioTTM != null) ttmBasis else sheetBasis),
            "currentRatio" to fact(ratios?.currentRatioTTM ?: Calc.divide(sheet?.totalCurrentAssets, sheet?.totalCurrentLiabilities),
                calculated = ratios?.currentRatioTTM == null, report = if (ratios?.currentRatioTTM != null) ttmBasis else sheetBasis),
            "quickRatio" to fact(ratios?.quickRatioTTM, report = ttmBasis),
            "interestCoverage" to if (trailingIncome?.interestExpense == 0.0) fact(
                null, report = ttmBasis, reason = FinancialAvailability.NON_POSITIVE_DENOMINATOR,
                note = "Interest coverage is unavailable when trailing interest expense is zero."
            ) else if (Calc.finite(ratios?.interestCoverageRatioTTM)?.let { it != 0.0 || trailingIncome?.interestExpense?.let { expense -> expense > 0 } == true } == true) fact(
                ratios?.interestCoverageRatioTTM, report = ttmBasis
            ) else fact(Calc.divide(latest?.ebit, latest?.interestExpense), true,
                note = "Annual EBIT / interest expense; unavailable for non-positive interest expense."),
            "netDebtEbitda" to fact(keys?.netDebtToEBITDATTM, report = ttmBasis)
        )
        val flow = cash.firstOrNull { it.date == latest?.date && it.period == latest?.period && it.reportedCurrency == latest?.reportedCurrency }
        val fcf = Calc.freeCashFlow(flow?.freeCashFlow, flow?.operatingCashFlow ?: flow?.netCashProvidedByOperatingActivities, flow?.capitalExpenditure)
        val cashFlow = linkedMapOf(
            "operatingCashFlow" to fact(flow?.operatingCashFlow ?: flow?.netCashProvidedByOperatingActivities, money = true),
            "capex" to fact(Calc.capexSpending(flow?.capitalExpenditure), money = true, note = "Shown as cash spent; FMP reports this outflow as negative."),
            "freeCashFlow" to fact(fcf, calculated = flow?.freeCashFlow == null, money = true),
            "fcfMargin" to fact(Calc.margin(fcf, latest?.revenue), true)
        )
        val dividendRows = dividends.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@mapNotNull null
            val amount = Calc.finite(row.adjDividend)?.takeIf { it >= 0 } ?: return@mapNotNull null
            if (date > today) null else date to amount
        }.distinct()
        val completedYear = today.year - 1
        fun dividendTotal(year: Int): Double? {
            // A provider response must extend before the year, otherwise coverage is unknown.
            if (dividendRows.none { it.first.year < year }) return null
            return dividendRows.filter { it.first.year == year }.sumOf { it.second }
        }
        val dividendGrowth = Calc.yoy(dividendTotal(completedYear), dividendTotal(completedYear - 1))
        val noTrailingDividend = ratios?.dividendPerShareTTM == 0.0 && trailingCash?.commonDividendsPaid == 0.0 && dividendRows.none { it.first > today.minusYears(1) && it.second > 0 }
        val dividendNote = if (noTrailingDividend) "No dividends paid in the trailing year." else null
        val shareholder = linkedMapOf(
            "dividendYield" to fact(ratios?.dividendYieldTTM?.takeUnless { noTrailingDividend }?.times(100), report = ttmBasis, note = dividendNote, reason = if (noTrailingDividend) FinancialAvailability.NO_DIVIDEND else FinancialAvailability.MISSING),
            "dividendPerShare" to fact(ratios?.dividendPerShareTTM?.takeUnless { noTrailingDividend }, report = ttmBasis, note = dividendNote, reason = if (noTrailingDividend) FinancialAvailability.NO_DIVIDEND else FinancialAvailability.MISSING),
            "dividendGrowth" to fact(dividendGrowth, true, report = FinancialBasis("calendar year", fiscalYear = completedYear), note = "Adjusted dividend totals; includes special payments. Coverage must be complete."),
            "payoutRatio" to fact(ratios?.dividendPayoutRatioTTM?.takeIf { trailingIncome?.netIncome?.let { n -> n > 0 } != false }?.times(100), report = ttmBasis),
            "buybacks" to fact(flow?.commonStockRepurchased?.takeIf { it <= 0 }?.let { -it }, money = true, note = "Gross common share repurchase spending."),
            "shares" to fact(shares?.outstandingShares, money = true, report = FinancialBasis("point in time", shares?.date), note = "Actual shares outstanding; not free float."),
            "sharesChange5" to fact(null, true, note = "Exact historical shares and split-adjustment consistency are not verified; no proxy is presented as actual shares change.")
        )
        val earningsPositive = trailingIncome?.netIncome?.let { it > 0 }
        val valuation = linkedMapOf(
            "pe" to fact(ratios?.priceToEarningsRatioTTM?.takeIf { it > 0 && earningsPositive != false }, report = ttmBasis,
                note = if (earningsPositive == false) "P/E is not meaningful with non-positive trailing earnings." else null,
                reason = if (earningsPositive == false || ratios?.priceToEarningsRatioTTM?.let { it <= 0 } == true) FinancialAvailability.NON_POSITIVE_DENOMINATOR else FinancialAvailability.MISSING),
            "peg" to fact(ratios?.priceToEarningsGrowthRatioTTM?.takeIf { it > 0 && earningsPositive != false }, report = ttmBasis, note = "Provider PEG; growth assumptions may differ from our historical CAGR."),
            "priceSales" to fact(ratios?.priceToSalesRatioTTM?.takeIf { it > 0 }, report = ttmBasis),
            "priceBook" to fact(ratios?.priceToBookRatioTTM?.takeIf { it > 0 }, report = ttmBasis),
            "priceFcf" to fact(ratios?.priceToFreeCashFlowRatioTTM?.takeIf { it > 0 && trailingCash?.freeCashFlow?.let { f -> f > 0 } != false }, report = ttmBasis),
            "enterpriseValue" to fact(keys?.enterpriseValueTTM ?: ratios?.enterpriseValueTTM, money = true, report = ttmBasis),
            "evEbitda" to fact((keys?.evToEBITDATTM ?: ratios?.enterpriseValueMultipleTTM)?.takeIf { trailingIncome?.ebitda?.let { e -> e > 0 } != false }, report = ttmBasis)
        )
        val nextFiscalDate = annual?.date?.let { runCatching { LocalDate.parse(it).plusYears(1) }.getOrNull() }
        val estimate = estimates.filter { row ->
            val date = row.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date != null && date > today && (nextFiscalDate == null || !date.isBefore(nextFiscalDate)) && row.numAnalystsEps?.let { it > 0 } == true
        }.minByOrNull { it.date!! }
        valuation["forwardPe"] = fact(if (quoteCurrency != null && quoteCurrency == annual?.reportedCurrency) Calc.divide(price, estimate?.epsAvg) else null, true,
            report = FinancialBasis("next fiscal year estimate", estimate?.date, currency = annual?.reportedCurrency), note = "Price / analyst consensus EPS; quote and reported currency must match. Forecasts may change; this is not next-12-month EPS.")
        val latestYear = annual?.fiscalYear?.toIntOrNull() ?: history.mapNotNull { it.fiscalYear?.toIntOrNull() }.maxOrNull()
        val historic = linkedMapOf<String, HistoricalComparison>()
        val properties: Map<String, (FmpRatios) -> Double?> = linkedMapOf(
            "pe" to { it.priceToEarningsRatio }, "priceSales" to { it.priceToSalesRatio }, "priceBook" to { it.priceToBookRatio },
            "priceFcf" to { it.priceToFreeCashFlowRatio }, "evEbitda" to { it.enterpriseValueMultiple }
        )
        for ((id, get) in properties) {
            val annualRatios = history.filter { it.period == "FY" && it.date != null && it.date <= today.toString() }
                .sortedByDescending { it.acceptedDate }.distinctBy { it.fiscalYear }.associateBy { it.fiscalYear?.toIntOrNull() }
            val observations = if (latestYear == null) emptyList() else (latestYear downTo latestYear - 4).map { year ->
                val row = annualRatios[year]
                val statement = years.firstOrNull { it.fiscalYear?.toIntOrNull() == year }
                val valid = when (id) {
                    "pe" -> statement?.netIncome?.let { it > 0 }
                    "priceSales" -> statement?.revenue?.let { it > 0 }
                    "evEbitda" -> statement?.ebitda?.let { it > 0 }
                    else -> true
                }
                FinancialObservation(year, row?.date ?: "", row?.let(get)?.takeIf { valid != false && it > 0 })
            }
            historic[id] = Calc.historical(valuation[id]?.numericValue(), observations)
        }
        if (comparable(latest, prior)) {
            val older = rows.firstOrNull {
                it.fiscalYear?.toIntOrNull() == prior?.fiscalYear?.toIntOrNull()?.minus(1) && it.period == prior?.period
            }
            val previousRate = if (comparable(prior, older)) Calc.yoy(prior?.revenue, older?.revenue) else null
            val currentRate = growth["revenueGrowth"]?.value
            if (currentRate != null && previousRate != null) {
                growth["revenueGrowth"] = growth.getValue("revenueGrowth").copy(contextSource = FinancialSource.CONTEXT_DERIVED, note = when {
                    currentRate > previousRate -> "Revenue growth accelerated versus the preceding comparable period."
                    currentRate < previousRate -> "Revenue growth slowed versus the preceding comparable period."
                    else -> "Revenue growth was unchanged versus the preceding comparable period."
                })
            }
            for ((id, get) in mapOf<String, (FmpIncomeStatement) -> Double?>(
                "grossMargin" to { it.grossProfit }, "operatingMargin" to { it.operatingIncome }, "netMargin" to { it.netIncome }
            )) {
                val nowMargin = Calc.margin(get(latest!!), latest.revenue)
                val beforeMargin = Calc.margin(get(prior!!), prior.revenue)
                if (nowMargin != null && beforeMargin != null) {
                    val delta = nowMargin - beforeMargin
                    profitability[id] = profitability.getValue(id).copy(contextSource = FinancialSource.CONTEXT_DERIVED, note =
                        "${period.replaceFirstChar { it.uppercase() }} margin ${if (delta > 0) "expanded" else if (delta < 0) "contracted" else "was unchanged"} versus the prior comparable period (${String.format(java.util.Locale.ROOT, "%.2f", delta)} percentage points).")
                }
            }
            val previousFlow = cash.firstOrNull { it.date == prior?.date && it.period == prior?.period && it.reportedCurrency == prior?.reportedCurrency }
            val previousFcf = Calc.freeCashFlow(previousFlow?.freeCashFlow, previousFlow?.operatingCashFlow, previousFlow?.capitalExpenditure)
            if (fcf != null && previousFcf != null) cashFlow["freeCashFlow"] = cashFlow.getValue("freeCashFlow").copy(contextSource = FinancialSource.CONTEXT_DERIVED, note =
                "FCF ${if (fcf > previousFcf) "increased" else if (fcf < previousFcf) "decreased" else "was unchanged"} versus the prior comparable period.")
        }
        val previousSheet = sheet?.let { current -> balance.firstOrNull {
            it.fiscalYear?.toIntOrNull() == current.fiscalYear?.toIntOrNull()?.minus(1) && it.period == current.period && it.reportedCurrency == current.reportedCurrency
        } }
        if (sheet?.totalDebt != null && previousSheet?.totalDebt != null) health["debt"] = health.getValue("debt").copy(contextSource = FinancialSource.CONTEXT_DERIVED, note =
            "Debt ${if (sheet.totalDebt > previousSheet.totalDebt) "increased" else if (sheet.totalDebt < previousSheet.totalDebt) "decreased" else "was unchanged"} versus the prior comparable balance date.")
        for ((id, current, previous) in listOf(
            Triple("cash", sheet?.cashAndCashEquivalents, previousSheet?.cashAndCashEquivalents),
            Triple("debt", sheet?.totalDebt, previousSheet?.totalDebt)
        )) {
            health[id]?.let { fact ->
                val change = Calc.yoy(current, previous)
                health[id] = fact.copy(changePercent = change,
                    contextSource = if (change != null) FinancialSource.CONTEXT_DERIVED else fact.contextSource)
            }
        }
        return CompanyFundamentals(
            symbol,
            CompanyFinancials(growth, profitability, health, cashFlow, shareholder),
            CompanyValuation(valuation, historic),
            warnings = listOf("Amounts use their reported currency and dates. Statement values and TTM ratios may cover different periods.",
                "Debt and free cash flow need different interpretation for banks. P/E is less informative for REITs; FFO/AFFO is not supplied."),
            history = history(period, income, balance, cash, today)
        )
    }

    /**
     * Merges the three statements into one row per reported period of the requested frequency,
     * newest first. Rows join on fiscal year + period, falling back to the period end date, so
     * companies with non-December fiscal years line up correctly. Nothing is annualized or summed.
     */
    fun history(
        period: String,
        income: List<FmpIncomeStatement>,
        balance: List<FmpBalanceSheetStatement>,
        cash: List<FmpCashFlowStatement>,
        today: LocalDate
    ): List<FinancialPeriodStatement> {
        val wanted = if (period == "annual") setOf("FY") else setOf("Q1", "Q2", "Q3", "Q4")
        fun usable(date: String?, kind: String?) = date != null && date <= today.toString() && kind in wanted
        fun key(year: String?, kind: String?, date: String?) = year?.toIntOrNull()?.let { "$it-$kind" } ?: "date-$date"
        val sheets = balance.filter { usable(it.date, it.period) }.associateBy { key(it.fiscalYear, it.period, it.date) }
        val sheetsByDate = balance.filter { usable(it.date, it.period) }.associateBy { it.date }
        val flows = cash.filter { usable(it.date, it.period) }.associateBy { key(it.fiscalYear, it.period, it.date) }
        val flowsByDate = cash.filter { usable(it.date, it.period) }.associateBy { it.date }
        return income.filter { usable(it.date, it.period) }
            .sortedWith(compareByDescending<FmpIncomeStatement> { it.date }.thenByDescending { it.acceptedDate })
            .distinctBy { key(it.fiscalYear, it.period, it.date) }
            .map { row ->
                val id = key(row.fiscalYear, row.period, row.date)
                val sheet = (sheets[id] ?: sheetsByDate[row.date])?.takeIf { it.reportedCurrency == null || it.reportedCurrency == row.reportedCurrency }
                val flow = (flows[id] ?: flowsByDate[row.date])?.takeIf { it.reportedCurrency == null || it.reportedCurrency == row.reportedCurrency }
                val operating = Calc.finite(flow?.operatingCashFlow ?: flow?.netCashProvidedByOperatingActivities)
                FinancialPeriodStatement(
                    period = row.period.orEmpty(),
                    fiscalYear = row.fiscalYear?.toIntOrNull(),
                    date = row.date,
                    currency = row.reportedCurrency,
                    revenue = Calc.finite(row.revenue),
                    grossProfit = Calc.finite(row.grossProfit),
                    operatingIncome = Calc.finite(row.operatingIncome),
                    netIncome = Calc.finite(row.netIncome),
                    epsDiluted = Calc.finite(row.epsDiluted),
                    operatingCashFlow = operating,
                    capitalExpenditure = Calc.capexSpending(flow?.capitalExpenditure),
                    freeCashFlow = flow?.let { Calc.freeCashFlow(it.freeCashFlow, operating, it.capitalExpenditure) },
                    dividendsPaid = Calc.finite(flow?.commonDividendsPaid)?.takeIf { it <= 0 }?.let { -it },
                    cash = Calc.finite(sheet?.cashAndCashEquivalents),
                    totalDebt = Calc.finite(sheet?.totalDebt),
                    totalAssets = Calc.finite(sheet?.totalAssets),
                    totalLiabilities = Calc.finite(sheet?.totalLiabilities),
                    equity = Calc.finite(sheet?.totalStockholdersEquity),
                    currentAssets = Calc.finite(sheet?.totalCurrentAssets),
                    currentLiabilities = Calc.finite(sheet?.totalCurrentLiabilities)
                )
            }
    }
}
