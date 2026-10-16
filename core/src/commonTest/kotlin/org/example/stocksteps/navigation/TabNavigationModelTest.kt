package org.example.stocksteps.navigation

import org.example.stocksteps.navigation.AppTab.HOME
import org.example.stocksteps.navigation.AppTab.LEARN
import org.example.stocksteps.navigation.AppTab.MARKETS
import org.example.stocksteps.navigation.AppTab.PORTFOLIO
import org.example.stocksteps.navigation.AppTab.WATCHLIST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plan §14 invariants I1–I11 and the §9 scenarios as pure transitions (Phase C implements the same contract on each platform). */
class TabNavigationModelTest {
    private val N = TabNavigation
    private fun start(tab: AppTab = HOME) = TabNavigationState.initial(tab)
    private fun TabNavigationState.screens(tab: AppTab) = stack(tab).map { it.screen }

    /** I2–I4 hold for any state. */
    private fun assertWellFormed(s: TabNavigationState) {
        AppTab.entries.forEach { tab ->
            assertEquals(NavigationPolicy.root(tab), s.stack(tab).first().screen, "I2 root of $tab")
            s.stack(tab).drop(1).forEach { assertEquals(tab, it.screen.tab, "I3 ${it.screen} in $tab") }
        }
        s.hops.forEach { assertTrue(it.depth in 2..s.stack(it.tab).size, "I4 $it") }
    }

    /** I1: only [touched] may differ. */
    private fun assertOthersUnchanged(before: TabNavigationState, after: TabNavigationState, vararg touched: AppTab) {
        AppTab.entries.filterNot { it in touched }.forEach { assertEquals(before.stack(it), after.stack(it), "I1 $it changed") }
    }

    @Test
    fun watchlistToCompanyDetailsAndBack() { // §9 #1, I1, I5
        val markets = N.open(N.open(N.selectTab(start(), MARKETS), ScreenId.DISCOVER), ScreenId.COMPANY_DETAILS, "MSFT")
        val s0 = N.selectTab(markets, WATCHLIST)
        val s1 = N.open(s0, ScreenId.COMPANY_DETAILS, "AAPL")
        assertEquals(MARKETS, s1.selected)
        assertEquals(NavEntry(ScreenId.COMPANY_DETAILS, "AAPL"), s1.top)
        assertOthersUnchanged(s0, s1, MARKETS)
        assertEquals(s0.stack(MARKETS) + NavEntry(ScreenId.COMPANY_DETAILS, "AAPL"), s1.stack(MARKETS)) // appended, nothing replaced
        assertWellFormed(s1)
        assertEquals(s0, N.back(s1)) // back to Watchlist; Markets exactly as before
    }

    @Test
    fun nestedHopsUnwindInOrder() { // I7: W → CD (Markets) → Term (Learn)
        val s0 = N.selectTab(start(), WATCHLIST)
        val s2 = N.open(N.open(s0, ScreenId.COMPANY_DETAILS, "AAPL"), ScreenId.LEARN_TERM, "pe-ratio")
        assertEquals(LEARN, s2.selected); assertEquals(2, s2.hops.size); assertWellFormed(s2)
        val s3 = N.back(s2)
        assertEquals(MARKETS, s3.selected); assertEquals(listOf(ScreenId.LEARN), s3.screens(LEARN))
        assertEquals(s0, N.back(s3))
    }

    @Test
    fun inTabPushesAfterAHopPopBeforeReturning() {
        val s0 = N.selectTab(start(), WATCHLIST)
        val s = N.open(N.open(s0, ScreenId.COMPANY_DETAILS, "AAPL"), ScreenId.COMPANY_FINANCIALS, "AAPL")
        val b1 = N.back(s)
        assertEquals(MARKETS, b1.selected); assertEquals(ScreenId.COMPANY_DETAILS, b1.top.screen)
        assertEquals(s0, N.back(b1))
    }

    @Test
    fun repeatedNavigationNeverDuplicatesAndReuseKeepsTheTargetStack() { // I6, rule 2
        val inMarkets = N.open(N.selectTab(start(), MARKETS), ScreenId.COMPANY_DETAILS, "AAPL")
        assertEquals(inMarkets, N.open(inMarkets, ScreenId.COMPANY_DETAILS, "AAPL")) // same tab, same top: nothing new
        val s0 = N.selectTab(inMarkets, HOME)
        val s1 = N.open(s0, ScreenId.COMPANY_DETAILS, "AAPL") // the identical entry is already on top of Markets
        assertEquals(s0.stack(MARKETS), s1.stack(MARKETS)); assertEquals(MARKETS, s1.selected)
        val back = N.back(s1)
        assertEquals(HOME, back.selected); assertEquals(s0.stack(MARKETS), back.stack(MARKETS)) // the reused entry stays (I1)
        assertEquals(s0, back)
        // A different symbol is a new entry.
        assertEquals(3, N.open(inMarkets, ScreenId.COMPANY_DETAILS, "MSFT").stack(MARKETS).size)
    }

