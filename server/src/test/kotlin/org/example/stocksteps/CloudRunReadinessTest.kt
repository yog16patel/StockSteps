package org.example.stocksteps

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.appconfig.StartupConfiguration
import org.example.stocksteps.logging.CloudLoggingJsonLayout
import org.example.stocksteps.security.RouteGroup
import kotlin.test.*

/** Phase 5A: Cloud Run probes, startup configuration validation and JSON logging. */
class CloudRunReadinessTest {
    private val validReal = mapOf("FMP_API_KEY" to "test-fmp", "FINNHUB_API_KEY" to "test-finnhub")
    private val validCloudRun = validReal + mapOf(
        "K_SERVICE" to "stocksteps-api-staging", "LOG_FORMAT" to "json",
        "FIREBASE_PROJECT_ID" to "example-staging", "NEWS_FIRESTORE_PROJECT_ID" to "example-staging",
        "CLOUD_RUN_MAX_INSTANCES" to "1", "PROVIDER_FMP_PER_MINUTE" to "300", "PROVIDER_FINNHUB_PER_MINUTE" to "60",
        "PROVIDER_GEMINI_PER_MINUTE" to "30", "PROVIDER_BOC_PER_MINUTE" to "10")

    private fun check(mode: DataMode, env: Map<String, String>) = StartupConfiguration.check(mode) { env[it] }

