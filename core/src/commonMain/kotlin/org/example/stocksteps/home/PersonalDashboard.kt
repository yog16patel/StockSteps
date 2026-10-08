package org.example.stocksteps.home

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*
import kotlin.math.abs

/** Personal facts only: watchlists are never treated as holdings or investment returns. */
data class HomeHighlight(val instrument: InstrumentRef, val row: StockRowUiModel, val stale: Boolean)
data class HomeFact(val id: String, val title: String, val detail: String, val symbol: String, val alerts: Boolean = false)
@Serializable data class HomeStory(val symbol: String, val article: NewsArticle)
@Serializable data class RecentCompany(val instrument: InstrumentRef, val openedAt: Long)

data class PersonalDashboard(
    val initializing: Boolean = true,
    val watchlistLoading: Boolean = false,
    val watchlistError: String? = null,
    val watchlistCount: Int = 0,
    val highlights: List<HomeHighlight> = emptyList(),
    val quotesLoading: Boolean = false,
    val quotesError: String? = null,
    val quoteNotice: String? = null,
    val brief: List<HomeFact> = emptyList(),
    val events: List<HomeFact> = emptyList(),
    val alertsError: String? = null,
    val stories: List<HomeStory> = emptyList(),
    val newsLoading: Boolean = false,
    val newsError: String? = null,
    val newsNotice: String? = null,
    val mockPersona: String? = null,
    val recent: List<RecentCompany> = emptyList()
)

/** Replaceable deterministic brief policy; has no AI or network dependency. */
interface DailyBriefPolicy {
    fun build(instruments: List<InstrumentRef>, quotes: List<WatchQuote>, events: List<HomeFact>): List<HomeFact>
}
class VerifiedDailyBrief(private val significantMovePercent: Double = PersonalDashboardRules.SIGNIFICANT_MOVE_PERCENT) : DailyBriefPolicy {
    override fun build(instruments: List<InstrumentRef>, quotes: List<WatchQuote>, events: List<HomeFact>): List<HomeFact> {
        val symbols = instruments.map { it.symbol }.toSet()
        val moves = quotes.filter {
            it.symbol in symbols && !it.stale && it.changePercent?.isFinite() == true && abs(it.changePercent) >= significantMovePercent
        }.sortedByDescending { abs(it.changePercent!!) }.map {
            HomeFact("move:${it.symbol}", "${it.symbol} ${HomePresentation.percent(it.changePercent)} today",
                "Daily price change, not your investment return. Quote: ${it.asOf ?: "time unavailable"}.", it.symbol)
        }
        return (moves + events).distinctBy { it.id }.take(PersonalDashboardRules.MAX_BRIEF)
    }
}

object PersonalDashboardRules {
    const val MAX_HIGHLIGHTS = 3
    const val MAX_BRIEF = 3
    const val MAX_EVENTS = 3
    const val MAX_NEWS = 3
    const val MAX_RECENT = 4
    const val SIGNIFICANT_MOVE_PERCENT = 3.0
    const val QUOTE_TTL_MS = 60_000L
    const val NEWS_TTL_MS = 600_000L

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    fun instruments(lists: List<Watchlist>): List<InstrumentRef> = lists.sortedBy { it.order }
        .flatMap { it.entries.sortedBy { entry -> entry.order }.map { entry -> entry.instrument } }
        .distinctBy { it.symbol }

    fun highlights(instruments: List<InstrumentRef>, quotes: List<WatchQuote>, offline: Boolean, significantMove: Double = SIGNIFICANT_MOVE_PERCENT): List<HomeHighlight> {
        val bySymbol = quotes.associateBy { it.symbol }
        // Stable partition: significant moves first, original saved order within each group.
        return instruments.sortedBy { instrument ->
            val quote = bySymbol[instrument.symbol]
            if (!offline && quote?.stale == false && quote.changePercent?.isFinite() == true && abs(quote.changePercent) >= significantMove) 0 else 1
        }.take(MAX_HIGHLIGHTS).map { instrument ->
            val quote = bySymbol[instrument.symbol]
            val stale = offline || quote?.stale == true
            val currency = instrument.currency ?: quote?.currency
            val row = HomePresentation.stockRow(instrument.symbol, instrument.name ?: quote?.name, quote?.price,
                quote?.changePercent, currency)
            HomeHighlight(instrument, row.copy(logoUrl = quote?.logoUrl,
                price = row.price?.let { if (currency.equals("USD", true)) "$it USD" else it }), stale)
        }
    }

