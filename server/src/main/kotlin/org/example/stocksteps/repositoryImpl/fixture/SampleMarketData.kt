package org.example.stocksteps.repositoryImpl.fixture

import org.example.stocksteps.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlin.math.abs
import kotlin.math.round

/**
 * MOCK-mode stand-in values for tickers without captured fixtures. Values are seeded by
 * symbol, so a ticker always gets the same numbers, and price series end at the ticker's
 * quote so charts, sparklines and the displayed price agree. Never used in REAL mode.
 */
internal class SampleMarketData(private val now: () -> Instant) {

    fun quote(symbol: String, name: String?): StockQuote {
        val rng = rng(symbol, "quote")
        val price = round2(8.0 + rng.nextDouble() * 320.0)
        val changePercent = round2((rng.nextGaussian() * 2.0).coerceIn(-6.0, 6.0))
        val previousClose = round2(price / (1 + changePercent / 100))
        val shares = 50_000_000L + (rng.nextDouble() * 4_950_000_000L).toLong()
        return StockQuote(
            symbol = symbol,
            companyName = name ?: sampleName(symbol),
            price = price,
            change = round2(price - previousClose),
            changePercent = changePercent,
            dayHigh = round2(maxOf(price, previousClose) * (1 + rng.nextDouble() * 0.01)),
            dayLow = round2(minOf(price, previousClose) * (1 - rng.nextDouble() * 0.01)),
            previousClose = previousClose,
            volume = 200_000L + (rng.nextDouble() * 40_000_000).toLong(),
            timestamp = now().epochSecond,
            marketCap = (price * shares).toLong(),
            open = round2(previousClose * (1 + rng.nextGaussian() * 0.004)),
            yearHigh = round2(maxOf(price, previousClose) * (1.08 + rng.nextDouble() * 0.4)),
            yearLow = round2(minOf(price, previousClose) * (0.55 + rng.nextDouble() * 0.35))
        )
    }

    fun profile(symbol: String, name: String?): CompanyProfile {
        val (sector, industry) = SECTORS[rng(symbol, "profile").nextInt(SECTORS.size)]
        return CompanyProfile(
            symbol = symbol,
            companyName = name ?: sampleName(symbol),
            description = "Sample profile for development. This is not real information about $symbol.",
            sector = sector,
            industry = industry,
            country = "US",
            currency = "USD",
            exchange = "NASDAQ",
            logoUrl = "https://images.financialmodelingprep.com/symbol/$symbol.png"
        )
    }

    /**
     * About five years of weekday closes, oldest first, ending at [lastPrice]; the close before
     * it is [previousClose] so ranges agree with today's quote change.
     */
    fun dailyCloses(symbol: String, lastPrice: Double, previousClose: Double?, sessionDate: LocalDate): List<PricePoint> {
        val rng = rng(symbol, "daily")
        val days = generateSequence(sessionDate) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(DAILY_POINTS).toList()
        var close = lastPrice
        val closes = days.mapIndexed { index, day ->
            PricePoint(day.toString(), round2(close)).also {
                close = if (index == 0 && previousClose != null && previousClose > 0) previousClose
                    else (close / (1 + 0.0004 + rng.nextGaussian() * 0.018)).coerceAtLeast(0.01)
            }
        }
        return closes.reversed()
    }

    /** One session of 5-minute closes from the previous close to [price], kept inside the day's low/high when known. */
    fun intraday(symbol: String, previousClose: Double, price: Double, sessionDate: LocalDate, low: Double? = null, high: Double? = null): List<PricePoint> {
        val rng = rng(symbol, "intraday")
        // Noise grows with the day's move so large movers still get a lifelike, wiggly line.
        val volatility = maxOf(0.0015, abs(price / previousClose - 1) * 0.04)
        val walk = DoubleArray(INTRADAY_POINTS)
        for (i in 1 until INTRADAY_POINTS) walk[i] = walk[i - 1] + rng.nextGaussian() * volatility
        val last = INTRADAY_POINTS - 1
        return (0 until INTRADAY_POINTS).map { i ->
            // Bridge: the noise returns to zero at the close, so the line ends exactly at the quote.
            val drift = previousClose + (price - previousClose) * i / last
            val time = sessionDate.atTime(OPEN.plusMinutes(5L * i)).format(INTRADAY_TIME)
            val close = drift * (1 + walk[i] - walk[last] * i / last)
            val bounded = if (low != null && high != null && high > low && i in 1 until last) close.coerceIn(low, high) else close
            PricePoint(time, round2(bounded))
        }
    }

