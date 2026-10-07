package org.example.stocksteps.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Core Company Details payload: one request for identity, price, market status and annual
 * fundamentals. Sections fail independently; [errors] names the ones that could not load
 * (sections: "profile", "quote", "marketStatus", "fundamentals") with user-safe messages.
 */
@Serializable
data class CompanyDetails(
    val symbol: String,
    val profile: CompanyProfile? = null,
    val quote: StockQuote? = null,
    val marketStatus: MarketStatus = MarketStatus.UNKNOWN,
    val fundamentals: CompanyFundamentals? = null,
    val errors: List<SnapshotSectionError> = emptyList()
)

@Serializable
enum class ChartRange(val label: String) {
    @SerialName("1D") ONE_DAY("1D"),
    @SerialName("1W") ONE_WEEK("1W"),
    @SerialName("1M") ONE_MONTH("1M"),
    @SerialName("3M") THREE_MONTHS("3M"),
    @SerialName("1Y") ONE_YEAR("1Y"),
    @SerialName("5Y") FIVE_YEARS("5Y"),
    @SerialName("ALL") ALL("ALL");

    companion object {
        fun parse(value: String?): ChartRange? = entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
    }
}

/** One close per point, oldest first. `time` is ISO date ("2026-10-06") or exchange-local date-time for intraday. */
@Serializable
data class PricePoint(val time: String, val close: Double)

@Serializable
data class PriceChart(
    val symbol: String,
    val range: ChartRange,
    val points: List<PricePoint>,
    /** "5min" for 1D, "1day" otherwise. */
    val interval: String
)

@Serializable
data class WhyMovingSource(val title: String, val url: String, val publisher: String? = null)

/**
 * Source-backed explanation of a recent price move. Facts come from normalized data; the text
 * never recommends buying or selling.
 */
@Serializable
data class WhyMoving(
    val symbol: String,
    val summary: String,
    val whyItMatters: String? = null,
    val changePercent: Double? = null,
    val sources: List<WhyMovingSource> = emptyList(),
    val generatedAt: String? = null
)
