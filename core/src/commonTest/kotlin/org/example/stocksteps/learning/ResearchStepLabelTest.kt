package org.example.stocksteps.learning

import kotlin.test.*

/** Phase 5C: step rows are read as one sentence on Android and iOS, without "?." after the question. */
class ResearchStepLabelTest {
    @Test fun questionKeepsItsOwnPunctuation() {
        assertEquals("Step 1 of 5: What does Microsoft do? Not started", ResearchStep.accessibilityLabel(1, "What does Microsoft do?", "Not started"))
    }

    @Test fun textWithoutPunctuationGetsAPeriod() {
        assertEquals("Step 5 of 5: Valuation basics. Completed", ResearchStep.accessibilityLabel(5, "Valuation basics", "Completed"))
    }
}
