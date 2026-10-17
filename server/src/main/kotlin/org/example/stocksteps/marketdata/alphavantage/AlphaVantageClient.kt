package org.example.stocksteps.marketdata.alphavantage

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import kotlinx.serialization.SerializationException
import org.example.stocksteps.httpclient.alphaVantageCall
import org.example.stocksteps.marketdata.alphavantage.AlphaVantageParsers.DailySeries
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.providerCooldown
import java.net.URLEncoder
import java.time.Clock

/**
 * Phase 9B.7: typed Alpha Vantage functions over [alphaVantageCall], with one per-instance cache (single flight per function + symbol,
 * bounded LRU) in front of every request:
 * - refusals are cached as outcomes with [providerCooldown] (premium-only/invalid key 1 h, rate limit 10 min, other 30 s) — never retried
 *   in a loop, never stored as data; an `Error Message` (unknown symbol) is a cached "none";
 * - premium functions are **never called** without a configured premium plan ([AlphaVantageConfig.premium]): they fail locally as
 *   restricted (no request, no quota used);
 * - symbols Alpha Vantage doesn't list (index tickers `^…`) are refused locally.
 * TTLs: daily series and quotes until the listing's next expected close ([EodSessions.ttl]; both are end-of-day data); movers 15 min;
 * search 6 h; overview, statements, corporate actions 24 h; earnings 12 h.
 */
