package org.example.stocksteps.userdata

import org.example.stocksteps.model.*
import org.example.stocksteps.userdata.WatchlistsService.Companion.fail
import java.util.Locale
import java.util.UUID

/** Current market facts needed to validate an alert (listing currency and latest quote). */
interface AlertMarketData {
    suspend fun quote(symbol: String): StockQuote?
    suspend fun currency(symbol: String): String?
}

/**
 * Alert rules for one signed-in user. Rules belong to the user and the instrument, not to a
 * watchlist row, so deleting a watchlist never deletes alerts. Price alerts whose condition is
 * already true at creation are refused with 409 CONDITION_ALREADY_MET until the user chooses to
 * be notified now or when the price next crosses the threshold — never a surprise notification.
 */
class AlertsService(
    private val store: UserDataStore,
    private val market: AlertMarketData,
    private val rules: AlertRules = AlertRules(),
    private val deliveryNote: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Server-side StockSteps+ check for advanced earnings reminder options. */
    private val isPlus: suspend (String) -> Boolean = { false }
) {
    /** Lead days, results and surprise options are StockSteps+; basic day-before/day-of stays free. */
    private suspend fun advancedEarnings(uid: String, leadDays: Int?, results: Boolean?, surprise: Double?): Triple<Int?, Boolean, Double?> {
        val advanced = leadDays != null && leadDays != 1 || results == true || surprise != null
        if (advanced && !isPlus(uid)) fail(403, "PLUS_REQUIRED", "Custom lead times and results notifications are part of StockSteps+.")
        if (leadDays != null && leadDays !in 1..7) fail(400, "INVALID_LEAD", "Choose 1–7 days before earnings.")
        if (surprise != null && (!surprise.isFinite() || surprise < 1 || surprise > 100)) fail(400, "INVALID_SURPRISE", "Choose a surprise threshold between 1% and 100%.")
        if (surprise != null && results != true) fail(400, "INVALID_SURPRISE", "A surprise threshold needs results notifications.")
        return Triple(leadDays, results == true, surprise)
    }

    suspend fun list(uid: String): AlertsResponse = AlertsResponse(
        alerts = store.updateAlerts(uid) { it to it }.sortedByDescending { it.createdAt },
        history = store.history(uid, HISTORY_LIMIT),
        deliveryNote = deliveryNote,
        limits = UserDataLimits.value
    )

    suspend fun create(uid: String, request: CreateAlertRequest): AlertsResponse {
        val instrument = WatchlistsService.validInstrument(request.instrument)
        val type = request.type
        var threshold = request.threshold
        var currency: String? = null
        var armed = true
        when (type) {
            AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> {
                threshold = validPrice(threshold)
                currency = request.currency?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{3}")) }
                    ?: fail(400, "CURRENCY_REQUIRED", "Choose the currency of the target price.")
                val listing = runCatching { market.currency(instrument.symbol) }.getOrNull() ?: instrument.currency
                if (listing != null && !listing.equals(currency, ignoreCase = true)) {
                    fail(400, "CURRENCY_MISMATCH", "${instrument.symbol} trades in $listing. Enter the target price in $listing.")
                }
                val price = runCatching { market.quote(instrument.symbol) }.getOrNull()?.price?.takeIf { it.isFinite() && it > 0 }
                // Without a current price the rule waits until it sees the price on the other side first.
                armed = if (price == null) false else if (!rules.priceConditionMet(type, threshold, price)) true else when (request.whenAlreadyMet) {
                    AlreadyMetChoice.NOTIFY_NOW -> true
                    AlreadyMetChoice.WAIT_FOR_CROSS -> false
                    null -> fail(409, "CONDITION_ALREADY_MET",
                        "${instrument.symbol} is already at ${AlertRules.money(price, currency)}, ${if (type == AlertType.PRICE_ABOVE) "above" else "below"} ${AlertRules.money(threshold, currency)}.")
                }
            }
            AlertType.DAILY_MOVE -> threshold = validPercent(threshold)
            AlertType.EARNINGS, AlertType.NEWS -> threshold = null
        }
        val (leadDays, results, surprise) = if (type == AlertType.EARNINGS)
            advancedEarnings(uid, request.earningsLeadDays, request.earningsResults, request.earningsSurprisePercent) else Triple(null, false, null)
        val time = now()
        val rule = AlertRule(
            id = newId(),
            instrument = instrument,
            type = type,
            threshold = threshold,
            currency = currency,
            direction = if (type == AlertType.DAILY_MOVE) request.direction ?: MoveDirection.EITHER else null,
            earningsTiming = if (type == AlertType.EARNINGS) request.earningsTiming ?: EarningsTiming.BOTH else null,
            earningsLeadDays = leadDays,
            earningsResults = results,
            earningsSurprisePercent = surprise,
            repeat = request.repeat ?: if (type == AlertType.DAILY_MOVE) RepeatPolicy.REPEAT else RepeatPolicy.ONCE,
            status = AlertStatus.ACTIVE,
            armed = armed,
            createdAt = time,
            updatedAt = time
        )
        store.updateAlerts(uid) { current ->
            if (current.size >= UserDataLimits.MAX_ALERTS) fail(409, "ALERT_LIMIT", "You can have up to ${UserDataLimits.MAX_ALERTS} alerts.")
            if (current.any { it.status != AlertStatus.TRIGGERED && it.sameCondition(rule) }) fail(409, "DUPLICATE_ALERT", "You already have this alert.")
            (current + rule) to Unit
        }
        return list(uid)
    }

    suspend fun update(uid: String, id: String, request: UpdateAlertRequest): AlertsResponse {
        val newThreshold = request.threshold
        val existing = store.updateAlerts(uid) { it to it }.firstOrNull { it.id == id } ?: fail(404, "ALERT_NOT_FOUND", "That alert doesn't exist.")
        // A changed price threshold (or a resumed price alert) waits for a fresh cross when the condition already holds.
        val priceType = existing.type == AlertType.PRICE_ABOVE || existing.type == AlertType.PRICE_BELOW
        val validated = newThreshold?.let { if (priceType) validPrice(it) else if (existing.type == AlertType.DAILY_MOVE) validPercent(it) else null }
        val rearm = priceType && (validated != null || request.status == AlertStatus.ACTIVE)
        val currentPrice = if (rearm) runCatching { market.quote(existing.instrument.symbol) }.getOrNull()?.price else null
        val earningsOptions = if (existing.type == AlertType.EARNINGS && (request.earningsLeadDays != null || request.earningsResults != null || request.earningsSurprisePercent != null))
            advancedEarnings(uid, request.earningsLeadDays ?: existing.earningsLeadDays, request.earningsResults ?: existing.earningsResults,
                request.earningsSurprisePercent ?: existing.earningsSurprisePercent.takeIf { request.earningsResults != false })
            else null
        store.updateAlerts(uid) { current ->
            val rule = current.firstOrNull { it.id == id } ?: fail(404, "ALERT_NOT_FOUND", "That alert doesn't exist.")
            if (request.status == AlertStatus.TRIGGERED) fail(400, "INVALID_STATUS", "Alerts can be resumed or paused.")
            val threshold = validated ?: rule.threshold
            var next = rule.copy(
                status = request.status ?: rule.status,
                threshold = threshold,
                direction = if (rule.type == AlertType.DAILY_MOVE) request.direction ?: rule.direction else rule.direction,
                earningsTiming = if (rule.type == AlertType.EARNINGS) request.earningsTiming ?: rule.earningsTiming else rule.earningsTiming,
                earningsLeadDays = earningsOptions?.first ?: rule.earningsLeadDays,
                earningsResults = earningsOptions?.second ?: rule.earningsResults,
                earningsSurprisePercent = if (earningsOptions != null) earningsOptions.third else rule.earningsSurprisePercent,
                repeat = request.repeat ?: rule.repeat,
                updatedAt = now()
            )
            if (rearm && threshold != null) {
                next = next.copy(armed = currentPrice?.let { !rules.priceConditionMet(rule.type, threshold, it) } ?: false)
            }
            if (current.any { it.id != id && it.status != AlertStatus.TRIGGERED && it.sameCondition(next) }) fail(409, "DUPLICATE_ALERT", "You already have this alert.")
            current.map { if (it.id == id) next else it } to Unit
        }
        return list(uid)
    }

    suspend fun delete(uid: String, id: String): AlertsResponse {
        store.updateAlerts(uid) { current -> current.filter { it.id != id } to Unit }
        return list(uid)
    }

    private fun AlertRule.sameCondition(other: AlertRule) = instrument.symbol == other.instrument.symbol && type == other.type &&
        threshold == other.threshold && direction == other.direction && earningsTiming == other.earningsTiming

    companion object {
        const val HISTORY_LIMIT = 50
        fun validPrice(value: Double?): Double = value?.takeIf { it.isFinite() && it > 0 && it < 10_000_000 }
            ?: fail(400, "INVALID_THRESHOLD", "Enter a target price greater than zero.")
        fun validPercent(value: Double?): Double = value?.takeIf { it.isFinite() && it >= 0.5 && it <= 50 }
            ?: fail(400, "INVALID_THRESHOLD", "Enter a move between 0.5% and 50%.")
    }
}
