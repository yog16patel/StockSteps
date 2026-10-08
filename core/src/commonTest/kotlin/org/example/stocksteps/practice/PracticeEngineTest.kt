package org.example.stocksteps.practice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.InMemoryUserDataCache
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.model.User
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.EntitlementStatus
import kotlin.test.*

private val AAPL = PracticeInstrument("AAPL", "AAPL", "Apple", "NASDAQ", "USD", InstrumentKind.STOCK, "Technology")
private val RY = PracticeInstrument("RY.TO", "RY.TO", "Royal Bank", "TSX", "CAD", InstrumentKind.STOCK, "Financial Services")
private fun d(v: String) = Decimal.parse(v)
private fun tx(type: PracticeTransactionType, i: PracticeInstrument, qty: String, price: String, fx: String, at: Long, amount: String? = null) =
    PracticeTransaction("t$at", 1, type, i, qty, price, i.currency, fx, amount ?: PracticeEngine.tradeAmount(d(qty), d(price), d(fx)).toString(), at, null, "test", sequence = at)

class PracticePolicyTest {
    private val now = 1_000_000_000L
    private val day = 86_400_000L

    @Test fun accessMatrix() {
        assertEquals(3, PracticePolicy.maxOpenHoldings(PracticeAccess.FREE))
        assertNull(PracticePolicy.maxOpenHoldings(PracticeAccess.TRIAL))
        assertNull(PracticePolicy.maxOpenHoldings(PracticeAccess.PLUS))
        assertTrue(PracticePolicy.capabilities(PracticeAccess.FREE).isEmpty())
        assertEquals(PracticeCapability.entries.toSet(), PracticePolicy.capabilities(PracticeAccess.TRIAL))
        assertEquals(PracticePolicy.capabilities(PracticeAccess.TRIAL), PracticePolicy.capabilities(PracticeAccess.PLUS))
        assertTrue(PracticePolicy.canOpenNewHolding(PracticeAccess.FREE, 2))
        assertFalse(PracticePolicy.canOpenNewHolding(PracticeAccess.FREE, 3))
        assertFalse(PracticePolicy.canOpenNewHolding(PracticeAccess.FREE, 8))
        assertTrue(PracticePolicy.canOpenNewHolding(PracticeAccess.TRIAL, 50))
        assertEquals(listOf(PracticeRange.WEEK, PracticeRange.MONTH), PracticePolicy.ranges(PracticeAccess.FREE))
        assertEquals(PracticeRange.entries, PracticePolicy.ranges(PracticeAccess.PLUS))
    }

    @Test fun trialLifecycleIsExactlyFourteenDays() {
        val trial = PracticeTrial(now, now + PracticePolicy.TRIAL_MILLIS, now, now)
        assertEquals(14 * day, PracticePolicy.TRIAL_MILLIS)
        assertEquals(TrialStatus.NOT_STARTED, PracticePolicy.trialStatus(null, false, now))
        assertEquals(TrialStatus.ACTIVE, PracticePolicy.trialStatus(trial, false, now + 14 * day - 1))
        assertEquals(TrialStatus.EXPIRED, PracticePolicy.trialStatus(trial, false, now + 14 * day))
        assertEquals(TrialStatus.CONVERTED_TO_PLUS, PracticePolicy.trialStatus(trial, true, now + 20 * day))
        assertEquals(PracticeAccess.PLUS, PracticePolicy.access(true, trial, now + 20 * day))
        assertEquals(PracticeAccess.FREE, PracticePolicy.access(false, trial, now + 20 * day))
        val e = PracticePolicy.entitlement(EntitlementStatus.NONE, false, trial, now + 4 * day)
        assertEquals(10, e.trialDaysLeft)
        assertFalse(e.trialEligible)
        assertTrue(PracticePolicy.entitlement(EntitlementStatus.NONE, false, null, now).trialEligible)
    }
}

class PracticeEngineTest {
    @Test fun moneyAndQuantityRounding() {
        assertEquals(d("10.01"), PracticeEngine.money(d("10.005")))
        assertEquals(d("1.2345"), PracticeEngine.floorQuantity(d("1.23459")))
        assertEquals(d("7.4074"), PracticeEngine.quantityFor(d("1000"), d("100"), d("1.35")))
        assertEquals(d("405"), PracticeEngine.tradeAmount(d("3"), d("100"), d("1.35")))
        assertEquals(d("0.13"), PracticeEngine.tradeAmount(d("0.0001"), d("999.99"), d("1.35")))
        assertNull(PracticeEngine.validQuantity("0"))
        assertNull(PracticeEngine.validQuantity("1.00001"))
        assertNull(PracticeEngine.validQuantity("abc"))
        assertEquals(d("2.5"), PracticeEngine.validQuantity("2.5"))
    }

