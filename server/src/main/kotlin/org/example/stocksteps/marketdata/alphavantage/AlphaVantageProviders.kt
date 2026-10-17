package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.marketdata.AdjustmentBasis
import org.example.stocksteps.marketdata.DailyClose
import org.example.stocksteps.marketdata.DailyPriceSource
import org.example.stocksteps.marketdata.Dataset
import org.example.stocksteps.marketdata.PriceSeries
import org.example.stocksteps.marketdata.Provenance
import org.example.stocksteps.marketdata.Sourced
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialPeriodStatement
import org.example.stocksteps.model.MarketIndex
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.MarketDataProvider
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repository.StatementHistory
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.EodSessions
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 9B.7: Alpha Vantage behind the existing provider interfaces (response contracts unchanged).
 *
 * Daily prices are **as traded** (TIME_SERIES_DAILY: "raw (as-traded)", documented): the basis Portfolio/Practice quantities use. The
 * latest close is the same in any basis; history before a split is in pre-split dollars (charts show the cliff; valuation history skips
 * windows that span a split — `ValuationCalculations.monthlyPe(closesSplitAdjusted = false)`). Intraday data is premium/realtime and isn't
 * requested: the 1D chart and sparklines are empty (shown as unavailable).
 */
class AlphaVantagePriceHistoryProvider(private val av: AlphaVantageClient, private val clock: Clock = Clock.systemUTC()) : PriceHistoryProvider, DailyPriceSource {
    override suspend fun getIntradaySparkline(symbol: String): Sparkline = Sparkline(symbol, emptyList())
    override suspend fun getIntradayPoints(symbol: String): List<PricePoint> = emptyList()
    override suspend fun getDailyCloses(symbol: String): List<PricePoint> =
        av.dailyRaw(symbol)?.bars.orEmpty().map { PricePoint(it.date.toString(), it.close.toDouble()) }

    override suspend fun daily(symbol: String): Sourced<PriceSeries>? {
        val s = av.dailyRaw(symbol) ?: return null
        if (s.bars.isEmpty()) return null
        val provenance = Provenance("alphavantage", Dataset.DAILY_PRICES, clock.instant(), s.bars.last().date.toString())
        val zone = s.timeZone?.let { runCatching { ZoneId.of(if (it == "US/Eastern") "America/New_York" else it) }.getOrNull() }
        // Currency isn't in the daily series: US listings are USD; others unknown (never guessed).
        val currency = if (symbol.contains('.')) null else "USD"
        return Sourced(PriceSeries(symbol, null, zone, currency, AdjustmentBasis.UNADJUSTED, s.bars.map { DailyClose(it.date, it.close) }, provenance), provenance)
    }
}

/**
 * Stock data from Alpha Vantage, dataset by dataset. [delegate] (the development FMP adapter) serves only the datasets this adapter
 * doesn't cover **by configuration**: the fundamentals bundle (`getFundamentals`/`screenerFundamentals`) always, and profiles when no
 * premium plan is configured (OVERVIEW is premium). An Alpha Vantage refusal is never answered from [delegate] — that would hide a plan or
 * rate limit; callers get the typed failure.
 */
