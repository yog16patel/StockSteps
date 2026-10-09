package org.example.stocksteps.service

import org.example.stocksteps.earnings.EarningsEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Phase 3D: earnings-aware statement freshness, from earnings events the server already loaded (no extra provider
 * calls just to choose a cache lifetime).
 *
 * Evidence: an event with reported figures (`actual`) announced within the last [window] days. An announcement is not
 * proof that the provider has the statements yet (filings lag press releases), so it only *shortens* the statement
 * lifetime for that symbol to [acceleratedTtl] until rows for the reported period appear, then the normal lifetime
 * returns. "Rows for the reported period" means: a row ending on/after the event's `periodEnd` when the source gives one;
 * otherwise (Finnhub calendars have no period end) a row newer than the newest period available when the report arrived
 * (see [covers]). Annual statements are only accelerated for fiscal Q4 reports. Empty responses never count as the new
 * period. A newer report triggers one invalidation of that symbol's cached statements ([onNewReport] listeners); repeat
 * observations of the same report don't. Corrections have no reliable signal and keep the normal lifetime.
 */
class EarningsStatementSignals(
    private val now: () -> Instant = Instant::now,
    private val window: Long = 10,
    private val acceleratedTtl: Long = 2 * 3_600_000L,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    /** The newest reported quarter for one symbol whose statements may not be published yet. */
    data class Expected(val reportDate: LocalDate, val fiscalYear: Int, val fiscalQuarter: Int, val periodEnd: LocalDate?)

    private val expected = ConcurrentHashMap<String, Expected>()
    private val listeners = CopyOnWriteArrayList<(String, Expected) -> Unit>()

    fun onNewReport(listener: (symbol: String, report: Expected) -> Unit) { listeners += listener }

    private fun today() = now().atZone(ZoneOffset.UTC).toLocalDate()

    /** Records reported events (called by the earnings service when it fetches events). */
    fun observe(events: List<EarningsEvent>) {
        val today = today()
        for (event in events) {
            if (event.actual == null) continue
            val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: continue
            if (date > today || ChronoUnit.DAYS.between(date, today) > window) continue
            val report = Expected(date, event.fiscalYear, event.fiscalQuarter, event.periodEnd?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() })
            val symbol = event.symbol.uppercase()
            var added = false
            expected.compute(symbol) { _, previous -> if (previous == null || date > previous.reportDate) { added = true; report } else previous }
            if (added) { meter.event("statements.earningsSignal"); listeners.forEach { it(symbol, report) } }
        }
        if (expected.size > MAX_SYMBOLS) prune()
    }

    private fun active(symbol: String): Expected? {
        val e = expected[symbol.uppercase()] ?: return null
        if (ChronoUnit.DAYS.between(e.reportDate, today()) > window) { expected.remove(symbol.uppercase(), e); return null }
        return e
    }

    private fun prune() {
        expected.entries.removeIf { ChronoUnit.DAYS.between(it.value.reportDate, today()) > window }
        baselines.keys.removeIf { key -> expected[key.substringBefore('|')] == null }
    }

    /** Counts a statement load that still lacked the reported period and so got the accelerated lifetime (one more refresh). */
    fun countRefresh() = meter.event("fmp.statements.earningsRefresh")

    /** Newest period end seen per symbol, frequency and report when the report arrived (or first loaded after it). */
    private val baselines = ConcurrentHashMap<String, LocalDate>()
    private fun baselineKey(symbol: String, kind: String?, report: Expected) = "${symbol.uppercase()}|$kind|${report.reportDate}|${report.fiscalYear}Q${report.fiscalQuarter}"

    /** Records the newest period end already cached when [report] arrived (first value wins). */
    fun setBaseline(symbol: String, kind: String?, report: Expected, newestDate: String?) {
        parse(newestDate)?.let { baselines.putIfAbsent(baselineKey(symbol, kind, report), it) }
    }

    private fun parse(date: String?) = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * True when the newest statement row (period end [newestDate]) covers the report: on/after the event's period end
     * when the source gives one; otherwise strictly newer than the newest period that was available when the report
     * arrived (its baseline; the first load after the report sets it when nothing was cached, and isn't counted as
     * covered). Announcement-to-period-end gaps vary too much (a fast reporter's previous quarter can end < 105 days
     * before the announcement) for a date-distance rule, and fiscal labels differ between providers, so neither is used.
     */
    fun covers(symbol: String, kind: String?, report: Expected, newestDate: String?): Boolean {
        val end = parse(newestDate) ?: return false
        report.periodEnd?.let { return end >= it }
        val base = baselines.putIfAbsent(baselineKey(symbol, kind, report), end) ?: return false
        return end > base
    }

    /** Whether [symbol]'s statements of [period] ("quarter", "annual", "ttm") are awaiting a reported period. */
    fun awaiting(symbol: String, period: String?): Expected? = active(symbol)?.takeIf { period != "annual" || it.fiscalQuarter == 4 }

    /**
     * Lifetime for freshly loaded statement rows: [normal] unless a recent report isn't in them yet, then
     * [acceleratedTtl] (never longer than [normal]).
     */
    fun statementTtl(symbol: String, period: String?, newestDate: String?, normal: Long): Long {
        val report = awaiting(symbol, period) ?: return normal
        return if (covers(symbol, period, report, newestDate)) normal else minOf(normal, acceleratedTtl)
    }

    companion object {
        const val MAX_SYMBOLS = 5_000
    }
}
