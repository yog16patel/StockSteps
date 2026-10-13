package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase D1: form sections, hidden-field clearing, field-level checks from the existing rules, dates and read-only samples. */
class PortfolioPhaseD1Test {
    private fun tx(type: TransactionType, symbol: String = "AAPL", quantity: String = "10", price: String = "100", amount: String = "", fees: String = "0",
                   date: String = "2026-10-08", settlement: String = "", fx: String = "", cash: PortfolioCurrency = PortfolioCurrency.USD) =
        PortfolioFormRules.visibleTransaction("tx-1", "acc-1", type, date, PortfolioCurrency.USD, symbol, "", "", "USD", quantity, price, amount, fees,
            cash, fx, settlement, "")

    private fun check(type: TransactionType, quantity: String = "10", price: String = "100", amount: String = "", fees: String = "0", symbol: String = "AAPL",
                      date: String = "2026-10-08", settlement: String = "") =
        PortfolioFormRules.check(tx(type, symbol, quantity, price, amount, fees, date, settlement),
            mapOf(PortfolioFormField.SHARES to quantity, PortfolioFormField.PRICE to price, PortfolioFormField.AMOUNT to amount, PortfolioFormField.FEES to fees))

    @Test
    fun sectionsFollowTheTypeAndKeepOptionalFieldsUnderMore() {
        assertEquals(listOf(PortfolioFormField.TRANSACTION_TYPE, PortfolioFormField.DATE, PortfolioFormField.ACCOUNT, PortfolioFormField.SYMBOL),
            PortfolioFormRules.sectionFields(PortfolioFormSection.DETAILS, TransactionType.BUY, "AAPL", false))
        // E.2: the trade currency opens "Amounts" (it was under "More options").
        assertEquals(listOf(PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.FEES),
            PortfolioFormRules.sectionFields(PortfolioFormSection.AMOUNTS, TransactionType.BUY, "AAPL", false))
        assertEquals(listOf(PortfolioFormField.TRADE_CURRENCY, PortfolioFormField.AMOUNT, PortfolioFormField.FEES),
            PortfolioFormRules.sectionFields(PortfolioFormSection.AMOUNTS, TransactionType.CASH_DEPOSIT, "", false))
        assertFalse(PortfolioFormField.FX_RATE in PortfolioFormRules.sectionFields(PortfolioFormSection.MORE, TransactionType.BUY, "AAPL", false))
        assertTrue(PortfolioFormField.FX_RATE in PortfolioFormRules.sectionFields(PortfolioFormSection.MORE, TransactionType.BUY, "AAPL", true))
        // Every field belongs to exactly one section.
        assertEquals(PortfolioFormField.entries.toSet(), PortfolioFormSection.entries.flatMap { s ->
            PortfolioFormRules.redesignOrder.filter { PortfolioFormRules.section(it) == s } }.toSet())
    }

    @Test
    fun moneyLabelsNameTheirCurrency() {
        assertEquals("Price per share (USD)", PortfolioFormRules.label(PortfolioFormField.PRICE, TransactionType.BUY, "USD", "USD"))
        assertEquals("Amount (CAD)", PortfolioFormRules.label(PortfolioFormField.AMOUNT, TransactionType.CASH_DEPOSIT, "CAD", "CAD"))
        assertEquals("Fees (USD, optional)", PortfolioFormRules.label(PortfolioFormField.FEES, TransactionType.BUY, "USD", "USD"))
        assertEquals("Effective date", PortfolioFormRules.label(PortfolioFormField.DATE, TransactionType.OPENING_POSITION, "USD", "USD"))
    }

    @Test
    fun switchingTypeNeverSubmitsHiddenValues() {
        // Typed as a Buy, then switched to a cash deposit: shares and price are hidden, so they are not sent.
        val deposit = tx(TransactionType.CASH_DEPOSIT, symbol = "", quantity = "10", price = "100", amount = "250.00")
        assertEquals(listOf("0", "0", "250.00"), listOf(deposit.quantity, deposit.unitPrice, deposit.grossAmount))
        val buy = tx(TransactionType.BUY, amount = "999")
        assertEquals(listOf("10", "100", "0"), listOf(buy.quantity, buy.unitPrice, buy.grossAmount))
        assertNull(tx(TransactionType.BUY, fx = "1.37").fxRate) // FX only while the cash currency differs
        assertEquals("1.37", tx(TransactionType.BUY, fx = "1.37", cash = PortfolioCurrency.CAD).fxRate)
    }

