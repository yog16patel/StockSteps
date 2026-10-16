package org.example.stocksteps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioSectionTest {
    @Test
    fun aFreshSessionStartsOnMyPortfolio() {
        assertEquals(PortfolioSection.MY_PORTFOLIO, PortfolioSections.DEFAULT)
        assertEquals(listOf("My Portfolio", "Practice"), PortfolioSection.entries.map { it.label })
    }

    @Test
    fun everyPortfolioScreenHasExactlyOneSectionAndNothingElseHasOne() {
        ScreenId.entries.forEach { screen ->
            val section = PortfolioSections.sectionFor(screen)
            if (NavigationPolicy.parentTab(screen) == AppTab.PORTFOLIO) assertTrue(section != null, "$screen needs a section")
            else assertNull(section, "$screen is not a Portfolio screen")
        }
        assertEquals(PortfolioSection.PRACTICE, PortfolioSections.sectionFor(ScreenId.PRACTICE_ORDER)) // Back from an order shows Practice
        assertEquals(PortfolioSection.MY_PORTFOLIO, PortfolioSections.sectionFor(ScreenId.HOLDING_DETAILS)) // Back from a holding shows My Portfolio
    }

    @Test
    fun practiceIsTheLandingSectionNotAPushedScreen() {
        assertTrue(PortfolioSections.isLandingSection(ScreenId.PRACTICE))
        ScreenId.entries.filter { it != ScreenId.PRACTICE }.forEach { assertFalse(PortfolioSections.isLandingSection(it), "$it") }
        // Practice keeps its canonical owner, so any link to it selects Portfolio.
        assertEquals(AppTab.PORTFOLIO, NavigationPolicy.parentTab(ScreenId.PRACTICE))
    }
}
