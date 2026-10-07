package org.example.stocksteps

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
import org.example.stocksteps.settings.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private val themePreferences by lazy { AndroidThemePreferenceStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
            val accountOwner = viewModel { AndroidAccountOwner(application) }
            androidx.compose.runtime.DisposableEffect(accountOwner) {
                accountOwner.google.attach(this@MainActivity)
                onDispose { accountOwner.google.detach(this@MainActivity) }
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
                baseUrl = if (BuildConfig.DEBUG) "http://127.0.0.1:8080" else null,
                navigationIcon = { AndroidNavigationIcon(it) },
                backIcon = { AndroidBackIcon() },
                themePreferences = themePreferences,
                appVersion = BuildConfig.VERSION_NAME
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(navigationIcon = { AndroidNavigationIcon(it) }, backIcon = { AndroidBackIcon() })
}