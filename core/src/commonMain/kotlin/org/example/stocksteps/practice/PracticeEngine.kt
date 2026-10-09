package org.example.stocksteps.practice

import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.EntitlementStatus

/**
 * The single place that decides what each access level includes. Free forever: 3 open holdings and
 * the basics; Trial (14 days, explicit start) and StockSteps+: unlimited holdings and premium tools.
 * Selling, buying more of an existing holding, viewing everything and history are never limited.
 */
object PracticePolicy {
    const val STARTING_CASH = "10000"
    const val FREE_MAX_OPEN_HOLDINGS = 3
    const val TRIAL_DAYS = 14
    const val TRIAL_MILLIS = TRIAL_DAYS * 24L * 60 * 60 * 1000
    const val DISCLOSURE = "This is a simulated trade using virtual money. Prices are based on available market data and may differ from real execution prices."

    private val PREMIUM = PracticeCapability.entries.toSet()

    fun capabilities(access: PracticeAccess): Set<PracticeCapability> = if (access == PracticeAccess.FREE) emptySet() else PREMIUM
    fun maxOpenHoldings(access: PracticeAccess): Int? = if (PracticeCapability.UNLIMITED_HOLDINGS in capabilities(access)) null else FREE_MAX_OPEN_HOLDINGS
    /** Only opening a *new* distinct instrument is limited, and only while at or above the limit. */
    fun canOpenNewHolding(access: PracticeAccess, openHoldings: Int): Boolean = maxOpenHoldings(access)?.let { openHoldings < it } ?: true

    fun trialActive(trial: PracticeTrial?, now: Long) = trial != null && now >= trial.startedAt && now < trial.endsAt

    /** Effective access from verified records only: StockSteps+ overrides an expired trial. */
    fun access(plusActive: Boolean, trial: PracticeTrial?, now: Long): PracticeAccess = when {
        plusActive -> PracticeAccess.PLUS
        trialActive(trial, now) -> PracticeAccess.TRIAL
        else -> PracticeAccess.FREE
    }

    fun trialStatus(trial: PracticeTrial?, plusActive: Boolean, now: Long): TrialStatus = when {
        trial == null -> TrialStatus.NOT_STARTED
        plusActive -> TrialStatus.CONVERTED_TO_PLUS
        trialActive(trial, now) -> TrialStatus.ACTIVE
        else -> TrialStatus.EXPIRED
    }

    fun entitlement(plusStatus: EntitlementStatus, plusActive: Boolean, trial: PracticeTrial?, now: Long): PracticeEntitlement {
        val access = access(plusActive, trial, now)
        return PracticeEntitlement(access, maxOpenHoldings(access), trialStatus(trial, plusActive, now), trialEligible = trial == null,
            trialStartedAt = trial?.startedAt, trialEndsAt = trial?.endsAt, capabilities = capabilities(access).sortedBy { it.ordinal },
            plusStatus = plusStatus, serverTime = now)
    }

    /** Free shows a short chart; full history needs the capability. */
    fun ranges(access: PracticeAccess): List<PracticeRange> =
        if (PracticeCapability.FULL_HISTORY in capabilities(access)) PracticeRange.entries else listOf(PracticeRange.WEEK, PracticeRange.MONTH)
}

class PracticeException(val status: Int, val code: String, message: String) : Exception(message)

/** Open position after replaying the ledger. [cost] is the base-currency cost of the shares still held. */
data class PracticePosition(val instrument: PracticeInstrument, val quantity: Decimal, val cost: Decimal, val realized: Decimal, val dividends: Decimal) {
    val averageCost: Decimal get() = if (quantity == Decimal.ZERO) Decimal.ZERO else cost / quantity
}

data class PracticeLedgerState(
    val cash: Decimal,
    /** Open positions only (quantity > 0), by instrument id, in first-purchase order. */
    val positions: Map<String, PracticePosition>,
    val realized: Decimal,
    val dividends: Decimal
) {
    val openHoldings: Int get() = positions.size
}

/** Market inputs for a valuation: base-currency price per share by instrument id. */
data class PracticePrice(val price: Decimal, val priceCurrency: String, val fxRate: Decimal, val quoteAsOf: String?, val stale: Boolean = false) {
    val basePerShare: Decimal get() = price * fxRate
}

