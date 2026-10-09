package org.example.stocksteps.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.repository.StockProviderException
import org.slf4j.LoggerFactory
import kotlin.test.*

/**
 * Phase 4A-0: the production logback configuration (`src/main/resources/logback.xml`, also used by tests) keeps Ktor at WARN, so provider
 * URLs — which carry FMP's key as a query parameter — never reach the logs; application logs stay diagnosable without secrets.
 * Dummy keys only.
 */
class LoggingSecurityTest {
    private val key = "dummy-fmp-key-4a0-0000"
    private fun client(status: HttpStatusCode, body: String = "[]") = HttpClient(MockEngine { _ ->
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private fun <T> captured(block: () -> T): Pair<T, List<ILoggingEvent>> {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { context = root.loggerContext; start() }
        root.addAppender(appender)
        try { return block() to appender.list.toList() } finally { root.detachAppender(appender) }
    }

    @Test fun productionLevelsKeepKtorQuiet() {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        assertEquals(Level.INFO, root.effectiveLevel)
        for (name in listOf("io.ktor.client.plugins.contentnegotiation.ContentNegotiation", "io.ktor.client.plugins.DefaultResponseValidation",
            "io.ktor.client.HttpClient", "io.ktor.server.routing.Routing", "io.netty.handler")) {
            assertTrue((LoggerFactory.getLogger(name) as Logger).effectiveLevel.isGreaterOrEqual(Level.WARN), name)
        }
        assertTrue((LoggerFactory.getLogger("StockSteps.Provider") as Logger).isInfoEnabled, "application diagnostics stay on")
    }

    @Test fun providerCallsNeverLogTheKeyAndFailuresStayDiagnosable() = runBlocking {
        val meter = ProviderUsageMeter.shared
        val before = meter.count("fmp", "upstream")
        val (_, okLogs) = captured { runBlocking { client(HttpStatusCode.OK).apiCall<List<String>>("https://financialmodelingprep.com/stable/quote", key) { } } }
        val (failure, errorLogs) = captured {
            runBlocking { runCatching { client(HttpStatusCode.InternalServerError).apiCall<List<String>>("https://financialmodelingprep.com/stable/ratios-ttm", key) { } }.exceptionOrNull() }
        }
        val all = okLogs + errorLogs
        assertTrue(all.none { key in it.formattedMessage || "apikey=" in it.formattedMessage }, all.joinToString("\n") { it.formattedMessage })
        assertTrue(all.none { it.loggerName.startsWith("io.ktor") && it.level.levelInt < Level.WARN.levelInt }, "no Ktor debug/trace output")
        assertTrue(errorLogs.any { it.loggerName == "StockSteps.Provider" && "/stable/ratios-ttm" in it.formattedMessage && "500" in it.formattedMessage },
            "failures still name host, path and status")
        assertTrue(failure is StockProviderException)
        assertFalse(key in failure.toString() || "apikey" in failure.toString(), "exceptions don't carry the key")
        assertEquals(before + 2, meter.count("fmp", "upstream"), "metering unchanged")
    }
}