    @Test fun replayUsesWeightedAverageCostAndTracksRealizedGain() {
        val txs = listOf(
            tx(PracticeTransactionType.BUY, AAPL, "2", "200", "1.35", 1),   // 540
            tx(PracticeTransactionType.BUY, AAPL, "1", "300", "1.35", 2),   // 405 → 945 for 3
            tx(PracticeTransactionType.SELL, AAPL, "1", "300", "1.35", 3)   // 405 proceeds, 315 cost
        )
        val state = PracticeEngine.replay("10000", txs)
        assertEquals(d("9460"), state.cash)
        assertEquals(d("90"), state.realized)
        val p = state.positions.getValue("AAPL")
        assertEquals(d("2"), p.quantity)
        assertEquals(d("630"), p.cost)
        assertEquals("315.0000", p.averageCost.display(4))
    }

    @Test fun partialSaleWithThirdsKeepsExactCostAndFullSaleCloses() {
        val buy = tx(PracticeTransactionType.BUY, RY, "3", "100", "1", 1)          // cost 300
        val partial = PracticeEngine.replay("10000", listOf(buy, tx(PracticeTransactionType.SELL, RY, "1", "100", "1", 2)))
        assertEquals(d("200"), partial.positions.getValue("RY.TO").cost)
        assertEquals(d("0"), partial.realized)
        val closed = PracticeEngine.replay("10000", listOf(buy, tx(PracticeTransactionType.SELL, RY, "3", "110", "1", 2)))
        assertTrue(closed.positions.isEmpty())
        assertEquals(d("30"), closed.realized)
        assertEquals(d("10030"), closed.cash)
    }

    @Test fun dividendsAndSplits() {
        val txs = listOf(
            tx(PracticeTransactionType.BUY, AAPL, "10", "100", "1.35", 1),
            tx(PracticeTransactionType.DIVIDEND, AAPL, "10", "0.5", "1.35", 2),
            tx(PracticeTransactionType.SPLIT_ADJUSTMENT, AAPL, "2", "0", "1", 3, amount = "0")
        )
        val s = PracticeEngine.replay("10000", txs)
        assertEquals(d("6.75"), s.dividends)
        assertEquals(d("8656.75"), s.cash)
        assertEquals(d("20"), s.positions.getValue("AAPL").quantity)
        assertEquals(d("1350"), s.positions.getValue("AAPL").cost)
    }

    @Test fun blockersCoverCashSharesAndLimits() {
        val three = PracticeEngine.replay("10000", listOf("A", "B", "C").mapIndexed { i, s -> tx(PracticeTransactionType.BUY, RY.copy(id = s, symbol = s), "1", "10", "1", i.toLong()) })
        assertEquals("HOLDING_LIMIT", PracticeEngine.blocker(three, PracticeAccess.FREE, OrderSide.BUY, "D", d("1"), d("10"), "CAD")?.code)
        assertNull(PracticeEngine.blocker(three, PracticeAccess.FREE, OrderSide.BUY, "A", d("1"), d("10"), "CAD"), "more of an existing holding")
        assertNull(PracticeEngine.blocker(three, PracticeAccess.TRIAL, OrderSide.BUY, "D", d("1"), d("10"), "CAD"))
        assertNull(PracticeEngine.blocker(three, PracticeAccess.FREE, OrderSide.SELL, "A", d("1"), d("10"), "CAD"))
        assertEquals("INSUFFICIENT_SHARES", PracticeEngine.blocker(three, PracticeAccess.FREE, OrderSide.SELL, "A", d("2"), d("20"), "CAD")?.code)
        assertEquals("INSUFFICIENT_CASH", PracticeEngine.blocker(three, PracticeAccess.PLUS, OrderSide.BUY, "D", d("1"), d("99999"), "CAD")?.code)
        assertEquals("INVALID_QUANTITY", PracticeEngine.blocker(three, PracticeAccess.PLUS, OrderSide.BUY, "D", d("0"), d("0"), "CAD")?.code)
    }

