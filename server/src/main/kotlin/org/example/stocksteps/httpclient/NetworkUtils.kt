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

suspend inline fun<reified T> HttpClient.apiCall(
    url: String, apiKey: String,
    crossinline configurationBlock: HttpRequestBuilder.() -> Unit,
): T {
    try {
        val response = this.get(url) {
            // Classify statuses ourselves without exceptions containing provider bodies.
            expectSuccess = false
            configurationBlock()
            parameter("apikey", apiKey)
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw StockProviderException(Failure.RATE_LIMITED)
        }
        if (response.status.value !in 200..299) {
            throw StockProviderException(Failure.UNAVAILABLE)
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
            is IOException, is ResponseException -> Failure.UNAVAILABLE
            else -> throw cause
        }
        throw StockProviderException(failure)
    }
}