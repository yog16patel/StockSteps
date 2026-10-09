package org.example.stocksteps.earnings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.portfolio.Decimal

// Earnings Intelligence Lite, Phase 3: post-earnings price reaction. The server selects the baseline
// and endpoint (exchange calendar, announcement timing) and calculates the change exactly; the apps
// only format the result. Nothing here claims why a price moved.

/**
 * Measurement windows in regular trading sessions (never calendar days). Session 1 is the first
 * regular session whose close reflects the announcement, so "1 trading day" is the same as the
 * first session and isn't offered separately.
 */
@Serializable enum class ReactionWindow(val sessions: Int, val label: String) {
    FIRST_SESSION(1, "First Session"), THREE_SESSIONS(3, "3 Sessions"), FIVE_SESSIONS(5, "5 Sessions")
}

@Serializable enum class ReactionStatus {
    AVAILABLE,
    /** Announcement time unknown: a broader, labelled comparison may still be shown. */
    EVENT_TIME_UNKNOWN,
    BASELINE_UNAVAILABLE, ENDPOINT_UNAVAILABLE,
    /** The selected number of sessions hasn't passed yet. */
    WINDOW_INCOMPLETE,
    CORPORATE_ACTION_AMBIGUITY, DATA_NOT_COMPARABLE, PROVIDER_UNAVAILABLE
}

@Serializable enum class PriceSessionType { REGULAR_CLOSE, EXTENDED_HOURS }

@Serializable enum class PriceAdjustment(val label: String) {
    SPLIT_ADJUSTED("Split-adjusted"), UNADJUSTED("Not adjusted"), TOTAL_RETURN("Adjusted for splits and dividends")
}

