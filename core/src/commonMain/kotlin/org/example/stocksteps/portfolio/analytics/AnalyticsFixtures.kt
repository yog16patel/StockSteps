package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.*
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sin

/** Benchmarks offered for comparison. All are index levels (price return), not funds. */
object BenchmarkCatalog {
    val all: List<BenchmarkInfo> = listOf(
        BenchmarkInfo(BenchmarkId.SP500, "S&P 500", "^GSPC", PortfolioCurrency.USD, ReturnBasis.PRICE_RETURN),
        BenchmarkInfo(BenchmarkId.TSX, "S&P/TSX Composite", "^GSPTSE", PortfolioCurrency.CAD, ReturnBasis.PRICE_RETURN),
        BenchmarkInfo(BenchmarkId.NASDAQ, "NASDAQ Composite", "^IXIC", PortfolioCurrency.USD, ReturnBasis.PRICE_RETURN)
    )
    fun info(id: BenchmarkId) = all.first { it.id == id }
    /** Default: the home-market index for the account's reporting currency. */
    fun default(reporting: PortfolioCurrency) = if (reporting == PortfolioCurrency.CAD) BenchmarkId.TSX else BenchmarkId.SP500
    fun parse(value: String?) = BenchmarkId.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
}

/** A read-only Insights scenario. Its numbers come from [PortfolioAnalyticsEngine], never hand-written. */
data class AnalyticsFixture(
    val id: String,
    val title: String,
    val inputs: AnalyticsInputs,
    val entitlements: Entitlements,
    val benchmarks: Map<BenchmarkId, BenchmarkSeries>
) {
    fun analytics(period: AnalyticsPeriod = AnalyticsPeriod.ONE_YEAR, benchmark: BenchmarkId? = null): PortfolioAnalytics {
        val account = inputs.ledger.accounts.first { it.id == inputs.accountId }
        val chosen = benchmark ?: BenchmarkCatalog.default(account.reportingCurrency)
        val series = benchmarks[chosen]
        val withBenchmark = inputs.copy(benchmark = series)
        val result = PortfolioAnalyticsEngine.analyze(withBenchmark, period, entitlements.tier)
        return if (series == null && entitlements.plus) result.copy(
            benchmark = BenchmarkComparison(BenchmarkCatalog.info(chosen), Availability.UNAVAILABLE, currency = account.reportingCurrency,
                notes = listOf("Benchmark data for ${BenchmarkCatalog.info(chosen).name} isn't available right now. No return is shown instead of a guess."))
        ) else result
    }
}

/**
 * Deterministic development scenarios for Portfolio Intelligence (MOCK only, never written to a
 * user's database). Prices and FX are generated from fixed formulas on weekdays so every value can
 * be re-derived; trades execute at that day's close so the ledger and price history agree.
 */
object AnalyticsFixtureCatalog {
    const val TODAY = "2026-10-08"
    val ids = listOf(
        "new-portfolio", "single-holding", "multiple-holdings", "concentrated", "diversified", "mixed-currency",
        "dividends", "deposits", "withdrawals", "partial-sales", "outperforming", "underperforming",
        "insufficient-history", "missing-sectors", "missing-benchmark", "missing-fx", "partial-failure",
        "free", "plus", "expired"
    )
    private const val HISTORY_START = "2024-09-03"

    private data class Security(val ref: InstrumentRef, val currency: PortfolioCurrency, val base: Double, val drift: Double, val phase: Double, val meta: SecurityMetadata)

