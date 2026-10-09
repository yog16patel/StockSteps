package org.example.stocksteps.userdata

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import org.example.stocksteps.model.*
import org.example.stocksteps.service.UsMarketCalendar
import org.example.stocksteps.security.clientIdentity
import java.time.Clock
import java.time.Instant
import java.util.Locale

/** Quotes, names/logos and next earnings for watched symbols (public market data; no user data). */
class WatchDataService(
    private val market: WatchMarketData,
    private val rules: AlertRules,
    private val calendar: UsMarketCalendar,
    private val clock: Clock,
    private val notice: String,
    /** Phase 4A: symbols looked up at once per request (each costs up to a quote, a profile and an earnings history when cold). */
    private val maxConcurrent: Int = 8
) {
    suspend fun get(symbols: List<String>): WatchDataResponse = coroutineScope {
        val now = clock.instant()
        val permits = kotlinx.coroutines.sync.Semaphore(maxConcurrent)
        val rows = symbols.map { symbol ->
            async { permits.withPermit {
                val quote = market.quote(symbol)
                val profile = market.profile(symbol)
                val earnings = market.upcomingEarnings(symbol)?.takeIf { runCatching { java.time.LocalDate.parse(it.date) >= now.atZone(calendar.zone).toLocalDate() }.getOrDefault(false) }
                val price = quote?.price?.takeIf { it.isFinite() && it > 0 }
                val previous = quote?.previousClose?.takeIf { it.isFinite() && it > 0 }
                WatchQuote(
                    symbol = symbol,
                    name = profile?.companyName ?: quote?.companyName,
                    price = price,
                    change = if (price != null && previous != null) price - previous else quote?.change,
                    changePercent = if (price != null && previous != null) (price / previous - 1) * 100 else quote?.changePercent,
                    previousClose = previous,
                    currency = profile?.currency,
                    asOf = quote?.timestamp?.let { Instant.ofEpochSecond(it).toString() },
                    sessionDate = quote?.timestamp?.let { Instant.ofEpochSecond(it).atZone(calendar.zone).toLocalDate().toString() },
                    stale = price != null && rules.freshQuote(quote, now) == null,
                    logoUrl = profile?.logoUrl
                ) to earnings
            } }
        }.awaitAll()
        WatchDataResponse(rows.map { it.first }, rows.mapNotNull { it.second }, calendar.session(now), now.toString(), notice)
    }

    companion object {
        const val MAX_SYMBOLS = 100
        private val SYMBOL = Regex("[A-Z0-9][A-Z0-9.^-]{0,31}")
        fun parse(value: String?): List<String>? {
            val symbols = value?.split(',')?.map { it.trim().uppercase(Locale.ROOT) }?.filter { it.isNotEmpty() }?.distinct() ?: return null
            return symbols.takeIf { it.isNotEmpty() && it.size <= MAX_SYMBOLS && it.all(SYMBOL::matches) }
        }
    }
}

/**
 * Public market data for the Watchlist screen. Phase 4A: callers without a verified uid may send at most [anonymousMaxSymbols] distinct
 * symbols per request (the shared client splits larger watchlists into several requests); verified users keep [WatchDataService.MAX_SYMBOLS].
 */
fun Route.watchDataRoutes(service: WatchDataService, anonymousMaxSymbols: Int = WatchDataService.MAX_SYMBOLS) {
    get("/api/v1/stocks/watch-data") {
        val symbols = WatchDataService.parse(call.request.queryParameters["symbols"])
        if (symbols == null) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_SYMBOLS", "Send 1–${WatchDataService.MAX_SYMBOLS} comma-separated stock symbols."))
            return@get
        }
        val signedIn = call.clientIdentity() is org.example.stocksteps.security.ClientIdentity.User
        if (!signedIn && symbols.size > anonymousMaxSymbols) {
            call.respond(HttpStatusCode.BadRequest, ApiError("TOO_MANY_SYMBOLS", "Send at most $anonymousMaxSymbols symbols per request."))
            return@get
        }
        call.respond(service.get(symbols))
    }
}

/**
 * Signed-in user routes under `/api/v1/me`. The uid always comes from the verified token; a request
 * can only ever read or change the caller's own watchlists, notes, alerts and devices.
 */
