package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.example.stocksteps.network.StockStepsApiLogging
import org.example.stocksteps.network.configureStockStepsClient

internal actual fun createBackendClient() = HttpClient(OkHttp) {
    configureStockStepsClient()
    install(StockStepsApiLogging) // debug-only API log; inert unless `AndroidApiLogging.enable` turned it on
}
internal actual fun localBackendUrl() = "http://10.0.2.2:8080"
