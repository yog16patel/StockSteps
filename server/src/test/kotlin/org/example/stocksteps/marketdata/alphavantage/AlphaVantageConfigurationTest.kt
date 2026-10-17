package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.appconfig.StartupConfiguration
import org.example.stocksteps.service.ProviderBudgetConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.7 settings: off unless enabled with a key, validated at startup without echoing values, never active in MOCK. */
class AlphaVantageConfigurationTest {
    private val base = mapOf("FMP_API_KEY" to "t", "FINNHUB_API_KEY" to "t")
    private fun report(vararg kv: Pair<String, String>) = StartupConfiguration.check(DataMode.REAL) { k -> (base + kv.toMap())[k] }

    @Test fun offUnlessEnabledWithAKey() {
        assertNull(AlphaVantageConfig.fromEnvironment { null })
        assertNull(AlphaVantageConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_API_KEY" to "secret")[it] }, "a key alone doesn't enable it")
        assertNull(AlphaVantageConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to "  ")[it] })
        val c = AlphaVantageConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to "secret-value",
            "MARKET_DATA_PRIMARY_PROVIDER" to "alpha_vantage", "ALPHA_VANTAGE_PERSONAL_CLIENTS" to "loopback, 192.168.1.20")[it] }!!
        assertTrue(c.primary); assertEquals(AlphaVantageConfig.LicenseMode.PERSONAL, c.licenseMode, "personal by default")
        assertEquals(setOf("loopback", "192.168.1.20"), c.personalClients); assertTrue(!c.premium)
        assertTrue("secret-value" !in c.toString())
    }

    @Test fun startupValidationNamesSettingsNeverValues() {
        assertTrue(report("MARKET_DATA_PRIMARY_PROVIDER" to "alpha_vantage").errors.any { "requires ALPHA_VANTAGE_ENABLED=true and ALPHA_VANTAGE_API_KEY" in it })
        assertTrue(report("MARKET_DATA_PRIMARY_PROVIDER" to "yahoo").errors.any { "MARKET_DATA_PRIMARY_PROVIDER" in it })
        assertTrue(report("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "-5").errors.any { "ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" in it })
        assertTrue(report("ALPHA_VANTAGE_LICENSE_MODE" to "beta").errors.any { "ALPHA_VANTAGE_LICENSE_MODE" in it })
        val ok = report("MARKET_DATA_PRIMARY_PROVIDER" to "alpha_vantage", "ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to "secret-value")
        assertTrue(ok.errors.none { "ALPHA_VANTAGE" in it || "MARKET_DATA" in it }, "${ok.errors}")
        assertTrue(ok.warnings.any { "refuse every caller" in it } && ok.warnings.any { "free entitlement" in it })
        assertTrue((ok.errors + ok.warnings).none { "secret-value" in it })
        assertTrue(report("FUNDAMENTALS_PRIMARY_PROVIDER" to "sec").errors.any { "FUNDAMENTALS_PRIMARY_PROVIDER must be fmp or alpha_vantage" in it })
        assertTrue(report("FUNDAMENTALS_PRIMARY_PROVIDER" to "alpha_vantage").errors.any { "FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage requires" in it })
        assertTrue(report("FUNDAMENTALS_PRIMARY_PROVIDER" to "alpha_vantage", "ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to "x").errors.isEmpty())
        val cloud = report("K_SERVICE" to "api", "MARKET_DATA_PRIMARY_PROVIDER" to "alpha_vantage", "ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to "x")
        assertTrue(cloud.errors.any { "can't be served from Cloud Run" in it })
    }

    @Test fun theAlphaVantageLaneCountsTowardVerificationOnlyWhenEnabled() {
        val env = mapOf("CLOUD_RUN_MAX_INSTANCES" to "1") + listOf("FMP", "FINNHUB", "GEMINI", "BOC").associate { "PROVIDER_${it}_PER_MINUTE" to "60" }
        assertTrue(ProviderBudgetConfig.fromEnvironment { env[it] }.verified)
        // Free: the published allowance is the configuration. Premium: the plan rate must be set.
        assertTrue(ProviderBudgetConfig.fromEnvironment { (env + ("ALPHA_VANTAGE_ENABLED" to "true"))[it] }.verified)
        assertTrue(!ProviderBudgetConfig.fromEnvironment { (env + ("ALPHA_VANTAGE_ENABLED" to "true") + ("ALPHA_VANTAGE_ENTITLEMENT" to "premium"))[it] }.verified)
        assertTrue(ProviderBudgetConfig.fromEnvironment { (env + ("ALPHA_VANTAGE_ENABLED" to "true") + ("ALPHA_VANTAGE_ENTITLEMENT" to "premium") + ("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75"))[it] }.verified)
    }

    @Test fun mockNeverUsesAlphaVantageOrTheGate() {
        val sources = org.example.stocksteps.mockDataSources()
        assertNull(sources.alphaVantage); assertTrue(sources.marketDataProviders.isEmpty()); assertTrue(sources.closesSplitAdjusted)
    }
}
