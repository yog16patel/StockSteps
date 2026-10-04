package org.example.stocksteps

import androidx.compose.runtime.Composable
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.theme.StockStepsTheme

@Composable
fun App(baseUrl: String? = null, hinge: WindowHinge? = null) {
    StockStepsTheme { StockSearchRoute(baseUrl, hinge) }
}
