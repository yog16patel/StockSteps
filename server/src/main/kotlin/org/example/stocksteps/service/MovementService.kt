package org.example.stocksteps.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.buildJsonObject as obj
import org.example.stocksteps.model.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** The computed facts an optional narrator may word. Nothing else may appear in its text. */
data class MovementFacts(
    val symbol: String,
    val companyName: String,
    val period: String,
    val sessionLabel: String,
    val changePercent: String,
    val benchmarks: List<Pair<String, String>>,
    val events: List<Pair<String, EvidenceLabel>>,
    val noConfirmedCatalyst: Boolean
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("symbol", symbol); put("company", companyName); put("period", period); put("dates", sessionLabel)
        put("changePercent", changePercent)
        putJsonArray("benchmarks") { benchmarks.forEach { (name, pct) -> add(obj { put("name", name); put("changePercent", pct) }) } }
        putJsonArray("events") { events.forEach { (title, label) -> add(obj { put("title", title); put("label", label.name) }) } }
        put("noConfirmedCatalyst", noConfirmedCatalyst)
    }
}

/** Optional AI wording of [MovementFacts]; its output is validated and otherwise replaced by a template. */
fun interface MovementNarrator {
    suspend fun narrate(facts: MovementFacts): String
}

object MovementNarrativeValidator {
    private val forbidden = Regex("(?i)\\b(because|due to|driven by|caused|causing|as a result|thanks to|led to|sparked|triggered|you should|buy|sell|will (rise|fall|go up|go down)|expect(ed)? to (rise|fall))\\b|https?://|www\\.")
    private val number = Regex("\\d+(?:[.,]\\d+)*")

    fun validate(text: String?, facts: MovementFacts): String? {
        val summary = text?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (summary.length !in 40..600 || forbidden.containsMatchIn(summary)) return null
        val allowed = number.findAll(facts.toJson().toString()).map { it.value.trimEnd('.') }.toSet()
        if (number.findAll(summary).any { it.value.trimEnd('.') !in allowed }) return null
        return summary
    }
}

/**
 * "Why did it move?" for 1D, 1W and 1M. Prices, returns, dates and benchmark comparisons are
 * computed here; news is limited to articles published inside the move's window (plus 12 hours
 * before it), never after it. Timing alone is never presented as cause.
 */