    fun fundamentals(symbol: String, period: String, quote: StockQuote): CompanyFundamentals {
        val rng = rng(symbol, "fundamentals")
        val quarter = period == "quarter"
        val marketCap = quote.marketCap?.toDouble() ?: 10_000_000_000.0
        val price = quote.price ?: 100.0
        val priceSales = 1.5 + rng.nextDouble() * 10
        val annualRevenue = marketCap / priceSales
        val netMargin = 4.0 + rng.nextDouble() * 26
        val pe = marketCap / (annualRevenue * netMargin / 100)
        val year = sessionDate(quote).year - 1
        val grossMargin = netMargin + 15 + rng.nextDouble() * 35
        val operatingMargin = netMargin + 2 + rng.nextDouble() * 10
        val cashToRevenue = 0.05 + rng.nextDouble() * 0.4
        val debtToRevenue = rng.nextDouble() * 0.6
        val shares = marketCap / price
        // Statement history first; every latest-period fact below is read back from it.
        val annual = (5 downTo 0).fold(listOf<FinancialPeriodStatement>()) { rows, back ->
            val fiscalYear = year - back
            val revenue = annualRevenue / (1..back).fold(1.0) { total, _ -> total * (1.03 + rng.nextDouble() * 0.15) }
            val margin = (netMargin + rng.nextGaussian() * 2) / 100
            val operating = revenue * margin * (1.1 + rng.nextDouble() * 0.4)
            val capex = operating * (0.15 + rng.nextDouble() * 0.35)
            rows + FinancialPeriodStatement(
                period = "FY", fiscalYear = fiscalYear, date = "$fiscalYear-12-31", currency = "USD",
                revenue = round0(revenue), grossProfit = round0(revenue * grossMargin / 100), operatingIncome = round0(revenue * operatingMargin / 100),
                netIncome = round0(revenue * margin), epsDiluted = round2(revenue * margin / shares),
                operatingCashFlow = round0(operating), capitalExpenditure = round0(capex), freeCashFlow = round0(operating) - round0(capex),
                cash = round0(revenue * cashToRevenue), totalDebt = round0(revenue * debtToRevenue),
                totalAssets = round0(revenue * 1.6), totalLiabilities = round0(revenue * 0.8), equity = round0(revenue * 0.8),
                currentAssets = round0(revenue * 0.6), currentLiabilities = round0(revenue * 0.35)
            )
        }.reversed()
        val history = if (quarter) quarters(annual) else annual
        val latest = history.first()
        val previous = history.firstOrNull { it.period == latest.period && it.fiscalYear == latest.fiscalYear?.minus(1) }
        val basis = FinancialBasis(period = if (quarter) latest.period else "annual", date = latest.date, fiscalYear = latest.fiscalYear, currency = "USD")
        val ttm = FinancialBasis(period = "TTM", date = "$year-12-31", fiscalYear = year, currency = "USD")
        fun value(v: Double?, b: FinancialBasis = basis) = v?.let { FinancialFact(value = round2(it), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = b) }
            ?: FinancialFact(availability = FinancialAvailability.INSUFFICIENT_HISTORY, basis = b)
        fun amount(v: Double?) = FinancialFact(amount = v?.toLong(), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = basis)
        fun change(get: (FinancialPeriodStatement) -> Double?) = previous?.let { p -> get(p)?.takeIf { it > 0 }?.let { (get(latest)!! - it) / it * 100 } }
        val dividend = if (rng.nextDouble() < 0.35) FinancialFact(availability = FinancialAvailability.NO_DIVIDEND, basis = ttm)
            else value(0.2 + rng.nextDouble() * 3, ttm)
        val revenue = latest.revenue!!
        return CompanyFundamentals(
            symbol = symbol,
            financials = CompanyFinancials(
                growth = mapOf(
                    "revenue" to amount(revenue), "revenueGrowth" to value(change { it.revenue }),
                    "netIncome" to amount(latest.netIncome), "netIncomeGrowth" to value(change { it.netIncome }),
                    "eps" to value(latest.epsDiluted), "epsGrowth" to value(change { it.epsDiluted })
                ),
                profitability = mapOf(
                    "grossMargin" to value(latest.grossProfit!! / revenue * 100), "operatingMargin" to value(latest.operatingIncome!! / revenue * 100),
                    "netMargin" to value(latest.netIncome!! / revenue * 100)
                ),
                financialHealth = mapOf(
                    "cash" to amount(latest.cash), "debt" to amount(latest.totalDebt),
                    "assets" to amount(latest.totalAssets), "liabilities" to amount(latest.totalLiabilities), "equity" to amount(latest.equity),
                    "debtEquity" to value(latest.totalDebt!! / latest.equity!!), "currentRatio" to value(latest.currentAssets!! / latest.currentLiabilities!!)
                ),
                cashFlow = mapOf(
                    "operatingCashFlow" to amount(latest.operatingCashFlow), "capex" to amount(latest.capitalExpenditure),
                    "freeCashFlow" to amount(latest.freeCashFlow), "fcfMargin" to value(latest.freeCashFlow!! / revenue * 100)
                ),
                shareholderReturns = mapOf("dividendYield" to dividend)
            ),
            valuation = CompanyValuation(
                metrics = mapOf("pe" to value(pe, ttm), "priceSales" to value(priceSales, ttm)),
                historical = mapOf("pe" to peHistory(symbol, pe, year))
            ),
            datasets = mapOf("income" to FinancialAvailability.AVAILABLE, "cashFlow" to FinancialAvailability.AVAILABLE, "balance" to FinancialAvailability.AVAILABLE),
            warnings = listOf("Sample values for development; not real financial data."),
            retrievedAt = now().toString(),
            history = history
        )
    }

