package org.example.stocksteps.markets

import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.home.StockRowUiModel
import org.example.stocksteps.model.*
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class MoversTab(val label: String) { GAINERS("Gainers"), LOSERS("Losers"), MOST_ACTIVE("Most Active") }

/** OPEN = green, EXTENDED = pre-market/after-hours (amber), CLOSED = neutral. Never color alone: [MarketHeaderModel.statusLabel] says it. */
enum class SessionTone { OPEN, EXTENDED, CLOSED, UNKNOWN }

data class MarketHeaderModel(
    val statusLabel: String,
    val tone: SessionTone,
    /** "Closes 4:00 PM ET" / "Opens Mon, Oct 12 at 9:30 AM ET". */
    val detail: String?,
    /** "Updated 5:15 PM ET · Oct 7". */
    val updated: String?,
    val notice: String,
    val sampleData: Boolean
)

data class IndexCardModel(
    val id: String,
    val name: String,
    val value: String,
    val change: String?,
    val percent: String,
    val direction: PriceDirection,
    /** "Proxy: SPY fund price" when the value isn't the index level itself. */
    val proxyLabel: String?,
    val trend: List<Double>,
    val updated: String?,
    val available: Boolean,
    val accessibilityLabel: String
)

data class MoverRowModel(val row: StockRowUiModel, val volume: String?, val accessibilityLabel: String)

data class MoversModel(
    val tab: MoversTab,
    val rows: List<MoverRowModel>,
    /** Rows available in this list; "Show all" appears when it exceeds the preview. */
    val total: Int,
    val expanded: Boolean,
    val universe: String,
    val rankedBy: String,
    val failed: Boolean,
    val emptyMessage: String?
)

data class SectorRowModel(
    val sector: String,
    val symbol: String,
    val change: String,
    val direction: PriceDirection,
    /** Bar length 0..1 relative to the largest absolute move shown. */
    val fraction: Float,
    val accessibilityLabel: String
)

data class SectorsModel(val rows: List<SectorRowModel>, val methodology: String, val period: String, val failed: Boolean, val chartDescription: String)

data class MarketLesson(val id: String, val title: String, val body: String, val more: List<String>)

data class MarketsUiModel(
    val header: MarketHeaderModel,
    val indices: List<IndexCardModel>,
    val indicesFailed: Boolean,
    val movers: MoversModel,
    val sectors: SectorsModel,
    val news: List<NewsUiModel>,
    val newsFailed: Boolean,
    val lesson: MarketLesson
)

/** Markets dashboard content from [MarketsOverview]. Deterministic; no advice; missing values stay missing. */
@OptIn(ExperimentalTime::class)
object MarketsPresenter {
    const val PREVIEW_ROWS = 5

    fun build(overview: MarketsOverview, tab: MoversTab, expanded: Boolean, nowEpochMillis: Long): MarketsUiModel {
        val failed = overview.errors.map { it.section }.toSet()
        val offset = overview.session.utcOffsetMinutes
        return MarketsUiModel(
            header = header(overview),
            indices = overview.indices.map { index(it, offset) },
            indicesFailed = "indices" in failed,
            movers = movers(overview, tab, expanded, failed),
            sectors = sectors(overview.sectors, "sectors" in failed),
            news = overview.news.take(NEWS_PREVIEWS).map { NewsPresentation.model(it, nowEpochMillis) },
            newsFailed = "news" in failed,
            lesson = lessonFor(overview.session.sessionDate)
        )
    }