/** One price observation. [price] is an exact decimal string in [currency]. */
@Serializable
data class PriceObservation(
    /** Exchange-local trading-session date (yyyy-MM-dd). */
    val sessionDate: String,
    /** When the observation applies (the session's scheduled close, with offset). */
    val timestamp: String? = null,
    val price: String,
    val currency: String? = null,
    val sessionType: PriceSessionType = PriceSessionType.REGULAR_CLOSE,
    val adjustment: PriceAdjustment = PriceAdjustment.SPLIT_ADJUSTED,
    val source: String,
    /** True when the session closed early (half day). */
    val earlyClose: Boolean = false
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsPriceReaction(
    /** "SYMBOL:YYYY-Qn:WINDOW". */
    val reactionId: String,
    val reportId: String,
    val eventId: String,
    val instrumentId: String,
    val exchange: String? = null,
    /** Exchange-local announcement date. */
    val eventDate: String,
    /** Source-stated time ("16:05") and zone, when known. */
    val eventTime: String? = null,
    val eventTimeZone: String? = null,
    val timing: EarningsTime = EarningsTime.UNKNOWN,
    val window: ReactionWindow,
    val status: ReactionStatus,
    /** Plain-language reason for any status other than AVAILABLE. */
    val statusMessage: String? = null,
    val baseline: PriceObservation? = null,
    val endpoint: PriceObservation? = null,
    /** Exact decimal strings; null unless both observations are valid and comparable. */
    val absoluteChange: String? = null,
    val percentChange: String? = null,
    val currency: String? = null,
    val adjustment: PriceAdjustment? = null,
    /** "Close on Oct 6 (before the after-market announcement) → close on Oct 7 (first session after)". */
    val measurement: String? = null,
    /** Sessions completed so far in this window (≤ window.sessions). */
    val sessionsObserved: Int = 0,
    @EncodeDefault val warnings: List<String> = emptyList(),
    @EncodeDefault val sources: List<String> = emptyList(),
    val calculatedAt: String,
    val freshness: DataFreshness = DataFreshness.FRESH,
    /** Extended-hours prices aren't offered unless a source provides reliable timestamped data. */
    val extendedHoursAvailable: Boolean = false
) {
    /** A comparison worth showing (precise, or the labelled broader one for unknown timing). */
    val hasChange: Boolean get() = absoluteChange != null && percentChange != null
}

/** One chart slot per regular session; [close] is null when the session has no price (shown as a gap). */
@Serializable
data class PriceHistoryPoint(val date: String, val close: String? = null, val earlyClose: Boolean = false)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsPriceHistory(
    val reportId: String,
    @EncodeDefault val points: List<PriceHistoryPoint> = emptyList(),
    val eventDate: String,
    /** The slot the earnings marker belongs to (the first session whose close reflects the news). */
    val eventSlotDate: String? = null,
    /** "Earnings: Tue, Oct 6, after market close". */
    val eventLabel: String,
    val currency: String? = null,
    val adjustment: PriceAdjustment? = null,
    @EncodeDefault val missingSessions: List<String> = emptyList(),
    val source: String? = null,
    val freshness: DataFreshness = DataFreshness.FRESH
)

/** Window availability, so incomplete windows can be shown as "Not available yet". */
@Serializable data class ReactionWindowOption(val window: ReactionWindow, val status: ReactionStatus)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsPriceReactionResponse(
    val reaction: EarningsPriceReaction,
    val history: EarningsPriceHistory,
    @EncodeDefault val windows: List<ReactionWindowOption> = emptyList(),
    /** Phase 2 classifications for the same report (reused, never recalculated here). */
    val eps: Classification = Classification.UNAVAILABLE,
    val revenue: Classification = Classification.UNAVAILABLE,
    /** Deterministic, non-causal beginner explanation of results + price change. */
    val explanation: String,
    /** "EPS and revenue tell different parts of the story…" when they disagree; else null. */
    val mixedNote: String? = null,
    @EncodeDefault val learn: List<String> = emptyList(),
    val sampleData: Boolean = false
)

/**
 * Deterministic beginner explanations (no AI). Describes what happened during the window; never a
 * cause, a prediction or advice.
 */
object PriceReactionExplainer {
    const val NO_PRICE = "Reliable price observations are unavailable for this measurement window."
    const val INCOMPLETE = "The selected number of trading sessions has not yet passed."
    const val NO_ESTIMATES = "Comparable analyst estimates are unavailable, so the results cannot be classified reliably as a beat or miss."
    const val MIXED = "EPS and revenue results tell different parts of the story. A company can exceed one expectation while missing another."

    /** BEAT, MISS, MIXED or null (no comparable estimates) from the Phase 2 classifications. */
    fun overall(eps: Classification, revenue: Classification): String? {
        val set = setOf(eps, revenue) - Classification.UNAVAILABLE
        return when {
            set.isEmpty() -> null
            Classification.BEAT in set && Classification.MISS in set -> "MIXED"
            Classification.BEAT in set -> "BEAT"
            Classification.MISS in set -> "MISS"
            else -> "MET"
        }
    }

    fun explanation(reaction: EarningsPriceReaction, eps: Classification, revenue: Classification): String {
        if (reaction.status == ReactionStatus.WINDOW_INCOMPLETE) return INCOMPLETE
        val pct = EarningsMath.decimal(reaction.percentChange)
        if (!reaction.hasChange || pct == null) return NO_PRICE
        val up = pct > Decimal.ZERO
        val flat = pct == Decimal.ZERO
        val price = when {
            flat -> "The stock price was unchanged over the selected window."
            up -> "The stock increased after the earnings announcement."
            else -> "The stock price declined during the selected period."
        }
        val text = when (overall(eps, revenue)) {
            null -> "$price $NO_ESTIMATES"
            "BEAT" -> when {
                flat -> "The company exceeded some earnings expectations, and its stock price was unchanged over the selected window. Prices reflect many factors besides the latest results."
                up -> "The stock increased after the earnings announcement. Positive results may influence investor expectations, but other factors can also affect the price."
                else -> "The company exceeded some earnings expectations, but its stock price declined. Investors may consider future guidance, valuation, other financial results, and broader market conditions. The available data does not prove which factor caused the decline."
            }
            "MISS" -> when {
                flat -> "The company reported results below some expectations, and its stock price was unchanged over the selected window. Prices reflect many factors besides the latest results."
                up -> "The company reported results below some expectations, yet its share price increased. Stock prices reflect expectations about the future, not only the latest reported numbers."
                else -> "The company reported results below expectations, and its share price declined during the selected period. This does not establish that the earnings miss was the only cause."
            }
            "MIXED" -> "$price These observations do not establish which factors caused the price movement."
            else -> "The company's results matched analyst expectations. $price Prices reflect many factors besides the latest results."
        }
        return if (reaction.status == ReactionStatus.EVENT_TIME_UNKNOWN) "$text The announcement time isn't known, so this compares a broader window." else text
    }

    fun mixedNote(eps: Classification, revenue: Classification) = if (overall(eps, revenue) == "MIXED") MIXED else null

    /** The neutral one-line summary used on the price card. */
    fun summary(reaction: EarningsPriceReaction): String? {
        val pct = EarningsMath.decimal(reaction.percentChange) ?: return null
        val shown = EarningsResultsFormat.percent(reaction.percentChange)!!.removePrefix("+").removePrefix("−")
        val verb = when { pct > Decimal.ZERO -> "increased by $shown"; pct < Decimal.ZERO -> "decreased by $shown"; else -> "was unchanged" }
        return "The stock price $verb between the selected observations around the earnings announcement. This change may reflect earnings news and other market factors."
    }

    /** Learn links (existing lessons first; the rest are EarningsEducation topics). */
    val learnTopics = listOf("fall-after-beat", "reaction", "expectations", "after-hours", "volatility")
}

// ---------- Presentation (shared by Android and iOS; formatting only) ----------

data class ReactionChartView(
    val values: List<Double?>,
    val details: List<String>,
    val xLabels: List<String>,
    val yLabels: List<String>,
    /** Slot indexes for the earnings marker, baseline and endpoint (−1 when absent). */
    val eventIndex: Int,
    val baselineIndex: Int,
    val endpointIndex: Int,
    val eventLabel: String,
    val description: String,
    val missingNote: String?
)

data class EarningsPriceReactionState(
    val reportId: String,
    val window: ReactionWindow = ReactionWindow.FIRST_SESSION,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** A request failure (retryable), as opposed to an honest "unavailable" status. */
    val error: String? = null,
    val response: EarningsPriceReactionResponse? = null,
    val windows: List<ReactionWindowOption> = emptyList(),
    val lines: List<MetricLine> = emptyList(),
    val summary: String? = null,
    val explanation: String? = null,
    val statusText: String? = null,
    val contextLine: String? = null,
    val mixedNote: String? = null,
    val warnings: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val chart: ReactionChartView? = null,
    val expandedChart: Boolean = false,
    val stale: Boolean = false,
    val offline: Boolean = false,
    val learn: List<LearnLink> = emptyList(),
    val accessibility: String = ""
) {
    val available: Boolean get() = response?.reaction?.hasChange == true
}

object PriceReactionFormat {
    private fun money(text: String?, currency: String?) = EarningsResultsFormat.eps(text, currency)
    private fun dateText(d: String) = EarningsFormatter.date(d)
    fun timing(r: EarningsPriceReaction) = when (r.timing) {
        EarningsTime.BEFORE_OPEN -> "before market open"; EarningsTime.AFTER_CLOSE -> "after market close"
        EarningsTime.DURING_MARKET -> "during market hours"; EarningsTime.UNKNOWN -> "time not confirmed"
    } + (r.eventTime?.let { " (${it} ${if (r.eventTimeZone == "America/Toronto" || r.eventTimeZone == "America/New_York") "ET" else r.eventTimeZone.orEmpty()})".replace(" )", ")") } ?: "")

    private fun observation(o: PriceObservation) = dateText(o.sessionDate) + ", " + (if (o.sessionType == PriceSessionType.REGULAR_CLOSE) "regular-session close" else "extended hours") +
        (if (o.earlyClose) " (early close)" else "")

    fun chart(h: EarningsPriceHistory, r: EarningsPriceReaction): ReactionChartView? {
        if (h.points.none { it.close != null }) return null
        val values = h.points.map { EarningsMath.decimal(it.close)?.toString()?.toDouble() }
        val valid = values.filterNotNull()
        val details = h.points.map { p ->
            val tag = when (p.date) { r.baseline?.sessionDate -> " · baseline"; r.endpoint?.sessionDate -> " · endpoint"; else -> "" } +
                (if (p.date == h.eventSlotDate) " · earnings" else "")
            dateText(p.date) + ": " + (p.close?.let { money(it, h.currency) } ?: "no price") + tag
        }
        val missing = h.missingSessions.takeIf { it.isNotEmpty() }?.let { "No price for " + it.joinToString { d -> dateText(d) } + " (data missing or trading halted)." }
        val description = buildString {
            append("Daily closing prices from ${dateText(h.points.first().date)} to ${dateText(h.points.last().date)}. ${h.eventLabel}. ")
            if (r.hasChange) append("Baseline ${dateText(r.baseline!!.sessionDate)} ${money(r.baseline.price, r.currency)}, endpoint ${dateText(r.endpoint!!.sessionDate)} ${money(r.endpoint.price, r.currency)}, change ${money(r.absoluteChange, r.currency)}, ${EarningsResultsFormat.percent(r.percentChange)}. Window: ${r.window.label}.")
            missing?.let { append(" $it") }
        }
        return ReactionChartView(values, details, listOf(dateText(h.points.first().date).substringAfter(", "), dateText(h.points.last().date).substringAfter(", ")),
            listOf(money(valid.max().toString(), h.currency), money(valid.min().toString(), h.currency)),
            h.points.indexOfFirst { it.date == h.eventSlotDate }, h.points.indexOfFirst { it.date == r.baseline?.sessionDate },
            h.points.indexOfFirst { it.date == r.endpoint?.sessionDate }, h.eventLabel, description, missing)
    }

    fun state(base: EarningsPriceReactionState, response: EarningsPriceReactionResponse): EarningsPriceReactionState {
        val r = response.reaction
        val lines = if (r.hasChange) listOf(
            MetricLine("Before earnings", money(r.baseline!!.price, r.currency), observation(r.baseline)),
            MetricLine("After earnings", money(r.endpoint!!.price, r.currency), observation(r.endpoint)),
            MetricLine("Price change", (if (EarningsMath.decimal(r.absoluteChange)!! > Decimal.ZERO) "+" else "") + money(r.absoluteChange, r.currency)),
            MetricLine("Reaction", EarningsResultsFormat.percent(r.percentChange)!!),
            MetricLine("Measured", r.window.label, r.measurement)
        ) else emptyList()
        val context = listOf("EPS: ${response.eps.label.takeIf { response.eps != Classification.UNAVAILABLE } ?: "No comparable estimate"}",
            "Revenue: ${response.revenue.label.takeIf { response.revenue != Classification.UNAVAILABLE } ?: "No comparable estimate"}",
            "Stock reaction: ${EarningsResultsFormat.percent(r.percentChange) ?: "unavailable"}").joinToString(" · ")
        val statusText = when (r.status) {
            ReactionStatus.AVAILABLE -> null
            ReactionStatus.WINDOW_INCOMPLETE -> "Not available yet. " + (r.statusMessage ?: PriceReactionExplainer.INCOMPLETE)
            else -> r.statusMessage
        }
        val learn = PriceReactionExplainer.learnTopics.mapNotNull { EarningsEducation.topic(it) }.map { LearnLink(it.title, it.body) }
        return base.copy(response = response, window = r.window, windows = response.windows, lines = lines,
            summary = PriceReactionExplainer.summary(r), explanation = response.explanation, statusText = statusText, contextLine = context,
            mixedNote = response.mixedNote, warnings = r.warnings, sources = r.sources, chart = chart(response.history, r),
            stale = r.freshness == DataFreshness.STALE, learn = learn,
            accessibility = "How did the stock react? " + (if (r.hasChange) lines.joinToString(" ") { "${it.label}: ${it.value}." } else statusText.orEmpty()) + " $context.")
    }
}

/** Remote access to the price reaction (part of [EarningsRemote] implementations). */
interface PriceReactionRemote {
    suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse
}

/**
 * The price reaction section for one report. Window changes reload from the server (which owns the
 * policy); obsolete loads are cancelled. Successful responses are saved per window for offline use.
 */
class EarningsPriceReactionPresenter(
    val reportId: String,
    private val remote: PriceReactionRemote,
    private val scope: CoroutineScope,
    private val cache: EarningsResultsCache? = null,
    initialWindow: ReactionWindow = ReactionWindow.FIRST_SESSION,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(EarningsPriceReactionState(reportId, initialWindow))
    val state: StateFlow<EarningsPriceReactionState> = mutable.asStateFlow()
    private val request = MutableStateFlow(initialWindow to 0)

    init { scope.launch { request.collectLatest { (w, r) -> load(w, r > 0) } } }

    private fun key(w: ReactionWindow) = "$reportId:price:${w.name}"

    private suspend fun load(window: ReactionWindow, refresh: Boolean) {
        mutable.update { it.copy(window = window, loading = it.response == null || it.response.reaction.window != window, refreshing = refresh, error = null) }
        try {
            val response = remote.priceReaction(reportId, window)
            mutable.update { PriceReactionFormat.state(it.copy(loading = false, refreshing = false, offline = false), response) }
            runCatching { cache?.write(key(window), json.encodeToString(EarningsPriceReactionResponse.serializer(), response), now()) }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            val api = cause as? org.example.stocksteps.network.StockStepsApiException
            val saved = runCatching { cache?.read(key(window)) }.getOrNull()?.let { (text, _) ->
                runCatching { json.decodeFromString(EarningsPriceReactionResponse.serializer(), text) }.getOrNull() }
            val message = api?.error?.message ?: "The price reaction couldn't be loaded. Check your connection and try again."
            mutable.update { s ->
                if (saved != null) PriceReactionFormat.state(s.copy(loading = false, refreshing = false, offline = true, error = "You're seeing a copy saved on this device. $message"), saved)
                else s.copy(loading = false, refreshing = false, error = message, response = null, lines = emptyList(), chart = null, summary = null, explanation = null, statusText = null)
            }
        }
    }

    fun selectWindow(window: ReactionWindow) { if (request.value.first != window) request.value = window to request.value.second }
    fun refresh() { request.update { it.first to it.second + 1 } }
    fun toggleChart() = mutable.update { it.copy(expandedChart = !it.expandedChart) }
    val currentWindow: ReactionWindow get() = request.value.first
}
