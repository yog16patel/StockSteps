package org.example.stocksteps.presentation.watchlist

import androidx.compose.runtime.Composable
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.FeaturePlaceholderScreen

@Composable
internal fun WatchListScreen(hinge: WindowHinge?) {
    FeaturePlaceholderScreen(
        title = "WatchList",
        description = "Your saved stocks will appear here.",
        hinge = hinge
    )
}
