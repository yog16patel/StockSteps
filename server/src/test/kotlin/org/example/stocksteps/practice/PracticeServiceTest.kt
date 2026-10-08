package org.example.stocksteps.practice

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** A clock tests can move (trial expiry, history). */
class MutableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = instant
    fun advanceDays(days: Long) { instant = instant.plusSeconds(days * 86_400) }
}

/** Deterministic market: Wednesday 2026-10-07 after the close; quotes from that session. */
class FakeMarket : PracticeMarket {
    val sessionClose: Long = Instant.parse("2026-10-07T20:00:00Z").epochSecond
    val prices = mutableMapOf("AAPL" to 100.0, "MSFT" to 200.0, "KO" to 50.0, "RY.TO" to 150.0, "SPY" to 500.0, "NVDA" to 120.0,
        "GOOGL" to 80.0, "AMZN" to 90.0, "SHOP.TO" to 60.0, "XIU.TO" to 40.0, "EUR1" to 10.0)
    val timestamps = mutableMapOf<String, Long>()
    val currencies = mapOf("RY.TO" to "CAD", "SHOP.TO" to "CAD", "XIU.TO" to "CAD", "EUR1" to "EUR")
    val quoteCalls = AtomicInteger()
    override suspend fun quote(symbol: String): StockQuote? {
        quoteCalls.incrementAndGet()
        val price = prices[symbol] ?: return null
        return StockQuote(symbol, "$symbol Inc.", price, 0.0, 0.0, null, null, timestamp = timestamps[symbol] ?: sessionClose)
    }
    override suspend fun profile(symbol: String) = if (symbol !in prices && symbol != "GONE") null else
        CompanyProfile(symbol, "$symbol Inc.", currency = currencies[symbol] ?: "USD", exchange = if (symbol.endsWith(".TO")) "TSX" else "NASDAQ",
            sector = if (symbol in setOf("SPY", "XIU.TO")) null else "Technology", isEtf = symbol in setOf("SPY", "XIU.TO"))
    var history: Map<String, List<PricePoint>> = emptyMap()
    override suspend fun closes(symbol: String) = history[symbol].orEmpty()
}

class FakeFx(var rate: String? = "1.35") : PortfolioFxSource {
    override suspend fun rates(from: String, through: String): Map<String, String> {
        val r = rate ?: return emptyMap()
        var d = java.time.LocalDate.parse(from); val end = java.time.LocalDate.parse(through)
        return buildMap { while (d <= end) { if (d.dayOfWeek.value <= 5) put(d.toString(), r); d = d.plusDays(1) } }
    }
}

class PracticeServiceTest {
    private val store = InMemoryUserDataStore()
    private val clock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
    private val marketClock = MutableClock(Instant.parse("2026-10-07T21:15:00Z"))
    private val market = FakeMarket()
    private val fx = FakeFx()
    private val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
    private fun service(actions: CorporateActionSource? = null) = PracticeService(store, entitlements, market, fx, clock, marketClock, actions, sampleData = false)
    private val svc = service()
    private var keys = 0
    private fun key() = "key-${++keys}-abcdef"

    private suspend fun buy(symbol: String, qty: String = "1", uid: String = "alice", reviewed: String? = null): PracticeOrderResult {
        val preview = svc.preview(uid, PracticeOrderRequest(symbol, OrderSide.BUY, quantity = qty))
        return svc.execute(uid, PracticeOrderRequest(symbol, OrderSide.BUY, quantity = qty, idempotencyKey = key(), reviewedPrice = reviewed ?: preview.price))
    }
    private suspend fun sell(symbol: String, qty: String, uid: String = "alice") =
        svc.execute(uid, PracticeOrderRequest(symbol, OrderSide.SELL, quantity = qty, idempotencyKey = key(), reviewedPrice = market.prices[symbol].toString()))
    private fun code(block: suspend () -> Unit): String = runBlocking { assertFailsWith<UserDataException> { block() }.code }
    private fun d(v: String) = Decimal.parse(v)

    // ---------- Accounting ----------

    @Test fun startsWithTenThousandVirtualDollars(): Unit = runBlocking {
        val o = svc.overview("alice")
        assertEquals("10000", o.cash)
        assertEquals("10000", o.totalValue)
        assertEquals("0", o.totalGain)
        assertEquals(PracticeAccess.FREE, o.entitlement.access)
        assertEquals(3, o.entitlement.maxOpenHoldings)
        assertTrue(o.entitlement.trialEligible)
    }

