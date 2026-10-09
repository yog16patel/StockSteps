package org.example.stocksteps.userdata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant

/** Market data shared by the watchlist screen, alert validation and the evaluator (cached per symbol). */
class WatchMarketData(
    private val stocks: StockService,
    private val earnings: EarningsCalendarSource?,
    private val news: NewsService,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 2_048, name = "watch"),
    private val timeoutMillis: Long = 8_000
) : AlertMarketData {
    private class Box<T>(val value: T?)
    private val permits = Semaphore(6)

    override suspend fun quote(symbol: String): StockQuote? = cached("quote|$symbol", QUOTE_TTL) { stocks.getStock(symbol) }
    suspend fun profile(symbol: String): CompanyProfile? = cached("profile|$symbol", PROFILE_TTL) { stocks.getProfile(symbol) }
    override suspend fun currency(symbol: String): String? = profile(symbol)?.currency
    suspend fun upcomingEarnings(symbol: String): UpcomingEarnings? = earnings?.let { source -> cached("earnings|$symbol", EARNINGS_TTL) { source.upcoming(symbol) } }
    suspend fun recentEarningsResult(symbol: String): org.example.stocksteps.earnings.EarningsEvent? = earnings?.let { source -> cached("earnings-result|$symbol", RESULT_TTL) { source.recentResult(symbol) } }
    suspend fun companyNews(symbol: String): List<NewsArticle> = runCatching { news.companyFeed(symbol) }.getOrDefault(emptyList())

    private suspend fun <T> cached(key: String, ttl: Long, load: suspend () -> T?): T? =
        cache.getOrLoad(key, ttl, resultTtl = { if (it.value == null) FAILURE_TTL else ttl }) {
            Box(permits.withPermit {
                try { withTimeoutOrNull(timeoutMillis) { load() } } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    null
                }
            })
        }.value

    companion object {
        private const val QUOTE_TTL = 60_000L
        private const val PROFILE_TTL = 24 * 3_600_000L
        private const val EARNINGS_TTL = 12 * 3_600_000L
        /** Results arrive after the scheduled time; check more often than dates. */
        private const val RESULT_TTL = 30 * 60_000L
        private const val FAILURE_TTL = 60_000L
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class EvaluationReport(
    val startedAt: String,
    @kotlinx.serialization.EncodeDefault val skipped: Boolean = false,
    @kotlinx.serialization.EncodeDefault val rules: Int = 0,
    @kotlinx.serialization.EncodeDefault val symbols: Int = 0,
    @kotlinx.serialization.EncodeDefault val triggered: Int = 0,
    @kotlinx.serialization.EncodeDefault val duplicates: Int = 0,
    @kotlinx.serialization.EncodeDefault val armed: Int = 0,
    @kotlinx.serialization.EncodeDefault val stale: Int = 0,
    @kotlinx.serialization.EncodeDefault val dataErrors: Int = 0,
    @kotlinx.serialization.EncodeDefault val sent: Int = 0,
    @kotlinx.serialization.EncodeDefault val sendFailures: Int = 0
)

/**
 * Runs one alert pass (invoked by Cloud Scheduler in REAL; periodically in MOCK). Rules are grouped
 * by instrument so each symbol's quote, earnings date and news are fetched once per pass, whatever
 * the number of users. Triggers are recorded atomically under a deterministic event key, so
 * duplicate scheduler calls, concurrent instances and retries can't notify twice. Notifications go
 * through a durable outbox: a worker that crashes after recording a trigger leaves PENDING (or
 * lease-expired SENDING) items that the next pass delivers without creating a new event.
 */
class AlertEvaluator(
    private val store: UserDataStore,
    private val market: WatchMarketData,
    private val sender: PushSender,
    private val rules: AlertRules = AlertRules(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxRules: Int = 5_000,
    private val maxSymbols: Int = 500,
    private val sendBatch: Int = 500
) {
    private val running = Mutex()

    suspend fun run(): EvaluationReport {
        val started = clock.instant()
        if (!running.tryLock()) return EvaluationReport(started.toString(), skipped = true)
        try {
            return deliver(evaluate(started))
        } finally { running.unlock() }
    }

    private suspend fun evaluate(now: Instant): EvaluationReport {
        // Earnings rules moved to EarningsReminderService (Phase 4), which migrates them; never evaluated here.
        val active = store.activeAlerts(maxRules).filter { it.rule.type != AlertType.EARNINGS }
        val bySymbol = active.groupBy { it.rule.instrument.symbol }.entries.take(maxSymbols)
        var report = EvaluationReport(now.toString(), rules = active.size, symbols = bySymbol.size)
        val snapshots = coroutineScope {
            bySymbol.map { (symbol, owned) -> async { symbol to snapshot(symbol, owned.map { it.rule.type }.toSet()) } }.awaitAll()
        }.toMap()
        for ((symbol, owned) in bySymbol) {
            val data = snapshots.getValue(symbol)
            if (data == null) { report = report.copy(dataErrors = report.dataErrors + owned.size); continue }
            for ((uid, rule) in owned) {
                val recent = if (rule.type == AlertType.NEWS) store.history(uid, 30).filter { it.ruleId == rule.id && now.toEpochMilli() - it.triggeredAt < AlertRules.NEWS_WINDOW_MILLIS }.map { it.body } else emptyList()
                when (val decision = rules.decide(rule, data, now, recent)) {
                    AlertDecision.None -> Unit
                    AlertDecision.Stale -> report = report.copy(stale = report.stale + 1)
                    AlertDecision.Arm -> {
                        store.updateRule(uid, rule.id) { if (it.status == AlertStatus.ACTIVE && !it.armed) it.copy(armed = true) else it }
                        report = report.copy(armed = report.armed + 1)
                    }
                    is AlertDecision.Trigger -> {
                        val recorded = record(uid, rule, decision, now)
                        report = if (recorded) report.copy(triggered = report.triggered + 1) else report.copy(duplicates = report.duplicates + 1)
                    }
                }
            }
        }
        return report
    }

    private suspend fun snapshot(symbol: String, types: Set<AlertType>): InstrumentSnapshot? {
        val needsQuote = types.any { it == AlertType.PRICE_ABOVE || it == AlertType.PRICE_BELOW || it == AlertType.DAILY_MOVE }
        val quote = if (needsQuote) market.quote(symbol) else null
        if (needsQuote && quote == null && types.none { it == AlertType.EARNINGS || it == AlertType.NEWS }) return null
        return InstrumentSnapshot(
            symbol = symbol,
            name = market.profile(symbol)?.companyName ?: quote?.companyName,
            quote = quote,
            earnings = if (AlertType.EARNINGS in types) market.upcomingEarnings(symbol) else null,
            earningsResult = if (AlertType.EARNINGS in types) market.recentEarningsResult(symbol) else null,
            news = if (AlertType.NEWS in types) market.companyNews(symbol) else emptyList()
        )
    }

    private suspend fun record(uid: String, rule: AlertRule, trigger: AlertDecision.Trigger, now: Instant): Boolean {
        val devices = store.devices(uid)
        val event = AlertEvent(
            id = trigger.eventKey,
            ruleId = rule.id,
            symbol = rule.instrument.symbol,
            type = rule.type,
            title = trigger.title,
            body = trigger.body,
            triggeredAt = now.toEpochMilli(),
            observedValue = trigger.observedValue,
            sessionDate = trigger.sessionDate,
            sourceUrl = trigger.sourceUrl,
            delivery = if (devices.isEmpty()) DeliveryStatus.NO_DEVICES else DeliveryStatus.PENDING
        )
        val data = mapOf("type" to "alert", "symbol" to rule.instrument.symbol, "eventId" to event.id, "link" to "stocksteps://alerts/${rule.instrument.symbol}")
        val outbox = devices.map { device ->
            OutboxItem("${event.id}|${device.deviceId}", uid, event.id, device.deviceId, device.token, trigger.title, trigger.body, data, createdAt = now.toEpochMilli())
        }
        return store.recordTrigger(uid, event, trigger.update, outbox)
    }

    /** Sends due outbox items (new ones and those a crashed worker left behind). */
    private suspend fun deliver(report: EvaluationReport): EvaluationReport {
        val now = clock.millis()
        var sent = 0
        var failed = 0
        val touched = mutableSetOf<Pair<String, String>>()
        for (due in store.dueOutbox(now, sendBatch)) {
            val item = store.claimOutbox(due.id, now, LEASE_MILLIS) ?: continue
            touched += item.uid to item.eventId
            when (val result = sender.send(PushMessage(item.token, item.title, item.body, item.data))) {
                is PushResult.Accepted, PushResult.Simulated -> { store.finishOutbox(item.id, OutboxStatus.ACCEPTED, null, retryable = false); sent++ }
                PushResult.InvalidToken -> { store.finishOutbox(item.id, OutboxStatus.INVALID_TOKEN, "Token no longer valid", retryable = false); store.removeToken(item.token); failed++ }
                is PushResult.Retryable -> { store.finishOutbox(item.id, OutboxStatus.FAILED, result.reason, retryable = item.attempts < MAX_ATTEMPTS); failed++ }
                is PushResult.Failed -> { store.finishOutbox(item.id, OutboxStatus.FAILED, result.reason, retryable = false); failed++ }
            }
        }
        for ((uid, eventId) in touched) {
            val items = store.outboxForEvent(eventId)
            val simulated = sender is SimulatedPushSender
            val status = when {
                items.any { it.status == OutboxStatus.ACCEPTED } -> if (simulated) DeliveryStatus.SIMULATED else DeliveryStatus.ACCEPTED
                items.any { it.status == OutboxStatus.PENDING || it.status == OutboxStatus.SENDING } -> DeliveryStatus.PENDING
                else -> DeliveryStatus.FAILED
            }
            store.setEventDelivery(uid, eventId, status)
        }
        return report.copy(sent = sent, sendFailures = failed)
    }

    companion object {
        const val LEASE_MILLIS = 60_000L
        const val MAX_ATTEMPTS = 5
    }
}
