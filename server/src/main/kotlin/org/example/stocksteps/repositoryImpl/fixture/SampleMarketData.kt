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
            marketCap = (price * shares).toLong()
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

    /** About five years of weekday closes, oldest first, ending at [lastPrice]. */
    fun dailyCloses(symbol: String, lastPrice: Double, sessionDate: LocalDate): List<PricePoint> {
        val rng = rng(symbol, "daily")
        val days = generateSequence(sessionDate) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(DAILY_POINTS).toList()
        var close = lastPrice
        val closes = days.map { day ->
            PricePoint(day.toString(), round2(close)).also {
                close = (close / (1 + 0.0004 + rng.nextGaussian() * 0.018)).coerceAtLeast(0.5)
            }
        }
        return closes.reversed()
    }

    /** One session of 5-minute closes from the previous close to [price]. */
    fun intraday(symbol: String, previousClose: Double, price: Double, sessionDate: LocalDate): List<PricePoint> {
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
            PricePoint(time, round2(drift * (1 + walk[i] - walk[last] * i / last)))
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
        val annualNetIncome = annualRevenue * netMargin / 100
        val pe = marketCap / annualNetIncome
        val divisor = if (quarter) 4 else 1
        val year = sessionDate(quote).year - 1
        val basis = FinancialBasis(period = if (quarter) "quarter" else "annual", date = "$year-12-31", fiscalYear = year, currency = "USD")
        val ttm = FinancialBasis(period = "TTM", date = "$year-12-31", fiscalYear = year, currency = "USD")
        fun value(v: Double, b: FinancialBasis = basis) = FinancialFact(value = round2(v), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = b)
        fun amount(v: Double) = FinancialFact(amount = (v / divisor).toLong(), source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, basis = basis)
        val grossMargin = netMargin + 15 + rng.nextDouble() * 35
        val operatingMargin = netMargin + 2 + rng.nextDouble() * 10
        val operatingCashFlow = annualNetIncome * (1.1 + rng.nextDouble() * 0.4)
        val freeCashFlow = operatingCashFlow * (0.5 + rng.nextDouble() * 0.4)
        val history = (1..5).map { pe * (0.7 + rng.nextDouble() * 0.6) }
        val average = history.average()
        val dividend = if (rng.nextDouble() < 0.35) FinancialFact(availability = FinancialAvailability.NO_DIVIDEND, basis = ttm)
            else value(0.2 + rng.nextDouble() * 3, ttm)
        return CompanyFundamentals(
            symbol = symbol,
            financials = CompanyFinancials(
                growth = mapOf(
                    "revenue" to amount(annualRevenue), "revenueGrowth" to value(rng.nextGaussian() * 12 + 6),
                    "netIncome" to amount(annualNetIncome), "netIncomeGrowth" to value(rng.nextGaussian() * 18 + 5),
                    "eps" to value(price / pe / divisor), "epsGrowth" to value(rng.nextGaussian() * 18 + 5)
                ),
                profitability = mapOf("grossMargin" to value(grossMargin), "operatingMargin" to value(operatingMargin), "netMargin" to value(netMargin)),
                financialHealth = mapOf(
                    "cash" to amount(annualRevenue * (0.05 + rng.nextDouble() * 0.4)), "debt" to amount(annualRevenue * rng.nextDouble() * 0.6),
                    "debtEquity" to value(rng.nextDouble() * 1.8), "currentRatio" to value(0.8 + rng.nextDouble() * 2.5)
                ),
                cashFlow = mapOf(
                    "operatingCashFlow" to amount(operatingCashFlow), "freeCashFlow" to amount(freeCashFlow),
                    "fcfMargin" to value(freeCashFlow / annualRevenue * 100)
                ),
                shareholderReturns = mapOf("dividendYield" to dividend)
            ),
            valuation = CompanyValuation(
                metrics = mapOf("pe" to value(pe, ttm), "priceSales" to value(priceSales, ttm)),
                historical = mapOf("pe" to HistoricalComparison(
                    observations = history.mapIndexed { i, v -> FinancialObservation(year - 4 + i, "${year - 4 + i}-12-31", round2(v)) },
                    average = round2(average), median = round2(history.sorted()[2]),
                    minimum = round2(history.min()), maximum = round2(history.max()), validCount = history.size,
                    differencePercent = round2((pe / average - 1) * 100), reliable = true
                ))
            ),
            warnings = listOf("Sample values for development; not real financial data."),
            retrievedAt = now().toString()
        )
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
