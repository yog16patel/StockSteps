package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.MarketDataProvider
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockQuoteProviderRepository
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Quotes and daily history for actual index instruments (e.g. ^GSPC). */
interface IndexDataSource {
    suspend fun indexQuote(symbol: String): StockQuote?
    suspend fun indexHistory(symbol: String): List<PricePoint>
}

/** A market index and the fund used as a clearly labelled proxy when the index itself isn't available. */
data class IndexDefinition(
    val id: String, val name: String, val symbol: String, val currency: String, val region: String,
    val proxySymbol: String, val proxyName: String
)

/** Where the dashboard's data comes from, for the labels shown to users. */
data class MarketsSourceLabels(val movers: String, val quotes: String, val sampleData: Boolean, val notice: String)

/** Simple provider-usage counters (exposed for diagnostics and tests; never includes symbols or keys). */
class MarketsUsage {
    val overviewBuilds = AtomicLong()
    val providerCalls = AtomicLong()
    val providerFailures = AtomicLong()
}

/**
 * The Markets dashboard: session, indices, gainers, losers, most active, sector performance and
 * market news in one response. Sections load in parallel and fail independently; each dataset has
 * its own cache with shorter lifetimes while the market is open, and identical concurrent requests
 * share one load (CompanyFinancialCache coalesces misses).
 */