fun Route.userRoutes(auth: UserAuthenticator, watchlists: WatchlistsService, alerts: AlertsService, store: UserDataStore, now: () -> Long = System::currentTimeMillis) {
    route("/api/v1/me") {
        get("/watchlists") { user(auth) { uid -> call.respond(watchlists.get(uid)) } }
        post("/watchlists") { user(auth) { uid -> call.respond(watchlists.create(uid, call.receive<CreateWatchlistRequest>().name)) } }
        put("/watchlists/order") { user(auth) { uid -> call.respond(watchlists.reorderWatchlists(uid, call.receive<ReorderRequest>().ids)) } }
        post("/watchlists/import") { user(auth) { uid -> call.respond(watchlists.import(uid, call.receive<ImportEntriesRequest>().instruments)) } }
        patch("/watchlists/{id}") { user(auth) { uid -> call.respond(watchlists.rename(uid, call.id("id"), call.receive<RenameWatchlistRequest>().name)) } }
        delete("/watchlists/{id}") { user(auth) { uid -> call.respond(watchlists.delete(uid, call.id("id"))) } }
        post("/watchlists/{id}/entries") { user(auth) { uid -> call.respond(watchlists.add(uid, call.id("id"), call.receive<AddEntryRequest>().instrument)) } }
        put("/watchlists/{id}/entries/order") { user(auth) { uid -> call.respond(watchlists.reorderEntries(uid, call.id("id"), call.receive<ReorderRequest>().ids)) } }
        patch("/watchlists/{id}/entries/{entryId}") { user(auth) { uid -> call.respond(watchlists.updateNote(uid, call.id("id"), call.id("entryId"), call.receive<UpdateEntryRequest>().note)) } }
        delete("/watchlists/{id}/entries/{entryId}") { user(auth) { uid -> call.respond(watchlists.remove(uid, call.id("id"), call.id("entryId"))) } }
        post("/watchlists/{id}/entries/{entryId}/move") {
            user(auth) { uid ->
                val request = call.receive<MoveEntryRequest>()
                call.respond(watchlists.move(uid, call.id("id"), call.id("entryId"), request.targetWatchlistId, request.copy))
            }
        }

        get("/alerts") { user(auth) { uid -> call.respond(alerts.list(uid)) } }
        post("/alerts") { user(auth) { uid -> call.respond(alerts.create(uid, call.receive<CreateAlertRequest>())) } }
        patch("/alerts/{id}") { user(auth) { uid -> call.respond(alerts.update(uid, call.id("id"), call.receive<UpdateAlertRequest>())) } }
        delete("/alerts/{id}") { user(auth) { uid -> call.respond(alerts.delete(uid, call.id("id"))) } }

        post("/devices") {
            user(auth) { uid ->
                val request = call.receive<RegisterDeviceRequest>()
                val deviceId = request.deviceId.trim()
                val token = request.token.trim()
                val platform = request.platform.lowercase(Locale.ROOT)
                if (!ID.matches(deviceId) || token.isEmpty() || token.length > 4_096 || platform !in setOf("android", "ios")) {
                    throw UserDataException(400, "INVALID_DEVICE", "Invalid device registration.")
                }
                store.registerDevice(DeviceRecord(uid, deviceId, token, platform, now()))
                call.respond(HttpStatusCode.NoContent)
            }
        }
        delete("/devices/{deviceId}") {
            user(auth) { uid ->
                store.unregisterDevice(uid, call.id("deviceId"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

/**
 * `POST /internal/alerts/evaluate`: one evaluation pass. REAL requires a Cloud Scheduler OIDC token or this job's own secret
 * (`ALERTS_EVALUATOR_TOKEN`) in `X-StockSteps-Scheduler-Token`, compared in constant time; the route is absent when neither is
 * configured. MOCK accepts local calls without a secret.
 */
fun Route.alertEvaluationRoutes(evaluator: AlertEvaluator, secret: String?, mock: Boolean) {
    if (!org.example.stocksteps.security.InternalCallers.available(secret) && !mock) return
    post("/internal/alerts/evaluate") {
        if (!mock && !org.example.stocksteps.security.InternalCallers.authorized(call, secret)) {
            call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Not allowed."))
            return@post
        }
        try {
            call.respond(kotlinx.coroutines.withContext(org.example.stocksteps.service.ProviderPriorityElement(org.example.stocksteps.service.ProviderPriority.NORMAL)) { evaluator.run() })
        } catch (cause: UserDataException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
        }
    }
}

private val ID = Regex("[A-Za-z0-9_-]{1,64}")

internal suspend fun io.ktor.server.routing.RoutingContext.user(auth: UserAuthenticator, block: suspend (String) -> Unit) {
    val uid = call.requireUser(auth) ?: return
    try {
        block(uid)
    } catch (cause: UserDataException) {
        call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
    } catch (cause: io.ktor.server.plugins.BadRequestException) {
        call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
    }
}

private fun ApplicationCall.id(name: String): String =
    parameters[name]?.takeIf(ID::matches) ?: throw UserDataException(400, "INVALID_ID", "Invalid id.")