    /** Eight calendar quarters from the two latest fiscal years: flows split by season, balances interpolated. */
    private fun quarters(annual: List<FinancialPeriodStatement>): List<FinancialPeriodStatement> {
        val season = listOf(0.23, 0.24, 0.25, 0.28)
        return annual.take(2).zip(annual.drop(1).take(2)).flatMap { (fy, prior) ->
            (0 until 4).map { q ->
                fun flow(v: Double?) = v?.let { round0(it * season[q]) }
                fun point(a: Double?, b: Double?) = if (a != null && b != null) round0(b + (a - b) * (q + 1) / 4.0) else null
                val operating = flow(fy.operatingCashFlow)
                val capex = flow(fy.capitalExpenditure)
                fy.copy(
                    period = "Q${q + 1}", date = "${fy.fiscalYear}-${listOf("03-31", "06-30", "09-30", "12-31")[q]}",
                    revenue = flow(fy.revenue), grossProfit = flow(fy.grossProfit), operatingIncome = flow(fy.operatingIncome),
                    netIncome = flow(fy.netIncome), epsDiluted = fy.epsDiluted?.let { round2(it * season[q]) },
                    operatingCashFlow = operating, capitalExpenditure = capex, freeCashFlow = if (operating != null && capex != null) operating - capex else null,
                    dividendsPaid = flow(fy.dividendsPaid),
                    cash = point(fy.cash, prior.cash), totalDebt = point(fy.totalDebt, prior.totalDebt), totalAssets = point(fy.totalAssets, prior.totalAssets),
                    totalLiabilities = point(fy.totalLiabilities, prior.totalLiabilities), equity = point(fy.equity, prior.equity),
                    currentAssets = point(fy.currentAssets, prior.currentAssets), currentLiabilities = point(fy.currentLiabilities, prior.currentLiabilities)
                )
            }.reversed()
        }
    }

    private fun round0(value: Double) = kotlin.math.round(value)

    /** Five yearly P/E observations around [pe] with the comparison the backend would calculate. */
    fun peHistory(symbol: String, pe: Double, year: Int): HistoricalComparison {
        val rng = rng(symbol, "history")
        val history = (1..5).map { pe * (0.7 + rng.nextDouble() * 0.6) }
        val average = history.average()
        return HistoricalComparison(
            observations = history.mapIndexed { i, v -> FinancialObservation(year - 4 + i, "${year - 4 + i}-12-31", round2(v)) },
            average = round2(average), median = round2(history.sorted()[2]),
            minimum = round2(history.min()), maximum = round2(history.max()), validCount = history.size,
            differencePercent = round2((pe / average - 1) * 100), reliable = true
        )
    }

    /** Fills quote fields a captured quote lacks (open, volume, market cap) in proportion to its price. */
    fun fillQuote(stored: StockQuote): StockQuote {
        val price = stored.price ?: return stored
        val sample = quote(stored.symbol, stored.companyName)
        val samplePrice = sample.price ?: return stored
        val reference = stored.previousClose ?: price
        return stored.copy(
            open = stored.open ?: sample.open?.let { round2(reference * it / (sample.previousClose ?: samplePrice)) },
            volume = stored.volume ?: sample.volume,
            marketCap = stored.marketCap ?: sample.marketCap?.let { (it / samplePrice * price).toLong() }
        )
    }

