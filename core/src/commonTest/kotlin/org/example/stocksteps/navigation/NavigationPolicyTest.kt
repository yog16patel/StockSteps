package org.example.stocksteps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationPolicyTest {
    @Test
    fun everyScreenHasExactlyOneClassificationAndTabScreensExactlyOneParent() {
        ScreenId.entries.forEach { s ->
            when (s.kind) {
                ScreenKind.TAB_ROOT, ScreenKind.TAB_SCREEN -> assertNotNull(NavigationPolicy.parentTab(s), "$s needs a parent tab")
                ScreenKind.MODAL, ScreenKind.GLOBAL -> assertNull(NavigationPolicy.parentTab(s), "$s must not inherit a tab")
            }
        }
    }

    @Test
    fun eachTabHasExactlyOneRoot() {
        AppTab.entries.forEach { tab ->
            assertEquals(1, ScreenId.entries.count { it.kind == ScreenKind.TAB_ROOT && it.tab == tab }, "$tab")
            assertEquals(tab, NavigationPolicy.root(tab).tab)
        }
        assertEquals(AppTab.entries.size, ScreenId.entries.count { it.kind == ScreenKind.TAB_ROOT })
    }

    @Test
    fun auditedInventoryIsCovered() {
        // 5D.5.6A §2: 33 tab destinations + 9 modal/global rows (Company Search is modal; in-screen sheets excluded) = 42 ids, plus the Settings-scope Earnings reminders (5D.5.6C1).
        assertEquals(33, ScreenId.entries.count { NavigationPolicy.isTabScreen(it) })
        assertEquals(4, ScreenId.entries.count { it.kind == ScreenKind.MODAL })
        assertEquals(6, ScreenId.entries.count { it.kind == ScreenKind.GLOBAL })
    }

    @Test
    fun approvedCanonicalOwners() {
        val markets = listOf(ScreenId.COMPANY_DETAILS, ScreenId.COMPANY_FINANCIALS, ScreenId.COMPANY_VALUATION, ScreenId.COMPANY_NEWS,
            ScreenId.NEWS_INSIGHT, ScreenId.STOCK_MOVEMENT, ScreenId.DISCOVER, ScreenId.COMPARE, ScreenId.EARNINGS_CALENDAR, ScreenId.EARNINGS_DIGEST)
        markets.forEach { assertEquals(AppTab.MARKETS, NavigationPolicy.parentTab(it), "$it") }
        assertEquals(AppTab.HOME, NavigationPolicy.parentTab(ScreenId.DAILY_BRIEF))
        assertEquals(AppTab.HOME, NavigationPolicy.parentTab(ScreenId.DAILY_BRIEF_HISTORY))
        listOf(ScreenId.HOLDING_DETAILS, ScreenId.PRACTICE, ScreenId.PRACTICE_ORDER).forEach { assertEquals(AppTab.PORTFOLIO, NavigationPolicy.parentTab(it)) }
        assertEquals(AppTab.WATCHLIST, NavigationPolicy.parentTab(ScreenId.STOCK_ALERTS))
        listOf(ScreenId.LEARN_TERM, ScreenId.LEARN_TOPICS, ScreenId.GUIDED_RESEARCH).forEach { assertEquals(AppTab.LEARN, NavigationPolicy.parentTab(it)) }
        listOf(ScreenId.SETTINGS, ScreenId.NOTIFICATIONS, ScreenId.AUTH, ScreenId.APP_LOCK).forEach { assertEquals(ScreenKind.GLOBAL, it.kind) }
        assertEquals(ScreenKind.MODAL, ScreenId.COMPANY_SEARCH.kind) // D1a
        assertEquals(ScreenKind.MODAL, ScreenId.TRANSACTION_FORM.kind) // D11
    }

    @Test
    fun bottomNavigationAndTopBarAreDeterministic() {
        ScreenId.entries.forEach { s ->
            assertEquals(NavigationPolicy.isTabScreen(s), NavigationPolicy.showsBottomNavigation(s), "$s")
            val bar = NavigationPolicy.topBar(s)
            when (s.kind) {
                ScreenKind.TAB_ROOT -> assertEquals(TopBarStyle.LANDING, bar)
                ScreenKind.TAB_SCREEN -> assertEquals(TopBarStyle.BACK, bar)
                else -> assertTrue(bar == TopBarStyle.MODAL || (s == ScreenId.APP_LOCK && bar == TopBarStyle.NONE), "$s")
            }
        }
    }

    @Test
    fun crossingTabsDependsOnlyOnTheCanonicalOwner() {
        AppTab.entries.forEach { current ->
            assertEquals(current != AppTab.MARKETS, NavigationPolicy.crossesTabs(current, ScreenId.COMPANY_DETAILS))
            assertFalse(NavigationPolicy.crossesTabs(current, ScreenId.SETTINGS))
            assertFalse(NavigationPolicy.crossesTabs(current, ScreenId.COMPANY_SEARCH))
        }
    }

    @Test
    fun landingBarCopy() {
        assertEquals("StockSteps", LandingTopBarDisplay.title(AppTab.HOME))
        assertTrue(LandingTopBarDisplay.showsBrand(AppTab.HOME))
        listOf(AppTab.MARKETS to "Markets", AppTab.PORTFOLIO to "Portfolio", AppTab.WATCHLIST to "Watchlist", AppTab.LEARN to "Learn").forEach { (tab, title) ->
            assertEquals(title, LandingTopBarDisplay.title(tab)); assertFalse(LandingTopBarDisplay.showsBrand(tab))
        }
        assertEquals("Notifications", LandingTopBarDisplay.NOTIFICATIONS_SPOKEN)
        assertEquals("Settings", LandingTopBarDisplay.SETTINGS_SPOKEN)
    }
}
