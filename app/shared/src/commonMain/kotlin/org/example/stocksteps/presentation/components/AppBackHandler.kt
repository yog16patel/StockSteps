package org.example.stocksteps.presentation.components

import androidx.compose.runtime.Composable

/**
 * 5D.5.6C1: the app-level system Back hook of the tab navigator (Android: the activity's back dispatcher, registered after the NavHost so it
 * decides first; elsewhere a no-op — the iOS app is SwiftUI).
 */
@Composable
internal expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
