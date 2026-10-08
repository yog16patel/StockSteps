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
    private val cache = CompanyFinancialCache(capacity = 64)
    override suspend fun rates(from: String, through: String): Map<String, String> = cache.getOrLoad("$from:$through", 21_600_000) {
        val body = client.get("https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json") {
            parameter("start_date", from); parameter("end_date", through)
        }.body<JsonObject>()
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
    suspend fun report(uid: String, accountId: String, range: String, includeHistory: Boolean): PortfolioReport = coroutineScope {
        val data = ledger.get(uid)
        val account = data.accounts.find { it.id == accountId } ?: throw UserDataException(404, "ACCOUNT_NOT_FOUND", "Account does not exist.")
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val first = data.transactions.filter { it.accountId == accountId }.minOfOrNull { it.tradeDate }?.let(LocalDate::parse) ?: today
        val from = maxOf(first, when (range) {
            "1D" -> today
            "1W" -> today.minusWeeks(1)
            "1M" -> today.minusMonths(1)
            "3M" -> today.minusMonths(3)
            "1Y" -> today.minusYears(1)
            "ALL" -> first
            else -> throw UserDataException(400, "INVALID_RANGE", "Choose 1D, 1W, 1M, 3M, 1Y or ALL.")
        })
        val state = PortfolioEngine.replay(data, accountId)
        val symbols = data.transactions.filter { it.accountId == accountId }.mapNotNull { it.instrument?.symbol }.distinct()
        val needsFx = data.accounts.filterNot { it.archived }.map { it.reportingCurrency }.distinct().size > 1 || data.transactions.any { it.accountId == accountId && (it.currency != account.reportingCurrency || it.cashCurrency != account.reportingCurrency || it.instrument?.currency?.let { currency -> currency != it.currency.name } == true) }
        val rates = if (needsFx) safe { fx.rates(first.minusDays(7).toString(), today.toString()) }.orEmpty() else emptyMap()
        fun ratesAt(date: String): Map<PortfolioCurrency, String> {
            val publication = rates.keys.filter { it <= date }.maxOrNull() ?: return emptyMap()
            if (LocalDate.parse(publication).plusDays(7) < LocalDate.parse(date)) return emptyMap()
            val usdCad = Decimal.parse(rates.getValue(publication))
            return if (account.reportingCurrency == PortfolioCurrency.CAD) mapOf(PortfolioCurrency.USD to usdCad.toString())
            else mapOf(PortfolioCurrency.CAD to (Decimal.ONE / usdCad).toString())
        }
        val basisFx = data.transactions.filter { it.accountId == accountId }.map { it.tradeDate }.distinct().associateWith(::ratesAt)
        val calendar = org.example.stocksteps.service.UsMarketCalendar()
        val sessionDate = calendar.lastTradingDay(clock.instant().atZone(calendar.zone).toLocalDate())
        val previousDate = calendar.lastTradingDay(sessionDate.minusDays(1))
        val marketQuotes = symbols.map { symbol -> async { symbol to safe { quotes.quote(symbol) } } }.awaitAll().toMap()
        val quoteCurrencies = symbols.map { symbol -> async { symbol to safe { quotes.currency(symbol) } } }.awaitAll().toMap()
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
        val previousPrices = marketQuotes.mapNotNull { (symbol, quote) -> quote?.previousClose?.takeIf { it.isFinite() && it > 0 }?.let { value -> priceInTradeCurrency(symbol, value, previousDate.toString())?.let { symbol to it } } }.toMap()
        val currentObservation = PortfolioPrices(today.toString(), currentPrices, ratesAt(today.toString()), basisFx)
        val summary = PortfolioEngine.value(data, accountId, currentObservation)
        val fresh = marketQuotes.values.filterNotNull().all { quote -> quote.timestamp?.let { java.time.Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate() == sessionDate } == true }
        val daily = if (fresh && today == sessionDate) PortfolioEngine.daily(data, accountId,
            PortfolioPrices(previousDate.toString(), previousPrices, ratesAt(previousDate.toString()), basisFx), currentObservation)
            else PortfolioDailyPerformance(null, null, "Daily change is unavailable until we have prices for the current trading session.")
        val points = if (!includeHistory || from > today) emptyList() else {
            val datasets = symbols.map { symbol -> async {
                symbol to safe { cache.getOrLoad("portfolio-history:$symbol", 21_600_000) { historyPermits.withPermit { dailyCloses(symbol) } } }.orEmpty()
                    .mapNotNull { point -> priceInTradeCurrency(symbol, point.close, point.time.take(10))?.let { point.time.take(10) to it } }.toMap()
            } }.awaitAll().toMap()
            val dates = datasets.values.flatMap { it.keys }.distinct().sorted().filter { it >= from.toString() && it <= today.toString() }
            // Cash-only accounts still have dated valuations, sampled daily.
            val valuationDates = if (symbols.isEmpty()) generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { it.toString() }.toList() else dates
            val stride = maxOf(1, (valuationDates.size + 599) / 600)
            val sampledDates = valuationDates.filterIndexed { index, _ -> index % stride == 0 || index == valuationDates.lastIndex }
            PortfolioEngine.history(data, accountId, sampledDates.map { date ->
                PortfolioPrices(date, datasets.mapNotNull { (symbol, series) -> series[date]?.let { symbol to it } }.toMap(), ratesAt(date), basisFx)
            })
        }
        val allocations = PortfolioEngine.allocations(state, currentObservation, account.reportingCurrency)
        PortfolioReport(accountId, data.revision, summary, points, range,
            notice = "This chart shows account value, including cash. Missing prices or exchange rates appear as gaps. Record stock splits in your transactions.",
            fxAsOf = rates.keys.maxOrNull(), daily = daily, allocations = allocations.holdings, currentFx = ratesAt(today.toString()), currencyAllocations = allocations.currencies,
            quoteAsOf = marketQuotes.values.mapNotNull { it?.timestamp }.minOrNull()?.let { java.time.Instant.ofEpochSecond(it).toString() })
    }
    private suspend fun <T> safe(action: suspend () -> T): T? = try { action() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }
    private fun decimal(value: Double): String = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
