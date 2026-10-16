package org.example.stocksteps.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
