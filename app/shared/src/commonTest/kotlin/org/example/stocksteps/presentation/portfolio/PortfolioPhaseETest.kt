package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioErrorSource
import org.example.stocksteps.portfolio.PortfolioUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Portfolio Phase E: accessibility display rules (labels that don't break mid-word, one announcer per error). */
class PortfolioPhaseETest {
    @Test
    fun profitAndLossLabelsNeverBreakAtTheSlash() {
        val joined = PortfolioPresentation.keepTogether("Unrealized P/L")
        assertEquals("Unrealized P⁠/⁠L", joined)
        // Line breaking happens between words only: the label still has exactly one break opportunity, the space.
        assertEquals(listOf("Unrealized", "P⁠/⁠L"), joined.split(' '))
        assertEquals("Realized P⁠/⁠L", PortfolioPresentation.keepTogether("Realized P/L"))
        listOf("Shares", "Market value", "Invested cost").forEach { assertEquals(it, PortfolioPresentation.keepTogether(it)) }
        // The data labels and spoken text stay plain; only the drawn label gets the joiners.
        assertTrue(PortfolioOverviewDisplay.of(PortfolioUiState(), 1).metrics.any { it.label == "Unrealized P/L" })
    }

    @Test
    fun eachErrorHasExactlyOneAnnouncer() {
        fun state(source: PortfolioErrorSource?) = PortfolioUiState(error = "Rejected by the server.", errorSource = source)
        // A rejected save is announced by the form that sent it, so the screen behind the form stays quiet.
        assertFalse(PortfolioDisplay.errorAnnounced(state(PortfolioErrorSource.SAVE_TRANSACTION)))
        // Delete (from a dialog), refresh and account problems are announced by the screen banner.
        assertTrue(PortfolioDisplay.errorAnnounced(state(PortfolioErrorSource.DELETE_TRANSACTION)))
        assertTrue(PortfolioDisplay.errorAnnounced(state(PortfolioErrorSource.REFRESH)))
        assertTrue(PortfolioDisplay.errorAnnounced(state(PortfolioErrorSource.ACCOUNT)))
        assertTrue(PortfolioDisplay.errorAnnounced(state(null)))
        // The banner still shows the action-specific title either way (D3.1).
        assertEquals("Couldn't save transaction", PortfolioDisplay.errorTitle(state(PortfolioErrorSource.SAVE_TRANSACTION)))
    }
}
