package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.movementRoutes
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.*

class MovementTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val fixtures = FixtureMarketDataSource(sampleFallback = true, now = { now })
    private fun service(narrator: MovementNarrator? = null) =
        MovementService(StockService(fixtures, fixtures), PriceChartService(fixtures), NewsService(fixtures, now = { now }), narrator, now = { now })

    @Test fun oneDayMoveIsComputedFromTheQuoteWithBenchmarksAndAlignedNews() = runBlocking {
        val msft = assertNotNull(service().explain("MSFT", MovementPeriod.ONE_DAY))
        assertEquals(529.3, msft.startPrice); assertEquals(529.76, msft.endPrice)
        assertEquals((529.76 / 529.3 - 1) * 100, msft.changePercent!!, 1e-9)
        assertEquals("2026-10-06", msft.startDate); assertEquals("2026-10-07", msft.endDate)
        assertEquals(listOf("SPY", "QQQ", "XLK"), msft.benchmarks.map { it.symbol })
        assertEquals(-0.24, msft.benchmarks.first().changePercent, 0.01)
        val close = Instant.parse("2026-10-07T20:00:00Z")
        assertTrue(msft.events.isNotEmpty() && msft.events.size <= 5)
        assertTrue(msft.events.all { Instant.parse(it.publishedAt).let { t -> !t.isAfter(close) && !t.isBefore(Instant.parse("2026-10-06T08:00:00Z")) } })
        assertFalse(msft.noConfirmedCatalyst)
        assertEquals(EvidenceLabel.CONFIRMED_EVENT, msft.events.first().label)
        assertTrue(msft.whatWeCannotConfirm.first().contains("Timing alone"))
        assertFalse(msft.aiGenerated)
        listOf(msft.summary, *msft.whatWeKnow.toTypedArray()).forEach { assertFalse(Regex("(?i)\\b(because|caused|due to|driven by)\\b").containsMatchIn(it), it) }
    }

    @Test fun commentaryOnlyNewsMeansNoConfirmedCatalyst() = runBlocking {
        val nvda = assertNotNull(service().explain("NVDA", MovementPeriod.ONE_DAY))
        assertTrue(nvda.noConfirmedCatalyst)
        assertTrue(nvda.events.none { it.label == EvidenceLabel.CONFIRMED_EVENT })
        assertTrue(nvda.summary.contains("none was a confirmed company-specific event"))
        assertTrue(nvda.whatWeCannotConfirm.any { it.contains("didn't find a confirmed") })
    }

    @Test fun weekAndMonthUseDailyClosesOnTheSameDatesForBenchmarks() = runBlocking {
        val week = assertNotNull(service().explain("AAPL", MovementPeriod.ONE_WEEK))
        assertEquals("2026-09-30", week.startDate); assertEquals("2026-10-07", week.endDate)
        val closes = fixtures.getDailyCloses("AAPL").associate { it.time to it.close }
        assertEquals(closes.getValue("2026-09-30"), week.startPrice)
        val spy = fixtures.getDailyCloses("SPY").associate { it.time to it.close }
        assertEquals((spy.getValue("2026-10-07") / spy.getValue("2026-09-30") - 1) * 100, week.benchmarks.first { it.symbol == "SPY" }.changePercent, 1e-9)
        val month = assertNotNull(service().explain("AAPL", MovementPeriod.ONE_MONTH))
        assertTrue(java.time.LocalDate.parse(month.startDate) <= java.time.LocalDate.parse("2026-09-07"))
        assertTrue(abs(month.changePercent!! - week.changePercent!!) > 0.001)
    }

    @Test fun narratorOutputIsValidatedAndCached() = runBlocking {
        val calls = AtomicInteger()
        val causal = service { calls.incrementAndGet(); "Microsoft rose because of its new laptop, which caused buyers to pile in today." }
        val result = assertNotNull(causal.explain("MSFT", MovementPeriod.ONE_DAY))
        assertFalse(result.aiGenerated, "causal wording is rejected")
        val invented = service { "Microsoft rose 12.5% on Oct 7, in the same period as several company events." }
        assertFalse(assertNotNull(invented.explain("MSFT", MovementPeriod.ONE_DAY)).aiGenerated, "numbers not in the facts are rejected")
        val good = service { facts -> "${facts.companyName} changed ${facts.changePercent} on these dates. Company news happened in the same period and may have contributed." }
        val narrated = assertNotNull(good.explain("MSFT", MovementPeriod.ONE_DAY))
        assertTrue(narrated.aiGenerated)
        assertTrue(narrated.summary.contains("+0.09%"))
        causal.explain("MSFT", MovementPeriod.ONE_DAY)
        assertEquals(1, calls.get(), "explanations are cached")
    }

    @Test fun routeValidatesPeriodAndReportsMissingData() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { movementRoutes(service()) }
        }
        val body = client.get("/api/v1/stocks/TSLA/movement?period=1w")
        assertEquals(HttpStatusCode.OK, body.status)
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(MovementExplanation.serializer(), body.bodyAsText())
        assertEquals(MovementPeriod.ONE_WEEK, parsed.period)
        assertTrue(body.bodyAsText().contains("\"period\": \"1W\"") || body.bodyAsText().contains("\"period\":\"1W\""))
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/TSLA/movement?period=5Y").status)
        val empty = MovementService(StockService(FixtureMarketDataSource(), FixtureMarketDataSource()), PriceChartService(FixtureMarketDataSource()), NewsService(FixtureMarketDataSource()))
        assertNull(empty.explain("NONE", MovementPeriod.ONE_DAY), "no quote, no explanation")
    }
}