    @Test fun buyConvertsUsdToCadAndKeepsExactCash(): Unit = runBlocking {
        val r = buy("AAPL", "3")
        // 3 × 100 USD × 1.35 = 405.00 CAD
        assertEquals("405", r.transaction.amount)
        assertEquals("1.35", r.transaction.fxRate)
        assertEquals("9595", r.cashAfter)
        val cad = buy("RY.TO", "2")
        assertEquals("300", cad.transaction.amount)
        assertEquals("1", cad.transaction.fxRate)
    }

    @Test fun weightedAverageCostAndRealizedGain(): Unit = runBlocking {
        buy("MSFT", "2")            // 2 × 200 × 1.35 = 540
        market.prices["MSFT"] = 300.0
        buy("MSFT", "1")            // 405 → cost 945 for 3 shares, average 315
        val held = svc.overview("alice").holdings.single()
        assertEquals("3", held.quantity)
        assertEquals("315.0000", held.averageCost)
        val r = sell("MSFT", "1")   // proceeds 405; cost removed 315; realized 90
        assertEquals("405", r.transaction.amount)
        val o = svc.overview("alice")
        assertEquals("90", o.realizedGain)
        assertEquals("630", o.holdings.single().costBasis)
        // value 2 × 300 × 1.35 = 810; unrealized 180; total = cash 9460 + 810 = 10270
        assertEquals("810", o.holdings.single().marketValue)
        assertEquals("180", o.unrealizedGain)
        assertEquals("10270", o.totalValue)
        assertEquals("270", o.totalGain)
        assertEquals("2.7", o.totalGainPercent)
    }

    @Test fun fullSaleClosesThePositionAndFreesASlot(): Unit = runBlocking {
        buy("AAPL"); buy("MSFT"); buy("KO")
        assertEquals("HOLDING_LIMIT", code { buy("GOOGL") })
        sell("KO", "1")
        assertEquals(2, svc.overview("alice").openHoldings)
        buy("GOOGL")
        assertEquals(3, svc.overview("alice").openHoldings)
        assertEquals(5, svc.transactions("alice", null).items.size, "closed holdings keep their history")
    }

