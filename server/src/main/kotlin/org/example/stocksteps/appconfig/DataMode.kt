package org.example.stocksteps.appconfig

/**
 * Where market data comes from. REAL calls FMP/Finnhub; MOCK serves captured JSON fixtures with
 * no provider calls or keys. Defaults to REAL so a missing setting never serves sample data,
 * and MOCK is refused on Cloud Run (which always sets `K_SERVICE`).
 */
enum class DataMode {
    REAL, MOCK;

    companion object {
        const val ENV = "STOCKSTEPS_DATA_MODE"

        fun fromEnvironment(env: (String) -> String? = System::getenv): DataMode {
            val mode = when (env(ENV)?.trim()?.lowercase()) {
                null, "", "real" -> REAL
                "mock" -> MOCK
                else -> error("$ENV must be real or mock")
            }
            check(mode != MOCK || env("K_SERVICE") == null) { "Mock data mode is not allowed on Cloud Run." }
            return mode
        }
    }
}
