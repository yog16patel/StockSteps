package org.example.stocksteps.earnings

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 4: scheduling policy (pure planner). */
class ReminderPlannerTest {
    private fun event(date: String, status: EarningsDateStatus = EarningsDateStatus.CONFIRMED, actual: Boolean = false, sourceStatus: EarningsEventStatus? = null, previous: String? = null) =
        EarningsEvent("AAPL:2026-Q4", "AAPL", "Apple Inc.", "NASDAQ", "US", fiscalYear = 2026, fiscalQuarter = 4, date = date, dateStatus = status, previousDate = previous,
            actual = if (actual) EarningsActual(1.0, EpsBasis.ADJUSTED, 1e9, "USD", source = "t") else null, source = "t", updatedAt = "t", sourceStatus = sourceStatus)
    private fun reminder(offset: Int = 1, eventId: String? = "AAPL:2026-Q4", enabled: Boolean = true, last: String? = null) =
        EarningsReminder("r1", "AAPL", eventId, ReminderSource.MANUAL, offset, enabled, true, null, 0, 0, lastKnownEventDate = last)
    private fun doc(vararg r: EarningsReminder, prefs: EarningsReminderPreferences = EarningsReminderPreferences(timeZone = "America/Toronto")) =
        EarningsReminderDocument("u", r.toList(), prefs, migratedLegacy = true)
    private val now = Instant.parse("2026-10-07T21:15:00Z")
    private fun plan(d: EarningsReminderDocument, vararg events: EarningsEvent, watched: Set<String> = emptySet(), at: Instant = now) =
        ReminderPlanner.plan("u", d, watched, mapOf("AAPL" to events.toList()), at)

    @Test fun offsetsUseCalendarDaysAndTheUsersLocalDeliveryTime() {
        for ((offset, expected) in listOf(1 to "2026-10-28T13:00:00Z", 3 to "2026-10-26T13:00:00Z", 7 to "2026-10-22T13:00:00Z")) {
            val p = plan(doc(reminder(offset)), event("2026-10-29"))
            assertEquals(Instant.parse(expected), p.notifications.single().scheduledFor)         // 9:00 Toronto (EDT, UTC−4)
            assertEquals("AAPL:2026-Q4", p.notifications.single().event.id)
        }
        val tokyo = plan(doc(reminder(), prefs = EarningsReminderPreferences(deliveryTime = "08:30", timeZone = "Asia/Tokyo")), event("2026-10-29"))
        assertEquals(Instant.parse("2026-10-27T23:30:00Z"), tokyo.notifications.single().scheduledFor)  // 08:30 JST on Oct 28
        assertEquals("Upcoming Earnings", tokyo.notifications.single().title)
        assertEquals("Apple Inc. is expected to report earnings tomorrow.", tokyo.notifications.single().body)
    }

    @Test fun unconfirmedDatesSayTheDateMayChangeAndNoTimeIsInvented() {
        val p = plan(doc(reminder(3)), event("2026-10-29", EarningsDateStatus.ESTIMATED))
        assertEquals("Apple Inc. is currently expected to report earnings on October 29. The date may change.", p.notifications.single().body)
        assertFalse(p.notifications.single().body.contains("market"))
    }

