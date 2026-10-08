package org.example.stocksteps.practice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.example.stocksteps.model.MarketSessionStatus
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.UsMarketCalendar
import org.example.stocksteps.userdata.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Quotes, profiles and daily closes for the simulator (the same cached services the app uses). */
interface PracticeMarket {
    suspend fun quote(symbol: String): StockQuote?
    suspend fun profile(symbol: String): org.example.stocksteps.model.CompanyProfile?
    suspend fun closes(symbol: String): List<PricePoint>
}

class WatchPracticeMarket(private val data: WatchMarketData, private val dailyCloses: suspend (String) -> List<PricePoint>) : PracticeMarket {
    override suspend fun quote(symbol: String) = data.quote(symbol)
    override suspend fun profile(symbol: String) = data.profile(symbol)
    override suspend fun closes(symbol: String) = dailyCloses(symbol)
}

/** A cash dividend or a split, in the instrument's currency. */
data class CorporateAction(val id: String, val symbol: String, val type: PracticeTransactionType, val exDate: String, val payDate: String? = null, val perShare: String? = null, val ratio: String? = null, val note: String)

/**
 * Reliable corporate actions. REAL has no verified source yet (null), so none are applied and the
 * limitation is disclosed; MOCK uses labelled sample events.
 */
fun interface CorporateActionSource { suspend fun actions(symbol: String): List<CorporateAction> }

/**
 * MOCK sample events (not real announcements). Sample prices for the split symbol are divided by the
 * ratio from the ex-date, so the sample stays economically consistent.
 */
object MockCorporateActions : CorporateActionSource {
    val events = listOf(
        CorporateAction("sample-msft-div-2026q3", "MSFT", PracticeTransactionType.DIVIDEND, exDate = "2026-08-20", payDate = "2026-09-10", perShare = "0.91",
            note = "Sample dividend for development, not a real announcement."),
        CorporateAction("sample-nvda-split-2026", "NVDA", PracticeTransactionType.SPLIT_ADJUSTMENT, exDate = "2026-09-21", ratio = "2",
            note = "Sample 2-for-1 split for development, not a real announcement.")
    )
    override suspend fun actions(symbol: String) = events.filter { it.symbol == symbol }
    fun splitFactor(symbol: String, date: String): Double = events.filter { it.symbol == symbol && it.type == PracticeTransactionType.SPLIT_ADJUSTMENT && date >= it.exDate }
        .fold(1.0) { f, e -> f * e.ratio!!.toDouble() }
}

/**
 * Practice Portfolio backend. The server owns identity, entitlements, trial dates, cash, positions,
 * execution prices, idempotency and resets; every mutation happens inside one atomic
 * [UserDataStore.updatePractice] so concurrent orders can't overspend or exceed the holding limit.
 *
 * Execution policy (MVP): an order fills immediately at the latest *fresh* quote (the current US
 * session's quote, or its closing price after the close), converted with the latest Bank of Canada
 * USD/CAD rate published within 7 days. Stale or missing quotes, missing FX, and currencies other
 * than CAD and USD block execution with an explanation. No queued, stop, margin or short orders.
 */
