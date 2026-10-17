package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.repository.StockProviderException

fun Application.configureApiErrors() {
    install(StatusPages) {
        exception<StockProviderException> { call, cause ->
            // Phase 7A: plan restrictions and the daily allowance get their own codes (statuses unchanged): ProviderErrors.
            val (status, error) = org.example.stocksteps.service.ProviderErrors.of(cause)
            call.respond(HttpStatusCode.fromValue(status), error)
        }
        // Safety net for routes without their own mapping (e.g. Daily Brief's signed-in overlay): user-data failures keep their
        // status and code (503 USER_DATA_UNAVAILABLE) instead of becoming 500. Their messages are written for users.
        exception<org.example.stocksteps.userdata.UserDataException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause
            // Exception messages and stack traces can contain upstream API keys.
            call.application.log.error("Unhandled request failure: ${cause.javaClass.simpleName}")
            call.respond(HttpStatusCode.InternalServerError,
                ApiError("INTERNAL_ERROR", "An unexpected error occurred."))
        }
    }
}