    @Test fun valuationNeedsEveryPriceAndConvertsCurrency() {
        val s = PracticeEngine.replay("10000", listOf(tx(PracticeTransactionType.BUY, AAPL, "2", "100", "1.35", 1), tx(PracticeTransactionType.BUY, RY, "1", "50", "1", 2)))
        val full = PracticeEngine.value(s, mapOf("AAPL" to PracticePrice(d("110"), "USD", d("1.36"), null), "RY.TO" to PracticePrice(d("55"), "CAD", Decimal.ONE, null)))
        assertEquals(d("299.2"), full.values["AAPL"])
        assertEquals(d("354.2"), full.holdingsValue)
        assertEquals(d("10034.2"), full.totalValue)      // cash 9680 + 354.20
        assertEquals(d("34.2"), full.unrealized)
        val partial = PracticeEngine.value(s, mapOf("RY.TO" to PracticePrice(d("55"), "CAD", Decimal.ONE, null)))
        assertNull(partial.totalValue)
        assertEquals(ValuationStatus.PARTIAL, partial.status)
        assertEquals(ValuationStatus.COMPLETE, PracticeEngine.value(PracticeEngine.replay("10000", emptyList()), emptyMap()).status)
    }

    @Test fun historyNeverAppliesTodaysHoldingsToThePast() {
        val day = 86_400_000L
        val dayOf = { ms: Long -> listOf("2026-10-01", "2026-10-02", "2026-10-03")[(ms / day).toInt()] }
        val txs = listOf(tx(PracticeTransactionType.BUY, RY, "10", "100", "1", day + 1))   // bought on 10-02
        val closes = mapOf("2026-10-01" to mapOf("RY.TO" to PracticePrice(d("90"), "CAD", Decimal.ONE, null)),
            "2026-10-02" to mapOf("RY.TO" to PracticePrice(d("100"), "CAD", Decimal.ONE, null)),
            "2026-10-03" to emptyMap())
        val points = PracticeEngine.history("10000", "2026-10-01", txs, listOf("2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03"), closes, dayOf)
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), points.map { it.date })
        assertEquals("10000", points[0].value, "cash only before the purchase")
        assertEquals("10000", points[1].value)
        assertNull(points[2].value, "a missing close is a gap, not an estimate")
    }

    @Test fun insightsStateFactsOnly() {
        fun h(i: PracticeInstrument, v: String) = PracticeHoldingView(i, "1", "1", "1", "1", i.currency, "1", v, "0", "0")
        val spy = PracticeInstrument("SPY", "SPY", "SPDR", "NYSE", "USD", InstrumentKind.ETF)
        val free = PracticeInsights.build(listOf(h(AAPL, "650"), h(spy, "350")), d("250"), d("1250"), premium = false, "CAD")
        assertEquals(listOf("count", "cash"), free.map { it.id })
        assertEquals("Your virtual cash represents 20% of total portfolio value.", free[1].text)
        val premium = PracticeInsights.build(listOf(h(AAPL, "650"), h(spy, "350")), d("250"), d("1250"), premium = true, "CAD")
        assertTrue(premium.any { it.text == "AAPL represents 65% of your simulated holdings. When one investment is this large, its price moves have a big effect on the whole portfolio." })
        assertTrue(premium.any { it.text == "Your portfolio includes both individual stocks and ETFs." })
        assertTrue(premium.any { it.id == "currency" }.not())
    }

    @Test fun challengesUnlockWithTheCapabilityAndKeepCompletions() {
        val free = PracticePolicy.entitlement(EntitlementStatus.NONE, false, null, 0)
        val views = PracticeChallenges.views(free, mapOf("market-loss" to 5L))
        assertEquals(5, views.size)
        assertTrue(views.first().available)
        assertFalse(views[3].available)
        assertEquals(5L, views[3].completedAt)
        assertTrue(PracticeChallenges.catalog.none { it.summary.contains("profit", ignoreCase = true) })
    }
}

// ---------- Presenters ----------

private class Auth(uid: String?) : AuthRepository {
    val mutable = MutableStateFlow(AuthSession(uid?.let { User(it, null) }, initializing = false))
    override val session: StateFlow<AuthSession> = mutable
    override val currentUser = mutable.map { it.user }
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String) = Unit
    override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
}

