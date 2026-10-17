package org.example.stocksteps.marketdata.sec

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import org.example.stocksteps.httpclient.apiCallText
import org.example.stocksteps.marketdata.CompanyFinancialHistory
import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.FinancialPeriod
import org.example.stocksteps.marketdata.Sourced
import org.example.stocksteps.model.FinancialPeriodStatement
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.providerCooldown
import java.time.Clock
import java.time.LocalDate

/** One row of SEC `company_tickers_exchange.json`. */
data class SecTicker(val cik: Long, val name: String, val ticker: String, val exchange: String?)

/** Ticker ↔ CIK (the SEC's own list: periodically updated, "not guaranteed to be accurate or complete"). */
class SecTickerMap(rows: List<SecTicker>) {
    private val byTicker = rows.groupBy { normalize(it.ticker) }
    private val byCik = rows.groupBy { it.cik }
    val size: Int = rows.size
    /** The CIK for [symbol], or null when unknown. `BRK.B` and `BRK-B` are the same listing. */
    fun cik(symbol: String): Long? = byTicker[normalize(symbol)]?.map { it.cik }?.distinct()?.singleOrNull()
    fun tickers(cik: Long): List<SecTicker> = byCik[cik].orEmpty()
    fun row(symbol: String): SecTicker? = byTicker[normalize(symbol)]?.firstOrNull()
    companion object { fun normalize(symbol: String) = symbol.trim().uppercase().replace('.', '-') }
}

/** The parts of `submissions` StockSteps uses: identity, fiscal year end and recent periodic filings (newest first). */
data class SecSubmissions(val cik: Long, val name: String?, val tickers: List<String>, val exchanges: List<String>, val fiscalYearEnd: String?,
                          val filings: List<SecFiling>) {
    val latestPeriodic: SecFiling? get() = filings.firstOrNull { it.form in SecConcepts.FORMS }
}
data class SecFiling(val accession: String, val form: String, val filingDate: LocalDate, val reportDate: LocalDate?)

/**
 * Phase 9B: SEC EDGAR client (data.sec.gov APIs and the www.sec.gov ticker file). Fair access (sec.gov "Accessing EDGAR Data"):
 * every request declares an identifying User-Agent with a contact address ([userAgent], env `SEC_USER_AGENT`; the client refuses to
 * start without one), requests are paced by the `sec` lane of `ProviderGuard` (5/s, 2 at a time, well under the published 10/s),
 * documents are size-bounded ([apiCallText]) and every answer is cached per instance with single flight:
 * tickers 24 h, companyfacts 24 h, submissions 6 h; refusals keep [providerCooldown] (403 1 h, 429 10 min), 404 is cached as "none".
 */