class AlphaVantageStockProvider(
    private val av: AlphaVantageClient,
    private val delegate: StockProviderRepository,
    private val sessions: EodSessions,
    private val clock: Clock = Clock.systemUTC()
) : StockProviderRepository {

    override suspend fun getQuote(symbol: String): StockQuote? {
        val q = av.quote(symbol) ?: return null
        val price = q.price?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: return null
        val market = sessions.market(symbol)
        // End-of-day data: the quote is the latest trading day's close, timestamped at that session's close.
        val timestamp = q.latestTradingDay?.let { d -> market?.let { sessions.closeInstant(it, d).epochSecond } }
        return StockQuote(symbol, null, price, q.change?.toDouble(), q.changePercent?.toDouble(), q.high?.toDouble(), q.low?.toDouble(),
            previousClose = q.previousClose?.toDouble(), volume = q.volume, timestamp = timestamp, open = q.open?.toDouble())
    }

    override suspend fun getProfile(symbol: String): CompanyProfile? {
        if (!av.premium) return delegate.getProfile(symbol)
        val o = av.overview(symbol) ?: return null
        // Alpha Vantage sectors use the SEC's office names (e.g. "LIFE SCIENCES"), not the 11 sector names Discover filters on: the sector is
        // left null rather than mixed; the industry is the provider's own text.
        return CompanyProfile(symbol, o.name, o.description, sector = null, industry = o.industry, website = o.website, country = o.country,
            currency = o.currency, exchange = o.exchange, isEtf = o.assetType?.let { it.equals("ETF", ignoreCase = true) },
            marketCap = o.marketCap?.toDouble())
    }

    /** US listings only (D1 scope: USD listings on US exchanges); equities and ETFs. */
    override suspend fun searchStocks(query: String): List<StockSearchResult> = av.search(query)
        .filter { it.region.equals("United States", ignoreCase = true) && (it.type == null || it.type.equals("Equity", true) || it.type.equals("ETF", true)) }
        .map { StockSearchResult(it.symbol, it.name, it.currency) }

    override suspend fun getGainers(): List<MarketMover> = av.movers()?.gainers.orEmpty().map(::mover)
    override suspend fun getLosers(): List<MarketMover> = av.movers()?.losers.orEmpty().map(::mover)

    override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals = delegate.getFundamentals(symbol, period)
    override suspend fun screenerFundamentals(symbol: String, listingCurrency: String?): CompanyFundamentals = delegate.screenerFundamentals(symbol, listingCurrency)

    /** Reported statements from Alpha Vantage, merged by period end (the same contract as the FMP path). */
    override suspend fun statementHistory(symbol: String, period: String, statements: Set<Statement>): StatementHistory {
        val wanted = statements + Statement.INCOME
        val availability = HashMap<Statement, FinancialAvailability>()
        val loaded = HashMap<Statement, AlphaVantageParsers.Statement>()
        for (s in Statement.entries) {
            if (s !in wanted) continue
            val function = when (s) { Statement.INCOME -> "INCOME_STATEMENT"; Statement.BALANCE -> "BALANCE_SHEET"; Statement.CASH_FLOW -> "CASH_FLOW" }
            try {
                val st = av.statement(function, symbol)
                if (st == null || (st.annual.isEmpty() && st.quarterly.isEmpty())) availability[s] = FinancialAvailability.MISSING
                else { loaded[s] = st; availability[s] = FinancialAvailability.AVAILABLE }
            } catch (cause: StockProviderException) {
                if (s == Statement.INCOME) throw cause   // income defines the periods
                availability[s] = FinancialAvailability.TEMPORARILY_UNAVAILABLE
            }
        }
        val quarterly = period == "quarter"
        val rows = AlphaVantageStatements.merge(loaded, quarterly)
        return StatementHistory(symbol, period, rows, availability, clock.instant().toString(), source = "alphavantage")
    }

    private fun mover(m: AlphaVantageParsers.Mover) = MarketMover(m.ticker, null, m.price?.toDouble(), m.changeAmount?.toDouble(), m.changePercent?.toDouble(),
        volume = m.volume?.toDouble())
}

/** Movers from Alpha Vantage; legacy index snapshot and market status stay with [delegate] (unchanged legacy routes). */
class AlphaVantageMarketDataProvider(private val stocks: AlphaVantageStockProvider, private val av: AlphaVantageClient, private val delegate: MarketDataProvider) : MarketDataProvider {
    override suspend fun getMarketIndices(): List<MarketIndex> = delegate.getMarketIndices()
    override suspend fun getGainers(): List<MarketMover> = stocks.getGainers()
    override suspend fun getLosers(): List<MarketMover> = stocks.getLosers()
    override suspend fun getMostActive(): List<MarketMover> = av.movers()?.mostActive.orEmpty().map {
        MarketMover(it.ticker, null, it.price?.toDouble(), it.changeAmount?.toDouble(), it.changePercent?.toDouble(), volume = it.volume?.toDouble())
    }
    override suspend fun getMarketStatus(): MarketStatus = delegate.getMarketStatus()
}

/**
 * Alpha Vantage statement fields → the existing [FinancialPeriodStatement] (nulls stay null). Field mapping:
 * revenue `totalRevenue`, gross profit `grossProfit`, operating income `operatingIncome`, net income `netIncome`, operating cash flow
 * `operatingCashflow`, capex `capitalExpenditures` (stored as positive spending), dividends `dividendPayout`, cash
 * `cashAndCashEquivalentsAtCarryingValue`, debt `shortLongTermDebtTotal`, assets `totalAssets`, liabilities `totalLiabilities`, equity
 * `totalShareholderEquity`, current assets/liabilities `totalCurrentAssets`/`totalCurrentLiabilities`. Diluted EPS isn't in these
 * statements and stays null. Fiscal labels are inferred: FY = the annual report's year-end year; quarters by their distance from the fiscal
 * year-end month of the annual reports (Q4 ends in that month); a quarter whose position can't be inferred is left out.
 */