    fun header(overview: MarketsOverview): MarketHeaderModel {
        val session = overview.session
        val offset = session.utcOffsetMinutes
        val (label, tone) = when (session.status) {
            MarketSessionStatus.OPEN -> "Market open" to SessionTone.OPEN
            MarketSessionStatus.PRE_MARKET -> "Pre-market" to SessionTone.EXTENDED
            MarketSessionStatus.AFTER_HOURS -> "After-hours" to SessionTone.EXTENDED
            MarketSessionStatus.CLOSED -> "Market closed" to SessionTone.CLOSED
            MarketSessionStatus.WEEKEND -> "Closed for the weekend" to SessionTone.CLOSED
            MarketSessionStatus.HOLIDAY -> "Closed · ${session.holiday ?: "Market holiday"}" to SessionTone.CLOSED
            MarketSessionStatus.UNKNOWN -> "Market status unavailable" to SessionTone.UNKNOWN
        }
        val detail = when {
            session.status == MarketSessionStatus.OPEN -> session.closesAt?.let { "Closes ${clock(it)} ET" + if (session.earlyClose) " (early close)" else "" }
            session.nextOpenLocal != null -> "Opens ${localDateTimeLabel(session.nextOpenLocal!!, session.sessionDate)} ET"
            else -> null
        }
        return MarketHeaderModel(
            statusLabel = label,
            tone = tone,
            detail = detail,
            updated = epochSeconds(overview.generatedAt)?.let { "Updated ${timeLabel(it, offset)} ET · ${dateLabel(it, offset)}" },
            notice = overview.dataNotice,
            sampleData = overview.sampleData
        )
    }

    fun index(index: IndexQuote, offsetMinutes: Int): IndexCardModel {
        val value = index.value
        val direction = HomePresentation.direction(index.changePercent)
        val valueText = when {
            value == null -> "—"
            index.isProxy -> HomePresentation.price(value, index.unit) ?: "—"
            else -> number(value)
        }
        val change = index.change?.let { (if (it > 0) "+" else if (it < 0) "-" else "") + number(abs(it)) }
        val percent = HomePresentation.percent(index.changePercent)
        val updated = epochSeconds(index.asOf)?.let { "As of ${timeLabel(it, offsetMinutes)} ET · ${dateLabel(it, offsetMinutes)}" }
        val proxy = if (index.isProxy) "Proxy: ${index.symbol} fund price" else null
        val spokenDirection = when (direction) { PriceDirection.UP -> "up"; PriceDirection.DOWN -> "down"; PriceDirection.UNCHANGED -> "unchanged"; else -> "change unavailable" }
        return IndexCardModel(
            id = index.id,
            name = index.name,
            value = valueText,
            change = change,
            percent = percent,
            direction = direction,
            proxyLabel = proxy,
            trend = index.trend,
            updated = updated,
            available = value != null,
            accessibilityLabel = if (value == null) "${index.name}: unavailable"
                else listOfNotNull(index.name, valueText, "$spokenDirection ${percent.trimStart('+', '-')}", proxy, updated).joinToString(", ")
        )
    }

    fun movers(overview: MarketsOverview, tab: MoversTab, expanded: Boolean, failed: Set<String>): MoversModel {
        val (list, section) = when (tab) {
            MoversTab.GAINERS -> overview.gainers to "gainers"
            MoversTab.LOSERS -> overview.losers to "losers"
            MoversTab.MOST_ACTIVE -> overview.mostActive to "mostActive"
        }
        val rows = list.items.let { if (expanded) it else it.take(PREVIEW_ROWS) }.map { mover ->
            val price = mover.price?.let { HomePresentation.price(it, "USD") }
            val percent = HomePresentation.percent(mover.changePercent)
            val volume = mover.volume?.takeIf { it.isFinite() && it >= 0 }?.let { "Vol ${CompanyOverviewPresenter.compact(it)}" }
            MoverRowModel(
                row = StockRowUiModel(mover.symbol, mover.name, price, percent, HomePresentation.direction(mover.changePercent), mover.logoUrl),
                volume = volume,
                accessibilityLabel = listOfNotNull(mover.symbol, mover.name, price, percent, volume?.replace("Vol", "volume")).joinToString(", ")
            )
        }
        return MoversModel(
            tab = tab,
            rows = rows,
            total = list.items.size,
            expanded = expanded,
            universe = list.universe,
            rankedBy = list.rankedBy,
            failed = section in failed,
            emptyMessage = if (section !in failed && rows.isEmpty()) "No ${tab.label.lowercase()} to show right now." else null
        )
    }

