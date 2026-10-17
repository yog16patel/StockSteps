package org.example.stocksteps.marketdata

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.example.stocksteps.marketdata.alphavantage.AlphaVantageConfig
import org.example.stocksteps.security.ClientIdentityResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 9B.7: licence enforcement at the serving boundary (decisions and a real Ktor pipeline). */
class MarketDataAccessGateTest {
    private fun av(mode: AlphaVantageConfig.LicenseMode = AlphaVantageConfig.LicenseMode.PERSONAL, uids: Set<String> = emptySet(), clients: Set<String> = emptySet(), primary: Boolean = true) =
        AlphaVantageConfig("k", primary, 75, mode, uids, clients)

    @Test fun marketDataRoutesAreClassifiedAndPersonalSettingsAreExempt() {
        for (p in listOf("/api/v1/markets/overview", "/api/v1/stocks/AAPL/chart", "/api/v1/screener/catalog", "/api/v1/me/portfolio/accounts/a/history",
                "/api/v1/me/practice/orders/execute", "/api/v1/daily-brief/latest", "/api/v1/stocks/search", "/api/v1/compare"))
            assertTrue(MarketDataAccessGate.servesMarketData(p), p)
        for (p in listOf("/api/v1/meta", "/health/live", "/api/v1/me/learning", "/api/v1/me/entitlements", "/api/v1/me/watchlists", "/api/v1/me/alerts",
                "/api/v1/me/practice/entitlement", "/api/v1/me/practice/reset", "/api/v1/me/daily-brief/preferences", "/api/v1/me/devices"))
            assertFalse(MarketDataAccessGate.servesMarketData(p), p)
    }

    @Test fun personalModeServesOnlyExplicitAllowlists() {
        val gate = MarketDataAccessGate(setOf("alphavantage", "fmp"), commercial = false, alphaVantage = av(uids = setOf("owner-uid"), clients = setOf("loopback", "192.168.1.20")))
        // Phase 9B.9A: a LAN address never identifies one person (NAT): only loopback matches.
        val p = "/api/v1/stocks/AAPL/quote"
        assertEquals(MarketDataAccessGate.Decision.ALLOW, gate.decide(p, "owner-uid", "10.0.0.9"))
        assertEquals(MarketDataAccessGate.Decision.ALLOW, gate.decide(p, null, "127.0.0.1"))
        assertEquals(MarketDataAccessGate.Decision.REFUSED_PERSONAL, gate.decide(p, null, "192.168.1.20"))
        assertEquals(MarketDataAccessGate.Decision.REFUSED_PERSONAL, gate.decide(p, "someone-else", "192.168.1.21"))
        assertEquals(MarketDataAccessGate.Decision.REFUSED_PERSONAL, gate.decide(p, null, null))
        assertEquals(MarketDataAccessGate.Decision.NOT_MARKET_DATA, gate.decide("/api/v1/me/learning", "someone-else", null))
        val nobody = MarketDataAccessGate(setOf("alphavantage"), commercial = false, alphaVantage = av())
        assertEquals(MarketDataAccessGate.Decision.REFUSED_PERSONAL, nobody.decide(p, "owner-uid", "127.0.0.1"), "empty lists serve nobody")
    }

    @Test fun commercialModeFailsClosedUntilRightsAreRecorded() {
        val gate = MarketDataAccessGate(setOf("alphavantage", "fmp", "finnhub"), commercial = true, alphaVantage = av(AlphaVantageConfig.LicenseMode.COMMERCIAL, uids = setOf("owner-uid")))
        assertEquals(MarketDataAccessGate.Decision.REFUSED_COMMERCIAL, gate.decide("/api/v1/stocks/AAPL/quote", "owner-uid", "127.0.0.1"))
        assertEquals(MarketDataAccessGate.Decision.NOT_MARKET_DATA, gate.decide("/api/v1/meta", null, null))
        val all = Dataset.entries.fold(LicenseRegistry.DEFAULT) { r, d ->
            listOf("alphavantage", "fmp", "finnhub").fold(r) { rr, p -> rr.with(DataRights(p, d, DataClass.LICENSED_COMMERCIAL, LicenseStatus.VERIFIED_ALLOWED,
                LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.NOT_ALLOWED, setOf(DisplayChannel.MOBILE_APP), evidence = "test agreement")) }
        }
        val licensed = MarketDataAccessGate(setOf("alphavantage", "fmp", "finnhub"), commercial = true, alphaVantage = av(AlphaVantageConfig.LicenseMode.COMMERCIAL), registry = all)
        assertEquals(MarketDataAccessGate.Decision.ALLOW, licensed.decide("/api/v1/stocks/AAPL/quote", null, null))
        assertFalse(MarketDataAccessGate(setOf("fmp"), commercial = false, alphaVantage = null).active, "default FMP development: nothing to enforce")
        assertFalse(MarketDataAccessGate(setOf("fmp"), commercial = false, alphaVantage = av(primary = false)).active)
        // Phase 9B.8: Alpha Vantage serving fundamentals only (prices from FMP) is still personal-licence data.
        val fundamentalsOnly = MarketDataAccessGate(setOf("alphavantage", "fmp"), commercial = false, alphaVantage = av(primary = false))
        assertTrue(fundamentalsOnly.active)
        assertEquals(MarketDataAccessGate.Decision.REFUSED_PERSONAL, fundamentalsOnly.decide("/api/v1/stocks/AAPL/fundamentals", null, "10.0.0.2"))
    }

    @Test fun theGateRefusesAtTheBoundaryBeforeAnyRouteRuns() = testApplication {
        var reached = 0
        application {
            install(ContentNegotiation) { json() }
            val gate = MarketDataAccessGate(setOf("alphavantage"), commercial = false, alphaVantage = av(clients = emptySet()))
            installMarketDataAccessGate(gate, ClientIdentityResolver(null, null), null)
            routing {
                get("/api/v1/stocks/{s}/quote") { reached++; call.respondText("price") }
                get("/api/v1/meta") { call.respondText("meta") }
            }
        }
        val refused = client.get("/api/v1/stocks/AAPL/quote")
        assertEquals(HttpStatusCode.BadGateway, refused.status)
        assertTrue("PROVIDER_RESTRICTED" in refused.bodyAsText() && "licensed" in refused.bodyAsText())
        assertEquals("no-store, private", refused.headers["Cache-Control"])
        assertEquals(0, reached, "no route ran, no provider call")
        assertEquals("meta", client.get("/api/v1/meta").bodyAsText(), "non-market-data routes unaffected")
    }

    @Test fun theLoopbackAllowlistLetsTheOwnersLocalClientThrough() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installMarketDataAccessGate(MarketDataAccessGate(setOf("alphavantage"), commercial = false, alphaVantage = av(clients = setOf("loopback"))),
                ClientIdentityResolver(null, null), null)
            routing { get("/api/v1/stocks/{s}/quote") { call.respondText("price") } }
        }
        assertEquals("price", client.get("/api/v1/stocks/AAPL/quote").bodyAsText())
    }
}