class AlphaVantageClient(
    private val http: HttpClient,
    private val config: AlphaVantageConfig,
    private val sessions: EodSessions,
    private val clock: Clock = Clock.systemUTC(),
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 2_048, name = "alphavantage"),
    private val requestTimeoutMillis: Long? = null
) {
    private data class Outcome<T : Any>(val value: T?, val failure: StockProviderException?)

    val premium: Boolean get() = config.premium

    /** Alpha Vantage's symbol for an exchange-qualified StockSteps symbol: `.TO` → `.TRT`; index tickers aren't supported (null). */
    fun avSymbol(symbol: String): String? {
        val s = symbol.trim().uppercase()
        if (s.isEmpty() || s.startsWith("^") || s.length > 20 || s.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
        return if (s.endsWith(".TO")) s.removeSuffix(".TO") + ".TRT" else s
    }

    private fun restrictedLocally() = StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402)

    private suspend fun text(function: String, params: List<Pair<String, String>>, maxBytes: Long = MAX_BYTES): String {
        val query = (listOf("function" to function) + params).joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        return http.alphaVantageCall("$BASE?$query", config.apiKey, maxBytes) { requestTimeoutMillis?.let { t -> timeout { requestTimeoutMillis = t } } }
    }

    private suspend fun <T : Any> cached(key: String, ttl: Long, resultTtl: ((T?) -> Long)? = null, load: suspend () -> T?): T? {
        val outcome = cache.getOrLoad(key, ttl, resultTtl = { o: Outcome<T> -> if (o.failure != null) providerCooldown(o.failure, ttl) else resultTtl?.invoke(o.value) ?: ttl }) {
            try { Outcome(load(), null) } catch (cause: StockProviderException) {
                if (cause.upstreamStatus == 404) Outcome(null, null) else Outcome(null, cause)
            } catch (_: SerializationException) { Outcome(null, StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)) }
            catch (_: IllegalArgumentException) { Outcome(null, StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)) }
        }
        outcome.failure?.let { throw it }
        return outcome.value
    }

    private fun need(function: String) { if (!AlphaVantageFunctions.usable(function, config.premium)) throw restrictedLocally() }

    /** As-traded daily OHLCV (TIME_SERIES_DAILY): `full` history on a premium plan, else the latest 100 sessions. */
    suspend fun dailyRaw(symbol: String): DailySeries? {
        val av = avSymbol(symbol) ?: return null
        val size = if (config.premium) "full" else "compact"
        return cached("daily:$av:raw:$size", DAILY_FALLBACK_TTL, resultTtl = { s: DailySeries? -> dailyTtl(symbol, s) }) {
            need("TIME_SERIES_DAILY")
            AlphaVantageParsers.daily(text("TIME_SERIES_DAILY", listOf("symbol" to av, "outputsize" to size)))
        }
    }

    /** Daily series with adjusted close and split/dividend events (premium). */
    suspend fun dailyAdjusted(symbol: String): DailySeries? {
        val av = avSymbol(symbol) ?: return null
        need("TIME_SERIES_DAILY_ADJUSTED")
        return cached("daily:$av:adjusted:full", DAILY_FALLBACK_TTL, resultTtl = { s: DailySeries? -> dailyTtl(symbol, s) }) {
            AlphaVantageParsers.daily(text("TIME_SERIES_DAILY_ADJUSTED", listOf("symbol" to av, "outputsize" to "full")))
        }
    }

    private fun dailyTtl(symbol: String, s: DailySeries?): Long =
        if (s == null) NEGATIVE_TTL else sessions.ttl(symbol, s.bars.map { org.example.stocksteps.model.PricePoint(it.date.toString(), it.close.toDouble()) }, DAILY_FALLBACK_TTL)

    /** GLOBAL_QUOTE without `entitlement` (end-of-day data; StockSteps never requests realtime or delayed). */
    suspend fun quote(symbol: String): AlphaVantageParsers.Quote? {
        val av = avSymbol(symbol) ?: return null
        // End-of-day data: the quote only changes after the next close, so it lives until the next expected close (15 min if undated).
        return cached("quote:$av", QUOTE_TTL, resultTtl = { q: AlphaVantageParsers.Quote? ->
            val day = q?.latestTradingDay; val price = q?.price
            if (day != null && price != null) sessions.ttl(symbol, listOf(org.example.stocksteps.model.PricePoint(day.toString(), price.toDouble())), QUOTE_TTL) else QUOTE_TTL
        }) { need("GLOBAL_QUOTE"); AlphaVantageParsers.quote(text("GLOBAL_QUOTE", listOf("symbol" to av))) }
    }

    suspend fun search(query: String): List<AlphaVantageParsers.Match> {
        val q = query.trim().lowercase().take(60)
        if (q.isEmpty()) return emptyList()
        return cached("search:$q", SEARCH_TTL) { need("SYMBOL_SEARCH"); AlphaVantageParsers.search(text("SYMBOL_SEARCH", listOf("keywords" to q))) }.orEmpty()
    }

    suspend fun overview(symbol: String): AlphaVantageParsers.Overview? {
        val av = avSymbol(symbol) ?: return null
        need("OVERVIEW")
        return cached("overview:$av", DAY) { AlphaVantageParsers.overview(text("OVERVIEW", listOf("symbol" to av))) }
    }

    /** INCOME_STATEMENT, BALANCE_SHEET or CASH_FLOW. */
    suspend fun statement(function: String, symbol: String): AlphaVantageParsers.Statement? {
        require(function in setOf("INCOME_STATEMENT", "BALANCE_SHEET", "CASH_FLOW"))
        val av = avSymbol(symbol) ?: return null
        return cached("$function:$av", DAY) { need(function); AlphaVantageParsers.statement(text(function, listOf("symbol" to av))) }
    }

    suspend fun earnings(symbol: String): AlphaVantageParsers.Earnings? {
        val av = avSymbol(symbol) ?: return null
        return cached("earnings:$av", 12 * HOUR) { need("EARNINGS"); AlphaVantageParsers.earnings(text("EARNINGS", listOf("symbol" to av))) }
    }

    suspend fun dividends(symbol: String): List<AlphaVantageParsers.Dividend>? {
        val av = avSymbol(symbol) ?: return null
        return cached("dividends:$av", DAY) { need("DIVIDENDS"); AlphaVantageParsers.dividends(text("DIVIDENDS", listOf("symbol" to av))) }
    }

    suspend fun splits(symbol: String): List<AlphaVantageParsers.Split>? {
        val av = avSymbol(symbol) ?: return null
        return cached("splits:$av", DAY) { need("SPLITS"); AlphaVantageParsers.splits(text("SPLITS", listOf("symbol" to av))) }
    }

    /** US top gainers, losers and most active (end-of-day by default; one request serves all three lists). */
    suspend fun movers(): AlphaVantageParsers.Movers? = cached("movers", QUOTE_TTL) { need("TOP_GAINERS_LOSERS"); AlphaVantageParsers.movers(text("TOP_GAINERS_LOSERS", emptyList())) }

    fun stats(): Map<String, Long> = cache.stats()

    companion object {
        const val BASE = "https://www.alphavantage.co/query"
        /** `full` daily history of a long-listed stock is a few MB; statements and overview are far smaller. */
        const val MAX_BYTES = 16L * 1024 * 1024
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val QUOTE_TTL = 15 * 60_000L
        const val SEARCH_TTL = 6 * HOUR
        const val DAILY_FALLBACK_TTL = 6 * HOUR
        const val NEGATIVE_TTL = 6 * HOUR
    }
}
