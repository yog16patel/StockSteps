package org.example.stocksteps

import android.os.Build
import android.os.Bundle
import org.example.stocksteps.account.AndroidAccountOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import org.example.stocksteps.settings.BackendEndpoints
import org.example.stocksteps.settings.BackendRouter
import kotlinx.coroutines.flow.receiveAsFlow
import org.example.stocksteps.settings.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import androidx.compose.ui.tooling.preview.Preview

/** FragmentActivity (a ComponentActivity) so AndroidX BiometricPrompt can host the system prompt. */
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val themePreferences by lazy { AndroidThemePreferenceStore(applicationContext) }
    private val backendEnvironment by lazy { AndroidBackendEnvironmentStore(applicationContext) }
    private val backendRouter by lazy {
        BackendRouter(
            store = backendEnvironment,
            endpoints = BackendEndpoints(
                real = BuildConfig.BACKEND_URL,
                mock = BuildConfig.MOCK_BACKEND_URL.ifBlank { null }
            )
        )
    }
    private val appLock by lazy { androidx.lifecycle.ViewModelProvider(this)[org.example.stocksteps.security.AndroidAppLockOwner::class.java] }

    /** Symbols from tapped alert notifications (FCM background notifications and our own foreground ones). */
    private val notificationLinks = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    private var permissionResult: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    private val permissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        permissionResult?.complete(granted)
    }

    /** Asked only when the user turns on notifications (after the app explains why). */
    private val notifications = object : org.example.stocksteps.presentation.watchlist.NotificationAccess {
        override val enabled: Boolean get() = org.example.stocksteps.account.AndroidPushTokens.notificationsAllowed(this@MainActivity)
        override suspend fun request(): Boolean {
            if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
                permissionResult = result
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                result.await()
            }
            if (!enabled) {
                // Denied before (or disabled in system settings): open this app's notification settings.
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                return false
            }
            org.example.stocksteps.account.AndroidPushTokens.refresh(applicationContext)
            return true
        }
    }

    private fun handleNotification(intent: android.content.Intent?) {
        val symbol = intent?.getStringExtra(org.example.stocksteps.account.AlertNotifications.EXTRA_SYMBOL)
        if (intent?.getStringExtra(org.example.stocksteps.account.AlertNotifications.EXTRA_TYPE) == "alert" && symbol != null) {
            notificationLinks.trySend(symbol)
            intent.removeExtra(org.example.stocksteps.account.AlertNotifications.EXTRA_SYMBOL)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotification(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appLock.authenticator.attach(this)
        handleNotification(intent)

        setContent {
            // System bar icons follow the app's chosen theme, not only the OS setting.
            val mode by themePreferences.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val accountOwner = viewModel { AndroidAccountOwner(application, backendRouter) }
            androidx.compose.runtime.DisposableEffect(accountOwner) {
                accountOwner.google.attach(this@MainActivity)
                onDispose { accountOwner.google.detach(this@MainActivity) }
            }
            // The lock follows the restored/signed-in account (only after Firebase finished restoring).
            androidx.compose.runtime.LaunchedEffect(accountOwner) {
                accountOwner.dependencies.auth.session.collect { session ->
                    if (!session.initializing) appLock.manager.onAccountChanged(session.user?.id)
                }
            }
            // Hide the recents thumbnail while the app can be locked (screenshots stay allowed).
            val lockState by appLock.manager.state.collectAsState()
            LaunchedEffect(lockState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(lockState == org.example.stocksteps.security.AppLockState.NOT_REQUIRED)
                }
            }
            androidx.compose.runtime.LaunchedEffect(accountOwner) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    accountOwner.dependencies.watchlist.retrySync()
                    kotlinx.coroutines.awaitCancellation()
                }
            }
            val hinge by produceState<WindowHinge?>(null) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                        value = info.displayFeatures.filterIsInstance<FoldingFeature>()
                            .firstOrNull { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
                            ?.let { fold ->
                                WindowHinge(fold.bounds.left.toFloat(), fold.bounds.top.toFloat(),
                                    fold.bounds.right.toFloat(), fold.bounds.bottom.toFloat(),
                                    fold.orientation == FoldingFeature.Orientation.VERTICAL)
                            }
                    }
                }
            }
            App(
                accounts = accountOwner.dependencies,
                hinge = hinge,

                navigationIcon = { AndroidNavigationIcon(it) },
                backIcon = { AndroidBackIcon() },
                themePreferences = themePreferences,
                appVersion = BuildConfig.VERSION_NAME,
                backendRouter = accountOwner.backend,
                appLock = appLock.manager,
                notifications = notifications,
                notificationLinks = notificationLinks.receiveAsFlow()
            )
        }
    }

    // Single-activity app: the activity's start/stop is the app entering foreground/background.
    // Rotation (isChangingConfigurations) is not leaving the app.
    override fun onStart() {
        super.onStart()
        // Permission may have changed in system settings while the app was away.
        org.example.stocksteps.account.AndroidPushTokens.refresh(applicationContext)
        appLock.manager.onForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) appLock.manager.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        appLock.authenticator.detach(this)
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(navigationIcon = { AndroidNavigationIcon(it) }, backIcon = { AndroidBackIcon() })
}
