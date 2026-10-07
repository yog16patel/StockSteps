package org.example.stocksteps.home

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.companydetail.SectionStatus
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.MarketSnapshot
import org.example.stocksteps.model.MarketStatus
import kotlin.math.abs
import kotlin.math.roundToLong

/** Movers lists already delivered together in one market snapshot response. */
enum class MoverCategory(val sectionKey: String) {
    GAINERS("gainers"), LOSERS("losers"), MOST_ACTIVE("mostActive")
}

data class MarketIndexUiModel(
    val symbol: String,
    val name: String,
    val price: String?,
    val change: String,
    val direction: PriceDirection,
    /** Latest-session closes, oldest first; null hides the trend line. */
    val sparkline: List<Double>? = null
)

data class MarketSnapshotUiModel(
    val status: SectionStatus,
    val indices: List<MarketIndexUiModel>,
    val marketStatus: MarketStatus,
    /** Some, but not all, index values are unavailable. */
    val partiallyUnavailable: Boolean
)

data class StockRowUiModel(
    val symbol: String,
    val name: String?,
    val price: String?,
    val change: String,
    val direction: PriceDirection,
    val logoUrl: String? = null,
    /** Latest-session closes, oldest first; null hides the trend line. */
    val sparkline: List<Double>? = null
)

data class MoversUiModel(
    val status: SectionStatus,
    val category: MoverCategory,
    val rows: List<StockRowUiModel>
)

/**
 * Display mapping for Home shared by Compose and SwiftUI. It formats provider
 * values and classifies section availability; it never interprets the market.
 */
object HomePresentation {
    const val MAX_MOVERS = 3
    /** Home shows two compact index cards; the first two snapshot indices (S&P 500, Nasdaq-100). */
    const val HOME_MARKET_CARDS = 2
    const val MAX_NEWS = 3
    private const val UNAVAILABLE = "—"
    const val MIN_SPARKLINE_POINTS = 2
    private val defaultIndices = listOf("SPY" to "S&P 500", "QQQ" to "Nasdaq-100", "DIA" to "Dow 30")

    fun market(
        snapshot: MarketSnapshot?,
        loading: Boolean,
        failed: Boolean,
        maxIndices: Int,
        sparklines: Map<String, List<Double>> = emptyMap()
    ): MarketSnapshotUiModel {
        val indices = snapshot?.indices.orEmpty().take(maxIndices)
        val marketStatus = snapshot?.marketStatus ?: MarketStatus.UNKNOWN
        if (indices.isEmpty()) {
            val status = if (loading || (snapshot == null && !failed)) SectionStatus.LOADING else SectionStatus.ERROR
            val placeholders = defaultIndices.take(maxIndices).map { (symbol, name) ->
                MarketIndexUiModel(symbol, name, null, UNAVAILABLE, PriceDirection.UNAVAILABLE)
            }
            return MarketSnapshotUiModel(status, placeholders, marketStatus, partiallyUnavailable = false)
        }
        val rows = indices.map { index ->
            val percent = index.changePercent.takeIf { index.error == null }
            MarketIndexUiModel(
                symbol = index.symbol,
                name = index.name,
                price = index.price?.takeIf { index.error == null }?.let { price(it, "USD") },
                change = percent(percent),
                direction = direction(percent),
                sparkline = sparklines[index.symbol]?.takeIf { index.error == null && it.size >= MIN_SPARKLINE_POINTS }
            )
        }
        val unavailable = rows.count { it.direction == PriceDirection.UNAVAILABLE }
        return MarketSnapshotUiModel(
            status = if (unavailable == rows.size) SectionStatus.ERROR else SectionStatus.SUCCESS,
            indices = rows,
            marketStatus = marketStatus,
            partiallyUnavailable = unavailable in 1 until rows.size
        )
    }

    fun movers(
        snapshot: MarketSnapshot?,
        loading: Boolean,
        failed: Boolean,
        category: MoverCategory,
        sparklines: Map<String, List<Double>>,
        /** Profile logos for movers whose snapshot entry has none (older backends). */
        logos: Map<String, String>
    ): MoversUiModel {
        val movers = when (category) {
            MoverCategory.GAINERS -> snapshot?.gainers
            MoverCategory.LOSERS -> snapshot?.losers
            MoverCategory.MOST_ACTIVE -> snapshot?.mostActive
        }.orEmpty()
        val status = when {
            movers.isNotEmpty() -> SectionStatus.SUCCESS
            snapshot == null -> if (loading || !failed) SectionStatus.LOADING else SectionStatus.ERROR
            snapshot.errors.any { it.section == category.sectionKey } -> SectionStatus.ERROR
            else -> SectionStatus.EMPTY
        }
        val rows = movers.take(MAX_MOVERS).map { mover ->
            moverRow(mover).copy(
                logoUrl = mover.logoUrl ?: logos[mover.symbol],
                sparkline = sparklines[mover.symbol]?.takeIf { it.size >= MIN_SPARKLINE_POINTS }
            )
        }
        return MoversUiModel(status, category, rows)
    }

