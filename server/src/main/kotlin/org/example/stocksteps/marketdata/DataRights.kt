package org.example.stocksteps.marketdata

import java.time.Instant

/**
 * Phase 9B: source metadata and display rights for market data, provider-independent.
 *
 * Every dataset StockSteps shows is classified by **where it comes from** ([DataClass]) and **what we may do with it**
 * ([LicenseStatus]). Technical access (a key that returns data) never implies display, caching, redistribution or derived-data
 * rights: only a published licence that grants them (official public sources) or a signed agreement does. The registry is
 * fail-closed — anything not explicitly [LicenseStatus.VERIFIED_ALLOWED] for a channel is not cleared for public display.
 */
enum class DataClass { OFFICIAL_PUBLIC, LICENSED_COMMERCIAL, INTERNALLY_DERIVED, USER_OWNED, UNAVAILABLE_PENDING_LICENSE }

enum class LicenseStatus {
    /** Published terms (or a signed agreement on file) allow this use. */
    VERIFIED_ALLOWED,
    /** Published terms restrict this use under the current plan (e.g. personal/non-commercial plans). */
    VERIFIED_RESTRICTED,
    /** Not established either way; questions are open with the vendor or counsel. */
    PENDING_CONFIRMATION,
    /** Published terms forbid it. */
    NOT_ALLOWED
}

/** Where the data is shown. Mobile rights never imply website rights (and vice versa). */
enum class DisplayChannel { MOBILE_APP, WEBSITE }

/** Datasets with distinct licensing, freshness and caching rules. */
enum class Dataset {
    SYMBOL_REFERENCE, SYMBOL_SEARCH, COMPANY_PROFILE, COMPANY_LOGO, REPORTED_FUNDAMENTALS, PROVIDER_RATIOS, ANALYST_ESTIMATES,
    DAILY_PRICES, INTRADAY_BARS, QUOTES, MARKET_MOVERS, INDEX_LEVELS, DIVIDENDS, SPLITS, COMPANY_NEWS, MARKET_NEWS,
    EARNINGS_CALENDAR, ECONOMIC_INDICATORS, FX_RATES, DERIVED_METRICS
}

/** How fresh a price is. Nothing in StockSteps is real-time (no streaming); EOD is never labelled live. */
enum class PriceDelay { REAL_TIME, DELAYED, END_OF_DAY, STALE, UNKNOWN }

/** The adjustment basis of a price series. Series with different (or unknown) bases are never mixed. */
enum class AdjustmentBasis { UNADJUSTED, SPLIT_ADJUSTED, SPLIT_AND_DIVIDEND_ADJUSTED, UNVERIFIED }

/**
 * Internal provenance carried with every sourced value: which provider and dataset, when it was retrieved, the as-of date of
 * the data itself (a close date, a period end, a filing date), an optional source reference (an SEC accession number) and
 * whether it is an older copy shown because the source failed. Never dropped by fallbacks.
 */
data class Provenance(
    val provider: String,
    val dataset: Dataset,
    val retrievedAt: Instant,
    val asOf: String? = null,
    val sourceRef: String? = null,
    val stale: Boolean = false
)

/** A value with its provenance. */
data class Sourced<T>(val value: T, val provenance: Provenance)

/** Rights for one provider × dataset × channel, with the evidence they rest on. */
data class DataRights(
    val provider: String,
    val dataset: Dataset,
    val dataClass: DataClass,
    val display: LicenseStatus,
    val sharedCache: LicenseStatus,
    val derivedData: LicenseStatus,
    val redistribution: LicenseStatus,
    val channels: Set<DisplayChannel>,
    val attribution: String? = null,
    val evidence: String
)

/**
 * The licensing matrix as code (docs: `docs/reviews/market_data_phase9b/LICENSING_RIGHTS_MATRIX.md`). Unknown provider/dataset pairs
 * are not allowed. Values were checked against published pages on 2026-10-17; any signed agreement replaces them via [with].
 */
class LicenseRegistry(private val entries: Map<Pair<String, Dataset>, DataRights>) {
    fun rights(provider: String, dataset: Dataset): DataRights? = entries[provider to dataset]

    /** Public display is allowed only when display is VERIFIED_ALLOWED for that channel. Everything else fails closed. */
    fun displayAllowed(provider: String, dataset: Dataset, channel: DisplayChannel): Boolean =
        rights(provider, dataset)?.let { it.display == LicenseStatus.VERIFIED_ALLOWED && channel in it.channels } ?: false

    /** Shared server-side caching (one fetch served to many users) needs its own verified right. */
    fun sharedCacheAllowed(provider: String, dataset: Dataset): Boolean = rights(provider, dataset)?.sharedCache == LicenseStatus.VERIFIED_ALLOWED

    /**
     * A derived value inherits the **most restrictive** status of its inputs: P/E from SEC EPS and a licensed-pending price is
     * pending, not allowed. An empty input list is not allowed.
     */
    fun derivedDisplayAllowed(inputs: List<Pair<String, Dataset>>, channel: DisplayChannel): Boolean =
        inputs.isNotEmpty() && inputs.all { (p, d) -> displayAllowed(p, d, channel) && rights(p, d)?.derivedData == LicenseStatus.VERIFIED_ALLOWED }

