package org.example.stocksteps.news

import io.ktor.client.HttpClient
import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.example.stocksteps.appconfig.AppConfig
import org.example.stocksteps.repositoryImpl.FinnhubNewsProviderRepositoryImpl
import org.example.stocksteps.service.NewsService
import java.nio.file.Path

/** Backend composition only: domain interfaces do not depend on configuration or DI. */
fun Application.createNewsService(client: HttpClient): NewsService {
    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopped) { workerScope.cancel() }
    val simplification = AppConfig.geminiApiKey?.let { key ->
        try {
            NewsSimplificationService(
                ai = GeminiNewsSimplifier(client, key, AppConfig.geminiNewsModel),
                store = SqliteNewsSimplificationStore(Path.of(AppConfig.newsDatabasePath)),
                scope = workerScope,
                version = "${AppConfig.geminiNewsModel}:${GeminiNewsSimplifier.PROMPT_VERSION}"
            )
        } catch (cause: Exception) {
            // No credential, path, provider body or exception message is logged.
            log.warn("News simplification storage unavailable; serving original news.")
            null
        }
    }
    return NewsService(FinnhubNewsProviderRepositoryImpl(client, AppConfig.finnhubApiKey), simplification)
}
