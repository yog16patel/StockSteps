package org.example.stocksteps.di

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.data.account.PlatformAuthGateway
import org.example.stocksteps.local.WatchlistDatabase
import org.example.stocksteps.model.User
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Builds the real account graph (real SQLite, no network) so every Koin binding must resolve. */
class AccountDependenciesTest {
    private object SignedOut : PlatformAuthGateway {
        override val configurationError: String? = null
        override fun observeUser(onChange: (User?) -> Unit): AccountSubscription { onChange(null); return object : AccountSubscription { override fun cancel() = Unit } }
        override fun signUp(email: String, password: String, completion: (String?) -> Unit) = completion("unused")
        override fun signIn(email: String, password: String, completion: (String?) -> Unit) = completion("unused")
        override fun signOut(completion: (String?) -> Unit) = completion(null)
    }

    @Test fun everyBindingResolves() {
        val dependencies = AccountDependencies(SignedOut, inMemoryDriver(WatchlistDatabase.Schema), { "http://127.0.0.1:1" }, MutableStateFlow("mock"))
        try {
            assertNotNull(dependencies.watchlist)
            assertNotNull(dependencies.watchlists)
            assertNotNull(dependencies.alerts)
            assertNotNull(dependencies.watchData)
            assertNotNull(dependencies.devices)
            assertNotNull(dependencies.entitlements)
            assertNotNull(dependencies.insightsPresenter)
            assertNotNull(dependencies.signOut())
            assertNotNull(dependencies.addToWatchlist())
            assertNotNull(dependencies.removeFromWatchlist())
            assertNotNull(dependencies.accountViewModel())
            assertNotNull(dependencies.stockWatchlistViewModel())
        } finally { dependencies.close() }
    }
}