private class FakeRemote : PracticeRemote {
    var online = true
    val trials = mutableMapOf<String, PracticeEntitlement>()
    val executed = mutableListOf<PracticeOrderRequest>()
    var executeFailures = 0
    var priceChangedOnce = false
    var price = "100"
    fun entitlement(uid: String) = trials[uid] ?: PracticePolicy.entitlement(EntitlementStatus.NONE, false, null, 0)
    private fun check() { if (!online) throw IllegalStateException("offline") }
    override suspend fun overview(uid: String): PracticeOverview { check(); return PracticeOverview("p-$uid", 1, "CAD", "10000", "10000", "0", "10000", "0", "0", valuationStatus = ValuationStatus.COMPLETE,
        openHoldings = if (uid == "full") 3 else 0, entitlement = entitlement(uid), createdAt = 0, challenges = PracticeChallenges.views(entitlement(uid), emptyMap())) }
    override suspend fun transactions(uid: String, type: PracticeTransactionType?) = PracticeTransactions(emptyList(), "CAD")
    override suspend fun performance(uid: String, range: PracticeRange) = PracticePerformance(range, baseCurrency = "CAD")
    override suspend fun preview(uid: String, request: PracticeOrderRequest): PracticeOrderPreview {
        check()
        val qty = request.quantity ?: "2"
        val blocker = if (uid == "full") PracticeBlocker("HOLDING_LIMIT", "limit") else null
        return PracticeOrderPreview(AAPL, request.side, qty, price, "USD", "1.35", null, "CAD", "270", "10000", "9730", null, "Latest closing price", "0", true, 0, 3, blocker)
    }
    override suspend fun execute(uid: String, request: PracticeOrderRequest): PracticeOrderResult {
        executed += request
        if (executeFailures > 0) { executeFailures--; throw IllegalStateException("network") }
        if (priceChangedOnce) { priceChangedOnce = false; price = "110"; throw StockStepsApiException(409, ApiError("PRICE_CHANGED", "The price moved since your review.")) }
        return PracticeOrderResult(PracticeTransaction("tx", 1, PracticeTransactionType.BUY, AAPL, request.quantity!!, request.reviewedPrice!!, "USD", "1.35", "270", 1, request.idempotencyKey, "test"), "9730", "CAD")
    }
    override suspend fun reset(uid: String, request: ResetRequest) = overview(uid)
    override suspend fun activateTrial(uid: String): PracticeEntitlement { check(); return trials.getOrPut(uid) { PracticePolicy.entitlement(EntitlementStatus.NONE, false, PracticeTrial(0, PracticePolicy.TRIAL_MILLIS, 0, 0), 0) } }
    override suspend fun acknowledgeTrialNotice(uid: String) = entitlement(uid)
    override suspend fun completeChallenge(uid: String, id: String, optionId: String) = PracticeChallenges.views(entitlement(uid), mapOf(id to 1L))
    override suspend fun scenario(uid: String, name: String) = overview(uid)
}

