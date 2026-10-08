package org.example.stocksteps.presentation.home

/** Navigation and section intents; the Screen never resolves dependencies. */
internal sealed interface HomeAction {
    data object Refresh : HomeAction
    data object RetryQuotes : HomeAction
    data object RetryNews : HomeAction
    data object RetryWatchlists : HomeAction
    data object RetryAlerts : HomeAction
    data object Learn : HomeAction
    data object Practice : HomeAction
    data object DailyBrief : HomeAction
    data object Search : HomeAction
    data object Watchlist : HomeAction
    data object Alerts : HomeAction
    data object Settings : HomeAction
    data object ClearRecent : HomeAction
    data class Persona(val id: String?) : HomeAction
    data class OpenStock(val symbol: String) : HomeAction
    data class OpenArticle(val url: String) : HomeAction
    data class OpenStockAlerts(val symbol: String) : HomeAction
    data class OpenEarnings(val symbol: String) : HomeAction
    data class ContinueResearch(val symbol: String, val name: String) : HomeAction
}
