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
        "finnhub.io" in url -> "finnhub"
        "bankofcanada.ca" in url -> "boc"
        "generativelanguage.googleapis.com" in url -> "gemini"
        else -> "other"
    }
    fun endpoint(url: String): String = url.substringBefore('?').let { u ->
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