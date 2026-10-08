package org.example.stocksteps.userdata

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class AlertsTest {
    // Wed Oct 7 2026: 14:00Z = 10:00 EDT (open); quotes at 13:55Z.
    private val open = Instant.parse("2026-10-07T14:00:00Z")
    private val rules = AlertRules()
    private fun quote(price: Double, previous: Double = 100.0, at: Instant = Instant.parse("2026-10-07T13:55:00Z")) =
        StockQuote("AAPL", "Apple Inc.", price, price - previous, (price / previous - 1) * 100, null, null, previousClose = previous, timestamp = at.epochSecond)
    private fun rule(type: AlertType, threshold: Double? = null, armed: Boolean = true, repeat: RepeatPolicy = RepeatPolicy.ONCE, direction: MoveDirection? = null,
                     timing: EarningsTiming? = null, createdAt: Long = 0, triggerCount: Int = 0) =
        AlertRule("r1", InstrumentRef("AAPL", "Apple Inc.", currency = "USD"), type, threshold, "USD", direction, timing, repeat, AlertStatus.ACTIVE, armed, createdAt, createdAt, triggerCount = triggerCount)
    private fun snapshot(quote: StockQuote? = null, earnings: UpcomingEarnings? = null, news: List<NewsArticle> = emptyList()) =
        InstrumentSnapshot("AAPL", "Apple Inc.", quote, earnings, news)

    // --- price ---

    @Test fun priceThresholdsIncludeEqualityAndRespectArming() {
        val above = rule(AlertType.PRICE_ABOVE, 250.0)
        assertIs<AlertDecision.Trigger>(rules.decide(above, snapshot(quote(250.0)), open), "equal counts")
        assertEquals(AlertDecision.None, rules.decide(above, snapshot(quote(249.99)), open))
        val below = rule(AlertType.PRICE_BELOW, 90.0)
        assertIs<AlertDecision.Trigger>(rules.decide(below, snapshot(quote(89.5)), open))
        val waiting = rule(AlertType.PRICE_ABOVE, 250.0, armed = false)
        assertEquals(AlertDecision.None, rules.decide(waiting, snapshot(quote(260.0)), open), "already met at creation: wait")
        assertEquals(AlertDecision.Arm, rules.decide(waiting, snapshot(quote(240.0)), open), "seen below: armed")
    }

    @Test fun onceVersusRepeatAndDeterministicKeys() {
        val trigger = rules.decide(rule(AlertType.PRICE_ABOVE, 250.0), snapshot(quote(255.0)), open) as AlertDecision.Trigger
        assertEquals("r1:cycle-1", trigger.eventKey)
        assertEquals(AlertStatus.TRIGGERED, trigger.update(rule(AlertType.PRICE_ABOVE, 250.0)).status)
        assertTrue(trigger.body.contains("$255.00") && trigger.body.contains("may be delayed"))
        val repeat = rule(AlertType.PRICE_ABOVE, 250.0, repeat = RepeatPolicy.REPEAT)
        val next = (rules.decide(repeat, snapshot(quote(255.0)), open) as AlertDecision.Trigger).update(repeat)
        assertEquals(AlertStatus.ACTIVE, next.status); assertFalse(next.armed); assertEquals(1, next.triggerCount)
        assertEquals("r1:cycle-2", (rules.decide(next.copy(armed = true), snapshot(quote(256.0)), open) as AlertDecision.Trigger).eventKey)
    }

    @Test fun staleQuotesNeverTrigger() {
        val above = rule(AlertType.PRICE_ABOVE, 250.0)
        assertEquals(AlertDecision.Stale, rules.decide(above, snapshot(quote(300.0, at = Instant.parse("2026-10-06T19:59:00Z"))), open), "previous session")
        assertEquals(AlertDecision.Stale, rules.decide(above, snapshot(quote(300.0, at = Instant.parse("2026-10-07T13:00:00Z"))), Instant.parse("2026-10-07T15:00:00Z")), "older than 30 min while open")
        assertEquals(AlertDecision.Stale, rules.decide(above, snapshot(null), open))
        // Before Wednesday's open, Tuesday's close is the latest session.
        assertIs<AlertDecision.Trigger>(rules.decide(above, snapshot(quote(300.0, at = Instant.parse("2026-10-06T20:00:00Z"))), Instant.parse("2026-10-07T12:00:00Z")))
    }

    // --- daily move ---

    @Test fun dailyMovesByDirectionOncePerSession() {
        val up = rule(AlertType.DAILY_MOVE, 5.0, direction = MoveDirection.UP, repeat = RepeatPolicy.REPEAT)
        val trigger = rules.decide(up, snapshot(quote(106.0)), open) as AlertDecision.Trigger
        assertEquals("r1:session-2026-10-07", trigger.eventKey); assertEquals(6.0, trigger.observedValue!!, 1e-9)
        assertEquals(AlertStatus.ACTIVE, trigger.update(up).status, "repeat: next session can fire again")
        assertEquals(AlertDecision.None, rules.decide(up, snapshot(quote(94.0)), open))
        assertIs<AlertDecision.Trigger>(rules.decide(rule(AlertType.DAILY_MOVE, 5.0, direction = MoveDirection.DOWN), snapshot(quote(94.0)), open))
        assertIs<AlertDecision.Trigger>(rules.decide(rule(AlertType.DAILY_MOVE, 5.0, direction = MoveDirection.EITHER), snapshot(quote(94.0)), open))
        val nextDay = rules.decide(up, snapshot(quote(106.0, at = Instant.parse("2026-10-08T14:00:00Z"))), Instant.parse("2026-10-08T14:05:00Z")) as AlertDecision.Trigger
        assertEquals("r1:session-2026-10-08", nextDay.eventKey, "session rollover")
        val afterHours = quote(110.0, at = Instant.parse("2026-10-07T21:30:00Z"))
        assertEquals(AlertDecision.Stale, rules.decide(up, snapshot(afterHours), Instant.parse("2026-10-07T21:35:00Z")), "after-hours price isn't mixed in")
    }

    // --- earnings ---

    private fun earnings(date: String, status: EarningsDateStatus = EarningsDateStatus.ESTIMATED, time: EarningsTime = EarningsTime.UNKNOWN) = UpcomingEarnings("AAPL", date, time, status, "test")

    @Test fun earningsRemindersRespectTimingStatusAndRescheduling() {
        val both = rule(AlertType.EARNINGS, timing = EarningsTiming.BOTH)
        val dayBefore = rules.decide(both, snapshot(earnings = earnings("2026-10-08")), open) as AlertDecision.Trigger
        assertEquals("r1:earnings-2026-10-08-DAY_BEFORE", dayBefore.eventKey)
        assertTrue(dayBefore.body.contains("tomorrow") && dayBefore.body.contains("estimated and may change"))
        assertFalse(dayBefore.body.contains("before the market"), "no invented time")
        val dayOf = rules.decide(both, snapshot(earnings = earnings("2026-10-07", EarningsDateStatus.CONFIRMED, EarningsTime.AFTER_CLOSE)), open) as AlertDecision.Trigger
        assertTrue(dayOf.body.contains("today after the market closes") && dayOf.body.contains("confirmed"))
        assertEquals(AlertDecision.None, rules.decide(rule(AlertType.EARNINGS, timing = EarningsTiming.DAY_OF), snapshot(earnings = earnings("2026-10-08")), open))
        val moved = rules.decide(both, snapshot(earnings = earnings("2026-10-08")), open) as AlertDecision.Trigger
        assertEquals(dayBefore.eventKey, moved.eventKey, "same date → same key (no duplicate)")
        assertNotEquals(dayBefore.eventKey, (rules.decide(both, snapshot(earnings = earnings("2026-10-07")), open) as AlertDecision.Trigger).eventKey, "a rescheduled date gets its own reminder")
        assertEquals(AlertDecision.None, rules.decide(both, snapshot(), open), "no date known")
    }

    // --- news ---

    private fun article(id: String, title: String, at: String) = NewsArticle(title, "https://news.example/$id", "AAPL", "Wire", at, id = id)

    @Test fun newsAlertsUseConfirmedEventsGroupedAndAfterCreation() {
        val news = rule(AlertType.NEWS, createdAt = Instant.parse("2026-10-07T00:00:00Z").toEpochMilli())
        val feed = listOf(
            article("old", "Apple acquires a small AI startup", "2026-10-06T12:00:00Z"),
            article("opinion", "Is Apple stock a buy?", "2026-10-07T09:00:00Z"),
            article("a", "Apple acquires chip designer in $2 billion deal", "2026-10-07T10:00:00Z"),
            article("b", "Apple acquires chip designer in $2B deal", "2026-10-07T11:00:00Z")
        )
        val first = rules.decide(news, snapshot(news = feed), open) as AlertDecision.Trigger
        assertEquals("r1:news-a", first.eventKey); assertEquals("https://news.example/a", first.sourceUrl)
        assertEquals(AlertDecision.None, rules.decide(news, snapshot(news = feed), open, recentTitles = listOf(first.body)), "syndicated copy grouped with the notified story")
        assertEquals(AlertDecision.None, rules.decide(news, snapshot(news = feed.take(2)), open), "commentary and pre-creation news don't notify")
        assertEquals(AlertDecision.None, rules.decide(news, snapshot(), open), "no news")
    }

    // --- service + evaluator ---

    private class Market(var price: Double?, val currency: String? = "USD") : AlertMarketData {
        override suspend fun quote(symbol: String) = price?.let { StockQuote(symbol, "Apple Inc.", it, null, null, null, null, previousClose = 100.0, timestamp = 1_791_331_000) }
        override suspend fun currency(symbol: String) = currency
    }

    private val request = CreateAlertRequest(InstrumentRef("AAPL", "Apple Inc.", "NASDAQ", "USD"), AlertType.PRICE_ABOVE, 250.0, "USD")

    @Test fun creationValidatesAndAsksWhenAlreadyMet() = runBlocking {
        val store = InMemoryUserDataStore()
        val market = Market(260.0)
        val service = AlertsService(store, market, deliveryNote = "note")
        assertEquals("CONDITION_ALREADY_MET", assertFailsWith<UserDataException> { service.create("u1", request) }.code)
        assertTrue(service.create("u1", request.copy(whenAlreadyMet = AlreadyMetChoice.WAIT_FOR_CROSS)).alerts.single().armed.not())
        assertEquals("DUPLICATE_ALERT", assertFailsWith<UserDataException> { service.create("u1", request.copy(whenAlreadyMet = AlreadyMetChoice.NOTIFY_NOW)) }.code)
        assertEquals("INVALID_THRESHOLD", assertFailsWith<UserDataException> { service.create("u1", request.copy(threshold = -1.0)) }.code)
        assertEquals("CURRENCY_MISMATCH", assertFailsWith<UserDataException> { service.create("u1", request.copy(threshold = 300.0, currency = "CAD")) }.code)
        assertEquals("INVALID_THRESHOLD", assertFailsWith<UserDataException> { service.create("u1", request.copy(type = AlertType.DAILY_MOVE, threshold = 80.0)) }.code)
        val move = service.create("u1", request.copy(type = AlertType.DAILY_MOVE, threshold = 5.0, currency = null)).alerts.first { it.type == AlertType.DAILY_MOVE }
        assertEquals(MoveDirection.EITHER, move.direction); assertEquals(RepeatPolicy.REPEAT, move.repeat)
        val id = move.id
        assertEquals(AlertStatus.PAUSED, service.update("u1", id, UpdateAlertRequest(status = AlertStatus.PAUSED)).alerts.first { it.id == id }.status)
        assertEquals(AlertStatus.ACTIVE, service.update("u1", id, UpdateAlertRequest(status = AlertStatus.ACTIVE)).alerts.first { it.id == id }.status)
        assertTrue(service.delete("u1", id).alerts.none { it.id == id })
        assertTrue(service.list("u2").alerts.isEmpty(), "other users see nothing")
    }

    private fun evaluator(store: UserDataStore, price: Double, sender: PushSender = SimulatedPushSender(), at: Instant = open): AlertEvaluator {
        val provider = object : StockProviderRepository {
            override suspend fun getQuote(symbol: String) = StockQuote(symbol, "Apple Inc.", price, null, null, null, null, previousClose = 100.0, timestamp = Instant.parse("2026-10-07T13:55:00Z").epochSecond)
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol, "Apple Inc.", currency = "USD")
            override suspend fun getGainers() = emptyList<MarketMover>()
            override suspend fun getLosers() = emptyList<MarketMover>()
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
        }
        val news = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })
        return AlertEvaluator(store, WatchMarketData(StockService(provider, provider), null, news), sender, clock = Clock.fixed(at, ZoneOffset.UTC))
    }

    private suspend fun seed(store: InMemoryUserDataStore, devices: Int = 1) {
        store.updateAlerts("u1") { listOf(rule(AlertType.PRICE_ABOVE, 250.0)) to Unit }
        repeat(devices) { store.registerDevice(DeviceRecord("u1", "device$it", "token$it", "android", 0)) }
    }

    @Test fun duplicateAndConcurrentRunsNotifyOnce() = runBlocking {
        val store = InMemoryUserDataStore()
        seed(store, devices = 2)
        val sender = SimulatedPushSender()
        val workers = (1..3).map { evaluator(store, 260.0, sender) }
        val reports = coroutineScope { workers.map { async { it.run() } }.awaitAll() }
        assertEquals(1, reports.sumOf { it.triggered })
        assertEquals(2, sender.sent.size, "one message per device")
        workers.first().run()
        assertEquals(2, sender.sent.size, "a later run doesn't resend")
        assertEquals(AlertStatus.TRIGGERED, store.updateAlerts("u1") { it to it }.single().status)
        val event = store.history("u1", 10).single()
        assertEquals(DeliveryStatus.SIMULATED, event.delivery)
        assertFalse(event.body.contains("note"), "notes never appear in notifications")
    }

    @Test fun crashedDeliveryResumesWithoutANewEvent() = runBlocking {
        val store = InMemoryUserDataStore()
        seed(store)
        var fail = true
        val flaky = PushSender { if (fail) PushResult.Retryable("FCM 503") else PushResult.Accepted("m1") }
        val first = evaluator(store, 260.0, flaky).run()
        assertEquals(1, first.triggered); assertEquals(1, first.sendFailures)
        assertEquals(DeliveryStatus.PENDING, store.history("u1", 1).single().delivery)
        // A worker that died mid-send leaves a SENDING item; once the lease expires it is retried.
        store.claimOutbox(store.dueOutbox(0, 10).single().id, Instant.parse("2026-10-07T14:00:00Z").toEpochMilli(), 60_000)
        fail = false
        val second = evaluator(store, 260.0, flaky, at = open.plusSeconds(120)).run()
        assertEquals(0, second.triggered); assertEquals(1, second.sent)
        assertEquals(1, store.history("u1", 10).size)
        assertEquals(DeliveryStatus.ACCEPTED, store.history("u1", 1).single().delivery, "accepted by FCM, not 'seen'")
    }

    @Test fun invalidTokensAreRemovedAndNoDevicesIsRecorded() = runBlocking {
        val store = InMemoryUserDataStore()
        seed(store)
        evaluator(store, 260.0, PushSender { PushResult.InvalidToken }).run()
        assertTrue(store.devices("u1").isEmpty())
        assertEquals(DeliveryStatus.FAILED, store.history("u1", 1).single().delivery)
        val quiet = InMemoryUserDataStore()
        seed(quiet, devices = 0)
        evaluator(quiet, 260.0).run()
        assertEquals(DeliveryStatus.NO_DEVICES, quiet.history("u1", 1).single().delivery)
    }

    @Test fun pausedRulesAreIgnoredAndArmingIsPersisted() = runBlocking {
        val store = InMemoryUserDataStore()
        store.updateAlerts("u1") { listOf(rule(AlertType.PRICE_ABOVE, 250.0).copy(status = AlertStatus.PAUSED), rule(AlertType.PRICE_BELOW, 90.0, armed = false).copy(id = "r2")) to Unit }
        val report = evaluator(store, 120.0).run()
        assertEquals(1, report.rules); assertEquals(1, report.armed); assertEquals(0, report.triggered)
        assertTrue(store.updateAlerts("u1") { it to it }.first { it.id == "r2" }.armed)
    }

    @Test fun devicesBelongToOneAccountAtATime() = runBlocking {
        val store = InMemoryUserDataStore()
        store.registerDevice(DeviceRecord("alice", "phone", "tokenA", "ios", 0))
        store.registerDevice(DeviceRecord("bob", "phone", "tokenA", "ios", 1))
        assertTrue(store.devices("alice").isEmpty(), "the same token signed in as bob no longer reaches alice")
        store.registerDevice(DeviceRecord("bob", "phone", "tokenB", "ios", 2))
        assertEquals(listOf("tokenB"), store.devices("bob").map { it.token }, "token rotation replaces the old token")
        store.registerDevice(DeviceRecord("bob", "tablet", "tokenC", "android", 3))
        assertEquals(2, store.devices("bob").size, "multiple devices")
        store.unregisterDevice("bob", "phone")
        assertEquals(listOf("tablet"), store.devices("bob").map { it.deviceId })
    }
}