class SecEdgarClient(
    private val client: HttpClient,
    private val userAgent: String,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512, name = "sec"),
    private val requestTimeoutMillis: Long? = null,
    private val clock: Clock = Clock.systemUTC(),
    /** Upper bound for one companyfacts document (the largest filers' documents are tens of MB). */
    private val maxFactsBytes: Long = MAX_FACTS_BYTES
) {
    init { require(validUserAgent(userAgent)) { "SEC_USER_AGENT must name the app and a contact e-mail address" } }

    private data class Outcome<T : Any>(val value: T?, val failure: StockProviderException?)

    private suspend fun text(url: String, maxBytes: Long): String = client.apiCallText(url, maxBytes) {
        header(HttpHeaders.UserAgent, userAgent)
        header(HttpHeaders.Accept, "application/json")
        requestTimeoutMillis?.let { t -> timeout { requestTimeoutMillis = t } }
    }

    private suspend fun <T : Any> cached(key: String, ttl: Long, load: suspend () -> T?): T? {
        val outcome = cache.getOrLoad(key, ttl, resultTtl = { o: Outcome<T> -> providerCooldown(o.failure, ttl) }) {
            try { Outcome(load(), null) } catch (cause: StockProviderException) {
                if (cause.upstreamStatus == 404) Outcome(null, null) else Outcome(null, cause)
            } catch (_: SerializationException) { Outcome(null, StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)) }
            catch (_: IllegalArgumentException) { Outcome(null, StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)) }
        }
        outcome.failure?.let { throw it }
        return outcome.value
    }

    suspend fun tickers(): SecTickerMap = cached("tickers", DAY) { parseTickers(text(TICKERS_URL, MAX_TICKERS_BYTES)) }
        ?: throw StockProviderException(StockProviderException.Failure.UNAVAILABLE, 404)

    suspend fun cik(symbol: String): Long? = tickers().cik(symbol)

    /** All XBRL facts StockSteps uses for [cik]; null when the SEC has none (404: no XBRL filings). */
    suspend fun companyFacts(cik: Long): SecCompanyFacts? = cached("facts:${pad(cik)}", DAY) {
        SecCompanyFactsParser.parse(text("$BASE/api/xbrl/companyfacts/CIK${pad(cik)}.json", maxFactsBytes))
    }

    suspend fun submissions(cik: Long): SecSubmissions? = cached("submissions:${pad(cik)}", SIX_HOURS) {
        parseSubmissions(text("$BASE/submissions/CIK${pad(cik)}.json", MAX_SUBMISSIONS_BYTES))
    }

    /** Fiscal history for [symbol] from its companyfacts (null: unknown ticker or no XBRL facts). */
    suspend fun history(symbol: String, policy: RestatementPolicy = RestatementPolicy.LATEST_FILED): Sourced<CompanyFinancialHistory>? {
        val cik = cik(symbol) ?: return null
        val facts = companyFacts(cik) ?: return null
        val history = SecPeriodBuilder.build(symbol.uppercase(), facts, clock.instant(), policy)
        if (history.annual.isEmpty() && history.quarterly.isEmpty()) return null
        return Sourced(history, history.provenance)
    }

    companion object {
        const val BASE = "https://data.sec.gov"
        const val TICKERS_URL = "https://www.sec.gov/files/company_tickers_exchange.json"
        const val MAX_TICKERS_BYTES = 8L * 1024 * 1024
        const val MAX_FACTS_BYTES = 48L * 1024 * 1024
        const val MAX_SUBMISSIONS_BYTES = 12L * 1024 * 1024
        const val DAY = 86_400_000L
        const val SIX_HOURS = 21_600_000L
        private val EMAIL = Regex("[^\\s@<>]+@[^\\s@<>]+\\.[A-Za-z]{2,}")

        fun validUserAgent(ua: String) = ua.length in 8..200 && ua.none { it == '\n' || it == '\r' } && EMAIL.containsMatchIn(ua)
        fun pad(cik: Long) = cik.toString().padStart(10, '0')

        /** Null (SEC adapter off) unless `SEC_USER_AGENT` is a valid identifying agent. */
        fun fromEnvironment(client: HttpClient, env: (String) -> String? = System::getenv): SecEdgarClient? =
            env("SEC_USER_AGENT")?.trim()?.takeIf(::validUserAgent)?.let { SecEdgarClient(client, it, requestTimeoutMillis = 30_000) }

        private val json = Json { ignoreUnknownKeys = true }

        fun parseTickers(text: String): SecTickerMap {
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not a ticker file")
            val fields = root["fields"]?.jsonArray?.map { (it as JsonPrimitive).content } ?: throw IllegalArgumentException("no fields")
            val (ci, ni, ti, ei) = listOf("cik", "name", "ticker", "exchange").map { fields.indexOf(it) }
            require(ci >= 0 && ti >= 0)
            val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { r ->
                val row = r as? JsonArray ?: return@mapNotNull null
                fun at(i: Int) = row.getOrNull(i) as? JsonPrimitive
                val cik = at(ci)?.longOrNull ?: return@mapNotNull null
                val ticker = at(ti)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SecTicker(cik, at(ni)?.contentOrNull.orEmpty(), ticker, if (ei >= 0) at(ei)?.contentOrNull else null)
            }
            return SecTickerMap(rows)
        }

        fun parseSubmissions(text: String): SecSubmissions {
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not a submissions object")
            fun strings(o: JsonObject?, k: String) = (o?.get(k) as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull }
            val cik = (root["cik"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: throw IllegalArgumentException("no cik")
            val recent = (root["filings"] as? JsonObject)?.get("recent") as? JsonObject
            val acc = strings(recent, "accessionNumber"); val forms = strings(recent, "form")
            val dates = strings(recent, "filingDate"); val reports = strings(recent, "reportDate")
            val filings = acc.indices.mapNotNull { i ->
                val a = acc[i] ?: return@mapNotNull null
                val f = forms.getOrNull(i) ?: return@mapNotNull null
                val d = dates.getOrNull(i)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                SecFiling(a, f, d, reports.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            }.sortedByDescending { it.filingDate }
            return SecSubmissions(cik, (root["name"] as? JsonPrimitive)?.contentOrNull, strings(root, "tickers").filterNotNull(),
                strings(root, "exchanges").filterNotNull(), (root["fiscalYearEnd"] as? JsonPrimitive)?.contentOrNull, filings)
        }

        /**
         * The existing mobile contract row for one SEC period (backward compatible: same fields, Doubles in whole currency units).
         * Values StockSteps derived (Q4) are included only when [includeDerived]; free cash flow only when both inputs are reported.
         */
        fun toStatement(p: FinancialPeriod, includeDerived: Boolean = true): FinancialPeriodStatement {
            fun v(c: FinancialConcept) = p[c]?.takeIf { includeDerived || !it.derived }?.value?.toDouble()
            val ocf = v(FinancialConcept.OPERATING_CASH_FLOW); val capex = v(FinancialConcept.CAPITAL_EXPENDITURE)
            val debt = p[FinancialConcept.LONG_TERM_DEBT]?.value?.toDouble()
            return FinancialPeriodStatement(p.fiscalPeriod, p.fiscalYear, p.end.toString(), p.currency,
                revenue = v(FinancialConcept.REVENUE), grossProfit = v(FinancialConcept.GROSS_PROFIT), operatingIncome = v(FinancialConcept.OPERATING_INCOME),
                netIncome = v(FinancialConcept.NET_INCOME), epsDiluted = v(FinancialConcept.EPS_DILUTED), operatingCashFlow = ocf,
                capitalExpenditure = capex, freeCashFlow = if (ocf != null && capex != null) ocf - capex else null,
                cash = v(FinancialConcept.CASH), totalDebt = debt, totalAssets = v(FinancialConcept.TOTAL_ASSETS),
                totalLiabilities = v(FinancialConcept.TOTAL_LIABILITIES), equity = v(FinancialConcept.EQUITY),
                currentAssets = v(FinancialConcept.CURRENT_ASSETS), currentLiabilities = v(FinancialConcept.CURRENT_LIABILITIES))
        }
    }
}
