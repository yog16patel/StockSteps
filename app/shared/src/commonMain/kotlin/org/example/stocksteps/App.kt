package org.example.stocksteps

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import org.example.stocksteps.presentation.AppNavigation
import org.example.stocksteps.theme.StockStepsTheme

@Composable
fun App(
    baseUrl: String? = null,
    accounts: org.example.stocksteps.di.AccountDependencies? = null,
    hinge: WindowHinge? = null,
    navigationIcon: @Composable (MainDestination) -> Unit = { Text(it.label.take(1)) }
) {
    StockStepsTheme { AppNavigation(baseUrl, hinge, navigationIcon, accounts) }
}
