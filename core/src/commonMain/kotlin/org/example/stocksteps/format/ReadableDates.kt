package org.example.stocksteps.format

/**
 * Readable dates for "as of" stamps (Portfolio Phase 3.1, shared with Home since Phase 4A), without a time-zone database: dates stay calendar dates and UTC instants are
 * shown in UTC with the zone named ("Oct 8, 2026, 3:00 PM UTC") — exact rather than guessed local time. Anything else is shown unchanged.
 */
object ReadableDates {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val UTC_INSTANT = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?Z$""")

    /** "2026-10-09" → "Oct 9, 2026". */
    fun date(value: String): String {
        val m = DATE.matchEntire(value.trim()) ?: return value
        return label(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: value
    }

    /** "2026-09-01" → "Sep 1" — compact chart-axis label (the full date is in the selection line and the spoken description). */
    fun shortDate(value: String): String {
        val m = DATE.matchEntire(value.trim()) ?: return value
        val month = m.groupValues[2].toInt(); val day = m.groupValues[3].toInt()
        if (month !in 1..12 || day !in 1..31) return value
        return "${MONTHS[month - 1]} $day"
    }

    /** "2026-10-07T20:00:00Z" → "Oct 7, 2026, 8:00 PM UTC"; a plain date → "Oct 9, 2026". */
    fun dateTime(value: String): String {
        val trimmed = value.trim()
        DATE.matchEntire(trimmed)?.let { return date(trimmed) }
        val m = UTC_INSTANT.matchEntire(trimmed) ?: return value
        val day = label(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return value
        val hour = m.groupValues[4].toInt(); val minute = m.groupValues[5]
        if (hour > 23 || minute.toInt() > 59) return value
        return "$day, ${if (hour % 12 == 0) 12 else hour % 12}:$minute ${if (hour < 12) "AM" else "PM"} UTC"
    }

    private fun label(year: String, month: String, day: String): String? {
        val m = month.toInt(); val d = day.toInt()
        if (m !in 1..12 || d !in 1..31) return null
        return "${MONTHS[m - 1]} $d, $year"
    }
}