    private val securities = listOf(
        Security(InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"), PortfolioCurrency.USD, 220.0, 0.00045, 0.0, SecurityMetadata("Apple", "Technology", AssetClass.STOCK)),
        Security(InstrumentRef("MSFT", "Microsoft", "NASDAQ", "USD"), PortfolioCurrency.USD, 410.0, 0.00035, 1.1, SecurityMetadata("Microsoft", "Technology", AssetClass.STOCK)),
        Security(InstrumentRef("JNJ", "Johnson & Johnson", "NYSE", "USD"), PortfolioCurrency.USD, 160.0, 0.00005, 2.3, SecurityMetadata("Johnson & Johnson", "Healthcare", AssetClass.STOCK)),
        Security(InstrumentRef("TD.TO", "Toronto-Dominion Bank", "TSX", "CAD"), PortfolioCurrency.CAD, 78.0, 0.00025, 0.7, SecurityMetadata("Toronto-Dominion Bank", "Financial Services", AssetClass.STOCK)),
        Security(InstrumentRef("ENB.TO", "Enbridge", "TSX", "CAD"), PortfolioCurrency.CAD, 55.0, 0.0002, 3.1, SecurityMetadata("Enbridge", "Energy", AssetClass.STOCK)),
        Security(InstrumentRef("SHOP.TO", "Shopify", "TSX", "CAD"), PortfolioCurrency.CAD, 105.0, -0.0004, 4.2, SecurityMetadata("Shopify", "Technology", AssetClass.STOCK)),
        Security(InstrumentRef("XIU.TO", "iShares S&P/TSX 60 Index ETF", "TSX", "CAD"), PortfolioCurrency.CAD, 34.0, 0.0003, 5.0, SecurityMetadata("iShares S&P/TSX 60 Index ETF", null, AssetClass.ETF)),
        Security(InstrumentRef("VFV.TO", "Vanguard S&P 500 Index ETF", "TSX", "CAD"), PortfolioCurrency.CAD, 130.0, 0.00045, 5.6, SecurityMetadata("Vanguard S&P 500 Index ETF", null, AssetClass.ETF))
    ).associateBy { it.ref.symbol }

    private fun isWeekday(date: String) = (AnalyticsDates.day(date) + 3).mod(7) < 5 // 1970-01-01 was a Thursday
    private fun weekdays(from: String, through: String) = (AnalyticsDates.day(from)..AnalyticsDates.day(through)).map(AnalyticsDates::fromDay).filter(::isWeekday)
    private fun money(value: Double, places: Int = 2): String {
        val factor = listOf(1.0, 10.0, 100.0, 1000.0, 10000.0)[places]
        val scaled = round(value * factor).toLong()
        return (Decimal.parse(scaled.toString()) / Decimal.parse(factor.toLong().toString())).toString()
    }

    private fun close(security: Security, date: String, driftShift: Double = 0.0): String {
        val t = (AnalyticsDates.day(date) - AnalyticsDates.day(HISTORY_START)).toDouble()
        return money(security.base * exp((security.drift + driftShift) * t + 0.035 * sin(t / 11.0 + security.phase)))
    }

    private fun usdCad(date: String): String {
        val t = (AnalyticsDates.day(date) - AnalyticsDates.day(HISTORY_START)).toDouble()
        return money(1.355 + 0.025 * sin(t / 47.0) + 0.00002 * t, 4)
    }

    private fun benchmarkLevels(base: Double, drift: Double, phase: Double): Map<String, String> = weekdays(HISTORY_START, TODAY).associateWith { date ->
        val t = (AnalyticsDates.day(date) - AnalyticsDates.day(HISTORY_START)).toDouble()
        money(base * exp(drift * t + 0.02 * sin(t / 13.0 + phase)))
    }

    /** Next weekday on or after [date]. */
    private fun trading(date: String): String { var d = date; while (!isWeekday(d)) d = AnalyticsDates.plusDays(d, 1); return d }

    fun get(id: String): AnalyticsFixture {
        require(id in ids) { "Unknown analytics scenario." }
        val account = PortfolioAccount("insights-$id", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
        val drift = when (id) { "outperforming" -> 0.0004; "underperforming" -> -0.0005; else -> 0.0 }
        val transactions = mutableListOf<PortfolioTransaction>()
        fun tx(type: TransactionType, date: String, currency: PortfolioCurrency, symbol: String? = null, quantity: String = "0", amount: String = "0", fees: String = "0", price: String? = null) {
            val security = symbol?.let { securities.getValue(it) }
            val day = trading(date)
            val unit = price ?: if (security != null && type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.DIVIDEND_REINVESTMENT, TransactionType.OPENING_POSITION)) close(security, day, drift) else "0"
            transactions += PortfolioTransaction("fx-$id-${transactions.size + 1}", account.id, type, day, currency, security?.ref,
                quantity, unit, amount, fees, sequence = transactions.size.toLong(), createdAt = transactions.size.toLong())
        }
        fun deposit(date: String, amount: String, currency: PortfolioCurrency = PortfolioCurrency.CAD) = tx(TransactionType.CASH_DEPOSIT, date, currency, amount = amount)
        fun buy(date: String, symbol: String, quantity: String) = tx(TransactionType.BUY, date, securities.getValue(symbol).currency, symbol, quantity, fees = "4.95")

        val start = when (id) { "insufficient-history" -> "2026-09-21"; else -> "2024-10-01" }
        when (id) {
            "new-portfolio" -> Unit
            "single-holding" -> { deposit(start, "10000"); buy(start, "TD.TO", "120") }
            "concentrated" -> { deposit(start, "20000"); buy(start, "SHOP.TO", "140"); buy(start, "TD.TO", "20"); buy(start, "ENB.TO", "15") }
            "diversified" -> {
                deposit(start, "30000"); deposit(start, "12000", PortfolioCurrency.USD)
                buy(start, "TD.TO", "60"); buy(start, "ENB.TO", "80"); buy(start, "XIU.TO", "150"); buy(start, "VFV.TO", "40"); buy(start, "SHOP.TO", "30")
                buy(start, "AAPL", "15"); buy(start, "MSFT", "8"); buy(start, "JNJ", "20")
            }
            "mixed-currency", "missing-fx" -> {
                deposit(start, "15000"); deposit(start, "10000", PortfolioCurrency.USD)
                buy(start, "TD.TO", "80"); buy(start, "XIU.TO", "120"); buy(start, "AAPL", "25"); buy(start, "MSFT", "10")
            }
            "dividends" -> {
                deposit(start, "15000"); buy(start, "TD.TO", "90"); buy(start, "ENB.TO", "120")
                weekdays(start, TODAY).map { it.take(7) }.distinct().filter { it.substring(5, 7).toInt() % 3 == 1 }.forEach { month ->
                    if (trading("$month-28") <= TODAY) tx(TransactionType.DIVIDEND, "$month-28", PortfolioCurrency.CAD, "TD.TO", amount = money(90 * 1.02))
                    if (trading("$month-15") <= TODAY) tx(TransactionType.DIVIDEND_REINVESTMENT, "$month-15", PortfolioCurrency.CAD, "ENB.TO", "1.5")
                }
            }
            "deposits" -> {
                deposit(start, "5000"); buy(start, "XIU.TO", "140")
                listOf("2025-01-15", "2025-04-15", "2025-07-15", "2025-10-15", "2026-01-15", "2026-04-15", "2026-07-15").forEach { date ->
                    deposit(date, "2000"); buy(date, "XIU.TO", "45")
                }
            }
            "withdrawals" -> {
                deposit(start, "25000"); buy(start, "VFV.TO", "150"); buy(start, "TD.TO", "60")
                tx(TransactionType.SELL, "2025-06-02", PortfolioCurrency.CAD, "VFV.TO", "30", fees = "4.95")
                tx(TransactionType.CASH_WITHDRAWAL, "2025-06-04", PortfolioCurrency.CAD, amount = "4000")
                tx(TransactionType.CASH_WITHDRAWAL, "2026-03-02", PortfolioCurrency.CAD, amount = "1500")
            }
            "partial-sales" -> {
                deposit(start, "20000"); buy(start, "AAPL", "30"); deposit(start, "8000", PortfolioCurrency.USD)
                buy(start, "TD.TO", "100")
                tx(TransactionType.SELL, "2025-08-11", PortfolioCurrency.USD, "AAPL", "10", fees = "4.95")
                tx(TransactionType.SELL, "2026-02-10", PortfolioCurrency.CAD, "TD.TO", "40", fees = "4.95")
            }
            else -> { // multiple-holdings, outperforming, underperforming, insufficient-history, missing-*, partial-failure, free, plus, expired
                deposit(start, "20000"); deposit(start, "6000", PortfolioCurrency.USD)
                buy(start, "TD.TO", "70"); buy(start, "ENB.TO", "90"); buy(start, "XIU.TO", "100"); buy(start, "AAPL", "12"); buy(start, "MSFT", "5")
                if (id != "insufficient-history") { deposit("2025-09-02", "3000"); buy("2025-09-02", "SHOP.TO", "25") }
            }
        }
        // A deposit must fund its same-day buys: re-sequence so deposits come first each day.
        val ledger = PortfolioLedger(listOf(account), transactions.sortedWith(compareBy({ it.tradeDate }, { if (it.type == TransactionType.CASH_DEPOSIT) 0 else 1 }, { it.sequence }))
            .mapIndexed { index, it -> it.copy(sequence = index.toLong()) }, 1)

        val held = transactions.mapNotNull { it.instrument?.symbol }.distinct().map(securities::getValue)
        val days = weekdays(HISTORY_START, TODAY)
        val closes = held.associate { security -> security.ref.symbol to days.filter { it < TODAY }.associateWith { close(security, it, drift) } }
        val fxByDate = if (id == "missing-fx") emptyMap() else days.associateWith { mapOf(PortfolioCurrency.USD to usdCad(it)) }
        val currentPrices = held.filterNot { id == "partial-failure" && it.ref.symbol == "MSFT" }.associate { it.ref.symbol to close(it, TODAY, drift) }
        val current = PortfolioPrices(TODAY, currentPrices, mapOf(PortfolioCurrency.USD to usdCad(TODAY)))
        val metadata = held.associate { it.ref.symbol to if (id == "missing-sectors") it.meta.copy(sector = null, assetClass = AssetClass.UNCLASSIFIED) else it.meta }
        val benchmarks = if (id == "missing-benchmark") emptyMap() else mapOf(
            BenchmarkId.SP500 to BenchmarkSeries(BenchmarkCatalog.info(BenchmarkId.SP500), benchmarkLevels(5600.0, 0.00035, 0.4)),
            BenchmarkId.TSX to BenchmarkSeries(BenchmarkCatalog.info(BenchmarkId.TSX), benchmarkLevels(23400.0, 0.00028, 1.7)),
            BenchmarkId.NASDAQ to BenchmarkSeries(BenchmarkCatalog.info(BenchmarkId.NASDAQ), benchmarkLevels(17700.0, 0.00042, 2.9))
        )
        val entitlements = when (id) {
            "free" -> Entitlements(SubscriptionTier.FREE, EntitlementStatus.NONE)
            "expired" -> Entitlements(SubscriptionTier.FREE, EntitlementStatus.EXPIRED, expiresAt = 1_788_000_000_000, source = "subscription")
            else -> Entitlements(SubscriptionTier.PLUS, EntitlementStatus.ACTIVE, source = "mock", features = EntitlementFeatures.PLUS)
        }
        val title = id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        return AnalyticsFixture(id, title, AnalyticsInputs(ledger, account.id, TODAY, closes, fxByDate, current, metadata, null, "${TODAY}T20:00:00Z"), entitlements, benchmarks)
    }
}

/** Feature ids granted by each tier, shared by server enforcement and client display. */
object EntitlementFeatures {
    val FREE = listOf("valuation", "holdings", "transactions", "allocation-basic", "largest-holding", "gains-basic", "dividends-basic", "education")
    val PLUS = FREE + listOf("performance", "twr-xirr", "benchmark", "allocation-detail", "concentration-detail", "contributors", "dividends-detail", "currency-impact", "insights-advanced", "ai-explanations",
        "earnings-ai", "earnings-history", "earnings-digest")
}