class MarketsService(
    private val movers: MarketDataProvider,
    private val quotes: StockQuoteProviderRepository,
    private val indexData: IndexDataSource,
    private val news: NewsService,
    private val labels: MarketsSourceLabels,
    private val calendar: UsMarketCalendar = UsMarketCalendar(),
    private val clock: Clock = Clock.systemUTC(),
    /** Uses a monotonic clock: [clock] only decides the session (MOCK pins it to the fixture time). */
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 128, name = "markets"),
    private val callTimeoutMillis: Long = 8_000,
    val usage: MarketsUsage = MarketsUsage(),
    /**
     * Phase 7B (EOD-first): the shared daily-close series (`PriceChartService.getDailyCloses`, one cached request per symbol until the
     * next expected close). When set, sectors and index fall-backs use the latest completed session's close and its change from the
     * previous trading day's close instead of quotes, and most-active rows aren't enriched with quote volumes. Null keeps the quote path.
     */
    private val dailyCloses: (suspend (String) -> List<PricePoint>)? = null,
    private val eod: EodSessions = EodSessions(clock)
) {
    private val permits = Semaphore(MAX_PARALLEL_QUOTES)

    suspend fun overview(): MarketsOverview {
        val session = calendar.session(clock.instant())
        val live = session.status in setOf(MarketSessionStatus.OPEN, MarketSessionStatus.PRE_MARKET, MarketSessionStatus.AFTER_HOURS)
        return cache.getOrLoad("overview", if (live) OVERVIEW_LIVE_TTL else OVERVIEW_CLOSED_TTL) { build(session, live) }
    }

    private suspend fun build(session: MarketSession, live: Boolean): MarketsOverview = coroutineScope {
        usage.overviewBuilds.incrementAndGet()
        val quoteTtl = if (live) LIVE_TTL else CLOSED_TTL
        val indices = async { section("indices") { cached("indices", quoteTtl) { loadIndices() } } }
        val gainers = async { section("gainers") { cached("gainers", quoteTtl) { MoverRules.gainers(call { movers.getGainers() }) } } }
        val losers = async { section("losers") { cached("losers", quoteTtl) { MoverRules.losers(call { movers.getLosers() }) } } }
        val active = async { section("mostActive") { cached("mostActive", quoteTtl) { mostActive() } } }
        val sectors = async { section("sectors") {
            if (dailyCloses != null) cached("sectors-eod", quoteTtl * 2) { eodSectors() }   // the closes themselves live until the next close
            else cached("sectors", quoteTtl * 2) { Sectors(loadSectors(), null, emptyList()) }
        } }
        val articles = async { section("news") { cached("news", NEWS_TTL) { call { news.getNews(0, NEWS_COUNT) }.filter { NewsService.isHttps(it.url) } } } }
        val asOf = clock.instant().toString()
        val errors = listOf(indices, gainers, losers, active, sectors, articles).mapNotNull { it.await().second }
        MarketsOverview(
            session = session,
            indices = indices.await().first.orEmpty(),
            gainers = MarketMoverList(gainers.await().first.orEmpty(), labels.movers, labels.quotes, "Largest % gain since the previous close", asOf),
            losers = MarketMoverList(losers.await().first.orEmpty(), labels.movers, labels.quotes, "Largest % decline since the previous close", asOf),
            mostActive = MarketMoverList(active.await().first.orEmpty(), labels.movers, labels.quotes, "Absolute share volume in the current or latest session", asOf),
            sectors = sectors.await().first.let { s -> SectorPerformanceSection(
                items = s?.items.orEmpty(),
                methodology = "Measured with the Select Sector SPDR ETFs (one fund per sector). These are ETF proxies, not official sector index returns." +
                    if (dailyCloses != null) " Each fund's latest closing price is compared with its close on the previous trading day." else "",
                period = if (dailyCloses != null) "Change from the previous close" else "Change since the previous close",
                closeDate = s?.closeDate,
                unavailable = s?.unavailable.orEmpty()
            ) },
            news = articles.await().first.orEmpty(),
            dataNotice = labels.notice,
            sampleData = labels.sampleData,
            generatedAt = asOf,
            errors = errors
        )
    }

    private suspend fun loadIndices(): List<IndexQuote> = coroutineScope {
        INDICES.map { definition -> async { index(definition) } }.awaitAll()
    }

    /** The index itself when available; otherwise its fund proxy, labelled; otherwise unavailable. */
    private suspend fun index(definition: IndexDefinition): IndexQuote {
        // Phase 7B: outside the index's active hours its latest close is the value; no quote is requested.
        if (dailyCloses != null && !eod.active(definition.symbol, clock.instant())) return eodIndex(definition)
        val direct = attempt { call { indexData.indexQuote(definition.symbol) } }?.takeIf { it.price.valid() }
        if (direct != null) {
            val trend = attempt { call { indexData.indexHistory(definition.symbol) } }.orEmpty()
            return indexQuote(definition, direct, isProxy = false, trend)
        }
        if (dailyCloses != null) return eodIndex(definition)
        val proxy = attempt { quote(definition.proxySymbol) }?.takeIf { it.price.valid() }
            ?: return IndexQuote(definition.id, definition.name, definition.symbol, unit = "points", currency = definition.currency,
                region = definition.region, error = ApiError("INDEX_UNAVAILABLE", "This index isn't available right now."))
        return indexQuote(definition, proxy, isProxy = true, emptyList())
    }

    /**
     * Phase 7B: no index quote — the index's own latest close (points), else its fund's latest close (labelled proxy, fund currency, no
     * index level), else unavailable. Values are a completed session's close with the change from the preceding trading day's close.
     */
    private suspend fun eodIndex(definition: IndexDefinition): IndexQuote {
        val own = attempt { closes(definition.symbol) }
        own?.let { series -> EodChange.of(definition.symbol, series, eod, clock.instant())?.let { return eodQuote(definition, it, isProxy = false, series) } }
        val fund = attempt { closes(definition.proxySymbol) }
        fund?.let { series -> EodChange.of(definition.proxySymbol, series, eod, clock.instant())?.let { return eodQuote(definition, it, isProxy = true, series) } }
        return IndexQuote(definition.id, definition.name, definition.symbol, unit = "points", currency = definition.currency, region = definition.region,
            error = ApiError("INDEX_UNAVAILABLE", "This index isn't available right now."))
    }

    private fun eodQuote(definition: IndexDefinition, c: EodChange, isProxy: Boolean, series: List<PricePoint>): IndexQuote {
        // The index's own closes only: a fund's trend isn't drawn as the index (as before for proxies).
        val trend = if (isProxy) emptyList() else series.filter { p -> EodSessions.dateOf(p)?.let { it <= c.closeDate } == true && p.close.valid() }
            .takeLast(TREND_POINTS).map { it.close }.takeIf { it.size >= 2 }.orEmpty()
        return IndexQuote(
        id = definition.id, name = definition.name, symbol = if (isProxy) definition.proxySymbol else definition.symbol,
        value = c.close, change = c.change, changePercent = c.changePercent,
        unit = if (isProxy) definition.currency else "points", currency = definition.currency, region = definition.region, isProxy = isProxy,
        proxyDescription = if (isProxy) "${definition.proxyName} (${definition.proxySymbol}), a fund that tracks the ${definition.name}. Fund price, not the index level." else null,
        asOf = c.closedAt.toString(),
        trend = trend, trendLabel = trend.takeIf { it.isNotEmpty() }?.let { "Daily closes, last ${it.size} trading days" },
        closeDate = c.closeDate.toString(), previousCloseDate = c.previousCloseDate.toString()
        )
    }

    private suspend fun closes(symbol: String): List<PricePoint> = permits.withPermit { call { dailyCloses!!(symbol) } }

    private fun indexQuote(definition: IndexDefinition, quote: StockQuote, isProxy: Boolean, history: List<PricePoint>): IndexQuote {
        val price = quote.price!!
        val previous = quote.previousClose?.takeIf { it.valid() }
        val change = previous?.let { price - it } ?: quote.change?.takeIf { it.isFinite() }
        val percent = previous?.let { (price / it - 1) * 100 } ?: quote.changePercent?.takeIf { it.isFinite() }
        // The trend must end on the same session as the quote; otherwise it's dropped rather than mixed.
        val quoteDate = quote.timestamp?.let { Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate().toString() }
        val trend = history.takeLast(TREND_POINTS).takeIf { it.size >= 2 && (quoteDate == null || it.last().time.take(10) == quoteDate) }
        return IndexQuote(
            id = definition.id,
            name = definition.name,
            symbol = if (isProxy) definition.proxySymbol else definition.symbol,
            value = price,
            change = change,
            changePercent = percent,
            unit = if (isProxy) definition.currency else "points",
            currency = definition.currency,
            region = definition.region,
            isProxy = isProxy,
            proxyDescription = if (isProxy) "${definition.proxyName} (${definition.proxySymbol}), a fund that tracks the ${definition.name}. Fund price, not the index level." else null,
            asOf = quote.timestamp?.let { Instant.ofEpochSecond(it).toString() },
            trend = trend?.map { it.close }.orEmpty(),
            trendLabel = trend?.let { "Daily closes, last ${it.size} trading days" }
        )
    }

    /** The provider's most-active list, enriched with each stock's own quote volume, ranked by volume. */
    private suspend fun mostActive(): List<MarketMover> = coroutineScope {
        val ranked = MoverRules.clean(call { movers.getMostActive() }).take(MAX_MOVERS)
        // Phase 7B: no quote per row for a missing volume (most were refused on the current plan); rows keep the list's own fields.
        if (dailyCloses != null) return@coroutineScope MoverRules.mostActive(ranked)
        val enriched = ranked.map { mover ->
            async { if (mover.volume != null) mover else mover.copy(volume = attempt { quote(mover.symbol) }?.volume?.toDouble()) }
        }.awaitAll()
        MoverRules.mostActive(enriched)
    }

    private class Sectors(val items: List<SectorPerformance>, val closeDate: String?, val unavailable: List<String>)

    /**
     * Phase 7B: each fund's latest completed-session close vs the previous trading day's close. Only funds on the newest close date are
     * compared (others are listed as unavailable); a fund without a usable pair is unavailable, never estimated. Every fund refused by
     * the plan → PROVIDER_RESTRICTED for the section.
     */
    private suspend fun eodSectors(): Sectors = coroutineScope {
        val failures = java.util.concurrent.ConcurrentLinkedQueue<StockProviderException>()
        val rows = SECTORS.map { (sector, symbol, fund) ->
            async {
                val change = try { EodChange.of(symbol, closes(symbol), eod, clock.instant()) } catch (cause: Exception) {
                    if (cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
                    (cause as? StockProviderException)?.let(failures::add)
                    null
                }
                Triple(sector, SectorPerformance(sector, symbol, fund, change?.changePercent, change?.closedAt?.toString(),
                    closeDate = change?.closeDate?.toString(), previousCloseDate = change?.previousCloseDate?.toString()), change?.closeDate)
            }
        }.awaitAll()
        val latest = rows.mapNotNull { it.third }.maxOrNull()
        val usable = rows.filter { it.third != null && it.third == latest }.map { it.second }
        if (usable.isEmpty()) throw sectionFailure(failures.toList())
        Sectors(usable.sortedByDescending { it.changePercent }, latest?.toString(), rows.filter { it.second !in usable }.map { it.first })
    }

    /**
     * Why no fund could be shown: when every fund failed with a provider error, the section keeps that type (daily allowance, plan
     * restriction when all were refused, else the first failure — e.g. a rate limit); otherwise it's a plain unavailability (no data).
     */
    private fun sectionFailure(failures: List<StockProviderException>): StockProviderException = when {
        failures.size < SECTORS.size -> StockProviderException(StockProviderException.Failure.UNAVAILABLE)
        failures.any { it.dailyLimitReached } -> failures.first { it.dailyLimitReached }
        failures.all { it.restricted } -> failures.first()
        else -> failures.firstOrNull { !it.restricted } ?: failures.first()
    }

    private suspend fun loadSectors(): List<SectorPerformance> = coroutineScope {
        val refusals = java.util.concurrent.ConcurrentLinkedQueue<StockProviderException>()
        val rows = SECTORS.map { (sector, symbol, fund) ->
            async {
                val quote = try { quote(symbol) } catch (cause: Exception) {
                    if (cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
                    (cause as? StockProviderException)?.takeIf { it.restricted }?.let(refusals::add)
                    null
                }
                val price = quote?.price?.takeIf { it.valid() }
                val previous = quote?.previousClose?.takeIf { it.valid() }
                val percent = if (price != null && previous != null) (price / previous - 1) * 100 else quote?.changePercent?.takeIf { it.isFinite() }
                val session = quote?.timestamp?.let { Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate() }
                Triple(SectorPerformance(sector, symbol, fund, percent, quote?.timestamp?.let { Instant.ofEpochSecond(it).toString() }), session, percent != null)
            }
        }.awaitAll()
        // Only funds quoted on the same (latest) session are compared; others are left out.
        val latest = rows.mapNotNull { it.second }.maxOrNull()
        val usable = rows.filter { it.third && (latest == null || it.second == null || it.second == latest) }.map { it.first }
        // Phase 7A: when the plan refused every fund, the section says so (PROVIDER_RESTRICTED, 1 h section cooldown) instead of "temporarily".
        if (usable.isEmpty()) throw refusals.takeIf { it.size == SECTORS.size }?.peek() ?: StockProviderException(StockProviderException.Failure.UNAVAILABLE)
        usable.sortedByDescending { it.changePercent }
    }

    private suspend fun quote(symbol: String): StockQuote? = permits.withPermit { call { quotes.getQuote(symbol) } }

    private suspend fun <T> call(block: suspend () -> T): T {
        usage.providerCalls.incrementAndGet()
        return try { withTimeout(callTimeoutMillis) { block() } } catch (cause: Exception) {
            usage.providerFailures.incrementAndGet()
            if (cause is kotlinx.coroutines.TimeoutCancellationException) throw StockProviderException(StockProviderException.Failure.TIMEOUT)
            throw cause
        }
    }

    private class Outcome<T>(val value: T?, val failure: Exception?)

    /** Cached per dataset; failures are cached briefly (longer for rate limits and denied access). */
    private suspend fun <T> cached(key: String, ttl: Long, load: suspend () -> T): T {
        val outcome = cache.getOrLoad("section:$key", ttl, resultTtl = { if (it.failure == null) ttl else providerCooldown(it.failure as? StockProviderException, FinancialCachePolicy.FAILURE) }) {
            try { Outcome(load(), null) } catch (cause: Exception) {
                if (cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
                Outcome<T>(null, cause)
            }
        }
        outcome.failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return outcome.value as T
    }

    private suspend fun <T> section(name: String, block: suspend () -> T): Pair<T?, SnapshotSectionError?> = try {
        block() to null
    } catch (cause: Exception) {
        if (cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
        null to SnapshotSectionError(name, snapshotError(cause))
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
        null
    }

    companion object {
        const val MAX_MOVERS = 10
        private const val TREND_POINTS = 22
        private const val NEWS_COUNT = 5
        private const val MAX_PARALLEL_QUOTES = 4
        private const val OVERVIEW_LIVE_TTL = 30_000L
        private const val OVERVIEW_CLOSED_TTL = 300_000L
        private const val LIVE_TTL = 60_000L
        private const val CLOSED_TTL = 900_000L
        private const val NEWS_TTL = 300_000L

        val INDICES = listOf(
            IndexDefinition("SP500", "S&P 500", "^GSPC", "USD", "US", "SPY", "SPDR S&P 500 ETF Trust"),
            IndexDefinition("NASDAQ_COMPOSITE", "Nasdaq Composite", "^IXIC", "USD", "US", "ONEQ", "Fidelity Nasdaq Composite Index ETF"),
            IndexDefinition("DOW", "Dow Jones Industrial Average", "^DJI", "USD", "US", "DIA", "SPDR Dow Jones Industrial Average ETF Trust"),
            IndexDefinition("TSX", "S&P/TSX Composite", "^GSPTSE", "CAD", "Canada", "XIC.TO", "iShares Core S&P/TSX Capped Composite Index ETF")
        )

        /** Select Sector SPDR funds, the standard ETF proxies for the 11 GICS sectors. */
        val SECTORS = listOf(
            Triple("Technology", "XLK", "Technology Select Sector SPDR Fund"),
            Triple("Healthcare", "XLV", "Health Care Select Sector SPDR Fund"),
            Triple("Financials", "XLF", "Financial Select Sector SPDR Fund"),
            Triple("Consumer Discretionary", "XLY", "Consumer Discretionary Select Sector SPDR Fund"),
            Triple("Communication Services", "XLC", "Communication Services Select Sector SPDR Fund"),
            Triple("Industrials", "XLI", "Industrial Select Sector SPDR Fund"),
            Triple("Consumer Staples", "XLP", "Consumer Staples Select Sector SPDR Fund"),
            Triple("Energy", "XLE", "Energy Select Sector SPDR Fund"),
            Triple("Utilities", "XLU", "Utilities Select Sector SPDR Fund"),
            Triple("Real Estate", "XLRE", "Real Estate Select Sector SPDR Fund"),
            Triple("Materials", "XLB", "Materials Select Sector SPDR Fund")
        )

        private fun Double?.valid() = this != null && isFinite() && this > 0
    }
}

/**
 * Cleaning rules applied to every mover list: valid US-listed symbols only, a positive price, no
 * duplicates, and no changes beyond ±[MAX_ABS_CHANGE]% (treated as data errors). Small-cap stocks
 * are kept: no market-cap or liquidity filter is applied.
 */
object MoverRules {
    const val MAX_ABS_CHANGE = 1_000.0
    private val SYMBOL = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")
    private val US_EXCHANGES = setOf("NASDAQ", "NYSE", "AMEX", "NYSEARCA", "NYSEAMERICAN", "BATS", "CBOE")

    fun clean(items: List<MarketMover>): List<MarketMover> = items.asSequence()
        .map { it.copy(symbol = it.symbol.trim().uppercase(Locale.ROOT), name = it.name?.trim()?.takeIf(String::isNotEmpty)) }
        .filter { SYMBOL.matches(it.symbol) }
        .filter { mover -> mover.price?.let { it.isFinite() && it > 0 } == true }
        .filter { mover -> mover.exchange?.let { it.uppercase(Locale.ROOT) in US_EXCHANGES } ?: true }
        .filter { mover -> mover.changePercent?.let { it.isFinite() && kotlin.math.abs(it) <= MAX_ABS_CHANGE } ?: true }
        .distinctBy { it.symbol }
        .toList()

    fun gainers(items: List<MarketMover>) = clean(items).filter { (it.changePercent ?: 0.0) > 0 }
        .sortedByDescending { it.changePercent }.take(MarketsService.MAX_MOVERS)

    fun losers(items: List<MarketMover>) = clean(items).filter { (it.changePercent ?: 0.0) < 0 }
        .sortedBy { it.changePercent }.take(MarketsService.MAX_MOVERS)

    /** Ranked by volume; rows without a known volume keep the provider's order after them. */
    fun mostActive(items: List<MarketMover>) = clean(items)
        .sortedWith(compareByDescending<MarketMover> { it.volume?.takeIf { v -> v.isFinite() && v >= 0 } != null }.thenByDescending { it.volume ?: 0.0 })
        .take(MarketsService.MAX_MOVERS)
}

/** MOCK index fixture: `market/indices.json`. */
@Serializable
data class IndexFixture(val quote: StockQuote, val history: List<PricePoint> = emptyList())