    /**
     * Keeps every captured fact and fills only missing ones from [generated]. A captured P/E gets a
     * history built around it, so the comparison stays consistent with the real value.
     */
    fun fillFundamentals(stored: CompanyFundamentals, generated: CompanyFundamentals, year: Int): CompanyFundamentals {
        fun merge(captured: Map<String, FinancialFact>, sample: Map<String, FinancialFact>) =
            sample + captured.filterValues { it.availability == FinancialAvailability.AVAILABLE || it.availability == FinancialAvailability.NO_DIVIDEND }
        val financials = CompanyFinancials(
            growth = merge(stored.financials.growth, generated.financials.growth),
            profitability = merge(stored.financials.profitability, generated.financials.profitability),
            financialHealth = merge(stored.financials.financialHealth, generated.financials.financialHealth),
            cashFlow = merge(stored.financials.cashFlow, generated.financials.cashFlow),
            shareholderReturns = merge(stored.financials.shareholderReturns, generated.financials.shareholderReturns)
        )
        val metrics = merge(stored.valuation.metrics, generated.valuation.metrics)
        val pe = metrics["pe"]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value
        val history = stored.valuation.historical["pe"]?.takeIf { it.reliable }
            ?: pe?.let { peHistory(stored.symbol, it, year) }
        return stored.copy(
            history = stored.history.ifEmpty { generated.history },
            datasets = if (stored.history.isEmpty()) generated.datasets else stored.datasets,
            financials = financials,
            valuation = CompanyValuation(metrics, stored.valuation.historical + listOfNotNull(history?.let { "pe" to it })),
            warnings = (stored.warnings + "Some values are sample data for development.").distinct()
        )
    }

    /**
     * 24 calendar quarters of diluted EPS whose implied P/E wanders around [pe], from this ticker's
     * own closes, filed 35 days after each quarter (so the series never uses unfiled earnings).
     */
    fun quarterlyEarnings(symbol: String, closes: List<PricePoint>, pe: Double): List<org.example.stocksteps.service.QuarterlyEarnings> {
        if (closes.isEmpty() || pe <= 0) return emptyList()
        val rng = rng(symbol, "earnings")
        val last = LocalDate.parse(closes.last().time.take(10))
        val shares = 1_000_000_000.0
        return (0 until 24).mapNotNull { back ->
            val quarterEnd = last.withDayOfMonth(1).minusMonths((last.monthValue - 1) % 3 + 3L * back).minusDays(1)
            val price = closes.lastOrNull { it.time.take(10) <= quarterEnd.toString() }?.close ?: return@mapNotNull null
            val impliedPe = pe * (0.75 + rng.nextDouble() * 0.5)
            org.example.stocksteps.service.QuarterlyEarnings(
                periodEnd = quarterEnd.toString(), availableOn = quarterEnd.plusDays(35).toString(),
                epsDiluted = round2(price / impliedPe / 4), shares = shares, currency = "USD"
            )
        }.reversed()
    }

    /** Trading date of the quote (New York time), stepping back from weekends. */
    fun sessionDate(quote: StockQuote?): LocalDate {
        var date = Instant.ofEpochSecond(quote?.timestamp ?: now().epochSecond).atZone(NEW_YORK).toLocalDate()
        while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) date = date.minusDays(1)
        return date
    }

    private fun sampleName(symbol: String) = "$symbol Sample Company"
    private fun rng(symbol: String, salt: String) = Random("$salt:$symbol".hashCode().toLong())
    private fun round2(value: Double) = round(value * 100) / 100

    companion object {
        const val DAILY_POINTS = 1260
        const val INTRADAY_POINTS = 78
        private val OPEN: LocalTime = LocalTime.of(9, 30)
        private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        private val INTRADAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val SECTORS = listOf(
            "Technology" to "Software - Application", "Healthcare" to "Biotechnology",
            "Consumer Cyclical" to "Specialty Retail", "Industrials" to "Specialty Industrial Machinery",
            "Financial Services" to "Asset Management", "Energy" to "Oil & Gas E&P"
        )

        /** A stored series matches the quote when its last close is within [tolerance] of the price. */
        fun matches(points: List<Double>, price: Double?, tolerance: Double): Boolean {
            val last = points.lastOrNull() ?: return false
            return price == null || price <= 0 || abs(last / price - 1) <= tolerance
        }
    }
}