    fun newsStatus(count: Int, loading: Boolean, failed: Boolean): SectionStatus = when {
        count > 0 -> SectionStatus.SUCCESS
        loading -> SectionStatus.LOADING
        failed -> SectionStatus.ERROR
        else -> SectionStatus.EMPTY
    }

    /** Movers carry no currency in the public contract, so no currency symbol is claimed. */
    private fun moverRow(mover: MarketMover) =
        stockRow(mover.symbol, mover.name, mover.price, mover.changePercent, currency = null).copy(logoUrl = mover.logoUrl)

    /** Visible movers that still need a logo from their company profile. */
    fun moversMissingLogos(snapshot: MarketSnapshot?, category: MoverCategory): List<String> =
        visibleMovers(snapshot, category).filter { it.logoUrl == null }.map { it.symbol }

    /** Symbols whose sparklines Home should request for the visible movers. */
    fun visibleMoverSymbols(snapshot: MarketSnapshot?, category: MoverCategory): List<String> =
        visibleMovers(snapshot, category).map { it.symbol }

    /** Symbols of the index cards Home shows, for their sparklines. */
    fun visibleIndexSymbols(snapshot: MarketSnapshot?): List<String> =
        snapshot?.indices.orEmpty().take(HOME_MARKET_CARDS).filter { it.error == null }.map { it.symbol }

    private fun visibleMovers(snapshot: MarketSnapshot?, category: MoverCategory): List<MarketMover> = when (category) {
        MoverCategory.GAINERS -> snapshot?.gainers
        MoverCategory.LOSERS -> snapshot?.losers
        MoverCategory.MOST_ACTIVE -> snapshot?.mostActive
    }.orEmpty().take(MAX_MOVERS)

    fun stockRow(symbol: String, name: String?, price: Double?, changePercent: Double?, currency: String?) = StockRowUiModel(
        symbol = symbol,
        name = name?.takeIf { it.isNotBlank() },
        price = price?.let { price(it, currency) },
        change = percent(changePercent),
        direction = direction(changePercent)
    )

    /** Direction follows the displayed (rounded) value so "+0.00%" never shows an up arrow. */
    fun direction(percent: Double?): PriceDirection {
        val hundredths = percent?.takeIf { it.isFinite() }?.let { (it * 100).roundToLong() } ?: return PriceDirection.UNAVAILABLE
        return when {
            hundredths > 0 -> PriceDirection.UP
            hundredths < 0 -> PriceDirection.DOWN
            else -> PriceDirection.UNCHANGED
        }
    }

    fun percent(value: Double?): String = when (direction(value)) {
        PriceDirection.UNAVAILABLE -> UNAVAILABLE
        PriceDirection.UP -> "+${twoDecimals(value!!)}%"
        PriceDirection.DOWN -> "-${twoDecimals(value!!)}%"
        PriceDirection.UNCHANGED -> "0.00%"
    }

    fun price(value: Double, currency: String?): String? {
        if (!value.isFinite()) return null
        val amount = if (abs(value) < 1.0) subDollar(value) else twoDecimals(value)
        return when (currency?.uppercase()) {
            "USD" -> "$$amount"
            null, "" -> amount
            else -> "$amount ${currency.uppercase()}"
        }
    }

    /** Penny stocks: up to 4 decimals so $0.0002 does not read as $0.00 (trailing zeros trimmed to 2). */
    private fun subDollar(value: Double): String {
        val tenThousandths = (abs(value) * 10_000).roundToLong()
        val digits = (tenThousandths % 10_000).toString().padStart(4, '0').trimEnd('0').padEnd(2, '0')
        return "${tenThousandths / 10_000}.$digits"
    }

    private fun twoDecimals(value: Double): String {
        val cents = (abs(value) * 100).roundToLong()
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$whole.${(cents % 100).toString().padStart(2, '0')}"
    }
}
