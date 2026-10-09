package org.example.stocksteps.learning

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.CompanyDetailsService
import org.example.stocksteps.service.CompanyFinancialService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class LearningRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixtures = FixtureMarketDataSource()
    private val details = CompanyDetailsService(StockService(fixtures, fixtures), CompanyFinancialService(fixtures), fixtures)

    private fun ApplicationTestBuilder.install(store: UserDataStore = InMemoryUserDataStore(), research: ResearchAiProvider? = TemplateResearchAi, limit: Int = 20, calls: AtomicInteger = AtomicInteger(),
                                               wallClock: Clock = clock) {
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val provider = research?.let { inner -> ResearchAiProvider { q, s, snap -> calls.incrementAndGet(); inner.answer(q, s, snap) } }
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { learningRoutes(MockUserAuthenticator(), LearningService(store, entitlements, details::getDetails, provider, limit, clock, wallClock)) }
        }
    }

    private fun HttpRequestBuilder.user(uid: String) = header(HttpHeaders.Authorization, "Bearer mock-user:$uid")
    private fun doc(vararg journeys: ResearchProgress) = LearningProgressDocument(journeys.toList(), 1)
    private fun journey(symbol: String, steps: List<Int>, visited: Long) = ResearchProgress(symbol, symbol, steps, startedAt = 1, lastVisited = visited)
    private suspend fun ApplicationTestBuilder.put(uid: String, body: LearningProgressDocument) = client.put("/api/v1/me/learning") {
        user(uid); contentType(ContentType.Application.Json); setBody(json.encodeToString(LearningProgressDocument.serializer(), body))
    }
    private suspend fun ApplicationTestBuilder.ask(uid: String, question: String = "What does this mean?", symbol: String = "AAPL", step: Int? = 3) =
        client.post("/api/v1/me/research/$symbol/ask") { user(uid); contentType(ContentType.Application.Json); setBody(json.encodeToString(ResearchQuestion.serializer(), ResearchQuestion(question, step))) }

    /** Phase 5C regression: MOCK's market clock is pinned to the fixture capture; devices stamp visits with real time. */
    @Test fun futureCheckUsesRealTimeNotThePinnedMarketClock() = testApplication {
        val now = Instant.parse("2026-10-11T12:00:00Z")   // three days after the pinned market clock
        install(wallClock = Clock.fixed(now, ZoneOffset.UTC))
        assertEquals(HttpStatusCode.OK, put("dev", doc(journey("AAPL", listOf(1), now.toEpochMilli()))).status)
        val tooFar = put("dev", doc(journey("KO", listOf(1), now.toEpochMilli() + 2 * 86_400_000L)))
        assertEquals(HttpStatusCode.BadRequest, tooFar.status)
        assertTrue(tooFar.bodyAsText().contains("INVALID_PROGRESS"))
    }

    @Test fun progressRequiresSignIn() = testApplication {
        install()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/learning").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/research/AAPL/ask").status)
    }

    @Test fun progressIsMergedPerCompanyAndKeptPerUser() = testApplication {
        install()
        assertEquals(HttpStatusCode.OK, put("alice", doc(journey("aapl", listOf(1, 2), 10), journey("KO", listOf(1), 10))).status)
        val merged = json.decodeFromString(LearningProgressDocument.serializer(), put("alice", doc(journey("AAPL", listOf(1), 5), journey("JPM", listOf(3, 1, 1), 20))).bodyAsText())
        assertEquals(listOf("JPM", "AAPL", "KO"), merged.journeys.map { it.symbol })
        assertEquals(listOf(1, 2), merged.journeys.first { it.symbol == "AAPL" }.completedSteps, "an older visit never overwrites newer progress")
        assertEquals(listOf(1, 3), merged.journeys.first { it.symbol == "JPM" }.completedSteps)
        val bob = json.decodeFromString(LearningProgressDocument.serializer(), client.get("/api/v1/me/learning") { user("bob") }.bodyAsText())
        assertTrue(bob.journeys.isEmpty())
    }

    @Test fun invalidProgressIsRejected() = testApplication {
        install()
        assertEquals(HttpStatusCode.BadRequest, put("alice", doc(journey("AAPL", listOf(9), 10))).status)
        assertEquals(HttpStatusCode.BadRequest, put("alice", doc(journey("../x", listOf(1), 10))).status)
        assertEquals(HttpStatusCode.BadRequest, put("alice", doc(journey("AAPL", listOf(1), clock.millis() + 10 * 86_400_000L))).status)
        assertEquals(HttpStatusCode.BadRequest, put("alice", LearningProgressDocument((1..51).map { journey("S$it", listOf(1), 10) })).status)
    }

    @Test fun storageOutageIsAClear503() = testApplication {
        install(store = UnavailableUserDataStore)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/me/learning") { user("alice") }.status)
    }

    @Test fun freeUsersAreRefusedBeforeAnyAiCall() = testApplication {
        val calls = AtomicInteger()
        install(calls = calls)
        val response = ask("alice")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue("PLUS_REQUIRED" in response.bodyAsText())
        assertEquals(0, calls.get())
    }

    @Test fun plusUsersGetAGroundedSampleAnswerWithADailyLimit() = testApplication {
        val store = InMemoryUserDataStore()
        runBlocking { store.setEntitlement("carol", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription")) }
        install(store = store, limit = 2)
        val first = json.decodeFromString(ResearchAnswer.serializer(), ask("carol").bodyAsText())
        assertTrue(first.answer.startsWith("Sample answer"))
        assertTrue(first.answer.contains("Apple"))
        assertEquals(1, first.remainingToday)
        val why = json.decodeFromString(ResearchAnswer.serializer(), ask("carol", "Should I buy it?").bodyAsText())
        assertTrue(why.answer.contains("doesn't make buy or sell recommendations"))
        assertEquals(HttpStatusCode.TooManyRequests, ask("carol").status)
        assertEquals(HttpStatusCode.BadRequest, ask("carol", "?").status)
    }

    @Test fun expiredPlusIsRefusedAndRealWithoutAProviderIs503() = testApplication {
        val store = InMemoryUserDataStore()
        runBlocking {
            store.setEntitlement("dan", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() - 1, "subscription"))
            store.setEntitlement("erin", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription"))
        }
        install(store = store, research = null)
        assertEquals(HttpStatusCode.Forbidden, ask("dan").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, ask("erin").status)
    }
}

/** The guide over the MOCK fixtures: the same CompanyDetails the app shows. */
class GuidedResearchFixturesTest {
    private val fixtures = FixtureMarketDataSource()
    private val details = CompanyDetailsService(StockService(fixtures, fixtures), CompanyFinancialService(fixtures), fixtures)
    private fun build(symbol: String) = runBlocking { GuidedResearchEngine.build(details.getDetails(symbol), "2026-10-08") }

    @Test fun appleHasAllFiveStepsWithData() {
        val apple = build("AAPL")
        assertEquals(CompanyKind.OPERATING, apple.kind)
        assertNotNull(apple.step(2)!!.chart)
        assertTrue(apple.step(2)!!.chart!!.bars.size in 2..5)
        assertNotNull(apple.step(3)!!.meaning)
        assertNotNull(apple.step(4)!!.figures.firstOrNull { it.label == "Cash" })
        assertNotNull(apple.step(5)!!.figures.firstOrNull { it.label == "P/E ratio" })
    }

    @Test fun banksEtfsAndCanadianCompaniesAreHandled() {
        assertEquals(CompanyKind.FINANCIAL, build("JPM").kind)
        // MOCK fixtures hold only prices for ETFs (no profile flag, no statements): explained, never guessed from the name.
        assertEquals(CompanyKind.UNSUPPORTED, build("SPY").kind)
        val ry = build("RY.TO")
        assertTrue(ry.steps.flatMap { it.figures }.any { it.value.startsWith("C$") } || ry.kind == CompanyKind.FINANCIAL)
    }

    @Test fun everyFixtureCompanyBuildsWithoutInventingNumbers() {
        listOf("AAPL", "MSFT", "KO", "JPM", "SPY", "TSLA", "RIVN", "NVDA", "SHOP.TO", "LONGN", "XOM").forEach { symbol ->
            val snapshot = build(symbol)
            assertEquals(5, snapshot.steps.size, symbol)
            snapshot.steps.forEach { step ->
                assertTrue(step.meaning != null || step.unavailableReason != null || step.step == ResearchStep.BUSINESS, "$symbol step ${step.step}")
                step.figures.forEach { assertFalse(it.value.contains("NaN") || it.value.contains("Infinity"), "$symbol ${it.label}") }
            }
        }
    }
}
