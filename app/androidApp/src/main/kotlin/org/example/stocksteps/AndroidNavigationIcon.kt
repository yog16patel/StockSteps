package org.example.stocksteps

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource

@Composable
internal fun AndroidNavigationIcon(destination: MainDestination) {
    val drawable = when (destination) {
        MainDestination.HOME -> R.drawable.ic_home
        MainDestination.WATCHLIST -> R.drawable.ic_watchlist
        MainDestination.LEARN -> R.drawable.ic_learn_more
        MainDestination.SETTINGS -> R.drawable.ic_settings
    }
    Icon(painter = painterResource(drawable), contentDescription = null)
}
