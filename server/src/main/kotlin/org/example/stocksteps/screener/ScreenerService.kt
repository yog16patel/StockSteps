package org.example.stocksteps.screener

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.*
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.StockService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** One company in the defined screening universe, with what the universe source itself reports. */
data class UniverseEntry(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val country: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val price: Double? = null,
    val marketCap: Double? = null,
    val volume: Double? = null,
    val isEtf: Boolean? = null
)

data class UniverseDefinition(val description: String, val entries: List<UniverseEntry>)

fun interface ScreenerUniverseSource {
    suspend fun universe(): UniverseDefinition
}

/** MOCK: the symbols in `fixtures/screener/universe.json`; no network. */
class FixtureScreenerUniverse : ScreenerUniverseSource {
    @Serializable private data class File(val description: String, val symbols: List<String>)
    private val definition by lazy {
        val text = requireNotNull(javaClass.classLoader.getResource("fixtures/screener/universe.json")) { "Missing screener universe fixture" }.readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(File.serializer(), text)
        UniverseDefinition(file.description, file.symbols.map { UniverseEntry(it) })
    }
    override suspend fun universe() = definition
}

/**
 * REAL: FMP `company-screener` (one request, cached 24 h) defines the universe: actively trading
 * stocks on the configured exchanges above a minimum market cap, largest first, up to [limit].
 * It also supplies price, market cap, volume, sector and industry, so basic filters need no
 * per-company requests.
 */
class FmpScreenerUniverse(
    private val client: HttpClient,
    private val apiKey: String,
    private val exchanges: List<String>,
    private val limit: Int,
    private val minMarketCap: Long,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 4)
) : ScreenerUniverseSource {
    @Serializable private data class Row(
        val symbol: String, val companyName: String? = null, val marketCap: Double? = null, val sector: String? = null,
        val industry: String? = null, val price: Double? = null, val volume: Double? = null, val exchangeShortName: String? = null,
        val exchange: String? = null, val country: String? = null, val isEtf: Boolean? = null, val isFund: Boolean? = null
    )
    override suspend fun universe(): UniverseDefinition = cache.getOrLoad("universe", 86_400_000L) {
        val rows = exchanges.flatMap { exchange ->
            client.apiCall<List<Row>>("https://financialmodelingprep.com/stable/company-screener", apiKey) {
                parameter("exchange", exchange)
                parameter("marketCapMoreThan", minMarketCap)
                parameter("isEtf", false); parameter("isFund", false); parameter("isActivelyTrading", true)
                parameter("limit", limit)
            }
        }
        UniverseDefinition(
            "Actively traded stocks on ${exchanges.joinToString()} with market cap over $${minMarketCap / 1_000_000_000}B (largest ${limit} per exchange, from Financial Modeling Prep).",
            rows.filter { it.isEtf != true && it.isFund != true }.distinctBy { it.symbol }.map {
                UniverseEntry(it.symbol, it.companyName, it.exchangeShortName ?: it.exchange, it.country, it.sector, it.industry, it.price, it.marketCap, it.volume, false)
            }
        )
    }
}

class ScreenerRequestException(val status: Int, val code: String, message: String) : Exception(message)

/**
 * Screening and comparison over the defined universe. Records are built by [CompanyRecordBuilder]
 * from the same quote/profile/fundamentals services Company Details uses.
 *
 * Quota protection (REAL): fundamentals are loaded for at most [fundamentalsPerHour] new companies
 * per hour, in the background with bounded concurrency, and cached for [recordTtl]. Searches never
 * wait for them; responses report how much of the universe could be evaluated.
 */
