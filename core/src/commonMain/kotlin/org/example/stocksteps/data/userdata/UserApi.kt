package org.example.stocksteps.data.userdata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApiException

/**
 * Signed-in StockSteps routes (`/api/v1/me/…`). Every request carries the Firebase ID token; a 401
 * is retried once with a refreshed token. The base URL is read per request (Mock/Real switch).
 */
class UserApi(
    private val client: HttpClient,
    private val baseUrl: () -> String,
    private val token: suspend (forceRefresh: Boolean) -> String?
) {
    suspend fun watchlists(): WatchlistsResponse = send(HttpMethod.Get, "watchlists")
    suspend fun createWatchlist(name: String): WatchlistsResponse = send(HttpMethod.Post, "watchlists", CreateWatchlistRequest(name))
    suspend fun renameWatchlist(id: String, name: String): WatchlistsResponse = send(HttpMethod.Patch, "watchlists/${id.segment()}", RenameWatchlistRequest(name))
    suspend fun deleteWatchlist(id: String): WatchlistsResponse = send(HttpMethod.Delete, "watchlists/${id.segment()}")
    suspend fun reorderWatchlists(ids: List<String>): WatchlistsResponse = send(HttpMethod.Put, "watchlists/order", ReorderRequest(ids))
    suspend fun importEntries(instruments: List<InstrumentRef>): WatchlistsResponse = send(HttpMethod.Post, "watchlists/import", ImportEntriesRequest(instruments))
    suspend fun addEntry(listId: String, instrument: InstrumentRef): WatchlistsResponse = send(HttpMethod.Post, "watchlists/${listId.segment()}/entries", AddEntryRequest(instrument))
    suspend fun removeEntry(listId: String, entryId: String): WatchlistsResponse = send(HttpMethod.Delete, "watchlists/${listId.segment()}/entries/${entryId.segment()}")
    suspend fun updateNote(listId: String, entryId: String, note: String?): WatchlistsResponse =
        send(HttpMethod.Patch, "watchlists/${listId.segment()}/entries/${entryId.segment()}", UpdateEntryRequest(note))
    suspend fun moveEntry(listId: String, entryId: String, targetId: String, copy: Boolean): WatchlistsResponse =
        send(HttpMethod.Post, "watchlists/${listId.segment()}/entries/${entryId.segment()}/move", MoveEntryRequest(targetId, copy))
    suspend fun reorderEntries(listId: String, ids: List<String>): WatchlistsResponse = send(HttpMethod.Put, "watchlists/${listId.segment()}/entries/order", ReorderRequest(ids))

    suspend fun alerts(): AlertsResponse = send(HttpMethod.Get, "alerts")
    suspend fun createAlert(request: CreateAlertRequest): AlertsResponse = send(HttpMethod.Post, "alerts", request)
    suspend fun updateAlert(id: String, request: UpdateAlertRequest): AlertsResponse = send(HttpMethod.Patch, "alerts/${id.segment()}", request)
    suspend fun deleteAlert(id: String): AlertsResponse = send(HttpMethod.Delete, "alerts/${id.segment()}")

    suspend fun registerDevice(request: RegisterDeviceRequest) { send<Unit>(HttpMethod.Post, "devices", request) }
    suspend fun unregisterDevice(deviceId: String) { send<Unit>(HttpMethod.Delete, "devices/${deviceId.segment()}") }

    private suspend inline fun <reified T> send(method: HttpMethod, path: String, body: Any? = null): T {
        var response = request(method, path, body, refresh = false)
        if (response.status == HttpStatusCode.Unauthorized) response = request(method, path, body, refresh = true)
        if (response.status.value !in 200..299) {
            val error = try { response.body<ApiError>() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                ApiError("HTTP_ERROR", "The StockSteps server could not complete the request.")
            }
            throw StockStepsApiException(response.status.value, error)
        }
        @Suppress("UNCHECKED_CAST")
        return if (T::class == Unit::class) Unit as T else response.body()
    }

    private suspend fun request(method: HttpMethod, path: String, body: Any?, refresh: Boolean): HttpResponse {
        val bearer = token(refresh) ?: throw StockStepsApiException(401, ApiError("SIGN_IN_REQUIRED", "Sign in to use watchlists and alerts."))
        return client.request("${baseUrl().trimEnd('/')}/api/v1/me/$path") {
            this.method = method
            expectSuccess = false
            header(HttpHeaders.Authorization, "Bearer $bearer")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private fun String.segment(): String {
        require(matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid id" }
        return this
    }
}
