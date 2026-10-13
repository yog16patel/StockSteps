package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.data.userdata.UserDataErrorKind
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioErrorSource
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portfolio Phase E.2: the shorter account-sheet title, the trade currency shown with the amounts (with a note when it isn't the account's
 * reporting currency), and the status banner chosen by failure kind — "Session expired" + "Sign in again", "Couldn't refresh…" + Retry for a
 * server error, "Offline" + Retry when there was no answer — with Add unavailable only for an ended session.
 */
class PortfolioPhaseE2Test {
    private val cad = PortfolioAccount("acc-1", "E1 iOS", PortfolioCategory.PERSONAL, PortfolioCurrency.CAD)

    @Test fun accountSheetTitleIsTheShortForm() {
        assertEquals("New investment account", PortfolioDisplay.ACCOUNT_CREATE_TITLE)
        assertTrue(PortfolioDisplay.ACCOUNT_CREATE_TITLE.length < "Create investment account".length)
    }

    @Test fun tradeCurrencyOpensTheAmountsForEveryType() {
        for (type in TransactionType.entries) for (symbol in listOf("", "TD.TO")) {
            val amounts = PortfolioFormRules.sectionFields(PortfolioFormSection.AMOUNTS, type, symbol, cashDiffers = false)
            assertEquals(PortfolioFormField.TRADE_CURRENCY, amounts.first(), "$type '$symbol'")
            assertFalse(PortfolioFormField.TRADE_CURRENCY in PortfolioFormRules.sectionFields(PortfolioFormSection.MORE, type, symbol, cashDiffers = false))
            // "More options" still has its optional fields (cash currency, settlement, notes) for every type.
            assertTrue(PortfolioFormRules.sectionFields(PortfolioFormSection.MORE, type, symbol, cashDiffers = false)
                .containsAll(listOf(PortfolioFormField.CASH_CURRENCY, PortfolioFormField.SETTLEMENT, PortfolioFormField.NOTES)))
        }
        assertFalse(PortfolioDisplay.FORM_MORE_HINT.startsWith("Currencies"))
    }

    @Test fun currencyNoteOnlyWhenTheTradeCurrencyDiffersFromTheAccount() {
        assertEquals("This account reports in CAD. These amounts are in USD; change Trade currency if you paid in CAD. " +
            "They're converted to CAD with dated exchange rates.", PortfolioFormRules.currencyNote("USD", cad))
        assertNull(PortfolioFormRules.currencyNote("CAD", cad))
        assertNull(PortfolioFormRules.currencyNote("USD", null))
        assertEquals("This account reports in USD. These amounts are in CAD; change Trade currency if you paid in USD. " +
            "They're converted to USD with dated exchange rates.", PortfolioFormRules.currencyNote("CAD", cad.copy(reportingCurrency = PortfolioCurrency.USD)))
    }

    private fun state(kind: UserDataErrorKind?, offline: Boolean = true, source: PortfolioErrorSource? = PortfolioErrorSource.REFRESH,
                      error: String? = "Problem.", expired: Boolean = kind == UserDataErrorKind.SESSION) =
        PortfolioUiState(error = error, errorSource = source, offline = offline, errorKind = kind, sessionExpired = expired, signedIn = true)

    @Test fun anEndedSessionSaysSessionExpiredAndOffersSignInAgain() {
        val expired = state(UserDataErrorKind.SESSION).copy(error = "Your session has ended. Sign in again to continue.")
        assertEquals("Session expired", PortfolioDisplay.errorTitle(expired))
        assertEquals("Your session has ended. Sign in again to continue. Showing your last saved portfolio.", PortfolioDisplay.errorMessage(expired))
        assertEquals(PortfolioErrorAction.SIGN_IN, PortfolioDisplay.errorAction(expired))
        assertEquals("Sign in again", PortfolioDisplay.errorActionLabel(PortfolioDisplay.errorAction(expired)))
        assertFalse(PortfolioDisplay.errorRetries(expired))
        assertEquals("Sign in again to add or change transactions.", PortfolioDisplay.addDisabledReason(expired))
        // Nothing cached: the message isn't followed by "Showing your last saved portfolio."
        assertEquals("Problem.", PortfolioDisplay.errorMessage(state(UserDataErrorKind.SESSION, offline = false)))
    }

    @Test fun aServerErrorIsCouldNotRefreshWithRetry() {
        val server = state(UserDataErrorKind.SERVER)
        assertEquals("Couldn't refresh your portfolio", PortfolioDisplay.errorTitle(server))
        assertEquals("Problem. Showing your last saved portfolio.", PortfolioDisplay.errorMessage(server))
        assertEquals(PortfolioErrorAction.RETRY, PortfolioDisplay.errorAction(server))
        assertNull(PortfolioDisplay.addDisabledReason(server))
    }

    @Test fun noAnswerStaysOfflineWithRetryAndAddAvailable() {
        val network = state(UserDataErrorKind.NETWORK)
        assertEquals(PortfolioDisplay.OFFLINE_TITLE, PortfolioDisplay.errorTitle(network))
        assertEquals("Problem.", PortfolioDisplay.errorMessage(network))
        assertEquals(PortfolioErrorAction.RETRY, PortfolioDisplay.errorAction(network))
        assertEquals("Retry", PortfolioDisplay.errorActionLabel(PortfolioErrorAction.RETRY))
        assertNull(PortfolioDisplay.addDisabledReason(network))
        // Cached data without an error: no action, the existing "Values may be out of date."
        val cached = state(null, error = null)
        assertEquals(PortfolioErrorAction.NONE, PortfolioDisplay.errorAction(cached))
        assertNull(PortfolioDisplay.errorActionLabel(PortfolioErrorAction.NONE))
        assertEquals(PortfolioDisplay.OUT_OF_DATE, PortfolioDisplay.errorMessage(cached))
        assertEquals(PortfolioDisplay.OFFLINE_TITLE, PortfolioDisplay.errorTitle(cached))
    }

    @Test fun rejectedChangesKeepTheirTitleAndDismissWhateverTheKind() {
        for (kind in listOf(null, UserDataErrorKind.SESSION, UserDataErrorKind.SERVER, UserDataErrorKind.NETWORK)) {
            val rejected = state(kind, offline = false, source = PortfolioErrorSource.SAVE_TRANSACTION, error = "Selling more shares than owned.")
            assertEquals("Couldn't save transaction", PortfolioDisplay.errorTitle(rejected), "$kind")
            assertEquals(PortfolioErrorAction.DISMISS, PortfolioDisplay.errorAction(rejected), "$kind")
            assertEquals("Selling more shares than owned.", PortfolioDisplay.errorMessage(rejected), "$kind")
        }
        // A save that failed because the session ended still makes Add unavailable afterwards.
        assertEquals(PortfolioDisplay.SESSION_ADD_DISABLED,
            PortfolioDisplay.addDisabledReason(state(UserDataErrorKind.SESSION, source = PortfolioErrorSource.SAVE_TRANSACTION)))
    }
}
