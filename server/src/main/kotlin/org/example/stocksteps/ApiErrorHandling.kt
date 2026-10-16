package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure

fun Application.configureApiErrors() {
    install(StatusPages) {
        exception<StockProviderException> { call, cause ->
            val (status, error) = when (cause.failure) {
                Failure.TIMEOUT -> HttpStatusCode.GatewayTimeout to
                    ApiError("PROVIDER_TIMEOUT", "Market data took too long to respond.")
                Failure.RATE_LIMITED -> HttpStatusCode.ServiceUnavailable to
                    ApiError("PROVIDER_RATE_LIMITED", "Market data is temporarily unavailable. Try again later.")
                Failure.UNAVAILABLE -> HttpStatusCode.BadGateway to
                    ApiError("PROVIDER_UNAVAILABLE", "Market data is temporarily unavailable.")
                Failure.INVALID_RESPONSE -> HttpStatusCode.BadGateway to
                    ApiError("INVALID_PROVIDER_RESPONSE", "Market data could not be read.")
            }
            call.respond(status, error)
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
