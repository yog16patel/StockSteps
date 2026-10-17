package org.example.stocksteps.marketdata

import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.appconfig.StartupConfiguration
import org.example.stocksteps.service.ProviderBudgetConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 9B settings: validated at startup, names only in messages; SEC's published ceiling can't be exceeded by configuration. */
class MarketDataConfigurationTest {
    private fun errors(vararg kv: Pair<String, String>) = StartupConfiguration.check(DataMode.REAL) { k -> (mapOf("FMP_API_KEY" to "test", "FINNHUB_API_KEY" to "test") + kv.toMap())[k] }.errors

    @Test fun newSettingsAreValidated() {
        assertTrue(errors("SCREENER_ENRICHMENT_PER_DAY" to "-1").any { "SCREENER_ENRICHMENT_PER_DAY" in it })
        assertTrue(errors("SCREENER_ENRICHMENT_PER_DAY" to "0").none { "SCREENER_ENRICHMENT_PER_DAY" in it })
        assertTrue(errors("SEC_USER_AGENT" to "StockSteps").any { "SEC_USER_AGENT" in it })
        assertTrue(errors("SEC_USER_AGENT" to "StockSteps ops@example.com").none { "SEC_USER_AGENT" in it })
        assertTrue(errors("STOCKSTEPS_LICENSE_MODE" to "lenient").any { "STOCKSTEPS_LICENSE_MODE" in it })
        assertTrue(errors("PROVIDER_SEC_PER_MINUTE" to "601").any { "PROVIDER_SEC_PER_MINUTE" in it })
        assertTrue(errors("PROVIDER_SEC_PER_MINUTE" to "120").none { "PROVIDER_SEC_PER_MINUTE" in it })
        // Messages never echo the value.
        assertTrue(errors("SEC_USER_AGENT" to "secret-value").none { "secret-value" in it })
    }

    @Test fun secLimitsArePublishedSoTheyDontBlockVerification() {
        val env = mapOf("CLOUD_RUN_MAX_INSTANCES" to "1") + listOf("FMP", "FINNHUB", "GEMINI", "BOC").associate { "PROVIDER_${it}_PER_MINUTE" to "60" }
        assertTrue(ProviderBudgetConfig.fromEnvironment { env[it] }.verified)
        assertEquals(300, ProviderBudgetConfig.fromEnvironment { env[it] }.limits.getValue("sec").perMinute)
    }
}
