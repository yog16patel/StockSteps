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
 * A failed company isn't retried for [retryAfterMillis].
 * Phase 9B daily cap: with [perDay] set (env `SCREENER_ENRICHMENT_PER_DAY`, default [DEFAULT_PER_DAY]; 0 = no background enrichment),
 * enrichment makes at most that many **upstream provider requests** per UTC day on this instance. Each company reserves its worst case
 * (one request per due part: profile, daily series) before it starts, and the unused part is returned afterwards from the exact count of
 * upstream calls it caused (cache hits and joined loads cost nothing), so concurrent passes can't overshoot. Never-loaded companies go
 * first, then missing closes, then profile refreshes. The engine keeps unenriched companies out of value filters (reported as
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
    private val perDay: Int? = DEFAULT_PER_DAY,
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
    private var budgetDay = ""
    private var usedToday = 0
    /** Upstream requests charged to enrichment in the current UTC day (reserved + actual), for tests and diagnostics. */
    val usedTodayForTests: Int get() = usedToday
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
            val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            if (day != budgetDay) { budgetDay = day; usedToday = 0 }
            val hourRoom = (perHour - attempts.size).coerceAtLeast(0)
            // Parts in priority order: never-loaded companies (both parts), then due closes, then due profile refreshes; oldest attempt
            // first within each group. A company's parts chosen in one pass are merged (one attempt, one hourly slot).
            val parts = file.companies.mapNotNull { c -> c.symbol.takeIf { it !in inFlight }?.let { due(it, enriched[it], now) } }.flatMap { d ->
                if (enriched[d.symbol] == null) listOf(0 to d)
                else listOfNotNull(if (d.close) 1 to d.copy(profile = false) else null, if (d.profile) 2 to d.copy(close = false) else null)
            }.sortedWith(compareBy({ it.first }, { enriched[it.second.symbol]?.attemptAt ?: Long.MIN_VALUE }))
            // Closes come first for the whole day: while a close is still to be published in this budget day, profile refreshes may use
            // only what the companies still waiting for that close won't need.
            val closeReserve = if (perDay == null) 0 else eod.nextPublication(EodSessions.Market.US, clock.instant()).let { next ->
                if (next.atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() != day) 0
                else { val session = eod.expectedSession(EodSessions.Market.US, next); file.companies.count { c -> enriched[c.symbol]?.priceDate?.let { it < session } ?: true } }
            }
            val picked = LinkedHashMap<String, Due>()
            var reserved = 0; var hourFull = false; var dayFull = false; var closesWaiting = false
            for ((stage, d) in parts) {
                // Profile refreshes wait while any new company or close couldn't be scheduled.
                if (stage == 2 && closesWaiting && perDay != null) { dayFull = true; continue }
                val existing = picked[d.symbol]
                if (existing == null && picked.size >= hourRoom) { hourFull = true; if (stage < 2) closesWaiting = true; continue }
                val keep = if (stage == 2) closeReserve else 0
                if (perDay != null && usedToday + reserved + d.parts + keep > perDay) { dayFull = true; if (stage < 2) closesWaiting = true; continue }
                picked[d.symbol] = existing?.let { Due(d.symbol, it.profile || d.profile, it.close || d.close) } ?: d
                reserved += d.parts
            }
            val chosen = picked.values.toList()
            if (hourFull) meter.event("discover.enrich.budgetReached")
            if (dayFull) meter.event("discover.enrich.dailyBudgetReached")
            chosen.forEach { inFlight += it.symbol; attempts.addLast(now) }
            if (perDay != null) usedToday += reserved
            entries to chosen
        }
        val day = lock.withLock { budgetDay }
        if (due.isNotEmpty()) pending = scope.launch(ProviderFeature("discover-enrichment") + ProviderPriorityElement(ProviderPriority.LOW)) {
            due.map { d -> launch {
                val cost = org.example.stocksteps.security.RequestCost()
                permits.withPermit { try { withContext(cost) { enrich(d) } } finally { lock.withLock {
                    inFlight -= d.symbol
                    // Return the unused part of the reservation (same budget day only); never below the real upstream count.
                    if (perDay != null && budgetDay == day) usedToday += cost.data.get() - d.parts
                } } }
            } }.joinAll()
        }
        val loaded = entries.count { it.price != null }
        meter.gauge("discover.coverage.priced", loaded.toLong()); meter.gauge("discover.coverage.size", entries.size.toLong())
        return UniverseDefinition(file.description, entries)
    }

    /** What one company needs now: its profile, its latest close, or both ([parts] = the most upstream requests it can cause). */
    private data class Due(val symbol: String, val profile: Boolean, val close: Boolean) { val parts: Int get() = (if (profile) 1 else 0) + (if (close) 1 else 0) }

    private fun due(symbol: String, e: Enriched?, now: Long): Due? {
        if (e == null) return Due(symbol, profile = true, close = true)
        if (now - e.attemptAt < if (e.failed) retryAfterMillis else 60_000L) return null
        val market = eod.market(symbol) ?: return null
        val expected = eod.expectedSession(market, clock.instant())
        val priceBehind = e.priceDate == null || e.priceDate < expected
        val profileOld = e.profileAt == null || now - e.profileAt >= profileRefreshMillis
        // A missing expected close is re-checked at most every 30 minutes (the close cache's own late-retry interval).
        val close = priceBehind && (now - e.attemptAt >= 1_800_000L || profileOld)
        return Due(symbol, profileOld, close).takeIf { it.parts > 0 }
    }

    private suspend fun enrich(d: Due) {
        val symbol = d.symbol
        val now = clock.millis()
        val previous = lock.withLock { enriched[symbol] }
        // Phase 9B: only the parts that are due are requested (a close refresh doesn't re-read the profile, and vice versa).
        if (previous != null && !d.close) {
            val p = runCatching { profile(symbol) }.getOrNull()
            val next = previous.copy(
                name = p?.companyName?.takeIf { it.isNotBlank() } ?: previous.name, exchange = p?.exchange?.takeIf { it.isNotBlank() } ?: previous.exchange,
                sector = p?.sector?.takeIf { it.isNotBlank() } ?: previous.sector, industry = p?.industry?.takeIf { it.isNotBlank() } ?: previous.industry,
                country = p?.country?.takeIf { it.isNotBlank() } ?: previous.country, marketCap = if (p != null) p.marketCap else previous.marketCap,
                profileAt = if (p != null) now else previous.profileAt, attemptAt = now, failed = p == null && previous.price == null)
            lock.withLock { enriched[symbol] = next }
            meter.event(if (p == null) "discover.enrich.failed" else "discover.enrich.ok")
            return
        }
        val p = if (previous == null || d.profile) runCatching { profile(symbol) }.getOrNull() else null
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
            failed = close == null && (p == null || !d.profile)
        )
        lock.withLock { enriched[symbol] = next }
        meter.event(if (next.failed) "discover.enrich.failed" else "discover.enrich.ok")
    }

    companion object {
        const val DEFAULT_PER_HOUR = 60
        /** Phase 9B (the Phase 7E.1 design): 100 upstream requests a day by default. A full refresh needs ≈ 310 (155 closes + 155 profiles). */
        const val DEFAULT_PER_DAY = 100
    }
}
