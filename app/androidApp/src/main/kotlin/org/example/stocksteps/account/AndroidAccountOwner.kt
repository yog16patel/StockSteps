package org.example.stocksteps.account

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import org.example.stocksteps.BuildConfig
import androidx.lifecycle.ViewModel
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.local.WatchlistDatabase

internal class AndroidAccountOwner(application: Application) : ViewModel() {
    private val firebase = FirebaseApp.getApps(application).firstOrNull()
        ?: FirebaseApp.initializeApp(application)
    private val auth = firebase?.let(FirebaseAuth::getInstance)
    private val cloud = firebase?.let(FirebaseFirestore::getInstance)
    init {
        if (BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATORS) {
            auth?.useEmulator("127.0.0.1", 9099)
            cloud?.useEmulator("127.0.0.1", 8085)
        }
    }
    val google = AndroidGoogleSignIn(application)
    val dependencies = AccountDependencies(
        AndroidAuthGateway(auth, google),
        AndroidWatchlistGateway(cloud),
        AndroidSqliteDriver(WatchlistDatabase.Schema, application, "stocksteps-watchlist.db")
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