    fun sectors(section: SectorPerformanceSection, failed: Boolean): SectorsModel {
        val known = section.items.filter { it.changePercent?.isFinite() == true }
        val largest = known.maxOfOrNull { abs(it.changePercent!!) }?.takeIf { it > 0 } ?: 1.0
        val rows = known.map { item ->
            val percent = HomePresentation.percent(item.changePercent)
            SectorRowModel(
                sector = item.sector,
                symbol = item.symbol,
                change = percent,
                direction = HomePresentation.direction(item.changePercent),
                fraction = (abs(item.changePercent!!) / largest).toFloat().coerceIn(0.02f, 1f),
                accessibilityLabel = "${item.sector}, $percent, measured by ${item.symbol}"
            )
        }
        val best = rows.firstOrNull(); val worst = rows.lastOrNull()
        return SectorsModel(
            rows = rows,
            methodology = section.methodology,
            period = section.period,
            failed = failed,
            chartDescription = if (best == null || worst == null) "No sector data."
                else "Sector performance, ${section.period.lowercase()}. Best: ${best.sector} ${best.change}. Weakest: ${worst.sector} ${worst.change}."
        )
    }

    /** Rotates daily (by exchange date) through reviewed lessons; the same date always shows the same lesson. */
    fun lessonFor(sessionDate: String?): MarketLesson {
        val ordinal = sessionDate?.let(::dayNumber) ?: 0
        return MarketEducation.lessons[((ordinal % MarketEducation.lessons.size) + MarketEducation.lessons.size) % MarketEducation.lessons.size]
    }

    // --- formatting ---

    private const val NEWS_PREVIEWS = 3

    /** 7,772.36 (index points). */
    fun number(value: Double): String {
        val cents = (abs(value) * 100).roundToLong()
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "${if (value < 0) "-" else ""}$whole.${(cents % 100).toString().padStart(2, '0')}"
    }