    @Test
    fun manualTabSwitchClearsTheTrail() { // I8, D2
        val s1 = N.open(N.selectTab(start(), WATCHLIST), ScreenId.COMPANY_DETAILS, "AAPL")
        val s2 = N.selectTab(N.selectTab(s1, PORTFOLIO), MARKETS)
        assertTrue(s2.hops.isEmpty()); assertEquals(ScreenId.COMPANY_DETAILS, s2.top.screen) // Markets kept its stack
        val s3 = N.back(s2)
        assertEquals(MARKETS, s3.selected); assertEquals(listOf(ScreenId.MARKETS), s3.screens(MARKETS))
    }

    @Test
    fun tabSwitchRestoresStacksUntouched() { // §9 #3
        val s0 = N.open(N.selectTab(start(), MARKETS), ScreenId.DISCOVER)
        val s1 = N.open(N.selectTab(s0, PORTFOLIO), ScreenId.HOLDING_DETAILS, "acc:AAPL")
        val s2 = N.selectTab(s1, MARKETS)
        assertEquals(s0.stack(MARKETS), s2.stack(MARKETS)); assertEquals(ScreenId.DISCOVER, s2.top.screen)
        assertEquals(ScreenId.HOLDING_DETAILS, s2.stack(PORTFOLIO).last().screen)
    }

    @Test
    fun reselectPopsToRootThenScrollsToTop() { // I9, D3, D12, §9 #2 and #7
        val s1 = N.open(N.open(start(), ScreenId.COMPANY_DETAILS, "AAPL"), ScreenId.COMPANY_NEWS, "AAPL")
        val (r1, e1) = N.reselect(s1)
        assertEquals(ReselectEffect.POP_TO_ROOT, e1); assertEquals(listOf(ScreenId.MARKETS), r1.screens(MARKETS)); assertTrue(r1.hops.isEmpty())
        assertOthersUnchanged(s1, r1, MARKETS)
        val (r2, e2) = N.reselect(r1)
        assertEquals(ReselectEffect.SCROLL_TO_TOP, e2); assertEquals(r1, r2)
        assertEquals(r1, N.back(r1)) // Back at a root: the platform decides
    }

    @Test
    fun modalAndGlobalScreensAreNeutral() { // I10, §9 #14 and #15
        val s = N.open(N.selectTab(start(), LEARN), ScreenId.LEARN_TERM, "dividend")
        listOf(ScreenId.SETTINGS, ScreenId.NOTIFICATIONS, ScreenId.COMPANY_SEARCH, ScreenId.AUTH, ScreenId.TRANSACTION_FORM).forEach {
            assertEquals(s, N.open(s, it))
        }
    }

    @Test
    fun openingALandingPageIsATabSwitch() { // rule 6 (Home "See all" → Learn)
        val s = N.open(N.open(start(), ScreenId.DAILY_BRIEF), ScreenId.LEARN)
        assertEquals(LEARN, s.selected); assertTrue(s.hops.isEmpty()); assertEquals(listOf(ScreenId.HOME, ScreenId.DAILY_BRIEF), s.screens(HOME))
    }

    @Test
    fun deepLinkOnColdStartReturnsHome() { // §9 #8 and #9
        val s = N.open(start(), ScreenId.EARNINGS_EVENT, "AAPL:2026-Q3")
        assertEquals(MARKETS, s.selected)
        assertEquals(start(), N.back(s))
    }

    @Test
    fun resetClearsEverything() { // I11, §9 #11
        val s = N.open(N.open(N.selectTab(start(), WATCHLIST), ScreenId.COMPANY_DETAILS, "AAPL"), ScreenId.PRACTICE)
        assertEquals(start(), N.reset())
        assertTrue(N.reset().hops.isEmpty()); assertTrue(s.hops.isNotEmpty())
    }

    @Test
    fun randomWalksKeepInvariants() { // I1–I4 over long deterministic sequences of every operation
        val screens = ScreenId.entries
        var seed = 7L
        fun next(n: Int): Int { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % n).toInt() }
        repeat(20) {
            var s = start()
            repeat(200) {
                val before = s
                when (next(5)) {
                    0, 1 -> { val screen = screens[next(screens.size)]; s = N.open(s, screen, "k${next(3)}")
                        assertOthersUnchanged(before, s, *listOfNotNull(screen.tab).toTypedArray()) }
                    2 -> { s = N.back(s); assertOthersUnchanged(before, s, before.selected) }
                    3 -> { s = N.selectTab(s, AppTab.entries[next(5)]); assertOthersUnchanged(before, s, before.selected) }
                    else -> { s = N.reselect(s).first; assertOthersUnchanged(before, s, before.selected) }
                }
                assertWellFormed(s)
            }
        }
    }
}
