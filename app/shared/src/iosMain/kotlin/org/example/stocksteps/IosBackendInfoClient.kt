package org.example.stocksteps

import org.example.stocksteps.di.StockStepsDependencies

/** Reads the backend's data mode for the iOS "Sample data" banner. */
/** `baseUrl` is read per request, so a backend switch applies to the next call. */
class IosBackendInfoClient(baseUrl: () -> String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    @Throws(Exception::class)
    suspend fun isMock(): Boolean = dependencies.getBackendInfo()().isMock
    fun close() = dependencies.close()
}
