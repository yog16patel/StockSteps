package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase D1.1: per-field guidance from the unchanged engine, type-switch regressions, hidden fields and the security line. */
class PortfolioPhaseD11Test {
    private fun tx(type: TransactionType, symbol: String = "AAPL", quantity: String = "10", price: String = "100", amount: String = "", fees: String = "0",
                   settlement: String = "", fx: String = "", cash: PortfolioCurrency = PortfolioCurrency.USD) =
        PortfolioFormRules.visibleTransaction("tx-1", "acc-1", type, "2026-10-08", PortfolioCurrency.USD, symbol, "Apple Inc.", "NASDAQ", "USD",
            quantity, price, amount, fees, cash, fx, settlement, "")

    private fun check(type: TransactionType, symbol: String = "AAPL", quantity: String = "10", price: String = "100", amount: String = "",
                      fees: String = "0", settlement: String = "") =
        PortfolioFormRules.check(tx(type, symbol, quantity, price, amount, fees, settlement),
            mapOf(PortfolioFormField.SHARES to quantity, PortfolioFormField.PRICE to price, PortfolioFormField.AMOUNT to amount, PortfolioFormField.FEES to fees))

    @Test
    fun aCombinedEngineMessageBecomesOneSentencePerFailingField() {
        val both = check(TransactionType.BUY, symbol = "", quantity = "")
        assertEquals(PortfolioDisplay.FORM_CHOOSE_SECURITY, both.errors[PortfolioFormField.SYMBOL])
        assertEquals(PortfolioDisplay.FORM_POSITIVE_SHARES, both.errors[PortfolioFormField.SHARES])
        val negatives = check(TransactionType.BUY, quantity = "-1", fees = "-2")
        assertEquals(setOf(PortfolioFormField.SHARES, PortfolioFormField.FEES), negatives.errors.keys)
        assertTrue(negatives.errors.values.all { it == PortfolioDisplay.FORM_NOT_NEGATIVE })
    }

    @Test
    fun everyProblemIsReportedInOneSaveAttempt() {
        // Before D1.1 only the engine's first failure was shown; fixing it revealed the next one.
        val result = check(TransactionType.BUY, symbol = "", quantity = "", price = "", settlement = "2026-10-01")
        assertEquals(setOf(PortfolioFormField.SYMBOL, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.SETTLEMENT), result.errors.keys)
        assertEquals("Trade price must be positive.", result.errors[PortfolioFormField.PRICE]) // engine wording kept where it names one field
        assertNull(result.message)
    }

    @Test
    fun theCheckNeverChangesWhatIsSubmitted() {
        val original = tx(TransactionType.BUY, symbol = "", quantity = "", price = "")
        PortfolioFormRules.check(original, emptyMap())
        assertEquals(listOf(null, "0", "0"), listOf(original.instrument, original.quantity, original.unitPrice))
    }

    @Test
    fun switchingTypeDropsRuleErrorsAndKeepsFormatErrorsOnVisibleFields() {
        val sellErrors = mapOf(PortfolioFormField.SHARES to PortfolioDisplay.FORM_POSITIVE_SHARES, PortfolioFormField.PRICE to "Trade price must be positive.",
            PortfolioFormField.FEES to PortfolioDisplay.FORM_NUMBER_INVALID)
        // Sell → Cash deposit: shares and price are hidden; the fees format error describes the typed text, so it stays.
        assertEquals(mapOf(PortfolioFormField.FEES to PortfolioDisplay.FORM_NUMBER_INVALID),
            PortfolioFormRules.retainedErrors(sellErrors, TransactionType.CASH_DEPOSIT, "", false))
        // Sell → Opening position: shares/price still visible but their rule errors came from Sell's rules.
        assertEquals(mapOf(PortfolioFormField.FEES to PortfolioDisplay.FORM_NUMBER_INVALID),
            PortfolioFormRules.retainedErrors(sellErrors, TransactionType.OPENING_POSITION, "AAPL", false))
        // A hidden field's format error is dropped too.
        assertEquals(emptyMap(), PortfolioFormRules.retainedErrors(mapOf(PortfolioFormField.SHARES to PortfolioDisplay.FORM_NUMBER_INVALID),
            TransactionType.DIVIDEND, "", false))
    }

    @Test
    fun hiddenFieldsAreExcludedForEveryTypeTransition() {
        TransactionType.entries.forEach { type ->
            val sent = tx(type, quantity = "7", price = "9", amount = "11", fx = "1.3", cash = PortfolioCurrency.CAD)
            val visible = PortfolioFormRules.visibleFields(type, "AAPL", true).toSet()
            assertEquals(if (PortfolioFormField.SHARES in visible) "7" else "0", sent.quantity, "$type shares")
            assertEquals(if (PortfolioFormField.PRICE in visible) "9" else "0", sent.unitPrice, "$type price")
            assertEquals(if (PortfolioFormField.AMOUNT in visible) "11" else "0", sent.grossAmount, "$type amount")
            assertEquals("1.3", sent.fxRate, "$type FX (cash currency differs, so it is shown)")
        }
        assertNull(tx(TransactionType.BUY, fx = "1.3").fxRate)
    }

    @Test
    fun selectedSecurityLineOnlyFromKnownDetails() {
        assertEquals("Apple Inc. · NASDAQ · USD", PortfolioFormRules.selectedSecurity("AAPL", "Apple Inc.", "NASDAQ", "USD"))
        assertEquals("TSX · CAD", PortfolioFormRules.selectedSecurity("TD.TO", "", "TSX", "CAD"))
        assertNull(PortfolioFormRules.selectedSecurity("AAPL", "", "", "USD")) // typed symbol only: nothing is looked up
        assertNull(PortfolioFormRules.selectedSecurity("", "Apple Inc.", "NASDAQ", "USD"))
    }

    @Test
    fun earlierReturnsNoteOnlyForOpeningPositions() {
        assertEquals(PortfolioDisplay.FORM_INTRO, PortfolioFormRules.dateNote(TransactionType.OPENING_POSITION))
        assertNull(PortfolioFormRules.dateNote(TransactionType.BUY))
    }
}
