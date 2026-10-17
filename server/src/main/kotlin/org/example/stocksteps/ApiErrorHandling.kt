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
            // Phase 9A: provider quota and plan restrictions are security-relevant (abuse can exhaust them); the log names the code only.
            if (cause.dailyLimitReached || cause.restricted || cause.failure == StockProviderException.Failure.RATE_LIMITED)
                org.example.stocksteps.security.SecurityLog.event(call, org.example.stocksteps.security.SecurityEvent.UPSTREAM_RESTRICTED, error.code.lowercase())
            call.respond(HttpStatusCode.fromValue(status), error)
        }
        // Phase 9A: unreadable input is a 4xx with a stable code, never a 500 (routes without their own mapping included).
        exception<io.ktor.server.plugins.UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("UNSUPPORTED_MEDIA_TYPE", "Send the request body as JSON."))
        }
        // A body in a type no converter reads (e.g. text/plain sent to a JSON route) used to surface as a 500.
        exception<io.ktor.server.plugins.CannotTransformContentToTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("UNSUPPORTED_MEDIA_TYPE", "Send the request body as JSON."))
        }
        exception<io.ktor.server.plugins.PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError("PAYLOAD_TOO_LARGE", "The request is too large."))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
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