class PracticePresenterTest {
    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean) = withTimeout(5_000) { first(predicate) }

    @Test fun accountSwitchNeverShowsAnotherUsersPortfolioAndOfflineShowsTheCache(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = Auth("alice")
        val remote = FakeRemote()
        val cache = InMemoryUserDataCache()
        try {
            val p = PracticePresenter(remote, auth, cache, MutableStateFlow("mock"), scope).also { it.start() }
            assertEquals("p-alice", p.state.await { it.overview != null && !it.offline }.overview!!.portfolioId)
            withTimeout(5_000) { while (cache.read("mock|user:alice", PracticePresenter.KEY) == null) delay(10) }
            auth.mutable.value = AuthSession(User("bob", null), initializing = false)
            assertEquals("p-bob", p.state.await { it.uid == "bob" && it.overview != null }.overview!!.portfolioId)
            auth.mutable.value = AuthSession(initializing = false)
            val signedOut = p.state.await { it.uid == null }
            assertNull(signedOut.overview)
            assertFalse(signedOut.canTrade)
            remote.online = false
            auth.mutable.value = AuthSession(User("alice", null), initializing = false)
            val offline = p.state.await { it.uid == "alice" && it.error != null }
            assertTrue(offline.offline)
            assertEquals("p-alice", offline.overview!!.portfolioId)
            assertFalse(offline.canTrade, "no trading or plan changes while the entitlement can't be confirmed")
        } finally { scope.cancel() }
    }

    @Test fun trialIsStartedOnlyFromTheConfirmationAndLockedRangesOfferIt(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = FakeRemote()
        try {
            val p = PracticePresenter(remote, Auth("alice"), InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
            p.state.await { it.overview != null }
            assertEquals("0 of 3 free holdings used", p.state.value.accessLabel)
            assertTrue(remote.trials.isEmpty(), "opening Practice never starts the trial")
            p.selectRange(PracticeRange.YEAR)
            assertEquals(PracticeSheet.Locked("Longer performance history", true), p.state.value.sheet)
            p.showTrialConfirm()
            assertEquals(PracticeSheet.TrialConfirm, p.state.value.sheet)
            p.startTrial()
            val active = p.state.await { it.entitlement?.access == PracticeAccess.TRIAL }
            assertNull(active.sheet)
            assertEquals("14 days left in your Practice trial", active.accessLabel)
            p.showTrialConfirm()
            assertEquals(PracticeSheet.PlusInfo, p.state.value.sheet, "no second trial offer")
        } finally { scope.cancel() }
    }

    @Test fun orderFlowReusesItsKeyOnRetryAndRereviewsAfterAPriceMove(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = FakeRemote()
        var keys = 0
        try {
            val order = PracticeOrderPresenter("aapl", OrderSide.BUY, remote, { "alice" }, scope, newKey = { "key-${++keys}" }, debounceMillis = 0)
            order.start()
            assertEquals("0", order.state.await { it.preview != null }.preview!!.quantity, "the opening preview shows the price only")
            order.setInput("2")
            order.state.await { it.preview?.quantity == "2" }
            order.review()
            assertEquals(OrderStep.REVIEW, order.state.value.step)
            remote.executeFailures = 1
            order.confirm()
            assertTrue(order.state.await { it.error != null && !it.executing }.error!!.contains("won't place it twice"))
            order.confirm()
            order.state.await { it.step == OrderStep.DONE }
            assertEquals(listOf("key-1", "key-1"), remote.executed.map { it.idempotencyKey }, "a retry reuses the same request id")
            assertEquals("100", remote.executed.last().reviewedPrice)

            val moved = PracticeOrderPresenter("AAPL", OrderSide.BUY, remote, { "alice" }, scope, newKey = { "key-${++keys}" }, debounceMillis = 0)
            moved.setInput("2"); moved.state.await { it.preview?.quantity == "2" }
            remote.priceChangedOnce = true
            moved.review(); moved.confirm()
            val again = moved.state.await { it.notice != null && it.preview?.price == "110" }
            assertEquals(OrderStep.ENTRY, again.step)
            moved.review(); moved.confirm()
            moved.state.await { it.step == OrderStep.DONE }
            assertEquals("110", remote.executed.last().reviewedPrice)
            assertNotEquals(remote.executed[remote.executed.size - 2].idempotencyKey, remote.executed.last().idempotencyKey)
        } finally { scope.cancel() }
    }

    @Test fun holdingLimitShowsTheUpgradeInsteadOfReview(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val order = PracticeOrderPresenter("GOOGL", OrderSide.BUY, FakeRemote(), { "full" }, scope, debounceMillis = 0)
            order.setInput("1")
            assertTrue(order.state.await { it.preview != null }.holdingLimit)
            order.review()
            assertEquals(OrderStep.ENTRY, order.state.value.step)
            order.setMode(OrderInput.AMOUNT)
            assertEquals(OrderInput.AMOUNT, order.state.value.mode)
            assertEquals(OrderInput.SHARES, PracticeOrderPresenter("A", OrderSide.SELL, FakeRemote(), { "x" }, scope).also { it.setMode(OrderInput.AMOUNT) }.state.value.mode)
        } finally { scope.cancel() }
    }

    @Test fun formattingNeverReliesOnColour() {
        assertEquals("Gain", PracticeFormat.direction("1.5"))
        assertEquals("Loss", PracticeFormat.direction("-2"))
        assertEquals("+\$1,234.50", PracticeFormat.signedMoney("1234.5", "CAD"))
        assertEquals("−US\$3.00", PracticeFormat.money("-3", "USD"))
        assertEquals("−2.50%", PracticeFormat.percent("-2.5"))
    }
}