    @Test
    fun requiredValuesAreReportedNextToTheirField() {
        assertEquals(mapOf(PortfolioFormField.SYMBOL to PortfolioDisplay.FORM_CHOOSE_SECURITY), check(TransactionType.BUY, symbol = "").errors)
        assertEquals(mapOf(PortfolioFormField.SHARES to PortfolioDisplay.FORM_POSITIVE_SHARES), check(TransactionType.BUY, quantity = "").errors)
        assertEquals(mapOf(PortfolioFormField.PRICE to "Trade price must be positive."), check(TransactionType.BUY, price = "").errors)
        assertEquals(mapOf(PortfolioFormField.AMOUNT to "Amount must be positive."), check(TransactionType.CASH_DEPOSIT, symbol = "", amount = "").errors)
        assertEquals(mapOf(PortfolioFormField.SETTLEMENT to "Settlement cannot precede the trade."), check(TransactionType.BUY, settlement = "2026-10-01").errors)
        assertTrue(check(TransactionType.BUY).valid)
    }

    @Test
    fun invalidNumbersAreExplainedAndNeverRewritten() {
        val commas = check(TransactionType.BUY, quantity = "1,000", price = "$5")
        assertEquals(setOf(PortfolioFormField.SHARES, PortfolioFormField.PRICE), commas.errors.keys)
        assertEquals(PortfolioDisplay.FORM_NUMBER_INVALID, commas.errors[PortfolioFormField.SHARES])
        assertEquals(setOf(PortfolioFormField.SHARES), check(TransactionType.BUY, quantity = "0.123456789").errors.keys) // > 8 decimals (ledger rule)
        // Up to 8 decimals pass and are submitted exactly as typed.
        assertTrue(check(TransactionType.BUY, quantity = "0.12345678", price = "150.10").valid)
        assertEquals("0.12345678", tx(TransactionType.BUY, quantity = "0.12345678").quantity)
        assertEquals(setOf(PortfolioFormField.FEES), check(TransactionType.BUY, fees = "-1").errors.keys)
        assertEquals("0", tx(TransactionType.BUY, fees = "").fees) // blank fees mean none, as the iOS bridge always sent
    }

    @Test
    fun datesRoundTripAsPlainDaysWithoutShifting() {
        assertEquals(0L, PortfolioFormDates.epochDay("1970-01-01"))
        listOf("1900-01-01", "2024-02-29", "2026-10-08", "2099-12-31").forEach { day ->
            assertEquals(day, PortfolioFormDates.date(PortfolioFormDates.epochDay(day)!!))
        }
        assertNull(PortfolioFormDates.epochDay("2026-02-30"))
        assertEquals("Oct 8, 2026", PortfolioFormDates.display("2026-10-08"))
        assertEquals(mapOf(PortfolioFormField.DATE to PortfolioDisplay.FORM_DATE_INVALID), check(TransactionType.BUY, date = "").errors)
    }

    @Test
    fun moreOptionsOpenWhenTheyHoldSomethingOrAnError() {
        assertFalse(PortfolioFormRules.moreOptionsOpen(emptyMap(), emptySet(), "USD", "USD"))
        assertTrue(PortfolioFormRules.moreOptionsOpen(emptyMap(), emptySet(), "USD", "CAD"))
        assertTrue(PortfolioFormRules.moreOptionsOpen(mapOf(PortfolioFormField.NOTES to "gift"), emptySet(), "USD", "USD"))
        assertTrue(PortfolioFormRules.moreOptionsOpen(emptyMap(), setOf(PortfolioFormField.SETTLEMENT), "USD", "USD"))
    }

    @Test
    fun sampleScenariosAreReadOnly() {
        assertTrue(PortfolioFormRules.readOnly(PortfolioUiState(mockScenario = "mixed-currencies")))
        assertFalse(PortfolioFormRules.readOnly(PortfolioUiState()))
        assertFalse(PortfolioFormRules.readOnly(null))
    }
}
