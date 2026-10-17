package org.example.stocksteps.security

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

private val LOGGED = AttributeKey<Unit>("StockSteps.SecurityLogged")

/** Marks this call's security outcome as already logged (so the response hook doesn't log it twice). */
internal fun ApplicationCall.markSecurityLogged() = attributes.put(LOGGED, Unit)

/**
 * Outermost Phase 9A hook, installed before every other security plugin:
 * - response headers: `X-Content-Type-Options: nosniff` everywhere; `Cache-Control: no-store, private` on every signed-in and internal
 *   response, errors included (routes don't set their own). No CORS headers are ever sent: the API serves native apps only, so browsers
 *   keep their same-origin default (CORS is not authentication either way);
 * - after the response: [SecurityEvent.AUTHZ_DENIED] for 403s (plan, admin and internal-job denials; another user's resource can't be
 *   denied because lookups only search the caller's own documents, so a foreign id is simply unknown: 400/404) and [SecurityEvent.INVALID_INPUT] for rejected input (400/411/413/414/415/422),
 *   with the status as the reason — never the body or the request content.
 */
fun Application.installSecurityObservability() {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        if (requiresAccount(path) || path.startsWith("/internal/")) call.response.headers.append(HttpHeaders.CacheControl, "no-store, private")
        try { proceed() } catch (cause: Throwable) {
            // Input errors thrown by `receive` become 400/413/415 in StatusPages after this hook has unwound: log them here.
            val status = when (cause) {
                is io.ktor.server.plugins.PayloadTooLargeException -> 413
                is io.ktor.server.plugins.UnsupportedMediaTypeException, is io.ktor.server.plugins.CannotTransformContentToTypeException -> 415
                is io.ktor.server.plugins.BadRequestException -> 400
                else -> null
            }
            if (status != null) { SecurityLog.event(call, SecurityEvent.INVALID_INPUT, "status-$status"); call.markSecurityLogged() }
            throw cause
        } finally {
            val status = call.response.status()?.value
            if (status != null && call.attributes.getOrNull(LOGGED) == null) when (status) {
                403 -> SecurityLog.event(call, SecurityEvent.AUTHZ_DENIED, "forbidden")
                400, 411, 413, 414, 415, 422 -> SecurityLog.event(call, SecurityEvent.INVALID_INPUT, "status-$status")
            }
        }
    }
}
