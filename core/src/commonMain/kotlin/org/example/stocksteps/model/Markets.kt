package org.example.stocksteps.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** US equity session at a moment, from the NYSE trading calendar (not the device clock). */
@Serializable
enum class MarketSessionStatus { OPEN, PRE_MARKET, AFTER_HOURS, CLOSED, WEEKEND, HOLIDAY, UNKNOWN }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarketSession(
    /** Which market the status describes, e.g. "US stocks (NYSE, Nasdaq)". */
    val market: String,
    val status: MarketSessionStatus,
    val timezone: String,
    /** The instant the status was evaluated for (ISO-8601). */
    val asOf: String,
    /** Exchange-local date of [asOf]. */
    val sessionDate: String? = null,
    /** Today's regular session in exchange time, when today is a trading day ("09:30", "16:00"). */
    val opensAt: String? = null,
    val closesAt: String? = null,
    /** Next regular-session open (ISO-8601) when the market isn't open. */
    val nextOpen: String? = null,
    /** [nextOpen] in exchange time ("2026-10-12T09:30"), so apps need no time-zone database. */
    val nextOpenLocal: String? = null,
    /** Exchange UTC offset at [asOf] (e.g. -240 for EDT), for showing update times in exchange time. */
    val utcOffsetMinutes: Int = 0,
    @EncodeDefault val earlyClose: Boolean = false,
    /** Holiday name when [status] is HOLIDAY. */
    val holiday: String? = null,
    val source: String
)

/**
 * One market index. [isProxy] is true when the value comes from a fund that tracks the index
 * (then [symbol] is the fund and [proxyDescription] names it); index values are in points.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class IndexQuote(
    val id: String,
    val name: String,
    val symbol: String,
    val value: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    /** "points" for index values; the fund's currency (USD, CAD) for proxies. */
    val unit: String,
    /** Currency of the market the index covers. */
    val currency: String,
    val region: String,
    @EncodeDefault val isProxy: Boolean = false,
    val proxyDescription: String? = null,
    /** When the value was last updated (ISO-8601), from the provider's quote time. */
    val asOf: String? = null,
    /** Recent daily closes, oldest first; empty when no reliable history is available. */
    @EncodeDefault val trend: List<Double> = emptyList(),
    val trendLabel: String? = null,
    val error: ApiError? = null
)

/** A ranked mover list with its universe and source spelled out. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarketMoverList(
    @EncodeDefault val items: List<MarketMover> = emptyList(),
    /** e.g. "US-listed stocks in FMP's daily movers list". */
    val universe: String,
    val source: String,
    /** "Absolute share volume" etc. */
    val rankedBy: String,
    val asOf: String? = null
)

@Serializable
data class SectorPerformance(
    val sector: String,
    /** The fund used to measure the sector (e.g. XLK). */
    val symbol: String,
    val fundName: String,
    val changePercent: Double? = null,
    val asOf: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SectorPerformanceSection(
    @EncodeDefault val items: List<SectorPerformance> = emptyList(),
    val methodology: String,
    val period: String,
    @EncodeDefault val isProxy: Boolean = true
)

/**
 * The Markets dashboard in one response. Every section is independent: a failed section is empty
 * and listed in [errors] (section ids: session, indices, gainers, losers, mostActive, sectors, news).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarketsOverview(
    val session: MarketSession,
    @EncodeDefault val indices: List<IndexQuote> = emptyList(),
    val gainers: MarketMoverList,
    val losers: MarketMoverList,
    val mostActive: MarketMoverList,
    val sectors: SectorPerformanceSection,
    @EncodeDefault val news: List<NewsArticle> = emptyList(),
    /** Plain statement about data freshness (delays, sample data). */
    val dataNotice: String,
    @EncodeDefault val sampleData: Boolean = false,
    val generatedAt: String,
    @EncodeDefault val errors: List<SnapshotSectionError> = emptyList()
)