    /** Datasets not cleared for public display on [channel] (startup report and launch checklist). */
    fun notCleared(channel: DisplayChannel): List<DataRights> = entries.values.filter { !displayAllowed(it.provider, it.dataset, channel) }
        .sortedWith(compareBy({ it.provider }, { it.dataset.ordinal }))

    /** A copy with [rights] recorded (e.g. after a signed vendor agreement). */
    fun with(rights: DataRights) = LicenseRegistry(entries + ((rights.provider to rights.dataset) to rights))

    companion object {
        private val BOTH = setOf(DisplayChannel.MOBILE_APP, DisplayChannel.WEBSITE)
        private const val SEC = "sec.gov Privacy & Security Policy, Website Dissemination: public information, may be copied or redistributed; cite the SEC as source; no SEC seal/logos; no implied endorsement"
        private const val FMP = "FMP Terms of Service and pricing/insight pages: individual plans are personal, non-commercial; public display requires a Data Display and Licensing Agreement and prior written approval"
        private const val FINNHUB = "Finnhub Terms of Service: personal plans can't be used by a business without written approval; no redistribution of data or derived results without written approval"
        private const val BOC = "Bank of Canada Terms and Conditions: content may be freely used, copied and distributed with attribution and no implied endorsement; FX rates indicative only, not for trade execution; underlying data from LSEG"
        private const val GENERATED = "Computed by StockSteps; rights follow the most restrictive input (LicenseRegistry.derivedDisplayAllowed)"

        private fun pending(provider: String, dataset: Dataset, evidence: String, cls: DataClass = DataClass.UNAVAILABLE_PENDING_LICENSE) =
            DataRights(provider, dataset, cls, LicenseStatus.VERIFIED_RESTRICTED, LicenseStatus.PENDING_CONFIRMATION, LicenseStatus.VERIFIED_RESTRICTED,
                LicenseStatus.VERIFIED_RESTRICTED, emptySet(), evidence = evidence)

        val DEFAULT: LicenseRegistry = run {
            val list = mutableListOf<DataRights>()
            for (d in listOf(Dataset.SYMBOL_REFERENCE, Dataset.REPORTED_FUNDAMENTALS))
                list += DataRights("sec", d, DataClass.OFFICIAL_PUBLIC, LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED,
                    LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, BOTH, "Source: SEC EDGAR filings", SEC)
            list += DataRights("boc", Dataset.FX_RATES, DataClass.OFFICIAL_PUBLIC, LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED,
                LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, BOTH, "Source: Bank of Canada (indicative rate)", BOC)
            for (d in listOf(Dataset.SYMBOL_SEARCH, Dataset.COMPANY_PROFILE, Dataset.COMPANY_LOGO, Dataset.REPORTED_FUNDAMENTALS, Dataset.PROVIDER_RATIOS,
                    Dataset.ANALYST_ESTIMATES, Dataset.DAILY_PRICES, Dataset.INTRADAY_BARS, Dataset.QUOTES, Dataset.MARKET_MOVERS, Dataset.INDEX_LEVELS,
                    Dataset.DIVIDENDS)) list += pending("fmp", d, FMP)
            for (d in listOf(Dataset.COMPANY_NEWS, Dataset.MARKET_NEWS, Dataset.EARNINGS_CALENDAR, Dataset.COMPANY_PROFILE, Dataset.QUOTES)) list += pending("finnhub", d, FINNHUB)
            // StockSteps' own computations: allowed by us, but display is still gated on every input (derivedDisplayAllowed).
            list += DataRights("stocksteps", Dataset.DERIVED_METRICS, DataClass.INTERNALLY_DERIVED, LicenseStatus.VERIFIED_ALLOWED,
                LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, BOTH, null, GENERATED)
            LicenseRegistry(list.associateBy { it.provider to it.dataset })
        }
    }
}

/**
 * How the license registry is applied. DEVELOPMENT (default): everything that technically works stays enabled for development and
 * MOCK, and gaps are reported. COMMERCIAL (env `STOCKSTEPS_LICENSE_MODE=commercial`, for a public launch): only datasets cleared for
 * the channel may be shown — fail closed.
 */
enum class LicenseMode {
    DEVELOPMENT, COMMERCIAL;
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): LicenseMode =
            if (env("STOCKSTEPS_LICENSE_MODE")?.trim()?.lowercase() == "commercial") COMMERCIAL else DEVELOPMENT
    }
}

class LicenseGate(val mode: LicenseMode, val registry: LicenseRegistry = LicenseRegistry.DEFAULT) {
    /** Whether [provider]'s [dataset] may be served on [channel] in this mode. */
    fun permits(provider: String, dataset: Dataset, channel: DisplayChannel): Boolean =
        mode == LicenseMode.DEVELOPMENT || registry.displayAllowed(provider, dataset, channel)

    /** One startup line per mode: what would be blocked at a commercial launch (no keys, no URLs). */
    fun summary(channel: DisplayChannel = DisplayChannel.MOBILE_APP): String {
        val gaps = registry.notCleared(channel).joinToString(", ") { "${it.provider}:${it.dataset.name.lowercase()}" }
        return "license mode=${mode.name.lowercase()} channel=${channel.name.lowercase()} notCleared=[$gaps]"
    }
}
