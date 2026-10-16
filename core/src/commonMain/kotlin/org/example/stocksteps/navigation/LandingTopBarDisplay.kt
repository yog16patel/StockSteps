package org.example.stocksteps.navigation

/**
 * Copy and spoken labels for the shared landing top bar (Phase 5D.5.6B, plan §7), used by Compose and SwiftUI. Home shows the StockSteps brand
 * (D7); the other landing pages show their tab name as the page heading. Notifications (bell) and Settings (gear, D6) close every bar, after the
 * page's own actions. Notifications opens the existing all-alerts history (D5); no unread badge exists (there is no unread contract).
 */
object LandingTopBarDisplay {
    const val BRAND = "StockSteps"
    const val NOTIFICATIONS = "Notifications"
    const val SETTINGS = "Settings"
    /** Spoken labels (buttons say what they open). */
    const val NOTIFICATIONS_SPOKEN = "Notifications"
    const val SETTINGS_SPOKEN = "Settings"
    const val SEARCH_STOCKS_SPOKEN = "Search stocks"

    /** The bar's heading: the brand on Home, the tab name elsewhere. */
    fun title(tab: AppTab): String = if (tab == AppTab.HOME) BRAND else tab.label

    /** Home draws the brand mark (tile + wordmark) instead of a plain heading. */
    fun showsBrand(tab: AppTab): Boolean = tab == AppTab.HOME
}
