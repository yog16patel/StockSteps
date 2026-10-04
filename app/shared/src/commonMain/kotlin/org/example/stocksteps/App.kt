package org.example.stocksteps

import androidx.compose.runtime.Composable
import org.example.stocksteps.presentation.AppNavigation
import org.example.stocksteps.theme.StockStepsTheme

@Composable
fun App(baseUrl: String? = null, hinge: WindowHinge? = null) {
    StockStepsTheme { AppNavigation(baseUrl, hinge) }
}
