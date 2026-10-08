package org.example.stocksteps.screener

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.user

/**
 * Public (no sign-in) screening and comparison. Every provider request stays on the server;
 * apps never call FMP or Finnhub. Rate-limited per client address.
 */
fun Route.screenerRoutes(service: ScreenerService, limiter: RequestRateLimiter) {
    suspend fun RoutingContext.guarded(block: suspend () -> Any) {
        if (!limiter.allow(call.request.origin.remoteHost)) {
            call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again."))
            return
        }
        try { call.respond(block()) } catch (cause: ScreenerRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        } catch (cause: io.ktor.server.plugins.BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_SCREEN", "Invalid screener request."))
        }
    }
    get("/api/v1/screener/catalog") { guarded { service.catalog() } }
    post("/api/v1/screener/search") { guarded { service.search(call.receive<ScreenerQuery>()) } }
    get("/api/v1/compare") { guarded { service.compare(call.request.queryParameters["symbols"]) } }
    get("/api/v1/compare/performance") { guarded { service.performance(call.request.queryParameters["symbols"], call.request.queryParameters["period"]) } }
}

/** Signed-in saved screens; the owner comes only from the verified token. */
fun Route.savedScreenRoutes(auth: UserAuthenticator, screens: SavedScreensService) {
    route("/api/v1/me/screens") {
        get { user(auth) { uid -> call.respond(screens.list(uid)) } }
        post { user(auth) { uid -> call.respond(screens.create(uid, call.receive<SaveScreenRequest>())) } }
        put("/{id}") { user(auth) { uid -> val body = call.receive<UpdateScreenRequest>(); call.respond(screens.update(uid, call.parameters["id"].orEmpty(), body.name, body.query)) } }
        delete("/{id}") { user(auth) { uid -> call.respond(screens.delete(uid, call.parameters["id"].orEmpty())) } }
    }
}
