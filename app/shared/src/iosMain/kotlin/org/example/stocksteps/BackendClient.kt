package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.example.stocksteps.network.configureStockStepsClient

internal actual fun createBackendClient() = HttpClient(Darwin) { configureStockStepsClient() }
internal actual fun localBackendUrl() = "http://localhost:8080"