class ScreenerService(
    private val universe: ScreenerUniverseSource,
    private val stocks: StockService,
    /** Same fundamentals Company Details shows (`CompanyFinancialService.getFundamentals(symbol, "annual")`). */
    private val fundamentalsOf: suspend (String) -> CompanyFundamentals,
    private val charts: PriceChartService,
    /** USD per one CAD, or null when unknown. */
    private val usdPerCad: suspend () -> Double?,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val fundamentalsPerHour: Int,
    /** MOCK builds everything from fixtures; REAL uses universe values for basic fields. */
    private val fullRecords: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val recordTtl: Long = 21_600_000L,
    private val pageCache: CompanyFinancialCache = CompanyFinancialCache(capacity = 128),
    /**
     * Latest-quarter revenue growth from the Earnings Results pipeline (the same calculation and
     * comparability rules as Earnings Results; one cached earnings-history request per company).
     * Null: the row shows "not available".
     */
    private val quarterlyRevenueGrowth: (suspend (String) -> MetricValue)? = null,
    /** Where comparison data comes from in this environment (shown with every comparison). */
    private val provenance: List<String> = emptyList(),
    /** The CAD→USD rate behind converted market caps, with its date and source (disclosed by Phase 2 explanations). */
    private val usdPerCadQuote: (suspend () -> FxConversion?)? = null
) {
    private val log = LoggerFactory.getLogger("StockSteps.Screener")
    private val lock = Mutex()
    private data class Cached(val record: CompanyRecord, val at: Long)
    private val records = HashMap<String, Cached>()
    private val fundamentals = HashMap<String, Pair<CompanyFundamentals, Long>>()
    private val loading = HashSet<String>()
    private val loadTimes = ArrayDeque<Long>()
    private val permits = Semaphore(4)

    private fun today(): String = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString()
    private fun now() = clock.millis()

    private suspend fun rate(currency: String?): Double? = when (currency) {
        null, "USD" -> 1.0
        "CAD" -> usdPerCad()
        else -> null
    }

    /** Builds (or reuses) one record; [loadFundamentals] decides whether provider fundamentals may be fetched now. */
    private suspend fun record(entry: UniverseEntry, loadFundamentals: Boolean): CompanyRecord {
        lock.withLock { records[entry.symbol]?.takeIf { now() - it.at < recordTtl }?.let { return it.record } }
        val fundamentals = cachedFundamentals(entry.symbol) ?: if (loadFundamentals) loadFundamentals(entry.symbol) else null
        val record = if (fullRecords) {
            val quote = runCatching { stocks.getStock(entry.symbol) }.getOrNull()
            val profile = runCatching { stocks.getProfile(entry.symbol) }.getOrNull()
            CompanyRecordBuilder.build(entry.symbol, quote, profile, fundamentals, rate(profile?.currency), today())
        } else {
            val profile = CompanyProfile(entry.symbol, entry.name, sector = entry.sector, industry = entry.industry, country = entry.country,
                exchange = entry.exchange, currency = if (entry.exchange == "TSX") "CAD" else "USD",
                logoUrl = "https://images.financialmodelingprep.com/symbol/${entry.symbol}.png", isEtf = entry.isEtf)
            val quote = StockQuote(entry.symbol, entry.name, entry.price, null, null, null, null, volume = entry.volume?.toLong(), marketCap = entry.marketCap?.toLong())
            CompanyRecordBuilder.build(entry.symbol, quote, profile, fundamentals, rate(profile.currency), today())
        }.copy(hasFundamentals = fundamentals != null)
        if (fundamentals != null || fullRecords) lock.withLock { records[entry.symbol] = Cached(record, now()) }
        return record
    }

    private suspend fun cachedFundamentals(symbol: String) = lock.withLock { fundamentals[symbol]?.takeIf { now() - it.second < recordTtl }?.first }

    private suspend fun loadFundamentals(symbol: String): CompanyFundamentals? = permits.withPermit {
        try {
            fundamentalsOf(symbol).also { lock.withLock { fundamentals[symbol] = it to now() } }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Screener fundamentals unavailable for {}", symbol)
            null
        }
    }

    /** REAL: schedules background fundamentals loads within the hourly budget (never blocks a search). */
    private suspend fun warm(entries: List<UniverseEntry>) {
        if (fullRecords || fundamentalsPerHour <= 0) return
        val chosen = lock.withLock {
            while (loadTimes.isNotEmpty() && now() - loadTimes.first() > 3_600_000L) loadTimes.removeFirst()
            val room = fundamentalsPerHour - loadTimes.size
            entries.filter { fundamentals[it.symbol] == null && it.symbol !in loading }.take(room.coerceAtLeast(0)).onEach {
                loading += it.symbol; loadTimes.addLast(now())
            }
        }
        for (entry in chosen) scope.launch {
            try { loadFundamentals(entry.symbol); lock.withLock { records.remove(entry.symbol) } }
            finally { lock.withLock { loading -= entry.symbol } }
        }
    }

    private suspend fun snapshot(): Pair<UniverseDefinition, List<CompanyRecord>> = coroutineScope {
        val definition = try { universe.universe() } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            throw ScreenerRequestException(503, "SCREENER_UNAVAILABLE", "The screening universe couldn't be loaded. Try again shortly.")
        }
        val built = definition.entries.map { entry -> async { record(entry, loadFundamentals = fullRecords) } }.awaitAll()
        warm(definition.entries)
        definition to built
    }

    private fun universeInfo(definition: UniverseDefinition, records: List<CompanyRecord>, needsFundamentals: Boolean): UniverseInfo {
        val evaluated = if (needsFundamentals) records.count { it.hasFundamentals } else records.size
        return UniverseInfo(definition.description, records.size, evaluated, evaluated == records.size)
    }

    suspend fun catalog(): ScreenerCatalog {
        val (definition, built) = snapshot()
        fun values(get: (CompanyRecord) -> String?) = built.mapNotNull(get).distinct().sorted()
        return ScreenerCatalog(
            ScreenerDefinitions.presets, ScreenerDefinitions.metrics,
            mapOf(ChoiceField.COUNTRY to values { it.country }, ChoiceField.EXCHANGE to values { it.exchange },
                ChoiceField.SECTOR to values { it.sector }, ChoiceField.INDUSTRY to values { it.industry },
                ChoiceField.SECURITY_TYPE to values { it.securityType }),
            universeInfo(definition, built, needsFundamentals = true), clock.instant().toString()
        )
    }

    suspend fun search(query: ScreenerQuery): ScreenerPage {
        try { ScreenerEngine.validate(query) } catch (cause: ScreenerValidationException) { throw ScreenerRequestException(400, "INVALID_SCREEN", cause.message ?: "Invalid filters.") }
        val (definition, built) = snapshot()
        val marketOnly = setOf("marketCap", "price", "volume", "yearRangePosition")
        val needsFundamentals = query.ranges.any { it.metric !in marketOnly } ||
            query.sort.field == SortField.METRIC && query.sort.metric !in marketOnly
        val candidates = if (needsFundamentals) built.filter { it.hasFundamentals } else built
        // The full sorted match list is cached per query so later pages are consistent and cheap.
        val key = "${ScreenerEngine.fingerprint(query)}:${candidates.size}:${today()}"
        val evaluation = pageCache.getOrLoad(key, 300_000L) {
            try { ScreenerEngine.evaluate(candidates, query.copy(cursor = null)) } catch (cause: ScreenerValidationException) {
                throw ScreenerRequestException(400, "INVALID_SCREEN", cause.message ?: "Invalid filters.")
            }
        }
        val (rows, next) = try { ScreenerEngine.page(evaluation.matches, query) } catch (cause: ScreenerValidationException) {
            throw ScreenerRequestException(400, "INVALID_CURSOR", cause.message ?: "Invalid cursor.")
        }
        // Fresh quotes only for the rows on this page (bounded), so daily change is current.
        val enriched = coroutineScope {
            rows.map { row -> async {
                val quote = runCatching { stocks.getStock(row.company.symbol) }.getOrNull()
                if (quote?.price == null) row else row.copy(company = row.company.copy(price = quote.price, changePercent = quote.changePercent))
            } }.awaitAll()
        }
        val info = universeInfo(definition, built, needsFundamentals)
        val warnings = buildList {
            if (!info.complete) add("Financial filters were applied to ${info.evaluated} of ${info.size} companies whose fundamentals are loaded; the rest aren't included yet.")
            if (enriched.any { it.company.stale }) add("Some companies' latest financial statements are more than 18 months old; they're labelled.")
            if (built.map { it.currency }.distinct().size > 1) add("Prices are in each listing's currency. Market cap is converted to USD so companies are comparable.")
        }
        return ScreenerPage(enriched, evaluation.matches.size, next, info, query, evaluation.excludedForMissingData,
            ScreenerDefinitions.displayMetricsFor(query), warnings, clock.instant().toString(), sampleData)
    }

    // ---------- Comparison ----------

    private fun symbols(raw: String?): List<String> {
        val list = raw.orEmpty().split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (list.size !in 2..MAX_COMPARED_COMPANIES) throw ScreenerRequestException(400, "INVALID_COMPARISON", "Compare between 2 and $MAX_COMPARED_COMPANIES companies.")
        if (list.distinct().size != list.size) throw ScreenerRequestException(400, "INVALID_COMPARISON", "Each company can be compared once.")
        if (list.any { !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(it) }) throw ScreenerRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
        return list
    }

    suspend fun compare(rawSymbols: String?): ComparisonResponse = coroutineScope {
        val list = symbols(rawSymbols)
        val companies = list.map { symbol -> async {
            try {
                val quote = async { runCatching { stocks.getStock(symbol) }.getOrNull() }
                val profile = async { runCatching { stocks.getProfile(symbol) }.getOrNull() }
                val quarter = async { quarterGrowth(symbol) }
                val data = cachedFundamentals(symbol) ?: loadFundamentals(symbol)
                val p = profile.await()
                if (p == null && quote.await() == null) ComparedCompany(symbol = symbol, error = "Company data isn't available right now.")
                else {
                    val record = CompanyRecordBuilder.build(symbol, quote.await(), p, data, rate(p?.currency), today())
                    ComparedCompany(record.copy(hasFundamentals = data != null, metrics = record.metrics + ("quarterRevenueGrowth" to quarter.await())),
                        symbol, if (data == null) "Financial data isn't available right now; only price information is shown." else null,
                        CompanyRecordBuilder.annualFigures(data))
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                ComparedCompany(symbol = symbol, error = "Company data isn't available right now.")
            }
        } }.awaitAll()
        val loaded = companies.mapNotNull { it.record }
        val currencies = companies.flatMap { c -> c.annual.mapNotNull { it.currency } + listOfNotNull(c.record?.currency) }.distinct()
        val notes = buildList {
            if (currencies.size > 1) add("Amounts are in each company's own currency (${currencies.joinToString()}) and aren't converted, except the approximate USD market cap shown for comparison. Percentages and ratios can be compared directly.")
            val fiscalEnds = loaded.mapNotNull { it.metrics["revenueGrowth"]?.basis?.date?.drop(5)?.take(2) }.distinct()
            if (fiscalEnds.size > 1) add("Fiscal years end in different months, so annual figures cover different periods.")
            add("Trailing (TTM) and estimated forward P/E are shown separately and never mixed.")
            if (loaded.any { it.stale }) add("Some financial statements are more than 18 months old.")
            loaded.mapNotNull { r -> r.fundamentalsAsOf?.take(10)?.let { "${r.symbol} $it" } }.takeIf { it.isNotEmpty() }
                ?.let { add("Financial statements retrieved: ${it.joinToString(", ")}.") }
            addAll(provenance)
        }
        val fx = if (loaded.any { it.currency == "CAD" }) listOfNotNull(try { usdPerCadQuote?.invoke() } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            null
        }) else emptyList()
        ComparisonResponse(companies, ScreenerDefinitions.metrics, ComparisonEngine.observations(loaded), notes, clock.instant().toString(), sampleData, fx)
    }

    private suspend fun quarterGrowth(symbol: String): MetricValue = try {
        quarterlyRevenueGrowth?.invoke(symbol) ?: MetricValue(note = "Quarterly results aren't available in this environment.")
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        MetricValue(availability = FinancialAvailability.TEMPORARILY_UNAVAILABLE, note = "Quarterly results aren't available right now.")
    }

    suspend fun performance(rawSymbols: String?, rawPeriod: String?): PerformanceComparison = coroutineScope {
        val list = symbols(rawSymbols)
        val period = PerformancePeriod.parse(rawPeriod ?: "1Y") ?: throw ScreenerRequestException(400, "INVALID_PERIOD", "Choose 1M, 3M, 1Y, 3Y or 5Y.")
        val end = LocalDate.parse(today())
        val start = end.minusMonths(period.months.toLong())
        val closes = list.map { symbol -> async {
            symbol to try { charts.getDailyCloses(symbol).map { it.time.take(10) to it.close } } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                emptyList()
            }
        } }.awaitAll().toMap()
        val currencies = list.associateWith { symbol -> runCatching { stocks.getProfile(symbol)?.currency }.getOrNull() }
        val result = PerformanceNormalizer.compute(closes, start.toString(), end.toString(), currencies = currencies)
        PerformanceComparison(period, ReturnKind.PRICE_RETURN, result.dates, result.series, listOfNotNull(
            "Price change only: every line starts at 100 on ${result.baseDate ?: start}. Dividends aren't included, so this isn't total return.",
            "This shows how share prices moved, not how the businesses performed, and past moves don't predict future returns.",
            "Prices are split-adjusted by the data provider, so a split doesn't appear as a drop.",
            "When one market is closed (a holiday), that company's last close is carried for up to ${PerformanceNormalizer.CARRY_DAYS} days; longer gaps are left empty.",
            if (currencies.values.filterNotNull().distinct().size > 1) "Each line is a change in its own trading currency; exchange-rate moves aren't included." else null,
            if (sampleData) "Sample price history for development, not real prices." else null
        ), result.baseDate)
    }
}

