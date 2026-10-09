package org.example.stocksteps.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.util.AttributeKey
import org.example.stocksteps.userdata.UserAuthenticator
import java.net.InetAddress

/**
 * Who a request is for admission purposes (Phase 4A). Never an authorization fact: StockSteps+ and account access still come only from
 * the verified Firebase uid in each route.
 */
sealed interface ClientIdentity {
    /** Limiter key; null when the client can't be told apart (see [Unverified]). */
    val key: String?

    /** A verified Firebase ID token's uid. */
    data class User(val uid: String) : ClientIdentity { override val key get() = "u:$uid" }

    /** A client IP taken from the trusted end of `X-Forwarded-For` (IPv6 grouped by /64 so address rotation within a subnet doesn't help). */
    data class Address(val address: String) : ClientIdentity { override val key get() = "ip:$address" }

    /**
     * No trustworthy identity: anonymous and no verified proxy configuration (`TRUSTED_PROXY_HOPS` unset), or a malformed header. Such
     * requests share a per-instance anonymous pool sized for aggregate traffic — never a small per-client bucket that would throttle every
     * guest together — and the provider budgets (Phase 4C) remain the backstop.
     */
    data object Unverified : ClientIdentity { override val key: String? = null }
}

/**
 * Resolves [ClientIdentity]:
 * 1. a `Bearer` Firebase ID token verified by [auth] → [ClientIdentity.User] (an invalid token is ignored here; the route still rejects it);
 * 2. otherwise, only when [trustedProxyHops] is configured, the client IP that the trusted proxies appended to `X-Forwarded-For`:
 *    the entry [trustedProxyHops] positions from the right (Cloud Run direct ingress appends the client IP as the last entry → 1; an external
 *    HTTPS load balancer appends `client, lb` → 2). Entries to the left are client-controlled and never used. The hop count must be verified
 *    on the deployed topology before it's set (docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md §5);
 * 3. otherwise [ClientIdentity.Unverified]. `remoteHost` (the proxy), device ids and User-Agent are never identities.
 */
class ClientIdentityResolver(private val auth: UserAuthenticator?, private val trustedProxyHops: Int?) {
    init { require(trustedProxyHops == null || trustedProxyHops in 1..5) { "TRUSTED_PROXY_HOPS must be 1–5" } }

    suspend fun resolve(call: ApplicationCall): ClientIdentity {
        call.attributes.getOrNull(KEY)?.let { return it }
        val token = call.request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        val uid = if (token.isNullOrEmpty() || token.length > 4_096) null else try { auth?.uid(token) } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            null
        }
        val identity = uid?.let { ClientIdentity.User(it) }
            ?: trustedProxyHops?.let { hops -> clientIp(call.request.headers.getAll("X-Forwarded-For").orEmpty(), hops) }?.let { ClientIdentity.Address(it) }
            ?: ClientIdentity.Unverified
        call.attributes.put(KEY, identity)
        return identity
    }

    companion object {
        val KEY = AttributeKey<ClientIdentity>("StockSteps.ClientIdentity")

        /** The trusted entry ([hops] from the right) of all `X-Forwarded-For` values, normalised; null when absent or not an IP literal. */
        fun clientIp(headerValues: List<String>, hops: Int): String? {
            val entries = headerValues.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
            if (entries.size < hops || entries.size > 32) return null
            return normalise(entries[entries.size - hops])
        }

        private val IPV4 = Regex("""(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}""")
        private val IPV6 = Regex("""[0-9A-Fa-f:.]{2,45}""")

        /** IP literals only (no host names, so no DNS lookups); IPv6 reduced to its /64 network. */
        fun normalise(raw: String): String? {
            val value = raw.removePrefix("[").substringBefore("]").let { if (IPV4.matches(it.substringBefore(':'))) it.substringBefore(':') else it }
            if (IPV4.matches(value)) return value
            if (':' !in value || !IPV6.matches(value)) return null
            val bytes = runCatching { InetAddress.getByName(value).address }.getOrNull()?.takeIf { it.size == 16 } ?: return null
            return (0 until 8 step 2).joinToString(":") { i -> "%x".format(((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)) } + "::/64"
        }
    }
}

/** The identity resolved for this call by the admission plugin (null before it ran, e.g. in route tests without the plugin). */
fun ApplicationCall.clientIdentity(): ClientIdentity? = attributes.getOrNull(ClientIdentityResolver.KEY)

/**
 * Key for route-level `RequestRateLimiter`s: uid or trusted IP; null for unverified anonymous callers, who are bounded by the admission
 * anonymous pool instead of sharing one small proxy-address bucket. Without the admission plugin (route tests) it keeps the legacy
 * `remoteHost` key.
 */
fun ApplicationCall.limiterKey(): String? = attributes.getOrNull(ClientIdentityResolver.KEY).let { identity ->
    if (identity == null) request.origin.remoteHost else identity.key
}
