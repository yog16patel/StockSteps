package org.example.stocksteps.presentation

import org.example.stocksteps.navigation.AppTab
import org.example.stocksteps.navigation.NavigationPolicy
import org.example.stocksteps.navigation.ScreenId
import org.example.stocksteps.navigation.ScreenKind
import org.example.stocksteps.presentation.account.AuthRoute
import org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute
import org.example.stocksteps.presentation.brief.DailyBriefRoute
import org.example.stocksteps.presentation.companydetails.*
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.earnings.*
import org.example.stocksteps.presentation.learn.LearnRoute
import org.example.stocksteps.presentation.learn.LearnTermRoute
import org.example.stocksteps.presentation.learn.LearnTopicsRoute
import org.example.stocksteps.presentation.markets.MarketsRoute
import org.example.stocksteps.presentation.portfolio.*
import org.example.stocksteps.presentation.practice.PracticeOrderRoute
import org.example.stocksteps.presentation.practice.PracticeRoute
import org.example.stocksteps.presentation.practice.PracticeSearchRoute
import org.example.stocksteps.presentation.research.GuidedResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonAiRoute
import org.example.stocksteps.presentation.screener.ComparisonResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonRoute
import org.example.stocksteps.presentation.screener.ScreenerRoute
import org.example.stocksteps.presentation.settings.SettingsRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.watchlist.AlertsRoute
import org.example.stocksteps.presentation.watchlist.WatchListRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 5D.5.6B: every route registered in `AppNavigation` (40 `composable<…>` entries, 5D.5.6A audit) has exactly one stable screen id. */
class AppScreensTest {
    /** One sample value per registered route type, in `AppNavigation` order. */
    private val routes: List<Any> = listOf(
        DiscoveryRoute, PortfolioRoute, ScreenerRoute(), ComparisonRoute, ComparisonResearchRoute, ComparisonAiRoute(), PortfolioInsightsRoute,
        HoldingDetailsRoute("acc", "AAPL"), PortfolioTransactionsRoute(), PortfolioTransactionRoute("t1"), PracticeSearchRoute, PortfolioSearchRoute,
        PortfolioEntryRoute(), MarketsRoute, DailyBriefRoute(), DailyBriefHistoryRoute, EarningsCalendarRoute(), EarningsEventRoute("AAPL:2026-Q3"),
        EarningsResultsRoute("AAPL", 2026, 3), EarningsDetailsRoute("AAPL"), WatchListRoute, AlertsRoute("AAPL"), LearnRoute, LearnTopicsRoute(),
        LearnTermRoute("pe-ratio"), PracticeRoute, PracticeOrderRoute("AAPL"), GuidedResearchRoute("AAPL"), SettingsRoute, EarningsDigestRoute,
        EarningsDigestSettingsRoute, EarningsRemindersRoute, AuthRoute(), StockSearchRoute(), CompanyDetailsRoute("AAPL"),
        CompanyFinancialsRoute("AAPL"), CompanyValuationRoute("AAPL"), CompanyNewsRoute("AAPL"), NewsInsightRoute("AAPL", "a1"),
        StockMovementRoute("AAPL")
    )

    @Test
    fun everyRegisteredRouteHasOneScreenId() {
        assertEquals(40, routes.map { it::class }.toSet().size, "one sample per registered route type")
        routes.forEach { assertNotNull(screenIdOf(it), "$it has no ScreenId") }
        assertNull(screenIdOf("not a route"))
    }

    @Test
    fun everyIdWithAnAndroidRouteIsCovered() {
        // Every ScreenId except the overlay-only app lock is reachable through a route (Notifications = the all-alerts history).
        val covered = (routes + AlertsRoute(null)).mapNotNull(::screenIdOf).toSet()
        assertEquals(ScreenId.entries.toSet() - ScreenId.APP_LOCK, covered)
    }

    @Test
    fun canonicalOwnersOfKeyRoutes() {
        assertEquals(AppTab.MARKETS, NavigationPolicy.parentTab(screenIdOf(CompanyDetailsRoute("AAPL"))!!))
        assertEquals(AppTab.MARKETS, NavigationPolicy.parentTab(screenIdOf(CompanyValuationRoute("AAPL"))!!))
        assertEquals(AppTab.PORTFOLIO, NavigationPolicy.parentTab(screenIdOf(PracticeRoute)!!))
        assertEquals(AppTab.LEARN, NavigationPolicy.parentTab(screenIdOf(LearnTermRoute("pe-ratio"))!!))
        assertEquals(AppTab.WATCHLIST, NavigationPolicy.parentTab(screenIdOf(AlertsRoute("AAPL"))!!))
        assertEquals(ScreenKind.GLOBAL, screenIdOf(AlertsRoute(null))!!.kind) // Notifications never inherits a tab
        assertEquals(ScreenKind.GLOBAL, screenIdOf(SettingsRoute)!!.kind)
    }
}
