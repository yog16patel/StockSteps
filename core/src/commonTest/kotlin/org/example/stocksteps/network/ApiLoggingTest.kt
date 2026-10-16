package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.model.ApiError
import kotlin.test.*

class ApiLoggingTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private class Captured {
        val lines = mutableListOf<Pair<ApiLogLevel, String>>()
        val text get() = lines.joinToString("\n") { it.second }
        fun of(prefix: String) = lines.map { it.second }.filter { it.startsWith("$prefix ") }
    }

    private fun client(
        captured: Captured,
        environment: () -> String = { "MOCK" },
        maxBodyChars: Int = ApiLogging.DEFAULT_MAX_BODY_CHARS,
        engine: MockEngine
    ): HttpClient {
        val settings = ApiLogSettings(environment, ApiLogWriter({ level, line -> captured.lines += level to line }, dispatcher = null), maxBodyChars)
        return HttpClient(engine) {
            configureStockStepsClient()
            install(StockStepsApiLogging) { this.settings = { settings } }
        }
    }

    private fun idOf(line: String) = Regex("id=(\\S+)").find(line)!!.groupValues[1]

    @Test fun requestAndResponseShareOneIdWithMethodUrlAndRedactedQueryHeadersAndJson() = runBlocking<Unit> {
        val captured = Captured()
        val body = """{"symbol":"AAPL","price":189.5,"owner":{"email":"pat@example.com","idToken":"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"},"items":[{"apiKey":"k-123","name":"Apple Inc."}]}"""
        client(captured, engine = MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("sid=abc"))) }).use { http ->
            val text = http.get("https://stocksteps.test:8443/api/v1/stocks/AAPL/quote?symbol=AAPL&apikey=secret-key&email=pat@example.com#frag") {
                header(HttpHeaders.Authorization, "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln")
                header(HttpHeaders.Cookie, "session=xyz")
                header("X-Firebase-AppCheck", "app-check-token")
            }.body<String>()
            assertEquals(body, text, "the app still reads the full body")
        }
        val request = captured.of("REQ").single()
        val response = captured.of("RES").single()
        assertEquals(idOf(request), idOf(response))
        assertTrue("env=MOCK" in request && "env=MOCK" in response)
        assertTrue("method=GET url=https://stocksteps.test:8443/api/v1/stocks/AAPL/quote?symbol=AAPL&apikey=[REDACTED]&email=[REDACTED]" in request, request)
        assertTrue("host=stocksteps.test port=8443 path=/api/v1/stocks/AAPL/quote" in request, request)
        assertTrue("Authorization=[REDACTED]" in request && "Cookie=[REDACTED]" in request && "X-Firebase-AppCheck=[REDACTED]" in request, request)
        assertTrue("Accept=application/json" in request, request)
        assertTrue(Regex("ts=\\d{4}-\\d{2}-\\d{2}T").containsMatchIn(request), request)
        assertTrue("status=200 result=OK" in response && "size=${body.length}" in response && Regex("durationMs=\\d+").containsMatchIn(response), response)
        assertTrue("Set-Cookie=[REDACTED]" in response, response)
        assertTrue(""""symbol":"AAPL"""" in response && """"price":189.5""" in response && """"name":"Apple Inc."""" in response, response)
        assertTrue(""""owner":"[REDACTED]"""" in response && """"apiKey":"[REDACTED]"""" in response, response)
        for (secret in listOf("secret-key", "pat@example.com", "eyJhbGci", "session=xyz", "app-check-token", "sid=abc", "k-123", "frag")) {
            assertFalse(secret in captured.text, "leaked $secret")
        }
    }

    @Test fun signedInPayloadsAreMetadataOnlyAndAuthIsUnchanged() = runBlocking<Unit> {
        val captured = Captured()
        var sentAuthorization: String? = null
        val ledger = """{"accounts":[{"id":"acc_7f2","name":"Brokerage","cash":"1520.75"}],"transactions":[],"revision":4,"reportingCurrency":"USD"}"""
        val engine = MockEngine { request ->
            sentAuthorization = request.headers[HttpHeaders.Authorization]
            respond(ledger, headers = json)
        }
        client(captured, environment = { "REAL" }, engine = engine).use { http ->
            val api = UserApi(http, { "https://stocksteps.test" }, token = { "firebase-id-token-value" })
            val result = api.portfolio()
            assertEquals(1, result.accounts.size, "the repository still parses the ledger")
            assertEquals("Bearer firebase-id-token-value", sentAuthorization, "the token is sent unchanged")
        }
        val response = captured.of("RES").single()
        assertTrue("[METADATA] {accounts:array(1)[{id:string,name:string,cash:string}],transactions:array(0),revision:number,reportingCurrency:string}" in response, response)
        for (value in listOf("1520.75", "Brokerage", "acc_7f2", "firebase-id-token-value")) assertFalse(value in captured.text, "leaked $value")
        assertTrue("env=REAL" in response)
    }

    @Test fun signedInPathIdsAndRequestBodiesAreHidden() = runBlocking<Unit> {
        val captured = Captured()
        client(captured, engine = MockEngine { respond("""{"accounts":[],"transactions":[]}""", headers = json) }).use { http ->
            http.post("https://stocksteps.test/api/v1/me/portfolio/accounts/acc_7f2?range=1M") {
                contentType(ContentType.Application.Json)
                setBody(ApiError("cash", "9999.99"))
            }
        }
        val request = captured.of("REQ").single()
        assertTrue("path=/api/v1/me/portfolio/accounts/[REDACTED]" in request && "query=range=1M" in request, request)
        assertTrue("body=[METADATA] {code:string,message:string}" in request, request)
        assertFalse("9999.99" in captured.text || "acc_7f2" in captured.text)
    }

    @Test fun errorResponsesKeepTheirCodeAndAreWarnings() = runBlocking<Unit> {
        val captured = Captured()
        val engine = MockEngine { respond("""{"code":"USER_DATA_UNAVAILABLE","message":"Watchlists and alerts are temporarily unavailable."}""", HttpStatusCode.ServiceUnavailable, json) }
        client(captured, environment = { "REAL" }, engine = engine).use { http ->
            val api = UserApi(http, { "https://stocksteps.test" }, token = { "t" })
            val error = assertFailsWith<StockStepsApiException> { api.practice() }
            assertEquals(503, error.status)
            assertEquals("USER_DATA_UNAVAILABLE", error.error.code, "error mapping unchanged")
        }
        val (level, response) = captured.lines.single { it.second.startsWith("RES ") }
        assertEquals(ApiLogLevel.WARN, level)
        assertTrue("method=GET path=/api/v1/me/practice status=503 result=HTTP_5XX" in response, response)
        assertTrue(""""code":"USER_DATA_UNAVAILABLE"""" in response, response)

        val notFound = Captured()
        client(notFound, engine = MockEngine { respond("missing", HttpStatusCode.NotFound) }).use { it.get("https://stocksteps.test/api/v1/nope") }
        assertTrue("status=404 result=HTTP_4XX" in notFound.of("RES").single())
    }

    @Test fun connectionFailuresAreClassifiedAndRethrownUnchanged() = runBlocking<Unit> {
        val captured = Captured()
        val failure = IllegalStateException("Failed to reach https://stocksteps.test/x?token=abc for pat@example.com")
        client(captured, engine = MockEngine { throw failure }).use { http ->
            val thrown = assertFailsWith<IllegalStateException> { http.get("https://stocksteps.test/api/v1/meta") }
            assertEquals(failure.message, thrown.message) // same exception (coroutines may recover a copy with its stack)
        }
        val error = captured.of("ERR").single()
        assertTrue("kind=UNKNOWN type=IllegalStateException" in error, error)
        assertTrue("token=[REDACTED]" in error && "abc" !in error && "pat@example.com" !in error, error)
        assertEquals(idOf(captured.of("REQ").single()), idOf(error))
    }

    @Test fun timeoutsAreFailuresWithASanitizedMessage() = runBlocking<Unit> {
        val captured = Captured()
        client(captured, engine = MockEngine { delay(5_000); respond("late") }).use { http ->
            assertFails { http.get("https://stocksteps.test/api/v1/meta?apikey=secret") { timeout { requestTimeoutMillis = 50 } } }
        }
        val error = captured.of("ERR").single()
        assertTrue("kind=TIMEOUT_REQUEST type=HttpRequestTimeoutException" in error, error)
        assertFalse("secret" in error, error)
        assertTrue(captured.of("CANCEL").isEmpty())
    }

    @Test fun cancellationIsReportedApartFromFailures() = runBlocking<Unit> {
        val captured = Captured()
        val started = CompletableDeferred<Unit>()
        client(captured, engine = MockEngine { started.complete(Unit); delay(10_000); respond("late") }).use { http ->
            val job = launch { http.get("https://stocksteps.test/api/v1/meta") }
            started.await()
            job.cancel()
            job.join()
        }
        val cancel = captured.of("CANCEL").single()
        assertTrue("method=GET path=/api/v1/meta" in cancel, cancel)
        assertTrue(captured.of("ERR").isEmpty(), captured.text)
        assertEquals(ApiLogLevel.INFO, captured.lines.single { it.second.startsWith("CANCEL ") }.first)
    }

    @Test fun deserializationFailuresGiveTypeAndFieldsOnly() = runBlocking<Unit> {
        val captured = Captured()
        client(captured, engine = MockEngine { respond("""{"unexpected":"private-value-123"}""", headers = json) }).use { http ->
            assertFails { http.get("https://stocksteps.test/api/v1/meta").body<ApiError>() }
        }
        val error = captured.of("ERR").single()
        assertTrue("kind=SERIALIZATION" in error && "target=ApiError" in error, error)
        assertTrue("missing=" in error, error)
        assertFalse("private-value-123" in error, error)
    }

    @Test fun largeBodiesAreTruncatedAndHugeOnesNotInspected() = runBlocking<Unit> {
        val captured = Captured()
        val big = "[" + (1..400).joinToString(",") { """{"symbol":"S$it","name":"Company $it"}""" } + "]"
        val huge = "[" + "1,".repeat(ApiLogRedaction.PARSE_LIMIT_BYTES / 2) + "1]"
        client(captured, maxBodyChars = 120, engine = MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith("huge")) huge else big, headers = json)
        }).use { http ->
            assertEquals(big, http.get("https://stocksteps.test/api/v1/big").body<String>())
            assertEquals(huge.length, http.get("https://stocksteps.test/api/v1/huge").body<String>().length)
        }
        val (first, second) = captured.of("RES")
        assertTrue("[TRUNCATED]" in first && first.substringAfter("body=").length < 200, first)
        assertTrue("[TRUNCATED] (size=${huge.length} bytes, not inspected)" in second, second)
    }

    @Test fun binaryBodiesAreOmittedAndStillReadable() = runBlocking<Unit> {
        val captured = Captured()
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0)
        client(captured, engine = MockEngine { respond(png, headers = headersOf(HttpHeaders.ContentType, "image/png")) }).use { http ->
            assertContentEquals(png, http.get("https://stocksteps.test/logo.png").body<ByteArray>())
        }
        val response = captured.of("RES").single()
        assertTrue("body=[BINARY CONTENT OMITTED]" in response && "size=10" in response && "contentType=image/png" in response, response)
    }

    @Test fun concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter() = runBlocking<Unit> {
        val lines = mutableListOf<String>()
        val settings = ApiLogSettings({ "MOCK" }, ApiLogWriter({ _, line -> lines += line }))
        val count = 25
        HttpClient(MockEngine { request -> delay(5); respond("""{"n":"${request.url.parameters["n"]}"}""", headers = json) }) {
            configureStockStepsClient()
            install(StockStepsApiLogging) { this.settings = { settings } }
        }.use { http ->
            withContext(Dispatchers.Default) {
                (1..count).map { n -> async { http.get("https://stocksteps.test/api/v1/meta?n=$n").body<String>() } }.awaitAll()
            }
        }
        withTimeout(5_000) { while (lines.count { it.startsWith("RES ") } < count) delay(10) }
        val requests = lines.filter { it.startsWith("REQ ") }
        val ids = requests.map(::idOf)
        assertEquals(count, ids.toSet().size, "distinct ids")
        assertEquals(ids.toSet(), lines.filter { it.startsWith("RES ") }.map(::idOf).toSet())
        // Every response line carries its own request's parameter.
        requests.forEach { request ->
            val n = Regex("n=(\\d+)").find(request)!!.groupValues[1]
            assertTrue(lines.single { it.startsWith("RES id=${idOf(request)} ") }.contains("\"n\":\"$n\""))
        }
    }

    @Test fun environmentLabelsComeFromTheExplicitChoiceAndSwitchesAreLogged() = runBlocking<Unit> {
        val captured = Captured()
        var environment = "UNSET-${kotlin.random.Random.nextInt()}"
        val engine = MockEngine { respond("{}", headers = json) }
        client(captured, environment = { environment }, engine = engine).use { warmUp -> warmUp.get("http://10.0.2.2:8081/api/v1/meta") }
        captured.lines.clear()
        environment = "MOCK"
        client(captured, environment = { environment }, engine = engine).use { http ->
            client(captured, environment = { environment }, engine = engine).use { second ->
                http.get("http://10.0.2.2:8081/api/v1/meta")
                second.get("http://10.0.2.2:8081/api/v1/meta") // another client, same environment: no second ENV line
                environment = "REAL"
                http.get("http://10.0.2.2:8081/api/v1/meta") // same URL: the label still follows the choice, not the host
            }
        }
        val switches = captured.of("ENV")
        assertEquals(2, switches.size, "one line per switch; the tracker is process-wide")
        assertTrue("from=UNSET-" in switches[0] && "to=MOCK host=10.0.2.2 port=8081" in switches[0], switches[0])
        assertTrue("from=MOCK to=REAL" in switches[1], switches[1])
        assertEquals(listOf("MOCK", "MOCK", "REAL"), captured.of("REQ").map { Regex("env=(\\S+)").find(it)!!.groupValues[1] })
    }

    @Test fun releaseBuildsCannotEnableLogging() = runBlocking<Unit> {
        val captured = Captured()
        val settings = ApiLogSettings({ "REAL" }, ApiLogWriter({ level, line -> captured.lines += level to line }, dispatcher = null))
        try {
            assertFalse(ApiLogging.enable(debugBuild = false, settings = settings))
            assertNull(ApiLogging.settings)
            HttpClient(MockEngine { respond("{}", headers = json) }) {
                configureStockStepsClient()
                install(StockStepsApiLogging) // default: the global settings
            }.use { http -> http.get("https://stocksteps.test/api/v1/meta") }
            assertTrue(captured.lines.isEmpty())
            assertTrue(ApiLogging.enable(debugBuild = true, settings = settings))
            HttpClient(MockEngine { respond("{}", headers = json) }) {
                configureStockStepsClient()
                install(StockStepsApiLogging)
            }.use { http -> http.get("https://stocksteps.test/api/v1/meta") }
            assertEquals(1, captured.of("REQ").size)
            // A later non-debug call turns it off again.
            assertFalse(ApiLogging.enable(debugBuild = false, settings = settings))
        } finally {
            ApiLogging.disable()
        }
    }
}
