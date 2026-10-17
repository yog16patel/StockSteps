package org.example.stocksteps.httpclient

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import java.io.IOException
import org.slf4j.LoggerFactory

/**
 * Provider usage at the one place every FMP/Finnhub request passes: provider and endpoint come from [url]
 * (never the query string, which carries the FMP key), the feature from the caller's `ProviderFeature`.
 * Each attempt counts once as `upstream`, plus its outcome (`ok`, `rateLimited`, `denied`, `error`,
 * `timeout`, `invalid`) and latency. Caches count their own hits/misses; nothing else records `upstream`.
 */
object ProviderCalls {
    val meter get() = org.example.stocksteps.service.ProviderUsageMeter.shared
    fun provider(url: String): String = when {
        "financialmodelingprep.com" in url -> "fmp"
        // Phase 9B: SEC EDGAR (data.sec.gov APIs and www.sec.gov reference files) — free, keyless, fair-access paced.
        "sec.gov" in url -> "sec"
        "finnhub.io" in url -> "finnhub"
        "bankofcanada.ca" in url -> "boc"
        "generativelanguage.googleapis.com" in url -> "gemini"
        else -> "other"
    }
    fun endpoint(url: String): String = url.substringBefore('?').let { u ->
        // SEC paths carry the CIK; labels stay bounded (one per dataset, never per company).
        if ("sec.gov" in u) return@let when {
            "/companyfacts/" in u -> "xbrl/companyfacts"
            "/companyconcept/" in u -> "xbrl/companyconcept"
            "/submissions/" in u -> "submissions"
            else -> u.substringAfterLast('/').takeIf { it.startsWith("company_tickers") } ?: "other"
        }
        listOf("/stable/", "/api/v1/", "/valet/", "/v1beta/models/").firstNotNullOfOrNull { marker -> u.substringAfter(marker, "").takeIf { it.isNotEmpty() } } ?: u.substringAfterLast('/')
    }.take(60)
    suspend fun record(url: String, outcome: String, startedNanos: Long) {
        val feature = meter.feature()
        val provider = provider(url)
        val endpoint = endpoint(url)
        meter.record(provider, endpoint, feature, "upstream")
        meter.record(provider, endpoint, feature, outcome)
        // Phase 4A: the request that caused this upstream call is charged for it by admission control.
        org.example.stocksteps.security.RequestCost.add(provider)
        meter.event("provider.$provider.latencyMs", (System.nanoTime() - startedNanos) / 1_000_000)
    }
}

