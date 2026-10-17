package org.example.stocksteps.marketdata.alphavantage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Phase 9B.7: Alpha Vantage response parsers. Pure; never invent values: Alpha Vantage sends numbers as strings and missing values as
 * `"None"`, `"-"` or `""`, which become null (never 0). Rows that can't be read are skipped and counted. Refusals inside HTTP 200
 * (`Information`/`Note`/`Error Message`) are classified earlier ([org.example.stocksteps.httpclient.AlphaVantageAnswers]); a parser that
 * still meets an object without its data key throws [IllegalArgumentException] (an invalid response, never empty data).
 *
 * Field names: TIME_SERIES_DAILY and TIME_SERIES_DAILY_ADJUSTED were checked against the documentation's own sample responses
 * (2026-10-17); the others follow the documented response shapes and must be confirmed by the first bounded REAL smoke test.
 */
object AlphaVantageParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun obj(text: String): JsonObject = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not an object")
    internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "None" && it != "-" && it != "null" }
    internal fun JsonObject.dec(k: String): BigDecimal? = str(k)?.removeSuffix("%")?.toBigDecimalOrNull()
    internal fun JsonObject.date(k: String): LocalDate? = str(k)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // ---- Prices -------------------------------------------------------------------------------------------------------------

    data class DailyBar(val date: LocalDate, val open: BigDecimal?, val high: BigDecimal?, val low: BigDecimal?, val close: BigDecimal,
                        val adjustedClose: BigDecimal?, val volume: Long?, val dividend: BigDecimal?, val splitCoefficient: BigDecimal?)

    /** [bars] oldest first; [adjusted] = the series came from TIME_SERIES_DAILY_ADJUSTED. */
    data class DailySeries(val symbol: String?, val lastRefreshed: LocalDate?, val timeZone: String?, val outputSize: String?, val adjusted: Boolean,
                           val bars: List<DailyBar>, val skipped: Int)

    fun daily(text: String): DailySeries {
        val root = obj(text)
        val meta = root["Meta Data"] as? JsonObject
        val series = root["Time Series (Daily)"] as? JsonObject ?: throw IllegalArgumentException("no daily series")
        var skipped = 0
        var adjusted = false
        val bars = series.mapNotNull { (day, v) ->
            val row = v as? JsonObject
            val date = runCatching { LocalDate.parse(day) }.getOrNull()
            val close = row?.dec("4. close")?.takeIf { it.signum() > 0 }
            if (row == null || date == null || close == null) { skipped++; return@mapNotNull null }
            if ("5. adjusted close" in row) adjusted = true
            DailyBar(date, row.dec("1. open"), row.dec("2. high"), row.dec("3. low"), close, row.dec("5. adjusted close")?.takeIf { it.signum() > 0 },
                (row.str("6. volume") ?: row.str("5. volume"))?.toBigDecimalOrNull()?.toLong(), row.dec("7. dividend amount"), row.dec("8. split coefficient"))
        }.sortedBy { it.date }
        return DailySeries(meta?.str("2. Symbol"), meta?.date("3. Last Refreshed"), meta?.str("5. Time Zone") ?: meta?.str("4. Time Zone"),
            meta?.str("4. Output Size"), adjusted, bars, skipped)
    }

    data class Quote(val symbol: String, val open: BigDecimal?, val high: BigDecimal?, val low: BigDecimal?, val price: BigDecimal?, val volume: Long?,
                     val latestTradingDay: LocalDate?, val previousClose: BigDecimal?, val change: BigDecimal?, val changePercent: BigDecimal?)

    /** GLOBAL_QUOTE; null for an empty `Global Quote` (unknown symbol). */
    fun quote(text: String): Quote? {
        val q = obj(text)["Global Quote"] as? JsonObject ?: throw IllegalArgumentException("no quote")
        val symbol = q.str("01. symbol") ?: return null
        return Quote(symbol, q.dec("02. open"), q.dec("03. high"), q.dec("04. low"), q.dec("05. price"), q.str("06. volume")?.toBigDecimalOrNull()?.toLong(),
            q.date("07. latest trading day"), q.dec("08. previous close"), q.dec("09. change"), q.dec("10. change percent"))
    }

    // ---- Reference ----------------------------------------------------------------------------------------------------------

    data class Match(val symbol: String, val name: String, val type: String?, val region: String?, val currency: String?, val matchScore: BigDecimal?)

    fun search(text: String): List<Match> {
        val matches = obj(text)["bestMatches"] as? JsonArray ?: throw IllegalArgumentException("no matches")
        return matches.mapNotNull { m ->
            val o = m as? JsonObject ?: return@mapNotNull null
            val symbol = o.str("1. symbol") ?: return@mapNotNull null
            Match(symbol, o.str("2. name") ?: return@mapNotNull null, o.str("3. type"), o.str("4. region"), o.str("8. currency"), o.dec("9. matchScore"))
        }
    }

    data class Overview(val symbol: String, val assetType: String?, val name: String?, val description: String?, val cik: String?, val exchange: String?,
                        val currency: String?, val country: String?, val sector: String?, val industry: String?, val website: String?,
                        val fiscalYearEnd: String?, val latestQuarter: LocalDate?, val marketCap: BigDecimal?, val sharesOutstanding: BigDecimal?)

    /** OVERVIEW; null for `{}` (no overview for this symbol). */
    fun overview(text: String): Overview? {
        val o = obj(text)
        val symbol = o.str("Symbol") ?: if (o.isEmpty()) return null else throw IllegalArgumentException("no overview")
        return Overview(symbol, o.str("AssetType"), o.str("Name"), o.str("Description"), o.str("CIK"), o.str("Exchange"), o.str("Currency"), o.str("Country"),
            o.str("Sector"), o.str("Industry"), o.str("OfficialSite"), o.str("FiscalYearEnd"), o.date("LatestQuarter"), o.dec("MarketCapitalization"),
            o.dec("SharesOutstanding"))
    }

    data class Mover(val ticker: String, val price: BigDecimal?, val changeAmount: BigDecimal?, val changePercent: BigDecimal?, val volume: Long?)
    data class Movers(val lastUpdated: String?, val gainers: List<Mover>, val losers: List<Mover>, val mostActive: List<Mover>)

    fun movers(text: String): Movers {
        val o = obj(text)
        fun list(k: String) = (o[k] as? JsonArray ?: throw IllegalArgumentException("no $k")).mapNotNull { r ->
            val x = r as? JsonObject ?: return@mapNotNull null
            Mover(x.str("ticker") ?: return@mapNotNull null, x.dec("price"), x.dec("change_amount"), x.dec("change_percentage"), x.str("volume")?.toLongOrNull())
        }
        return Movers(o.str("last_updated"), list("top_gainers"), list("top_losers"), list("most_actively_traded"))
    }

    // ---- Fundamentals -------------------------------------------------------------------------------------------------------

    /** One reported period of a statement: [values] by Alpha Vantage field name, exact decimals, missing fields absent. */
    data class Report(val fiscalDateEnding: LocalDate, val currency: String?, val values: Map<String, BigDecimal>)
    data class Statement(val symbol: String?, val annual: List<Report>, val quarterly: List<Report>, val skipped: Int)

    /** INCOME_STATEMENT, BALANCE_SHEET or CASH_FLOW (same shape: `annualReports` / `quarterlyReports`, newest first). */
    fun statement(text: String): Statement {
        val o = obj(text)
        val a = o["annualReports"] as? JsonArray; val q = o["quarterlyReports"] as? JsonArray
        if (a == null && q == null) { if (o.isEmpty()) return Statement(null, emptyList(), emptyList(), 0); throw IllegalArgumentException("no reports") }
        var skipped = 0
        fun reports(arr: JsonArray?) = arr.orEmpty().mapNotNull { r ->
            val x = r as? JsonObject
            val end = x?.date("fiscalDateEnding")
            if (x == null || end == null) { skipped++; return@mapNotNull null }
            Report(end, x.str("reportedCurrency"), x.keys.filter { it != "fiscalDateEnding" && it != "reportedCurrency" }.mapNotNull { k -> x.dec(k)?.let { k to it } }.toMap())
        }.sortedByDescending { it.fiscalDateEnding }
        return Statement(o.str("symbol"), reports(a), reports(q), skipped)
    }

    data class QuarterlyEps(val fiscalDateEnding: LocalDate, val reportedDate: LocalDate?, val reportedEps: BigDecimal?, val estimatedEps: BigDecimal?)
    data class Earnings(val symbol: String?, val annual: List<Pair<LocalDate, BigDecimal>>, val quarterly: List<QuarterlyEps>)

    fun earnings(text: String): Earnings {
        val o = obj(text)
        val a = o["annualEarnings"] as? JsonArray; val q = o["quarterlyEarnings"] as? JsonArray
        if (a == null && q == null) { if (o.isEmpty()) return Earnings(null, emptyList(), emptyList()); throw IllegalArgumentException("no earnings") }
        return Earnings(o.str("symbol"),
            a.orEmpty().mapNotNull { r -> (r as? JsonObject)?.let { x -> x.date("fiscalDateEnding")?.let { d -> x.dec("reportedEPS")?.let { d to it } } } },
            q.orEmpty().mapNotNull { r -> (r as? JsonObject)?.let { x -> x.date("fiscalDateEnding")?.let { QuarterlyEps(it, x.date("reportedDate"), x.dec("reportedEPS"), x.dec("estimatedEPS")) } } })
    }

    // ---- Corporate actions --------------------------------------------------------------------------------------------------

    data class Dividend(val exDate: LocalDate, val declarationDate: LocalDate?, val recordDate: LocalDate?, val paymentDate: LocalDate?, val amount: BigDecimal)
    /** [ratio] = new shares per old share (`split_factor`, e.g. 4.0 for 4-for-1; 0.1 for 1-for-10). */
    data class Split(val effectiveDate: LocalDate, val ratio: BigDecimal)

    private fun dataRows(text: String): JsonArray? {
        val o = obj(text)
        return o["data"] as? JsonArray ?: if (o.isEmpty() || (o.size == 1 && "symbol" in o)) null else throw IllegalArgumentException("no data")
    }

    fun dividends(text: String): List<Dividend> = dataRows(text).orEmpty().mapNotNull { r ->
        val x = r as? JsonObject ?: return@mapNotNull null
        Dividend(x.date("ex_dividend_date") ?: return@mapNotNull null, x.date("declaration_date"), x.date("record_date"), x.date("payment_date"),
            x.dec("amount")?.takeIf { it.signum() >= 0 } ?: return@mapNotNull null)
    }.sortedBy { it.exDate }

    fun splits(text: String): List<Split> = dataRows(text).orEmpty().mapNotNull { r ->
        val x = r as? JsonObject ?: return@mapNotNull null
        Split(x.date("effective_date") ?: return@mapNotNull null, x.dec("split_factor")?.takeIf { it.signum() > 0 } ?: return@mapNotNull null)
    }.sortedBy { it.effectiveDate }
}
