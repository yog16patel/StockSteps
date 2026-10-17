package org.example.stocksteps.watchlist

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class WatchlistTab(val id: String, val name: String, val count: Int, val selected: Boolean)

data class WatchlistRowModel(
    val entryId: String,
    val symbol: String,
    val name: String?,
    val logoUrl: String?,
    /** "$258.42", or "41.20 CAD" outside USD (the currency is already in the text; never append [currency] again). */
    val price: String?,
    val currency: String?,
    val change: String,
    val direction: PriceDirection,
    /** Signed amount and percentage, "+$3.04 (+0.91%)"; the percentage alone when the amount is missing; null without a quote (Phase 5A). */
    val changeLine: String?,
    /** False when no quote came back: the row shows [UNAVAILABLE] instead of a price, never $0.00 or 0.00%. */
    val quoteAvailable: Boolean,
    /** "Delayed · as of Oct 6" when the quote isn't from the latest session. */
    val staleLabel: String?,
    val notePreview: String?,
    val note: String?,
    val activeAlerts: Int,
    val accessibilityLabel: String
)

/**
 * Equal-weighted average of the daily % change of stocks with a fresh quote from the same
 * (latest) session. It is not a portfolio return: a watchlist has no holdings.
 */
data class WatchlistSummary(
    val title: String,
    val companies: String,
    val change: String?,
    val direction: PriceDirection,
    /** "Equal-weighted average of 7 of 8 stocks · not a portfolio return" */
    val methodology: String?,
    val updated: String?,
    val stale: Boolean
)

data class WatchlistModel(
    val tabs: List<WatchlistTab>,
    val selectedId: String?,
    val summary: WatchlistSummary?,
    val rows: List<WatchlistRowModel>,
    /** Deterministic statements about today, only when supported by the data (the equal-weighted average first, Phase 5A). */
    val insights: List<String>,
    val emptyMessage: String?,
    /**
     * Visible empty-state title: "Your watchlist is empty" for the default list, "This watchlist is empty" otherwise — the selector already shows
     * the name, so a long name no longer becomes a multi-line heading (Phase 5A.1). Null when the list has stocks.
     */
    val emptyTitle: String? = null,
    /** Spoken form that keeps the list's identity: "Long Term Growth is empty". */
    val emptySpokenTitle: String? = null,
    /**
     * One line under the title: company count and freshness, "5 companies · Updated 5:16 PM ET". Inside each part spaces are non-breaking, so
     * it wraps only between parts, never inside a time ("5:16 / PM ET", Phase 5A.1). Null when there is no list.
     */
    val headerLine: String? = null,
    /** True when [headerLine] reports offline / saved prices (shown in the caution colour). */
    val headerCaution: Boolean = false
)

@OptIn(ExperimentalTime::class)
object WatchlistPresenter {
    /** Shown in place of a price when no quote came back (Phase 5A). */
    const val UNAVAILABLE = "No data available"
    /** What the row's change refers to, shown once under the list. */
    const val CHANGE_CONTEXT = "Change since the previous close. Prices may be delayed."
    const val EMPTY_MESSAGE = "Add companies to follow and see their latest prices and news in one place."

    /**
     * Phase 5A: a message when the list has stocks but the price request failed (not while it is still loading, and never as an empty list);
     * null otherwise. Rows stay, showing [UNAVAILABLE].
     */
    fun pricesUnavailableMessage(hasRows: Boolean, hasData: Boolean, loading: Boolean): String? =
        if (hasRows && !hasData && !loading) "Prices couldn't be loaded. Check your connection and try again." else null