class MovementService(
    private val stocks: StockService,
    private val charts: PriceChartService,
    private val news: NewsService,
    private val narrator: MovementNarrator? = null,
    private val version: String = "movement-template-v1",
    private val narratorTimeoutMillis: Long = 8_000,
    /** Cost cap for AI narration on this public route (per instance, rolling hour); the deterministic summary is used beyond it. */
    private val narrationsPerHour: Int = 300,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512, name = "movement"),
    private val now: () -> Instant = Instant::now
) {
    private class Window(
        val startDate: LocalDate, val endDate: LocalDate,
        val startPrice: Double, val endPrice: Double,
        val startInstant: Instant, val endInstant: Instant,
        val sessionLabel: String, val phrase: String,
        val limitations: List<String>
    )
    private class Outcome(val value: MovementExplanation?)

    suspend fun explain(symbol: String, period: MovementPeriod): MovementExplanation? =
        cache.getOrLoad("movement|$symbol|${period.label}|$version", if (period == MovementPeriod.ONE_DAY) DAY_TTL else LONG_TTL,
            resultTtl = { if (it.value == null) FAILURE_TTL else if (period == MovementPeriod.ONE_DAY) DAY_TTL else LONG_TTL }) {
            Outcome(build(symbol, period))
        }.value

    /** The Company Details preview: today's movement in the older card shape. */
    suspend fun preview(symbol: String): WhyMoving? = explain(symbol, MovementPeriod.ONE_DAY)?.let { movement ->
        WhyMoving(
            symbol = movement.symbol,
            summary = movement.summary,
            whyItMatters = movement.whatWeCannotConfirm.firstOrNull(),
            changePercent = movement.changePercent,
            sources = movement.events.take(3).map { WhyMovingSource(it.title, it.url, it.publisher) },
            generatedAt = movement.generatedAt
        )
    }

    private suspend fun build(symbol: String, period: MovementPeriod): MovementExplanation? {
        val quote = safely { cached("quote|$symbol", QUOTE_TTL) { stocks.getStock(symbol) } }
        val profile = safely { stocks.getProfile(symbol) }
        val closes = if (period == MovementPeriod.ONE_DAY) emptyList() else dailyCloses(symbol)
        val window = when (period) {
            MovementPeriod.ONE_DAY -> dayWindow(quote ?: return null, dailyCloses(symbol))
            else -> rangeWindow(closes, period)
        } ?: return null
        val name = profile?.companyName ?: quote?.companyName ?: symbol
        val currency = profile?.currency ?: "USD"
        val changePercent = (window.endPrice / window.startPrice - 1) * 100

        val benchmarkSymbols = listOfNotNull("SPY", "QQQ", SECTOR_ETFS[profile?.sector]).filter { it != symbol }
        val benchmarks = coroutineScope {
            benchmarkSymbols.map { benchmark -> async { benchmarkMove(benchmark, period, window) } }.awaitAll()
        }.filterNotNull()
        val missingBenchmarks = benchmarks.size < benchmarkSymbols.size

        val events = events(symbol, name, window)
        val noConfirmedCatalyst = events.none { it.label == EvidenceLabel.CONFIRMED_EVENT }
        val market = benchmarks.firstOrNull { it.symbol == "SPY" }
        val inLine = market != null && abs(changePercent - market.changePercent) < IN_LINE_POINTS

        val whatWeKnow = buildList {
            add("${name} ${verb(changePercent)} ${pct(changePercent)} ${window.phrase}, from ${money(window.startPrice, currency)} to ${money(window.endPrice, currency)}.")
            if (benchmarks.isNotEmpty()) add("Over the same dates: " + benchmarks.joinToString(", ") { "${it.name} ${signed(it.changePercent)}" } + ".")
            if (market != null) {
                val gap = changePercent - market.changePercent
                add(if (inLine) "That's roughly in line with the broad market (S&P 500)."
                    else "That's ${String.format(Locale.US, "%.2f", abs(gap))} percentage points ${if (gap > 0) "better" else "worse"} than the S&P 500 over the same dates.")
            }
            val confirmed = events.filter { it.label == EvidenceLabel.CONFIRMED_EVENT }
            if (confirmed.isNotEmpty()) add("Company news reported in this window included “${confirmed.first().title}”.")
        }
        val whatWeCannotConfirm = buildList {
            add("Timing alone can't prove that any news story caused this price change.")
            if (noConfirmedCatalyst) add("We didn't find a confirmed company-specific event in the news for this period.")
            if (inLine) add("The move may reflect market-wide factors rather than company news, but we can't confirm that.")
            add("Large trades, fund flows and shifts in investor sentiment aren't visible in this data.")
        }
        val limitations = buildList {
            addAll(window.limitations)
            if (missingBenchmarks) add("Some benchmark prices weren't available for these exact dates, so they're left out.")
            add("News coverage is limited to recent articles from our news provider.")
        }

        val facts = MovementFacts(symbol, name, period.label, window.sessionLabel, signed(changePercent),
            benchmarks.map { it.name to signed(it.changePercent) }, events.map { it.title to it.label }, noConfirmedCatalyst)
        val narrated = narrator?.let { narrate(it, facts) }
        return MovementExplanation(
            symbol = symbol,
            period = period,
            sessionLabel = window.sessionLabel,
            startDate = window.startDate.toString(),
            endDate = window.endDate.toString(),
            startPrice = window.startPrice,
            endPrice = window.endPrice,
            change = window.endPrice - window.startPrice,
            changePercent = changePercent,
            currency = currency,
            benchmarks = benchmarks,
            events = events,
            summary = narrated ?: templateSummary(name, changePercent, window, market, events),
            whatWeKnow = whatWeKnow,
            whatWeCannotConfirm = whatWeCannotConfirm,
            noConfirmedCatalyst = noConfirmedCatalyst,
            limitations = limitations,
            aiGenerated = narrated != null,
            generatedAt = now().toString(),
            explanationVersion = version
        )
    }

    private val narrationWindow = java.util.ArrayDeque<Long>()

    /** Public route: at most [narrationsPerHour] AI narrations start per hour on this instance; beyond that the template is used. */
    private fun narrationAllowed(): Boolean = synchronized(narrationWindow) {
        val t = System.currentTimeMillis()
        while (narrationWindow.isNotEmpty() && t - narrationWindow.first() >= 3_600_000L) narrationWindow.removeFirst()
        if (narrationWindow.size >= narrationsPerHour) false else { narrationWindow.addLast(t); true }
    }

    private class Narration(val text: String?, val denied: Boolean)

    private suspend fun narrate(narrator: MovementNarrator, facts: MovementFacts): String? {
        val key = "narration|$version|" + digest(facts.toJson().toString())
        // A budget denial is remembered briefly (so it can be narrated later); results and failures as before (24 h).
        return cache.getOrLoad(key, NARRATION_TTL, resultTtl = { if (it.denied) FAILURE_TTL else NARRATION_TTL }) { narration(narrator, facts) }.text
    }

    private suspend fun narration(narrator: MovementNarrator, facts: MovementFacts): Narration {
        if (!narrationAllowed()) return Narration(null, denied = true)
        return try {
            Narration(MovementNarrativeValidator.validate(withTimeoutOrNull(narratorTimeoutMillis) { narrator.narrate(facts) }, facts), denied = false)
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            Narration(null, denied = false)
        }
    }

    private fun templateSummary(name: String, changePercent: Double, window: Window, market: BenchmarkMove?, events: List<MovementEvent>): String {
        val move = if (abs(changePercent) < FLAT_PERCENT) "was little changed (${signed(changePercent)})" else "${verb(changePercent)} ${pct(changePercent)}"
        val versus = market?.let { ", while the S&P 500 ${verb(it.changePercent)} ${pct(it.changePercent)}" }.orEmpty()
        val confirmed = events.firstOrNull { it.label == EvidenceLabel.CONFIRMED_EVENT }
        val news = when {
            confirmed != null -> "Company news in the same period included “${confirmed.title}”. It may have contributed, but timing alone doesn't prove it."
            events.isNotEmpty() -> "Some news mentioned the company in this window, but none was a confirmed company-specific event."
            else -> "We didn't find a confirmed company-specific catalyst for this move."
        }
        return "$name $move ${window.phrase}$versus. $news"
    }

    /** News inside [start − 12h, end]; never after the move. Confirmed events first, at most five. */
    private suspend fun events(symbol: String, name: String, window: Window): List<MovementEvent> {
        val from = window.startInstant.minusSeconds(LEAD_SECONDS)
        val feed = safely { news.companyFeed(symbol) }.orEmpty()
        val shortName = name.replace(Regex("(?i)(?:[,. ]+(?:incorporated|inc|corporation|corp|limited|ltd|plc|holdings|company|co)\\.?)+$"), "").trim()
            .split(" ").first().takeIf { it.length >= 4 }
        fun mentionAt(title: String): Int? = listOfNotNull(
            Regex("(?<![A-Za-z0-9])${Regex.escape(symbol)}(?![A-Za-z0-9])").find(title)?.range?.first,
            shortName?.let { Regex("(?i)(?<![a-z0-9])${Regex.escape(it)}").find(title)?.range?.first }
        ).minOrNull()
        fun mentions(title: String) = mentionAt(title) != null
        // The company must be the headline's subject (named near the start), not one name in a list.
        fun subject(title: String) = (mentionAt(title) ?: Int.MAX_VALUE) <= SUBJECT_POSITION
        return feed.mapNotNull { article ->
            val published = article.publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (published.isBefore(from) || published.isAfter(window.endInstant)) return@mapNotNull null
            val category = article.category ?: NewsCategory.OTHER
            val label = when {
                subject(article.title) && org.example.stocksteps.news.NewsClassifier.eventCategory(article.title) in COMPANY_EVENTS -> EvidenceLabel.CONFIRMED_EVENT
                MACRO.containsMatchIn(article.title) && !mentions(article.title) -> EvidenceLabel.MARKET_CONTEXT
                else -> EvidenceLabel.POSSIBLE_CONTRIBUTOR
            }
            MovementEvent(article.id.orEmpty(), article.title, article.source, article.publishedAt, article.url, category, label)
        }.sortedWith(compareBy<MovementEvent> { it.label.ordinal }.thenByDescending { it.publishedAt }).take(MAX_EVENTS)
    }

    private fun dayWindow(quote: StockQuote, closes: List<Pair<LocalDate, Double>>): Window? {
        val price = quote.price ?: return null
        val previous = quote.previousClose?.takeIf { it > 0 } ?: return null
        val quotedAt = quote.timestamp?.let(Instant::ofEpochSecond)
        val session = (quotedAt ?: now()).atZone(NEW_YORK).toLocalDate()
        val close = closeOf(session)
        val start = closes.lastOrNull { it.first.isBefore(session) }?.first ?: previousWeekday(session)
        val live = quotedAt != null && quotedAt.isBefore(close)
        val end = if (live) quotedAt!! else close
        val label = if (live) "${DAY.format(session)} · so far today (as of ${TIME.format(quotedAt!!.atZone(NEW_YORK))} ET)" else "${DAY.format(session)} · regular session"
        return Window(start, session, previous, price, closeOf(start), end, label,
            if (live) "so far today" else "on ${SHORT_DAY.format(session)}",
            buildList {
                add("Compared with the previous close on ${SHORT_DAY.format(start)}. Pre-market and after-hours trading aren't included.")
                if (quotedAt == null) add("The quote had no timestamp, so the session date is estimated.")
            })
    }

    private fun rangeWindow(closes: List<Pair<LocalDate, Double>>, period: MovementPeriod): Window? {
        val (endDate, endPrice) = closes.lastOrNull() ?: return null
        val target = if (period == MovementPeriod.ONE_WEEK) endDate.minusWeeks(1) else endDate.minusMonths(1)
        val (startDate, startPrice) = closes.lastOrNull { !it.first.isAfter(target) } ?: return null
        if (startPrice <= 0) return null
        return Window(startDate, endDate, startPrice, endPrice, closeOf(startDate), closeOf(endDate),
            "${SHORT_DAY.format(startDate)} – ${DAY.format(endDate)}",
            if (period == MovementPeriod.ONE_WEEK) "over the past week" else "over the past month",
            listOf("Uses daily closing prices from ${SHORT_DAY.format(startDate)} to ${SHORT_DAY.format(endDate)}."))
    }

    private suspend fun benchmarkMove(benchmark: String, period: MovementPeriod, window: Window): BenchmarkMove? {
        val name = BENCHMARK_NAMES[benchmark] ?: "$benchmark"
        if (period == MovementPeriod.ONE_DAY) {
            val quote = safely { cached("quote|$benchmark", QUOTE_TTL) { stocks.getStock(benchmark) } } ?: return null
            val session = quote.timestamp?.let { Instant.ofEpochSecond(it).atZone(NEW_YORK).toLocalDate() }
            if (session != window.endDate) return null
            val price = quote.price ?: return null
            val previous = quote.previousClose?.takeIf { it > 0 } ?: return null
            return BenchmarkMove(benchmark, name, (price / previous - 1) * 100)
        }
        val closes = dailyCloses(benchmark).toMap()
        val start = closes[window.startDate] ?: return null
        val end = closes[window.endDate] ?: return null
        return BenchmarkMove(benchmark, name, (end / start - 1) * 100)
    }

    private suspend fun dailyCloses(symbol: String): List<Pair<LocalDate, Double>> =
        safely { charts.getChart(symbol, ChartRange.ALL) }?.points.orEmpty().mapNotNull { point ->
            runCatching { LocalDate.parse(point.time.take(10)) }.getOrNull()?.let { it to point.close }
        }.sortedBy { it.first }

    private class Box<T>(val value: T)
    private suspend fun <T> cached(key: String, ttl: Long, load: suspend () -> T): T =
        cache.getOrLoad(key, ttl) { Box(load()) }.value

    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }

    companion object {
        private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        private val SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private const val DAY_TTL = 5 * 60_000L
        private const val LONG_TTL = 60 * 60_000L
        private const val FAILURE_TTL = 60_000L
        private const val QUOTE_TTL = 60_000L
        private const val NARRATION_TTL = 24 * 3_600_000L
        private const val LEAD_SECONDS = 12 * 3_600L
        private const val MAX_EVENTS = 5
        private const val SUBJECT_POSITION = 20
        private const val IN_LINE_POINTS = 0.5
        private const val FLAT_PERCENT = 0.25
        private val COMPANY_EVENTS = setOf(NewsCategory.EARNINGS, NewsCategory.BUSINESS, NewsCategory.PRODUCTS, NewsCategory.REGULATION)
        private val MACRO = Regex("(?i)\\b(stocks|markets?|wall street|s&p 500|nasdaq|dow|fed|federal reserve|inflation|interest rates?|treasur(y|ies)|economy|jobs report|tariffs?)\\b")
        val BENCHMARK_NAMES = mapOf(
            "SPY" to "S&P 500 (SPY)", "QQQ" to "Nasdaq-100 (QQQ)",
            "XLK" to "Technology sector (XLK)", "XLV" to "Healthcare sector (XLV)", "XLF" to "Financials sector (XLF)",
            "XLY" to "Consumer discretionary (XLY)", "XLC" to "Communication services (XLC)", "XLE" to "Energy sector (XLE)",
            "XLI" to "Industrials sector (XLI)", "XLP" to "Consumer staples (XLP)", "XLU" to "Utilities sector (XLU)",
            "XLRE" to "Real estate sector (XLRE)", "XLB" to "Materials sector (XLB)"
        )
        val SECTOR_ETFS = mapOf(
            "Technology" to "XLK", "Healthcare" to "XLV", "Financial Services" to "XLF", "Consumer Cyclical" to "XLY",
            "Communication Services" to "XLC", "Energy" to "XLE", "Industrials" to "XLI", "Consumer Defensive" to "XLP",
            "Utilities" to "XLU", "Real Estate" to "XLRE", "Basic Materials" to "XLB"
        )

        private fun closeOf(date: LocalDate): Instant = date.atTime(LocalTime.of(16, 0)).atZone(NEW_YORK).toInstant()
        private fun previousWeekday(date: LocalDate): LocalDate {
            var day = date.minusDays(1)
            while (day.dayOfWeek.value >= 6) day = day.minusDays(1)
            return day
        }
        private fun verb(percent: Double) = if (percent >= 0) "rose" else "fell"
        private fun pct(percent: Double) = String.format(Locale.US, "%.2f%%", abs(percent))
        private fun signed(percent: Double) = String.format(Locale.US, "%+.2f%%", percent)
        private fun money(value: Double, currency: String) =
            if (currency == "USD") String.format(Locale.US, "$%,.2f", value) else String.format(Locale.US, "%,.2f %s", value, currency)
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
    }
}
