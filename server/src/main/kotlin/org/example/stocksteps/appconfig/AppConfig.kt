package org.example.stocksteps.appconfig

object AppConfig {
    val finnhubApiKey: String
        get() = requiredEnvironmentVariable("FINNHUB_API_KEY")

    val fmpApiKey: String
        get() = requiredEnvironmentVariable("FMP_API_KEY")

    private fun requiredEnvironmentVariable(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("$name is missing or blank")
}
