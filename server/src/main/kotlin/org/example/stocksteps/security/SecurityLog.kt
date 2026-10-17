package org.example.stocksteps.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.example.stocksteps.service.ProviderUsageMeter
import java.security.MessageDigest
import java.security.SecureRandom

/** Security events (Phase 9A). Names are fixed strings, safe for logs-based metrics. */
enum class SecurityEvent(val label: String) {
    AUTH_REJECTED("auth_rejected"), AUTH_UNAVAILABLE("auth_unavailable"), AUTHZ_DENIED("authz_denied"), RATE_LIMITED("rate_limited"),
    EXPENSIVE_BLOCKED("expensive_blocked"), INVALID_INPUT("invalid_input"), UPSTREAM_RESTRICTED("upstream_restricted")
}

/**
 * Structured, secret-free security log (Phase 9A), logger `StockSteps.Security`, one line per event:
 * `security event=rate_limited reason=identity class=PUBLIC_MARKET_DATA method=GET route=/api/v1/stocks/{symbol}/details identity=u:3f9a…`.
 *
 * - **Never logged:** tokens, `Authorization` / App Check / scheduler headers, query strings, bodies, raw paths (only the route
 *   template from [RouteAccess]), uids or IP addresses. The identity is its kind plus a 12-hex-digit SHA-256 of a per-process random salt
 *   and the limiter key, so one instance's lines can be correlated without revealing who the caller is (and not across instances).
 * - **Trace:** the Cloud Run trace id from `X-Cloud-Trace-Context` when present (hex only), so lines join the request log.
 * - **Volume:** every event is counted in [ProviderUsageMeter] (`security.<event>.<reason>`, included in the per-minute usage summary); a line
 *   is written at most once per minute per (event, reason, identity), so a flood can't flood the logs. Bounded state.
 */
object SecurityLog {
    private val logger = org.slf4j.LoggerFactory.getLogger("StockSteps.Security")
    private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val lastLogged = LinkedHashMap<String, Long>(64, 0.75f, true)
    private const val MAX_KEYS = 10_000
    private val TRACE = Regex("[0-9a-fA-F]{8,64}")
    @Volatile var meter: ProviderUsageMeter = ProviderUsageMeter.shared
    /** Tests: captures formatted lines instead of (in addition to) the logger. */
    @Volatile internal var sink: ((String) -> Unit)? = null
    @Volatile internal var now: () -> Long = System::currentTimeMillis

    /** A short, salted digest of a limiter key ("u:…" / "ip:…"), or "none". */
    fun digest(key: String?): String {
        if (key == null) return "none"
        val kind = key.substringBefore(':', "id")
        val hash = MessageDigest.getInstance("SHA-256").apply { update(salt); update(key.toByteArray()) }.digest()
        return kind + ":" + hash.take(6).joinToString("") { "%02x".format(it) }
    }

    fun event(call: ApplicationCall, event: SecurityEvent, reason: String, rateClass: RateLimitClass? = null) {
        val rule = RouteAccess.of(call.request.httpMethod, call.request.path())
        val identity = digest(call.clientIdentity()?.key ?: call.principal()?.let { ClientIdentity.User(it.uid).key })
        val trace = call.request.headers["X-Cloud-Trace-Context"]?.substringBefore('/')?.takeIf(TRACE::matches)
        record(event, reason, call.request.httpMethod.value, rule?.pattern ?: "unmatched", (rateClass ?: rule?.limit)?.name, identity, trace)
    }

    internal fun record(event: SecurityEvent, reason: String, method: String, route: String, rateClass: String?, identity: String, trace: String? = null) {
        val safeReason = reason.filter { it.isLetterOrDigit() || it in "-_." }.take(40)
        meter.event("security.${event.label}.$safeReason")
        val key = "${event.label}|$safeReason|$identity"
        val t = now()
        val write = synchronized(lastLogged) {
            val last = lastLogged[key]
            if (last != null && t - last < 60_000) false else {
                lastLogged[key] = t
                while (lastLogged.size > MAX_KEYS) lastLogged.remove(lastLogged.keys.first())
                true
            }
        }
        if (!write) return
        val line = buildString {
            append("security event=").append(event.label).append(" reason=").append(safeReason)
            rateClass?.let { append(" class=").append(it) }
            append(" method=").append(method.filter(Char::isLetter).take(10)).append(" route=").append(route)
            append(" identity=").append(identity)
            trace?.let { append(" trace=").append(it) }
        }
        sink?.invoke(line)
        if (event == SecurityEvent.AUTH_UNAVAILABLE || event == SecurityEvent.UPSTREAM_RESTRICTED) logger.warn(line) else logger.info(line)
    }

    /** Tests: forget throttling state. */
    internal fun reset() = synchronized(lastLogged) { lastLogged.clear() }
    internal fun trackedKeys(): Int = synchronized(lastLogged) { lastLogged.size }
}
