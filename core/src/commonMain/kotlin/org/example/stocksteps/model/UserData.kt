package org.example.stocksteps.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * A listed security. [symbol] is the provider symbol, which is exchange-qualified outside the US
 * (SHOP.TO vs SHOP), so it is the canonical identity; exchange and currency are kept with it.
 */
@Serializable
data class InstrumentRef(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val currency: String? = null
)

/** A stock in one watchlist. Notes are per entry: the same company can be watched for different reasons. */
@Serializable
data class WatchlistEntry(
    val id: String,
    val instrument: InstrumentRef,
    val order: Int,
    val addedAt: Long,
    val note: String? = null,
    val noteUpdatedAt: Long? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Watchlist(
    val id: String,
    val name: String,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
    @EncodeDefault val isDefault: Boolean = false,
    @EncodeDefault val entries: List<WatchlistEntry> = emptyList()
)

@Serializable
data class WatchlistLimits(val maxWatchlists: Int, val maxEntries: Int, val maxNameLength: Int, val maxNoteLength: Int, val maxAlerts: Int)

/** The signed-in user's watchlists, ordered; every mutation returns the full new state. */
@Serializable
data class WatchlistsResponse(val watchlists: List<Watchlist>, val limits: WatchlistLimits)

@Serializable data class CreateWatchlistRequest(val name: String)
@Serializable data class RenameWatchlistRequest(val name: String)
@Serializable data class AddEntryRequest(val instrument: InstrumentRef)
/** `note = null` deletes the note. */
@Serializable data class UpdateEntryRequest(val note: String? = null)
/** Moves (or copies, when [copy]) an entry, with its note, to another watchlist. */
@Serializable data class MoveEntryRequest(val targetWatchlistId: String, val copy: Boolean = false)
@Serializable data class ReorderRequest(val ids: List<String>)
/** One-time import of stocks saved before sign-in (guest list) into the default watchlist. */
@Serializable data class ImportEntriesRequest(val instruments: List<InstrumentRef>)

// --- Market data for watched stocks (public, not stored with user data) ---

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WatchQuote(
    val symbol: String,
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val previousClose: Double? = null,
    val currency: String? = null,
    /** When the quote was last updated (ISO-8601). */
    val asOf: String? = null,
    /** Exchange-local trading date of the quote. */
    val sessionDate: String? = null,
    /** True when the quote is from an earlier session than the latest one (or older than the freshness limit). */
    @EncodeDefault val stale: Boolean = false,
    val logoUrl: String? = null
)

@Serializable enum class EarningsTime { BEFORE_OPEN, AFTER_CLOSE, DURING_MARKET, UNKNOWN }

/**
 * CONFIRMED only when the source says the company confirmed it; provider calendar dates are
 * otherwise ESTIMATED. TENTATIVE: announced but subject to change; UNKNOWN: no date information.
 */
@Serializable enum class EarningsDateStatus { CONFIRMED, ESTIMATED, TENTATIVE, UNKNOWN }

@Serializable
data class UpcomingEarnings(
    val symbol: String,
    /** Exchange-local date (yyyy-MM-dd). */
    val date: String,
    val time: EarningsTime = EarningsTime.UNKNOWN,
    val status: EarningsDateStatus = EarningsDateStatus.ESTIMATED,
    val source: String,
    /** Stable fiscal-period identity ("SYMBOL:2026-Q3"); reminders key on it, so a moved date never re-notifies. */
    val eventId: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WatchDataResponse(
    @EncodeDefault val quotes: List<WatchQuote> = emptyList(),
    @EncodeDefault val earnings: List<UpcomingEarnings> = emptyList(),
    val session: MarketSession,
    val generatedAt: String,
    val notice: String
)

// --- Alerts ---

@Serializable enum class AlertType { PRICE_ABOVE, PRICE_BELOW, DAILY_MOVE, EARNINGS, NEWS }
@Serializable enum class MoveDirection { UP, DOWN, EITHER }
@Serializable enum class EarningsTiming { DAY_BEFORE, DAY_OF, BOTH }

/** ONCE: price alerts stop after triggering. REPEAT: price alerts re-arm after the price moves back. Daily moves fire at most once per session either way. */
@Serializable enum class RepeatPolicy { ONCE, REPEAT }

/** ACTIVE = being checked; PAUSED = kept but not checked; TRIGGERED = a one-time alert that already fired. */
@Serializable enum class AlertStatus { ACTIVE, PAUSED, TRIGGERED }

/** What to do when a price alert's condition is already true when it is created. */
@Serializable enum class AlreadyMetChoice { NOTIFY_NOW, WAIT_FOR_CROSS }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AlertRule(
    val id: String,
    val instrument: InstrumentRef,
    val type: AlertType,
    /** Price (in [currency]) for price alerts; percent for daily moves. */
    val threshold: Double? = null,
    val currency: String? = null,
    val direction: MoveDirection? = null,
    val earningsTiming: EarningsTiming? = null,
    @EncodeDefault val repeat: RepeatPolicy = RepeatPolicy.ONCE,
    val status: AlertStatus,
    /** Price alerts: false until the price is seen on the other side of the threshold. */
    @EncodeDefault val armed: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTriggeredAt: Long? = null,
    @EncodeDefault val triggerCount: Int = 0,
    /** StockSteps+: remind this many days before (2–7) instead of the day before. */
    val earningsLeadDays: Int? = null,
    /** StockSteps+: notify once results for the event are reported. */
    @EncodeDefault val earningsResults: Boolean = false,
    /** StockSteps+: with [earningsResults], only notify when |EPS or revenue surprise| ≥ this percent. */
    val earningsSurprisePercent: Double? = null
)

@Serializable
data class CreateAlertRequest(
    val instrument: InstrumentRef,
    val type: AlertType,
    val threshold: Double? = null,
    val currency: String? = null,
    val direction: MoveDirection? = null,
    val earningsTiming: EarningsTiming? = null,
    val earningsLeadDays: Int? = null,
    val earningsResults: Boolean? = null,
    val earningsSurprisePercent: Double? = null,
    val repeat: RepeatPolicy? = null,
    /** Required when a price condition is already met at creation (otherwise 409 CONDITION_ALREADY_MET). */
    val whenAlreadyMet: AlreadyMetChoice? = null
)

@Serializable
data class UpdateAlertRequest(
    /** ACTIVE resumes (and re-arms a triggered alert); PAUSED pauses. */
    val status: AlertStatus? = null,
    val threshold: Double? = null,
    val direction: MoveDirection? = null,
    val earningsTiming: EarningsTiming? = null,
    val earningsLeadDays: Int? = null,
    val earningsResults: Boolean? = null,
    val earningsSurprisePercent: Double? = null,
    val repeat: RepeatPolicy? = null
)

/** How far a notification got. ACCEPTED means FCM accepted it, not that the user saw it. */
@Serializable enum class DeliveryStatus { PENDING, ACCEPTED, NO_DEVICES, FAILED, SIMULATED }

@Serializable
data class AlertEvent(
    /** Deterministic idempotency key (rule + session/cycle/event). */
    val id: String,
    val ruleId: String,
    val symbol: String,
    val type: AlertType,
    val title: String,
    val body: String,
    val triggeredAt: Long,
    val observedValue: Double? = null,
    val sessionDate: String? = null,
    val sourceUrl: String? = null,
    val delivery: DeliveryStatus
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AlertsResponse(
    @EncodeDefault val alerts: List<AlertRule> = emptyList(),
    /** Most recent first. */
    @EncodeDefault val history: List<AlertEvent> = emptyList(),
    /** How often alerts are checked and how fresh prices are, in plain words. */
    val deliveryNote: String,
    val limits: WatchlistLimits
)

@Serializable data class RegisterDeviceRequest(val deviceId: String, val token: String, val platform: String)
