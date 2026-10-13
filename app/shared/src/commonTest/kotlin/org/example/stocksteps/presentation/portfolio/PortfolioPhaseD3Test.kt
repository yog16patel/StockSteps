package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioActionKind
import org.example.stocksteps.portfolio.PortfolioActionResult
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioFixtureCatalog
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Portfolio Phase D3: transaction details and row text (recorded values only), and one-time action feedback. */
class PortfolioPhaseD3Test {
    private val account = PortfolioAccount("acc-1", "D2Test", PortfolioCategory.PERSONAL, PortfolioCurrency.CAD)
    private val apple = InstrumentRef("AAPL", "Apple Inc.", "NASDAQ", "USD")

    // Server-normalized rows (PortfolioService.normalize): gross = shares × price for trades; net = gross ± fees.
    private val buy = PortfolioTransaction("t-buy", "acc-1", TransactionType.BUY, "2026-10-13", PortfolioCurrency.USD, apple,
        quantity = "10", unitPrice = "100", grossAmount = "1000", fees = "1.5", netAmount = "1001.5")
    private val deposit = PortfolioTransaction("t-dep", "acc-1", TransactionType.CASH_DEPOSIT, "2026-08-05", PortfolioCurrency.USD,
        grossAmount = "1000", netAmount = "1000")

    private fun labels(tx: PortfolioTransaction) = PortfolioTransactionDetailsDisplay.of(tx, listOf(account)).rows.map { it.label }
    private fun value(tx: PortfolioTransaction, label: String) = PortfolioTransactionDetailsDisplay.of(tx, listOf(account)).rows.firstOrNull { it.label == label }?.value

    @Test
    fun aBuyShowsItsRecordedTradeFieldsWithCurrencies() {
        val details = PortfolioTransactionDetailsDisplay.of(buy, listOf(account))
        assertEquals("Buy", details.title)
        assertEquals("AAPL · Apple Inc.", details.security)
        assertEquals("USD 1,000.00", details.amount)
        assertEquals(listOf("Transaction type", "Trade date", "Account", "Security symbol", "Company name", "Exchange", "Shares", "Price per share",
            "Amount before fees", "Fees", "Net amount", "Currency"), details.rows.map { it.label })
        assertEquals("USD 100.00", value(buy, "Price per share"))
        assertEquals("USD 1.50", value(buy, "Fees"))
        assertEquals("USD 1,001.50", value(buy, "Net amount")) // the server's net, shown — never recomputed here
        assertEquals("D2Test · CAD\nPersonal · reports in CAD", value(buy, "Account"))
    }

    @Test
    fun aCashDepositHasNoShareOrPriceRows() {
        val rows = labels(deposit)
        assertFalse("Shares" in rows || "Price per share" in rows || "Security symbol" in rows, "$rows")
        assertEquals("USD 1,000.00", value(deposit, "Amount before fees"))
        assertEquals(PortfolioTransactionIcon.CASH, PortfolioTransactionDetailsDisplay.icon(deposit))
        assertNull(PortfolioTransactionDetailsDisplay.row(deposit).unitPrice)
    }

    @Test
    fun openingPositionsUseTheirOwnLabels() {
        val opening = buy.copy(type = TransactionType.OPENING_POSITION)
        assertTrue("Effective date" in labels(opening) && "Average cost per share" in labels(opening))
        assertFalse("Trade date" in labels(opening))
    }

    @Test
    fun optionalFieldsAppearOnlyWhenRecorded() {
        assertFalse(listOf("Settlement date", "Cash currency", "FX rate").any { it in labels(buy) })
        assertNull(PortfolioTransactionDetailsDisplay.of(buy, listOf(account)).notes)
        val full = buy.copy(settlementDate = "2026-10-15", cashCurrency = PortfolioCurrency.CAD, fxRate = "1.37", notes = "Monthly plan")
        assertEquals("Oct 15, 2026", value(full, "Settlement date"))
        assertEquals("CAD", value(full, "Cash currency"))
        assertEquals("1.37", value(full, "FX rate"))
        assertEquals("Monthly plan", PortfolioTransactionDetailsDisplay.of(full, listOf(account)).notes)
        assertFalse("Account" in labels(buy.copy(accountId = "gone"))) // an unknown account has no row, never a guessed name
        val blankNotes = buy.copy(notes = "  ")
        assertNull(PortfolioTransactionDetailsDisplay.of(blankNotes, listOf(account)).notes)
    }

