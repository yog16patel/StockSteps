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
import org.example.stocksteps.service.ProviderFeature
import org.example.stocksteps.service.ProviderUsageMeter
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
 * REAL: FMP `company-screener` (one request per exchange, cached 24 h) defines the universe: actively trading
 * stocks on the configured exchanges above a minimum market cap, at most [limit] per exchange (default 50:
 * NASDAQ + NYSE + TSX = 150, the Phase 3 owner decision).
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
        // At most [limit] rows per exchange, in the provider's order (its ordering isn't documented as "largest first",
        // so it isn't described that way). Symbols stay exchange-qualified (TD.TO ≠ TD).
        val rows = exchanges.flatMap { exchange ->
            client.apiCall<List<Row>>("https://financialmodelingprep.com/stable/company-screener", apiKey) {
                parameter("exchange", exchange)
                parameter("marketCapMoreThan", minMarketCap)
                parameter("isEtf", false); parameter("isFund", false); parameter("isActivelyTrading", true)
                parameter("limit", limit)
            }.filter { it.isEtf != true && it.isFund != true }.take(limit)
        }
        UniverseDefinition(
            "Up to $limit actively traded stocks per exchange (${exchanges.joinToString()}) with market cap over $${minMarketCap / 1_000_000_000}B, as selected by Financial Modeling Prep's company screener. Not every listed company is included.",
            rows.distinctBy { it.symbol }.map {
                UniverseEntry(it.symbol, it.companyName, it.exchangeShortName ?: it.exchange, it.country, it.sector, it.industry, it.price, it.marketCap, it.volume, false)
            }
        )
    }
}

class ScreenerRequestException(val status: Int, val code: String, message: String) : Exception(message)