    @Test fun fractionalSharesAndAmountOrders(): Unit = runBlocking {
        val preview = svc.preview("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, amount = "1000"))
        // 1000 / (100 × 1.35) = 7.4074… → 7.4074 shares (never rounded up), cost 999.999 → 1000.00
        assertEquals("7.4074", preview.quantity)
        assertEquals("1000", preview.estimatedTotal)
        val r = svc.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, amount = "1000", idempotencyKey = key(), reviewedPrice = preview.price))
        assertEquals("7.4074", r.transaction.quantity)
        assertEquals("INVALID_QUANTITY", code { buy("AAPL", "0.00001") })
        assertEquals("INVALID_QUANTITY", code { buy("AAPL", "-1") })
    }

    @Test fun cashAndSharesCanNeverGoNegative(): Unit = runBlocking {
        val p = svc.preview("alice", PracticeOrderRequest("SPY", OrderSide.BUY, quantity = "20"))
        assertEquals("INSUFFICIENT_CASH", p.blocker?.code)
        assertTrue(p.blocker!!.message.contains("You have \$10,000.00 in virtual cash"))
        assertTrue(p.blocker!!.message.contains("approximately \$13,500.00"))
        assertEquals("INSUFFICIENT_CASH", code { buy("SPY", "20") })
        buy("AAPL", "2")
        assertEquals("INSUFFICIENT_SHARES", code { sell("AAPL", "3") })
        assertEquals("NO_HOLDING", code { sell("MSFT", "1") })
        assertEquals("9730", svc.overview("alice").cash)
    }

    // ---------- Pricing policy ----------

    @Test fun staleMissingUnsupportedAndNoFxBlockTrading(): Unit = runBlocking {
        market.timestamps["AAPL"] = market.sessionClose - 3 * 86_400
        assertEquals("QUOTE_STALE", svc.preview("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1")).blocker?.code)
        assertEquals("QUOTE_STALE", code { svc.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1", idempotencyKey = key(), reviewedPrice = "100")) })
        assertEquals("UNSUPPORTED_INSTRUMENT", svc.preview("alice", PracticeOrderRequest("NOPE", OrderSide.BUY, quantity = "1")).blocker?.code)
        assertEquals("UNSUPPORTED_INSTRUMENT", svc.preview("alice", PracticeOrderRequest("EUR1", OrderSide.BUY, quantity = "1")).blocker?.code)
        market.prices.remove("GONE")
        assertEquals("QUOTE_MISSING", svc.preview("alice", PracticeOrderRequest("GONE", OrderSide.BUY, quantity = "1")).blocker?.code)
        fx.rate = null
        assertEquals("FX_UNAVAILABLE", svc.preview("alice", PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "1")).blocker?.code)
        assertNull(svc.preview("alice", PracticeOrderRequest("RY.TO", OrderSide.BUY, quantity = "1")).blocker, "CAD needs no conversion")
        assertTrue(svc.preview("alice", PracticeOrderRequest("RY.TO", OrderSide.BUY, quantity = "1")).priceBasis.contains("Latest closing price"))
    }

    @Test fun priceIsServerAuthoritativeAndAMaterialMoveNeedsAFreshReview(): Unit = runBlocking {
        // A forged "reviewed" price can't set the fill price: it's only compared with the server's.
        assertEquals("PRICE_CHANGED", code { svc.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1", idempotencyKey = key(), reviewedPrice = "1")) })
        val small = svc.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1", idempotencyKey = key(), reviewedPrice = "100.5"))
        assertEquals("100", small.transaction.price)
        assertEquals("REVIEW_REQUIRED", code { svc.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1", idempotencyKey = key())) })
    }

    @Test fun duplicateSubmissionsFillOnceAndConflictingReuseIsRefused(): Unit = runBlocking {
        val request = PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "1", idempotencyKey = "same-key-123", reviewedPrice = "100")
        val results = (1..8).map { async(Dispatchers.Default) { svc.execute("alice", request) } }.awaitAll()
        assertEquals(1, results.map { it.transaction.id }.toSet().size)
        assertEquals(7, results.count { it.replayed })
        assertEquals(1, svc.transactions("alice", null).items.size)
        assertEquals("IDEMPOTENCY_CONFLICT", code { svc.execute("alice", request.copy(quantity = "2")) })
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", code { svc.execute("alice", request.copy(idempotencyKey = null)) })
    }

    @Test fun concurrentOrdersCannotOverspendOrBypassTheLimit(): Unit = runBlocking {
        buy("AAPL"); buy("MSFT")
        val symbols = listOf("KO", "GOOGL", "AMZN", "NVDA", "SPY", "SHOP.TO")
        val outcomes = symbols.map { s -> async(Dispatchers.Default) { runCatching { buy(s) } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess }, "only one new holding fits in the free limit")
        assertTrue(outcomes.filter { it.isFailure }.all { (it.exceptionOrNull() as UserDataException).code == "HOLDING_LIMIT" })
        assertEquals(3, svc.overview("alice").openHoldings)
        // Cash: many parallel buys of an existing holding never overspend.
        val more = (1..20).map { async(Dispatchers.Default) { runCatching { buy("AAPL", "5") } } }.awaitAll()
        val cash = d(svc.overview("alice").cash)
        assertTrue(cash >= Decimal.ZERO)
        assertTrue(more.count { it.isSuccess } < 20)
    }

    // ---------- Entitlements ----------

    @Test fun freeUserLimitAppliesOnlyToNewDistinctHoldings(): Unit = runBlocking {
        buy("AAPL"); buy("MSFT"); buy("KO")                       // 1–3
        assertEquals("HOLDING_LIMIT", code { buy("GOOGL") })        // 4th refused
        val limited = svc.preview("alice", PracticeOrderRequest("GOOGL", OrderSide.BUY, quantity = "1"))
        assertTrue(limited.opensNewHolding)
        assertEquals("HOLDING_LIMIT", limited.blocker?.code)
        repeat(5) { buy("AAPL") }                                   // more of an existing holding: unlimited trades
        sell("MSFT", "1")                                           // close a position
        buy("GOOGL")                                                // replacement allowed
        assertEquals(3, svc.overview("alice").openHoldings)
    }

    @Test fun trialStartsOnlyOnRequestOnceAndSurvivesReinstallAndReset(): Unit = runBlocking {
        assertEquals(TrialStatus.NOT_STARTED, svc.entitlement("alice").trialStatus)
        val started = svc.activateTrial("alice")
        assertEquals(PracticeAccess.TRIAL, started.access)
        assertEquals(clock.millis() + 14L * 86_400_000, started.trialEndsAt)
        assertNull(started.maxOpenHoldings)
        assertEquals(14, started.trialDaysLeft)
        clock.advanceDays(3)
        val again = svc.activateTrial("alice")
        assertEquals(started.trialEndsAt, again.trialEndsAt, "a second request never restarts or extends")
        // A new service instance (new app install / device) reads the same server record.
        assertEquals(started.trialEndsAt, service().entitlement("alice").trialEndsAt)
        svc.reset("alice", ResetRequest(true, "reset-key-1"))
        assertEquals(started.trialEndsAt, svc.entitlement("alice").trialEndsAt, "reset can't restart the trial")
        assertFalse(svc.entitlement("alice").trialEligible)
        // Concurrent activations by another user record one trial.
        val parallel = (1..10).map { async(Dispatchers.Default) { svc.activateTrial("bob") } }.awaitAll()
        assertEquals(1, parallel.map { it.trialEndsAt }.toSet().size)
    }

    @Test fun expiredTrialKeepsEverythingAndOnlyBlocksNewHoldingsAtTheLimit(): Unit = runBlocking {
        svc.activateTrial("alice")
        val eight = listOf("AAPL", "MSFT", "KO", "GOOGL", "AMZN", "NVDA", "SPY", "SHOP.TO")
        eight.forEach { buy(it) }
        assertEquals(8, svc.overview("alice").openHoldings)
        clock.advanceDays(15)
        val expired = svc.overview("alice")
        assertEquals(TrialStatus.EXPIRED, expired.entitlement.trialStatus)
        assertEquals(PracticeAccess.FREE, expired.entitlement.access)
        assertEquals(8, expired.holdings.size, "all holdings stay visible")
        assertTrue(expired.showTrialExpiredNotice)
        svc.acknowledgeTrialNotice("alice")
        assertFalse(svc.overview("alice").showTrialExpiredNotice, "the notice is shown once")
        assertNull(expired.allocation, "premium analytics are locked again")
        sell("SPY", "1")                                            // sell allowed
        buy("AAPL", "2")                                            // buying more of an existing holding allowed
        assertEquals("HOLDING_LIMIT", code { buy("XIU.TO") })       // 8th → 7: still above the limit
        listOf("MSFT", "KO", "GOOGL", "AMZN", "NVDA").forEach { sell(it, "1") }
        assertEquals(2, svc.overview("alice").openHoldings)
        buy("XIU.TO")                                               // below 3 again: one new holding
        assertEquals("HOLDING_LIMIT", code { buy("RY.TO") })
        assertFalse(svc.entitlement("alice").trialEligible, "an expired trial is never offered again")
        assertEquals("16", svc.transactions("alice", null).items.size.toString())
    }

    @Test fun plusIsUnlimitedExpiryRestoresLimitsAndRenewalRestoresAccess(): Unit = runBlocking {
        store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        listOf("AAPL", "MSFT", "KO", "GOOGL", "AMZN").forEach { buy(it) }
        val plus = svc.entitlement("alice")
        assertEquals(PracticeAccess.PLUS, plus.access)
        assertTrue(plus.has(PracticeCapability.ALLOCATION))
        assertEquals("PLUS_ACTIVE", code { svc.activateTrial("alice") })
        store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() - 1, "subscription"))
        assertEquals(PracticeAccess.FREE, svc.entitlement("alice").access)
        assertEquals("HOLDING_LIMIT", code { buy("NVDA") })
        assertEquals(5, svc.overview("alice").holdings.size)
        assertTrue(svc.entitlement("alice").trialEligible, "an expired plan doesn't use up the trial")
        store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        buy("NVDA")
        assertEquals(6, svc.overview("alice").openHoldings)
    }

    @Test fun plusAfterATrialIsReportedAsConverted(): Unit = runBlocking {
        svc.activateTrial("alice")
        store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        assertEquals(TrialStatus.CONVERTED_TO_PLUS, svc.entitlement("alice").trialStatus)
        clock.advanceDays(30)
        assertEquals(PracticeAccess.PLUS, svc.entitlement("alice").access)
    }

    @Test fun debugPlanRecordsAreIgnoredOutsideMock(): Unit = runBlocking {
        val real = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = false), market, fx, clock, marketClock, null, false)
        store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, null, "debug"))
        assertEquals(PracticeAccess.FREE, real.entitlement("alice").access)
        assertEquals("NOT_FOUND", code { real.loadScenario("alice", "empty") })
    }

    // ---------- Performance, allocation, reset, challenges ----------

    @Test fun historyUsesHoldingsAsOfEachDayAndNeverBeforeCreation(): Unit = runBlocking {
        clock.instant = Instant.parse("2026-10-05T15:00:00Z")      // portfolio created Monday
        buy("RY.TO", "10")                                         // 1,500 CAD on Monday
        clock.instant = Instant.parse("2026-10-08T15:00:00Z")
        market.history = mapOf("RY.TO" to listOf(PricePoint("2026-10-02", 140.0), PricePoint("2026-10-05", 150.0), PricePoint("2026-10-06", 160.0), PricePoint("2026-10-07", 155.0)))
        val perf = svc.performance("alice", "1M")
        assertEquals(listOf("2026-10-05", "2026-10-06", "2026-10-07"), perf.points.map { it.date }, "no point before the portfolio existed")
        assertEquals(listOf("10000", "10100", "10050"), perf.points.map { it.value })
        assertEquals("50", perf.change)
        assertEquals("RANGE_LOCKED", code { svc.performance("alice", "1Y") })
        assertEquals(listOf(PracticeRange.THREE_MONTHS, PracticeRange.YEAR, PracticeRange.ALL), perf.locked)
    }

    @Test fun missingPricesAreNeverEstimated(): Unit = runBlocking {
        buy("AAPL")
        market.prices.remove("AAPL")
        val o = svc.overview("alice")
        assertNull(o.totalValue)
        assertEquals(ValuationStatus.UNAVAILABLE, o.valuationStatus)
        assertNull(o.holdings.single().marketValue)
        assertTrue(o.notices.any { "aren't available" in it })
    }

    @Test fun allocationAndPremiumInsightsNeedTheCapability(): Unit = runBlocking {
        buy("AAPL", "10"); buy("SPY", "1")
        val free = svc.overview("alice")
        assertNull(free.allocation)
        assertTrue(free.insights.none { it.premium })
        assertNull(free.holdings.first().weightPercent)
        svc.activateTrial("alice")
        val trial = svc.overview("alice")
        assertEquals(listOf("AAPL", "SPY", "Cash"), trial.allocation!!.map { it.label })
        assertTrue(trial.insights.any { it.id == "mix" && it.text.contains("both individual stocks and ETFs") })
        assertTrue(trial.insights.any { it.id == "concentration" })
        val words = trial.insights.joinToString(" ") { it.text }.lowercase()
        listOf("should buy", "guaranteed", "outperform", "safe").forEach { assertFalse(it in words, it) }
    }

    @Test fun resetArchivesAndStartsFreshWithoutTouchingTrialOrChallenges(): Unit = runBlocking {
        buy("AAPL", "5")
        svc.activateTrial("alice")
        val before = svc.overview("alice")
        assertNotNull(before.challenges.first { it.id == PracticeChallenges.FIRST_BUY }.completedAt)
        val after = svc.reset("alice", ResetRequest(true, "reset-key-abc"))
        assertEquals(2, after.generation)
        assertEquals("10000", after.cash)
        assertTrue(after.holdings.isEmpty())
        assertNotNull(after.challenges.first { it.id == PracticeChallenges.FIRST_BUY }.completedAt)
        assertEquals(PracticeAccess.TRIAL, after.entitlement.access)
        assertEquals(2, svc.reset("alice", ResetRequest(true, "reset-key-abc")).generation, "a repeated reset request is a no-op")
        assertEquals("CONFIRMATION_REQUIRED", code { svc.reset("alice", ResetRequest(false, "reset-key-xyz")) })
        assertTrue(svc.transactions("alice", null).items.isEmpty())
    }

    @Test fun challengesNeedCorrectAnswersAndPremiumButStayCompleted(): Unit = runBlocking {
        val free = svc.challenges("alice")
        assertTrue(free.first().available)
        assertTrue(free.drop(1).none { it.available })
        assertEquals("CHALLENGE_LOCKED", code { svc.completeChallenge("alice", "explore-etf", ChallengeAnswer("a")) })
        assertEquals("AUTO_COMPLETED", code { svc.completeChallenge("alice", PracticeChallenges.FIRST_BUY, ChallengeAnswer("a")) })
        svc.activateTrial("alice")
        assertEquals("INCORRECT_ANSWER", code { svc.completeChallenge("alice", "explore-etf", ChallengeAnswer("b")) })
        svc.completeChallenge("alice", "explore-etf", ChallengeAnswer("a"))
        svc.completeChallenge("alice", "review-decisions", ChallengeAnswer("c"))  // a reflection accepts any option
        clock.advanceDays(20)
        val later = svc.challenges("alice")
        assertNotNull(later.first { it.id == "explore-etf" }.completedAt, "completed challenges stay after the trial")
        assertFalse(later.first { it.id == "market-loss" }.available)
    }

    @Test fun dividendsAndSplitsApplyOnceFromHoldingsBeforeTheExDate(): Unit = runBlocking {
        val actions = CorporateActionSource { symbol ->
            listOf(
                CorporateAction("div-1", "AAPL", PracticeTransactionType.DIVIDEND, exDate = "2026-10-06", payDate = "2026-10-07", perShare = "0.50", note = "Test dividend"),
                CorporateAction("split-1", "MSFT", PracticeTransactionType.SPLIT_ADJUSTMENT, exDate = "2026-10-06", ratio = "3", note = "Test split")
            ).filter { it.symbol == symbol }
        }
        val withActions = service(actions)
        clock.instant = Instant.parse("2026-10-02T15:00:00Z")
        listOf("AAPL" to "10", "MSFT" to "2").forEach { (s, q) ->
            withActions.execute("alice", PracticeOrderRequest(s, OrderSide.BUY, quantity = q, idempotencyKey = key(), reviewedPrice = market.prices[s].toString()))
        }
        clock.instant = Instant.parse("2026-10-06T15:00:00Z")        // bought on the ex-date: not eligible
        withActions.execute("alice", PracticeOrderRequest("AAPL", OrderSide.BUY, quantity = "5", idempotencyKey = key(), reviewedPrice = "100"))
        clock.instant = Instant.parse("2026-10-08T15:00:00Z")
        repeat(3) { withActions.overview("alice") }                  // processed once however often it loads
        val txs = withActions.transactions("alice", null).items
        val dividend = txs.single { it.type == PracticeTransactionType.DIVIDEND }
        assertEquals("10", dividend.quantity, "eligibility uses shares held before the ex-date")
        assertEquals("6.75", dividend.amount)                         // 10 × 0.50 × 1.35
        assertEquals(1, txs.count { it.type == PracticeTransactionType.SPLIT_ADJUSTMENT })
        val o = withActions.overview("alice")
        val msft = o.holdings.single { it.instrument.id == "MSFT" }
        assertEquals("6", msft.quantity)
        assertEquals("540", msft.costBasis, "a split changes shares, not cost")
        assertEquals("90.0000", msft.averageCost)
        assertEquals("6.75", o.dividends)
    }

    @Test fun usersAreIsolated(): Unit = runBlocking {
        buy("AAPL", "1", uid = "alice")
        assertTrue(svc.overview("bob").holdings.isEmpty())
        assertEquals("10000", svc.overview("bob").cash)
    }
}

class PracticeRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun signInIsRequiredAndScenariosAreMockOnly() = testApplication {
        val store = InMemoryUserDataStore()
        val clock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
        val svc = PracticeService(store, EntitlementService(store, clock::millis, true), FakeMarket(), FakeFx(), clock, MutableClock(Instant.parse("2026-10-07T21:15:00Z")), null, false)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { practiceRoutes(MockUserAuthenticator(), svc, mock = false) }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/practice").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/practice/orders/execute").status)
        val overview = client.get("/api/v1/me/practice") { header(HttpHeaders.Authorization, "Bearer mock-user:alice") }
        assertEquals(HttpStatusCode.OK, overview.status)
        assertEquals("10000", json.decodeFromString(PracticeOverview.serializer(), overview.bodyAsText()).cash)
        val forged = client.post("/api/v1/me/practice/orders/execute") {
            header(HttpHeaders.Authorization, "Bearer mock-user:alice"); contentType(ContentType.Application.Json)
            // Unknown fields (a client-chosen price, cash or plan) are ignored; the server prices the order.
            setBody("""{"symbol":"AAPL","side":"BUY","quantity":"1","idempotencyKey":"forged-key-1","reviewedPrice":"100","price":"0.01","cash":"999999","access":"PLUS"}""")
        }
        assertEquals(HttpStatusCode.OK, forged.status)
        assertEquals("135", json.decodeFromString(PracticeOrderResult.serializer(), forged.bodyAsText()).transaction.amount)
        val limit = client.get("/api/v1/me/practice/performance?range=ALL") { header(HttpHeaders.Authorization, "Bearer mock-user:alice") }
        assertEquals(HttpStatusCode.Forbidden, limit.status)
        assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/me/practice/debug/scenario") {
            header(HttpHeaders.Authorization, "Bearer mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"scenario":"empty"}""")
        }.status)
    }
}
