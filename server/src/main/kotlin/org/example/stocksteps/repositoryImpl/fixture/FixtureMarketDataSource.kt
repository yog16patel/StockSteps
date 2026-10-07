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
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * MOCK-mode data: responses previously captured from the REAL backend's public API
 * (see `scripts/capture-fixtures.sh`), served with no provider calls. Missing fixtures
 * behave like the provider having no data (404/empty), never like fabricated values.
 *
 * Layout under [root]: `manifest.json`, `market/snapshot.json`, `stocks/search.json`,
 * `stocks/{SYMBOL}/{quote,profile,sparkline,fundamentals-annual,fundamentals-quarter}.json`,
 * `news/market.json`, `news/company/{SYMBOL}.json`.
 */
class FixtureMarketDataSource(
    private val root: String = "fixtures",
    private val now: () -> Instant = Instant::now,
    private val read: (String) -> String? = { path ->
        FixtureMarketDataSource::class.java.classLoader.getResource(path)?.readText()
    }
) : StockProviderRepository, MarketDataProvider, NewsProviderRepository, PriceHistoryProvider {

    @Serializable
    private data class Manifest(val capturedAt: String)

    private val json = Json { ignoreUnknownKeys = true }

    /** News times are shifted by the fixture's age so "2h ago" stays meaningful. */
    private val age: Duration by lazy {
        val captured = load("manifest.json", Manifest.serializer())?.capturedAt
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        captured?.let { Duration.between(it, now()).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    }

    private val snapshot: MarketSnapshot? by lazy { load("market/snapshot.json", MarketSnapshot.serializer()) }

    override suspend fun searchStocks(query: String): List<StockSearchResult> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return load("stocks/search.json", ListSerializer(StockSearchResult.serializer())).orEmpty().filter {
            it.symbol.lowercase(Locale.ROOT).startsWith(needle) || it.name.lowercase(Locale.ROOT).contains(needle)
        }
    }

    override suspend fun getQuote(symbol: String): StockQuote? = stock(symbol, "quote", StockQuote.serializer())
    override suspend fun getProfile(symbol: String): CompanyProfile? = stock(symbol, "profile", CompanyProfile.serializer())
    override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals =
        stock(symbol, "fundamentals-$period", CompanyFundamentals.serializer()) ?: CompanyFundamentals(symbol)
    override suspend fun getIntradaySparkline(symbol: String): Sparkline =
        stock(symbol, "sparkline", Sparkline.serializer()) ?: Sparkline(symbol, emptyList())

    override suspend fun getMarketIndices(): List<MarketIndex> = snapshot?.indices.orEmpty()
    override suspend fun getGainers(): List<MarketMover> = snapshot?.gainers.orEmpty()
    override suspend fun getLosers(): List<MarketMover> = snapshot?.losers.orEmpty()
    override suspend fun getMostActive(): List<MarketMover> = snapshot?.mostActive.orEmpty()
    override suspend fun getMarketStatus(): MarketStatus = snapshot?.marketStatus ?: MarketStatus.UNKNOWN

    override suspend fun getNews(page: Int, limit: Int): List<NewsArticle> =
        news("news/market.json").drop(page * limit).take(limit)
    override suspend fun getCompanyNews(symbol: String): List<NewsArticle> =
        safeSymbol(symbol)?.let { news("news/company/$it.json") }.orEmpty()

    private fun news(path: String): List<NewsArticle> =
        load(path, ListSerializer(NewsArticle.serializer())).orEmpty().map { article ->
            article.copy(publishedAt = article.publishedAt?.let { shifted(it) })
        }

    private fun shifted(timestamp: String): String =
        runCatching { Instant.parse(timestamp).plus(age).toString() }.getOrDefault(timestamp)

    private fun <T> stock(symbol: String, name: String, serializer: KSerializer<T>): T? =
        safeSymbol(symbol)?.let { load("stocks/$it/$name.json", serializer) }

    /** Routes validate symbols already; this also keeps fixture paths inside [root]. */
    private fun safeSymbol(symbol: String): String? =
        symbol.uppercase(Locale.ROOT).takeIf { it.matches(SYMBOL) }

    private fun <T> load(path: String, serializer: KSerializer<T>): T? =
        read("$root/$path")?.let { json.decodeFromString(serializer, it) }

    private companion object {
        val SYMBOL = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")
    }
}
