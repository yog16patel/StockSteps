package org.example.stocksteps.portfolio

import kotlin.test.*
import org.example.stocksteps.model.InstrumentRef

class PortfolioEngineTest {
    private val account = PortfolioAccount("account", "Investments", reportingCurrency = PortfolioCurrency.USD)
    private val apple = InstrumentRef("AAPL", "Apple", "NASDAQ", "USD")
    private fun tx(id: String, type: TransactionType, quantity: String = "0", price: String = "0", amount: String = "0", fees: String = "0", date: String = "2026-01-01", instrument: InstrumentRef? = apple) =
        PortfolioTransaction(id, account.id, type, date, PortfolioCurrency.USD, instrument, quantity, price, amount, fees)
    private fun ledger(vararg transactions: PortfolioTransaction) = PortfolioLedger(listOf(account), transactions.toList())

    @Test fun decimalArithmeticIsExactAndPortable() {
        assertEquals("0.3", (Decimal.parse("0.1") + Decimal.parse("0.2")).toString())
        assertEquals("121932631112635269", (Decimal.parse("123456789") * Decimal.parse("987654321")).toString())
        assertEquals("0.33333333", (Decimal.ONE / Decimal.parse("3")).toString())
        assertEquals("0.66666667", (Decimal.parse("2") / Decimal.parse("3")).toString())
        assertEquals("-0.1", (Decimal.parse("0.2") - Decimal.parse("0.3")).toString())
        assertEquals("0.00000001", (Decimal.parse("0.00000001") * Decimal.parse("0.5")).toString())
        assertFailsWith<IllegalArgumentException> { Decimal.parse("NaN") }
        assertFailsWith<IllegalArgumentException> { Decimal.parse("0.000000001") }
        assertEquals("2.68", Decimal.parse("2.675").display())
        assertEquals("-2.68", Decimal.parse("-2.675").display())
        assertEquals("0.00", Decimal.parse("-0.00000001").display())
        assertEquals("2.675", PortfolioPresenter.providerDecimal(2.675))
        assertEquals("0.00001", PortfolioPresenter.providerDecimal(1e-5))
        assertNull(PortfolioPresenter.providerDecimal(Double.NaN))
    }
    @Test fun multipleBuysAndPartialSaleUseMovingAverageIncludingFees() {
        val data = ledger(tx("deposit", TransactionType.CASH_DEPOSIT, amount = "5000", instrument = null),
            tx("buy1", TransactionType.BUY, "10", "100", fees = "10"),
            tx("buy2", TransactionType.BUY, "10", "200", fees = "10"),
            tx("sell", TransactionType.SELL, "5", "200", fees = "5", date = "2026-01-02"))
        val state = PortfolioEngine.replay(data, account.id)
        assertEquals("15", state.holdings.single().quantity)
        assertEquals("2265", state.holdings.single().costBasis)
        assertEquals("240", state.holdings.single().realizedGain)
        assertEquals("2975", state.cash[PortfolioCurrency.USD])
        val value = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-01-02", mapOf("AAPL" to "210"), emptyMap()))
        assertEquals("6125", value.totalValue)
        assertEquals("885", value.unrealizedGain)
    }
    @Test fun existingFractionalPositionDoesNotCreateCashOrEarlierHistory() {
        val data = ledger(tx("opening", TransactionType.OPENING_POSITION, "0.125", "100", date = "2026-02-01"))
        assertTrue(PortfolioEngine.replay(data, account.id).cash.isEmpty())
        assertEquals("12.5", PortfolioEngine.replay(data, account.id).holdings.single().costBasis)
        val observations = listOf("2026-01-01", "2026-02-01").map { PortfolioPrices(it, mapOf("AAPL" to "120"), emptyMap()) }
        assertEquals(listOf("2026-02-01"), PortfolioEngine.history(data, account.id, observations).map { it.date })
    }
    @Test fun closedPositionHasNoRoundingResidueAndRetainsRealizedGain() {
        val data = ledger(tx("open", TransactionType.OPENING_POSITION, "3", "10"), tx("sale", TransactionType.SELL, "3", "20", date = "2026-02-01"))
        val holding = PortfolioEngine.replay(data, account.id).holdings.single()
        assertEquals("0", holding.quantity); assertEquals("0", holding.costBasis); assertEquals("30", holding.realizedGain)
    }
    @Test fun deletingOrMovingOpeningAfterSaleCannotProduceNegativeHoldings() {
        val sell = tx("sale", TransactionType.SELL, "2", "20", date = "2026-02-01")
        assertFailsWith<IllegalArgumentException> { PortfolioEngine.replay(ledger(sell), account.id) }
        assertFailsWith<IllegalArgumentException> { PortfolioEngine.replay(ledger(sell, tx("opening", TransactionType.OPENING_POSITION, "2", "10", date = "2026-03-01")), account.id) }
    }
    @Test fun dividendsReinvestmentSplitAndTransferDoNotInventProfit() {
        val data = ledger(tx("open", TransactionType.OPENING_POSITION, "10", "100"),
            tx("dividend", TransactionType.DIVIDEND, amount = "20", date = "2026-02-01"),
            tx("drip", TransactionType.DIVIDEND_REINVESTMENT, "0.2", "100", date = "2026-02-02"),
            tx("split", TransactionType.STOCK_SPLIT, "2", date = "2026-02-03"),
            tx("out", TransactionType.TRANSFER_OUT, "10.2", date = "2026-02-04"))
        val state = PortfolioEngine.replay(data, account.id)
        assertEquals("10.2", state.holdings.single().quantity)
        assertEquals("510", state.holdings.single().costBasis)
        assertEquals("0", state.holdings.single().realizedGain)
        assertEquals("40", state.holdings.single().dividends)
        assertEquals("20", state.cash[PortfolioCurrency.USD])
        assertTrue(state.externalContributions.isEmpty())
    }
    @Test fun cashFlowsFeesAndAdjustmentsAreDistinctFromInvestmentReturns() {
        val data = ledger(tx("a", TransactionType.CASH_DEPOSIT, amount = "100", instrument = null),
            tx("b", TransactionType.CASH_WITHDRAWAL, amount = "20", instrument = null),
            tx("c", TransactionType.FEE, amount = "5", instrument = null),
            tx("d", TransactionType.CASH_ADJUSTMENT, amount = "-2", instrument = null),
            tx("e", TransactionType.TRANSFER_IN, amount = "10", instrument = null))
        val state = PortfolioEngine.replay(data, account.id)
        assertEquals("83", state.cash[PortfolioCurrency.USD]); assertEquals("80", state.externalContributions[PortfolioCurrency.USD])
        assertTrue(state.realizedGain.isEmpty())
    }
    @Test fun missingFxNeverAssumesOneToOneAndHistoricalValuesUseHistoricalShares() {
        val data = ledger(tx("opening", TransactionType.OPENING_POSITION, "1", "100"), tx("buy", TransactionType.BUY, "1", "100", date = "2026-02-01"))
            .copy(accounts = listOf(account.copy(reportingCurrency = PortfolioCurrency.CAD)))
        val missing = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-01-01", mapOf("AAPL" to "110"), emptyMap()))
        assertNull(missing.totalValue); assertTrue(missing.missing.any { it.startsWith("FX:") })
        val historical = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-01-01", mapOf("AAPL" to "110"), mapOf(PortfolioCurrency.USD to "1.3"), mapOf("2026-01-01" to mapOf(PortfolioCurrency.USD to "1.3"))))
        assertEquals("143", historical.totalValue)
        assertEquals("13", historical.unrealizedGain)
    }
    @Test fun calendarValidationRejectsImpossibleDates() {
        assertFalse(PortfolioEngine.validDate("2026-02-29")); assertTrue(PortfolioEngine.validDate("2024-02-29"))
        assertFalse(PortfolioEngine.validDate("2026-13-01")); assertFalse(PortfolioEngine.validDate("2026-04-31"))
    }

