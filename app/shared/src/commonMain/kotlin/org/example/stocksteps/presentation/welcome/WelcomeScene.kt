package org.example.stocksteps.presentation.welcome

import androidx.compose.runtime.Composable
import org.example.stocksteps.WindowHinge

@Composable
internal fun WelcomeScene(hinge: WindowHinge?, onStartExploring: () -> Unit) {
    WelcomeScreen(hinge = hinge, onStartExploring = onStartExploring)
}
