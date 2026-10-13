package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.PortfolioAccount
import org.example.stocksteps.portfolio.PortfolioCategory
import org.example.stocksteps.portfolio.PortfolioCurrency
import org.example.stocksteps.portfolio.PortfolioEngine
import org.example.stocksteps.portfolio.TransactionType
import kotlin.test.Test
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.PortfolioTransaction
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Finding A-F1 (accounts chosen and submitted by id) and the Phase A shared form rules (A2, A5, A7). */
class PortfolioFormRulesTest {
    private val first = PortfolioAccount("acc-1", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
    private val second = PortfolioAccount("acc-2", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
    private val twins = listOf(first, second)

    /** What the Android form does on submit: resolve the saved choice, then build the transaction with that id. */
    private fun submitted(choice: String?, accounts: List<PortfolioAccount> = twins, default: String? = "acc-1"): String? {
        val accountId = PortfolioFormRules.resolveAccountId(choice, accounts, default) ?: return null
        if (accountId.isEmpty()) return null
        val transaction = PortfolioFormRules.transaction("tx-1", accountId, TransactionType.BUY, "2026-10-09", PortfolioCurrency.USD,
            "AAPL", "", "", "USD", "2", "150.25", "", "1.00", PortfolioCurrency.USD, "", "", "")
        PortfolioEngine.validate(transaction)
        return transaction.accountId
    }

    @Test
    fun duplicateNamesGetDistinctLabelsAndKeepTheirIds() {
        val options = PortfolioFormRules.accountOptions(twins)
        assertEquals(listOf("acc-1", "acc-2"), options.map { it.id })
        assertEquals(2, options.map { it.label }.toSet().size)
        assertEquals("TFSA · CAD · TFSA (1 of 2)", options[0].label)
        assertEquals("TFSA · CAD · TFSA (2 of 2)", options[1].label)
    }

    @Test
    fun sameNameDifferentCategoryIsDisambiguatedByCategoryOnly() {
        val options = PortfolioFormRules.accountOptions(listOf(first, second.copy(category = PortfolioCategory.RRSP)))
        assertEquals(listOf("TFSA · CAD · TFSA", "TFSA · CAD · RRSP"), options.map { it.label })
    }

    @Test
    fun selectingTheFirstAccountSubmitsItsId() = assertEquals("acc-1", submitted("acc-1"))

    @Test
    fun selectingTheSecondSameNamedAccountSubmitsItsId() = assertEquals("acc-2", submitted("acc-2"))

    @Test
    fun switchingBetweenAccountsFollowsTheLatestChoice() {
        var choice: String? = null
        assertEquals("acc-1", submitted(choice))
        choice = "acc-2"
        assertEquals("acc-2", submitted(choice))
        choice = "acc-1"
        assertEquals("acc-1", submitted(choice))
        choice = "acc-2"
        assertEquals("acc-2", submitted(choice, default = "acc-1"))
    }

    @Test
    fun restoredChoiceIsTheSameId() {
        // rememberSaveable restores the saved String id; resolving it again must give the same account, not the dashboard default.
        val saved: String? = "acc-2"
        val restored: String? = saved.toString()
        assertEquals("acc-2", PortfolioFormRules.resolveAccountId(restored, twins, default = "acc-1"))
        assertEquals("acc-2", submitted(restored, default = "acc-1"))
    }

    @Test
    fun deletedAccountIsNeverReplacedSilently() {
        assertEquals("", PortfolioFormRules.resolveAccountId("acc-2", listOf(first), default = "acc-1"))
        assertNull(submitted("acc-2", accounts = listOf(first)))
        assertEquals("", PortfolioFormRules.resolveAccountId("", twins, default = "acc-1"))
    }

    @Test
    fun noChoiceUsesTheActiveDashboardAccountOnly() {
        assertEquals("acc-2", PortfolioFormRules.resolveAccountId(null, twins, default = "acc-2"))
        assertNull(PortfolioFormRules.resolveAccountId(null, twins, default = "gone"))
        assertNull(PortfolioFormRules.resolveAccountId(null, listOf(first, second.copy(archived = true)), default = "acc-2"))
        assertNull(PortfolioFormRules.resolveAccountId(null, emptyList(), default = null))
    }

    @Test
    fun archivedAccountOfAnEditedTransactionStaysSelectable() {
        val archived = second.copy(archived = true)
        assertEquals(listOf("acc-1"), PortfolioFormRules.accountOptions(listOf(first, archived)).map { it.id })
        val editing = PortfolioFormRules.accountOptions(listOf(first, archived), currentId = "acc-2")
        assertEquals(listOf("acc-1", "acc-2"), editing.map { it.id })
        assertEquals("TFSA · reports in CAD · archived", editing[1].detail)
        assertEquals("acc-2", PortfolioFormRules.resolveAccountId("acc-2", listOf(first, archived), default = "acc-1"))
    }

    @Test
    fun uniquelyNamedAccountsKeepTheirPlainLabels() {
        val personal = PortfolioAccount("p", "Personal", PortfolioCategory.PERSONAL, PortfolioCurrency.CAD)
        val usd = PortfolioAccount("u", "US trading", PortfolioCategory.NON_REGISTERED, PortfolioCurrency.USD)
        val options = PortfolioFormRules.accountOptions(listOf(personal, usd))
        assertEquals(listOf("Personal · CAD", "US trading · USD"), options.map { it.label })
        assertEquals("Non-registered · reports in USD", options[1].detail)
        assertEquals("u", submitted("u", accounts = listOf(personal, usd), default = "p"))
    }

    @Test
    fun builtTransactionKeepsTheExistingFieldDefaults() {
        val tx = PortfolioFormRules.transaction("tx-9", "acc-2", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD,
            "", "ignored", "ignored", "CAD", "", "", "500.10", "0", PortfolioCurrency.CAD, "", "", "")
        assertEquals("acc-2", tx.accountId)
        assertNull(tx.instrument)
        assertEquals(listOf("0", "0", "500.10", "0"), listOf(tx.quantity, tx.unitPrice, tx.grossAmount, tx.fees))
        assertNull(tx.notes); assertNull(tx.fxRate); assertNull(tx.settlementDate)
    }

    // ---- A7: fields per transaction type ----

    private val shareFields = listOf(PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE, PortfolioFormField.SHARES, PortfolioFormField.PRICE)

    @Test
    fun everyTransactionTypeHasItsFieldSet() {
        TransactionType.entries.forEach { type ->
            val blank = PortfolioFormRules.visibleFields(type, "", cashDiffers = false)
            val named = PortfolioFormRules.visibleFields(type, "AAPL", cashDiffers = false)
            // Exhaustive: a new ledger type fails to compile here until its form fields are decided.
            when (type) {
                TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT,
                TransactionType.STOCK_SPLIT -> {
                    assertTrue(blank.containsAll(shareFields), "$type"); assertFalse(PortfolioFormField.AMOUNT in blank, "$type")
                    assertEquals(blank, named, "$type")
                }
                TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> {
                    // Cash transfer without a security, share transfer with one (as PortfolioEngine replays it).
                    assertTrue(PortfolioFormField.AMOUNT in blank && shareFields.none { it in blank }, "$type")
                    assertTrue(named.containsAll(shareFields) && PortfolioFormField.AMOUNT !in named, "$type")
                }
                TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.DIVIDEND, TransactionType.FEE,
                TransactionType.CASH_ADJUSTMENT -> {
                    assertTrue(PortfolioFormField.AMOUNT in blank && shareFields.none { it in blank }, "$type")
                    // A named security adds its optional name / exchange, never shares or price.
                    assertTrue(PortfolioFormField.COMPANY_NAME in named && PortfolioFormField.SHARES !in named, "$type")
                }
            }
            listOf(blank, named).forEach { fields ->
                assertTrue(fields.containsAll(listOf(PortfolioFormField.ACCOUNT, PortfolioFormField.TRANSACTION_TYPE, PortfolioFormField.TRADE_CURRENCY,
                    PortfolioFormField.SYMBOL, PortfolioFormField.FEES, PortfolioFormField.DATE, PortfolioFormField.CASH_CURRENCY,
                    PortfolioFormField.SETTLEMENT, PortfolioFormField.NOTES)), "$type")
                assertFalse(PortfolioFormField.FX_RATE in fields, "$type")
                assertEquals(PortfolioFormField.entries.filter { it in fields }, fields, "on-screen order for $type")
            }
            assertTrue(PortfolioFormField.FX_RATE in PortfolioFormRules.visibleFields(type, "", cashDiffers = true), "$type")
        }
    }

    @Test
    fun shareFieldsMatchTheEngineValidation() {
        // The form asks for shares exactly when PortfolioEngine.validate requires a positive quantity — no new rule.
        TransactionType.entries.forEach { type ->
            listOf("", "AAPL").forEach { symbol ->
                val tx = PortfolioTransaction("tx", "acc-1", type, "2026-10-09", PortfolioCurrency.USD,
                    instrument = symbol.takeIf { it.isNotEmpty() }?.let { InstrumentRef(it) }, quantity = "0", unitPrice = "1", grossAmount = "1")
                val message = runCatching { PortfolioEngine.validate(tx) }.exceptionOrNull()?.message
                assertEquals(PortfolioFormRules.movesShares(type, symbol), message == "Choose a security and a positive share quantity.", "$type '$symbol'")
            }
        }
    }

    @Test
    fun labelsAreReadableAndTypeSpecific() {
        TransactionType.entries.forEach { type ->
            PortfolioFormField.entries.forEach { field ->
                val label = PortfolioFormRules.label(field, type, "USD", "CAD")
                assertTrue(label.isNotBlank() && '_' !in label && label != field.name, "$field / $type")
            }
        }
        assertEquals("Average cost per share (USD)", PortfolioFormRules.label(PortfolioFormField.PRICE, TransactionType.OPENING_POSITION, "USD", "USD"))
        assertEquals("Price per share (USD)", PortfolioFormRules.label(PortfolioFormField.PRICE, TransactionType.BUY, "USD", "USD"))
        assertEquals("Split ratio: new / old", PortfolioFormRules.label(PortfolioFormField.SHARES, TransactionType.STOCK_SPLIT, "USD", "USD"))
        assertEquals("Security symbol", PortfolioFormRules.label(PortfolioFormField.SYMBOL, TransactionType.SELL, "USD", "USD"))
        assertEquals("Security symbol (optional for cash)", PortfolioFormRules.label(PortfolioFormField.SYMBOL, TransactionType.CASH_DEPOSIT, "CAD", "CAD"))
        assertEquals("Trade-date FX: one USD in CAD", PortfolioFormRules.label(PortfolioFormField.FX_RATE, TransactionType.BUY, "USD", "CAD"))
    }

    @Test
    fun typePickerOffersEveryTypeOnceWithTheEverydayOnesFirst() {
        assertEquals(listOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION), PortfolioFormRules.typeOptions.take(3))
        assertEquals(TransactionType.entries.toSet(), PortfolioFormRules.typeOptions.toSet())
        assertEquals(TransactionType.entries.size, PortfolioFormRules.typeOptions.size)
        assertEquals(PortfolioFormRules.typeOptions, PortfolioFormRules.primaryTypes + PortfolioFormRules.otherTypes)
        // Readable picker text (A2), enum names stay the stored values.
        assertEquals(listOf("Buy", "Sell", "Opening position"), PortfolioFormRules.primaryTypes.map(PortfolioPresentation::transactionLabel))
        assertEquals(TransactionType.DIVIDEND_REINVESTMENT, PortfolioFormRules.type("DIVIDEND_REINVESTMENT"))
        assertNull(PortfolioFormRules.type("Dividend reinvestment"))
        assertEquals(listOf("TFSA", "RRSP", "FHSA", "Non-registered", "Personal", "Other"), PortfolioFormRules.categoryOptions.map(PortfolioPresentation::categoryLabel))
    }

    @Test
    fun decimalKeyboardOnlyForNumberFields() {
        assertEquals(setOf(PortfolioFormField.SHARES, PortfolioFormField.PRICE, PortfolioFormField.AMOUNT, PortfolioFormField.FEES, PortfolioFormField.FX_RATE),
            PortfolioFormField.entries.filter(PortfolioFormRules::decimal).toSet())
    }

    @Test
    fun redesignOrderCoversEveryFieldOnceWithAdvancedLast() {
        val order = PortfolioFormRules.redesignOrder
        assertEquals(PortfolioFormField.entries.toSet(), order.toSet())
        assertEquals(PortfolioFormField.entries.size, order.size)
        assertTrue(order.dropWhile { !it.advanced }.all { it.advanced })
    }

    @Test
    fun enteredNumbersAreSubmittedExactlyAsTyped() {
        val tx = PortfolioFormRules.transaction("tx", "acc-1", TransactionType.BUY, "2026-10-09", PortfolioCurrency.USD, "AAPL", "", "", "USD",
            "0.123456789", "150.10", "", "0.50", PortfolioCurrency.CAD, "1.3725", "", "")
        assertEquals(listOf("0.123456789", "150.10", "0.50", "1.3725"), listOf(tx.quantity, tx.unitPrice, tx.fees, tx.fxRate))
    }
}
