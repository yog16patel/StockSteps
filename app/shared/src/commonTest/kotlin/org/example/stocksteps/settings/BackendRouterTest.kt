package org.example.stocksteps.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BackendRouterTest {
    private val endpoints = BackendEndpoints(real = "https://api.example.com", mock = "http://127.0.0.1:8081")

    @Test fun urlFollowsTheStoredEnvironmentOnEveryCall() {
        val store = InMemoryBackendEnvironmentStore(BackendEnvironment.MOCK)
        val router = BackendRouter(store, endpoints)
        assertEquals("http://127.0.0.1:8081", router.currentUrl())
        router.select(BackendEnvironment.REAL)
        assertEquals("https://api.example.com", router.currentUrl())
        router.select(BackendEnvironment.MOCK)
        assertEquals("http://127.0.0.1:8081", router.currentUrl())
    }

    @Test fun withoutAMockUrlTheAppIsAlwaysReal() {
        val store = InMemoryBackendEnvironmentStore(BackendEnvironment.MOCK)
        val router = BackendRouter(store, BackendEndpoints(real = "https://api.example.com"))
        assertFalse(router.mockAvailable)
        assertEquals(BackendEnvironment.REAL, router.currentEnvironment())
        assertEquals("https://api.example.com", router.currentUrl())
        router.select(BackendEnvironment.MOCK)
        assertEquals("https://api.example.com", router.currentUrl())
    }

    @Test fun unsavedPreferenceDefaultsToReal() {
        assertEquals(BackendEnvironment.REAL, BackendEnvironmentStore.decode(null))
        assertEquals(BackendEnvironment.MOCK, BackendEnvironmentStore.decode("MOCK"))
    }
}
