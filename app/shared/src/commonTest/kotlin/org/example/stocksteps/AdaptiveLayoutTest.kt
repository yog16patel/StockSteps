package org.example.stocksteps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveLayoutTest {
    @Test fun compactAndExpandedWindows() {
        assertNull(paneLayout(839f, 900f, 1f, 0f, 0f, null).detail)
        assertNotNull(paneLayout(840f, 900f, 1f, 0f, 0f, null).detail)
        assertNull(paneLayout(1200f, 1800f, 2f, 0f, 0f, null).detail)
    }

    @Test fun verticalHingeUsesWindowCoordinates() {
        val layout = paneLayout(1000f, 700f, 1f, 20f, 30f, WindowHinge(510f, 0f, 530f, 800f, true))
        assertTrue(layout.search.x + layout.search.width < 490f)
        assertTrue(assertNotNull(layout.detail).x > 510f)
    }

    @Test fun tabletopPanesAvoidHorizontalFold() {
        val layout = paneLayout(700f, 900f, 1f, 0f, 0f, WindowHinge(0f, 440f, 700f, 460f, false))
        assertTrue(layout.search.height < 440f)
        assertTrue(assertNotNull(layout.detail).y > 460f)
    }

    @Test fun smallPaneFallsBackToLargerSide() {
        val layout = paneLayout(700f, 500f, 1f, 0f, 0f, WindowHinge(100f, 0f, 120f, 500f, true))
        assertNull(layout.detail)
        assertEquals(132f, layout.search.x)
    }

    @Test fun hingeOutsideContentDoesNotSplit() {
        assertNull(paneLayout(400f, 700f, 1f, 500f, 0f, WindowHinge(100f, 0f, 120f, 700f, true)).detail)
    }
}
