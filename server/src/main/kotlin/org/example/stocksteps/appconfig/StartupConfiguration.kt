package org.example.stocksteps.appconfig

/**
 * Phase 5A startup validation. Settings that are present but malformed used to fall back to defaults silently (e.g. `TRUSTED_PROXY_HOPS=two`
 * meant "no trusted proxy"); now they stop startup with a message that names the variable, never its value. On Cloud Run (`K_SERVICE` is set)
 * settings whose defaults are only safe on a developer machine — the Firebase/Firestore project, provider plan limits and the instance count
 * the budgets are divided by — must be explicit, so a staging service can't silently use the wrong project or development budgets.
 */
object StartupConfiguration {
    /** [errors] stop startup; [warnings] are logged. Both name settings only. */
    data class Report(val errors: List<String>, val warnings: List<String>)

    val PROVIDERS = listOf("FMP", "FINNHUB", "GEMINI", "BOC")
    private val INTERNAL_TOKENS = listOf("ALERTS_EVALUATOR_TOKEN", "EARNINGS_REMINDERS_TOKEN", "DAILY_BRIEF_DISPATCH_TOKEN", "USAGE_METRICS_TOKEN")

    /** `PORT` (Cloud Run sets it), default 8080; malformed values fail instead of silently using 8080. */
    fun port(env: (String) -> String? = System::getenv): Int {
        val value = env("PORT")?.trim()?.takeIf { it.isNotEmpty() } ?: return 8080
        return value.toIntOrNull()?.takeIf { it in 1..65_535 } ?: error("PORT must be a port number (1–65535)")
    }

    fun onCloudRun(env: (String) -> String? = System::getenv): Boolean = !env("K_SERVICE").isNullOrBlank()