    @Test
    fun unrecordedAmountsAreNeverShownAsZero() {
        // Sample fixtures skip server normalization: gross/net stay "0" on the opening position.
        val sample = PortfolioFixtureCatalog.get("multiple-buys").ledger.transactions
        sample.forEach { tx ->
            // Fees are a recorded value (the form saves 0 when none were paid), so "USD 0.00" fees is data, not a missing amount.
            val texts = PortfolioTransactionDetailsDisplay.of(tx, emptyList()).rows.filter { it.label != "Fees" }.map { it.value } +
                listOfNotNull(PortfolioTransactionDetailsDisplay.row(tx).amount, PortfolioTransactionDetailsDisplay.amount(tx))
            texts.forEach { text ->
                assertFalse(Regex("^[A-Z]{3} 0\\.00$").matches(text), "${tx.type}: $text")
            }
        }
        val opening = sample.first { it.type == TransactionType.OPENING_POSITION }
        assertNull(PortfolioTransactionDetailsDisplay.amount(opening))
        assertFalse("Amount before fees" in labels(opening) || "Net amount" in labels(opening))
        assertEquals("USD 80.00 per share", PortfolioTransactionDetailsDisplay.row(opening).unitPrice)
    }

    @Test
    fun rowTextIsRecordedValuesInReadingOrder() {
        val row = PortfolioTransactionDetailsDisplay.row(buy)
        assertEquals(PortfolioTransactionIcon.SECURITY, row.icon)
        assertEquals("AAPL · 10 shares", row.security)
        assertEquals("USD 100.00 per share", row.unitPrice)
        assertEquals("USD 1,000.00", row.amount)
        assertEquals("Buy, AAPL · 10 shares, Oct 13, 2026, USD 100.00 per share, amount USD 1,000.00", row.spoken)
        assertEquals(PortfolioTransactionIcon.DIVIDEND, PortfolioTransactionDetailsDisplay.icon(deposit.copy(type = TransactionType.DIVIDEND)))
        assertEquals(PortfolioTransactionIcon.FEE, PortfolioTransactionDetailsDisplay.icon(deposit.copy(type = TransactionType.FEE)))
        assertEquals(PortfolioTransactionIcon.SECURITY, PortfolioTransactionDetailsDisplay.icon(deposit.copy(type = TransactionType.DIVIDEND, instrument = apple)))
    }

    @Test
    fun noRawEnumNamesInDetails() {
        TransactionType.entries.forEach { type ->
            val details = PortfolioTransactionDetailsDisplay.of(buy.copy(type = type), listOf(account))
            (details.rows.flatMap { listOf(it.label, it.value) } + details.title).forEach {
                assertFalse(Regex("[A-Z]{2,}_[A-Z]").containsMatchIn(it), "$type: $it")
            }
        }
    }

    @Test
    fun feedbackShowsEachConfirmedResultOnce() {
        assertEquals("Transaction added", PortfolioActionFeedback.message(PortfolioActionKind.ADDED))
        assertEquals("Transaction updated", PortfolioActionFeedback.message(PortfolioActionKind.UPDATED))
        assertEquals("Transaction deleted", PortfolioActionFeedback.message(PortfolioActionKind.DELETED))
        val tracker = PortfolioFeedbackTracker()
        val first = PortfolioActionResult(1, PortfolioActionKind.ADDED, "t-1")
        assertNull(tracker.take(null))
        assertEquals("Transaction added", tracker.take(first))
        assertNull(tracker.take(first)) // recomposition, rotation or another screen: not again
        val deleted = PortfolioActionResult(2, PortfolioActionKind.DELETED, "t-1")
        tracker.skip(deleted) // finished where Portfolio isn't on screen
        assertNull(tracker.take(deleted))
        assertEquals("Transaction updated", tracker.take(PortfolioActionResult(3, PortfolioActionKind.UPDATED, "t-1")))
    }

    @Test
    fun screensOnlyReactToResultsNewerThanWhenTheyAppeared() {
        val old = PortfolioActionResult(4, PortfolioActionKind.UPDATED, "t-1")
        val since = PortfolioActionFeedback.baseline(old)
        assertEquals(0, PortfolioActionFeedback.baseline(null))
        assertFalse(PortfolioActionFeedback.isNew(old, since))
        assertTrue(PortfolioActionFeedback.isNew(old.copy(id = 5), since))
        assertFalse(PortfolioActionFeedback.isNew(null, since))
    }
}