    @Test fun depositsAreNotProfitAndSalesAreNotExternalFlows() {
        val data = ledger(tx("deposit", TransactionType.CASH_DEPOSIT, amount = "1000", date = "2026-02-01", instrument = null),
            tx("buy", TransactionType.BUY, "5", "100", fees = "1", date = "2026-02-01"))
        val daily = PortfolioEngine.daily(data, account.id, PortfolioPrices("2026-01-31", emptyMap(), emptyMap()),
            PortfolioPrices("2026-02-01", mapOf("AAPL" to "110"), emptyMap()))
        assertEquals("49", daily.gain)
        assertEquals("4.9", daily.percent)
        val sold = data.copy(transactions = data.transactions + tx("sale", TransactionType.SELL, "5", "110", fees = "1", date = "2026-02-02"))
        val next = PortfolioEngine.daily(sold, account.id, PortfolioPrices("2026-02-01", mapOf("AAPL" to "110"), emptyMap()),
            PortfolioPrices("2026-02-02", emptyMap(), emptyMap()))
        assertEquals("-1", next.gain)
    }

    @Test fun reportingBasisUsesAcquisitionFxAndCurrentMarketValueUsesCurrentFx() {
        val data = ledger(tx("opening", TransactionType.OPENING_POSITION, "1", "100"))
            .copy(accounts = listOf(account.copy(reportingCurrency = PortfolioCurrency.CAD)))
        val value = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-02-01", mapOf("AAPL" to "100"),
            mapOf(PortfolioCurrency.USD to "1.4"), mapOf("2026-01-01" to mapOf(PortfolioCurrency.USD to "1.3"))))
        assertEquals("140", value.totalValue)
        assertEquals("130", value.investedCost)
        assertEquals("10", value.unrealizedGain)
        val missingBasis = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-02-01", mapOf("AAPL" to "100"), mapOf(PortfolioCurrency.USD to "1.4")))
        assertEquals("140", missingBasis.totalValue)
        assertNull(missingBasis.investedCost)
        assertNull(missingBasis.unrealizedGain)
    }

    @Test fun allEighteenScenariosReplayDeterministicallyWithoutSharedMutation() {
        assertEquals(18, PortfolioFixtureCatalog.ids.size)
        PortfolioFixtureCatalog.ids.forEach { id ->
            val fixture = PortfolioFixtureCatalog.get(id)
            assertEquals(fixture.ledger, PortfolioFixtureCatalog.get(id).ledger)
            fixture.ledger.accounts.forEach { account -> PortfolioEngine.replay(fixture.ledger, account.id); fixture.report(account.id) }
        }
        val partial = PortfolioFixtureCatalog.get("partial-sale")
        val state = PortfolioEngine.replay(partial.ledger, partial.selectedAccount!!)
        assertEquals("7", state.holdings.single().quantity)
        assertEquals("560", state.holdings.single().costBasis)
        assertEquals("44", state.holdings.single().realizedGain)
        val fixture = PortfolioFixtureCatalog.get("fractional-shares")
        assertEquals("12.5", fixture.report(fixture.selectedAccount!!).summary.totalValue)
        val missingFx = PortfolioFixtureCatalog.get("missing-fx")
        assertNull(missingFx.report(missingFx.selectedAccount!!).summary.totalValue)
    }
    @Test fun cashFeesReduceValueWithoutReducingGrossContributionsAndAllocationIncludesCash() {
        val data = ledger(tx("deposit", TransactionType.CASH_DEPOSIT, amount = "100", fees = "2", instrument = null),
            tx("withdraw", TransactionType.CASH_WITHDRAWAL, amount = "10", fees = "1", instrument = null),
            tx("opening", TransactionType.OPENING_POSITION, "1", "10"))
        val state = PortfolioEngine.replay(data, account.id)
        assertEquals("87", state.cash[PortfolioCurrency.USD])
        assertEquals("90", state.externalContributions[PortfolioCurrency.USD])
        val allocation = PortfolioEngine.allocations(state, PortfolioPrices("2026-01-01", mapOf("AAPL" to "13"), emptyMap()), PortfolioCurrency.USD)
        assertEquals("13", allocation.holdings.single().percent)
        assertEquals("100", allocation.currencies.single().percent)
        assertEquals("100", allocation.currencies.single().reportingValue)
        val missing = PortfolioEngine.allocations(state, PortfolioPrices("2026-01-01", emptyMap(), emptyMap()), PortfolioCurrency.USD)
        assertNull(missing.holdings.single().percent)
        assertNull(missing.currencies.single().reportingValue)
    }
    @Test fun proportionalBasisAvoidsIntermediateOverflowAndFxConvertsWholeTrades() {
        assertEquals("50000000000000", Decimal.parse("100000000000000").multiplyDivide(Decimal.parse("50000000"), Decimal.parse("100000000")).toString())
        val data = ledger(tx("opening", TransactionType.OPENING_POSITION, "100000000", "0.00000001"))
            .copy(accounts = listOf(account.copy(reportingCurrency = PortfolioCurrency.CAD)))
        val fx = mapOf("2026-01-01" to mapOf(PortfolioCurrency.USD to "1.4"), "2026-02-01" to mapOf(PortfolioCurrency.USD to "1.3"))
        val opened = PortfolioEngine.value(data, account.id, PortfolioPrices("2026-01-01", mapOf("AAPL" to "0.00000001"), mapOf(PortfolioCurrency.USD to "1.4"), fx))
        assertEquals("1.4", opened.totalValue)
        assertEquals("1.4", opened.investedCost)
        assertEquals("0", opened.unrealizedGain)
        val sold = data.copy(transactions = data.transactions + tx("sale", TransactionType.SELL, "100000000", "0.00000002", date = "2026-02-01"))
        val closed = PortfolioEngine.value(sold, account.id, PortfolioPrices("2026-02-01", emptyMap(), mapOf(PortfolioCurrency.USD to "1.3"), fx))
        assertEquals("2.6", closed.totalValue)
        assertEquals("1.2", closed.realizedGain)
    }
}