/** Simple per-client token bucket for public screener/compare routes. */
class RequestRateLimiter(private val perMinute: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val windows = LinkedHashMap<String, ArrayDeque<Long>>()
    fun allow(client: String): Boolean = synchronized(lock) {
        val now = clock()
        val window = windows.getOrPut(client) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() >= 60_000) window.removeFirst()
        if (windows.size > 10_000) windows.remove(windows.keys.first())
        if (window.size >= perMinute) false else { window.addLast(now); true }
    }
}

/**
 * Latest-quarter revenue growth from a published Earnings Results report: the same year-over-year
 * calculation and comparability checks (same fiscal quarter, currency, fiscal calendar, positive base)
 * that Earnings Results shows. The note names the quarters compared ("Q3 FY2026 vs Q3 FY2025").
 */
fun quarterRevenueGrowth(results: org.example.stocksteps.earnings.EarningsResultsResponse): MetricValue {
    val report = results.report
    val growth = results.insights.yearOverYear
    val basis = org.example.stocksteps.model.FinancialBasis("quarter", report.fiscalPeriodEnd ?: report.reportDate, report.fiscalYear, report.reportingCurrency)
    val percent = growth.percent?.toDoubleOrNull()?.takeIf { it.isFinite() }
    return if (percent != null) MetricValue(percent, FinancialAvailability.AVAILABLE, basis, "${report.period} vs ${growth.priorLabel ?: "a year earlier"}")
    else MetricValue(null, when {
        growth.reason?.contains("currency", ignoreCase = true) == true || growth.reason?.contains("calendar", ignoreCase = true) == true -> FinancialAvailability.PERIOD_MISMATCH
        growth.reason?.contains("zero", ignoreCase = true) == true || growth.reason?.contains("negative", ignoreCase = true) == true -> FinancialAvailability.UNRELIABLE_COMPARISON
        else -> FinancialAvailability.INSUFFICIENT_HISTORY
    }, basis, "${report.period}: ${growth.reason ?: "the same quarter a year earlier isn't available."}")
}
