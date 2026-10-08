package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.home.HomePersonaFixture
import org.example.stocksteps.model.ApiError

/** Registered only for DataMode.MOCK; read-only fixture data cannot alter users or send push. */
fun Route.homePersonaRoutes() {
    get("/api/v1/home/personas/{id}") {
        val id = call.parameters["id"]
        val allowed = setOf("new-user", "watchlist-only", "portfolio-only", "watchlist-and-portfolio", "learning-only",
            "upcoming-earnings", "triggered-alerts", "no-news", "stale-quotes", "partial-failures")
        if (id !in allowed) {
            call.respond(HttpStatusCode.NotFound, ApiError("PERSONA_NOT_FOUND", "Choose a supported sample persona."))
            return@get
        }
        val text = HomePersonaFixture::class.java.getResource("/fixtures/home/$id.json")!!.readText()
        call.respond(Json.decodeFromString<HomePersonaFixture>(text))
    }
}