    fun build(
        lists: List<Watchlist>,
        selectedId: String?,
        data: WatchDataResponse?,
        alerts: List<AlertRule>,
        offlineSince: Long?
    ): WatchlistModel {
        val selected = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull { it.isDefault } ?: lists.firstOrNull()
        val tabs = lists.map { WatchlistTab(it.id, it.name, it.entries.size, it.id == selected?.id) }
        if (selected == null) return WatchlistModel(tabs, null, null, emptyList(), emptyList(), "Your watchlists couldn't be loaded.")
        val quotes = data?.quotes.orEmpty().associateBy { it.symbol }
        val offset = data?.session?.utcOffsetMinutes ?: 0
        val activeAlerts = alerts.filter { it.status == AlertStatus.ACTIVE }.groupingBy { it.instrument.symbol }.eachCount()
        val built = selected.entries.map { entry -> row(entry, quotes[entry.instrument.symbol], activeAlerts[entry.instrument.symbol] ?: 0, offset, offlineSince != null) }
        // Offline: the label most rows share ("Saved prices · as of Oct 7") moves to the header once; rows keep it only when their date differs.
        // The spoken row label still says it (Phase 5A.1: the same orange line was repeated on every row).
        val shared = if (offlineSince != null) built.mapNotNull { it.staleLabel }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key else null
        val rows = if (shared == null) built else built.map { if (it.staleLabel == shared) it.copy(staleLabel = null) else it }
        val summary = summary(selected, quotes, data, offlineSince)
        val freshness = when {
            selected.entries.isEmpty() -> null
            offlineSince != null -> "Offline · " + (shared?.replace(" · ", " ")?.replaceFirstChar { it.lowercase() } ?: "saved prices")
            else -> summary.updated
        }
        return WatchlistModel(
            tabs = tabs,
            selectedId = selected.id,
            summary = summary,
            rows = rows,
            insights = listOfNotNull(summary.change?.let { average(it, summary) }) + insights(selected, quotes, data),
            emptyMessage = if (selected.entries.isEmpty()) "No stocks in ${selected.name} yet. Search for a company and tap Add to Watchlist." else null,
            emptyTitle = if (selected.entries.isEmpty()) (if (selected.isDefault) "Your watchlist is empty" else "This watchlist is empty") else null,
            emptySpokenTitle = if (selected.entries.isEmpty()) "${selected.name} is empty" else null,
            headerLine = listOfNotNull(summary.companies, freshness).joinToString(" · ") { keepTogether(it) },
            headerCaution = offlineSince != null && selected.entries.isNotEmpty()
        )
    }

    /** Non-breaking spaces inside a header part, except around its own " · " separators, so lines break only between parts. */
    internal fun keepTogether(part: String): String = part.split(" · ").joinToString(" · ") { it.replace(' ', '\u00A0') }

    /**
     * The equal-weighted average as a plain sentence (it used to be its own card): "On average, these 5 stocks moved +0.50% today
     * (equal-weighted; not a portfolio return)." When only some stocks have today's price it says so instead of implying all moved.
     */
    private fun average(change: String, summary: WatchlistSummary): String? {
        val counts = summary.methodology?.substringAfter("average of ")?.substringBefore(" stocks")?.split(" of ")?.mapNotNull { it.trim().toIntOrNull() }
        // An "average" of one stock only repeats that row.
        if (counts != null && counts.size == 2 && counts[0] < 2) return null
        val subject = when {
            counts == null || counts.size != 2 -> "these stocks"
            counts[0] == counts[1] -> "these ${counts[0]} stocks"
            else -> "the ${counts[0]} of ${counts[1]} stocks with today's prices"
        }
        return "On average, $subject moved ${change.removeSuffix(" today")} today (equal-weighted; not a portfolio return)."
    }

    private fun row(entry: WatchlistEntry, quote: WatchQuote?, alerts: Int, offset: Int, offline: Boolean): WatchlistRowModel {
        val currency = quote?.currency ?: entry.instrument.currency
        val price = quote?.price?.let { HomePresentation.price(it, currency) }
        val change = HomePresentation.percent(quote?.changePercent)
        val staleDate = quote?.takeIf { it.stale || offline }?.asOf?.let { iso -> epochSeconds(iso)?.let { MarketsPresenter.dateLabel(it, offset) } }
        val stale = when {
            quote == null -> null
            offline -> "Saved prices${staleDate?.let { " · as of $it" } ?: ""}"
            quote.stale -> "Delayed${staleDate?.let { " · as of $it" } ?: ""}"
            else -> null
        }
        val name = quote?.name ?: entry.instrument.name
        val preview = entry.note?.lineSequence()?.firstOrNull()?.let { if (it.length > 80) it.take(79).trimEnd() + "…" else it }
        val direction = HomePresentation.direction(quote?.changePercent)
        val amount = HomePresentation.signedPrice(quote?.change, currency)
        val changeLine = when {
            quote?.changePercent == null && amount == null -> null
            amount != null && quote?.changePercent != null -> "$amount ($change)"
            amount != null -> amount
            else -> change
        }
        // Spoken direction in words, never colour alone: "up $3.04, 0.91% today".
        val spoken = when {
            price == null -> "price unavailable"
            changeLine == null -> "$price, change unavailable"
            direction == PriceDirection.UNCHANGED -> "$price, unchanged today"
            else -> "$price, ${if (direction == PriceDirection.UP) "up" else "down"} ${listOfNotNull(amount?.trimStart('+', '-'), change.trimStart('+', '-').takeIf { quote?.changePercent != null }).joinToString(", ")} today"
        }
        return WatchlistRowModel(
            entryId = entry.id,
            symbol = entry.instrument.symbol,
            name = name,
            logoUrl = quote?.logoUrl,
            price = price,
            currency = currency,
            change = change,
            direction = direction,
            changeLine = changeLine,
            quoteAvailable = price != null,
            staleLabel = stale,
            notePreview = preview,
            note = entry.note,
            activeAlerts = alerts,
            accessibilityLabel = listOfNotNull(entry.instrument.symbol, name, spoken,
                stale, if (alerts > 0) "$alerts active alert${if (alerts == 1) "" else "s"}" else null, preview?.let { "Note: $it" }).joinToString(", ")
        )
    }

