package org.example.stocksteps.presentation.settings

import androidx.compose.runtime.Composable
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.FeaturePlaceholderScreen

@Composable
internal fun SettingsScreen(hinge: WindowHinge?) {
    FeaturePlaceholderScreen(
        title = "Settings",
        description = "App preferences will be available here.",
        hinge = hinge
    )
}
