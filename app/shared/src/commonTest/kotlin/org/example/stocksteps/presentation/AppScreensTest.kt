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
import org.example.stocksteps.presentation.watchlist.NotificationsRoute
import org.example.stocksteps.presentation.watchlist.WatchListRoute
import org.example.stocksteps.navigation.NavigationPolicy.parentTab
import org.example.stocksteps.navigation.NavEntry
import org.example.stocksteps.navigation.TabNavigation
import org.example.stocksteps.navigation.TabNavigationState
import kotlin.test.assertTrue
import org.example.stocksteps.presentation.earnings.SettingsRemindersRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 5D.5.6B/C1: every route registered in `AppNavigation` (42 route types since C1 added Notifications and Settings reminders) has one stable screen id. */
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
        StockMovementRoute("AAPL"), NotificationsRoute, SettingsRemindersRoute
    )

    @Test
    fun everyRegisteredRouteHasOneScreenId() {
        assertEquals(42, routes.map { it::class }.toSet().size, "one sample per registered route type")
        assertEquals(routes.map { it::class }.toSet(), AppRouteScreens.map { it.first }.toSet(), "AppRouteScreens lists exactly the registered types")
        assertEquals(AppRouteScreens.size, AppRouteScreens.map { it.first }.toSet().size, "no route type listed twice")
        routes.forEach { assertNotNull(screenIdOf(it), "$it has no ScreenId") }
        assertNull(screenIdOf("not a route"))
    }

    @Test
    fun everyIdWithAnAndroidRouteIsCovered() {
        // Every ScreenId except the overlay-only app lock is reachable through a route (Notifications = the all-alerts history).
        val covered = routes.mapNotNull(::screenIdOf).toSet()
        assertEquals(ScreenId.entries.toSet() - ScreenId.APP_LOCK, covered)
    }

    @Test
    fun canonicalOwnersOfKeyRoutes() {
        assertEquals(AppTab.MARKETS, NavigationPolicy.parentTab(screenIdOf(CompanyDetailsRoute("AAPL"))!!))
        assertEquals(AppTab.MARKETS, NavigationPolicy.parentTab(screenIdOf(CompanyValuationRoute("AAPL"))!!))
        assertEquals(AppTab.PORTFOLIO, NavigationPolicy.parentTab(screenIdOf(PracticeRoute)!!))
        assertEquals(AppTab.LEARN, NavigationPolicy.parentTab(screenIdOf(LearnTermRoute("pe-ratio"))!!))
        assertEquals(AppTab.WATCHLIST, NavigationPolicy.parentTab(screenIdOf(AlertsRoute("AAPL"))!!))
        assertEquals(ScreenKind.GLOBAL, screenIdOf(NotificationsRoute)!!.kind) // Notifications never inherits a tab
        assertEquals(ScreenKind.GLOBAL, screenIdOf(SettingsRemindersRoute)!!.kind) // reminders opened from Settings stay in Settings
        assertEquals(ScreenKind.GLOBAL, screenIdOf(SettingsRoute)!!.kind)
    }

    @Test
    fun everyTabGraphStartsAtThePolicyRoot() { // 5D.5.6C1: one graph per tab, rooted at NavigationPolicy.root
        AppTab.entries.forEach { tab ->
            assertEquals(NavigationPolicy.root(tab), screenIdOf(tab.rootRoute()), "$tab root")
            assertEquals(tab, parentTab(screenIdOf(tab.rootRoute())!!))
        }
        assertEquals(AppTab.entries.size, AppTab.entries.map { it.graphClass }.toSet().size, "five distinct tab graphs")
    }

    @Test
    fun theTrailSurvivesSavingAndRestoring() { // 5D.5.6C1: activity recreation and process death restore the model with the NavController
        val s = TabNavigation.open(TabNavigation.open(TabNavigation.selectTab(TabNavigationState.initial(), AppTab.WATCHLIST),
            ScreenId.COMPANY_DETAILS, CompanyDetailsRoute("AAPL").toString()), ScreenId.LEARN_TERM, LearnTermRoute("pe-ratio").toString())
        val snapshot = TrailSnapshot(s, mapOf("entry-1" to CompanyDetailsRoute("AAPL").toString(), "entry-2" to "LearnTermRoute(id=pe|ratio)"))
        assertEquals(snapshot, decodeTrail(encodeTrail(snapshot)))
        assertEquals(TrailSnapshot(), decodeTrail(encodeTrail(TrailSnapshot())))
        assertTrue(s.hops.size == 2 && decodeTrail(encodeTrail(snapshot)).state.hops == s.hops)
        // Route keys carry the arguments, so the same screen with other arguments is a new entry.
        assertEquals(NavEntry(ScreenId.COMPANY_DETAILS, "CompanyDetailsRoute(symbol=AAPL)"), s.stack(AppTab.MARKETS).last())
    }
}
