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
    private val token: suspend (forceRefresh: Boolean) -> String?,
    private val identity: () -> String? = { null }
) {
    suspend fun portfolioReport(accountId: String, range: String? = null, expectedOwner: String? = null): org.example.stocksteps.portfolio.PortfolioReport {
        require(range == null || range in setOf("1D", "1W", "1M", "3M", "1Y", "ALL"))
        return send(HttpMethod.Get, "portfolio/accounts/${accountId.segment()}/${if (range == null) "summary" else "history?range=$range"}", expectedOwner = expectedOwner)
    }
    suspend fun portfolio(): org.example.stocksteps.portfolio.PortfolioLedger = send(HttpMethod.Get, "portfolio")
    suspend fun savePortfolioAccount(account: org.example.stocksteps.portfolio.PortfolioAccount): org.example.stocksteps.portfolio.PortfolioLedger = send(HttpMethod.Put, "portfolio/accounts", account)
    suspend fun deletePortfolioAccount(id: String): org.example.stocksteps.portfolio.PortfolioLedger = send(HttpMethod.Delete, "portfolio/accounts/${id.segment()}")
    suspend fun savePortfolioTransaction(transaction: org.example.stocksteps.portfolio.PortfolioTransaction, edit: Boolean): org.example.stocksteps.portfolio.PortfolioLedger = send(if (edit) HttpMethod.Put else HttpMethod.Post, "portfolio/transactions", transaction)
    suspend fun deletePortfolioTransaction(id: String): org.example.stocksteps.portfolio.PortfolioLedger = send(HttpMethod.Delete, "portfolio/transactions/${id.segment()}")
    suspend fun portfolioAnalytics(accountId: String, period: org.example.stocksteps.portfolio.analytics.AnalyticsPeriod, benchmark: org.example.stocksteps.portfolio.analytics.BenchmarkId?, expectedOwner: String? = null): org.example.stocksteps.portfolio.analytics.PortfolioAnalytics =
        send(HttpMethod.Get, "portfolio/accounts/${accountId.segment()}/analytics?period=${period.label}" + (benchmark?.let { "&benchmark=${it.name}" } ?: ""), expectedOwner = expectedOwner)
    suspend fun entitlements(): org.example.stocksteps.portfolio.analytics.Entitlements = send(HttpMethod.Get, "entitlements")
    suspend fun savedScreens(): org.example.stocksteps.screener.SavedScreensResponse = send(HttpMethod.Get, "screens")
    suspend fun earningsFollowing(query: org.example.stocksteps.earnings.EarningsCalendarQuery): org.example.stocksteps.earnings.EarningsCalendarPage {
        val params = io.ktor.http.ParametersBuilder().apply {
            append("from", query.from); append("to", query.to); append("pageSize", query.pageSize.toString())
            if (query.exchanges.isNotEmpty()) append("exchange", query.exchanges.joinToString(","))
            if (query.countries.isNotEmpty()) append("country", query.countries.joinToString(","))
            if (query.sessions.isNotEmpty()) append("session", query.sessions.joinToString(",") { it.name })
            query.view?.let { append("view", it) }
            query.cursor?.let { append("cursor", it) }
        }.build().formUrlEncode()
        return send(HttpMethod.Get, "earnings/following?$params")
    }
    /** Tier-aware details (extended history and advanced insights for StockSteps+). */
    suspend fun earningsDetails(symbol: String): org.example.stocksteps.earnings.EarningsDetails {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return send(HttpMethod.Get, "earnings/${symbol.uppercase()}")
    }
    /** StockSteps+ only; the server refuses (403) before any AI provider is called. */
    suspend fun askEarnings(symbol: String, question: String): org.example.stocksteps.earnings.EarningsAnswer {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return send(HttpMethod.Post, "earnings/${symbol.uppercase()}/ask", org.example.stocksteps.earnings.EarningsQuestion(question))
    }
    suspend fun learning(expectedOwner: String? = null): org.example.stocksteps.learning.LearningProgressDocument = send(HttpMethod.Get, "learning", expectedOwner = expectedOwner)
    /** The server merges per company (most recent visit wins) and returns the merged document. */
    suspend fun saveLearning(document: org.example.stocksteps.learning.LearningProgressDocument, expectedOwner: String? = null): org.example.stocksteps.learning.LearningProgressDocument =
        send(HttpMethod.Put, "learning", document, expectedOwner = expectedOwner)
    /** StockSteps+ only; the server refuses (403) before any AI provider is called. */
    suspend fun askResearch(symbol: String, question: org.example.stocksteps.learning.ResearchQuestion): org.example.stocksteps.learning.ResearchAnswer {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return send(HttpMethod.Post, "research/${symbol.uppercase()}/ask", question)
    }
    suspend fun saveScreen(request: org.example.stocksteps.screener.SaveScreenRequest): org.example.stocksteps.screener.SavedScreensResponse = send(HttpMethod.Post, "screens", request)
    suspend fun updateScreen(id: String, request: org.example.stocksteps.screener.UpdateScreenRequest): org.example.stocksteps.screener.SavedScreensResponse = send(HttpMethod.Put, "screens/${id.segment()}", request)
    suspend fun deleteScreen(id: String): org.example.stocksteps.screener.SavedScreensResponse = send(HttpMethod.Delete, "screens/${id.segment()}")
    /** MOCK backend only (the route doesn't exist in REAL). */
    suspend fun simulateEntitlements(request: org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest): org.example.stocksteps.portfolio.analytics.Entitlements =
        send(HttpMethod.Put, "entitlements/debug", request)

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

    /** [expectedOwner]: the user the caller is acting for; the request is never sent under another identity. */
    private suspend inline fun <reified T> send(method: HttpMethod, path: String, body: Any? = null, expectedOwner: String? = null): T {
        val owner = identity()
        if (expectedOwner != null && owner != expectedOwner) throw CancellationException("Account changed")
        val endpoint = baseUrl()
        var response = request(method, path, body, refresh = false, owner, endpoint)
        if (response.status == HttpStatusCode.Unauthorized) response = request(method, path, body, refresh = true, owner, endpoint)
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

    private suspend fun request(method: HttpMethod, path: String, body: Any?, refresh: Boolean, owner: String?, endpoint: String): HttpResponse {
        if (identity() != owner || baseUrl() != endpoint) throw CancellationException("Account or environment changed")
        val requestUrl = "${endpoint.trimEnd('/')}/api/v1/me/$path"
        val bearer = token(refresh) ?: throw StockStepsApiException(401, ApiError("SIGN_IN_REQUIRED", "Sign in to use watchlists and alerts."))
        if (identity() != owner || baseUrl() != endpoint) throw CancellationException("Account changed before request")
        return client.request(requestUrl) {
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