    fun summary(list: Watchlist, quotes: Map<String, WatchQuote>, data: WatchDataResponse?, offlineSince: Long?): WatchlistSummary {
        val withQuotes = list.entries.mapNotNull { quotes[it.instrument.symbol] }
        val latest = withQuotes.filter { !it.stale }.mapNotNull { it.sessionDate }.maxOrNull()
        val eligible = withQuotes.filter { !it.stale && it.sessionDate == latest && it.changePercent?.isFinite() == true }
        val average = eligible.takeIf { it.isNotEmpty() }?.map { it.changePercent!! }?.average()
        val count = list.entries.size
        val offset = data?.session?.utcOffsetMinutes ?: 0
        return WatchlistSummary(
            title = list.name,
            companies = "$count ${if (count == 1) "company" else "companies"}",
            change = average?.let { "${HomePresentation.percent(it)} today" },
            direction = HomePresentation.direction(average),
            methodology = average?.let { "Equal-weighted average of ${eligible.size} of $count stocks · not a portfolio return" },
            updated = when {
                offlineSince != null -> "Offline · showing saved prices"
                // Phase 8C: with the date ("Updated Oct 7, 5:15 PM ET"), so a time alone is never read as today's.
                data != null -> epochSeconds(data.generatedAt)?.let { "Updated ${MarketsPresenter.dateLabel(it, offset)}, ${MarketsPresenter.timeLabel(it, offset)} ET" }
                else -> null
            },
            stale = offlineSince != null || withQuotes.any { it.stale }
        )
    }

    fun insights(list: Watchlist, quotes: Map<String, WatchQuote>, data: WatchDataResponse?): List<String> {
        val fresh = list.entries.mapNotNull { quotes[it.instrument.symbol] }.filter { !it.stale && it.changePercent?.isFinite() == true }
        val result = mutableListOf<String>()
        if (fresh.size >= 2) {
            val up = fresh.count { HomePresentation.direction(it.changePercent) == PriceDirection.UP }
            val down = fresh.count { HomePresentation.direction(it.changePercent) == PriceDirection.DOWN }
            result += "$up ${if (up == 1) "stock" else "stocks"} rose and $down ${if (down == 1) "stock" else "stocks"} fell today."
            fresh.maxByOrNull { it.changePercent!! }?.takeIf { it.changePercent!! > 0 }?.let { result += "${it.symbol} had the largest gain (${HomePresentation.percent(it.changePercent)})." }
            fresh.minByOrNull { it.changePercent!! }?.takeIf { it.changePercent!! < 0 }?.let { result += "${it.symbol} had the largest decline (${HomePresentation.percent(it.changePercent)})." }
        }
        val today = data?.session?.sessionDate
        val symbols = list.entries.map { it.instrument.symbol }.toSet()
        data?.earnings.orEmpty().filter { it.symbol in symbols && within(it.date, today, 14) }.sortedBy { it.date }.take(2).forEach { earnings ->
            val name = quotes[earnings.symbol]?.name ?: earnings.symbol
            result += "$name reports earnings ${dayLabel(earnings.date)}${if (earnings.status == EarningsDateStatus.ESTIMATED) " (estimated date)" else ""}."
        }
        return result
    }

