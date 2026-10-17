package org.example.stocksteps.marketdata.alphavantage

/**
 * Phase 9B.7: Alpha Vantage settings (server only; the key never leaves the server).
 *
 * - `ALPHA_VANTAGE_API_KEY` — the key; absent → the adapter is off (REAL keeps its other providers; never MOCK).
 * - `ALPHA_VANTAGE_ENABLED` — `true` to build the adapter at all (default false).
 * - `MARKET_DATA_PRIMARY_PROVIDER` — `alpha_vantage` to make it the primary source of the datasets it covers (default `fmp`).
 * - `ALPHA_VANTAGE_ENTITLEMENT` — `free` (default) or `premium`. Phase 9B.9A: the **only** switch for premium functions (daily `full`
 *   history, OVERVIEW, adjusted series, news…). A rate setting never implies premium.
 * - `ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE` — the subscribed plan's request rate (premium plans 75/150/300/600/1200 published); a rate
 *   limit only. On FREE the lane stays at the free rate whatever this says.
 * - `ALPHA_VANTAGE_DAILY_BUDGET`, `ALPHA_VANTAGE_MAX_CONCURRENT` — this instance's own limits (`ProviderBudgetConfig.alphaVantageLimits`).
 * - `ALPHA_VANTAGE_LICENSE_MODE` — `personal` (default: the key holder only, see [AlphaVantageAccess]) or `commercial` (requires recorded
 *   written permissions in the licence registry; fails closed until then).
 * - `ALPHA_VANTAGE_PERSONAL_UIDS`, `ALPHA_VANTAGE_PERSONAL_CLIENTS` — personal mode's explicit allowlists: verified Firebase uids and/or
 *   `loopback` (Phase 9B.9A: the only client value accepted — a LAN address can stand for many people behind NAT). Empty = nobody.
 */
data class AlphaVantageConfig(
    val apiKey: String,
    val primary: Boolean,
    val planRequestsPerMinute: Int?,
    val licenseMode: LicenseMode,
    val personalUids: Set<String>,
    val personalClients: Set<String>,
    /** Phase 9B.9A: explicit entitlement; FREE unless `ALPHA_VANTAGE_ENTITLEMENT=premium`. */
    val entitlement: Entitlement = Entitlement.FREE
) {
    enum class LicenseMode { PERSONAL, COMMERCIAL }
    enum class Entitlement { FREE, PREMIUM }
    /** Premium functions only with an explicit premium entitlement — never inferred from [planRequestsPerMinute]. */
    val premium: Boolean get() = entitlement == Entitlement.PREMIUM

    override fun toString() = "AlphaVantageConfig(primary=$primary, entitlement=$entitlement, licenseMode=$licenseMode, personalUids=${personalUids.size}, personalClients=${personalClients.size})"

    companion object {
        const val PRIMARY_VALUE = "alpha_vantage"

        /** Null unless enabled with a non-blank key. Values are trimmed; the key is never logged ([toString] omits it). */
        fun fromEnvironment(env: (String) -> String? = System::getenv): AlphaVantageConfig? {
            if (env("ALPHA_VANTAGE_ENABLED")?.trim()?.lowercase() != "true") return null
            val key = env("ALPHA_VANTAGE_API_KEY")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            fun list(name: String) = env(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
            return AlphaVantageConfig(
                apiKey = key,
                primary = env("MARKET_DATA_PRIMARY_PROVIDER")?.trim()?.lowercase() == PRIMARY_VALUE,
                planRequestsPerMinute = env("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE")?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                licenseMode = if (env("ALPHA_VANTAGE_LICENSE_MODE")?.trim()?.lowercase() == "commercial") LicenseMode.COMMERCIAL else LicenseMode.PERSONAL,
                personalUids = list("ALPHA_VANTAGE_PERSONAL_UIDS"),
                personalClients = list("ALPHA_VANTAGE_PERSONAL_CLIENTS"),
                entitlement = if (env("ALPHA_VANTAGE_ENTITLEMENT")?.trim()?.lowercase() == "premium") Entitlement.PREMIUM else Entitlement.FREE
            )
        }
    }
}

/**
 * Entitlement per Alpha Vantage function, from the official documentation (retrieved 2026-10-17): FREE = no premium label (subject to the
 * 25-requests-a-day free allowance); PREMIUM = "premium API function" (any premium plan); PREMIUM_PERSONAL_REALTIME = realtime/15-minute
 * delayed US data, "for your personal use", commercial use "contact sales". Account-specific entitlement is **not verified** (no key).
 */
enum class AlphaVantageEntitlement { FREE, PREMIUM, PREMIUM_PERSONAL_REALTIME }

object AlphaVantageFunctions {
    val ENTITLEMENT: Map<String, AlphaVantageEntitlement> = mapOf(
        "TIME_SERIES_DAILY" to AlphaVantageEntitlement.FREE,            // compact (100 points) free; outputsize=full premium
        "TIME_SERIES_DAILY_ADJUSTED" to AlphaVantageEntitlement.PREMIUM,
        "TIME_SERIES_WEEKLY_ADJUSTED" to AlphaVantageEntitlement.FREE,
        "TIME_SERIES_MONTHLY_ADJUSTED" to AlphaVantageEntitlement.FREE,
        "GLOBAL_QUOTE" to AlphaVantageEntitlement.FREE,                 // end-of-day by default; entitlement=realtime/delayed is premium, personal
        "REALTIME_BULK_QUOTES" to AlphaVantageEntitlement.PREMIUM_PERSONAL_REALTIME,
        "SYMBOL_SEARCH" to AlphaVantageEntitlement.FREE,
        "MARKET_STATUS" to AlphaVantageEntitlement.FREE,
        "LISTING_STATUS" to AlphaVantageEntitlement.FREE,
        "OVERVIEW" to AlphaVantageEntitlement.PREMIUM,
        "ETF_PROFILE" to AlphaVantageEntitlement.FREE,
        "INCOME_STATEMENT" to AlphaVantageEntitlement.FREE,
        "BALANCE_SHEET" to AlphaVantageEntitlement.FREE,
        "CASH_FLOW" to AlphaVantageEntitlement.FREE,
        "SHARES_OUTSTANDING" to AlphaVantageEntitlement.FREE,
        "EARNINGS" to AlphaVantageEntitlement.FREE,
        "EARNINGS_ESTIMATES" to AlphaVantageEntitlement.FREE,
        "EARNINGS_CALENDAR" to AlphaVantageEntitlement.FREE,
        "DIVIDENDS" to AlphaVantageEntitlement.FREE,
        "SPLITS" to AlphaVantageEntitlement.FREE,
        "TOP_GAINERS_LOSERS" to AlphaVantageEntitlement.FREE,           // end-of-day by default; realtime/delayed premium, personal
        "NEWS_SENTIMENT" to AlphaVantageEntitlement.PREMIUM,
        "INDEX_DATA" to AlphaVantageEntitlement.PREMIUM,                // 150+/min plans; index licensing separate (Q9)
        "INDEX_CATALOG" to AlphaVantageEntitlement.FREE,
        "FX_DAILY" to AlphaVantageEntitlement.FREE,
        "TREASURY_YIELD" to AlphaVantageEntitlement.FREE
    )
    fun usable(function: String, premium: Boolean): Boolean = when (ENTITLEMENT[function]) {
        AlphaVantageEntitlement.FREE -> true
        AlphaVantageEntitlement.PREMIUM -> premium
        AlphaVantageEntitlement.PREMIUM_PERSONAL_REALTIME, null -> false   // never used by StockSteps (no realtime, unknown functions)
    }
}