/**
 * Deterministic accounting (weighted-average cost) for the simulator.
 *
 * - Cash is in the portfolio base currency and rounded to cents (half-up) on every movement.
 * - Shares have up to [QUANTITY_PLACES] decimals; prices and FX keep their supplied precision.
 * - Buy: cost += amount. Sell: cost removed = cost × sold ÷ held (all of it on a full sale);
 *   realized gain = proceeds − cost removed. Split: shares × ratio, cost unchanged.
 * - Cash and quantities can never go negative; there is no margin, shorting or leverage.
 */
object PracticeEngine {
    const val QUANTITY_PLACES = 4
    const val CASH_PLACES = 2
    val MIN_QUANTITY: Decimal = Decimal.parse("0.0001")

    fun money(value: Decimal): Decimal = Decimal.parse(value.display(CASH_PLACES))
    /** Truncates toward zero to whole 0.0001 shares (never rounds a share count up). */
    fun floorQuantity(value: Decimal): Decimal {
        val text = value.toString()
        val dot = text.indexOf('.')
        return if (dot < 0 || text.length - dot - 1 <= QUANTITY_PLACES) value else Decimal.parse(text.substring(0, dot + 1 + QUANTITY_PLACES))
    }
    fun validQuantity(text: String?): Decimal? = runCatching { Decimal.parse(text!!.trim()) }.getOrNull()
        ?.takeIf { it > Decimal.ZERO && floorQuantity(it) == it }

    fun replay(startingCash: String, transactions: List<PracticeTransaction>): PracticeLedgerState {
        var cash = Decimal.parse(startingCash)
        var realized = Decimal.ZERO
        var dividends = Decimal.ZERO
        val positions = LinkedHashMap<String, PracticePosition>()
        transactions.sortedWith(compareBy({ it.executedAt }, { it.sequence })).forEach { tx ->
            val qty = Decimal.parse(tx.quantity)
            val amount = Decimal.parse(tx.amount)
            val current = positions[tx.instrument.id]
            when (tx.type) {
                PracticeTransactionType.BUY -> {
                    cash -= amount
                    positions[tx.instrument.id] = current?.copy(quantity = current.quantity + qty, cost = current.cost + amount)
                        ?: PracticePosition(tx.instrument, qty, amount, Decimal.ZERO, Decimal.ZERO)
                }
                PracticeTransactionType.SELL -> {
                    val held = current ?: throw IllegalStateException("Sell without a position: ${tx.id}")
                    require(qty <= held.quantity) { "Sell exceeds holding: ${tx.id}" }
                    val removed = if (qty == held.quantity) held.cost else held.cost.multiplyDivide(qty, held.quantity)
                    cash += amount
                    realized += amount - removed
                    val left = held.copy(quantity = held.quantity - qty, cost = held.cost - removed, realized = held.realized + amount - removed)
                    if (left.quantity == Decimal.ZERO) positions.remove(tx.instrument.id) else positions[tx.instrument.id] = left
                }
                PracticeTransactionType.DIVIDEND -> {
                    cash += amount
                    dividends += amount
                    current?.let { positions[tx.instrument.id] = it.copy(dividends = it.dividends + amount) }
                }
                PracticeTransactionType.SPLIT_ADJUSTMENT -> current?.let { positions[tx.instrument.id] = it.copy(quantity = it.quantity * qty) }
            }
            check(cash >= Decimal.ZERO) { "Cash below zero after ${tx.id}" }
        }
        return PracticeLedgerState(cash, positions, realized, dividends)
    }

    /** Base-currency cash for [quantity] shares at [price] × [fx], rounded to cents. */
    fun tradeAmount(quantity: Decimal, price: Decimal, fx: Decimal): Decimal = money(quantity * price * fx)

    /** Shares an amount buys at [price] × [fx], truncated to whole 0.0001 shares. */
    fun quantityFor(amount: Decimal, price: Decimal, fx: Decimal): Decimal = floorQuantity(amount / (price * fx))

