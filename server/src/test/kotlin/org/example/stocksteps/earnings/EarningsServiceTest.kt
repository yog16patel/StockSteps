package org.example.stocksteps.earnings

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class EarningsReactionTest {
    private val us = UsExchangeCalendar()
    private fun event(date: String, session: EarningsTime, exchange: String = "NYSE", previous: String? = null) = EarningsEvent(
        "T:2026-Q3", "T", "Test", exchange, fiscalYear = 2026, fiscalQuarter = 3, date = date, session = session, dateStatus = EarningsDateStatus.CONFIRMED,
        previousDate = previous, actual = EarningsActual(1.0, EpsBasis.ADJUSTED, 1e9, "USD", source = "t"), source = "t", updatedAt = "t")
    // Mon Oct 5 … Fri Oct 9, Mon Oct 12 … Wed Oct 14 (US sessions; no weekend closes).
    private val closes = mapOf("2026-10-02" to "99", "2026-10-05" to "100", "2026-10-06" to "102", "2026-10-07" to "101", "2026-10-08" to "110", "2026-10-09" to "99",
        "2026-10-12" to "105", "2026-10-13" to "104", "2026-10-14" to "106", "2026-10-15" to "107")
    private fun series(c: Map<String, String> = closes, currency: String = "USD", actions: List<CorporateAction> = emptyList(), known: Boolean = true) =
        PriceSeries(c.map { (d, v) -> LocalDate.parse(d) to DailyBar(LocalDate.parse(d), Decimal.parse(v), currency, PriceAdjustment.SPLIT_ADJUSTED) }.toMap(),
            actions, known, "test", Instant.parse("2026-10-20T00:00:00Z"))
    private val later = Instant.parse("2026-10-20T00:00:00Z")
    private fun compute(e: EarningsEvent, w: ReactionWindow = ReactionWindow.FIRST_SESSION, s: PriceSeries? = series(), now: Instant = later, cal: ExchangeCalendar? = us) =
        PriceReactionEngine.compute(e, "T:2026-Q3", w, s, cal, now)

    @Test fun beforeOpenUsesPreviousCloseToSameDayClose() {
        val r = compute(event("2026-10-08", EarningsTime.BEFORE_OPEN))
        assertEquals("2026-10-07", r.baseline?.sessionDate); assertEquals("2026-10-08", r.endpoint?.sessionDate)
        assertEquals("9", r.absoluteChange); assertEquals("8.91089109", r.percentChange)   // exact decimals
        assertEquals(ReactionStatus.AVAILABLE, r.status)
    }

    @Test fun afterCloseUsesSameDayCloseToNextSessionClose() {
        val r = compute(event("2026-10-08", EarningsTime.AFTER_CLOSE))
        assertEquals("2026-10-08", r.baseline?.sessionDate); assertEquals("2026-10-09", r.endpoint?.sessionDate)
        assertEquals("-11", r.absoluteChange); assertEquals("-10", r.percentChange)
        assertEquals("2026-10-09T16:00-04:00", r.endpoint?.timestamp)
    }

    @Test fun multiSessionWindowsCountTradingSessionsNotCalendarDays() {
        val three = compute(event("2026-10-08", EarningsTime.AFTER_CLOSE), ReactionWindow.THREE_SESSIONS)
        assertEquals("2026-10-13", three.endpoint?.sessionDate)                              // Fri 9, Mon 12, Tue 13 (weekend skipped)
        val five = compute(event("2026-10-08", EarningsTime.AFTER_CLOSE), ReactionWindow.FIVE_SESSIONS)
        assertEquals("2026-10-15", five.endpoint?.sessionDate)
    }

    @Test fun unknownTimeIsNotGuessedButABroaderComparisonIsLabelled() {
        val r = compute(event("2026-10-08", EarningsTime.UNKNOWN))
        assertEquals(ReactionStatus.EVENT_TIME_UNKNOWN, r.status)
        assertEquals("2026-10-07", r.baseline?.sessionDate); assertEquals("2026-10-09", r.endpoint?.sessionDate)
        assertTrue(r.statusMessage!!.contains("broader comparison"))
    }

    @Test fun duringMarketWithoutIntradayDataIsALabelledRegularSessionComparison() {
        val r = compute(event("2026-10-08", EarningsTime.DURING_MARKET))
        assertEquals("2026-10-07", r.baseline?.sessionDate); assertEquals("2026-10-08", r.endpoint?.sessionDate)
        assertTrue(r.warnings.any { it.contains("includes trading before the announcement") })
    }

    @Test fun weekendsHolidaysAndHalfDaysFollowTheExchangeCalendar() {
        val saturday = compute(event("2026-10-10", EarningsTime.BEFORE_OPEN))
        assertEquals("2026-10-09", saturday.baseline?.sessionDate); assertEquals("2026-10-12", saturday.endpoint?.sessionDate)
        assertTrue(saturday.warnings.any { it.contains("wasn't a trading day") })
        // Thu Jul 2 after the close: Fri Jul 3 is a US holiday (observed), so the next session is Mon Jul 6.
        val july = series(mapOf("2026-07-02" to "30", "2026-07-06" to "31.2"))
        assertEquals("2026-07-06", compute(event("2026-07-02", EarningsTime.AFTER_CLOSE), s = july).endpoint?.sessionDate)
        // The same dates on the TSX: Jul 1 is Canada Day, Jul 3 is a trading day.
        val tsx = TsxExchangeCalendar()
        assertEquals("2026-07-02", PriceReactionEngine.plan(EarningsTime.AFTER_CLOSE, LocalDate.parse("2026-06-30"), tsx).first.toString())
        assertEquals("2026-07-03", PriceReactionEngine.plan(EarningsTime.AFTER_CLOSE, LocalDate.parse("2026-07-02"), tsx).first.toString())
        // Dec 24 is a half day: the close is at 1:00 pm, so the session is complete at 1:15 pm.
        val xmas = series(mapOf("2025-12-23" to "50", "2025-12-24" to "50.5"))
        val half = compute(event("2025-12-24", EarningsTime.BEFORE_OPEN), s = xmas, now = ZonedDateTime.of(2025, 12, 24, 13, 20, 0, 0, ZoneId.of("America/New_York")).toInstant())
        assertEquals(ReactionStatus.AVAILABLE, half.status); assertTrue(half.endpoint!!.earlyClose); assertTrue(half.warnings.any { it.contains("early close") })
    }

    @Test fun incompleteWindowsAreNeverFilled() {
        // Friday's session isn't finished at 3 pm New York time on Friday.
        val during = compute(event("2026-10-08", EarningsTime.AFTER_CLOSE), now = ZonedDateTime.of(2026, 10, 9, 15, 0, 0, 0, ZoneId.of("America/New_York")).toInstant())
        assertEquals(ReactionStatus.WINDOW_INCOMPLETE, during.status); assertNull(during.absoluteChange); assertEquals("2026-10-08", during.baseline?.sessionDate)
        assertEquals(ReactionStatus.AVAILABLE, compute(event("2026-10-08", EarningsTime.AFTER_CLOSE), now = ZonedDateTime.of(2026, 10, 9, 16, 30, 0, 0, ZoneId.of("America/New_York")).toInstant()).status)
        // 20:15 UTC is 16:15 New York in summer (EDT) but 15:15 in winter (EST): only the summer session is complete.
        assertEquals(ReactionStatus.AVAILABLE, compute(event("2026-07-01", EarningsTime.AFTER_CLOSE), s = series(mapOf("2026-07-01" to "100", "2026-07-02" to "101")), now = Instant.parse("2026-07-02T20:15:00Z")).status)
        assertEquals(ReactionStatus.WINDOW_INCOMPLETE, compute(event("2026-12-01", EarningsTime.AFTER_CLOSE), s = series(mapOf("2026-12-01" to "100", "2026-12-02" to "101")), now = Instant.parse("2026-12-02T20:15:00Z")).status)
    }

    @Test fun missingInvalidAndIncomparablePricesAreUnavailable() {
        val e = event("2026-10-08", EarningsTime.AFTER_CLOSE)
        assertEquals(ReactionStatus.BASELINE_UNAVAILABLE, compute(e, s = series(closes - "2026-10-08")).status)
        val halted = compute(e, s = series(closes - "2026-10-09"))
        assertEquals(ReactionStatus.ENDPOINT_UNAVAILABLE, halted.status); assertTrue(halted.statusMessage!!.contains("trading halted"))
        assertEquals(ReactionStatus.DATA_NOT_COMPARABLE, compute(e, s = series(closes + ("2026-10-08" to "0"))).status)
        val mixedCurrency = series().let { s -> s.copy(bars = s.bars + (LocalDate.parse("2026-10-09") to s.bars.getValue(LocalDate.parse("2026-10-09")).copy(currency = "CAD"))) }
        assertTrue(compute(e, s = mixedCurrency).statusMessage!!.contains("different currencies"))
        val mixedBasis = series().let { s -> s.copy(bars = s.bars + (LocalDate.parse("2026-10-09") to s.bars.getValue(LocalDate.parse("2026-10-09")).copy(adjustment = PriceAdjustment.UNADJUSTED))) }
        assertTrue(compute(e, s = mixedBasis).statusMessage!!.contains("adjustment bases"))
        assertEquals(ReactionStatus.DATA_NOT_COMPARABLE, compute(e, cal = null).status)          // unsupported market
        assertEquals(ReactionStatus.PROVIDER_UNAVAILABLE, compute(e, s = null).status)
        // A gap inside a multi-session window is reported, not filled.
        assertTrue(compute(e, ReactionWindow.THREE_SESSIONS, series(closes - "2026-10-12")).warnings.any { it.contains("No price for Mon, Oct 12") })
    }

    @Test fun corporateActionsQualifyOrBlockTheComparison() {
        val e = event("2026-10-08", EarningsTime.AFTER_CLOSE)
        val split = compute(e, s = series(actions = listOf(CorporateAction(LocalDate.parse("2026-10-09"), "SPLIT", "A 2-for-1 split"))))
        assertEquals(ReactionStatus.AVAILABLE, split.status); assertTrue(split.warnings.any { it.contains("split-adjusted") })
        val dividend = compute(e, s = series(actions = listOf(CorporateAction(LocalDate.parse("2026-10-09"), "SPECIAL_DIVIDEND", "A special dividend"))))
        assertEquals(ReactionStatus.CORPORATE_ACTION_AMBIGUITY, dividend.status); assertNull(dividend.percentChange)
        assertEquals(ReactionStatus.AVAILABLE, compute(e, s = series(actions = listOf(CorporateAction(LocalDate.parse("2026-10-20"), "SPECIAL_DIVIDEND", "Later")))).status)
        assertTrue(compute(e, s = series(known = false)).warnings.any { it.contains("doesn't report stock splits") })
    }

    @Test fun rescheduledEventsUseTheCurrentDateAndSayItMoved() {
        val r = compute(event("2026-10-08", EarningsTime.AFTER_CLOSE, previous = "2026-10-06"))
        assertEquals("2026-10-08", r.baseline?.sessionDate)
        assertTrue(r.warnings.any { it.contains("moved from Tue, Oct 6") })
    }

    @Test fun historyCoversFiveSessionsEachSideWithGapsAndTheMarker() {
        val h = PriceReactionEngine.history(event("2026-10-08", EarningsTime.AFTER_CLOSE), "T:2026-Q3", series(closes - "2026-10-12"), us, later)
        assertEquals("2026-10-02", h.points.first().date); assertEquals("2026-10-15", h.points.last().date)
        assertEquals("2026-10-09", h.eventSlotDate); assertEquals(listOf("2026-10-12"), h.missingSessions)
        assertEquals("Earnings: Thu, Oct 8, after market close", h.eventLabel)
        assertTrue(h.points.map { it.date }.none { LocalDate.parse(it).dayOfWeek.value >= 6 })
    }

    @Test fun reactionLanguageNeverClaimsACause() {
        val sentence = EarningsReactionCalculator.sentence(PriceReaction(true, changePercent = -10.0, methodology = "m"))!!
        assertTrue(sentence.startsWith("The stock fell 10.0% over the measured earnings window"))
        assertFalse(Regex("(?i)\\bbecause\\b|due to|disappoint|cheer").containsMatchIn(sentence))
    }

    @Test fun finnhubRowsNormalizeWithoutInventingFields() {
        val row = buildJsonObject {
            put("symbol", "msft"); put("date", "2026-10-28"); put("hour", ""); put("year", 2027); put("quarter", 1)
            put("epsEstimate", 3.65); put("revenueEstimate", 8.8e10)
        }
        val e = EarningsNormalizer.finnhub(row)!!
        assertEquals("MSFT:2027-Q1", e.id)
        assertEquals(EarningsTime.UNKNOWN, e.session) // blank hint → unknown, never inferred
        assertEquals(EarningsDateStatus.ESTIMATED, e.dateStatus) // a provider date isn't "confirmed"
        assertNull(e.actual)
        assertNull(EarningsNormalizer.finnhub(buildJsonObject { put("symbol", "X"); put("date", "2026-10-28") }), "no fiscal period → rejected")
        assertEquals(EarningsTime.BEFORE_OPEN, EarningsNormalizer.session("bmo"))
        assertEquals(EarningsTime.AFTER_CLOSE, EarningsNormalizer.session("amc"))
        assertEquals(EarningsTime.DURING_MARKET, EarningsNormalizer.session("dmh"))
    }
}

class EarningsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T21:15:00Z"), ZoneOffset.UTC)
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun service(store: UserDataStore = InMemoryUserDataStore(), research: EarningsResearchProvider? = TemplateEarningsResearch, source: EarningsDataSource = FixtureEarningsDataSource()) =
        EarningsService(source, StockService(fixture, fixture), PriceChartService(fixture), store, EntitlementService(store, clock::millis, true), clock, true, research, aiDailyLimit = 2)

    @Test fun calendarSortsFiltersAndPagesWithinABoundedRange(): Unit = runBlocking {
        val s = service()
        val page = s.calendar(EarningsCalendarQuery("2026-10-12", "2026-10-25", view = "upcoming"))
        val keys = page.items.map { it.event.date to it.event.session }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { listOf(EarningsTime.BEFORE_OPEN, EarningsTime.DURING_MARKET, EarningsTime.AFTER_CLOSE, EarningsTime.UNKNOWN).indexOf(it.second) })), keys)
        assertEquals(listOf("JNJ", "JPM"), page.items.filter { it.event.date == "2026-10-14" }.map { it.event.symbol }) // same date & session → by name
        assertTrue(page.items.any { it.event.dateStatus == EarningsDateStatus.CONFIRMED } && page.items.any { it.event.dateStatus == EarningsDateStatus.ESTIMATED })
        assertEquals("2026-10-20", page.items.first { it.event.symbol == "CNR.TO" }.event.previousDate) // a changed date stays visible
        val canada = s.calendar(EarningsCalendarQuery("2026-08-01", "2026-09-30", countries = listOf("CA"), view = "results"))
        assertTrue(canada.items.isNotEmpty() && canada.items.all { it.event.country == "CA" })
        val bmo = s.calendar(EarningsCalendarQuery("2026-10-12", "2026-10-25", sessions = listOf(EarningsTime.BEFORE_OPEN)))
        assertTrue(bmo.items.all { it.event.session == EarningsTime.BEFORE_OPEN })
        // Paging covers everything once.
        val all = mutableListOf<String>(); var cursor: String? = null
        do { val p = s.calendar(EarningsCalendarQuery("2026-10-01", "2026-11-30", pageSize = 4, cursor = cursor)); all += p.items.map { it.event.id }; cursor = p.nextCursor } while (cursor != null)
        assertEquals(all.distinct(), all)
        assertEquals(s.calendar(EarningsCalendarQuery("2026-10-01", "2026-11-30", pageSize = 50)).total, all.size)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-01-01", "2026-12-31")) }.status) // never a whole year
        assertTrue(s.calendar(EarningsCalendarQuery("2026-12-26", "2026-12-31")).items.isEmpty()) // empty calendar
    }

    @Test fun staleUpcomingDatesAreLabelled(): Unit = runBlocking {
        val page = service().calendar(EarningsCalendarQuery("2026-12-01", "2026-12-05"))
        assertTrue(page.stale)
        assertTrue(page.notes.any { it.contains("haven't been updated") })
    }

    @Test fun followingCombinesPortfolioAndWatchlistsWithoutDuplicatesOrOwnershipFromWatchlists(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val portfolios = PortfolioService(store, clock::millis)
        portfolios.saveAccount("alice", PortfolioAccount("a", "Main", reportingCurrency = PortfolioCurrency.USD))
        portfolios.saveTransaction("alice", PortfolioTransaction("o1", "a", TransactionType.OPENING_POSITION, "2026-01-02", PortfolioCurrency.USD, InstrumentRef("JPM", currency = "USD"), "4", "200"), false)
        val watchlists = WatchlistsService(store, now = clock::millis)
        val list = watchlists.get("alice").watchlists.first()
        watchlists.add("alice", list.id, InstrumentRef("JPM", "JPMorgan", "NYSE", "USD"))
        watchlists.add("alice", list.id, InstrumentRef("KO", "Coca-Cola", "NYSE", "USD"))
        val page = service(store).following("alice", EarningsCalendarQuery("2026-10-12", "2026-10-25"))
        assertEquals(listOf("JPM", "KO"), page.items.map { it.event.symbol }.sorted())
        val jpm = page.items.single { it.event.symbol == "JPM" }
        assertEquals(setOf(FollowReason.PORTFOLIO, FollowReason.WATCHLIST), jpm.following.toSet())
        assertEquals(4.0, jpm.sharesHeld)
        val ko = page.items.single { it.event.symbol == "KO" }
        assertEquals(listOf(FollowReason.WATCHLIST), ko.following)
        assertNull(ko.sharesHeld) // watching isn't owning
        assertTrue(service(store).following("bob", EarningsCalendarQuery("2026-10-12", "2026-10-25")).items.isEmpty()) // per-user
    }

    @Test fun detailsAreTierShapedAndKeepFinancialsConsistent(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val s = service(store)
        val free = s.details("NVDA", null)
        assertEquals(EarningsStatus.REPORTED, free.status)
        assertEquals(EarningsService.FREE_HISTORY, free.history.size)
        assertTrue(free.historyLocked)
        assertEquals(1, free.lockedInsights) // the streak insight is StockSteps+
        assertEquals("EPS beat, revenue beat", free.summary)
        store.setEntitlement("plus", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        val plus = s.details("NVDA", "plus")
        assertTrue(plus.history.size > EarningsService.FREE_HISTORY)
        assertFalse(plus.historyLocked)
        assertTrue(plus.insights.any { it.advanced })
        store.setEntitlement("lapsed", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() - 1, "subscription"))
        assertEquals(EarningsService.FREE_HISTORY, s.details("NVDA", "lapsed").history.size)
        // Four quarters of a completed fiscal year add up to the annual revenue Financials shows.
        val annual = runBlocking { CompanyFinancialService(fixture).getFundamentals("KO", "annual") }.history.first { it.fiscalYear == 2025 }
        val quarters = plus.copy().let { s.details("KO", "plus").history.filter { it.event.fiscalYear == 2025 } }
        assertEquals(4, quarters.size)
        assertEquals(annual.revenue!!, quarters.sumOf { it.revenue.actual!! }, 4.0)
    }

    @Test fun pendingPartialMissingAndNonComparableResultsAreHonest(): Unit = runBlocking {
        val s = service()
        assertEquals(EarningsStatus.DATA_PENDING, s.details("BCE.TO", null).status)
        val partial = s.details("BB.TO", null)
        assertEquals(EarningsStatus.PARTIALLY_REPORTED, partial.status)
        assertEquals(Classification.UNAVAILABLE, partial.revenue.classification)
        assertEquals(Classification.BEAT, partial.eps.classification) // −0.05 vs −0.08
        val csu = s.details("CSU.TO", null)
        assertEquals(Classification.UNAVAILABLE, csu.eps.classification) // missing estimate
        assertTrue(csu.revenue.reason!!.contains("currencies"))
        assertEquals(Classification.UNAVAILABLE, s.details("JNJ", null).history.first { it.event.fiscalQuarter == 2 && it.event.fiscalYear == 2026 }.eps.classification) // basis mismatch
        assertNull(s.details("RIVN", null).history.first { it.event.fiscalYear == 2026 && it.event.fiscalQuarter == 2 }.eps.percent) // zero estimate
        val longn = s.details("LONGN", null).reaction!!
        assertFalse(longn.available) // this ticker has no price history
        assertTrue(longn.reason!!.contains("Price history"))
        val aapl = s.details("AAPL", null)
        assertTrue(aapl.reaction!!.changePercent!! < 0) // price fell after an EPS beat
        assertEquals("EPS beat, revenue miss", aapl.summary)
        assertTrue(s.details("MSFT", null).reaction!!.changePercent!! > 0)
        val enb = s.details("ENB.TO", null).reaction!!
        assertTrue(enb.approximate) // Saturday announcement with unknown time
    }

    @Test fun nextAndRecentResultFeedRemindersFromTheSameSource(): Unit = runBlocking {
        val s = service()
        val next = s.next("MSFT")!!
        assertEquals("MSFT:2027-Q1", next.eventId)
        assertEquals(EarningsDateStatus.ESTIMATED, next.status)
        assertNull(s.recentResult("MSFT")) // reported in July: not "recent"
        assertEquals(next, FixtureMarketDataSource(sampleFallback = true).upcoming("MSFT")) // watch-data and alerts agree
    }

    @Test fun aiIsPlusOnlyAndCheckedBeforeAnyProviderCall(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val calls = AtomicInteger()
        val counting = EarningsResearchProvider { q, d -> calls.incrementAndGet(); TemplateEarningsResearch.answer(q, d) }
        val s = service(store, counting)
        assertEquals(403, assertFailsWith<EarningsRequestException> { s.ask("free", "AAPL", "Explain this report") }.status)
        assertEquals(0, calls.get())
        store.setEntitlement("plus", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        val answer = s.ask("plus", "AAPL", "Why did the stock fall after an EPS beat?")
        assertTrue(answer.insufficientEvidence)
        assertTrue(answer.answer.contains("can't say why"))
        assertEquals(1, answer.remainingToday)
        s.ask("plus", "AAPL", "Explain this report")
        assertEquals(429, assertFailsWith<EarningsRequestException> { s.ask("plus", "AAPL", "Again") }.status) // fair use
        store.setEntitlement("plus2", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        assertEquals(503, assertFailsWith<EarningsRequestException> { service(store, null).ask("plus2", "AAPL", "Explain") }.status) // no AI configured (REAL)
    }

    @Test fun providerFailureIs503(): Unit = runBlocking {
        val failing = object : EarningsDataSource {
            override val label = "failing"
            override suspend fun calendar(from: LocalDate, to: LocalDate): List<EarningsEvent> = throw IllegalStateException("down")
            override suspend fun history(symbol: String): List<EarningsEvent> = throw IllegalStateException("down")
        }
        assertEquals(503, assertFailsWith<EarningsRequestException> { service(source = failing).calendar(EarningsCalendarQuery("2026-10-12", "2026-10-18")) }.status)
    }

    @Test fun routesRequireAuthForPersonalDataAndPublicCalendarWorks() = testApplication {
        val store = InMemoryUserDataStore()
        val s = service(store)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1_000)) }
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/calendar?from=2026-10-12&to=2026-10-18").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/AAPL").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/earnings/following").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/earnings/AAPL/ask") { contentType(ContentType.Application.Json); setBody("""{"question":"x"}""") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/me/earnings/AAPL/ask") {
            bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"question":"Explain this report"}""") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/calendar?from=2026-01-01&to=2026-12-31").status)
    }
}

class EarningsRemindersTest {
    private val rules = AlertRules()
    private val zone = ZoneId.of("America/New_York")
    private fun rule(timing: EarningsTiming = EarningsTiming.BOTH, lead: Int? = null, results: Boolean = false, surprise: Double? = null) =
        AlertRule("r1", InstrumentRef("AAPL", "Apple", currency = "USD"), AlertType.EARNINGS, earningsTiming = timing, status = AlertStatus.ACTIVE, createdAt = 0, updatedAt = 0,
            earningsLeadDays = lead, earningsResults = results, earningsSurprisePercent = surprise)
    private fun at(date: String, hour: Int = 10) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.of(hour, 0), zone).toInstant()
    private fun upcoming(date: String) = UpcomingEarnings("AAPL", date, EarningsTime.AFTER_CLOSE, EarningsDateStatus.ESTIMATED, "t", "AAPL:2026-Q4")

    @Test fun movedDateNeverRemindsTwiceForTheSameEvent() {
        val first = rules.decide(rule(), InstrumentSnapshot("AAPL", "Apple", earnings = upcoming("2026-10-29")), at("2026-10-28")) as AlertDecision.Trigger
        val moved = rules.decide(rule(), InstrumentSnapshot("AAPL", "Apple", earnings = upcoming("2026-10-30")), at("2026-10-29")) as AlertDecision.Trigger
        assertEquals(first.eventKey, moved.eventKey) // the outbox's idempotent recordTrigger keeps only one
        assertEquals("r1:earnings-AAPL:2026-Q4-DAY_BEFORE", first.eventKey)
    }

    @Test fun leadDaysAndResultsOptions() {
        val early = rules.decide(rule(lead = 3), InstrumentSnapshot("AAPL", "Apple", earnings = upcoming("2026-10-29")), at("2026-10-26")) as AlertDecision.Trigger
        assertTrue(early.title.contains("in 3 days"))
        assertEquals(AlertDecision.None, rules.decide(rule(lead = 3), InstrumentSnapshot("AAPL", "Apple", earnings = upcoming("2026-10-29")), at("2026-10-28")))
        val reported = EarningsEvent("AAPL:2026-Q3", "AAPL", "Apple", fiscalYear = 2026, fiscalQuarter = 3, date = "2026-07-30", dateStatus = EarningsDateStatus.CONFIRMED,
            estimate = EarningsEstimate(1.0, EpsBasis.ADJUSTED, 100.0, "USD", source = "t"), actual = EarningsActual(1.02, EpsBasis.ADJUSTED, 101.0, "USD", source = "t"), source = "t", updatedAt = "t")
        val result = rules.decide(rule(results = true), InstrumentSnapshot("AAPL", "Apple", earningsResult = reported), at("2026-07-31")) as AlertDecision.Trigger
        assertEquals("r1:earnings-results-AAPL:2026-Q3", result.eventKey)
        assertTrue(result.body.contains("don't determine how the stock moves"))
        // A 2% surprise doesn't pass a 5% threshold; nothing fires.
        assertEquals(AlertDecision.None, rules.decide(rule(results = true, surprise = 5.0), InstrumentSnapshot("AAPL", "Apple", earningsResult = reported), at("2026-07-31")))
    }

    @Test fun advancedReminderOptionsArePlusOnlyOnTheServer(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val market = object : AlertMarketData {
            override suspend fun quote(symbol: String): StockQuote? = null
            override suspend fun currency(symbol: String) = "USD"
        }
        val free = AlertsService(store, market, deliveryNote = "", isPlus = { false })
        val request = CreateAlertRequest(InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"), AlertType.EARNINGS, earningsTiming = EarningsTiming.BOTH)
        assertEquals(EarningsTiming.BOTH, free.create("u", request).alerts.single().earningsTiming) // basic reminder is free
        assertEquals(403, assertFailsWith<UserDataException> { free.create("u2", request.copy(earningsResults = true)) }.status)
        assertEquals(403, assertFailsWith<UserDataException> { free.create("u2", request.copy(earningsLeadDays = 3)) }.status)
        val plus = AlertsService(store, market, deliveryNote = "", isPlus = { true })
        val created = plus.create("p", request.copy(earningsLeadDays = 3, earningsResults = true, earningsSurprisePercent = 5.0)).alerts.single()
        assertEquals(3, created.earningsLeadDays); assertTrue(created.earningsResults); assertEquals(5.0, created.earningsSurprisePercent)
        assertEquals(400, assertFailsWith<UserDataException> { plus.create("p2", request.copy(earningsLeadDays = 30)) }.status)
        // Cancelling removes the rule (nothing left to schedule).
        assertTrue(plus.delete("p", created.id).alerts.isEmpty())
    }
}
