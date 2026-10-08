package org.example.stocksteps.brief

import org.example.stocksteps.service.UsMarketCalendar
import java.time.*
import java.time.temporal.TemporalAdjusters

/**
 * Toronto Stock Exchange calendar from published TSX holiday rules, in exchange time
 * (America/Toronto, the same clock as New York). Regular session 9:30–16:00; pre-open from 7:00.
 * Holidays: New Year's Day, Family Day (3rd Monday of February), Good Friday, Victoria Day (Monday
 * before May 25), Canada Day, Civic Holiday (1st Monday of August), Labour Day, Thanksgiving (2nd
 * Monday of October), Christmas Day and Boxing Day, moved to a weekday when they fall on a weekend.
 * Early closes (e.g. Dec 24) and unscheduled closures aren't modelled.
 */
class TsxMarketCalendar {
    val zone: ZoneId = ZoneId.of("America/Toronto")
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Map<LocalDate, String>>()

    fun holiday(date: LocalDate): String? = holidays(date.year)[date]
    fun isTradingDay(date: LocalDate): Boolean = date.dayOfWeek.value <= 5 && holiday(date) == null
    fun lastTradingDay(date: LocalDate): LocalDate { var d = date; while (!isTradingDay(d)) d = d.minusDays(1); return d }

    private fun holidays(year: Int): Map<LocalDate, String> = cache.getOrPut(year) {
        fun weekday(date: LocalDate) = when (date.dayOfWeek) { DayOfWeek.SATURDAY -> date.plusDays(2); DayOfWeek.SUNDAY -> date.plusDays(1); else -> date }
        fun nth(month: Month, n: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val christmas = LocalDate.of(year, 12, 25)
        val (xmas, boxing) = when (christmas.dayOfWeek) {
            DayOfWeek.SATURDAY -> christmas.plusDays(2) to christmas.plusDays(3)
            DayOfWeek.SUNDAY -> christmas.plusDays(1) to christmas.plusDays(2)
            DayOfWeek.FRIDAY -> christmas to christmas.plusDays(3)
            else -> christmas to christmas.plusDays(1)
        }
        buildMap {
            put(weekday(LocalDate.of(year, 1, 1)), "New Year's Day")
            put(nth(Month.FEBRUARY, 3), "Family Day")
            put(easter(year).minusDays(2), "Good Friday")
            put(LocalDate.of(year, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), "Victoria Day")
            put(weekday(LocalDate.of(year, 7, 1)), "Canada Day")
            put(nth(Month.AUGUST, 1), "Civic Holiday")
            put(nth(Month.SEPTEMBER, 1), "Labour Day")
            put(nth(Month.OCTOBER, 2), "Thanksgiving Day")
            put(xmas, "Christmas Day")
            put(boxing, "Boxing Day")
        }
    }

    private fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        return LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
    }
}

/** The editions and market phases at one instant, in explicit exchange time zones. */
data class BriefMoment(val edition: BriefEdition, val briefDate: LocalDate, val sessionDate: LocalDate, val us: BriefMarketSession, val ca: BriefMarketSession)

class BriefSessions(private val us: UsMarketCalendar = UsMarketCalendar(), private val tsx: TsxMarketCalendar = TsxMarketCalendar()) {
    private val open = LocalTime.of(9, 30)
    private val close = LocalTime.of(16, 0)
    private val earlyClose = LocalTime.of(13, 0)

    fun usSession(now: Instant): BriefMarketSession {
        val local = now.atZone(us.zone)
        val date = local.toLocalDate()
        val time = local.toLocalTime()
        val closesAt = if (us.isEarlyClose(date)) earlyClose else close
        val trading = us.isTradingDay(date)
        val phase = when {
            date.dayOfWeek.value >= 6 -> BriefPhase.WEEKEND
            us.holiday(date) != null -> BriefPhase.HOLIDAY
            time < UsMarketCalendar.PRE_OPEN -> BriefPhase.CLOSED
            time < open -> BriefPhase.PRE_MARKET
            time < closesAt -> BriefPhase.OPEN
            time < LocalTime.of(20, 0) -> BriefPhase.AFTER_HOURS
            else -> BriefPhase.CLOSED
        }
        val completed = if (trading && time >= closesAt) date else us.lastTradingDay(date.minusDays(1))
        return BriefMarketSession("US", "US stocks (NYSE, Nasdaq)", phase, date.toString(), completed.toString(), us.holiday(date), us.zone.id)
    }

    fun caSession(now: Instant): BriefMarketSession {
        val local = now.atZone(tsx.zone)
        val date = local.toLocalDate()
        val time = local.toLocalTime()
        val trading = tsx.isTradingDay(date)
        val phase = when {
            date.dayOfWeek.value >= 6 -> BriefPhase.WEEKEND
            tsx.holiday(date) != null -> BriefPhase.HOLIDAY
            time < LocalTime.of(7, 0) -> BriefPhase.CLOSED
            time < open -> BriefPhase.PRE_MARKET
            time < close -> BriefPhase.OPEN
            else -> BriefPhase.CLOSED
        }
        val completed = if (trading && time >= close) date else tsx.lastTradingDay(date.minusDays(1))
        return BriefMarketSession("CA", "Canadian stocks (TSX)", phase, date.toString(), completed.toString(), tsx.holiday(date), tsx.zone.id)
    }

    /** The US session decides the edition; a day closed in both markets is a weekend/holiday edition. */
    fun moment(now: Instant): BriefMoment {
        val usSession = usSession(now)
        val caSession = caSession(now)
        val date = now.atZone(us.zone).toLocalDate()
        val edition = when (usSession.phase) {
            BriefPhase.WEEKEND -> BriefEdition.WEEKEND
            BriefPhase.HOLIDAY -> BriefEdition.HOLIDAY
            BriefPhase.OPEN -> BriefEdition.MARKET_HOURS
            BriefPhase.PRE_MARKET -> BriefEdition.PRE_MARKET
            BriefPhase.AFTER_HOURS -> BriefEdition.AFTER_CLOSE
            BriefPhase.CLOSED -> if (usSession.lastCompletedSession == usSession.localDate) BriefEdition.AFTER_CLOSE else BriefEdition.PRE_MARKET
        }
        val session = if (edition == BriefEdition.MARKET_HOURS) date else LocalDate.parse(usSession.lastCompletedSession)
        return BriefMoment(edition, date, session, usSession, caSession)
    }
}
