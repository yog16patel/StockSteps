package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioAllocation
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioEngine
import org.example.stocksteps.portfolio.PortfolioErrorSource
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioLedger
import org.example.stocksteps.portfolio.PortfolioPrices
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portfolio Phase D3.1: the screenshot's 32.62% + 68.43% = 101.05% reproduced on the real engine (cash below zero is in the denominator, by
 * design), negative cash as a supported ledger state, money lines, the allocation note, error titles and the Portfolio tab rule.
 */
class PortfolioPhaseD31Test {
    private val account = PortfolioAccount("acc-1", "D3Test", PortfolioCategory.PERSONAL, PortfolioCurrency.CAD)
    private fun tx(id: String, type: TransactionType, date: String, symbol: String? = null, quantity: String = "0", price: String = "0",
                   gross: String, fees: String = "0", net: String) =
        PortfolioTransaction(id, "acc-1", type, date, PortfolioCurrency.USD, symbol?.let { InstrumentRef(it, null, null, "USD") },
            quantity, price, gross, fees, net)

    // The device-check ledger, server-normalized (PortfolioService.normalize): gross = shares × price, net = gross ± fees.
    private val ledger = PortfolioLedger(listOf(account), listOf(
        tx("dep", TransactionType.CASH_DEPOSIT, "2026-08-05", gross = "1000", net = "1000"),
        tx("open", TransactionType.OPENING_POSITION, "2026-07-01", "TD.TO", "5", "75", gross = "375", net = "375"),
        tx("buy", TransactionType.BUY, "2026-10-13", "AAPL", "10", "105", gross = "1050", fees = "1.5", net = "1051.5"),
    ), 1)
    // Prices that give the screenshot's values: TD.TO USD 1,604.80 and AAPL USD 3,366.70 at USD→CAD 1.35.
    private val prices = PortfolioPrices("2026-10-13", mapOf("TD.TO" to "320.96", "AAPL" to "336.67"), mapOf(PortfolioCurrency.USD to "1.35"))

    @Test
    fun negativeCashIsTheLedgersOwnResultNotAnError() {
        val state = PortfolioEngine.replay(ledger, "acc-1")
        // Deposit 1,000.00 − buy (10 × 105 + 1.50) = −51.50; an opening position records holdings that already existed and uses no cash.
        assertEquals(Decimal.parse("-51.5"), Decimal.parse(state.cash.getValue(PortfolioCurrency.USD)))
    }

    @Test
    fun allocationDividesByHoldingsPlusCashSoHoldingsExceed100WithNegativeCash() {
        val allocations = PortfolioEngine.allocations(PortfolioEngine.replay(ledger, "acc-1"), prices, PortfolioCurrency.CAD)
        fun shown(a: PortfolioAllocation) = PortfolioFormat.amount(a.percent)
        val bySymbol = allocations.holdings.associateBy { it.symbol }
        assertEquals("32.62", shown(bySymbol.getValue("TD.TO"))) // the screenshot
        assertEquals("68.43", shown(bySymbol.getValue("AAPL")))
        val holdings = allocations.holdings.fold(Decimal.ZERO) { sum, a -> sum + Decimal.parse(a.percent!!) }
        assertTrue(holdings > Decimal.parse("100"), "holdings $holdings")
        // The currency groups include cash; together they are exactly the whole account value.
        val all = allocations.currencies.fold(Decimal.ZERO) { sum, c -> sum + Decimal.parse(c.percent!!) }
        assertEquals("100.00", PortfolioFormat.amount(all.toString()))
    }

    @Test
    fun positiveCashMakesHoldingsAddUpToLessThan100() {
        val funded = ledger.copy(transactions = ledger.transactions + tx("dep2", TransactionType.CASH_DEPOSIT, "2026-10-13", gross = "500", net = "500"))
        val allocations = PortfolioEngine.allocations(PortfolioEngine.replay(funded, "acc-1"), prices, PortfolioCurrency.CAD)
        val holdings = allocations.holdings.fold(Decimal.ZERO) { sum, a -> sum + Decimal.parse(a.percent!!) }
        assertTrue(holdings < Decimal.parse("100"), "holdings $holdings")
    }