suspend inline fun<reified T> HttpClient.apiCall(
    url: String, apiKey: String? = null,
    crossinline configurationBlock: HttpRequestBuilder.() -> Unit,
): T {
    // Phase 4C: every upstream attempt (retries included) passes the provider-wide budget first; a denial throws before any request.
    val guard = org.example.stocksteps.service.ProviderGuard.current()
    val permit = guard?.acquire(ProviderCalls.provider(url), ProviderCalls.endpoint(url))
    val started = System.nanoTime()
    var outcome = "error"
    var status: Int? = null
    var retryAfter: Long? = null
    try {
        val response = this.get(url) {
            // Classify statuses ourselves without exceptions containing provider bodies.
            expectSuccess = false
            configurationBlock()
            if (apiKey != null) parameter("apikey", apiKey)
        }
        status = response.status.value
        retryAfter = response.headers[io.ktor.http.HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
        if (response.status.value !in 200..299) {
            LoggerFactory.getLogger("StockSteps.Provider").warn(
                "Provider HTTP failure: host={}, path={}, status={}",
                response.call.request.url.host, response.call.request.url.encodedPath, response.status.value
            )
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            outcome = "rateLimited"
            throw StockProviderException(Failure.RATE_LIMITED, response.status.value)
        }
        if (response.status.value !in 200..299) {
            outcome = if (response.status.value == 402 || response.status.value == 403) "denied" else "error"
            throw StockProviderException(Failure.UNAVAILABLE, response.status.value)
        }
        outcome = "invalid"
        return response.body<T>().also { outcome = "ok" }
    } catch (cause: Exception) {
        if (cause is HttpRequestTimeoutException || cause is ConnectTimeoutException || cause is SocketTimeoutException) outcome = "timeout"
        if (cause is CancellationException) outcome = "cancelled"
        val failure = when (cause) {
            is StockProviderException -> throw cause
            is HttpRequestTimeoutException, is ConnectTimeoutException,
            is SocketTimeoutException -> Failure.TIMEOUT
            is CancellationException -> throw cause
            is SerializationException, is JsonConvertException,
            is NoTransformationFoundException -> Failure.INVALID_RESPONSE
            is ResponseException -> throw StockProviderException(Failure.UNAVAILABLE, cause.response.status.value)
            is IOException -> Failure.UNAVAILABLE
            else -> throw cause
        }
        LoggerFactory.getLogger("StockSteps.Provider").warn("Provider request failed: {}", failure.name)
        throw StockProviderException(failure)
    } finally {
        if (permit != null) guard.complete(permit, status, timedOut = outcome == "timeout", retryAfterSeconds = retryAfter, cancelled = outcome == "cancelled")
        ProviderCalls.record(url, outcome, started)
    }
}
/**
 * Phase 9B: [apiCall] for large public documents (SEC EDGAR `companyfacts` is several MB): the same provider guard, outcome
 * classification, logging and the single `ProviderCalls.record`, but the body is read as text and **bounded**: a declared
 * `Content-Length` above [maxBytes] is refused before reading, and reading stops after [maxBytes] + 1 bytes (`INVALID_RESPONSE`,
 * outcome `invalid`). No API key parameter (keyless public sources); callers add their own headers (e.g. the SEC User-Agent).
 * 404 is `UNAVAILABLE` with status 404, so callers can treat "no such document" as an answer, not an outage.
 */
suspend fun HttpClient.apiCallText(
    url: String, maxBytes: Long,
    configurationBlock: HttpRequestBuilder.() -> Unit = {},
): String {
    require(maxBytes > 0)
    val guard = org.example.stocksteps.service.ProviderGuard.current()
    val permit = guard?.acquire(ProviderCalls.provider(url), ProviderCalls.endpoint(url))
    val started = System.nanoTime()
    var outcome = "error"
    var status: Int? = null
    var retryAfter: Long? = null
    try {
        val response = this.get(url) { expectSuccess = false; configurationBlock() }
        status = response.status.value
        retryAfter = response.headers[io.ktor.http.HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
        if (response.status.value !in 200..299) {
            LoggerFactory.getLogger("StockSteps.Provider").warn(
                "Provider HTTP failure: host={}, path={}, status={}",
                response.call.request.url.host, ProviderCalls.endpoint(url), response.status.value
            )
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            outcome = "rateLimited"
            throw StockProviderException(Failure.RATE_LIMITED, response.status.value)
        }
        if (response.status.value !in 200..299) {
            outcome = if (response.status.value == 402 || response.status.value == 403) "denied" else "error"
            throw StockProviderException(Failure.UNAVAILABLE, response.status.value)
        }
        outcome = "invalid"
        val declared = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > maxBytes) throw StockProviderException(Failure.INVALID_RESPONSE)
        val bytes = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
        if (bytes.size > maxBytes) throw StockProviderException(Failure.INVALID_RESPONSE)
        return bytes.decodeToString().also { outcome = "ok" }
    } catch (cause: Exception) {
        if (cause is HttpRequestTimeoutException || cause is ConnectTimeoutException || cause is SocketTimeoutException) outcome = "timeout"
        if (cause is CancellationException) outcome = "cancelled"
        val failure = when (cause) {
            is StockProviderException -> throw cause
            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> Failure.TIMEOUT
            is CancellationException -> throw cause
            is IOException -> Failure.UNAVAILABLE
            else -> throw cause
        }
        LoggerFactory.getLogger("StockSteps.Provider").warn("Provider request failed: {}", failure.name)
        throw StockProviderException(failure)
    } finally {
        if (permit != null) guard.complete(permit, status, timedOut = outcome == "timeout", retryAfterSeconds = retryAfter, cancelled = outcome == "cancelled")
        ProviderCalls.record(url, outcome, started)
    }
}