class PracticeService(
    private val store: UserDataStore,
    private val entitlements: EntitlementService,
    private val market: PracticeMarket,
    private val fx: PortfolioFxSource,
    /** Authoritative backend time: timestamps and trial dates. */
    private val clock: Clock,
    /** Market time for quote freshness (MOCK pins it to the fixture capture). */
    private val marketClock: Clock,
    private val corporateActions: CorporateActionSource?,
    private val sampleData: Boolean,
    private val baseCurrency: String = "CAD",
    private val rules: AlertRules = AlertRules(),
    private val calendar: UsMarketCalendar = UsMarketCalendar()
) {
    private val cache = CompanyFinancialCache(capacity = 512)
    /** MOCK-only overrides set by scenarios ("missing-fx", "stale-quote"). */
    private val mockFlags = ConcurrentHashMap<String, Set<String>>()

    companion object {
        const val MAX_TRANSACTIONS = 5_000
        const val MAX_ARCHIVES = 5
        const val MAX_CARRY_DAYS = 4L
        const val IDEMPOTENCY_LIMIT = 500
        const val IDEMPOTENCY_RETENTION = 30L * 86_400_000
        /** A reviewed price that moved more than this asks for a fresh review. */
        val PRICE_TOLERANCE: Decimal = Decimal.parse("0.01")
        private val SYMBOL = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")
        private val KEY = Regex("[A-Za-z0-9_-]{8,64}")
    }

    private fun now() = clock.millis()
    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    // ---------- Entitlements and trial ----------

    suspend fun entitlement(uid: String, data: PracticeAccountData? = null): PracticeEntitlement {
        val plus = entitlements.get(uid)
        val trial = (data ?: store.updatePractice(uid) { it to it }).trial
        return PracticePolicy.entitlement(plus.status, plus.plus, trial, now())
    }

    /** Explicit, idempotent and one-time: a second request never restarts or extends the trial. */
    suspend fun activateTrial(uid: String): PracticeEntitlement {
        val plus = entitlements.get(uid)
        val data = store.updatePractice(uid) { current ->
            when {
                current.trial != null -> current to current
                plus.plus -> throw UserDataException(409, "PLUS_ACTIVE", "You already have StockSteps+, which includes everything in the trial.")
                else -> {
                    val start = now()
                    val next = current.copy(trial = PracticeTrial(start, start + PracticePolicy.TRIAL_MILLIS, start, start), revision = current.revision + 1)
                    next to next
                }
            }
        }
        return PracticePolicy.entitlement(plus.status, plus.plus, data.trial, now())
    }

    suspend fun acknowledgeTrialNotice(uid: String) {
        store.updatePractice(uid) { it.copy(trialExpiredNoticeSeen = true) to Unit }
    }

    // ---------- Pricing ----------

    private class Priced(val instrument: PracticeInstrument, val price: PracticePrice, val basis: String, val fxAsOf: String?)

    private suspend fun <T> safe(block: suspend () -> T): T? = try { block() } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }

    private suspend fun instrument(symbol: String, profile: org.example.stocksteps.model.CompanyProfile?, quote: StockQuote?): PracticeInstrument? {
        val currency = profile?.currency?.uppercase() ?: return null
        val kind = when { profile.isEtf == true -> InstrumentKind.ETF; profile.isEtf == false || profile.sector != null -> InstrumentKind.STOCK; else -> InstrumentKind.UNKNOWN }
        return PracticeInstrument(symbol, symbol, profile.companyName ?: quote?.companyName, profile.exchange, currency, kind,
            profile.sector?.takeIf { kind == InstrumentKind.STOCK && it.isNotBlank() }, profile.logoUrl)
    }

    /** USD→CAD publications between two dates (one cached request per window). */
    private suspend fun fxTable(from: LocalDate, to: LocalDate): Map<String, String> =
        safe { cache.getOrLoad("fx:$from:$to", 3_600_000) { fx.rates(from.toString(), to.toString()) } }.orEmpty()

    /** Native-currency → base rate for [date] (latest publication at most 7 days earlier), or null. */
    private suspend fun fxRate(currency: String, date: LocalDate, uid: String?, table: Map<String, String>? = null): Pair<Decimal, String?>? {
        if (currency == baseCurrency) return Decimal.ONE to null
        if (uid != null && "missing-fx" in mockFlags[uid].orEmpty()) return null
        if (setOf(currency, baseCurrency) != setOf("USD", "CAD")) return null
        val rates = (table ?: fxTable(date.minusDays(7), date)).filterKeys { it >= date.minusDays(7).toString() }
        val publication = rates.keys.filter { it <= date.toString() }.maxOrNull() ?: return null
        val usdCad = Decimal.parse(rates.getValue(publication))
        return (if (baseCurrency == "CAD") usdCad else Decimal.ONE / usdCad) to publication
    }

    private fun decimal(value: Double, places: Int = 4) = Decimal.parse(BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it.contains('E')) BigDecimal(it).toPlainString() else it })

    private fun adjust(symbol: String, price: Double, date: String) = if (sampleData) price / MockCorporateActions.splitFactor(symbol, date) else price

    private suspend fun rawQuote(uid: String?, symbol: String): StockQuote? {
        val quote = safe { market.quote(symbol) } ?: return null
        val flags = uid?.let { mockFlags[it] }.orEmpty()
        if ("missing-quote" in flags && symbol == "AAPL") return null
        val shifted = if ("stale-quote" in flags && symbol == "AAPL") quote.copy(timestamp = quote.timestamp?.minus(3 * 86_400)) else quote
        val date = shifted.timestamp?.let { Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate().toString() } ?: return shifted
        return shifted.copy(price = shifted.price?.let { adjust(symbol, it, date) })
    }

    /** The execution price under the documented policy, or a blocker explaining why trading is unavailable. */
    private suspend fun priceForTrade(uid: String, symbol: String): Pair<Priced?, PracticeBlocker?> {
        val profile = safe { market.profile(symbol) }
        val quote = rawQuote(uid, symbol)
        val instrument = instrument(symbol, profile, quote)
            ?: return null to PracticeBlocker("UNSUPPORTED_INSTRUMENT", "This investment can't be used in the Practice Portfolio because its trading currency isn't available.")
        if (instrument.currency !in setOf("USD", "CAD")) return null to PracticeBlocker("UNSUPPORTED_INSTRUMENT", "Only investments priced in Canadian or US dollars can be practised right now.")
        val price = quote?.price?.takeIf { it.isFinite() && it > 0 }
            ?: return null to PracticeBlocker("QUOTE_MISSING", "There's no price for ${instrument.symbol} right now, so a practice trade can't be simulated.")
        val marketNow = marketClock.instant()
        val fresh = rules.freshQuote(quote, marketNow)
            ?: return null to PracticeBlocker("QUOTE_STALE", "The latest price for ${instrument.symbol} is from an earlier trading session, so it isn't used for a practice trade. Try again when an up-to-date price is available.")
        val session = calendar.session(marketNow).status
        val basis = if (session == MarketSessionStatus.OPEN) "Delayed market quote (market open)" else "Latest closing price (market closed)"
        val quoteDate = Instant.ofEpochSecond(fresh.timestamp!!).atZone(calendar.zone).toLocalDate()
        val (rate, fxAsOf) = fxRate(instrument.currency, quoteDate, uid)
            ?: return null to PracticeBlocker("FX_UNAVAILABLE", "A current ${instrument.currency}→$baseCurrency exchange rate isn't available, so this trade can't be converted. Nothing was assumed.")
        return Priced(instrument, PracticePrice(decimal(price), instrument.currency, rate, Instant.ofEpochSecond(fresh.timestamp!!).toString()), basis, fxAsOf) to null
    }

    // ---------- Ledger helpers ----------

    private fun ensurePortfolio(data: PracticeAccountData): PracticeAccountData = if (data.portfolio != null) data else {
        val t = now()
        data.copy(portfolio = PracticePortfolio("practice-1", 1, baseCurrency, PracticePolicy.STARTING_CASH, t, t), revision = data.revision + 1)
    }

    private fun currentTransactions(data: PracticeAccountData) = data.transactions.filter { it.generation == data.portfolio!!.generation }

    /** Applies due corporate actions once each, using shares held before the ex-date. */
    private suspend fun corporateActions(data: PracticeAccountData): List<CorporateAction> {
        val source = corporateActions ?: return emptyList()
        val symbols = currentTransactions(data).map { it.instrument.id }.distinct()
        return symbols.flatMap { safe { source.actions(it) }.orEmpty() }
    }

    private suspend fun applyActions(data: PracticeAccountData, actions: List<CorporateAction>): PracticeAccountData {
        if (actions.isEmpty()) return data
        val portfolio = data.portfolio!!
        val today = day(now())
        var current = data
        actions.sortedBy { it.exDate }.forEach { action ->
            val eventId = "${action.id}:g${portfolio.generation}"
            val txs = currentTransactions(current)
            if (txs.any { it.eventId == eventId } || action.exDate <= day(portfolio.createdAt)) return@forEach
            val effective = if (action.type == PracticeTransactionType.DIVIDEND) action.payDate ?: action.exDate else action.exDate
            if (effective > today) return@forEach
            val before = PracticeEngine.replay(portfolio.startingCash, txs.filter { day(it.executedAt) < action.exDate })
            val held = before.positions[action.symbol] ?: return@forEach
            val at = LocalDate.parse(effective).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val tx = when (action.type) {
                PracticeTransactionType.DIVIDEND -> {
                    val (rate, fxAsOf) = fxRate(held.instrument.currency, LocalDate.parse(effective), null) ?: return@forEach
                    val perShare = Decimal.parse(action.perShare!!)
                    PracticeTransaction("tx-${eventId}", portfolio.generation, PracticeTransactionType.DIVIDEND, held.instrument, held.quantity.toString(), perShare.toString(),
                        held.instrument.currency, rate.toString(), PracticeEngine.tradeAmount(held.quantity, perShare, rate).toString(), at, null,
                        action.note, fxAsOf = fxAsOf, eventId = eventId, sequence = current.revision + 1)
                }
                else -> PracticeTransaction("tx-${eventId}", portfolio.generation, PracticeTransactionType.SPLIT_ADJUSTMENT, held.instrument, action.ratio!!, "0",
                    held.instrument.currency, "1", "0", at, null, action.note, eventId = eventId, sequence = current.revision + 1)
            }
            current = current.copy(transactions = current.transactions + tx, revision = current.revision + 1)
        }
        return current
    }

    /** Loads (creating on first use) and applies due corporate actions, atomically. */
    private suspend fun load(uid: String): PracticeAccountData {
        val first = store.updatePractice(uid) { current -> ensurePortfolio(current).let { it to it } }
        val actions = corporateActions(first)
        if (actions.isEmpty()) return first
        // Fetch FX outside the lock; recheck inside it so an event is applied only once.
        val prepared = applyActions(first, actions)
        if (prepared == first) return first
        return store.updatePractice(uid) { current ->
            val added = prepared.transactions.filter { tx -> tx.eventId != null && current.transactions.none { it.eventId == tx.eventId } && tx.generation == current.portfolio?.generation }
            val next = if (added.isEmpty()) current else current.copy(transactions = current.transactions + added, revision = current.revision + 1)
            next to next
        }
    }

    // ---------- Orders ----------

    private fun requestHash(request: PracticeOrderRequest) = MessageDigest.getInstance("SHA-256")
        .digest(listOf(request.symbol.uppercase(), request.side, request.quantity, request.amount).joinToString("|").toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normalized(request: PracticeOrderRequest): PracticeOrderRequest {
        val symbol = request.symbol.trim().uppercase()
        if (!SYMBOL.matches(symbol)) throw UserDataException(400, "INVALID_SYMBOL", "Choose a valid investment.")
        if ((request.quantity == null) == (request.amount == null)) throw UserDataException(400, "INVALID_QUANTITY", "Enter either a number of shares or an amount.")
        if (request.amount != null && request.side == OrderSide.SELL) throw UserDataException(400, "INVALID_QUANTITY", "Enter the number of shares to sell.")
        return request.copy(symbol = symbol)
    }

    private fun preview(data: PracticeAccountData, entitlement: PracticeEntitlement, request: PracticeOrderRequest, priced: Priced?, priceBlocker: PracticeBlocker?, instrumentFallback: PracticeInstrument?): PracticeOrderPreview {
        val portfolio = data.portfolio!!
        val state = PracticeEngine.replay(portfolio.startingCash, currentTransactions(data))
        val held = state.positions[request.symbol]
        val instrument = priced?.instrument ?: held?.instrument ?: instrumentFallback
            ?: PracticeInstrument(request.symbol, request.symbol, currency = "?")
        val price = priced?.price
        val quantity = when {
            request.quantity != null -> PracticeEngine.validQuantity(request.quantity)
            price != null -> runCatching { Decimal.parse(request.amount!!.trim()) }.getOrNull()?.takeIf { it > Decimal.ZERO }?.let { PracticeEngine.quantityFor(it, price.price, price.fxRate) }
            else -> null
        } ?: Decimal.ZERO
        val total = price?.let { PracticeEngine.tradeAmount(quantity, it.price, it.fxRate) } ?: Decimal.ZERO
        val blocker = priceBlocker ?: PracticeEngine.blocker(state, entitlement.access, request.side, request.symbol, quantity, total, portfolio.baseCurrency)
        val cashAfter = if (request.side == OrderSide.BUY) state.cash - total else state.cash + total
        return PracticeOrderPreview(instrument, request.side, quantity.toString(), price?.price?.toString() ?: "0", instrument.currency,
            price?.fxRate?.toString() ?: "0", priced?.fxAsOf, portfolio.baseCurrency, total.toString(),
            state.cash.toString(), cashAfter.toString(), price?.quoteAsOf, priced?.basis ?: "Unavailable",
            (held?.quantity ?: Decimal.ZERO).toString(), opensNewHolding = request.side == OrderSide.BUY && held == null,
            openHoldings = state.openHoldings, maxOpenHoldings = entitlement.maxOpenHoldings, blocker = blocker)
    }

    suspend fun preview(uid: String, raw: PracticeOrderRequest): PracticeOrderPreview {
        val request = normalized(raw)
        val data = load(uid)
        val (priced, blocker) = priceForTrade(uid, request.symbol)
        return preview(data, entitlement(uid, data), request, priced, blocker, null)
    }

    /**
     * Fills an order. The price, cash, holding count and access are all re-checked inside one atomic
     * update; the same idempotency key replays the first result instead of filling twice.
     */
    suspend fun execute(uid: String, raw: PracticeOrderRequest): PracticeOrderResult {
        val request = normalized(raw)
        val key = request.idempotencyKey?.takeIf(KEY::matches) ?: throw UserDataException(400, "IDEMPOTENCY_KEY_REQUIRED", "This order is missing its request id. Review it again.")
        val hash = requestHash(request)
        load(uid).idempotency.firstOrNull { it.key == key && it.operation == "order" }?.let { return replay(uid, it, hash) }
        val (priced, priceBlocker) = priceForTrade(uid, request.symbol)
        priceBlocker?.let { throw UserDataException(blockerStatus(it.code), it.code, it.message) }
        val price = priced!!.price
        val reviewed = request.reviewedPrice?.let { runCatching { Decimal.parse(it) }.getOrNull() }
            ?: throw UserDataException(400, "REVIEW_REQUIRED", "Review the order before confirming it.")
        val move = if (reviewed > price.price) reviewed - price.price else price.price - reviewed
        if (move > reviewed * PRICE_TOLERANCE) throw UserDataException(409, "PRICE_CHANGED", "The price moved since your review (now ${PracticeEngine.symbol(price.priceCurrency)}${price.price.display(2)}). Review the order again.")
        val plus = entitlements.get(uid)
        return store.updatePractice(uid) { stored ->
            val current = ensurePortfolio(stored)
            current.idempotency.firstOrNull { it.key == key && it.operation == "order" }?.let { record ->
                if (record.requestHash != hash) throw UserDataException(409, "IDEMPOTENCY_CONFLICT", "This request id was already used for a different order.")
                val tx = current.transactions.first { it.id == record.resultId }
                val existing = current.portfolio!!
                val cash = PracticeEngine.replay(existing.startingCash, currentTransactions(current)).cash
                return@updatePractice current to PracticeOrderResult(tx, cash.toString(), existing.baseCurrency, replayed = true)
            }
            val portfolio = current.portfolio!!
            val txs = currentTransactions(current)
            if (txs.size >= MAX_TRANSACTIONS) throw UserDataException(409, "LEDGER_FULL", "This practice portfolio has reached its transaction limit. Reset it to start again.")
            val access = PracticePolicy.access(plus.plus, current.trial, now())
            val state = PracticeEngine.replay(portfolio.startingCash, txs)
            val quantity = request.quantity?.let(PracticeEngine::validQuantity)
                ?: request.amount?.let { runCatching { Decimal.parse(it.trim()) }.getOrNull() }?.takeIf { it > Decimal.ZERO }?.let { PracticeEngine.quantityFor(it, price.price, price.fxRate) }
                ?: Decimal.ZERO
            val total = PracticeEngine.tradeAmount(quantity, price.price, price.fxRate)
            PracticeEngine.blocker(state, access, request.side, request.symbol, quantity, total, portfolio.baseCurrency)?.let { throw UserDataException(blockerStatus(it.code), it.code, it.message) }
            val time = maxOf(now(), (txs.maxOfOrNull { it.executedAt } ?: 0) + 1)
            val instrument = state.positions[request.symbol]?.instrument ?: priced.instrument
            val tx = PracticeTransaction("tx-${current.revision + 1}-${key.take(12)}", portfolio.generation,
                if (request.side == OrderSide.BUY) PracticeTransactionType.BUY else PracticeTransactionType.SELL, instrument, quantity.toString(), price.price.toString(),
                price.priceCurrency, price.fxRate.toString(), total.toString(), time, key, priced.basis, price.quoteAsOf,
                priced.fxAsOf, sequence = current.revision + 1)
            val challenges = if (request.side == OrderSide.BUY && PracticeChallenges.FIRST_BUY !in current.challenges) current.challenges + (PracticeChallenges.FIRST_BUY to time) else current.challenges
            val records = (current.idempotency + IdempotencyRecord(key, "order", hash, tx.id, time))
                .filter { time - it.createdAt < IDEMPOTENCY_RETENTION }.takeLast(IDEMPOTENCY_LIMIT)
            val next = current.copy(transactions = current.transactions + tx, challenges = challenges, idempotency = records, revision = current.revision + 1)
            val cashAfter = PracticeEngine.replay(portfolio.startingCash, txs + tx).cash
            next to PracticeOrderResult(tx, cashAfter.toString(), portfolio.baseCurrency)
        }
    }

    private suspend fun replay(uid: String, record: IdempotencyRecord, hash: String): PracticeOrderResult {
        if (record.requestHash != hash) throw UserDataException(409, "IDEMPOTENCY_CONFLICT", "This request id was already used for a different order.")
        val data = store.updatePractice(uid) { it to it }
        val tx = data.transactions.first { it.id == record.resultId }
        val portfolio = data.portfolio!!
        return PracticeOrderResult(tx, PracticeEngine.replay(portfolio.startingCash, currentTransactions(data)).cash.toString(), portfolio.baseCurrency, replayed = true)
    }

    private fun blockerStatus(code: String) = when (code) {
        "HOLDING_LIMIT" -> 403
        "QUOTE_STALE", "QUOTE_MISSING", "FX_UNAVAILABLE" -> 409
        else -> 400
    }

    // ---------- Reset ----------

    /** Archives the current generation and starts again with the starting cash. Trial, plan and challenges are untouched. */
    suspend fun reset(uid: String, request: ResetRequest): PracticeOverview {
        if (!request.confirm) throw UserDataException(400, "CONFIRMATION_REQUIRED", "Confirm the reset to continue.")
        val key = request.idempotencyKey.takeIf(KEY::matches) ?: throw UserDataException(400, "IDEMPOTENCY_KEY_REQUIRED", "This request is missing its request id.")
        store.updatePractice(uid) { stored ->
            val current = ensurePortfolio(stored)
            if (current.idempotency.any { it.key == key && it.operation == "reset" }) return@updatePractice current to Unit
            val portfolio = current.portfolio!!
            val t = now()
            val txs = currentTransactions(current)
            val archive = PracticeArchive(portfolio.generation, portfolio.createdAt, t, PracticeEngine.replay(portfolio.startingCash, txs).cash.toString(), txs)
            val generation = portfolio.generation + 1
            current.copy(
                portfolio = PracticePortfolio("practice-$generation", generation, baseCurrency, PracticePolicy.STARTING_CASH, t, t),
                transactions = current.transactions.filter { it.generation != portfolio.generation },
                archives = (current.archives + archive).takeLast(MAX_ARCHIVES),
                idempotency = (current.idempotency + IdempotencyRecord(key, "reset", key, null, t)).takeLast(IDEMPOTENCY_LIMIT),
                revision = current.revision + 1
            ) to Unit
        }
        return overview(uid)
    }

    // ---------- Views ----------

    private suspend fun valuationPrices(uid: String, positions: Collection<PracticePosition>): Map<String, PracticePrice> = coroutineScope {
        val today = marketClock.instant().atZone(calendar.zone).toLocalDate()
        positions.map { p -> async {
            val quote = rawQuote(uid, p.instrument.id)
            val price = quote?.price?.takeIf { it.isFinite() && it > 0 } ?: return@async null
            val date = quote.timestamp?.let { Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate() } ?: today
            val (rate, _) = fxRate(p.instrument.currency, date, uid) ?: return@async null
            p.instrument.id to PracticePrice(decimal(price), p.instrument.currency, rate, quote.timestamp?.let { Instant.ofEpochSecond(it).toString() },
                stale = rules.freshQuote(quote, marketClock.instant()) == null)
        } }.awaitAll().filterNotNull().toMap()
    }

    suspend fun overview(uid: String): PracticeOverview {
        val data = load(uid)
        val portfolio = data.portfolio!!
        val entitlement = entitlement(uid, data)
        val txs = currentTransactions(data)
        val state = PracticeEngine.replay(portfolio.startingCash, txs)
        val prices = valuationPrices(uid, state.positions.values)
        val valuation = PracticeEngine.value(state, prices)
        val allocationAllowed = entitlement.has(PracticeCapability.ALLOCATION)
        val holdings = state.positions.values.map { p ->
            val price = prices[p.instrument.id]
            val value = valuation.values[p.instrument.id]
            PracticeHoldingView(p.instrument, p.quantity.toString(), p.averageCost.display(4), PracticeEngine.money(p.cost).toString(),
                price?.price?.toString(), p.instrument.currency, price?.fxRate?.toString(), value?.toString(),
                value?.let { (it - p.cost).toString() }, value?.let { PracticeEngine.percent(it - p.cost, p.cost)?.toString() },
                PracticeEngine.money(p.realized).toString(), price?.quoteAsOf, price?.stale ?: false,
                weightPercent = if (allocationAllowed && valuation.holdingsValue != null) value?.let { v -> PracticeEngine.percent(v, valuation.holdingsValue!!)?.toString() } else null)
        }
        val starting = Decimal.parse(portfolio.startingCash)
        val gain = valuation.totalValue?.let { it - starting }
        val allocation = if (!allocationAllowed || valuation.totalValue == null || valuation.totalValue!! <= Decimal.ZERO) null else {
            val total = valuation.totalValue!!
            holdings.map { h -> val v = h.marketValue!!; PracticeAllocationSlice(h.instrument.symbol, v, PracticeEngine.percent(Decimal.parse(v), total)!!.toString()) } +
                PracticeAllocationSlice("Cash", state.cash.toString(), PracticeEngine.percent(state.cash, total)!!.toString())
        }
        val sectors = if (!allocationAllowed || valuation.holdingsValue == null || holdings.isEmpty() || holdings.any { it.instrument.kind == InstrumentKind.UNKNOWN || (it.instrument.kind == InstrumentKind.STOCK && it.instrument.sector == null) }) null else {
            val invested = valuation.holdingsValue!!
            holdings.groupBy { if (it.instrument.kind == InstrumentKind.ETF) "ETFs" else it.instrument.sector!! }
                .map { (label, list) -> list.fold(Decimal.ZERO) { s, h -> s + Decimal.parse(h.marketValue!!) }.let { v -> PracticeAllocationSlice(label, v.toString(), PracticeEngine.percent(v, invested)!!.toString()) } }
                .takeIf { invested > Decimal.ZERO }
        }
        val premiumInsights = entitlement.has(PracticeCapability.PREMIUM_INSIGHTS)
        return PracticeOverview(
            portfolio.id, portfolio.generation, portfolio.baseCurrency, portfolio.startingCash, state.cash.toString(),
            valuation.holdingsValue?.toString(), valuation.totalValue?.toString(), gain?.toString(), gain?.let { PracticeEngine.percent(it, starting)?.toString() },
            valuation.unrealized?.toString(), PracticeEngine.money(state.realized).toString(), state.dividends.toString(), valuation.status,
            holdings, state.openHoldings, entitlement, txs.sortedByDescending { it.executedAt }.take(5), allocation, sectors,
            PracticeInsights.build(holdings, state.cash, valuation.totalValue, premiumInsights, portfolio.baseCurrency),
            PracticeChallenges.views(entitlement, data.challenges),
            notices = listOfNotNull(
                "Simulated with virtual money. No real orders are placed.",
                if (valuation.status != ValuationStatus.COMPLETE && holdings.isNotEmpty()) "Some prices or exchange rates aren't available, so totals that depend on them aren't shown." else null,
                if (holdings.any { it.stale }) "Some prices are from an earlier trading session." else null,
                if (corporateActions == null) "Dividends and stock splits aren't applied yet, so returns may differ from a real account." else null,
                if (sampleData) "Sample market data for development, not live prices." else null
            ),
            createdAt = portfolio.createdAt,
            showTrialExpiredNotice = entitlement.trialStatus == TrialStatus.EXPIRED && !data.trialExpiredNoticeSeen,
            sampleData = sampleData
        )
    }

    suspend fun transactions(uid: String, type: String?): PracticeTransactions {
        val filter = type?.let { t -> PracticeTransactionType.entries.firstOrNull { it.name.equals(t, true) } ?: throw UserDataException(400, "INVALID_TYPE", "Choose BUY, SELL or DIVIDEND.") }
        val data = load(uid)
        return PracticeTransactions(currentTransactions(data).filter { filter == null || it.type == filter }.sortedByDescending { it.executedAt }, data.portfolio!!.baseCurrency)
    }

    /**
     * Daily values from dated closes and FX, using the holdings each day actually had. Days before
     * the portfolio existed and days with a missing close or rate have no value.
     */
    suspend fun performance(uid: String, rangeLabel: String?): PracticePerformance = coroutineScope {
        val data = load(uid)
        val entitlement = entitlement(uid, data)
        val portfolio = data.portfolio!!
        val allowed = PracticePolicy.ranges(entitlement.access)
        val range = rangeLabel?.let { l -> PracticeRange.entries.firstOrNull { it.label.equals(l, true) } ?: throw UserDataException(400, "INVALID_RANGE", "Choose 1W, 1M, 3M, 1Y or ALL.") } ?: PracticeRange.MONTH
        if (range !in allowed) throw UserDataException(403, "RANGE_LOCKED", "Longer history is part of the Practice trial and StockSteps+.")
        val txs = currentTransactions(data)
        val today = LocalDate.parse(day(now()))
        val created = day(portfolio.createdAt)
        val from = when (range) {
            PracticeRange.WEEK -> today.minusWeeks(1); PracticeRange.MONTH -> today.minusMonths(1); PracticeRange.THREE_MONTHS -> today.minusMonths(3)
            PracticeRange.YEAR -> today.minusYears(1); PracticeRange.ALL -> LocalDate.parse(created)
        }.toString().let { maxOf(it, created) }
        val instruments = txs.map { it.instrument }.distinctBy { it.id }
        val series = instruments.map { i -> async { i to safe { cache.getOrLoad("practice-closes:${i.id}", 21_600_000) { market.closes(i.id) } }.orEmpty() } }.awaitAll()
        val dates = (series.flatMap { (_, points) -> points.map { it.time.take(10) } }.filter { it in from..today.toString() } + listOf(from, today.toString()).filter { instruments.isEmpty() })
            .distinct().sorted()
        val fxByDate = HashMap<String, Decimal?>()
        val table = if (instruments.any { it.currency != baseCurrency }) fxTable(LocalDate.parse(from).minusDays(7), today) else emptyMap()
        suspend fun rate(currency: String, date: String): Decimal? = if (currency == baseCurrency) Decimal.ONE
            else fxByDate.getOrPut("$currency$date") { fxRate(currency, LocalDate.parse(date), uid, table.filterKeys { it <= date })?.first }
        val closes = HashMap<String, MutableMap<String, PracticePrice>>()
        var carried = false
        series.forEach { (instrument, points) ->
            val byDate = points.associate { it.time.take(10) to it.close }.toSortedMap()
            dates.forEach { date ->
                // Exchanges close on different holidays: a gap of up to 4 days uses the previous close (disclosed); longer gaps stay empty.
                val exact = byDate[date]
                val source = exact?.let { date to it } ?: byDate.headMap(date).entries.lastOrNull()
                    ?.takeIf { LocalDate.parse(it.key).plusDays(MAX_CARRY_DAYS) >= LocalDate.parse(date) }?.let { it.key to it.value }
                if (source != null) rate(instrument.currency, date)?.let { r ->
                    if (exact == null) carried = true
                    closes.getOrPut(date) { HashMap() }[instrument.id] = PracticePrice(decimal(adjust(instrument.id, source.second, source.first)), instrument.currency, r, source.first)
                }
            }
        }
        val points = PracticeEngine.history(portfolio.startingCash, created, txs, dates, closes, ::day)
        val first = points.firstOrNull { it.value != null }?.value?.let(Decimal::parse)
        val last = points.lastOrNull { it.value != null }?.value?.let(Decimal::parse)
        PracticePerformance(range, allowed, PracticeRange.entries - allowed.toSet(), points, portfolio.baseCurrency, first?.toString(), last?.toString(),
            if (first != null && last != null) (last - first).toString() else null,
            if (first != null && last != null) PracticeEngine.percent(last - first, first)?.toString() else null,
            notes = listOfNotNull(
                "Values use end-of-day closing prices and the holdings you had on each day. Your portfolio didn't exist before ${created}.",
                if (carried) "When an exchange was closed for a holiday, the previous closing price is used for up to $MAX_CARRY_DAYS days." else null,
                if (points.any { it.value == null }) "Days with a missing price or exchange rate are left empty rather than estimated." else null,
                if (points.size < 2) "There isn't enough history yet for a chart. Check back after a few trading days." else null
            ))
    }

    // ---------- Challenges ----------

    suspend fun challenges(uid: String): List<PracticeChallengeView> {
        val data = load(uid)
        return PracticeChallenges.views(entitlement(uid, data), data.challenges)
    }

    suspend fun completeChallenge(uid: String, id: String, answer: ChallengeAnswer): List<PracticeChallengeView> {
        val challenge = PracticeChallenges.find(id) ?: throw UserDataException(404, "CHALLENGE_NOT_FOUND", "Challenge not found.")
        val entitlement = entitlement(uid)
        if (!challenge.freeAccess && !entitlement.has(PracticeCapability.ALL_CHALLENGES)) throw UserDataException(403, "CHALLENGE_LOCKED", "This challenge is part of the Practice trial and StockSteps+.")
        val quiz = challenge.quiz ?: throw UserDataException(400, "AUTO_COMPLETED", "This challenge completes when you make your first practice purchase.")
        if (quiz.options.none { it.id == answer.optionId }) throw UserDataException(400, "INVALID_ANSWER", "Choose one of the answers.")
        if (challenge.kind == ChallengeKind.QUIZ && answer.optionId != quiz.correctOptionId) throw UserDataException(400, "INCORRECT_ANSWER", "Not quite. ${quiz.explanation}")
        val data = store.updatePractice(uid) { current ->
            val next = if (id in current.challenges) current else current.copy(challenges = current.challenges + (id to now()), revision = current.revision + 1)
            next to next
        }
        return PracticeChallenges.views(entitlement, data.challenges)
    }

    // ---------- MOCK scenarios ----------

    /**
     * MOCK only: replaces the signed-in user's practice data with a deterministic scenario built from
     * fixture closes (no invented prices). Plan state uses the existing debug entitlement record.
     */
    suspend fun loadScenario(uid: String, name: String): PracticeOverview {
        if (!entitlements.debugAllowed) throw UserDataException(404, "NOT_FOUND", "Not found.")
        val t = now()
        val dayMs = 86_400_000L
        mockFlags.remove(uid)
        suspend fun fill(symbol: String, date: String, quantity: String, side: OrderSide = OrderSide.BUY, seq: Long): PracticeTransaction {
            val profile = market.profile(symbol)
            val instrument = instrument(symbol, profile, null) ?: error("no profile for $symbol")
            val close = market.closes(symbol).firstOrNull { it.time.startsWith(date) } ?: error("no close for $symbol on $date")
            val price = decimal(adjust(symbol, close.close, date))
            val rate = fxRate(instrument.currency, LocalDate.parse(date), null)!!.first
            val qty = Decimal.parse(quantity)
            val at = LocalDate.parse(date).atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            return PracticeTransaction("tx-scenario-$seq", 1, if (side == OrderSide.BUY) PracticeTransactionType.BUY else PracticeTransactionType.SELL, instrument,
                qty.toString(), price.toString(), instrument.currency, rate.toString(), PracticeEngine.tradeAmount(qty, price, rate).toString(), at, "scenario-$seq",
                "Sample: fixture closing price", date, sequence = seq)
        }
        val created = LocalDate.parse("2026-08-03").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val three = listOf(fill("AAPL", "2026-08-03", "5", seq = 1), fill("MSFT", "2026-08-03", "2", seq = 2), fill("RY.TO", "2026-09-01", "5", seq = 3))
        val eight = three + listOf(fill("NVDA", "2026-09-01", "4", seq = 4), fill("GOOGL", "2026-09-01", "2", seq = 5), fill("KO", "2026-09-01", "10", seq = 6),
            fill("SHOP.TO", "2026-09-21", "3", seq = 7), fill("SPY", "2026-09-21", "1", seq = 8))
        val activeTrial = PracticeTrial(t - 4 * dayMs, t + 10 * dayMs, t - 4 * dayMs, t - 4 * dayMs)
        val expiredTrial = PracticeTrial(t - 20 * dayMs, t - 6 * dayMs, t - 20 * dayMs, t - 20 * dayMs)
        var plan: StoredEntitlement? = null
        val (txs, trial) = when (name) {
            "empty" -> emptyList<PracticeTransaction>() to null
            "one-holding" -> listOf(three[0]) to null
            "three-holdings", "free-limit" -> three to null
            "eight-holdings-trial-expired" -> eight to expiredTrial
            "trial-available" -> listOf(three[0]) to null
            "trial-active" -> three to activeTrial
            "trial-expiring" -> three to PracticeTrial(t - 13 * dayMs, t + dayMs / 2, t - 13 * dayMs, t - 13 * dayMs)
            "trial-expired" -> three to expiredTrial
            "plus-active" -> eight.also { plan = StoredEntitlement(org.example.stocksteps.portfolio.analytics.SubscriptionTier.PLUS, null, "debug") } to null
            "plus-expired" -> eight.also { plan = StoredEntitlement(org.example.stocksteps.portfolio.analytics.SubscriptionTier.PLUS, t - 1, "debug") } to null
            "low-cash" -> listOf(fill("MSFT", "2026-08-03", "15", seq = 1)) to null
            "missing-quote" -> listOf(three[0]) to null
            "dividend" -> listOf(three[1]) to null
            "split" -> listOf(fill("NVDA", "2026-09-01", "4", seq = 1)) to null
            "missing-fx" -> listOf(three[0]) to null
            "stale-quote" -> listOf(three[0]) to null
            else -> throw UserDataException(400, "UNKNOWN_SCENARIO", "Unknown scenario.")
        }
        if (name in setOf("missing-fx", "stale-quote", "missing-quote")) mockFlags[uid] = setOf(name)
        store.setEntitlement(uid, plan)
        store.updatePractice(uid) { _ ->
            PracticeAccountData(PracticePortfolio("practice-1", 1, baseCurrency, PracticePolicy.STARTING_CASH, if (txs.isEmpty()) t else created, t),
                transactions = txs, trial = trial, challenges = if (txs.isEmpty()) emptyMap() else mapOf(PracticeChallenges.FIRST_BUY to created),
                revision = 1) to Unit
        }
        return overview(uid)
    }
}