    /**
     * Validates an order against the ledger and access level. Returns the blocker (null when it can
     * execute). Holding limits apply only to opening a new instrument; sells are always allowed.
     */
    fun blocker(state: PracticeLedgerState, access: PracticeAccess, side: OrderSide, instrumentId: String, quantity: Decimal, total: Decimal, baseCurrency: String): PracticeBlocker? {
        if (quantity <= Decimal.ZERO) return PracticeBlocker("INVALID_QUANTITY", "Enter a number of shares greater than zero (up to 4 decimal places).")
        val held = state.positions[instrumentId]
        return when (side) {
            OrderSide.BUY -> when {
                total <= Decimal.ZERO -> PracticeBlocker("INVALID_QUANTITY", "This order is too small to simulate. Try a larger amount.")
                held == null && !PracticePolicy.canOpenNewHolding(access, state.openHoldings) ->
                    PracticeBlocker("HOLDING_LIMIT", "Your free Practice Portfolio includes ${PracticePolicy.FREE_MAX_OPEN_HOLDINGS} holdings. Sell one, or unlock unlimited practice, to add a new company.")
                total > state.cash -> PracticeBlocker("INSUFFICIENT_CASH",
                    "You have ${format(state.cash, baseCurrency)} in virtual cash. This simulated purchase would cost approximately ${format(total, baseCurrency)}.")
                else -> null
            }
            OrderSide.SELL -> when {
                held == null -> PracticeBlocker("NO_HOLDING", "You don't hold this investment in your Practice Portfolio.")
                quantity > held.quantity -> PracticeBlocker("INSUFFICIENT_SHARES", "You can sell up to ${PracticeFormat.shareCount(held.quantity.toString())}.")
                else -> null
            }
        }
    }

    data class Valuation(
        val holdingsValue: Decimal?,
        val totalValue: Decimal?,
        val unrealized: Decimal?,
        val status: ValuationStatus,
        val values: Map<String, Decimal>
    )

    /** Portfolio value = cash + Σ(shares × price × FX). Any missing price makes the totals unavailable. */
    fun value(state: PracticeLedgerState, prices: Map<String, PracticePrice>): Valuation {
        val values = state.positions.mapNotNull { (id, p) -> prices[id]?.let { id to money(p.quantity * it.basePerShare) } }.toMap()
        val complete = values.size == state.positions.size
        val holdings = if (complete) values.values.fold(Decimal.ZERO, Decimal::plus) else null
        val cost = state.positions.values.fold(Decimal.ZERO) { sum, p -> sum + p.cost }
        val status = when {
            complete -> ValuationStatus.COMPLETE
            values.isNotEmpty() -> ValuationStatus.PARTIAL
            else -> ValuationStatus.UNAVAILABLE
        }
        return Valuation(holdings, holdings?.let { state.cash + it }, holdings?.let { it - cost }, status, values)
    }

    /** Percent with two decimals, or null when the base is zero. */
    fun percent(part: Decimal, whole: Decimal): Decimal? = if (whole == Decimal.ZERO) null else Decimal.parse((part * Decimal.parse("100") / whole).display(2))

    /**
     * Value at the end of each date using only transactions executed by then (never today's holdings
     * applied to the past). A date before the portfolio existed, or with a missing close for a held
     * instrument, has no value rather than an estimate.
     */
    fun history(
        startingCash: String,
        createdDate: String,
        transactions: List<PracticeTransaction>,
        dates: List<String>,
        closes: Map<String, Map<String, PracticePrice>>,
        dayOf: (Long) -> String
    ): List<PracticeValuePoint> = dates.filter { it >= createdDate }.map { date ->
        val state = replay(startingCash, transactions.filter { dayOf(it.executedAt) <= date })
        val valuation = value(state, closes[date].orEmpty())
        PracticeValuePoint(date, valuation.totalValue?.let { money(it).toString() })
    }

    fun format(value: Decimal, currency: String): String = (if (value < Decimal.ZERO) "−" else "") + symbol(currency) + groupDigits(money(if (value < Decimal.ZERO) -value else value).display(2))
    fun symbol(currency: String) = when (currency) { "CAD" -> "$"; "USD" -> "US$"; else -> "$currency " }
    private fun groupDigits(text: String): String {
        val (whole, fraction) = text.split('.').let { it[0] to it.getOrNull(1) }
        return whole.reversed().chunked(3).joinToString(",").reversed() + (fraction?.let { ".$it" } ?: "")
    }
}
