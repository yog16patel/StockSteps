package org.example.stocksteps.brief

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.userdata.PushSender
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.requireUser

/**
 * Public brief content (no account needed; never contains user data) and signed-in overlays,
 * history, preferences and StockSteps+ AI. The owner always comes from the verified token.
 */
fun Route.dailyBriefRoutes(service: DailyBriefService, auth: UserAuthenticator, limiter: RequestRateLimiter, push: PushSender, schedulerSecret: String?, mock: Boolean) {
    suspend fun RoutingContext.guarded(block: suspend () -> Any) {
        if (!limiter.allow(call.request.origin.remoteHost)) {
            call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return
        }
        try { call.respond(block()) } catch (cause: BriefRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        } catch (cause: io.ktor.server.plugins.BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
        }
    }
    suspend fun RoutingContext.signedIn(block: suspend (String) -> Any) {
        val uid = call.requireUser(auth) ?: return
        guarded { block(uid) }
    }
    val scenario: RoutingContext.() -> String? = { call.request.queryParameters["scenario"] }

    route("/api/v1/daily-brief") {
        get("/latest") { guarded { service.latest(scenario()) } }
        get("/history") { guarded { service.history(BriefAccess.ANONYMOUS) } }
        get("/{id}") { guarded { service.byId(call.parameters["id"].orEmpty(), BriefAccess.ANONYMOUS) } }
        get("/{id}/sources") { guarded { service.byId(call.parameters["id"].orEmpty(), BriefAccess.ANONYMOUS).sources } }
    }
    route("/api/v1/me/daily-brief") {
        get("/history") { signedIn { uid -> service.history(service.access(uid)) } }
        get("/preferences") { signedIn { uid -> service.preferences(uid) } }
        put("/preferences") { signedIn { uid -> service.savePreferences(uid, call.receive<BriefPreferences>()) } }
        get("/{id}") { signedIn { uid -> service.byId(call.parameters["id"].orEmpty(), service.access(uid)) } }
        get("/{id}/personalized") { signedIn { uid -> service.personalized(uid, call.parameters["id"].orEmpty(), scenario()) } }
        post("/{id}/ai/explain") { signedIn { uid -> service.ai(uid, call.parameters["id"].orEmpty(), call.receive<BriefAiRequest>().copy(question = null), scenario()) } }
        post("/{id}/ai/ask") { signedIn { uid -> service.ai(uid, call.parameters["id"].orEmpty(), call.receive<BriefAiRequest>(), scenario()) } }
    }
    // Scheduler (Cloud Scheduler with a shared token in REAL; open in MOCK, where pushes are simulated).
    if (schedulerSecret != null || mock) post("/internal/daily-brief/dispatch") {
        if (!mock && call.request.headers["X-StockSteps-Scheduler-Token"] != schedulerSecret) {
            call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Not allowed.")); return@post
        }
        call.respond(service.dispatch(push))
    }
}