object AlphaVantageStatements {
    private fun v(m: Map<String, BigDecimal>, k: String) = m[k]?.toDouble()

    /**
     * [verifiedQuarters] (Phase 9B.9A, D-9B9A-6; on for every StockSteps path): quarterly income and cash-flow values appear only for quarters
     * trusted by `AlphaVantageFundamentalsMapper.quarterTrust` (recent fiscal years verified standalone); other quarters keep their period and
     * balance-sheet values with income/cash-flow fields null — never year-to-date figures shown as quarters. Annual rows are unaffected.
     */
    fun merge(loaded: Map<Statement, AlphaVantageParsers.Statement>, quarterly: Boolean, verifiedQuarters: Boolean = true): List<FinancialPeriodStatement> {
        val income = loaded[Statement.INCOME] ?: return emptyList()
        val incomeTrusted = if (quarterly && verifiedQuarters) AlphaVantageFundamentalsMapper.quarterTrust(income, "totalRevenue").trusted else null
        val cashTrusted = if (quarterly && verifiedQuarters) AlphaVantageFundamentalsMapper.quarterTrust(loaded[Statement.CASH_FLOW], "operatingCashflow").trusted else null
        val fyEndMonth = income.annual.map { periodMonth(it.fiscalDateEnding) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        fun reports(s: Statement) = loaded[s]?.let { if (quarterly) it.quarterly else it.annual }.orEmpty().associateBy { it.fiscalDateEnding }
        val inc = reports(Statement.INCOME); val bal = reports(Statement.BALANCE); val cf = reports(Statement.CASH_FLOW)
        return inc.keys.sortedDescending().mapNotNull { end ->
            val label = if (!quarterly) "FY" else quarterLabel(end, fyEndMonth) ?: return@mapNotNull null
            val fiscalYear = if (!quarterly || fyEndMonth == null) (if (end.dayOfMonth <= 7 && end.monthValue == 1) end.year - 1 else end.year) else fiscalYearOf(end, fyEndMonth)
            val i = inc.getValue(end).values.takeIf { incomeTrusted == null || end in incomeTrusted }.orEmpty()
            val b = bal[end]?.values.orEmpty()
            val c = cf[end]?.values?.takeIf { cashTrusted == null || end in cashTrusted }.orEmpty()
            val ocf = v(c, "operatingCashflow")
            val capex = v(c, "capitalExpenditures")?.let { if (it < 0) -it else it }
            FinancialPeriodStatement(label, fiscalYear, end.toString(), inc.getValue(end).currency,
                revenue = v(i, "totalRevenue"), grossProfit = v(i, "grossProfit"), operatingIncome = v(i, "operatingIncome"), netIncome = v(i, "netIncome"),
                epsDiluted = null, operatingCashFlow = ocf, capitalExpenditure = capex,
                freeCashFlow = if (ocf != null && capex != null) ocf - capex else null,
                dividendsPaid = v(c, "dividendPayout")?.let { if (it < 0) -it else it },
                cash = v(b, "cashAndCashEquivalentsAtCarryingValue"), totalDebt = v(b, "shortLongTermDebtTotal"), totalAssets = v(b, "totalAssets"),
                totalLiabilities = v(b, "totalLiabilities"), equity = v(b, "totalShareholderEquity"), currentAssets = v(b, "totalCurrentAssets"),
                currentLiabilities = v(b, "totalCurrentLiabilities"))
        }
    }

    /** The month a period end belongs to: 52/53-week periods can end a few days into the next month (use the nearest month end). */
    fun periodMonth(end: LocalDate): Int = if (end.dayOfMonth <= 7) end.minusDays(end.dayOfMonth.toLong()).monthValue else end.monthValue

    /** Q1…Q4 by months after the fiscal year-end month (3 → Q1, 6 → Q2, 9 → Q3, 0 → Q4); null when unknown or not a quarter boundary. */
    fun quarterLabel(end: LocalDate, fyEndMonth: Int?): String? {
        fyEndMonth ?: return null
        val month = periodMonth(end)
        return when ((month - fyEndMonth + 12) % 12) { 3 -> "Q1"; 6 -> "Q2"; 9 -> "Q3"; 0 -> "Q4"; else -> null }
    }

    /** The year of the fiscal year end on or after [end] (fiscal years named by the calendar year they end in). */
    fun fiscalYearOf(end: LocalDate, fyEndMonth: Int): Int {
        val month = periodMonth(end)
        val year = if (end.dayOfMonth <= 7 && end.monthValue == 1) end.year - 1 else end.year
        return if (month <= fyEndMonth) year else year + 1
    }
}
