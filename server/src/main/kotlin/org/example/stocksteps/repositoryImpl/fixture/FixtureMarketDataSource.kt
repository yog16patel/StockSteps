package org.example.stocksteps.repositoryImpl.fixture

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.MarketDataProvider
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.WhyMovingSource
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * MOCK-mode data: responses previously captured from the REAL backend's public API
 * (see `scripts/capture-fixtures.sh`), served with no provider calls. Missing fixtures
 * behave like the provider having no data (404/empty), unless [sampleFallback] is on: then
 * quotes, profiles, price series and fundamentals come from [SampleMarketData] instead, and
 * stored series that no longer match the quote are replaced too. Why-moving and news stay
 * fixture-only so their unavailable states remain testable.
 *
 * Layout under [root]: `manifest.json`, `market/snapshot.json`, `stocks/search.json`,
 * `stocks/{SYMBOL}/{quote,profile,sparkline,chart-intraday,chart-daily,why-moving,fundamentals-annual,fundamentals-quarter}.json`,
 * `news/market.json`, `news/company/{SYMBOL}.json`.
 */
class FixtureMarketDataSource(
    private val root: String = "fixtures",
    private val now: () -> Instant = Instant::now,
    private val sampleFallback: Boolean = false,
    private val read: (String) -> String? = { path ->
        FixtureMarketDataSource::class.java.classLoader.getResource(path)?.readText()
    }
) : StockProviderRepository, MarketDataProvider, NewsProviderRepository, PriceHistoryProvider, WhyMovingSource,
    org.example.stocksteps.service.QuarterlyEarningsSource, org.example.stocksteps.service.IndexDataSource {

    @Serializable
    private data class Manifest(
        val capturedAt: String,
        /** Tickers whose gaps stay unfilled, to exercise missing-data states (e.g. LONGN). */
        val keepMissing: List<String> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Market-news times are shifted by the fixture's age so "2h ago" stays meaningful. */
    private val age: Duration by lazy {
        val captured = manifest?.capturedAt
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        captured?.let { Duration.between(it, now()).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    }

    /** When the fixtures were captured; MOCK evaluates the market session at this instant. */
    val capturedAt: Instant? get() = manifest?.capturedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** `market/indices.json`: index levels (^GSPC, ^IXIC, ^DJI, ^GSPTSE) with their daily history. */
    private val indices: Map<String, org.example.stocksteps.service.IndexFixture> by lazy {
        load("market/indices.json", ListSerializer(org.example.stocksteps.service.IndexFixture.serializer())).orEmpty()
            .associateBy { it.quote.symbol.uppercase(Locale.ROOT) }
    }
    override suspend fun indexQuote(symbol: String): StockQuote? = indices[symbol.uppercase(Locale.ROOT)]?.quote
    override suspend fun indexHistory(symbol: String): List<PricePoint> = indices[symbol.uppercase(Locale.ROOT)]?.history.orEmpty()

    private val manifest: Manifest? by lazy { load("manifest.json", Manifest.serializer()) }
    private val snapshot: MarketSnapshot? by lazy { load("market/snapshot.json", MarketSnapshot.serializer()) }
    private val sample = SampleMarketData(now)

    /** Names known from the snapshot or search fixtures, so sample values keep a real company name. */
    private val knownNames: Map<String, String> by lazy {
        val movers = snapshot?.let { it.gainers + it.losers + it.mostActive }.orEmpty().mapNotNull { m -> m.name?.let { m.symbol to it } }
        val search = load("stocks/search.json", ListSerializer(StockSearchResult.serializer())).orEmpty().map { it.symbol to it.name }
        (search + movers).toMap()
    }

    override suspend fun searchStocks(query: String): List<StockSearchResult> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return load("stocks/search.json", ListSerializer(StockSearchResult.serializer())).orEmpty().filter {
            it.symbol.lowercase(Locale.ROOT).startsWith(needle) || it.name.lowercase(Locale.ROOT).contains(needle)
        }
    }

    override suspend fun getQuote(symbol: String): StockQuote? =
        stock(symbol, "quote", StockQuote.serializer())?.let(::withYearRange)?.let { if (fillsGaps(it.symbol)) sample.fillQuote(it) else it }
            ?: withSample(symbol) { sample.quote(it, knownNames[it]) }

    /** Captured quotes predating the 52-week fields get them from the stored daily closes (last year). */
    private fun withYearRange(quote: StockQuote): StockQuote {
        if (!sampleFallback || quote.yearHigh != null && quote.yearLow != null) return quote
        val closes = stock(quote.symbol, "chart-daily", ListSerializer(PricePoint.serializer())).orEmpty()
            .takeLast(TRADING_DAYS_PER_YEAR).map { it.close }
        if (closes.isEmpty()) return quote
        return quote.copy(yearHigh = quote.yearHigh ?: closes.max(), yearLow = quote.yearLow ?: closes.min())
    }
    override suspend fun getProfile(symbol: String): CompanyProfile? =
        stock(symbol, "profile", CompanyProfile.serializer()) ?: withSample(symbol) { sample.profile(it, knownNames[it]) }
    override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals {
        val stored = stock(symbol, "fundamentals-$period", CompanyFundamentals.serializer())
        val quote = if (sampleFallback) getQuote(symbol) else null
        val generated = quote?.let { q -> safeSymbol(symbol)?.let { sample.fundamentals(it, period, q) } }
        return when {
            stored == null -> generated ?: CompanyFundamentals(symbol)
            generated != null && fillsGaps(stored.symbol) -> sample.fillFundamentals(stored, generated, sample.sessionDate(quote).year - 1)
            else -> stored
        }
    }

    /** Mock mode fills gaps in captured data, except for tickers kept sparse on purpose. */
    private fun fillsGaps(symbol: String) =
        sampleFallback && manifest?.keepMissing.orEmpty().none { it.equals(symbol, ignoreCase = true) }

    override suspend fun getIntradaySparkline(symbol: String): Sparkline {
        val stored = stock(symbol, "sparkline", Sparkline.serializer())
        if (!sampleFallback || stored != null && SampleMarketData.matches(stored.closes, quoteFor(symbol)?.price, INTRADAY_TOLERANCE)) {
            return stored ?: Sparkline(symbol, emptyList())
        }
        val closes = getIntradayPoints(symbol).map { it.close }
        return Sparkline(symbol, closes, sessionDate = quoteFor(symbol)?.let { sample.sessionDate(it).toString() })
    }

    override suspend fun getIntradayPoints(symbol: String): List<PricePoint> =
        series(symbol, "chart-intraday", INTRADAY_TOLERANCE) { s, quote ->
            val price = quote.price ?: return@series emptyList()
            sample.intraday(s, quote.previousClose ?: price, price, sample.sessionDate(quote), quote.dayLow, quote.dayHigh)
        }

    override suspend fun getDailyCloses(symbol: String): List<PricePoint> =
        series(symbol, "chart-daily", DAILY_TOLERANCE) { s, quote ->
            quote.price?.let { sample.dailyCloses(s, it, quote.previousClose, sample.sessionDate(quote)) }.orEmpty()
        }
    override suspend fun getWhyMoving(symbol: String): WhyMoving? = stock(symbol, "why-moving", WhyMoving.serializer())

    /** `stocks/{SYMBOL}/earnings-quarterly.json`, or (mock gaps) EPS generated from this ticker's own prices. */
    override suspend fun quarterlyEarnings(symbol: String): List<org.example.stocksteps.service.QuarterlyEarnings> {
        stock(symbol, "earnings-quarterly", ListSerializer(org.example.stocksteps.service.QuarterlyEarnings.serializer()))?.let { return it }
        if (!sampleFallback || !fillsGaps(symbol)) return emptyList()
        val pe = getFundamentals(symbol, "annual").valuation.metrics["pe"]?.value ?: return emptyList()
        return sample.quarterlyEarnings(safeSymbol(symbol) ?: return emptyList(), getDailyCloses(symbol), pe)
    }

    override suspend fun getMarketIndices(): List<MarketIndex> = snapshot?.indices.orEmpty()
    override suspend fun getGainers(): List<MarketMover> = snapshot?.gainers.orEmpty()
    override suspend fun getLosers(): List<MarketMover> = snapshot?.losers.orEmpty()
    override suspend fun getMostActive(): List<MarketMover> = snapshot?.mostActive.orEmpty()
    override suspend fun getMarketStatus(): MarketStatus = snapshot?.marketStatus ?: MarketStatus.UNKNOWN

    override suspend fun getNews(page: Int, limit: Int): List<NewsArticle> =
        news("news/market.json").drop(page * limit).take(limit)
    override suspend fun getCompanyNews(symbol: String): List<NewsArticle> =
        safeSymbol(symbol)?.let { news("news/company/$it.json", shift = false) }.orEmpty()

    /**
     * Market news is shifted by the fixture's age so "2h ago" stays meaningful. Company news keeps
     * its captured times so it lines up with the captured quote and daily closes (Why did it move?).
     */
    private fun news(path: String, shift: Boolean = true): List<NewsArticle> =
        load(path, ListSerializer(NewsArticle.serializer())).orEmpty().map { article ->
            if (shift) article.copy(publishedAt = article.publishedAt?.let { shifted(it) }) else article
        }

    private fun shifted(timestamp: String): String =
        runCatching { Instant.parse(timestamp).plus(age).toString() }.getOrDefault(timestamp)

    /** A stored price series, or a sample one ending at the quote when it is missing or stale. */
    private suspend fun series(
        symbol: String, name: String, tolerance: Double,
        generate: (String, StockQuote) -> List<PricePoint>
    ): List<PricePoint> {
        val stored = stock(symbol, name, ListSerializer(PricePoint.serializer())).orEmpty()
        if (!sampleFallback) return stored
        val quote = quoteFor(symbol) ?: return stored
        if (SampleMarketData.matches(stored.map { it.close }, quote.price, tolerance)) return stored
        return generate(safeSymbol(symbol) ?: return stored, quote)
    }

    private suspend fun quoteFor(symbol: String): StockQuote? = getQuote(symbol)

    private inline fun <T> withSample(symbol: String, block: (String) -> T?): T? =
        if (sampleFallback) safeSymbol(symbol)?.let(block) else null

    private fun <T> stock(symbol: String, name: String, serializer: KSerializer<T>): T? =
        safeSymbol(symbol)?.let { load("stocks/$it/$name.json", serializer) }

    /** Routes validate symbols already; this also keeps fixture paths inside [root]. */
    private fun safeSymbol(symbol: String): String? =
        symbol.uppercase(Locale.ROOT).takeIf { it.matches(SYMBOL) }

    private fun <T> load(path: String, serializer: KSerializer<T>): T? =
        read("$root/$path")?.let { json.decodeFromString(serializer, it) }

    private companion object {
        val SYMBOL = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")
        /** Stored series whose last close is further than this from the quote are replaced. */
        const val INTRADAY_TOLERANCE = 0.03
        const val DAILY_TOLERANCE = 0.15
        const val TRADING_DAYS_PER_YEAR = 252
    }
}
