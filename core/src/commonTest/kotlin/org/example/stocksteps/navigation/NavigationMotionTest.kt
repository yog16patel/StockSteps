package org.example.stocksteps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 8E motion policy: tabs instant, forward and Back mirrored, modals fade, short durations. */
class NavigationMotionTest {
    @Test fun tabsAndLandingPagesSwitchInstantly() {
        assertEquals(NavMotion.NONE, NavigationMotion.selectTab())
        assertEquals(NavMotion.NONE, NavigationMotion.open(ScreenKind.TAB_ROOT))
        assertEquals(NavMotion.NONE, NavigationMotion.reset())
    }

    @Test fun forwardAndBackAreMirrored() {
        assertEquals(NavMotion.PUSH, NavigationMotion.open(ScreenKind.TAB_SCREEN))
        assertEquals(NavMotion.POP, NavigationMotion.back(ScreenKind.TAB_SCREEN))
        assertEquals(NavMotion.POP, NavigationMotion.reselectPopToRoot())
        // Back at a non-Home root shows Home: a tab switch, so instant.
        assertEquals(NavMotion.NONE, NavigationMotion.back(ScreenKind.TAB_ROOT, toLandingOfAnotherTab = true))
    }

    @Test fun modalsFadeEvenWhenTheyCloseThemselves() {
        assertEquals(NavMotion.MODAL_IN, NavigationMotion.open(ScreenKind.MODAL))
        assertEquals(NavMotion.MODAL_IN, NavigationMotion.open(ScreenKind.GLOBAL))
        assertEquals(NavMotion.MODAL_OUT, NavigationMotion.back(ScreenKind.GLOBAL))
        // A form popping itself after a tab tap was recorded: the modal side decides.
        assertEquals(NavMotion.MODAL_OUT, NavigationMotion.between(NavMotion.NONE, fromModal = true, toModal = false))
        assertEquals(NavMotion.MODAL_IN, NavigationMotion.between(NavMotion.PUSH, fromModal = false, toModal = true))
        // Between two tab screens the recorded step stands.
        assertEquals(NavMotion.POP, NavigationMotion.between(NavMotion.POP, fromModal = false, toModal = false))
    }

    @Test fun motionStaysShortAndSubtle() {
        assertTrue(NavigationMotion.SLIDE_MS in 180..240)
        assertTrue(NavigationMotion.FADE_OUT_MS + NavigationMotion.FADE_IN_MS <= NavigationMotion.SLIDE_MS)
        assertTrue(NavigationMotion.MODAL_FADE_MS <= 200)
        assertTrue(NavigationMotion.SLIDE_DP <= 32)
    }
}