    private fun epochSeconds(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Exchange-local clock time "5:15 PM" for an instant and UTC offset. */
    fun timeLabel(epochSeconds: Long, offsetMinutes: Int): String {
        val minutes = (((epochSeconds / 60 + offsetMinutes) % 1440) + 1440) % 1440
        return clock("${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}")
    }

    fun dateLabel(epochSeconds: Long, offsetMinutes: Int): String {
        val days = floorDiv(epochSeconds + offsetMinutes * 60L, 86_400)
        val (_, month, day) = civil(days)
        return "${MONTHS[month - 1]} $day"
    }

    /** "16:00" → "4:00 PM". */
    private fun clock(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    /**
     * Compact session line for the Markets status card (Phase 4B): "Closes · 4:00 PM ET" while open (with "early" on early-close days),
     * otherwise "Next open · Fri, Oct 9 · 9:30 AM ET" (or "Next open · today · 9:30 AM ET"). Uses only the backend's exchange calendar
     * fields ([MarketSession.closesAt], [MarketSession.nextOpenLocal]) — holidays and weekends are already resolved there; null when the
     * calendar gave nothing.
     */
    fun sessionLine(session: MarketSession): String? = runCatching {
        // Non-breaking spaces keep "9:30 AM ET" together when the line wraps at large text.
        fun time(hhmm: String) = clock(hhmm).replace(' ', '\u00A0') + "\u00A0ET"
        when {
            session.status == MarketSessionStatus.OPEN -> session.closesAt?.let { "${if (session.earlyClose) "Closes early" else "Closes"} · ${time(it)}" }
            session.nextOpenLocal != null -> {
                val local = session.nextOpenLocal!!
                val date = local.take(10)
                val opens = time(local.substring(11, 16))
                val day = if (date == session.sessionDate) "today" else dayNumber(date)?.let { days ->
                    val (_, month, dom) = civil(days.toLong())
                    "${WEEKDAYS[(((days + 3) % 7) + 7) % 7]}, ${MONTHS[month - 1]} $dom"
                } ?: return@runCatching null
                "Next open · $day · $opens"
            }
            else -> null
        }
    }.getOrNull()

    /** "2026-10-12T09:30" → "Mon, Oct 12 at 9:30 AM" (or "today at …" when the same date). */
    private fun localDateTimeLabel(local: String, today: String?): String {
        val date = local.take(10)
        val time = clock(local.substring(11, 16))
        if (date == today) return "today at $time"
        val days = dayNumber(date) ?: return "$date $time"
        val (_, month, day) = civil(days.toLong())
        val weekday = WEEKDAYS[(((days + 3) % 7) + 7) % 7] // 1970-01-01 was a Thursday
        return "$weekday, ${MONTHS[month - 1]} $day at $time"
    }

    /** Days since 1970-01-01 for "yyyy-MM-dd". */
    fun dayNumber(date: String): Int? = runCatching {
        val (y, m, d) = date.take(10).split("-").map { it.toInt() }
        val year = if (m <= 2) y - 1 else y
        val era = (if (year >= 0) year else year - 399) / 400
        val yoe = year - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        era * 146097 + doe - 719468
    }.getOrNull()

    private fun civil(days: Long): Triple<Int, Int, Int> {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        return Triple(year, month, day)
    }

    private fun floorDiv(a: Long, b: Long): Long = if (a >= 0) a / b else -((-a + b - 1) / b)
}

/** Reviewed beginner explanations for indices, sectors and daily lessons. No AI. */
object MarketEducation {
    fun index(id: String): MarketLesson? = indices[id]

    fun sector(sector: String, symbol: String, methodology: String): MarketLesson = MarketLesson(
        id = "sector-$symbol",
        title = sector,
        body = sectorDescriptions[sector] ?: "A group of companies in similar lines of business.",
        more = listOf(
            "Here the sector is measured with the $symbol fund, which holds the large US companies in this sector. $methodology",
            "Sectors often move differently from the whole market because news like interest rates, oil prices or consumer spending affects some industries more than others.",
            "A sector rising or falling on one day says little about any single company in it."
        )
    )

    private val indices = mapOf(
        "SP500" to MarketLesson("SP500", "What is the S&P 500?",
            "The S&P 500 tracks about 500 of the largest US companies. It's the most common way to describe how \"the US stock market\" is doing.",
            listOf("It's weighted by market capitalization, so the biggest companies move it the most.",
                "Index levels are in points, not dollars. What matters is the percentage change.")),
        "NASDAQ_COMPOSITE" to MarketLesson("NASDAQ_COMPOSITE", "What is the Nasdaq Composite?",
            "The Nasdaq Composite includes almost every stock listed on the Nasdaq exchange, more than 3,000 companies, with a large share of technology firms.",
            listOf("It's different from the Nasdaq-100, which holds only the 100 largest non-financial Nasdaq companies.",
                "Because it leans toward technology, it often moves more than the S&P 500.")),
        "DOW" to MarketLesson("DOW", "What is the Dow Jones Industrial Average?",
            "The Dow tracks 30 large, well-known US companies. It's one of the oldest market indices.",
            listOf("Unlike most indices it's price-weighted: a stock with a higher share price moves it more, regardless of company size.",
                "With only 30 companies, it covers a much smaller part of the market than the S&P 500.")),
        "TSX" to MarketLesson("TSX", "What is the S&P/TSX Composite?",
            "The S&P/TSX Composite is the main index for the Toronto Stock Exchange, covering about 70% of the value of Canadian-listed stocks.",
            listOf("It has more banks, energy and mining companies than US indices, so it can move differently.",
                "Its companies trade in Canadian dollars, and Canadian market hours and holidays differ slightly from US ones."))
    )

    private val sectorDescriptions = mapOf(
        "Technology" to "Companies that make software, chips, computers and IT services.",
        "Healthcare" to "Drug makers, medical-device companies, insurers and hospitals.",
        "Financials" to "Banks, insurers, asset managers and payment companies.",
        "Consumer Discretionary" to "Businesses selling things people want but don't strictly need, such as cars, retail, travel and restaurants.",
        "Communication Services" to "Telecom, media, entertainment and internet platforms.",
        "Industrials" to "Manufacturers, airlines, railroads, defense and construction companies.",
        "Consumer Staples" to "Everyday essentials such as food, drinks and household products.",
        "Energy" to "Oil, gas and other energy producers and their service companies.",
        "Utilities" to "Electricity, gas and water providers, usually with steady demand.",
        "Real Estate" to "Companies that own or manage property, including REITs.",
        "Materials" to "Chemicals, metals, mining, paper and packaging companies."
    )

    val lessons = listOf(
        MarketLesson("cap-weighting", "Why can the S&P 500 rise when many stocks fall?",
            "The S&P 500 is weighted by market capitalization, so a few very large companies can move the index more than hundreds of smaller ones.",
            listOf("Market capitalization = share price × number of shares.", "Looking at how many stocks rose versus fell (market breadth) gives a fuller picture.")),
        MarketLesson("index", "What is a market index?",
            "An index measures a group of stocks with one number, so you can see how that part of the market is doing at a glance.",
            listOf("You can't buy an index directly, but funds such as ETFs are built to track them.", "Different indices cover different groups of companies.")),
        MarketLesson("volume", "What does trading volume tell you?",
            "Volume is the number of shares traded. High volume means many investors are buying and selling, often around news or big price moves.",
            listOf("High volume doesn't say whether a stock is good or bad.", "Volume early in the day is naturally lower than a full day's total.")),
        MarketLesson("sectors", "Why do sectors move differently?",
            "Each sector reacts to different forces: energy to oil prices, banks to interest rates, retailers to consumer spending.",
            listOf("Spreading investments across sectors is one way people reduce risk.", "One day's sector moves rarely say much about the long term.")),
        MarketLesson("hours", "When is the US stock market open?",
            "Regular trading runs 9:30 AM to 4:00 PM Eastern Time on weekdays, except market holidays.",
            listOf("Pre-market and after-hours trading happen outside those hours with fewer participants, so prices can swing more.", "Some days before holidays close early at 1:00 PM.")),
        MarketLesson("volatility", "What is volatility?",
            "Volatility describes how much and how quickly prices move. A volatile stock can swing a lot in a short time, up or down.",
            listOf("Higher volatility means more uncertainty, not necessarily higher returns.", "Big daily moves in small companies are common and can reverse quickly.")),
        MarketLesson("correction", "What is a market correction?",
            "A correction is a drop of about 10% or more from a recent high. Corrections have happened regularly throughout market history.",
            listOf("A drop of 20% or more is usually called a bear market.", "Nobody can reliably predict when corrections start or end.")),
        MarketLesson("bull-bear", "Bull and bear markets",
            "A bull market is a long period of rising prices; a bear market is a decline of 20% or more from a high.",
            listOf("The names describe the past, not what will happen next.", "Both are normal parts of how markets have behaved over time.")),
        MarketLesson("market-cap", "What is market capitalization?",
            "Market cap is the total value of a company's shares: share price × number of shares. It's a common way to describe company size.",
            listOf("Large caps are often more established; small caps can grow faster but swing more.", "A higher share price doesn't mean a bigger company.")),
        MarketLesson("movers", "Why do some stocks jump 50% in a day?",
            "The biggest daily movers are often small companies reacting to news, earnings or low trading activity, where a few trades can move the price a lot.",
            listOf("Large one-day moves often partly reverse.", "Top movers lists show what moved, not what is a good investment."))
    )
}