    fun check(mode: DataMode, env: (String) -> String? = System::getenv): Report {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        fun value(name: String) = env(name)?.trim()?.takeIf { it.isNotEmpty() }
        fun positiveInt(name: String, range: IntRange = 1..Int.MAX_VALUE) {
            val v = value(name) ?: return
            if (v.toIntOrNull()?.takeIf { it in range } == null)
                errors += "$name must be a whole number${if (range.last == Int.MAX_VALUE) " ≥ ${range.first}" else " from ${range.first} to ${range.last}"}"
        }
        fun boolean(name: String) {
            val v = value(name) ?: return
            if (v.lowercase() !in setOf("true", "false")) errors += "$name must be true or false"
        }

        runCatching { port(env) }.onFailure { errors += it.message.orEmpty() }
        value("LOG_FORMAT")?.let { if (it !in setOf("text", "json")) errors += "LOG_FORMAT must be text or json (lower case)" }
        val cloudRun = onCloudRun(env)
        if (cloudRun && value("LOG_FORMAT") != "json") warnings += "LOG_FORMAT is not json on Cloud Run: log severities won't be recognised by Cloud Logging."
        if (mode == DataMode.MOCK) return Report(errors, warnings)

        // Provider credentials: market data can't work without them (REAL never falls back to sample data).
        for (name in listOf("FMP_API_KEY", "FINNHUB_API_KEY")) if (value(name) == null) errors += "$name is required in REAL mode"
        if (value("GEMINI_API_KEY") == null) warnings += "GEMINI_API_KEY is not set: AI explanations use their deterministic fallbacks or report AI as unavailable."
        value("QUOTE_PROVIDER")?.let { if (it.lowercase() !in setOf("fmp", "finnhub")) errors += "QUOTE_PROVIDER must be fmp or finnhub" }
        value("NEWS_STORE")?.let { if (it.lowercase() !in setOf("firestore", "sqlite")) errors += "NEWS_STORE must be firestore or sqlite" }
        if (cloudRun && value("NEWS_STORE")?.lowercase() == "sqlite")
            warnings += "NEWS_STORE=sqlite on Cloud Run: simplified news is kept in the instance's memory-backed filesystem and lost on scale-in."

        // Phase 4 security.
        positiveInt("TRUSTED_PROXY_HOPS", 1..5)
        boolean("APP_CHECK_ENFORCE")
        value("FIREBASE_PROJECT_NUMBER")?.let { if (!it.all(Char::isDigit)) errors += "FIREBASE_PROJECT_NUMBER must be the numeric project number" }
        if (value("APP_CHECK_ENFORCE")?.lowercase() == "true" && value("FIREBASE_PROJECT_NUMBER") == null)
            errors += "APP_CHECK_ENFORCE=true requires FIREBASE_PROJECT_NUMBER"
        val audience = value("INTERNAL_OIDC_AUDIENCE")
        val account = value("INTERNAL_OIDC_SERVICE_ACCOUNT")
        if ((audience == null) != (account == null)) errors += "INTERNAL_OIDC_AUDIENCE and INTERNAL_OIDC_SERVICE_ACCOUNT must be set together"
        val shortTokens = INTERNAL_TOKENS.filter { name -> value(name)?.let { it.length < 32 } == true }
        if (shortTokens.isNotEmpty()) errors += "${shortTokens.joinToString()} must be at least 32 characters"
        if (cloudRun && audience == null && INTERNAL_TOKENS.none { value(it) != null })
            warnings += "No internal job authentication (INTERNAL_OIDC_* or per-job tokens): the /internal/… routes are not registered."
        if (value("TRUSTED_PROXY_HOPS") == null)
            warnings += "TRUSTED_PROXY_HOPS is not set: guests without a verified sign-in share one admission pool per route group."
        positiveInt("WATCH_DATA_ANONYMOUS_MAX_SYMBOLS")
        // Phase 9A per-class rate limits (RateLimitPolicy): overrides must be positive whole numbers.
        for (name in org.example.stocksteps.security.RateLimitPolicy.DEFAULTS.keys) for (suffix in listOf("GUEST_PER_MINUTE", "USER_PER_MINUTE", "POOL_PER_MINUTE", "MAX_IN_FLIGHT"))
            positiveInt("RATE_LIMIT_${name.uppercase()}_$suffix")
        value("SCREENER_UNIVERSE")?.let { if (it.lowercase() !in setOf("curated", "fmp")) errors += "SCREENER_UNIVERSE must be curated or fmp" }
        value("SCREENER_ENRICHMENT_PER_HOUR")?.let { if (it.toIntOrNull()?.takeIf { n -> n >= 0 } == null) errors += "SCREENER_ENRICHMENT_PER_HOUR must be a whole number ≥ 0" }
        // Phase 9B.7: Alpha Vantage (names only in messages; the key is never echoed).
        boolean("ALPHA_VANTAGE_ENABLED")
        value("MARKET_DATA_PRIMARY_PROVIDER")?.let { if (it.lowercase() !in setOf("fmp", "alpha_vantage")) errors += "MARKET_DATA_PRIMARY_PROVIDER must be fmp or alpha_vantage" }
        for (name in listOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE", "ALPHA_VANTAGE_DAILY_BUDGET", "ALPHA_VANTAGE_MAX_CONCURRENT")) positiveInt(name)
        value("ALPHA_VANTAGE_LICENSE_MODE")?.let { if (it.lowercase() !in setOf("personal", "commercial")) errors += "ALPHA_VANTAGE_LICENSE_MODE must be personal or commercial" }
        // Phase 9B.9A: entitlement is explicit and separate from the request rate; the free budget can't exceed the published 25 a day.
        value("ALPHA_VANTAGE_ENTITLEMENT")?.let { if (it.lowercase() !in setOf("free", "premium")) errors += "ALPHA_VANTAGE_ENTITLEMENT must be free or premium" }
        val avPremium = value("ALPHA_VANTAGE_ENTITLEMENT")?.lowercase() == "premium"
        if (value("ALPHA_VANTAGE_ENABLED")?.lowercase() == "true") {
            if (!avPremium && (value("ALPHA_VANTAGE_DAILY_BUDGET")?.toLongOrNull() ?: 0) > 25)
                errors += "ALPHA_VANTAGE_DAILY_BUDGET can't exceed 25 (the published free allowance) without ALPHA_VANTAGE_ENTITLEMENT=premium"
            if (!avPremium && value("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE") != null)
                warnings += "ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE is ignored on the free entitlement (free rate and 25-a-day allowance apply; premium functions stay off)."
            if (avPremium && value("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE") == null)
                errors += "ALPHA_VANTAGE_ENTITLEMENT=premium requires ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE (the subscribed plan's rate)"
        }
        // Only loopback identifies the owner's own machine; a LAN address may be shared by many devices behind NAT.
        value("ALPHA_VANTAGE_PERSONAL_CLIENTS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { entries ->
            if (entries.any { !it.equals("loopback", ignoreCase = true) })
                errors += "ALPHA_VANTAGE_PERSONAL_CLIENTS accepts only loopback (a network address can stand for many people); use ALPHA_VANTAGE_PERSONAL_UIDS for signed-in access"
        }
        value("FUNDAMENTALS_PRIMARY_PROVIDER")?.let { if (it.lowercase() !in setOf("fmp", "alpha_vantage")) errors += "FUNDAMENTALS_PRIMARY_PROVIDER must be fmp or alpha_vantage" }
        if (value("FUNDAMENTALS_PRIMARY_PROVIDER")?.lowercase() == "alpha_vantage" && value("MARKET_DATA_PRIMARY_PROVIDER")?.lowercase() != "alpha_vantage") {
            if (value("ALPHA_VANTAGE_ENABLED")?.lowercase() != "true" || value("ALPHA_VANTAGE_API_KEY") == null)
                errors += "FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage requires ALPHA_VANTAGE_ENABLED=true and ALPHA_VANTAGE_API_KEY"
            if (value("ALPHA_VANTAGE_LICENSE_MODE")?.lowercase() != "commercial" && cloudRun)
                errors += "ALPHA_VANTAGE_LICENSE_MODE=personal can't be served from Cloud Run: a personal licence covers the key holder only"
        }
        if (value("MARKET_DATA_PRIMARY_PROVIDER")?.lowercase() == "alpha_vantage") {
            if (value("ALPHA_VANTAGE_ENABLED")?.lowercase() != "true" || value("ALPHA_VANTAGE_API_KEY") == null)
                errors += "MARKET_DATA_PRIMARY_PROVIDER=alpha_vantage requires ALPHA_VANTAGE_ENABLED=true and ALPHA_VANTAGE_API_KEY"
            val personal = value("ALPHA_VANTAGE_LICENSE_MODE")?.lowercase() != "commercial"
            if (personal && cloudRun) errors += "ALPHA_VANTAGE_LICENSE_MODE=personal can't be served from Cloud Run: a personal licence covers the key holder only"
            if (personal && value("ALPHA_VANTAGE_PERSONAL_UIDS") == null && value("ALPHA_VANTAGE_PERSONAL_CLIENTS") == null)
                warnings += "Alpha Vantage personal mode with no ALPHA_VANTAGE_PERSONAL_UIDS / ALPHA_VANTAGE_PERSONAL_CLIENTS: market-data routes refuse every caller."
            if (value("ALPHA_VANTAGE_ENTITLEMENT")?.lowercase() != "premium")
                warnings += "Alpha Vantage free entitlement: 20 requests a day on this instance, 100 daily closes, no profiles or premium functions; Discover enrichment and the fundamentals warm-up are off."
        }
        value("SCREENER_ENRICHMENT_PER_DAY")?.let { if (it.toIntOrNull()?.takeIf { n -> n >= 0 } == null) errors += "SCREENER_ENRICHMENT_PER_DAY must be a whole number ≥ 0" }
        value("SEC_USER_AGENT")?.let { if (!org.example.stocksteps.marketdata.sec.SecEdgarClient.validUserAgent(it.trim())) errors += "SEC_USER_AGENT must name the app and a contact e-mail address" }
        value("STOCKSTEPS_LICENSE_MODE")?.let { if (it.trim().lowercase() !in setOf("development", "commercial")) errors += "STOCKSTEPS_LICENSE_MODE must be development or commercial" }
        value("EOD_PUBLICATION_DELAY_MINUTES")?.let { if (it.toIntOrNull()?.takeIf { m -> m in 0..720 } == null) errors += "EOD_PUBLICATION_DELAY_MINUTES must be a whole number from 0 to 720" }

        // Phase 4C budgets: malformed numbers fail; on Cloud Run the plan limits and instance count must be explicit.
        positiveInt("CLOUD_RUN_MAX_INSTANCES")
        for (provider in PROVIDERS) for (suffix in listOf("PER_MINUTE", "BURST", "CONCURRENCY", "DAILY_TARGET", "DAILY_LIMIT")) positiveInt("PROVIDER_${provider}_$suffix")
        // Phase 9B: SEC EDGAR has a published ceiling (10 requests/second); overrides may lower the lane, never raise it past 600/min.
        for (suffix in listOf("PER_MINUTE", "BURST", "CONCURRENCY", "DAILY_TARGET", "DAILY_LIMIT")) positiveInt("PROVIDER_SEC_$suffix")
        value("PROVIDER_SEC_PER_MINUTE")?.toLongOrNull()?.let { if (it > 600) errors += "PROVIDER_SEC_PER_MINUTE must not exceed 600 (SEC fair access: 10 requests/second)" }
        // Phase 7A: the daily allowance is separate from the per-minute rate and is never inferred from it.
        for (provider in PROVIDERS) {
            val target = value("PROVIDER_${provider}_DAILY_TARGET")?.toLongOrNull()
            val limit = value("PROVIDER_${provider}_DAILY_LIMIT")?.toLongOrNull()
            if (target != null && limit != null && target > limit)
                warnings += "PROVIDER_${provider}_DAILY_TARGET is above PROVIDER_${provider}_DAILY_LIMIT: the target alert can never fire before the limit refuses calls."
            val perMinute = value("PROVIDER_${provider}_PER_MINUTE")?.toLongOrNull()
            if (limit != null && perMinute != null && limit < perMinute)
                warnings += "PROVIDER_${provider}_DAILY_LIMIT is below PROVIDER_${provider}_PER_MINUTE: check that the per-minute value isn't the plan's daily figure."
        }
        if (value("PROVIDER_FMP_DAILY_LIMIT") == null)
            warnings += "PROVIDER_FMP_DAILY_LIMIT is not set: no daily FMP allowance is enforced (set it only to the account's confirmed daily limit)."
        value("PROVIDER_SAFETY_MARGIN")?.let { if (it.toDoubleOrNull()?.takeIf { m -> m in 0.1..1.0 } == null) errors += "PROVIDER_SAFETY_MARGIN must be a number from 0.1 to 1.0" }
        if (cloudRun) {
            val missing = (listOf("CLOUD_RUN_MAX_INSTANCES") + PROVIDERS.map { "PROVIDER_${it}_PER_MINUTE" }).filter { value(it) == null }
            if (missing.isNotEmpty()) errors += "${missing.joinToString()} must be set on Cloud Run (verified plan limits and the service's maximum instances)"
            // The code defaults name the project "stocksteps"; a staging service must say which project it uses.
            if (value("FIREBASE_PROJECT_ID") == null) errors += "FIREBASE_PROJECT_ID must be set on Cloud Run"
            if (value("NEWS_FIRESTORE_PROJECT_ID") == null && value("GOOGLE_CLOUD_PROJECT") == null)
                errors += "NEWS_FIRESTORE_PROJECT_ID (or GOOGLE_CLOUD_PROJECT) must be set on Cloud Run"
        }
        val firestoreProject = value("NEWS_FIRESTORE_PROJECT_ID") ?: value("GOOGLE_CLOUD_PROJECT")
        if (firestoreProject != null && value("FIREBASE_PROJECT_ID") != null && firestoreProject != value("FIREBASE_PROJECT_ID"))
            warnings += "The Firestore project differs from FIREBASE_PROJECT_ID: user data and sign-in tokens come from different projects."
        return Report(errors, warnings)
    }
}