    @Test fun daylightSavingGapsAndOverlapsFollowTheDocumentedPolicy() {
        // US DST starts Sun Mar 8 2026: 02:30 doesn't exist in New York → 03:30 EDT (07:30Z).
        val gap = ReminderPlanner.localInstant(LocalDate.parse("2026-03-08"), EarningsReminderPreferences(deliveryTime = "02:30", timeZone = "America/New_York"))
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), gap)
        // DST ends Sun Nov 1 2026: 01:30 happens twice → the first (EDT, 05:30Z).
        val overlap = ReminderPlanner.localInstant(LocalDate.parse("2026-11-01"), EarningsReminderPreferences(deliveryTime = "01:30", timeZone = "America/New_York"))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlap)
        // Same local 9:00 across the change: 13:00Z before, 14:00Z after.
        assertEquals(Instant.parse("2026-10-31T13:00:00Z"), ReminderPlanner.localInstant(LocalDate.parse("2026-10-31"), EarningsReminderPreferences(timeZone = "America/New_York")))
        assertEquals(Instant.parse("2026-11-02T14:00:00Z"), ReminderPlanner.localInstant(LocalDate.parse("2026-11-02"), EarningsReminderPreferences(timeZone = "America/New_York")))
    }

    @Test fun quietHoursDeferDeliveryIncludingOvernightWindows() {
        val prefs = EarningsReminderPreferences(deliveryTime = "22:30", timeZone = "America/Toronto", quietStart = "22:00", quietEnd = "07:00")
        assertEquals(Instant.parse("2026-10-29T11:00:00Z"), ReminderPlanner.localInstant(LocalDate.parse("2026-10-28"), prefs))   // next day 07:00
        val morning = prefs.copy(deliveryTime = "06:00")
        assertEquals(Instant.parse("2026-10-28T11:00:00Z"), ReminderPlanner.localInstant(LocalDate.parse("2026-10-28"), morning))
        assertEquals(Instant.parse("2026-10-28T13:00:00Z"), ReminderPlanner.localInstant(LocalDate.parse("2026-10-28"), prefs.copy(deliveryTime = "09:00")))
    }

    @Test fun missingPostponedAndCanceledEventsNeverGetInventedSchedules() {
        val none = plan(doc(reminder()))
        assertEquals(ReminderScheduleStatus.WAITING_FOR_DATE, none.reminders.single().scheduleStatus); assertTrue(none.notifications.isEmpty())
        assertEquals(ReminderScheduleStatus.WAITING_FOR_DATE, plan(doc(reminder()), event("2026-10-29", EarningsDateStatus.UNKNOWN)).reminders.single().scheduleStatus)
        assertEquals(ReminderScheduleStatus.WAITING_FOR_DATE, plan(doc(reminder()), event("2026-10-29", sourceStatus = EarningsEventStatus.POSTPONED)).reminders.single().scheduleStatus)
        val canceled = plan(doc(reminder()), event("2026-10-29", sourceStatus = EarningsEventStatus.CANCELED))
        assertEquals(ReminderScheduleStatus.CANCELED, canceled.reminders.single().scheduleStatus)
        assertEquals(EarningsNotificationType.EVENT_CANCELED, canceled.notifications.single().type)
        assertTrue(plan(doc(reminder(), prefs = EarningsReminderPreferences(cancellations = false)), event("2026-10-29", sourceStatus = EarningsEventStatus.CANCELED)).notifications.isEmpty())
    }

    @Test fun reschedulingMovesThePendingReminderAndOptionallyNotifies() {
        val before = plan(doc(reminder(last = null)), event("2026-10-29")).notifications.single()
        val moved = plan(doc(reminder(last = "2026-10-29")), event("2026-10-30", previous = "2026-10-29"))
        val pre = moved.notifications.single { it.type == EarningsNotificationType.PRE_EARNINGS }
        assertNotEquals(before.key, pre.key)                                                          // the old pending one is reconciled away
        assertEquals(Instant.parse("2026-10-29T13:00:00Z"), pre.scheduledFor)
        assertEquals("2026-10-30", moved.reminders.single().lastKnownEventDate)
        assertTrue(moved.notifications.none { it.type == EarningsNotificationType.DATE_CHANGED })   // optional, off by default
        val notify = plan(doc(reminder(last = "2026-10-29"), prefs = EarningsReminderPreferences(dateChanges = true)), event("2026-10-30"))
        assertEquals("Apple Inc.'s expected earnings date has changed to October 30.", notify.notifications.single { it.type == EarningsNotificationType.DATE_CHANGED }.body)
    }

    @Test fun pastDueRemindersFollowTheLateGrace() {
        // 1 day before Oct 8 = Oct 7 09:00 Toronto (13:00Z). At 14:30Z (1.5 h late) it's still sent; at 21:15Z it's skipped.
        assertEquals(1, plan(doc(reminder()), event("2026-10-08"), at = Instant.parse("2026-10-07T14:30:00Z")).notifications.size)
        val late = plan(doc(reminder()), event("2026-10-08"))
        assertTrue(late.notifications.isEmpty()); assertTrue(late.reminders.single().statusMessage!!.contains("reminder time for this report has passed"))
    }

    @Test fun resultsNeedPublishedFiguresAndAreSentOnce() {
        assertTrue(plan(doc(reminder()), event("2026-10-06")).notifications.isEmpty())             // date passed, no figures → nothing
        val published = plan(doc(reminder()), event("2026-10-06", actual = true))
        val n = published.notifications.single()
        assertEquals(EarningsNotificationType.RESULTS_AVAILABLE, n.type); assertEquals("u|RESULTS_AVAILABLE|AAPL:2026-Q4", n.key)
        assertEquals("Apple Inc.'s quarterly earnings results are now available.", n.body)
        assertEquals(ReminderScheduleStatus.COMPLETED, published.reminders.single().scheduleStatus)
        assertTrue(plan(doc(reminder(), prefs = EarningsReminderPreferences(resultsAvailable = false)), event("2026-10-06", actual = true)).notifications.isEmpty())
        // Company/automatic reminders don't announce old results on opt-in.
        assertTrue(plan(doc(reminder(eventId = null)), event("2026-09-01", actual = true)).notifications.isEmpty())
    }

    @Test fun manualAndAutomaticRemindersDeduplicateButDistinctOffsetsStay() {
        val prefs = EarningsReminderPreferences(watchlistAuto = true)
        val same = plan(doc(reminder(1), prefs = prefs), event("2026-10-29"), watched = setOf("AAPL"))
        assertEquals(1, same.notifications.size)                                                       // manual + auto, same offset → one
        assertEquals(listOf(ReminderSource.MANUAL, ReminderSource.WATCHLIST_AUTO), same.reminders.map { it.source })
        val distinct = plan(doc(reminder(3), prefs = prefs), event("2026-10-29"), watched = setOf("AAPL"))
        assertEquals(2, distinct.notifications.size)
        assertTrue(plan(doc(prefs = EarningsReminderPreferences(watchlistAuto = false)), event("2026-10-29"), watched = setOf("AAPL")).notifications.isEmpty()) // opt-in only
    }

    @Test fun disabledReminderOrMasterSwitchSchedulesNothing() {
        assertEquals(ReminderScheduleStatus.PAUSED, plan(doc(reminder(enabled = false)), event("2026-10-29")).reminders.single().scheduleStatus)
        val off = plan(doc(reminder(), prefs = EarningsReminderPreferences(enabled = false)), event("2026-10-29"))
        assertTrue(off.notifications.isEmpty()); assertEquals("Earnings notifications are turned off.", off.reminders.single().statusMessage)
    }
}

