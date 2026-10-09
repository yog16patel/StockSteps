package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.log
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(val status: String)

/**
 * Phase 5A Cloud Run probes. Both answer from process state only — no FMP/Finnhub/Gemini/Bank of Canada call, no Firestore read, no
 * Firebase request — so probes cost nothing and can't be used to spend provider budgets (they're outside every admission group).
 * - `GET /health/live`: the process is serving HTTP.
 * - `GET /health/ready`: the module finished wiring (configuration validated, routes installed) and the server isn't shutting down;
 *   503 `starting`/`stopping` otherwise. It does not claim downstream availability: providers and Firestore failures surface per feature
 *   (labelled unavailable/stale data, 503 `USER_DATA_UNAVAILABLE`) and in the usage summaries.
 * Responses carry no configuration, versions or data mode (`/api/v1/meta` already exposes the mode to the apps).
 */
fun Application.healthRoutes() {
    val state = java.util.concurrent.atomic.AtomicReference("starting")
    monitor.subscribe(ApplicationStarted) { state.set("ready") }
    monitor.subscribe(ApplicationStopPreparing) {
        // Ktor can raise the event twice (shutdown hook, then the returning main thread); log the transition once.
        if (state.getAndSet("stopping") != "stopping") log.info("Shutdown requested: readiness reports stopping; in-flight requests may finish.")
    }
    routing {
        get("/health/live") {
            call.response.header("Cache-Control", "no-store")
            call.respond(HealthStatus("alive"))
        }
        get("/health/ready") {
            call.response.header("Cache-Control", "no-store")
            val current = state.get()
            call.respond(if (current == "ready") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, HealthStatus(current))
        }
    }
}
