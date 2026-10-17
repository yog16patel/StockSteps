package org.example.stocksteps.marketdata

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.example.stocksteps.marketdata.alphavantage.AlphaVantageConfig
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.security.ClientIdentity
import org.example.stocksteps.security.ClientIdentityResolver
import org.example.stocksteps.service.ProviderErrors
import org.example.stocksteps.service.ProviderUsageMeter

/**
 * Phase 9B.7: licence enforcement **at the API-serving boundary** for routes that return third-party market data. Non-market-data features
 * (meta, Learn, entitlements, alerts and watchlist management, Practice entitlement/reset, devices, health) are never affected.
 *
 * Active only when it has something to enforce:
 * - **commercial licence mode** (`STOCKSTEPS_LICENSE_MODE=commercial`, or Alpha Vantage primary with `ALPHA_VANTAGE_LICENSE_MODE=commercial`):
 *   a market-data route is served only if every active market-data provider is cleared for display on the channel in [registry]
 *   (`VERIFIED_ALLOWED`); today none is, so market data fails closed;
 * - **Alpha Vantage personal mode** (Alpha Vantage primary, the default licence mode): a personal licence covers the key holder only, so the
 *   data goes only to callers on explicit allowlists — a verified Firebase uid in `ALPHA_VANTAGE_PERSONAL_UIDS` or a client address in
 *   `ALPHA_VANTAGE_PERSONAL_CLIENTS` (only `loopback`, matched on the socket peer / trusted address; Phase 9B.9A).
 *   An environment label alone never opens it; empty lists serve nobody.
 * Refusals are 502 `PROVIDER_RESTRICTED` (the existing "trying again won't help" contract), `Cache-Control: no-store`, no provider call made.
 */
class MarketDataAccessGate(
    private val activeProviders: Set<String>,
    private val commercial: Boolean,
    private val alphaVantage: AlphaVantageConfig?,
    private val registry: LicenseRegistry = LicenseRegistry.DEFAULT,
    private val trustedProxyHops: Int? = null,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared,
    private val channel: DisplayChannel = DisplayChannel.MOBILE_APP
) {
    /** Whether the gate has anything to enforce (else it isn't installed). */
    val active: Boolean get() = commercial || personalAlphaVantage
    /** Phase 9B.8: personal mode applies whenever Alpha Vantage serves any dataset (prices or fundamentals). */
    private val personalAlphaVantage get() = alphaVantage != null && "alphavantage" in activeProviders && alphaVantage.licenseMode == AlphaVantageConfig.LicenseMode.PERSONAL

    fun recordRefusal(d: Decision) = meter.event("license.gate.${d.name.lowercase()}")

    enum class Decision { ALLOW, NOT_MARKET_DATA, REFUSED_COMMERCIAL, REFUSED_PERSONAL }

    fun decide(path: String, uid: String?, clientAddress: String?): Decision {
        if (!servesMarketData(path)) return Decision.NOT_MARKET_DATA
        if (commercial && activeProviders.any { !registry.displayAllowed(it, Dataset.DAILY_PRICES, channel) }) return Decision.REFUSED_COMMERCIAL
        if (personalAlphaVantage) {
            val cfg = alphaVantage!!
            val byUid = uid != null && uid in cfg.personalUids
            val byAddress = clientAddress != null && cfg.personalClients.any { allowed -> matches(allowed, clientAddress) }
            if (!byUid && !byAddress) return Decision.REFUSED_PERSONAL
        }
        return Decision.ALLOW
    }

    companion object {
        /** Route prefixes that return third-party market data (prices, fundamentals, news, earnings, movers, valuations). */
        val MARKET_DATA_PREFIXES = listOf(
            "/api/v1/markets", "/api/v1/market/", "/api/v1/stocks/", "/api/v1/screener", "/api/v1/compare", "/api/v1/earnings", "/api/v1/news",
            "/api/v1/daily-brief", "/api/v1/home/personas", "/api/v1/me/portfolio", "/api/v1/me/practice", "/api/v1/me/compare",
            "/api/v1/me/comparison-research", "/api/v1/me/earnings", "/api/v1/me/daily-brief", "/api/v1/me/research"
        )
        /** Under those prefixes, routes that serve only the user's own settings or status. */
        val EXEMPT = listOf(
            "/api/v1/me/practice/entitlement", "/api/v1/me/practice/trial", "/api/v1/me/practice/notices", "/api/v1/me/practice/reset",
            "/api/v1/me/practice/debug", "/api/v1/me/earnings/reminders/preferences", "/api/v1/me/earnings/digest/preferences",
            "/api/v1/me/daily-brief/preferences", "/api/v1/me/compare/ai/usage", "/api/v1/me/earnings/ai/usage"
        )
        const val LICENSE_MESSAGE = "This market data isn't licensed for display to you on this server. Trying again won't help."
        fun servesMarketData(path: String): Boolean = MARKET_DATA_PREFIXES.any { path.startsWith(it) } && EXEMPT.none { path.startsWith(it) }

        /**
         * Phase 9B.9A: only `loopback` matches (the owner's own machine: adb reverse, the iOS simulator, local tools). Any other entry
         * matches nothing — a LAN or public address can stand for many people behind NAT (startup rejects such entries).
         */
        fun matches(allowed: String, address: String): Boolean {
            val a = address.removePrefix("/").substringBefore('%')
            return allowed.equals("loopback", ignoreCase = true) && (a == "127.0.0.1" || a == "::1" || a == "0:0:0:0:0:0:0:1" || a == "localhost")
        }
    }
}

/** Installs [gate] after authentication (so the principal of `/me` requests is known). Not installed when it has nothing to enforce. */
fun Application.installMarketDataAccessGate(gate: MarketDataAccessGate, identities: ClientIdentityResolver, trustedProxyHops: Int?) {
    if (!gate.active) return
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (!MarketDataAccessGate.servesMarketData(path)) return@intercept
        val identity = identities.resolve(call)
        val uid = (identity as? ClientIdentity.User)?.uid
        val address = if (trustedProxyHops != null) (identity as? ClientIdentity.Address)?.address
            ?: ClientIdentityResolver.clientIp(call.request.headers.getAll("X-Forwarded-For").orEmpty(), trustedProxyHops)
            else call.request.local.remoteAddress
        val decision = gate.decide(path, uid, address)
        if (decision == MarketDataAccessGate.Decision.REFUSED_COMMERCIAL || decision == MarketDataAccessGate.Decision.REFUSED_PERSONAL) {
            gate.recordRefusal(decision)
            call.response.header(HttpHeaders.CacheControl, "no-store, private")
            call.respond(HttpStatusCode.BadGateway, ApiError(ProviderErrors.RESTRICTED, MarketDataAccessGate.LICENSE_MESSAGE))
            finish()
        }
    }
}
