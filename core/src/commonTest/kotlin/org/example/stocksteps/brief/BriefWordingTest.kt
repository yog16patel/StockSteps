package org.example.stocksteps.brief

import kotlin.test.Test
import kotlin.test.assertEquals

/** Phase 5C.1: the StockSteps+ brief insight read "1 of the 1 companies you follow have news …". */
class BriefWordingTest {
    @Test fun oneFollowedCompanyIsASingularSentence() {
        assertEquals("The company you follow has news from the last two days.", BriefWording.recentNews(1, 1))
        assertEquals("The company you follow has no news from the last two days.", BriefWording.recentNews(0, 1))
    }

    @Test fun severalCompaniesAgreeWithTheCount() {
        assertEquals("1 of the 3 companies you follow has news from the last two days.", BriefWording.recentNews(1, 3))
        assertEquals("2 of the 3 companies you follow have news from the last two days.", BriefWording.recentNews(2, 3))
        assertEquals("All 3 companies you follow have news from the last two days.", BriefWording.recentNews(3, 3))
        assertEquals("None of the 3 companies you follow have news from the last two days.", BriefWording.recentNews(0, 3))
    }
}
