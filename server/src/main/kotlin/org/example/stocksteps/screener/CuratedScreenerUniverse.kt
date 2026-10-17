package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.service.EodChange
import org.example.stocksteps.service.EodPrice
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.ProviderFeature
import org.example.stocksteps.service.ProviderPriority
import org.example.stocksteps.service.ProviderPriorityElement
import org.example.stocksteps.service.ProviderUsageMeter
import java.time.Clock
import java.time.LocalDate

/** The repository's curated Discover list (`screener/curated-us-universe.json`): metadata only, never prices or financials. */
@Serializable
data class CuratedUniverseFile(val version: String, val description: String, val provenance: String, val companies: List<CuratedCompany>) {
    @Serializable data class CuratedCompany(val symbol: String, val name: String, val exchange: String? = null, val sector: String? = null)

    companion object {
        const val RESOURCE = "screener/curated-us-universe.json"
        fun load(resource: String = RESOURCE): CuratedUniverseFile {
            val text = requireNotNull(CuratedUniverseFile::class.java.classLoader.getResource(resource)) { "Missing curated universe $resource" }.readText()
            return Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), text)
        }
    }
}

/**
 * Phase 7E (REAL Discover without FMP's restricted `company-screener`): the curated list of about 150 US-listed companies, enriched
 * per company from endpoints this plan serves — the company profile (provider name, sector, industry, exchange, market cap; the shared
 * 24 h profile cache) and the latest valid official close with its change from the previous trading day (the shared session-aware
 * daily-close cache, Phase 7B). No quote, no screener, no batch quote.
 *
 * [universe] never waits for the provider: it returns the list with whatever is enriched now (missing values stay null) and starts a
 * background pass for companies that are due — never loaded, a newer close expected, or a profile older than [profileRefreshMillis] —
 * at most [perHour] companies an hour, [concurrency] at a time, LOW provider priority (never the interactive share of a budget).
 * A failed company isn't retried for [retryAfterMillis]. The engine keeps unenriched companies out of value filters (reported as
 * excluded for missing data) and sorts them last; coverage is reported with every response.
 */
class CuratedScreenerUniverse(
    private val profile: suspend (String) -> CompanyProfile?,
    private val closes: suspend (String) -> List<PricePoint>,
    private val eod: EodSessions,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val perHour: Int = DEFAULT_PER_HOUR,
    private val concurrency: Int = 4,
    private val profileRefreshMillis: Long = 86_400_000L,
    private val retryAfterMillis: Long = 1_800_000L,
    private val file: CuratedUniverseFile = CuratedUniverseFile.load(),
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) : ScreenerUniverseSource {
    /** What the provider returned for one company; null fields are unavailable. */
    data class Enriched(
        val name: String? = null, val exchange: String? = null, val sector: String? = null, val industry: String? = null, val country: String? = null,
        val marketCap: Double? = null, val price: Double? = null, val priceDate: LocalDate? = null, val changePercent: Double? = null,
        val profileAt: Long? = null, val attemptAt: Long = 0, val failed: Boolean = false
    )

    private val lock = Mutex()
    private val enriched = HashMap<String, Enriched>()
    private val inFlight = HashSet<String>()
    private val attempts = ArrayDeque<Long>()
    private val permits = Semaphore(concurrency)
    /** The latest background pass (tests join it). */
    @Volatile var pending: Job? = null; private set

    val version: String get() = file.version
    val size: Int get() = file.companies.size

    override suspend fun universe(): UniverseDefinition {
        val now = clock.millis()
        val (entries, due) = lock.withLock {
            val entries = file.companies.map { c ->
                val e = enriched[c.symbol]
                UniverseEntry(c.symbol, e?.name ?: c.name, e?.exchange ?: c.exchange, e?.country ?: "US", e?.sector ?: c.sector, e?.industry,
                    e?.price, e?.marketCap, null, false, priceDate = e?.priceDate?.toString(), changePercent = e?.changePercent)
            }
            while (attempts.isNotEmpty() && now - attempts.first() >= 3_600_000L) attempts.removeFirst()
            val room = (perHour - attempts.size).coerceAtLeast(0)
            val due = file.companies.map { it.symbol }.filter { it !in inFlight && isDue(it, enriched[it], now) }
            if (due.size > room) meter.event("discover.enrich.budgetReached")
            entries to due.sortedBy { enriched[it]?.attemptAt ?: Long.MIN_VALUE }.take(room).onEach { inFlight += it; attempts.addLast(now) }
        }
        if (due.isNotEmpty()) pending = scope.launch(ProviderFeature("discover-enrichment") + ProviderPriorityElement(ProviderPriority.LOW)) {
            due.map { symbol -> launch { permits.withPermit { try { enrich(symbol) } finally { lock.withLock { inFlight -= symbol } } } } }.joinAll()
        }
        val loaded = entries.count { it.price != null }
        meter.gauge("discover.coverage.priced", loaded.toLong()); meter.gauge("discover.coverage.size", entries.size.toLong())
        return UniverseDefinition(file.description, entries)
    }

    private fun isDue(symbol: String, e: Enriched?, now: Long): Boolean {
        if (e == null) return true
        if (now - e.attemptAt < if (e.failed) retryAfterMillis else 60_000L) return false
        val market = eod.market(symbol) ?: return false
        val expected = eod.expectedSession(market, clock.instant())
        val priceBehind = e.priceDate == null || e.priceDate < expected
        val profileOld = e.profileAt == null || now - e.profileAt >= profileRefreshMillis
        // A missing expected close is re-checked at most every 30 minutes (the close cache's own late-retry interval).
        return profileOld || (priceBehind && now - e.attemptAt >= 1_800_000L)
    }

    private suspend fun enrich(symbol: String) {
        val now = clock.millis()
        val previous = lock.withLock { enriched[symbol] }
        val p = runCatching { profile(symbol) }.getOrNull()
        val series = runCatching { closes(symbol) }.getOrNull()
        val at = clock.instant()
        val close = series?.let { EodPrice.latest(symbol, it, eod, at) }?.takeIf { it.sessionsBehind <= EodPrice.VALUATION_MAX_SESSIONS }
        val change = series?.let { EodChange.of(symbol, it, eod, at) }?.takeIf { close != null && it.closeDate == close.date }
        val next = Enriched(
            name = p?.companyName?.takeIf { it.isNotBlank() } ?: previous?.name,
            exchange = p?.exchange?.takeIf { it.isNotBlank() } ?: previous?.exchange,
            sector = p?.sector?.takeIf { it.isNotBlank() } ?: previous?.sector,
            industry = p?.industry?.takeIf { it.isNotBlank() } ?: previous?.industry,
            country = p?.country?.takeIf { it.isNotBlank() } ?: previous?.country,
            marketCap = p?.marketCap ?: previous?.marketCap.takeIf { p == null },
            price = close?.close, priceDate = close?.date, changePercent = change?.changePercent,
            profileAt = if (p != null) now else previous?.profileAt, attemptAt = now,
            failed = p == null && close == null
        )
        lock.withLock { enriched[symbol] = next }
        meter.event(if (next.failed) "discover.enrich.failed" else "discover.enrich.ok")
    }

    companion object { const val DEFAULT_PER_HOUR = 60 }
}
