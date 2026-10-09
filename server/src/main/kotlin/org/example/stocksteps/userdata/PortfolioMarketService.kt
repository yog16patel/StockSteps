package org.example.stocksteps.userdata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.service.CompanyFinancialCache
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

interface PortfolioFxSource {
    /** USD -> CAD by actual publication date. Never a current-rate substitution. */
    suspend fun rates(from: String, through: String): Map<String, String>
}

class BankOfCanadaPortfolioFx(private val client: HttpClient) : PortfolioFxSource {
    private val cache = CompanyFinancialCache(capacity = 64, name = "fx")
    override suspend fun rates(from: String, through: String): Map<String, String> = cache.getOrLoad("$from:$through", 21_600_000) {
        val url = "https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json"
        val started = System.nanoTime()
        val body = try {
            client.get(url) { parameter("start_date", from); parameter("end_date", through) }.body<JsonObject>()
                .also { org.example.stocksteps.httpclient.ProviderCalls.record(url, "ok", started) }
        } catch (cause: Exception) {
            org.example.stocksteps.httpclient.ProviderCalls.record(url, if (cause is kotlinx.coroutines.CancellationException) "cancelled" else "error", started); throw cause
        }
        body["observations"]?.jsonArray.orEmpty().mapNotNull { element ->
            val row = element.jsonObject
            val date = row["d"]?.jsonPrimitive?.contentOrNull
            val value = row["FXUSDCAD"]?.jsonObject?.get("v")?.jsonPrimitive?.contentOrNull
            if (date != null && date >= from && date <= through && value != null && runCatching { Decimal.parse(value) > Decimal.ZERO }.getOrDefault(false)) date to value else null
        }.toMap()
    }
}

/** No network in MOCK. Dates are still explicit so history cannot borrow future FX. */
object MockPortfolioFx : PortfolioFxSource {
    override suspend fun rates(from: String, through: String): Map<String, String> = buildMap {
        var date = LocalDate.parse(from)
        val end = LocalDate.parse(through)
        while (date <= end) {
            if (date.dayOfWeek.value <= 5) put(date.toString(), "1.35")
            date = date.plusDays(1)
        }
    }
}

