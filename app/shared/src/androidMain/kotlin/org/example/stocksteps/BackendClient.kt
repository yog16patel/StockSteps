package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.example.stocksteps.network.configureStockStepsClient

internal actual fun createBackendClient() = HttpClient(OkHttp) {
    configureStockStepsClient()
    engine { addInterceptor(BackendDiagnostics.interceptor) }
}
internal actual fun localBackendUrl() = "http://10.0.2.2:8080"