    @Test
    fun theAllocationNoteExplainsCashWithoutComputingAnything() {
        val allocations = PortfolioEngine.allocations(PortfolioEngine.replay(ledger, "acc-1"), prices, PortfolioCurrency.CAD).holdings
        assertEquals(PortfolioAllocationDisplay.CASH_NEGATIVE_NOTE, PortfolioAllocationDisplay.of(PortfolioUiState(allocations = allocations, cash = listOf("USD -51.5"))).cashNote)
        assertEquals(PortfolioAllocationDisplay.CASH_POSITIVE_NOTE, PortfolioAllocationDisplay.cashNote(PortfolioUiState(allocations = allocations, cash = listOf("USD 168.5"))))
        assertNull(PortfolioAllocationDisplay.cashNote(PortfolioUiState(allocations = allocations, cash = listOf("USD 0"))))
        assertNull(PortfolioAllocationDisplay.cashNote(PortfolioUiState(allocations = allocations)))
        assertNull(PortfolioAllocationDisplay.cashNote(PortfolioUiState(cash = listOf("USD -51.5")))) // no holdings listed: nothing to explain
        // Mixed currencies: any balance below zero is the one to explain.
        assertEquals(PortfolioAllocationDisplay.CASH_NEGATIVE_NOTE, PortfolioAllocationDisplay.cashNote(PortfolioUiState(allocations = allocations, cash = listOf("CAD 20", "USD -1"))))
    }

    @Test
    fun moneyLinesUseTwoDecimalsAndTheSignBeforeTheCurrency() {
        assertEquals(PortfolioPresentation.MoneyLine("USD 168.50", "USD 168.50", false), PortfolioPresentation.moneyLine("USD 168.5"))
        assertEquals(PortfolioPresentation.MoneyLine("-USD 51.50", "minus USD 51.50", true), PortfolioPresentation.moneyLine("USD -51.5"))
        assertEquals("CAD 1,250.00", PortfolioPresentation.moneyLine("CAD 1250.00").text)
        assertEquals("USD 0.13", PortfolioPresentation.moneyLine("USD 0.125").text) // exact decimal, half-up — no floating point
        assertEquals("CAD 12,345,678.90", PortfolioPresentation.moneyLine("CAD 12345678.9").text)
        // Anything that isn't "CODE decimal" is shown as given, never reinterpreted.
        listOf("Unavailable", "USD n/a", "", "US 1.0").forEach { assertEquals(it, PortfolioPresentation.moneyLine(it).text) }
        assertFalse(PortfolioPresentation.moneyLine("Unavailable").negative)
    }

    @Test
    fun errorsAreTitledByTheActionThatFailed() {
        fun state(source: PortfolioErrorSource?, offline: Boolean = false) = PortfolioUiState(error = "Selling more shares than owned.", errorSource = source, offline = offline)
        assertEquals("Couldn't save transaction", PortfolioDisplay.errorTitle(state(PortfolioErrorSource.SAVE_TRANSACTION)))
        assertEquals("Couldn't delete transaction", PortfolioDisplay.errorTitle(state(PortfolioErrorSource.DELETE_TRANSACTION)))
        assertEquals("Couldn't update account", PortfolioDisplay.errorTitle(state(PortfolioErrorSource.ACCOUNT)))
        assertEquals("Couldn't refresh your portfolio", PortfolioDisplay.errorTitle(state(PortfolioErrorSource.REFRESH)))
        assertEquals("Couldn't refresh your portfolio", PortfolioDisplay.errorTitle(state(null)))
        assertEquals(PortfolioDisplay.OFFLINE_TITLE, PortfolioDisplay.errorTitle(state(PortfolioErrorSource.SAVE_TRANSACTION, offline = true)))
        // Retry runs a refresh, so only refresh problems offer it; a rejected save or delete is dismissed instead.
        assertTrue(PortfolioDisplay.errorRetries(state(PortfolioErrorSource.REFRESH)))
        assertTrue(PortfolioDisplay.errorRetries(state(null)))
        assertFalse(PortfolioDisplay.errorRetries(state(PortfolioErrorSource.SAVE_TRANSACTION)))
        assertFalse(PortfolioDisplay.errorRetries(state(PortfolioErrorSource.DELETE_TRANSACTION)))
        assertEquals(PortfolioDisplay.SAVE_TRANSACTION_FAILED_TITLE, PortfolioDisplay.FORM_SAVE_FAILED_TITLE) // the form and the screen say the same
    }

    @Test
    fun everyPortfolioDestinationKeepsThePortfolioTabSelected() {
        assertEquals(setOf(PortfolioRoute::class, PortfolioTransactionsRoute::class, PortfolioTransactionRoute::class, HoldingDetailsRoute::class,
            PortfolioInsightsRoute::class), PortfolioTabRoutes.toSet())
        assertFalse(PortfolioEntryRoute::class in PortfolioTabRoutes) // also opened from Company details on other tabs
        assertFalse(PortfolioSearchRoute::class in PortfolioTabRoutes)
    }
}
