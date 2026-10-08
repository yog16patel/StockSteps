package org.example.stocksteps.service

import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.ValuationObservation
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One reported fiscal quarter as filed. [availableOn] is the filing (accepted) date: the first day
 * the EPS was public. EPS and shares are as reported (not split-adjusted).
 */
@kotlinx.serialization.Serializable
data class QuarterlyEarnings(
    val periodEnd: String,
    val availableOn: String,
    val epsDiluted: Double?,
    val shares: Double?,
    val currency: String?
)

/** Source of quarterly earnings for the historical P/E series (provider or MOCK fixtures). */
fun interface QuarterlyEarningsSource {
    suspend fun quarterlyEarnings(symbol: String): List<QuarterlyEarnings>
}

data class PeSeries(val observations: List<ValuationObservation>, val currentPe: Double?, val ttmEps: Double?, val splitAdjusted: Boolean)

/**
 * Historical P/E from split-adjusted daily closes and reported quarterly diluted EPS.
 *
 * - Sampling: one observation per calendar month, at that month's last trading close.
 * - Earnings: the latest four consecutive quarters whose filings were public on that date
 *   (no look-ahead); a missing or out-of-sequence quarter leaves the month empty.
 * - Splits: a share-count jump between consecutive quarters close to a whole ratio (2:1, 3:1,
 *   10:1, or the reverse) divides earlier EPS by that ratio, matching split-adjusted prices.
 * - Validity: zero or negative TTM EPS has no meaningful P/E, so that month is omitted.
 */
object ValuationCalculations {
    private const val MIN_QUARTER_DAYS = 70L
    private const val MAX_QUARTER_DAYS = 110L
    private const val MIN_SPLIT_RATIO = 1.8
    private const val SPLIT_TOLERANCE = 0.08

    /**
     * Multiplier that converts each quarter's reported EPS to today's share basis: 0.1 for quarters
     * reported before a 10:1 split (ten times as many shares now, so a tenth of the EPS).
     */
    fun splitFactors(quarters: List<QuarterlyEarnings>): Map<String, Double> {
        val ordered = quarters.sortedBy { it.periodEnd }
        val factors = mutableMapOf<String, Double>()
        var cumulative = 1.0
        ordered.lastOrNull()?.let { factors[it.periodEnd] = 1.0 }
        // Walk from newest to oldest; every earlier quarter carries all later splits.
        for (i in ordered.lastIndex downTo 1) {
            cumulative /= splitStep(ordered[i - 1].shares, ordered[i].shares)
            factors[ordered[i - 1].periodEnd] = cumulative
        }
        return factors
    }

    /** 2.0 for a 2:1 split between two quarters, 0.5 for a 1:2 reverse split, else 1.0. */
    private fun splitStep(before: Double?, after: Double?): Double {
        if (before == null || after == null || before <= 0 || after <= 0) return 1.0
        val ratio = after / before
        fun whole(r: Double) = r >= MIN_SPLIT_RATIO && abs(r - r.roundToInt()) <= r * SPLIT_TOLERANCE
        return when {
            whole(ratio) -> ratio.roundToInt().toDouble()
            whole(1 / ratio) -> 1.0 / (1 / ratio).roundToInt()
            else -> 1.0
        }
    }

    /** Trailing-twelve-month EPS known on [date], or null when four consecutive quarters aren't available. */
    fun ttmEpsOn(date: String, quarters: List<QuarterlyEarnings>, factors: Map<String, Double>): Double? {
        val known = quarters.filter { it.availableOn <= date }.sortedByDescending { it.periodEnd }.take(4)
        if (known.size < 4) return null
        if (known.map { it.currency }.distinct().size > 1) return null
        known.zipWithNext().forEach { (later, earlier) ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(earlier.periodEnd), LocalDate.parse(later.periodEnd))
            if (days !in MIN_QUARTER_DAYS..MAX_QUARTER_DAYS) return null
        }
        if (known.any { it.epsDiluted == null }) return null
        return known.sumOf { it.epsDiluted!! * (factors[it.periodEnd] ?: 1.0) }
    }

    fun monthlyPe(closes: List<PricePoint>, quarters: List<QuarterlyEarnings>, today: LocalDate): PeSeries {
        val factors = splitFactors(quarters)
        val monthEnds = closes.filter { it.time.length >= 10 && it.time.take(10) <= today.toString() && it.close > 0 }
            .groupBy { it.time.take(7) }
            .map { (_, points) -> points.maxBy { it.time } }
            .sortedBy { it.time }
        val observations = monthEnds.mapNotNull { point ->
            val date = point.time.take(10)
            val eps = ttmEpsOn(date, quarters, factors)?.takeIf { it > 0 } ?: return@mapNotNull null
            ValuationObservation(date, round2(point.close / eps))
        }
        val latest = closes.filter { it.close > 0 }.maxByOrNull { it.time }
        val currentEps = latest?.let { ttmEpsOn(it.time.take(10), quarters, factors) }
        return PeSeries(
            observations = observations,
            currentPe = if (latest != null && currentEps != null && currentEps > 0) round2(latest.close / currentEps) else null,
            ttmEps = currentEps?.let(::round2),
            splitAdjusted = factors.values.any { it != 1.0 }
        )
    }

    private fun round2(value: Double) = kotlin.math.round(value * 100) / 100
}
