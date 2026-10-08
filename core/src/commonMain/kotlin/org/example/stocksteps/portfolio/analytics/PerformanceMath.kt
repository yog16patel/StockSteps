package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.markets.MarketsPresenter
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/** A dated cash flow from the investor's point of view: money put in is negative, money out positive. */
data class DatedFlow(val date: String, val amount: Double)

sealed interface XirrResult {
    /** Annualized rate as a fraction (0.08 = 8%). */
    data class Rate(val value: Double) : XirrResult
    data class Undefined(val reason: String) : XirrResult
}

/**
 * Money-weighted return. Solves Σ amount / (1 + r)^(days / 365) = 0 (actual/365). Doubles are used
 * only inside the solver; inputs come from exact decimal ledger amounts.
 *
 * The rate is reported only when it is unique: the net present value is scanned over a wide grid
 * (−99.9% to +10,000%) and a single sign change is bracketed and bisected. No sign change or more than
 * one (cash flows that switch direction repeatedly can have several mathematically valid rates) makes
 * the result undefined instead of picking one.
 */
object Xirr {
    fun solve(flows: List<DatedFlow>): XirrResult {
        val usable = flows.filter { it.amount != 0.0 && it.amount.isFinite() }
        if (usable.size < 2) return XirrResult.Undefined("Needs at least one investment and an ending value.")
        if (usable.none { it.amount > 0 } || usable.none { it.amount < 0 }) return XirrResult.Undefined("Needs both money invested and a value or withdrawal.")
        val days = usable.map { MarketsPresenter.dayNumber(it.date) ?: return XirrResult.Undefined("Invalid date.") }
        val start = days.min()
        val years = days.map { (it - start) / 365.0 }
        if (years.max() <= 0.0) return XirrResult.Undefined("All cash flows are on the same day.")
        fun npv(rate: Double): Double = usable.indices.sumOf { usable[it].amount / (1 + rate).pow(years[it]) }
        val grid = buildList {
            var r = -0.999
            while (r < 100.0) { add(r); r = if (r < 1.0) r + 0.005 else r * 1.05 }
            add(100.0)
        }
        val values = grid.map(::npv)
        val brackets = grid.indices.drop(1).filter { i ->
            val a = values[i - 1]; val b = values[i]
            a.isFinite() && b.isFinite() && (a == 0.0 || a.sign != b.sign)
        }
        if (brackets.isEmpty()) return XirrResult.Undefined("No rate makes these cash flows balance.")
        if (brackets.size > 1) return XirrResult.Undefined("These cash flows have more than one possible rate, so none is shown.")
        var low = grid[brackets.single() - 1]
        var high = grid[brackets.single()]
        var lowValue = npv(low)
        repeat(200) {
            val mid = (low + high) / 2
            val value = npv(mid)
            if (!value.isFinite()) return XirrResult.Undefined("The calculation did not converge.")
            if (value == 0.0 || (high - low) < 1e-12) return XirrResult.Rate(mid)
            if (value.sign == lowValue.sign) { low = mid; lowValue = value } else high = mid
        }
        val rate = (low + high) / 2
        val scale = usable.maxOf { abs(it.amount) }
        return if (abs(npv(rate)) <= scale * 1e-6) XirrResult.Rate(rate) else XirrResult.Undefined("The calculation did not converge.")
    }
}

object AnalyticsDates {
    fun day(date: String): Int = requireNotNull(MarketsPresenter.dayNumber(date)) { "Invalid date $date" }

    fun fromDay(day: Int): String {
        val z = day + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    fun plusDays(date: String, days: Int) = fromDay(day(date) + days)

    /** Calendar arithmetic on months, clamping the day (Mar 31 − 1M = Feb 28/29). */
    fun minusMonths(date: String, months: Int): String {
        var year = date.take(4).toInt()
        var month = date.substring(5, 7).toInt() - months
        while (month <= 0) { month += 12; year-- }
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val last = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
        val dayOfMonth = minOf(date.takeLast(2).toInt(), last)
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"
    }

    /** The calendar start of a period ending on [today]; null for ALL (starts at the first transaction). */
    fun periodStart(period: AnalyticsPeriod, today: String): String? = when (period) {
        AnalyticsPeriod.ONE_DAY -> plusDays(today, -1)
        AnalyticsPeriod.ONE_WEEK -> plusDays(today, -7)
        AnalyticsPeriod.ONE_MONTH -> minusMonths(today, 1)
        AnalyticsPeriod.THREE_MONTHS -> minusMonths(today, 3)
        AnalyticsPeriod.ONE_YEAR -> minusMonths(today, 12)
        AnalyticsPeriod.THREE_YEARS -> minusMonths(today, 36)
        AnalyticsPeriod.FIVE_YEARS -> minusMonths(today, 60)
        AnalyticsPeriod.ALL -> null
    }
}
