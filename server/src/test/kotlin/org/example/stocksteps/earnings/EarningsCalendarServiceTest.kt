package org.example.stocksteps.earnings

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.*
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 1: the Earnings Calendar API (MOCK fixtures, MOCK clock 2026-10-07 17:15 New York). */
class EarningsCalendarServiceTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-07T21:15:00Z"))
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun service(store: UserDataStore = InMemoryUserDataStore(), source: EarningsDataSource = FixtureEarningsDataSource(), sampleData: Boolean = true,
                        cache: CompanyFinancialCache = CompanyFinancialCache(512)) =
        EarningsService(source, StockService(fixture, fixture), PriceChartService(fixture), store, EntitlementService(store, clock::millis, true), clock, sampleData, null, cache = cache)

    private suspend fun EarningsService.statusOf(id: String) = event(id).item.eventStatus

    @Test fun statusComesFromSourceDataNotFromTheDatePassing(): Unit = runBlocking {
        val s = service()
        assertEquals(EarningsEventStatus.REPORTED, s.statusOf("LUCY:2026-Q3"))    // figures reported today
        assertEquals(EarningsEventStatus.SCHEDULED, s.statusOf("INTC:2026-Q3"))   // today after the close, no figures yet
        assertEquals(EarningsEventStatus.UNKNOWN, s.statusOf("BCE.TO:2026-Q3"))   // date passed, no results: not "reported"
        assertEquals(EarningsEventStatus.POSTPONED, s.statusOf("SSPX:2026-Q3"))   // explicit source flag only
        assertEquals(EarningsEventStatus.CANCELED, s.statusOf("SSCX:2026-Q3"))
        val reported = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", view = "reported")).items.map { it.event.symbol }
        assertTrue("LUCY" in reported && "BCE.TO" !in reported && "INTC" !in reported)
        val upcoming = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", view = "upcoming")).items.map { it.event.symbol }
        assertTrue(listOf("BCE.TO", "INTC", "CRBU", "TOPP", "SPEC", "SSPX").all { it in upcoming } && "LUCY" !in upcoming)
    }

    @Test fun dayCountsCoverTheWholeRangeWhileTheDayFilterNarrowsItems(): Unit = runBlocking {
        val s = service()
        val page = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", view = "upcoming", day = "2026-10-08"))
        assertEquals(listOf("CRBU", "TOPP", "SPEC"), page.items.map { it.event.symbol }) // several on one day: before open, after close, unknown
        assertEquals(3, page.dayCounts["2026-10-08"])
        assertEquals(1, page.dayCounts["2026-10-07"])                                     // INTC (LUCY is reported)
        assertNull(page.dayCounts["2026-10-10"])                                           // Saturday: nothing scheduled
        assertTrue(s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", day = "2026-10-10")).items.isEmpty())
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", day = "2026-10-20")) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", view = "maybe")) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-11", "2026-10-05")) }.status)
        // US company on Canadian Thanksgiving (TSX closed): kept on its own date.
        assertEquals(listOf("GCDT"), s.calendar(EarningsCalendarQuery("2026-10-12", "2026-10-12")).items.map { it.event.symbol })
    }

    @Test fun dateOnlyEventsKeepTheirDateAndTimingIsNeverInferred(): Unit = runBlocking {
        val s = service()
        val intc = s.event("INTC:2026-Q3").item.event
        assertEquals("2026-10-07", intc.date)
        assertEquals("After market close · 4:05 PM ET", EarningsCalendarRules.timing(intc))
        val spec = s.event("SPEC:2026-Q3").item.event
        assertEquals("2026-10-08", spec.date)
        assertEquals(EarningsTime.UNKNOWN, spec.session)
        assertEquals("Time not confirmed", EarningsCalendarRules.timing(spec))
        assertNull(spec.sourceUpdatedAt)                                                   // the source gave no timestamp; none is invented
        assertNull(spec.logoUrl)
        // Late evening UTC is still the same New York day: no shift into the next date.
        clock.now = Instant.parse("2026-10-08T03:30:00Z")                                  // 23:30 on Oct 7 in New York
        assertEquals(EarningsEventStatus.SCHEDULED, service().statusOf("INTC:2026-Q3"))
    }

    @Test fun searchMatchesTickerAndNameCaseInsensitivelyWithinABoundedRange(): Unit = runBlocking {
        val s = service()
        assertEquals(listOf("INTC"), s.calendar(EarningsCalendarQuery("2026-10-05", "2026-12-06", query = "intel")).items.map { it.event.symbol })
        assertEquals(listOf("CRBU"), s.calendar(EarningsCalendarQuery("2026-10-05", "2026-12-06", query = "crb")).items.map { it.event.symbol })
        assertEquals(setOf("TD", "TD.TO"), s.calendar(EarningsCalendarQuery("2026-10-05", "2026-12-06", query = "td")).items.map { it.event.symbol }.toSet())
        assertTrue(s.calendar(EarningsCalendarQuery("2026-10-05", "2026-12-06", query = "zzzz")).items.isEmpty())
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", query = "x".repeat(41))) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-01-01", "2026-12-31", query = "apple")) }.status)
    }

    @Test fun pagingCoversEveryEventOnceWithStableKeys(): Unit = runBlocking {
        val s = service()
        val seen = mutableListOf<String>(); var cursor: String? = null
        do {
            val p = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-11-30", view = "upcoming", pageSize = 3, cursor = cursor))
            assertTrue(p.items.size <= 3); seen += p.items.map { it.event.id }; cursor = p.nextCursor
        } while (cursor != null)
        assertEquals(seen.distinct(), seen)
        assertEquals(s.calendar(EarningsCalendarQuery("2026-10-05", "2026-11-30", view = "upcoming", pageSize = 50)).total, seen.size)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", pageSize = 51)) }.status)
    }

    @Test fun watchlistScopeUsesOnlyTheUsersOwnListsDeduplicatedByCanonicalListing(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val watchlists = WatchlistsService(store, now = clock::millis)
        val first = watchlists.get("alice").watchlists.first()
        watchlists.add("alice", first.id, InstrumentRef("TD", "Toronto-Dominion Bank", "NYSE", "USD"))
        watchlists.add("alice", first.id, InstrumentRef("KO", "Coca-Cola", "NYSE", "USD"))
        val second = watchlists.create("alice", "Banks").watchlists.first { it.name == "Banks" }
        watchlists.add("alice", second.id, InstrumentRef("TD", "Toronto-Dominion Bank", "NYSE", "USD")) // same listing in two lists
        val s = service(store)
        val page = s.following("alice", EarningsCalendarQuery("2026-11-23", "2026-12-06", scope = "watchlist"))
        assertEquals(listOf("TD:2026-Q4"), page.items.map { it.event.id })              // once; the TSX listing TD.TO isn't on the list
        assertEquals(2, page.followedCount)
        assertTrue(page.items.single().following == listOf(FollowReason.WATCHLIST))
        // Another user never sees alice's companies; an empty watchlist is reported as such.
        val bob = s.following("bob", EarningsCalendarQuery("2026-11-23", "2026-12-06", scope = "watchlist"))
        assertTrue(bob.items.isEmpty()); assertEquals(0, bob.followedCount)
        // Watchlist filtering is never public.
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-11-23", "2026-12-06", scope = "watchlist")) }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.following("alice", EarningsCalendarQuery("2026-11-23", "2026-12-06", scope = "everyone")) }.status)
    }

    @Test fun freshCachedAndStaleAreDistinguishedAndFailuresAreNeverReplacedWithSampleData(): Unit = runBlocking {
        var failing = false
        var calls = 0
        val source = object : EarningsDataSource {
            val inner = FixtureEarningsDataSource()
            override val label = "test"
            override suspend fun calendar(from: LocalDate, to: LocalDate): List<EarningsEvent> { calls++; if (failing) error("down"); return inner.calendar(from, to) }
            override suspend fun history(symbol: String): List<EarningsEvent> { if (failing) error("down"); return inner.history(symbol) }
        }
        var millis = 0L
        val s = service(source = source, sampleData = false, cache = CompanyFinancialCache(512, now = { millis }))
        val q = EarningsCalendarQuery("2026-10-05", "2026-10-11")
        val fresh = s.calendar(q)
        assertEquals(DataFreshness.FRESH, fresh.freshness)
        clock.now = clock.now.plusSeconds(600); millis += 600_000
        val cached = s.calendar(q)
        assertEquals(DataFreshness.CACHED, cached.freshness); assertEquals(1, calls)
        assertEquals(fresh.fetchedAt, cached.fetchedAt)
        // Cache expired and the source fails: the last real copy, labelled STALE, with its fetch time.
        failing = true; millis += 4_000_000; clock.now = clock.now.plusSeconds(4_000)
        val stale = s.calendar(q)
        assertEquals(DataFreshness.STALE, stale.freshness)
        assertEquals(fresh.fetchedAt, stale.fetchedAt)
        assertEquals(fresh.total, stale.total)
        // A window never loaded before: an honest 503, not sample data.
        assertEquals(503, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-12", "2026-10-18")) }.status)
        // REAL ignores MOCK scenarios.
        failing = false
        assertNotEquals(DataFreshness.STALE, s.calendar(EarningsCalendarQuery("2026-10-19", "2026-10-25", scenario = "stale-cache")).freshness)
    }

    @Test fun mockScenariosSimulateProviderFailureAndStaleData(): Unit = runBlocking {
        val s = service()
        assertEquals(503, assertFailsWith<EarningsRequestException> { s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", scenario = "provider-unavailable")) }.status)
        val stale = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11", scenario = "stale-cache"))
        assertEquals(DataFreshness.STALE, stale.freshness)
        assertTrue(stale.items.isNotEmpty() && stale.sampleData)
    }

    @Test fun eventLookupAndCompanyNextEarnings(): Unit = runBlocking {
        val s = service()
        val info = s.event("INTC:2026-Q3")
        assertEquals("Intel Corporation", info.item.event.name)
        assertTrue(info.sampleData)
        assertEquals(404, assertFailsWith<EarningsRequestException> { s.event("NOPE:2026-Q1") }.status)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.event("not an id") }.status)
        assertEquals("AAPL:2026-Q4", s.nextEvent("AAPL").event?.id)
        assertEquals(EarningsEventStatus.SCHEDULED, s.nextEvent("AAPL").eventStatus)
        assertNull(s.nextEvent("GOOGL").event)                                             // no date known: none invented
        assertNull(s.nextEvent("SSCX").event)                                              // canceled isn't "next"
        assertNull(s.next("SSPX"))                                                         // postponed: no reminder date
        assertEquals("CNR.TO:2026-Q3", s.nextEvent("CNR.TO").event?.id)
        assertEquals("2026-10-20", s.nextEvent("CNR.TO").event?.previousDate)             // rescheduled: one event, old date kept visible
    }

    @Test fun routesExposeTheCalendarPubliclyAndTheWatchlistOnlyWhenSignedIn() = testApplication {
        val store = InMemoryUserDataStore()
        val s = service(store)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1_000)) }
        }
        val week = client.get("/api/v1/earnings/calendar?from=2026-10-05&to=2026-10-11&view=upcoming&day=2026-10-08&q=&pageSize=10")
        assertEquals(HttpStatusCode.OK, week.status)
        val page = json.decodeFromString(EarningsCalendarPage.serializer(), week.bodyAsText())
        assertEquals(3, page.items.size); assertEquals(3, page.dayCounts["2026-10-08"])
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/calendar/search?from=2026-10-05&to=2026-12-06&q=Intel").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/calendar/search?from=2026-10-05&to=2026-12-06").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/calendar?from=2026-10-05&to=2026-10-11&scope=watchlist").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/calendar?from=10/05/2026&to=2026-10-11").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/events/INTC%3A2026-Q3").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/earnings/events/NOPE%3A2026-Q1").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/company/AAPL/next").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/earnings/following?from=2026-10-05&to=2026-10-11&scope=watchlist").status)
        val mine = client.get("/api/v1/me/earnings/following?from=2026-10-05&to=2026-10-11&scope=watchlist") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.OK, mine.status)
        assertEquals(0, json.decodeFromString(EarningsCalendarPage.serializer(), mine.bodyAsText()).followedCount)
    }
}
