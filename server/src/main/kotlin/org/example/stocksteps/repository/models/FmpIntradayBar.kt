package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.Sparkline

/** One `/stable/historical-chart/{interval}` bar; `date` is "yyyy-MM-dd HH:mm:ss" exchange time. */
@Serializable
data class FmpIntradayBar(val date: String? = null, val close: Double? = null)

/** Latest trading day's bars as chart points (time = "yyyy-MM-dd HH:mm:ss"), oldest first. */
fun List<FmpIntradayBar>.toLatestSessionPoints(): List<PricePoint> {
    val valid = mapNotNull { bar ->
        val date = bar.date?.takeIf { it.length >= SESSION_DATE_LENGTH } ?: return@mapNotNull null
        val close = bar.close.finiteValue(nonNegative = true)?.takeIf { it > 0 } ?: return@mapNotNull null
        PricePoint(date, close)
    }
    val session = valid.maxOfOrNull { it.time.take(SESSION_DATE_LENGTH) } ?: return emptyList()
    return valid.filter { it.time.startsWith(session) }.sortedBy { it.time }
}

/** One `/stable/historical-price-eod/light` row. */
@Serializable
data class FmpDailyPrice(val date: String? = null, val price: Double? = null, val close: Double? = null)

/** Daily closes oldest first; rows without a valid date or positive close are dropped. */
fun List<FmpDailyPrice>.toDailyPoints(): List<PricePoint> = mapNotNull { row ->
    val date = row.date?.take(SESSION_DATE_LENGTH)?.takeIf { it.length == SESSION_DATE_LENGTH } ?: return@mapNotNull null
    val close = (row.close ?: row.price).finiteValue(nonNegative = true)?.takeIf { it > 0 } ?: return@mapNotNull null
    PricePoint(date, close)
}.distinctBy { it.time }.sortedBy { it.time }

/** Keeps only the most recent trading day, ordered oldest first, with invalid closes dropped. */
fun List<FmpIntradayBar>.toSparkline(symbol: String): Sparkline {
    val valid = mapNotNull { bar ->
        val date = bar.date?.takeIf { it.length >= SESSION_DATE_LENGTH } ?: return@mapNotNull null
        val close = bar.close.finiteValue(nonNegative = true)?.takeIf { it > 0 } ?: return@mapNotNull null
        date to close
    }
    val session = valid.maxOfOrNull { it.first.take(SESSION_DATE_LENGTH) }
        ?: return Sparkline(symbol, emptyList())
    val closes = valid.filter { it.first.startsWith(session) }.sortedBy { it.first }.map { it.second }
    return Sparkline(symbol, closes, session)
}

private const val SESSION_DATE_LENGTH = 10
