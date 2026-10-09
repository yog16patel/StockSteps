package org.example.stocksteps.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A deterministic FMP on a Ktor MockEngine for the Phase 3 tests (REAL adapters, no network, no keys).
 * Counts every request by endpoint and by `endpoint?period=…`; statements are generated per symbol so different
 * companies have different figures. [newQuarter] adds a newly published quarter; [status] makes an endpoint fail.
 */
internal class FmpMock(private val universeSize: Int = 30, private val latency: Long = 2) {
    val counts = ConcurrentHashMap<String, AtomicInteger>()
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val total get() = counts.entries.filter { '?' !in it.key }.sumOf { it.value.get() }
    fun n(path: String) = counts[path]?.get() ?: 0
    /** Endpoint → HTTP status to return instead of data (e.g. 429, 403, 500). */
    val status = ConcurrentHashMap<String, Int>()
    /** A quarter published after the first load (period end, accepted date). */
    @Volatile var newQuarter: Pair<String, String>? = null
    /** Revenue added to the newest quarter (a provider correction). */
    @Volatile var correction = 0.0
    /** Symbols whose requests fail with 503 (any endpoint). */
    val failSymbols: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Requests answered with an error status. */
    val failed = AtomicInteger()
    /** When set, only these symbols exist: profile/quote/statements answer `[]` for every other symbol. */
    @Volatile var knownSymbols: Set<String>? = null
    private val inFlight = AtomicInteger()
    /** Highest number of concurrent requests seen. */
    val peakConcurrency = AtomicInteger()
    /** Finnhub earnings-calendar rows (JSON objects) returned for AAPL. */
    @Volatile var finnhubEvents = ""

    private fun base(symbol: String) = 1_000.0 + (symbol.hashCode() and 0xff)

    private fun quarters(symbol: String, q: Boolean): String {
        val rows = mutableListOf<String>()
        // A published fiscal Q4 also adds the fiscal year to annual statements.
        newQuarter?.takeIf { !q }?.let { (end, accepted) ->
            rows += """{"symbol":"$symbol","date":"$end","period":"FY","fiscalYear":"2026","revenue":${base(symbol) * 4.2},"netIncome":${base(symbol)},"epsDiluted":6.0,"reportedCurrency":"USD","acceptedDate":"$accepted"}"""
        }
        newQuarter?.takeIf { q }?.let { (end, accepted) ->
            rows += """{"symbol":"$symbol","date":"$end","period":"Q4","fiscalYear":"2026","revenue":${base(symbol) * 1.1 + correction},"netIncome":${base(symbol) / 5},"epsDiluted":1.5,"reportedCurrency":"USD","acceptedDate":"$accepted"}"""
        }
        val end = LocalDate.parse("2026-06-27")
        for (i in 0 until if (q) 24 else 6) {
            val date = if (q) end.minusMonths(3L * i) else LocalDate.parse("2025-09-27").minusYears(i.toLong())
            val fy = if (q) 2026 - (i + 1) / 4 else 2025 - i
            val period = if (q) "Q${3 - i % 4 + if (3 - i % 4 <= 0) 4 else 0}" else "FY"
            val revenue = base(symbol) * (if (q) 1.0 else 4.0) * (1 - i * 0.02)
            rows += """{"symbol":"$symbol","date":"$date","period":"$period","fiscalYear":"$fy","revenue":$revenue,"netIncome":${revenue / 5},"epsDiluted":${1.0 - i * 0.01},"reportedCurrency":"USD","acceptedDate":"$date 08:00:00"}"""
        }
        return rows.joinToString(",", "[", "]")
    }

    private fun sheets(symbol: String, q: Boolean) = (listOfNotNull(newQuarter?.let { (end, _) ->
        """{"symbol":"$symbol","date":"$end","period":"${if (q) "Q4" else "FY"}","fiscalYear":"2026","reportedCurrency":"USD","cashAndCashEquivalents":${base(symbol)},"totalDebt":${base(symbol) * 2},"totalStockholdersEquity":${base(symbol) * 3},"operatingCashFlow":${base(symbol) / 2},"capitalExpenditure":-${base(symbol) / 10}}"""
    }) + (0 until if (q) 8 else 6).map { i ->
        val date = if (q) LocalDate.parse("2026-06-27").minusMonths(3L * i) else LocalDate.parse("2025-09-27").minusYears(i.toLong())
        val fy = if (q) 2026 - (i + 1) / 4 else 2025 - i
        val period = if (q) "Q${3 - i % 4 + if (3 - i % 4 <= 0) 4 else 0}" else "FY"
        """{"symbol":"$symbol","date":"$date","period":"$period","fiscalYear":"$fy","reportedCurrency":"USD","cashAndCashEquivalents":${base(symbol)},"totalDebt":${base(symbol) * 2},"totalStockholdersEquity":${base(symbol) * 3},"operatingCashFlow":${base(symbol) / 2},"capitalExpenditure":-${base(symbol) / 10}}"""
    }).joinToString(",", "[", "]")

