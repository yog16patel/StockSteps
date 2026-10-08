package org.example.stocksteps.practice

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.learning.Quiz
import org.example.stocksteps.portfolio.analytics.EntitlementStatus

/*
 * Practice Portfolio: an educational simulation with virtual money. Nothing here is a brokerage
 * order or real money. Every monetary value is a decimal string (never Double) and every
 * authoritative calculation runs on the backend with [PracticeEngine].
 */

// ---------- Access ----------

@Serializable enum class PracticeAccess { FREE, TRIAL, PLUS }
@Serializable enum class TrialStatus { NOT_STARTED, ACTIVE, EXPIRED, CONVERTED_TO_PLUS }

/** Typed premium capabilities; screens ask for a capability, never for "isPremium". */
@Serializable enum class PracticeCapability { UNLIMITED_HOLDINGS, FULL_HISTORY, ALLOCATION, ALL_CHALLENGES, PREMIUM_INSIGHTS }

/** Server-written trial record (one per account, never restarted by reset, reinstall or sign-out). */
@Serializable data class PracticeTrial(val startedAt: Long, val endsAt: Long, val createdAt: Long, val updatedAt: Long)

/** What the server decided for this user right now. Clients display it; the backend enforces it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PracticeEntitlement(
    val access: PracticeAccess,
    /** Null = unlimited. */
    val maxOpenHoldings: Int?,
    val trialStatus: TrialStatus,
    /** True only when this account has never started a Practice trial. */
    val trialEligible: Boolean,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    @EncodeDefault val capabilities: List<PracticeCapability> = emptyList(),
    val plusStatus: EntitlementStatus = EntitlementStatus.NONE,
    /** Backend time the decision was made at (UTC millis); countdowns use it, not the device clock. */
    val serverTime: Long = 0
) {
    fun has(capability: PracticeCapability) = capability in capabilities
    /** Whole days left in the trial (rounded up), or null when no trial is running. */
    val trialDaysLeft: Int? get() = trialEndsAt?.takeIf { trialStatus == TrialStatus.ACTIVE }?.let { ((it - serverTime + DAY - 1) / DAY).toInt().coerceAtLeast(0) }
    private companion object { const val DAY = 86_400_000L }
}

// ---------- Ledger (stored on the server) ----------

@Serializable enum class InstrumentKind { STOCK, ETF, UNKNOWN }

/**
 * Canonical instrument: [id] is the provider symbol in upper case, whose exchange suffix (".TO")
 * keeps listings with the same ticker apart; [exchange] and [currency] are recorded at first trade.
 */
@Serializable
data class PracticeInstrument(
    val id: String,
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val currency: String,
    val kind: InstrumentKind = InstrumentKind.UNKNOWN,
    val sector: String? = null,
    val logoUrl: String? = null
)

/** Only types the accounting model supports. Resets start a new generation instead of a ledger entry. */
@Serializable enum class PracticeTransactionType { BUY, SELL, DIVIDEND, SPLIT_ADJUSTMENT }

/**
 * Immutable simulated fill or corporate action.
 * - BUY/SELL: [quantity] shares at [price] (instrument currency) × [fxRate] (to base) = [amount] (base, cents).
 * - DIVIDEND: [quantity] eligible shares × [price] per share; [amount] is the base cash credited.
 * - SPLIT_ADJUSTMENT: [quantity] is the new-per-old ratio; no cash.
 */
@Serializable
data class PracticeTransaction(
    val id: String,
    val generation: Int,
    val type: PracticeTransactionType,
    val instrument: PracticeInstrument,
    val quantity: String,
    val price: String,
    val priceCurrency: String,
    val fxRate: String,
    val amount: String,
    val executedAt: Long,
    val idempotencyKey: String? = null,
    /** "Latest closing price", "Delayed quote", "Sample corporate action", … */
    val pricingSource: String,
    val quoteAsOf: String? = null,
    val fxAsOf: String? = null,
    /** Corporate-action event id, so an event is never applied twice. */
    val eventId: String? = null,
    val status: String = "FILLED",
    val sequence: Long = 0
)

@Serializable enum class PracticeStatus { ACTIVE }

@Serializable
data class PracticePortfolio(
    val id: String,
    val generation: Int,
    val baseCurrency: String = "CAD",
    val startingCash: String = PracticePolicy.STARTING_CASH,
    val createdAt: Long,
    val updatedAt: Long,
    val status: PracticeStatus = PracticeStatus.ACTIVE
)

/** A previous generation kept for the record when the user resets (archive, not deletion). */
@Serializable
data class PracticeArchive(val generation: Int, val createdAt: Long, val resetAt: Long, val finalCash: String, val transactions: List<PracticeTransaction>)

/** A processed mutating request: the same key and body replays the stored result; a different body is refused. */
@Serializable
data class IdempotencyRecord(val key: String, val operation: String, val requestHash: String, val resultId: String?, val createdAt: Long)

/** Everything the backend stores for one user, updated atomically. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PracticeAccountData(
    val portfolio: PracticePortfolio? = null,
    @EncodeDefault val transactions: List<PracticeTransaction> = emptyList(),
    @EncodeDefault val archives: List<PracticeArchive> = emptyList(),
    val trial: PracticeTrial? = null,
    /** Challenge id → completion time; kept across resets and plan changes. */
    @EncodeDefault val challenges: Map<String, Long> = emptyMap(),
    @EncodeDefault val idempotency: List<IdempotencyRecord> = emptyList(),
    val trialExpiredNoticeSeen: Boolean = false,
    val revision: Long = 0
)