class PortfolioMarketService(
    private val ledger: PortfolioService,
    private val history: PriceHistoryProvider,
    private val quotes: WatchMarketData,
    private val fx: PortfolioFxSource,
    private val clock: Clock,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256),
    private val dailyCloses: suspend (String) -> List<org.example.stocksteps.model.PricePoint> = history::getDailyCloses
) {
    private val historyPermits = Semaphore(4)

    /**
     * Ledger, dated FX and trade-currency prices for one account, built once per request and shared
     * by the report and Portfolio Intelligence (one valuation pipeline, never a second one).
     */
    private inner class Context(
        val data: PortfolioLedger,
        val account: PortfolioAccount,
        val today: LocalDate,
        val first: LocalDate,
        val symbols: List<String>,
        val state: PortfolioPositionState,
        val rates: Map<String, String>,
        val marketQuotes: Map<String, org.example.stocksteps.model.StockQuote?>,
        private val quoteCurrencies: Map<String, String?>
    ) {
        val accountId get() = account.id
        fun ratesAt(date: String): Map<PortfolioCurrency, String> {
            val publication = rates.keys.filter { it <= date }.maxOrNull() ?: return emptyMap()
            if (LocalDate.parse(publication).plusDays(7) < LocalDate.parse(date)) return emptyMap()
            val usdCad = Decimal.parse(rates.getValue(publication))
            return if (account.reportingCurrency == PortfolioCurrency.CAD) mapOf(PortfolioCurrency.USD to usdCad.toString())
            else mapOf(PortfolioCurrency.CAD to (Decimal.ONE / usdCad).toString())
        }
        val basisFx = data.transactions.filter { it.accountId == account.id }.map { it.tradeDate }.distinct().associateWith(::ratesAt)
        fun priceInTradeCurrency(symbol: String, price: Double, date: String): String? {
            val holding = state.holdings.find { it.instrument.symbol == symbol } ?: return null
            val nativeCurrency = quoteCurrencies[symbol] ?: holding.instrument.currency ?: return null
            if (nativeCurrency == holding.currency.name) return decimal(price)
            val native = PortfolioCurrency.entries.firstOrNull { it.name == nativeCurrency } ?: return null
            val rates = ratesAt(date)
            fun toReporting(currency: PortfolioCurrency) = if (currency == account.reportingCurrency) Decimal.ONE else rates[currency]?.let(Decimal::parse)
            val sourceRate = toReporting(native) ?: return null
            val tradeRate = toReporting(holding.currency) ?: return null
            return (Decimal.parse(decimal(price)) * sourceRate / tradeRate).toString()
        }
        val currentPrices = marketQuotes.mapNotNull { (symbol, quote) -> quote?.price?.takeIf { it.isFinite() && it > 0 }?.let { value -> priceInTradeCurrency(symbol, value, today.toString())?.let { symbol to it } } }.toMap()
        val currentObservation = PortfolioPrices(today.toString(), currentPrices, ratesAt(today.toString()), basisFx)

        /** Dated daily closes in each holding's trade currency (shared 6-hour cache with charts). */
        suspend fun closes(): Map<String, Map<String, String>> = coroutineScope {
            symbols.map { symbol -> async {
                symbol to safe { cache.getOrLoad("portfolio-history:$symbol", 21_600_000) { historyPermits.withPermit { dailyCloses(symbol) } } }.orEmpty()
                    .mapNotNull { point -> priceInTradeCurrency(symbol, point.close, point.time.take(10))?.let { point.time.take(10) to it } }.toMap()
            } }.awaitAll().toMap()
        }
    }

    private suspend fun context(uid: String, accountId: String, alwaysFx: Boolean = false): Context = coroutineScope {
        val data = ledger.get(uid)
        val account = data.accounts.find { it.id == accountId } ?: throw UserDataException(404, "ACCOUNT_NOT_FOUND", "Account does not exist.")
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val first = data.transactions.filter { it.accountId == accountId }.minOfOrNull { it.tradeDate }?.let(LocalDate::parse) ?: today
        val symbols = data.transactions.filter { it.accountId == accountId }.mapNotNull { it.instrument?.symbol }.distinct()
        val needsFx = alwaysFx || data.accounts.filterNot { it.archived }.map { it.reportingCurrency }.distinct().size > 1 || data.transactions.any { it.accountId == accountId && (it.currency != account.reportingCurrency || it.cashCurrency != account.reportingCurrency || it.instrument?.currency?.let { currency -> currency != it.currency.name } == true) }
        val rates = if (needsFx) safe { fx.rates(first.minusDays(7).toString(), today.toString()) }.orEmpty() else emptyMap()
        val marketQuotes = symbols.map { symbol -> async { symbol to safe { quotes.quote(symbol) } } }.awaitAll().toMap()
        val quoteCurrencies = symbols.map { symbol -> async { symbol to safe { quotes.currency(symbol) } } }.awaitAll().toMap()
        Context(data, account, today, first, symbols, PortfolioEngine.replay(data, accountId), rates, marketQuotes, quoteCurrencies)
    }

    suspend fun report(uid: String, accountId: String, range: String, includeHistory: Boolean): PortfolioReport = coroutineScope {
        val ctx = context(uid, accountId)
        val data = ctx.data
        val account = ctx.account
        val today = ctx.today
        val first = ctx.first
        val from = maxOf(first, when (range) {
            "1D" -> today
            "1W" -> today.minusWeeks(1)
            "1M" -> today.minusMonths(1)
            "3M" -> today.minusMonths(3)
            "1Y" -> today.minusYears(1)
            "ALL" -> first
            else -> throw UserDataException(400, "INVALID_RANGE", "Choose 1D, 1W, 1M, 3M, 1Y or ALL.")
        })
        val state = ctx.state
        val symbols = ctx.symbols
        val calendar = org.example.stocksteps.service.UsMarketCalendar()
        val sessionDate = calendar.lastTradingDay(clock.instant().atZone(calendar.zone).toLocalDate())
        val previousDate = calendar.lastTradingDay(sessionDate.minusDays(1))
        val marketQuotes = ctx.marketQuotes
        val previousPrices = marketQuotes.mapNotNull { (symbol, quote) -> quote?.previousClose?.takeIf { it.isFinite() && it > 0 }?.let { value -> ctx.priceInTradeCurrency(symbol, value, previousDate.toString())?.let { symbol to it } } }.toMap()
        val currentObservation = ctx.currentObservation
        val summary = PortfolioEngine.value(data, accountId, currentObservation)
        val fresh = marketQuotes.values.filterNotNull().all { quote -> quote.timestamp?.let { java.time.Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate() == sessionDate } == true }
        val daily = if (fresh && today == sessionDate) PortfolioEngine.daily(data, accountId,
            PortfolioPrices(previousDate.toString(), previousPrices, ctx.ratesAt(previousDate.toString()), ctx.basisFx), currentObservation)
            else PortfolioDailyPerformance(null, null, "Daily change is unavailable until we have prices for the current trading session.")
        val points = if (!includeHistory || from > today) emptyList() else {
            val datasets = ctx.closes()
            val dates = datasets.values.flatMap { it.keys }.distinct().sorted().filter { it >= from.toString() && it <= today.toString() }
            // Cash-only accounts still have dated valuations, sampled daily.
            val valuationDates = if (symbols.isEmpty()) generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { it.toString() }.toList() else dates
            val stride = maxOf(1, (valuationDates.size + 599) / 600)
            val sampledDates = valuationDates.filterIndexed { index, _ -> index % stride == 0 || index == valuationDates.lastIndex }
            PortfolioEngine.history(data, accountId, sampledDates.map { date ->
                PortfolioPrices(date, datasets.mapNotNull { (symbol, series) -> series[date]?.let { symbol to it } }.toMap(), ctx.ratesAt(date), ctx.basisFx)
            })
        }
        val allocations = PortfolioEngine.allocations(state, currentObservation, account.reportingCurrency)
        PortfolioReport(accountId, data.revision, summary, points, range,
            notice = "This chart shows account value, including cash. Missing prices or exchange rates appear as gaps. Record stock splits in your transactions.",
            fxAsOf = ctx.rates.keys.maxOrNull(), daily = daily, allocations = allocations.holdings, currentFx = ctx.ratesAt(today.toString()), currencyAllocations = allocations.currencies,
            quoteAsOf = marketQuotes.values.mapNotNull { it?.timestamp }.minOrNull()?.let { java.time.Instant.ofEpochSecond(it).toString() })
    }

    /** Market inputs for [org.example.stocksteps.portfolio.analytics.PortfolioAnalyticsEngine], from the same pipeline as [report]. */
    internal class AnalyticsMarket(
        val ledger: PortfolioLedger,
        val account: PortfolioAccount,
        val today: String,
        val closes: Map<String, Map<String, String>>,
        /** Every dated publication converted for this account (benchmarks need FX even for CAD-only ledgers). */
        val fxByDate: Map<String, Map<PortfolioCurrency, String>>,
        val current: PortfolioPrices,
        val instruments: Map<String, org.example.stocksteps.model.InstrumentRef>
    )

    internal suspend fun analyticsMarket(uid: String, accountId: String): AnalyticsMarket {
        val ctx = context(uid, accountId, alwaysFx = true)
        return AnalyticsMarket(
            ctx.data, ctx.account, ctx.today.toString(), ctx.closes(),
            ctx.rates.keys.associateWith(ctx::ratesAt).filterValues { it.isNotEmpty() },
            ctx.currentObservation,
            ctx.state.holdings.associate { it.instrument.symbol to it.instrument }
        )
    }

    /** Revision only (no market calls): the analytics cache key. */
    internal suspend fun revision(uid: String) = ledger.get(uid).revision

    private suspend fun <T> safe(action: suspend () -> T): T? = try { action() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }
    private fun decimal(value: Double): String = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
