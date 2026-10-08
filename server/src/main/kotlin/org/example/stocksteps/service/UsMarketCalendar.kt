package org.example.stocksteps.service

import org.example.stocksteps.model.MarketSession
import org.example.stocksteps.model.MarketSessionStatus
import java.time.*
import java.time.temporal.TemporalAdjusters

/**
 * NYSE/Nasdaq trading calendar computed from the published holiday rules, in exchange time
 * (America/New_York, so daylight saving time is handled by the zone rules).
 *
 * Holidays: New Year's Day (Sunday → Monday; Saturday is not observed), Martin Luther King Jr.
 * Day, Washington's Birthday, Good Friday, Memorial Day, Juneteenth (from 2022), Independence
 * Day, Labor Day, Thanksgiving and Christmas (Saturday → Friday, Sunday → Monday).
 * Early closes at 1:00 PM: July 3, the day after Thanksgiving and December 24 when they are
 * trading days. Unscheduled closures (e.g. national days of mourning) aren't known in advance.
 */
class UsMarketCalendar {
    val zone: ZoneId = ZoneId.of("America/New_York")

    fun holiday(date: LocalDate): String? = holidays(date.year)[date]

    fun isTradingDay(date: LocalDate): Boolean = date.dayOfWeek.value <= 5 && holiday(date) == null

    fun isEarlyClose(date: LocalDate): Boolean {
        if (!isTradingDay(date)) return false
        val thanksgiving = nth(date.year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)
        return date == LocalDate.of(date.year, 7, 3) || date == thanksgiving.plusDays(1) || date == LocalDate.of(date.year, 12, 24)
    }

    fun session(at: Instant): MarketSession {
        val local = at.atZone(zone)
        val date = local.toLocalDate()
        val time = local.toLocalTime()
        val early = isEarlyClose(date)
        val close = if (early) EARLY_CLOSE else CLOSE
        val status = when {
            date.dayOfWeek.value >= 6 -> MarketSessionStatus.WEEKEND
            holiday(date) != null -> MarketSessionStatus.HOLIDAY
            time < PRE_OPEN -> MarketSessionStatus.CLOSED
            time < OPEN -> MarketSessionStatus.PRE_MARKET
            time < close -> MarketSessionStatus.OPEN
            time < (if (early) EARLY_AFTER_HOURS_END else AFTER_HOURS_END) -> MarketSessionStatus.AFTER_HOURS
            else -> MarketSessionStatus.CLOSED
        }
        val trading = isTradingDay(date)
        return MarketSession(
            market = "US stocks (NYSE, Nasdaq)",
            status = status,
            timezone = zone.id,
            asOf = at.toString(),
            sessionDate = date.toString(),
            opensAt = if (trading) OPEN.toString() else null,
            closesAt = if (trading) close.toString() else null,
            nextOpen = if (status == MarketSessionStatus.OPEN) null else nextOpen(local).toInstant().toString(),
            nextOpenLocal = if (status == MarketSessionStatus.OPEN) null else nextOpen(local).toLocalDateTime().toString(),
            utcOffsetMinutes = local.offset.totalSeconds / 60,
            earlyClose = early,
            holiday = holiday(date),
            source = "NYSE holiday and trading-hours rules"
        )
    }

    /** The next regular-session open strictly after [from] (today's open if it's still ahead). */
    fun nextOpen(from: ZonedDateTime): ZonedDateTime {
        var date = from.toLocalDate()
        if (!(isTradingDay(date) && from.toLocalTime() < OPEN)) date = date.plusDays(1)
        while (!isTradingDay(date)) date = date.plusDays(1)
        return date.atTime(OPEN).atZone(zone)
    }

    /** The most recent trading day on or before [date]. */
    fun lastTradingDay(date: LocalDate): LocalDate {
        var day = date
        while (!isTradingDay(day)) day = day.minusDays(1)
        return day
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Map<LocalDate, String>>()

    private fun holidays(year: Int): Map<LocalDate, String> = cache.getOrPut(year) {
        buildMap {
            val newYear = LocalDate.of(year, 1, 1)
            if (newYear.dayOfWeek == DayOfWeek.SUNDAY) put(newYear.plusDays(1), "New Year's Day")
            else if (newYear.dayOfWeek != DayOfWeek.SATURDAY) put(newYear, "New Year's Day")
            put(nth(year, Month.JANUARY, DayOfWeek.MONDAY, 3), "Martin Luther King Jr. Day")
            put(nth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3), "Washington's Birthday")
            put(easter(year).minusDays(2), "Good Friday")
            put(LocalDate.of(year, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), "Memorial Day")
            if (year >= 2022) put(observed(LocalDate.of(year, 6, 19)), "Juneteenth")
            put(observed(LocalDate.of(year, 7, 4)), "Independence Day")
            put(nth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1), "Labor Day")
            put(nth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4), "Thanksgiving Day")
            put(observed(LocalDate.of(year, 12, 25)), "Christmas Day")
        }
    }

    private fun observed(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minusDays(1)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun nth(year: Int, month: Month, day: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day))

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    private fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    companion object {
        val PRE_OPEN: LocalTime = LocalTime.of(4, 0)
        val OPEN: LocalTime = LocalTime.of(9, 30)
        val CLOSE: LocalTime = LocalTime.of(16, 0)
        val EARLY_CLOSE: LocalTime = LocalTime.of(13, 0)
        val AFTER_HOURS_END: LocalTime = LocalTime.of(20, 0)
        val EARLY_AFTER_HOURS_END: LocalTime = LocalTime.of(17, 0)
    }
}
