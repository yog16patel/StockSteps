package org.example.stocksteps.account

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import org.example.stocksteps.BuildConfig
import androidx.lifecycle.ViewModel
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.stocksteps.settings.BackendRouter
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.local.WatchlistDatabase

internal class AndroidAccountOwner(application: Application, val backend: BackendRouter) : ViewModel() {
    private val firebase = FirebaseApp.getApps(application).firstOrNull()
        ?: FirebaseApp.initializeApp(application)
    private val auth = firebase?.let(FirebaseAuth::getInstance)
    init {
        if (BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATORS) auth?.useEmulator("127.0.0.1", 9099)
        AlertNotifications.createChannel(application)
        AndroidPushTokens.refresh(application)
    }
    val google = AndroidGoogleSignIn(application)
    /** "mock"/"real": user data and device registration follow the Development backend switch. */
    private val environment = backend.selection
        .map { backend.effective(it).name.lowercase() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, backend.currentEnvironment().name.lowercase())
    val dependencies = AccountDependencies(
        AndroidAuthGateway(auth, google),
        AndroidSqliteDriver(WatchlistDatabase.Schema, application, "stocksteps-watchlist.db"),
        baseUrl = backend::currentUrl,
        environment = environment,
        pushTokens = AndroidPushTokens
    )
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { dependencies.watchlist.retrySync() }
    }
    init { connectivity.registerDefaultNetworkCallback(callback) }
    override fun onCleared() {
        connectivity.unregisterNetworkCallback(callback)
        google.close()
        dependencies.close()
    }
}