    /** Only dates on/after the exchange trading date; never relabel an estimate as confirmed. */
    fun events(earnings: List<UpcomingEarnings>, history: List<AlertEvent>, symbols: Set<String>, today: String, now: Long): List<HomeFact> {
        val calendar = earnings.filter { it.symbol in symbols && it.date >= today && Regex("\\d{4}-\\d{2}-\\d{2}").matches(it.date) }
            .sortedWith(compareBy<UpcomingEarnings> { it.date }.thenBy { it.symbol }).map {
                HomeFact("earnings:${it.symbol}:${it.date}", "${it.symbol} earnings · ${it.date}",
                    "${if (it.status == EarningsDateStatus.CONFIRMED) "Confirmed" else "Estimated"} · ${it.time.name.lowercase().replace('_', ' ')} · ${it.source}", it.symbol)
            }
        val triggered = history.filter { it.symbol in symbols && it.triggeredAt <= now && now - it.triggeredAt <= RECENT_ALERT_MS }
            .sortedBy { it.triggeredAt }.takeLast(MAX_EVENTS).map {
                HomeFact("alert:${it.id}", it.title, it.body, it.symbol, alerts = true)
            }
        return (triggered + calendar).take(MAX_EVENTS)
    }

    /** Backend company feeds already enforce relevance. Never substitute the market-wide feed. */
    fun stories(feeds: Map<String, List<NewsArticle>>, symbols: Set<String>): List<HomeStory> {
        val seenIds = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        return feeds
        .filterKeys { it in symbols }.flatMap { (symbol, articles) -> articles.map { HomeStory(symbol, it) } }
        .filter { it.article.url.startsWith("https://") || it.article.url.startsWith("http://") }
        .sortedByDescending { it.article.publishedAt.orEmpty() }
        .filter {
            val url = it.article.url.substringBefore('#')
            val query = url.substringAfter('?', "").split('&').filter { parameter ->
                val name = parameter.substringBefore('=').lowercase()
                name.isNotEmpty() && !name.startsWith("utm_") && name !in setOf("fbclid", "gclid")
            }.sorted().joinToString("&")
            val identity = url.substringBefore('?').trimEnd('/') + if (query.isEmpty()) "" else "?$query"
            seenUrls.add(identity) && (it.article.id == null || seenIds.add(it.article.id))
        }
        .take(MAX_NEWS)
    }

    private const val RECENT_ALERT_MS = 7 * 24 * 60 * 60 * 1000L
}

/** Read-only development scenario, served only by the MOCK backend. No account is seeded. */
@Serializable
data class HomePersonaFixture(
    val id: String,
    val instruments: List<InstrumentRef> = emptyList(),
    val quotes: List<WatchQuote> = emptyList(),
    val earnings: List<UpcomingEarnings> = emptyList(),
    val alerts: List<AlertEvent> = emptyList(),
    val news: Map<String, List<NewsArticle>> = emptyMap(),
    val recent: List<RecentCompany> = emptyList(),
    val date: String,
    val now: Long,
    val quotesFailed: Boolean = false,
    val newsFailed: Boolean = false,
    val limitation: String? = null
) {
    fun dashboard(): PersonalDashboard {
        val events = PersonalDashboardRules.events(earnings, alerts, instruments.map { it.symbol }.toSet(), date, now)
        return PersonalDashboard(initializing = false, watchlistCount = instruments.size,
            highlights = PersonalDashboardRules.highlights(instruments, quotes, false),
            brief = VerifiedDailyBrief().build(instruments, quotes, events), events = events,
            stories = PersonalDashboardRules.stories(news, instruments.map { it.symbol }.toSet()),
            quotesError = if (quotesFailed) "Sample prices unavailable. Try another scenario." else null,
            newsError = if (newsFailed) "Sample news unavailable. Try another scenario." else null,
            quoteNotice = "Sample scenario · $date", recent = recent,
            newsNotice = limitation, mockPersona = id)
    }
}
