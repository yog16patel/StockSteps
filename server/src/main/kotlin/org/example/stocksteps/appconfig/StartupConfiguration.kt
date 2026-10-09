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

        // Phase 4C budgets: malformed numbers fail; on Cloud Run the plan limits and instance count must be explicit.
        positiveInt("CLOUD_RUN_MAX_INSTANCES")
        for (provider in PROVIDERS) for (suffix in listOf("PER_MINUTE", "BURST", "CONCURRENCY", "DAILY_TARGET")) positiveInt("PROVIDER_${provider}_$suffix")
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
