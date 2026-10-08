package org.example.stocksteps.appconfig

object AppConfig {
    val newsStore: String get() = System.getenv("NEWS_STORE")?.lowercase() ?: "firestore"
    val newsFirestoreProject: String get() = System.getenv("NEWS_FIRESTORE_PROJECT_ID")
        ?: System.getenv("GOOGLE_CLOUD_PROJECT") ?: "stocksteps"
    val newsFirestoreDatabase: String get() = System.getenv("NEWS_FIRESTORE_DATABASE_ID") ?: "(default)"

    val geminiApiKey: String? get() = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
    val geminiNewsModel: String get() = System.getenv("GEMINI_NEWS_MODEL")?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash-lite"
    val newsDatabasePath: String get() = System.getenv("NEWS_DB_PATH") ?: "server/data/news.db"

    /** Firebase project whose ID tokens the backend accepts and whose FCM it sends through. */
    val firebaseProjectId: String get() = System.getenv("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() } ?: "stocksteps"

    val finnhubApiKey: String
        get() = requiredEnvironmentVariable("FINNHUB_API_KEY")

    val fmpApiKey: String
        get() = requiredEnvironmentVariable("FMP_API_KEY")

    private fun requiredEnvironmentVariable(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("$name is missing or blank")
}
