package org.example.stocksteps.news

import com.google.cloud.firestore.FirestoreOptions
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.example.stocksteps.appconfig.AppConfig
import org.example.stocksteps.repositoryImpl.FinnhubNewsProviderRepositoryImpl
import org.example.stocksteps.service.NewsService
import java.nio.file.Path
import java.nio.file.Files

/** Backend composition only: domain interfaces do not depend on configuration or DI. */
fun Application.createNewsService(client: HttpClient): NewsService {
    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopped) { workerScope.cancel() }
    val simplification = try {
        val store = when (AppConfig.newsStore) {
            "sqlite" -> SqliteNewsSimplificationStore(Path.of(AppConfig.newsDatabasePath))
            "firestore" -> {
                val database = FirestoreOptions.getDefaultInstance().toBuilder()
                    .setProjectId(AppConfig.newsFirestoreProject)
                    .setDatabaseId(AppConfig.newsFirestoreDatabase)
                    .build().service
                monitor.subscribe(ApplicationStopped) { database.close() }
                val cloud = FirestoreNewsSimplificationStore(database)
                val localPath = Path.of(AppConfig.newsDatabasePath)
                val local = if (Files.isRegularFile(localPath)) {
                    runCatching { SqliteNewsSimplificationStore(localPath) }.getOrNull()
                } else null
                if (local != null) MigratingNewsSimplificationStore(cloud, local) else cloud
            }
            else -> error("NEWS_STORE must be firestore or sqlite")
        }
        NewsSimplificationService(
            ai = AppConfig.geminiApiKey?.let { GeminiNewsSimplifier(client, it, AppConfig.geminiNewsModel) },
            store = store,
            scope = workerScope,
            version = "${AppConfig.geminiNewsModel}:${GeminiNewsSimplifier.PROMPT_VERSION}"
        )
    } catch (cause: Exception) {
        // No credential, path, provider body or exception message is logged.
        log.warn("News simplification storage unavailable; serving original news.")
        null
    }
    return NewsService(FinnhubNewsProviderRepositoryImpl(client, AppConfig.finnhubApiKey), simplification)
}
