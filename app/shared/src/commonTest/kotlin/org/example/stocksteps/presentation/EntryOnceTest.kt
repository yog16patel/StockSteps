package org.example.stocksteps.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 5D.5.6D: fetching work runs once per back-stack entry and key, not again when a tab switch restores the screen. */
class EntryOnceTest {
    @Test fun aKeyRunsOncePerEntryAndANewKeyRunsAgain() {
        val once = EntryOnce()
        assertTrue(once.first("brief-1"))
        assertFalse(once.first("brief-1"))   // the restored screen's effect after a tab switch
        assertTrue(once.first("brief-2"))    // a new key (another brief) still loads
        assertTrue(once.first(null))
        assertFalse(once.first(null))
    }

    @Test fun anotherEntryHasItsOwnFlags() {
        val first = EntryOnce().apply { first(Unit) }
        val second = EntryOnce()               // a new back-stack entry (or the process after death) loads again
        assertFalse(first.first(Unit))
        assertTrue(second.first(Unit))
    }
}
