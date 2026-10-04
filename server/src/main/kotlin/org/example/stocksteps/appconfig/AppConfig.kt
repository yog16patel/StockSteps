package org.example.stocksteps.appconfig

object AppConfig {
    val geminiApiKey: String? get() = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
    val geminiNewsModel: String get() = System.getenv("GEMINI_NEWS_MODEL")?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash-lite"
    val newsDatabasePath: String get() = System.getenv("NEWS_DB_PATH") ?: "server/data/news.db"

    val finnhubApiKey: String
        get() = requiredEnvironmentVariable("FINNHUB_API_KEY")

    val fmpApiKey: String
        get() = requiredEnvironmentVariable("FMP_API_KEY")

    private fun requiredEnvironmentVariable(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("$name is missing or blank")
}
