package org.example.stocksteps.repository.models

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.Sparkline

/** One `/stable/historical-chart/{interval}` bar; `date` is "yyyy-MM-dd HH:mm:ss" exchange time. */
@Serializable
data class FmpIntradayBar(val date: String? = null, val close: Double? = null)

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
