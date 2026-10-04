package org.example.stocksteps

import io.ktor.client.HttpClient

internal expect fun createBackendClient(): HttpClient
internal expect fun localBackendUrl(): String