/** 2–[MAX_COMPARED_COMPANIES] distinct, exchange-qualified symbols (shared by every comparison route). */
fun parseComparisonSymbols(raw: String?): List<String> {
    val list = raw.orEmpty().split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    if (list.size !in 2..MAX_COMPARED_COMPANIES) throw ScreenerRequestException(400, "INVALID_COMPARISON", "Compare between 2 and $MAX_COMPARED_COMPANIES companies.")
    if (list.distinct().size != list.size) throw ScreenerRequestException(400, "INVALID_COMPARISON", "Each company can be compared once.")
    if (list.any { !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(it) }) throw ScreenerRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
    return list
}

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
    private val usdPerCadQuote: (suspend () -> FxConversion?)? = null,
    /**
     * Phase 3B-1: the lighter screener dataset set (symbol, listing currency) — everything screener metrics use,
     * without historical annual ratios or a profile request. Null: [fundamentalsOf] (MOCK and tests).
     */
    private val screenerFundamentalsOf: (suspend (String, String?) -> CompanyFundamentals)? = null,
    /** A symbol whose background load failed isn't tried again for this long (no retry storms; the hourly budget still applies). */
    private val retryAfterFailure: Long = 1_800_000L,
    /** Fundamentals with a temporarily unavailable dataset may be reloaded after this long instead of [recordTtl]. */
    private val partialRefreshAfter: Long = 3_600_000L,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    private val log = LoggerFactory.getLogger("StockSteps.Screener")
    private val lock = Mutex()
    /** A built record; [at] is when its fundamentals were loaded (so it expires with them), or when it was built. */
    private data class Cached(val record: CompanyRecord, val at: Long)
    /** Fundamentals held for one company: loaded at [at], usable until `at + recordTtl`, eligible for a background reload from [refreshAt]. */
    private data class Held(val data: CompanyFundamentals, val at: Long, val refreshAt: Long)
    private val records = HashMap<String, Cached>()
    private val fundamentals = HashMap<String, Held>()
    private val failedAt = HashMap<String, Long>()
    private val loading = HashSet<String>()
    private val loadTimes = ArrayDeque<Long>()
    private val permits = Semaphore(4)
    private companion object {
        /** Upper bound on held fundamentals (universe plus companies opened in Comparison). */
        const val MAX_HELD = 2_000
    }

    private fun today(): String = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString()
    private fun now() = clock.millis()

    private suspend fun rate(currency: String?): Double? = when (currency) {
        null, "USD" -> 1.0
        "CAD" -> usdPerCad()
        else -> null
    }

    /** The listing's trading currency as the universe defines it (TSX → CAD; the US exchanges → USD). */
    private fun listingCurrency(entry: UniverseEntry) = if (entry.exchange == "TSX") "CAD" else "USD"

    /** Builds (or reuses) one record; [loadFundamentals] decides whether provider fundamentals may be fetched now. */
    private suspend fun record(entry: UniverseEntry, loadFundamentals: Boolean): CompanyRecord {
        lock.withLock { records[entry.symbol]?.takeIf { now() - it.at < recordTtl }?.let { return it.record } }
        if (loadFundamentals && held(entry.symbol) == null) loadFundamentals(entry.symbol, listingCurrency(entry), screenerSet = true)
        val held = held(entry.symbol)
        val fundamentals = held?.data
        val record = if (fullRecords) {
            val quote = runCatching { stocks.getStock(entry.symbol) }.getOrNull()
            val profile = runCatching { stocks.getProfile(entry.symbol) }.getOrNull()
            CompanyRecordBuilder.build(entry.symbol, quote, profile, fundamentals, rate(profile?.currency), today())
        } else {
            val profile = CompanyProfile(entry.symbol, entry.name, sector = entry.sector, industry = entry.industry, country = entry.country,
                exchange = entry.exchange, currency = listingCurrency(entry),
                logoUrl = "https://images.financialmodelingprep.com/symbol/${entry.symbol}.png", isEtf = entry.isEtf)
            val quote = StockQuote(entry.symbol, entry.name, entry.price, null, null, null, null, volume = entry.volume?.toLong(), marketCap = entry.marketCap?.toLong())
            CompanyRecordBuilder.build(entry.symbol, quote, profile, fundamentals, rate(profile.currency), today())
        }.copy(hasFundamentals = fundamentals != null)
        // A record with fundamentals expires when they do; without them it isn't kept (REAL), so it's rebuilt once they load.
        if (held != null || fullRecords) lock.withLock { records[entry.symbol] = Cached(record, held?.at ?: now()) }
        return record
    }

    /** Fundamentals younger than [recordTtl]; expired ones are never used for screening (the company counts as not evaluated). */
    private suspend fun held(symbol: String): Held? = lock.withLock { fundamentals[symbol]?.takeIf { now() - it.at < recordTtl } }
    private suspend fun cachedFundamentals(symbol: String) = held(symbol)?.data

    /** At least one dataset arrived (fixtures without dataset metadata count as usable). */
    private fun usable(data: CompanyFundamentals) = data.datasets.isEmpty() || data.datasets.values.any { it == FinancialAvailability.AVAILABLE }

    /**
     * Loads fundamentals and records them for screening when usable. A load where every dataset failed is
     * returned to the caller (Comparison shows per-metric availability) but never held, so the company stays
     * "not evaluated" instead of matching nothing; it's retried after [retryAfterFailure].
     */
    private suspend fun loadFundamentals(symbol: String, listingCurrency: String? = null, screenerSet: Boolean = false): CompanyFundamentals? = permits.withPermit {
        try {
            val data = screenerFundamentalsOf?.takeIf { screenerSet }?.invoke(symbol, listingCurrency) ?: fundamentalsOf(symbol)
            val t = now()
            lock.withLock {
                if (usable(data)) {
                    val partial = data.datasets.values.any { it == FinancialAvailability.TEMPORARILY_UNAVAILABLE } ||
                        data.freshness == org.example.stocksteps.earnings.DataFreshness.STALE
                    fundamentals[symbol] = Held(data, t, t + if (partial) minOf(recordTtl, partialRefreshAfter) else recordTtl)
                    failedAt.remove(symbol)
                } else failedAt[symbol] = t
            }
            data
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Screener fundamentals unavailable for {}", symbol)
            lock.withLock { failedAt[symbol] = now() }
            null
        }
    }

    /** Must hold [lock]. Bounds the maps: long-expired fundamentals, expired records and old failure marks are dropped. */
    private fun prune(t: Long) {
        fundamentals.entries.removeAll { (symbol, held) -> symbol !in loading && t - held.at >= 2 * recordTtl }
        records.entries.removeAll { t - it.value.at >= recordTtl }
        failedAt.entries.removeAll { t - it.value >= retryAfterFailure }
        if (fundamentals.size > MAX_HELD) fundamentals.entries.sortedBy { it.value.at }.take(fundamentals.size - MAX_HELD)
            .map { it.key }.filter { it !in loading }.forEach(fundamentals::remove)
    }

    /**
     * REAL: schedules background fundamentals loads within the hourly budget (never blocks a search).
     * Due: never loaded, or held past its refresh time (expired after [recordTtl], or sooner when partial), and
     * not failed within [retryAfterFailure]. Never-loaded companies go first (universe order), then the oldest loads.
     * One load per symbol at a time ([loading]); the provider cache's single flight also merges overlapping callers.
     */
    private suspend fun warm(entries: List<UniverseEntry>) {
        if (fullRecords || fundamentalsPerHour <= 0) return
        val chosen = lock.withLock {
            val t = now()
            prune(t)
            while (loadTimes.isNotEmpty() && t - loadTimes.first() >= 3_600_000L) loadTimes.removeFirst()
            val room = (fundamentalsPerHour - loadTimes.size).coerceAtLeast(0)
            val due = entries.filter { e ->
                e.symbol !in loading && failedAt[e.symbol].let { it == null || t - it >= retryAfterFailure } &&
                    fundamentals[e.symbol].let { it == null || t >= it.refreshAt }
            }
            if (due.size > room) meter.event("screener.warm.budgetReached")
            due.sortedBy { fundamentals[it.symbol]?.at ?: Long.MIN_VALUE }.take(room).onEach {
                loading += it.symbol; loadTimes.addLast(t)
            }
        }
        // Phase 4C: warm-up is LOW priority — it never uses the interactive share of a provider budget and is deferred when it's low.
        for (entry in chosen) scope.launch(ProviderFeature("screener-warmup") + org.example.stocksteps.service.ProviderPriorityElement(org.example.stocksteps.service.ProviderPriority.LOW)) {
            try {
                meter.event(if (lock.withLock { entry.symbol in fundamentals }) "screener.warm.refresh" else "screener.warm.initial")
                loadFundamentals(entry.symbol, listingCurrency(entry), screenerSet = true)
                lock.withLock { if (entry.symbol in failedAt) meter.event("screener.warm.failed") else records.remove(entry.symbol) }
            } finally { lock.withLock { loading -= entry.symbol } }
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
        if (needsFundamentals) {   // Phase 4D: coverage gauges for the usage summaries
            meter.gauge("screener.coverage.evaluated", evaluated.toLong()); meter.gauge("screener.coverage.size", records.size.toLong())
        }
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

    private fun symbols(raw: String?): List<String> = parseComparisonSymbols(raw)

    suspend fun compare(rawSymbols: String?): ComparisonResponse = coroutineScope {
        val list = symbols(rawSymbols)
        val staleSymbols = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val companies = list.map { symbol -> async {
            try {
                val quote = async { runCatching { stocks.getStock(symbol) }.getOrNull() }
                val profile = async { runCatching { stocks.getProfile(symbol) }.getOrNull() }
                val quarter = async { quarterGrowth(symbol) }
                val p = profile.await()
                // Phase 4A: no profile and no quote → unknown or unavailable company: never load its fundamentals bundle.
                if (p == null && quote.await() == null) ComparedCompany(symbol = symbol, error = "Company data isn't available right now.")
                else {
                    val data = cachedFundamentals(symbol) ?: loadFundamentals(symbol)
                    if (data?.freshness == org.example.stocksteps.earnings.DataFreshness.STALE) staleSymbols += symbol
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
            list.filter { it in staleSymbols }.takeIf { it.isNotEmpty() }
                ?.let { add("${it.joinToString(", ")}: the data provider couldn't be reached, so some earlier financial data is shown (retrieved on the date above).") }
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

/**
 * Simple per-client sliding-minute limiter for route groups. [client] is a uid, a trusted client IP or (route tests only) the remote host;
 * null — an unverified anonymous caller (Phase 4A) — isn't limited here, because a shared key would throttle every guest together; those
 * callers are bounded by the admission plugin's anonymous pool and the provider budgets.
 */
class RequestRateLimiter(private val perMinute: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val windows = LinkedHashMap<String, ArrayDeque<Long>>()
    fun allow(client: String?): Boolean = if (client == null) true else synchronized(lock) {
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