    @Test fun healthProbesAnswerFromProcessStateOnly() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            healthRoutes()
        }
        val live = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, live.status)
        assertEquals("alive", Json.parseToJsonElement(live.bodyAsText()).jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("no-store", live.headers["Cache-Control"])
        val ready = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, ready.status)
        // Only a status word: no data mode, versions, project ids or settings.
        assertEquals(setOf("status"), Json.parseToJsonElement(ready.bodyAsText()).jsonObject.keys)
        // Probes belong to no admission group: never rate limited, never charged, never App Check-gated.
        assertNull(RouteGroup.of("/health/live"))
        assertNull(RouteGroup.of("/health/ready"))
    }

    @Test fun readinessTurnsUnavailableWhenShutdownBegins() = testApplication {
        lateinit var app: io.ktor.server.application.Application
        application {
            install(ContentNegotiation) { json() }
            healthRoutes()
            app = this
        }
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        app.monitor.raise(ApplicationStopPreparing, app.environment)
        val stopping = client.get("/health/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, stopping.status)
        assertTrue(stopping.bodyAsText().contains("stopping"))
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }

    @Test fun portDefaultsTo8080AndRejectsMalformedValues() {
        assertEquals(8080, StartupConfiguration.port { null })
        assertEquals(9000, StartupConfiguration.port { if (it == "PORT") "9000" else null })
        for (bad in listOf("abc", "0", "70000", "-1")) assertFailsWith<IllegalStateException> { StartupConfiguration.port { if (it == "PORT") bad else null } }
    }

    @Test fun realModeRequiresProviderKeysAndAcceptsALocalRun() {
        val missing = check(DataMode.REAL, emptyMap())
        assertTrue(missing.errors.any { "FMP_API_KEY" in it } && missing.errors.any { "FINNHUB_API_KEY" in it })
        val local = check(DataMode.REAL, validReal)
        assertEquals(emptyList(), local.errors)
        assertTrue(local.warnings.any { "GEMINI_API_KEY" in it })
        // MOCK needs no provider keys and doesn't validate REAL-only settings.
        assertEquals(emptyList(), check(DataMode.MOCK, mapOf("TRUSTED_PROXY_HOPS" to "two")).errors)
    }

    @Test fun malformedSecurityAndBudgetSettingsFailInsteadOfFallingBack() {
        val cases = mapOf(
            "TRUSTED_PROXY_HOPS" to "two", "CLOUD_RUN_MAX_INSTANCES" to "0", "PROVIDER_FMP_PER_MINUTE" to "lots",
            "PROVIDER_GEMINI_CONCURRENCY" to "-3", "PROVIDER_SAFETY_MARGIN" to "1.5", "APP_CHECK_ENFORCE" to "yes",
            "FIREBASE_PROJECT_NUMBER" to "my-project", "LOG_FORMAT" to "JSON", "QUOTE_PROVIDER" to "yahoo", "NEWS_STORE" to "redis",
            "WATCH_DATA_ANONYMOUS_MAX_SYMBOLS" to "0", "PORT" to "http")
        for ((name, value) in cases) {
            val report = check(DataMode.REAL, validReal + (name to value))
            assertTrue(report.errors.any { name in it }, "$name=$value should be rejected: ${report.errors}")
            assertFalse(report.errors.any { value in it && value.length > 2 }, "messages never echo values")
        }
        assertTrue(check(DataMode.REAL, validReal + ("TRUSTED_PROXY_HOPS" to "6")).errors.any { "TRUSTED_PROXY_HOPS" in it })
        assertEquals(emptyList(), check(DataMode.REAL, validReal + ("TRUSTED_PROXY_HOPS" to "1")).errors)
    }

    @Test fun internalJobAuthenticationMustBeCompleteAndSecretsLong() {
        val halfOidc = check(DataMode.REAL, validReal + ("INTERNAL_OIDC_AUDIENCE" to "https://example.run.app"))
        assertTrue(halfOidc.errors.any { "INTERNAL_OIDC_SERVICE_ACCOUNT" in it })
        val short = check(DataMode.REAL, validReal + ("ALERTS_EVALUATOR_TOKEN" to "short-secret"))
        assertTrue(short.errors.any { "ALERTS_EVALUATOR_TOKEN" in it })
        assertFalse(short.errors.any { "short-secret" in it })
        assertTrue(check(DataMode.REAL, validReal + ("APP_CHECK_ENFORCE" to "true")).errors.any { "FIREBASE_PROJECT_NUMBER" in it })
        assertEquals(emptyList(), check(DataMode.REAL, validReal + mapOf("INTERNAL_OIDC_AUDIENCE" to "https://example.run.app",
            "INTERNAL_OIDC_SERVICE_ACCOUNT" to "scheduler@example.iam.gserviceaccount.com", "USAGE_METRICS_TOKEN" to "x".repeat(32))).errors)
    }

    @Test fun cloudRunNeedsExplicitProjectsBudgetsAndInstanceCount() {
        assertEquals(emptyList(), check(DataMode.REAL, validCloudRun).errors)
        for (name in listOf("FIREBASE_PROJECT_ID", "NEWS_FIRESTORE_PROJECT_ID", "CLOUD_RUN_MAX_INSTANCES", "PROVIDER_FMP_PER_MINUTE",
                "PROVIDER_FINNHUB_PER_MINUTE", "PROVIDER_GEMINI_PER_MINUTE", "PROVIDER_BOC_PER_MINUTE")) {
            assertTrue(check(DataMode.REAL, validCloudRun - name).errors.any { name in it }, "$name is required on Cloud Run")
        }
        // GOOGLE_CLOUD_PROJECT also names the Firestore project.
        assertEquals(emptyList(), check(DataMode.REAL, validCloudRun - "NEWS_FIRESTORE_PROJECT_ID" + ("GOOGLE_CLOUD_PROJECT" to "example-staging")).errors)
        val warnings = check(DataMode.REAL, validCloudRun - "LOG_FORMAT").warnings
        assertTrue(warnings.any { "LOG_FORMAT" in it } && warnings.any { "internal job" in it } && warnings.any { "TRUSTED_PROXY_HOPS" in it })
        assertTrue(check(DataMode.REAL, validCloudRun + ("NEWS_FIRESTORE_PROJECT_ID" to "other")).warnings.any { "differs" in it })
        assertTrue(check(DataMode.REAL, validCloudRun + ("NEWS_STORE" to "sqlite")).warnings.any { "NEWS_STORE" in it })
        // The same settings off Cloud Run keep the development defaults (with the existing "not production-ready" warning).
        assertEquals(emptyList(), check(DataMode.REAL, validReal).errors)
        // MOCK stays refused on Cloud Run.
        assertFailsWith<IllegalStateException> { DataMode.fromEnvironment { mapOf(DataMode.ENV to "mock", "K_SERVICE" to "svc")[it] } }
    }

    @Test fun jsonLogLinesCarrySeverityAndEscapeMessages() {
        val layout = CloudLoggingJsonLayout().apply { context = LoggerContext(); start() }
        val logger = LoggerContext().getLogger("StockSteps.Test")
        val event = LoggingEvent("x", logger, Level.WARN, "quote \"unavailable\"\nfor {}", null, arrayOf("SMPL"))
        val line = layout.doLayout(event)
        assertTrue(line.endsWith("\n") && line.trimEnd().lines().size == 1)
        val json = Json.parseToJsonElement(line).jsonObject
        assertEquals("WARNING", json["severity"]!!.jsonPrimitive.content)
        assertEquals("quote \"unavailable\"\nfor SMPL", json["message"]!!.jsonPrimitive.content)
        assertEquals("StockSteps.Test", json["logger"]!!.jsonPrimitive.content)
        assertEquals(setOf("severity", "message", "time", "logger", "thread"), json.keys)
        val failure = LoggingEvent("x", logger, Level.ERROR, "failed", IllegalStateException("boom"), null)
        val error = Json.parseToJsonElement(layout.doLayout(failure)).jsonObject
        assertEquals("ERROR", error["severity"]!!.jsonPrimitive.content)
        assertTrue(error["message"]!!.jsonPrimitive.content.contains("java.lang.IllegalStateException"))
        assertEquals("DEBUG", CloudLoggingJsonLayout.severity(Level.TRACE))
        assertEquals("INFO", CloudLoggingJsonLayout.severity(Level.INFO))
    }
}
