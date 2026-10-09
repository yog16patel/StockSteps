package org.example.stocksteps.service

import ch.qos.logback.classic.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.stocksteps.httpclient.apiCall
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.test.*

/** Phase 4D: per-instance usage summaries are deltas with bounded labels, aggregate across instances, and never carry sensitive data. */
class UsageSummaryTest {
    private var t = Instant.parse("2026-10-07T18:00:00Z")

    @Test fun summariesAreDeltasAndQuietIntervalsEmitNothing() {
        val meter = ProviderUsageMeter()
        val r = UsageSummaryReporter(meter, { t }, "inst-a") { }
        meter.record("fmp", "quote", "company-details", "upstream"); meter.record("fmp", "quote", "company-details", "ok"); meter.event("cache.fmp.hit")
        meter.gauge("screener.coverage.evaluated", 140); meter.gauge("screener.coverage.size", 150)
        t = t.plusSeconds(60)
        val first = r.next()!!
        assertEquals(listOf(UsageLine("fmp", "quote", "company-details", "ok", 1), UsageLine("fmp", "quote", "company-details", "upstream", 1)), first.counters)
        assertEquals(mapOf("cache.fmp.hit" to 1L), first.events)
        assertEquals(140L, first.gauges["screener.coverage.evaluated"])
        assertEquals("2026-10-07T18:01:00Z", first.to)
        assertNull(r.next(), "nothing new → no line")
        meter.record("fmp", "quote", "company-details", "upstream"); meter.event("provider.fmp.budget.denied.rate", 3)
        val second = r.next()!!
        assertEquals(1L, second.counters.single { it.event == "upstream" }.count, "only the new request")
        assertEquals(3L, second.events["provider.fmp.budget.denied.rate"])
    }

    @Test fun summariesFromSeveralInstancesAddUpToTheTotal() {
        val meters = (1..3).map { ProviderUsageMeter() }
        val reporters = meters.mapIndexed { i, m -> UsageSummaryReporter(m, { t }, "inst-$i") { } }
        meters.forEachIndexed { i, m -> repeat(i + 1) { m.record("finnhub", "calendar/earnings", "earnings", "upstream") } }
        val total = reporters.mapNotNull { it.next() }.flatMap { it.counters }.filter { it.event == "upstream" }.sumOf { it.count }
        assertEquals(6, total, "a logs-based metric summing `count` gives the deployment total without reaching a particular instance")
        assertEquals(3, reporters.map { it.instance }.distinct().size)
    }

    @Test fun summaryLinesAreSingleLineJsonWithoutSecretsOrUserData() = runBlocking {
        val key = "dummy-usage-key-4d"
        val client = HttpClient(MockEngine { respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }) {
            install(ContentNegotiation) { json() }
        }
        val reporter = UsageSummaryReporter(ProviderUsageMeter.shared, { t }) { }
        reporter.next()                                                       // baseline: earlier tests' counts
        client.apiCall<List<String>>("https://financialmodelingprep.com/stable/quote", key) { parameter("symbol", "SECRETCO") }
        val line = reporter.encode(reporter.next()!!)
        assertFalse('\n' in line)
        val obj = Json.parseToJsonElement(line).jsonObject
        assertEquals("\"stocksteps.usage\"", obj["kind"].toString())
        for (forbidden in listOf(key, "apikey", "SECRETCO", "https://", "Bearer", "uid")) assertFalse(forbidden in line, "$forbidden in $line")
        assertTrue(line.contains("\"endpoint\":\"quote\""))
        val usageLogger = LoggerFactory.getLogger("StockSteps.Usage") as Logger
        assertFalse(usageLogger.isAdditive, "summaries use their own plain-JSON appender")
        assertTrue(usageLogger.isInfoEnabled)
    }
}