    private fun within(date: String, today: String?, days: Int): Boolean {
        if (today == null) return false
        val target = dayNumber(date) ?: return false
        val now = dayNumber(today) ?: return false
        return target - now in 0..days
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun dayLabel(date: String): String = runCatching { "on ${MONTHS[date.substring(5, 7).toInt() - 1]} ${date.substring(8, 10).toInt()}" }.getOrDefault(date)
    private fun dayNumber(date: String): Int? = MarketsPresenter.dayNumber(date)

    private fun epochSeconds(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() }
}

data class AlertCardModel(
    val id: String,
    val symbol: String,
    val name: String?,
    val title: String,
    val detail: String,
    val status: AlertStatus,
    val statusLabel: String,
    val waitingForCross: Boolean
)

data class AlertHistoryRow(val id: String, val symbol: String, val title: String, val body: String, val time: String, val delivery: String, val sourceUrl: String?)

enum class AlertFilter(val label: String) { ACTIVE("Active"), TRIGGERED("Triggered"), PAUSED("Paused") }

@OptIn(ExperimentalTime::class)
object AlertPresenter {
    fun title(rule: AlertRule): String = when (rule.type) {
        AlertType.PRICE_ABOVE -> "Price above ${money(rule.threshold, rule.currency)}"
        AlertType.PRICE_BELOW -> "Price below ${money(rule.threshold, rule.currency)}"
        AlertType.DAILY_MOVE -> "Daily move " + when (rule.direction ?: MoveDirection.EITHER) {
            MoveDirection.UP -> "up ${percent(rule.threshold)}"
            MoveDirection.DOWN -> "down ${percent(rule.threshold)}"
            MoveDirection.EITHER -> "of ±${percent(rule.threshold)}"
        }
        AlertType.EARNINGS -> "Earnings reminder · " + when (rule.earningsTiming ?: EarningsTiming.BOTH) {
            EarningsTiming.DAY_BEFORE -> "day before"
            EarningsTiming.DAY_OF -> "day of"
            EarningsTiming.BOTH -> "day before and day of"
        }
        AlertType.NEWS -> "Important company news"
    }

    fun cards(rules: List<AlertRule>, filter: AlertFilter, offsetMinutes: Int): List<AlertCardModel> = rules
        .filter { it.status.name == filter.name }
        .map { rule ->
            val waiting = rule.status == AlertStatus.ACTIVE && !rule.armed && (rule.type == AlertType.PRICE_ABOVE || rule.type == AlertType.PRICE_BELOW)
            AlertCardModel(
                id = rule.id,
                symbol = rule.instrument.symbol,
                name = rule.instrument.name,
                title = title(rule),
                detail = listOfNotNull(
                    "Created ${MarketsPresenter.dateLabel(rule.createdAt / 1000, offsetMinutes)}",
                    rule.lastTriggeredAt?.let { "last triggered ${MarketsPresenter.dateLabel(it / 1000, offsetMinutes)} at ${MarketsPresenter.timeLabel(it / 1000, offsetMinutes)} ET" },
                    if (rule.repeat == RepeatPolicy.REPEAT && rule.type != AlertType.DAILY_MOVE) "repeats" else null
                ).joinToString(" · "),
                status = rule.status,
                statusLabel = when {
                    waiting -> "Waiting for the price to cross"
                    rule.status == AlertStatus.ACTIVE -> "Active"
                    rule.status == AlertStatus.PAUSED -> "Paused"
                    else -> "Triggered"
                },
                waitingForCross = waiting
            )
        }

    fun history(events: List<AlertEvent>, offsetMinutes: Int): List<AlertHistoryRow> = events.map { event ->
        AlertHistoryRow(
            id = event.id,
            symbol = event.symbol,
            title = event.title,
            body = event.body,
            time = "${MarketsPresenter.dateLabel(event.triggeredAt / 1000, offsetMinutes)}, ${MarketsPresenter.timeLabel(event.triggeredAt / 1000, offsetMinutes)} ET",
            delivery = when (event.delivery) {
                DeliveryStatus.PENDING -> "Sending…"
                DeliveryStatus.ACCEPTED -> "Sent to your devices"
                DeliveryStatus.NO_DEVICES -> "Not sent: notifications aren't on for any device"
                DeliveryStatus.FAILED -> "Couldn't be sent"
                DeliveryStatus.SIMULATED -> "Simulated (sample mode)"
            },
            sourceUrl = event.sourceUrl?.takeIf { it.startsWith("https://") }
        )
    }

    private fun money(value: Double?, currency: String?): String = value?.let { HomePresentation.price(it, currency ?: "USD") + if ((currency ?: "USD") == "USD") " USD" else "" } ?: "—"
    private fun percent(value: Double?): String = value?.let { v -> val r = (v * 10).toLong(); "${r / 10}.${abs(r % 10)}%" } ?: "—"
}