/** Earnings Intelligence Lite, Phase 4: API, reconciliation, dispatch and migration (fake clock, store and push gateway). */
class EarningsReminderServiceTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private class FakeSender : PushSender {
        val sent = CopyOnWriteArrayList<PushMessage>()
        var outcome: (PushMessage) -> PushResult = { PushResult.Accepted("m-${it.token}") }
        override suspend fun send(message: PushMessage): PushResult { sent += message; return outcome(message) }
    }

    private val clock = MutableClock(Instant.parse("2026-10-07T21:15:00Z"))
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val store = InMemoryUserDataStore()
    private val sender = FakeSender()
    private val earnings = EarningsService(FixtureEarningsDataSource(), StockService(fixture, fixture), PriceChartService(fixture), store, EntitlementService(store, clock::millis, true), clock, true, null)
    private val service = EarningsReminderService(store, earnings, sender, clock, sampleData = true)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun device(uid: String, id: String, token: String = "token-$id") = store.registerDevice(DeviceRecord(uid, id, token, "android", 0))
    private suspend fun at(text: String) { clock.now = Instant.parse(text) }

    @Test fun createUpdateDeleteAreIdempotentValidatedAndScheduled(): Unit = runBlocking {
        val r = service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1))
        val reminder = r.reminders.single()
        assertEquals(ReminderScheduleStatus.ACTIVE, reminder.scheduleStatus); assertEquals("2026-10-29", reminder.eventDate)
        assertEquals("2026-10-28T13:00:00Z", reminder.nextDeliveryAt); assertTrue(reminder.nextDeliveryText!!.contains("America/Toronto"))
        assertEquals(1, service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 3, idempotencyKey = "create:AAPL")).reminders.size) // repeat → same reminder
        assertEquals(3, service.get("alice").reminders.single().offsetDays)
        assertEquals(listOf(NotificationDeliveryStatus.CANCELED, NotificationDeliveryStatus.PENDING), store.earningsDeliveries("alice", 10).sortedBy { it.scheduledFor }.map { it.status }.sortedBy { it.name })
        val updated = service.update("alice", reminder.reminderId, UpdateEarningsReminder(offsetDays = 7))
        assertEquals("2026-10-22T13:00:00Z", updated.reminders.single().nextDeliveryAt)
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 2)) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.create("alice", CreateEarningsReminder("bad id")) }.status)
        assertEquals(404, assertFailsWith<EarningsRequestException> { service.create("alice", CreateEarningsReminder("NOPE:2026-Q1")) }.status)
        assertEquals("ALREADY_REPORTED", assertFailsWith<EarningsRequestException> { service.create("alice", CreateEarningsReminder("SSRV:2026-Q3")) }.code)
        // Another user can't see, change or delete it (it looks missing).
        assertTrue(service.get("bob").reminders.isEmpty())
        assertEquals(404, assertFailsWith<EarningsRequestException> { service.update("bob", reminder.reminderId, UpdateEarningsReminder(enabled = false)) }.status)
        assertEquals(404, assertFailsWith<EarningsRequestException> { service.delete("bob", reminder.reminderId) }.status)
        assertTrue(service.delete("alice", reminder.reminderId).reminders.isEmpty())
        assertTrue(store.earningsDeliveries("alice", 10).none { it.status == NotificationDeliveryStatus.PENDING })  // canceled with the reminder
    }

    @Test fun preferencesAreValidated(): Unit = runBlocking {
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.setPreferences("u", EarningsReminderPreferences(timeZone = "Mars/Base")) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.setPreferences("u", EarningsReminderPreferences(deliveryTime = "25:00")) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.setPreferences("u", EarningsReminderPreferences(defaultOffsetDays = 2)) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { service.setPreferences("u", EarningsReminderPreferences(quietStart = "22:00")) }.status)
        assertFalse(service.get("new-user").preferences.watchlistAuto)                                     // never on by default
    }

    @Test fun dispatchSendsOnceToEveryDeviceAndRetriesWithoutDuplicates(): Unit = runBlocking {
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1))
        device("alice", "phone"); device("alice", "tablet")
        assertEquals(0, service.runPass().submitted)                                                     // not due yet
        at("2026-10-28T13:00:30Z")
        sender.outcome = { if (it.token == "token-tablet") PushResult.Retryable("FCM 503") else PushResult.Accepted("m1") }
        val first = service.runPass()
        assertEquals(1, first.retried); assertEquals(2, sender.sent.size)
        assertEquals("earnings_reminders", sender.sent.first().channel)
        assertEquals(mapOf("type" to "earnings-reminder", "eventId" to "AAPL:2026-Q4", "instrumentId" to "AAPL", "payloadVersion" to "1"), sender.sent.first().data - "notificationId")
        sender.outcome = { PushResult.Accepted("m2") }
        assertEquals(0, service.runPass().submitted)                                                     // backoff not elapsed
        at("2026-10-28T13:02:00Z")
        val second = service.runPass()
        assertEquals(1, second.submitted)
        assertEquals(listOf("token-phone", "token-tablet", "token-tablet"), sender.sent.map { it.token })  // the phone isn't sent twice
        val d = store.earningsDeliveries("alice", 10).single()
        assertEquals(NotificationDeliveryStatus.SUBMITTED, d.status); assertEquals("m1", d.providerMessageId); assertEquals(2, d.attempts)
        at("2026-10-28T13:30:00Z"); service.runPass(); service.runPass()
        assertEquals(3, sender.sent.size)                                                                // never again
    }

    @Test fun concurrentWorkersAndRepeatedPassesNeverDuplicate(): Unit = runBlocking {
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1)); device("alice", "phone")
        at("2026-10-28T13:05:00Z")
        val other = EarningsReminderService(store, earnings, sender, clock, sampleData = true)
        coroutineScope { listOf(async { service.runPass() }, async { other.runPass() }, async { service.runPass() }).awaitAll() }
        assertEquals(1, sender.sent.size)
    }

    @Test fun invalidTokensAreRemovedRateLimitsRetryAndPermanentFailuresStop(): Unit = runBlocking {
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1)); device("alice", "old"); device("alice", "new")
        at("2026-10-28T13:05:00Z")
        sender.outcome = { if (it.token == "token-old") PushResult.InvalidToken else PushResult.Accepted("ok") }
        val report = service.runPass()
        assertEquals(1, report.invalidTokens); assertEquals(listOf("new"), store.devices("alice").map { it.deviceId })
        assertEquals(NotificationDeliveryStatus.SUBMITTED, store.earningsDeliveries("alice", 10).single().status)
        // Rate limited every time: bounded retries, then FAILED.
        service.create("bob", CreateEarningsReminder("AAPL:2026-Q4", 1)); device("bob", "x")
        sender.outcome = { PushResult.Retryable("FCM 429") }
        repeat(8) { at(Instant.parse("2026-10-28T13:10:00Z").plusSeconds(3_600L * it).toString()); service.runPass() }
        val bob = store.earningsDeliveries("bob", 10).single()
        assertTrue(bob.status == NotificationDeliveryStatus.FAILED || bob.status == NotificationDeliveryStatus.CANCELED)   // never retried forever
        assertTrue(bob.attempts <= EarningsReminderService.MAX_ATTEMPTS)
        assertEquals(30_000L, EarningsReminderService.backoff(1)); assertEquals(60_000L, EarningsReminderService.backoff(2)); assertEquals(1_800_000L, EarningsReminderService.backoff(20))
    }

    @Test fun lateDeliveriesAreSkippedAndNoDeviceIsReported(): Unit = runBlocking {
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1))
        at("2026-10-28T16:00:00Z")                                                                       // 3 h after 9:00 Toronto
        assertEquals(1, service.runPass().late); assertEquals("Delivery window passed", store.earningsDeliveries("alice", 10).single().failureReason)
        service.create("bob", CreateEarningsReminder("AAPL:2026-Q4", 7))                                // no device registered
        at("2026-10-22T13:01:00Z")
        service.runPass()
        assertEquals("No registered device", store.earningsDeliveries("bob", 10).single().failureReason)
    }

    @Test fun watchlistAutoRemindersFollowListChangesAndManualOnesSurvive(): Unit = runBlocking {
        val watchlists = WatchlistsService(store, now = clock::millis)
        val first = watchlists.get("alice").watchlists.first()
        watchlists.add("alice", first.id, InstrumentRef("KO", "Coca-Cola", "NYSE", "USD"))
        watchlists.add("alice", first.id, InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"))
        val second = watchlists.create("alice", "Two").watchlists.first { it.name == "Two" }
        watchlists.add("alice", second.id, InstrumentRef("KO", "Coca-Cola", "NYSE", "USD"))           // duplicate across lists
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 3))
        assertTrue(service.get("alice").reminders.none { it.source == ReminderSource.WATCHLIST_AUTO })   // opt-in only
        val on = service.setPreferences("alice", EarningsReminderPreferences(watchlistAuto = true))
        assertEquals(listOf("AAPL", "KO"), on.reminders.filter { it.source == ReminderSource.WATCHLIST_AUTO }.map { it.instrumentId })
        val pending = store.earningsDeliveries("alice", 20).filter { it.status == NotificationDeliveryStatus.PENDING }
        assertEquals(1, pending.count { it.instrumentId == "KO" })                                        // once, despite two lists
        assertEquals(2, pending.count { it.instrumentId == "AAPL" })                                      // manual 3-day + auto 1-day
        // Removing AAPL from the watchlist cancels only the automatic reminder.
        val entry = watchlists.get("alice").watchlists.first { it.id == first.id }.entries.first { it.instrument.symbol == "AAPL" }
        watchlists.remove("alice", first.id, entry.id)
        service.runPass()
        val after = store.earningsDeliveries("alice", 20).filter { it.status == NotificationDeliveryStatus.PENDING }
        assertEquals(listOf("3d"), after.filter { it.instrumentId == "AAPL" }.map { it.idempotencyKey.split('|')[3] })
        assertEquals(ReminderSource.MANUAL, service.get("alice").reminders.single { it.instrumentId == "AAPL" }.source)
    }

    @Test fun eventChangesAreReconciled(): Unit = runBlocking {
        var moved = false
        var reported = false
        val source = object : EarningsDataSource {
            val inner = FixtureEarningsDataSource()
            override val label = "test"
            override suspend fun calendar(from: LocalDate, to: LocalDate) = inner.calendar(from, to)
            override suspend fun history(symbol: String) = inner.history(symbol).map { e -> when {
                e.id != "AAPL:2026-Q4" -> e
                reported -> e.copy(actual = EarningsActual(1.9, EpsBasis.GAAP_DILUTED, 1e11, "USD", source = "t"))
                moved -> e.copy(date = "2026-11-05", previousDate = "2026-10-29")
                else -> e
            } }
        }
        var millis = 0L
        val svc = EarningsReminderService(store, EarningsService(source, StockService(fixture, fixture), PriceChartService(fixture), store,
            EntitlementService(store, clock::millis, true), clock, true, null, cache = CompanyFinancialCache(512, now = { millis })), sender, clock, sampleData = true)
        svc.setPreferences("alice", EarningsReminderPreferences(dateChanges = true))
        svc.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1)); device("alice", "phone")
        moved = true; millis += 30_000_000
        svc.runPass()
        val all = store.earningsDeliveries("alice", 10)
        assertEquals(NotificationDeliveryStatus.CANCELED, all.single { it.idempotencyKey.endsWith("2026-10-29") }.status)
        assertEquals("2026-11-04T14:00:00Z", Instant.ofEpochMilli(all.single { it.idempotencyKey.endsWith("2026-11-05") && it.type == EarningsNotificationType.PRE_EARNINGS }.scheduledFor).toString())
        assertEquals(1, sender.sent.count { it.data["type"] == "earnings-date-changed" })                 // sent now, once
        svc.runPass(); assertEquals(1, sender.sent.count { it.data["type"] == "earnings-date-changed" })
        // Results published → one results notification linking the report; a revision doesn't resend.
        reported = true; millis += 30_000_000
        svc.runPass()
        val results = sender.sent.single { it.data["type"] == "earnings-results" }
        assertEquals("AAPL:2026-Q4", results.data["reportId"])
        assertEquals(ReminderScheduleStatus.COMPLETED, svc.get("alice").reminders.single().scheduleStatus)
        millis += 30_000_000; svc.runPass()
        assertEquals(1, sender.sent.count { it.data["type"] == "earnings-results" })
    }

    @Test fun timeZoneChangesRescheduleAndTheMasterSwitchCancels(): Unit = runBlocking {
        service.create("alice", CreateEarningsReminder("AAPL:2026-Q4", 1))
        service.setPreferences("alice", EarningsReminderPreferences(timeZone = "Europe/London"))
        assertEquals("2026-10-28T09:00:00Z", service.get("alice").reminders.single().nextDeliveryAt)    // 9:00 GMT (UK clocks changed Oct 25)
        service.setPreferences("alice", EarningsReminderPreferences(enabled = false, timeZone = "Europe/London"))
        assertTrue(store.earningsDeliveries("alice", 10).none { it.status == NotificationDeliveryStatus.PENDING })
        assertEquals(ReminderScheduleStatus.PAUSED, service.get("alice").reminders.single().scheduleStatus)
    }

    @Test fun legacyEarningsAlertsAreConvertedOnceAndNoLongerEvaluated(): Unit = runBlocking {
        store.updateAlerts("carol") { rules -> rules + AlertRule("old1", InstrumentRef("AAPL", "Apple", currency = "USD"), AlertType.EARNINGS, status = AlertStatus.ACTIVE,
            earningsTiming = EarningsTiming.BOTH, earningsLeadDays = 3, earningsResults = true, createdAt = 0, updatedAt = 0) to Unit }
        service.runPass()                                                                                  // migration happens in the scheduler pass
        val r = service.get("carol").reminders.single()
        assertNull(r.earningsEventId); assertEquals(3, r.offsetDays); assertEquals("AAPL:2026-Q4", earnings.reminderEvents("AAPL").firstOrNull { it.date == r.eventDate }?.id)
        assertTrue(store.updateAlerts("carol") { it to it }.none { it.type == AlertType.EARNINGS })
        service.runPass(); assertEquals(1, service.get("carol").reminders.size)                          // once
        val alerts = AlertsService(store, object : AlertMarketData {
            override suspend fun quote(symbol: String): StockQuote? = null
            override suspend fun currency(symbol: String) = "USD"
        }, deliveryNote = "", isPlus = { false })
        assertEquals("EARNINGS_REMINDERS", assertFailsWith<UserDataException> {
            alerts.create("carol", CreateAlertRequest(InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"), AlertType.EARNINGS, earningsTiming = EarningsTiming.BOTH)) }.code)
    }

    @Test fun mockSenderScenariosNeverContactFcm(): Unit = runBlocking {
        val inner = SimulatedPushSender()
        val mock = MockScenarioPushSender(inner)
        assertEquals(PushResult.InvalidToken, mock.send(PushMessage("invalid-1", "t", "b", emptyMap())))
        assertTrue(mock.send(PushMessage("ratelimit-1", "t", "b", emptyMap())) is PushResult.Retryable)
        assertEquals(PushResult.Simulated, mock.send(PushMessage("device-1", "t", "b", emptyMap())))
        assertEquals(1, inner.sent.size)
    }

    @Test fun routesRequireAuthIsolateUsersAndExposeTheScheduler() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsReminderRoutes(service, MockUserAuthenticator(), RequestRateLimiter(1_000), schedulerSecret = "x".repeat(32), mock = false) }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/earnings/reminders").status)
        val created = client.post("/api/v1/me/earnings/reminders") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"eventId":"AAPL:2026-Q4","offsetDays":1}""") }
        assertEquals(HttpStatusCode.OK, created.status)
        val id = json.decodeFromString(EarningsRemindersResponse.serializer(), created.bodyAsText()).reminders.single().reminderId
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/me/earnings/reminders/$id") { bearerAuth("mock-user:bob") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/me/earnings/reminders/preferences") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody("""{"timeZone":"Nowhere"}""") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/me/earnings/reminders/deliveries") { bearerAuth("mock-user:alice") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/internal/earnings-reminders/dispatch").status)
        assertEquals(HttpStatusCode.OK, client.post("/internal/earnings-reminders/dispatch") { header("X-StockSteps-Scheduler-Token", "x".repeat(32)) }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/api/v1/me/earnings/reminders/$id") { bearerAuth("mock-user:alice") }.status)
    }
}