// ---------- API views ----------

@Serializable enum class OrderSide { BUY, SELL }
@Serializable enum class ValuationStatus { COMPLETE, PARTIAL, UNAVAILABLE }

@Serializable
data class PracticeOrderRequest(
    val symbol: String,
    val side: OrderSide,
    /** Shares (up to 4 decimals). Exactly one of [quantity] or [amount]. */
    val quantity: String? = null,
    /** Virtual cash in the base currency; converted to whole 0.0001 shares at the server price. */
    val amount: String? = null,
    /** Required for execution; the same key never fills twice. */
    val idempotencyKey: String? = null,
    /** The price the user reviewed; a material move asks for a fresh review. */
    val reviewedPrice: String? = null
)

@Serializable data class PracticeBlocker(val code: String, val message: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PracticeOrderPreview(
    val instrument: PracticeInstrument,
    val side: OrderSide,
    val quantity: String,
    /** Indicative price per share in the instrument currency (the server sets the fill price). */
    val price: String,
    val priceCurrency: String,
    val fxRate: String,
    val fxAsOf: String? = null,
    val baseCurrency: String,
    val estimatedTotal: String,
    val cashAvailable: String,
    val cashAfter: String,
    val quoteAsOf: String? = null,
    /** How the price was chosen, e.g. "Latest closing price (market closed)". */
    val priceBasis: String,
    val ownedQuantity: String,
    val opensNewHolding: Boolean,
    val openHoldings: Int,
    val maxOpenHoldings: Int? = null,
    val blocker: PracticeBlocker? = null,
    val disclosure: String = PracticePolicy.DISCLOSURE
) {
    val canExecute: Boolean get() = blocker == null
}

@Serializable data class PracticeOrderResult(val transaction: PracticeTransaction, val cashAfter: String, val baseCurrency: String, val replayed: Boolean = false)

@Serializable
data class PracticeHoldingView(
    val instrument: PracticeInstrument,
    val quantity: String,
    /** Base currency per share (weighted average, includes the FX at each purchase). */
    val averageCost: String,
    val costBasis: String,
    val currentPrice: String? = null,
    val priceCurrency: String,
    val fxRate: String? = null,
    val marketValue: String? = null,
    val unrealizedGain: String? = null,
    val unrealizedPercent: String? = null,
    val realizedGain: String = "0",
    val quoteAsOf: String? = null,
    val stale: Boolean = false,
    /** Share of holdings value, only when the allocation capability is included. */
    val weightPercent: String? = null
)

@Serializable data class PracticeAllocationSlice(val label: String, val value: String, val percent: String)
@Serializable data class PracticeInsight(val id: String, val text: String, val premium: Boolean = false)

@Serializable enum class ChallengeKind { FIRST_BUY, QUIZ, REFLECTION }

@Serializable
data class PracticeChallengeView(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val kind: ChallengeKind,
    val lesson: List<String>,
    val quiz: Quiz? = null,
    val completedAt: Long? = null,
    /** False = preview only for this plan (completed ones always stay completed). */
    val available: Boolean
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PracticeOverview(
    val portfolioId: String,
    val generation: Int,
    val baseCurrency: String,
    val startingCash: String,
    val cash: String,
    val holdingsValue: String? = null,
    val totalValue: String? = null,
    /** Total value − starting cash (virtual cash isn't profit). */
    val totalGain: String? = null,
    val totalGainPercent: String? = null,
    val unrealizedGain: String? = null,
    val realizedGain: String = "0",
    val dividends: String = "0",
    val valuationStatus: ValuationStatus,
    @EncodeDefault val holdings: List<PracticeHoldingView> = emptyList(),
    val openHoldings: Int,
    val entitlement: PracticeEntitlement,
    @EncodeDefault val recentTransactions: List<PracticeTransaction> = emptyList(),
    /** Null when locked for this plan. */
    val allocation: List<PracticeAllocationSlice>? = null,
    val sectorAllocation: List<PracticeAllocationSlice>? = null,
    @EncodeDefault val insights: List<PracticeInsight> = emptyList(),
    @EncodeDefault val challenges: List<PracticeChallengeView> = emptyList(),
    @EncodeDefault val notices: List<String> = emptyList(),
    val createdAt: Long,
    val showTrialExpiredNotice: Boolean = false,
    val sampleData: Boolean = false
)

@Serializable enum class PracticeRange(val label: String) { WEEK("1W"), MONTH("1M"), THREE_MONTHS("3M"), YEAR("1Y"), ALL("ALL") }

@Serializable data class PracticeValuePoint(val date: String, val value: String?)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PracticePerformance(
    val range: PracticeRange,
    @EncodeDefault val available: List<PracticeRange> = emptyList(),
    @EncodeDefault val locked: List<PracticeRange> = emptyList(),
    @EncodeDefault val points: List<PracticeValuePoint> = emptyList(),
    val baseCurrency: String,
    val startValue: String? = null,
    val endValue: String? = null,
    val change: String? = null,
    val changePercent: String? = null,
    @EncodeDefault val notes: List<String> = emptyList()
)

@Serializable data class PracticeTransactions(val items: List<PracticeTransaction>, val baseCurrency: String)
@Serializable data class ResetRequest(val confirm: Boolean, val idempotencyKey: String)
@Serializable data class ChallengeAnswer(val optionId: String)
/** MOCK only: load a deterministic scenario for the signed-in user. */
@Serializable data class PracticeScenarioRequest(val scenario: String)
