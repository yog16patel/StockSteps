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

suspend inline fun<reified T> HttpClient.apiCall(
    url: String, apiKey: String? = null,
    crossinline configurationBlock: HttpRequestBuilder.() -> Unit,
): T {
    try {
        val response = this.get(url) {
            // Classify statuses ourselves without exceptions containing provider bodies.
            expectSuccess = false
            configurationBlock()
            if (apiKey != null) parameter("apikey", apiKey)
        }
        if (response.status.value !in 200..299) {
            LoggerFactory.getLogger("StockSteps.Provider").warn(
                "Provider HTTP failure: host={}, path={}, status={}",
                response.call.request.url.host, response.call.request.url.encodedPath, response.status.value
            )
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw StockProviderException(Failure.RATE_LIMITED, response.status.value)
        }
        if (response.status.value !in 200..299) {
            throw StockProviderException(Failure.UNAVAILABLE, response.status.value)
        }
        return response.body<T>()
    } catch (cause: Exception) {
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
    }
}