    val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath.substringAfter("/stable/").substringAfter("/api/v1/")
        val period = request.url.parameters["period"]
        val symbol = request.url.parameters["symbol"] ?: "AAPL"
        counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        period?.let { counts.getOrPut("$path?period=$it") { AtomicInteger() }.incrementAndGet() }
        log += "$path|$symbol|${period.orEmpty()}"
        val now = inFlight.incrementAndGet(); peakConcurrency.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try { if (latency > 0) delay(latency) } finally { inFlight.decrementAndGet() }
        if (knownSymbols?.let { request.url.parameters["symbol"] != null && symbol !in it } == true && path != "company-screener" && !path.startsWith("calendar"))
            return@MockEngine respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        (status[path] ?: 503.takeIf { request.url.parameters["symbol"]?.let { it in failSymbols } == true })?.let { failed.incrementAndGet(); return@MockEngine respond("""{"error":"x"}""", HttpStatusCode.fromValue(it), headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }
        val body = when (path) {
            "quote" -> """[{"symbol":"$symbol","price":100.0,"previousClose":99.0,"timestamp":1791396000}]"""
            "profile" -> """[{"symbol":"$symbol","companyName":"$symbol Corp","currency":"${if (symbol.endsWith(".TO")) "CAD" else "USD"}","exchange":"${if (symbol.endsWith(".TO")) "TSX" else "NASDAQ"}","sector":"Technology","industry":"Software"}]"""
            "company-screener" -> (0 until universeSize).joinToString(",", "[", "]") { i ->
                val exchange = request.url.parameters["exchange"]
                val s = if (exchange == "TSX") "T$i.TO" else "$exchange$i"
                """{"symbol":"$s","companyName":"Co $i","marketCap":${1000 - i}000000000,"sector":"Technology","industry":"Software","price":50.0,"volume":1000000,"exchangeShortName":"$exchange","country":"${if (exchange == "TSX") "CA" else "US"}"}""" }
            "income-statement" -> quarters(symbol, period == "quarter")
            "balance-sheet-statement", "cash-flow-statement" -> sheets(symbol, period == "quarter")
            "ratios-ttm" -> """[{"symbol":"$symbol","priceToEarningsRatioTTM":25.0,"priceToSalesRatioTTM":5.0,"netProfitMarginTTM":0.2,"grossProfitMarginTTM":0.4,"debtToEquityRatioTTM":0.6,"currentRatioTTM":1.4}]"""
            "key-metrics-ttm" -> """[{"symbol":"$symbol","returnOnEquityTTM":0.3,"returnOnInvestedCapitalTTM":0.2,"evToEBITDATTM":18.0}]"""
            "income-statement-ttm" -> """[{"symbol":"$symbol","date":"${newQuarter?.first ?: "2026-06-27"}","revenue":${base(symbol) * 4},"netIncome":${base(symbol)},"ebitda":${base(symbol) * 1.5},"epsDiluted":4.0,"reportedCurrency":"USD"}]"""
            "cash-flow-statement-ttm" -> """[{"symbol":"$symbol","date":"${newQuarter?.first ?: "2026-06-27"}","operatingCashFlow":${base(symbol)},"capitalExpenditure":-${base(symbol) / 10},"commonDividendsPaid":-${base(symbol) / 20},"reportedCurrency":"USD"}]"""
            "analyst-estimates" -> """[{"symbol":"$symbol","date":"2026-09-27","epsAvg":4.5,"numAnalystsEps":10},{"symbol":"$symbol","date":"2027-09-27","epsAvg":5.0,"numAnalystsEps":8}]"""
            "dividends" -> (0 until 8).joinToString(",", "[", "]") { i -> """{"symbol":"$symbol","date":"${LocalDate.parse("2026-08-10").minusMonths(3L * i)}","adjDividend":${0.25 - i * 0.005},"dividend":${0.25 - i * 0.005}}""" }
            "shares-float" -> """[{"symbol":"$symbol","outstandingShares":1000000000}]"""
            "historical-chart/5min" -> """[{"date":"2026-10-07 09:30:00","close":100.0},{"date":"2026-10-07 09:35:00","close":101.0}]"""
            "historical-price-eod/light" -> """[{"date":"2026-10-05","price":99.0},{"date":"2026-10-06","price":100.0}]"""
            "calendar/earnings" -> """{"earningsCalendar":[${if (request.url.parameters["symbol"] in listOf(null, "AAPL")) finnhubEvents else ""}]}"""
            else -> "[]"
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) } }
}
