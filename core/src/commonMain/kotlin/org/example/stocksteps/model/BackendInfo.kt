package org.example.stocksteps.model

import kotlinx.serialization.Serializable

/** Public backend metadata. `dataMode` is "mock" when responses come from sample fixtures. */
@Serializable
data class BackendInfo(val dataMode: String) {
    val isMock: Boolean get() = dataMode.equals("mock", ignoreCase = true)
}
