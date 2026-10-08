package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.*
import kotlin.math.abs
import kotlin.test.*

class PortfolioAnalyticsEngineTest {
    private val account = PortfolioAccount("acct", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
    private val td = InstrumentRef("TD.TO", "TD", "TSX", "CAD")
    private val enb = InstrumentRef("ENB.TO", "Enbridge", "TSX", "CAD")
    private val aapl = InstrumentRef("AAPL", "Apple", "NASDAQ", "USD")
    private val xiu = InstrumentRef("XIU.TO", "iShares TSX 60", "TSX", "CAD")
    private var sequence = 0L
    private val all = AnalyticsPeriod.ALL

    private fun tx(type: TransactionType, date: String, instrument: InstrumentRef? = null, quantity: String = "0", price: String = "0", amount: String = "0", fees: String = "0") =
        PortfolioTransaction("t${++sequence}", account.id, type, date, if (instrument?.currency == "USD" || (instrument == null && amount.startsWith("USD:"))) PortfolioCurrency.USD else PortfolioCurrency.CAD,
            instrument, quantity, price, amount.removePrefix("USD:"), fees, sequence = sequence)

    private fun inputs(
        vararg transactions: PortfolioTransaction,
        closes: Map<String, Map<String, String>> = mapOf("TD.TO" to mapOf("2026-01-05" to "100", "2026-01-06" to "110")),
        current: Map<String, String> = mapOf("TD.TO" to "120"),
        fx: Map<String, String> = emptyMap(),
        currentFx: String? = null,
        metadata: Map<String, SecurityMetadata> = mapOf("TD.TO" to SecurityMetadata("TD", "Financial Services", AssetClass.STOCK)),
        today: String = "2026-01-07"
    ) = AnalyticsInputs(
        PortfolioLedger(listOf(account), transactions.toList(), 3), account.id, today, closes,
        fx.mapValues { mapOf(PortfolioCurrency.USD to it.value) },
        PortfolioPrices(today, current, currentFx?.let { mapOf(PortfolioCurrency.USD to it) } ?: emptyMap()),
        metadata
    )

    private fun d(value: String?) = Decimal.parse(assertNotNull(value))
    private fun assertClose(expected: String, actual: String?, tolerance: String = "0.0001") =
        assertTrue((d(actual) - Decimal.parse(expected)).let { if (it < Decimal.ZERO) -it else it } <= Decimal.parse(tolerance), "expected $expected, was $actual")

    private val deposit = { date: String, amount: String -> tx(TransactionType.CASH_DEPOSIT, date, amount = amount) }

    // ---------- Performance ----------

    @Test fun singleHoldingReturnMatchesPriceChange() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100")), all)
        assertEquals(Availability.AVAILABLE, perf.availability)
        assertClose("20", perf.timeWeightedReturn)
        assertEquals("200", perf.investmentGain)
        assertEquals("1000", perf.netExternalFlows)
        assertEquals("100", perf.portfolioIndex.first())
    }

    @Test fun multipleHoldingsReturnIsValueWeighted() {
        val closes = mapOf("TD.TO" to mapOf("2026-01-05" to "100", "2026-01-06" to "110"), "ENB.TO" to mapOf("2026-01-05" to "50", "2026-01-06" to "50"))
        val perf = PortfolioAnalyticsEngine.performance(inputs(deposit("2026-01-05", "2000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"),
            tx(TransactionType.BUY, "2026-01-05", enb, "20", "50"), closes = closes, current = mapOf("TD.TO" to "120", "ENB.TO" to "45")), all)
        // 1000 → 1200 and 1000 → 900: 2000 → 2100.
        assertClose("5", perf.timeWeightedReturn)
        assertEquals("100", perf.investmentGain)
    }

    @Test fun depositsAreNotProfit() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(
            deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"),
            deposit("2026-01-06", "550"), tx(TransactionType.BUY, "2026-01-06", td, "5", "110")), all)
        assertClose("20", perf.timeWeightedReturn) // same as without the second deposit
        assertEquals("1550", perf.netExternalFlows)
        assertEquals("250", perf.investmentGain) // 1800 − 1550
    }

    @Test fun withdrawalsAreNotLosses() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(
            deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"),
            tx(TransactionType.SELL, "2026-01-06", td, "5", "110"), tx(TransactionType.CASH_WITHDRAWAL, "2026-01-06", amount = "550")), all)
        assertClose("20", perf.timeWeightedReturn)
        assertEquals("550", perf.withdrawals)
        assertEquals("150", perf.investmentGain) // 600 − (1000 − 550)
    }

    @Test fun fullSaleLocksInReturnAndContribution() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.SELL, "2026-01-06", td, "10", "110"), current = emptyMap())
        val perf = PortfolioAnalyticsEngine.performance(data, all)
        assertClose("10", perf.timeWeightedReturn)
        val contributors = PortfolioAnalyticsEngine.contributors(data, all)
        assertEquals("100", contributors.positive.single().contribution)
        assertEquals("0", contributors.other)
    }

    @Test fun partialSaleKeepsRemainingPosition() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.SELL, "2026-01-06", td, "4", "110"))
        assertClose("16", PortfolioAnalyticsEngine.performance(data, all).timeWeightedReturn) // 1100/1000 × (6×120 + 440)/1100
        assertEquals("6", PortfolioEngine.replay(data.ledger, account.id).holdings.single().quantity)
    }

    @Test fun dividendsCountAsReturnAndAreTotalledOnce() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.DIVIDEND, "2026-01-06", td, amount = "10"))
        val perf = PortfolioAnalyticsEngine.performance(data, all)
        assertEquals("210", perf.investmentGain)
        assertEquals("10", perf.dividends)
        assertEquals("10", PortfolioAnalyticsEngine.dividends(data, all).total)
        assertEquals("210", PortfolioAnalyticsEngine.contributors(data, all).positive.single().contribution)
    }

    @Test fun dividendReinvestmentIsIncomeNotNewMoney() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.DIVIDEND_REINVESTMENT, "2026-01-06", td, "0.1", "110"))
        val perf = PortfolioAnalyticsEngine.performance(data, all)
        assertEquals("1000", perf.netExternalFlows)
        assertEquals("212", perf.investmentGain) // 10.1 × 120 − 1000
        val dividends = PortfolioAnalyticsEngine.dividends(data, all)
        assertEquals("11", dividends.total)
        assertEquals("11", dividends.reinvested)
    }

    @Test fun feesReduceGain() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(deposit("2026-01-05", "1005"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100", fees = "5")), all)
        assertEquals("195", perf.investmentGain)
        assertTrue(d(perf.timeWeightedReturn) < Decimal.parse("20"))
    }

    @Test fun fractionalSharesAreExact() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(deposit("2026-01-05", "12.5"), tx(TransactionType.BUY, "2026-01-05", td, "0.125", "100")), all)
        assertEquals("2.5", perf.investmentGain)
        assertClose("20", perf.timeWeightedReturn)
    }

    private fun usdInputs(fx: Map<String, String>, currentFx: String? = "1.5") = inputs(
        tx(TransactionType.CASH_DEPOSIT, "2026-01-05", amount = "USD:1000"), tx(TransactionType.BUY, "2026-01-05", aapl, "10", "100"),
        closes = mapOf("AAPL" to mapOf("2026-01-05" to "100", "2026-01-06" to "100")), current = mapOf("AAPL" to "100"),
        fx = fx, currentFx = currentFx, metadata = mapOf("AAPL" to SecurityMetadata("Apple", "Technology", AssetClass.STOCK))
    )

    @Test fun historicalFxConvertsEachDateAndIsolatesCurrencyEffect() {
        val data = usdInputs(mapOf("2026-01-05" to "1.3", "2026-01-06" to "1.4"))
        val perf = PortfolioAnalyticsEngine.performance(data, all)
        assertEquals("1300", perf.netExternalFlows) // converted at the deposit date's rate
        assertEquals("200", perf.investmentGain) // price unchanged: 1500 − 1300 is all currency
        val row = PortfolioAnalyticsEngine.contributors(data, all).positive.single()
        assertEquals("0", row.localPart)
        assertEquals("200", row.fxPart)
        assertEquals("200", PortfolioAnalyticsEngine.currency(data, PortfolioAnalyticsEngine.contributors(data, all)).fxEffect)
    }

    @Test fun missingFxHistoryIsUnavailableNotTodaysRate() {
        val perf = PortfolioAnalyticsEngine.performance(usdInputs(emptyMap()), all)
        assertEquals(Availability.UNAVAILABLE, perf.availability)
        assertNull(perf.timeWeightedReturn)
        assertTrue(perf.notes.single().contains("2026-01-05"))
    }

    @Test fun staleFxBeyondCarryWindowIsMissing() {
        val perf = PortfolioAnalyticsEngine.performance(usdInputs(mapOf("2025-12-20" to "1.3")), all)
        assertEquals(Availability.UNAVAILABLE, perf.availability)
    }

    @Test fun insufficientHistoryDisablesLongPeriods() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"))
        val periods = PortfolioAnalyticsEngine.availablePeriods(data.ledger, account.id, data.today)
        assertEquals(listOf(AnalyticsPeriod.ONE_DAY, AnalyticsPeriod.ALL), periods)
        val year = PortfolioAnalyticsEngine.performance(data, AnalyticsPeriod.ONE_YEAR)
        assertEquals(Availability.UNAVAILABLE, year.availability)
        assertNull(year.timeWeightedReturn)
    }

    @Test fun periodSelectionStartsAtThePeriodBoundary() {
        val perf = PortfolioAnalyticsEngine.performance(inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100")), AnalyticsPeriod.ONE_DAY)
        assertEquals("2026-01-06", perf.startDate)
        assertEquals("1100", perf.startValue)
        assertEquals("0", perf.netExternalFlows)
        assertClose("9.0909", perf.timeWeightedReturn)
        assertNull(perf.moneyWeightedReturn) // too short to annualize
    }

    @Test fun irregularCashFlowsSeparateTwrFromXirr() {
        val data = AnalyticsFixtureCatalog.get("deposits").analytics(AnalyticsPeriod.ONE_YEAR).performance!!
        assertNotNull(data.moneyWeightedReturn)
        assertNotEquals(data.timeWeightedReturn, data.moneyWeightedReturn)
    }

    // ---------- XIRR ----------

    @Test fun xirrConvergesOnKnownValues() {
        val simple = Xirr.solve(listOf(DatedFlow("2025-01-01", -1000.0), DatedFlow("2026-01-01", 1100.0)))
        assertTrue(abs((simple as XirrResult.Rate).value - 0.1) < 1e-6)
        // Reference example with irregular dates (spreadsheet XIRR = 0.373362535).
        val irregular = Xirr.solve(listOf(DatedFlow("2008-01-01", -10000.0), DatedFlow("2008-03-01", 2750.0), DatedFlow("2008-10-30", 4250.0),
            DatedFlow("2009-02-15", 3250.0), DatedFlow("2009-04-01", 2750.0)))
        assertTrue(abs((irregular as XirrResult.Rate).value - 0.373362535) < 1e-6)
    }

    @Test fun xirrRefusesUndefinedOrAmbiguousRates() {
        assertIs<XirrResult.Undefined>(Xirr.solve(listOf(DatedFlow("2025-01-01", -1000.0))))
        assertIs<XirrResult.Undefined>(Xirr.solve(listOf(DatedFlow("2025-01-01", -1000.0), DatedFlow("2026-01-01", -10.0))))
        assertIs<XirrResult.Undefined>(Xirr.solve(listOf(DatedFlow("2025-01-01", -1000.0), DatedFlow("2025-01-01", 900.0))))
        // −100, +230, −132 has two valid rates (10% and 20%): none is shown.
        val ambiguous = Xirr.solve(listOf(DatedFlow("2024-01-01", -100.0), DatedFlow("2025-01-01", 230.0), DatedFlow("2026-01-01", -132.0)))
        assertTrue((ambiguous as XirrResult.Undefined).reason.contains("more than one"))
    }

    // ---------- Benchmark ----------

    private fun benchmarkInputs(levels: Map<String, String>, info: BenchmarkInfo = BenchmarkCatalog.info(BenchmarkId.TSX), fx: Map<String, String> = emptyMap(), currentFx: String? = null) =
        inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), fx = fx, currentFx = currentFx)
            .copy(benchmark = BenchmarkSeries(info, levels))

    @Test fun benchmarkIsNormalizedToOneHundredOnTheSameDates() {
        val data = benchmarkInputs(mapOf("2026-01-02" to "200", "2026-01-05" to "200", "2026-01-06" to "210", "2026-01-07" to "220"))
        val perf = PortfolioAnalyticsEngine.performance(data, all)
        val comparison = assertNotNull(PortfolioAnalyticsEngine.benchmark(data, perf))
        assertEquals(perf.indexDates.size, comparison.benchmarkIndex.size)
        assertEquals("100", comparison.benchmarkIndex.first())
        assertClose("10", comparison.benchmarkReturn)
        assertClose("10", comparison.difference) // 20 − 10
    }

    @Test fun benchmarkCarriesForwardAcrossDifferentHolidays() {
        val comparison = PortfolioAnalyticsEngine.benchmark(benchmarkInputs(mapOf("2026-01-02" to "200", "2026-01-06" to "220")),
            PortfolioAnalyticsEngine.performance(benchmarkInputs(emptyMap()), all))!!
        assertEquals(Availability.AVAILABLE, comparison.availability)
        assertClose("10", comparison.benchmarkReturn) // 2026-01-07 uses the 01-06 close
    }

    @Test fun foreignBenchmarkIsConvertedAtDatedFx() {
        val info = BenchmarkCatalog.info(BenchmarkId.SP500)
        val data = benchmarkInputs(mapOf("2026-01-02" to "100", "2026-01-07" to "100"), info,
            fx = mapOf("2026-01-02" to "1.3"), currentFx = "1.43")
        val comparison = PortfolioAnalyticsEngine.benchmark(data, PortfolioAnalyticsEngine.performance(data, all))!!
        assertClose("10", comparison.benchmarkReturn) // flat in USD, +10% in CAD
        assertEquals(PortfolioCurrency.CAD, comparison.currency)
        assertTrue(comparison.notes.any { it.contains("converted to CAD") })
    }

    @Test fun priceReturnBasisIsLabelled() {
        val data = benchmarkInputs(mapOf("2026-01-02" to "200", "2026-01-07" to "220"))
        val comparison = PortfolioAnalyticsEngine.benchmark(data, PortfolioAnalyticsEngine.performance(data, all))!!
        assertEquals(ReturnBasis.PRICE_RETURN, comparison.benchmark.basis)
        assertTrue(comparison.notes.first().contains("price-return"))
    }

    @Test fun missingBenchmarkObservationsAreGapsOrUnavailableNeverZero() {
        val data = benchmarkInputs(mapOf("2025-11-01" to "200"))
        val comparison = PortfolioAnalyticsEngine.benchmark(data, PortfolioAnalyticsEngine.performance(data, all))!!
        assertEquals(Availability.UNAVAILABLE, comparison.availability)
        assertNull(comparison.benchmarkReturn)
        val missing = AnalyticsFixtureCatalog.get("missing-benchmark").analytics()
        assertEquals(Availability.UNAVAILABLE, missing.benchmark!!.availability)
        assertNull(missing.benchmark!!.benchmarkReturn)
    }

    @Test fun benchmarkComparisonUsesCashFlowAdjustedReturn() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"),
            deposit("2026-01-06", "550"), tx(TransactionType.BUY, "2026-01-06", td, "5", "110"))
            .copy(benchmark = BenchmarkSeries(BenchmarkCatalog.info(BenchmarkId.TSX), mapOf("2026-01-02" to "100", "2026-01-07" to "120")))
        val comparison = PortfolioAnalyticsEngine.benchmark(data, PortfolioAnalyticsEngine.performance(data, all))!!
        assertClose("0", comparison.difference) // the deposit doesn't inflate the portfolio's side
    }

    // ---------- Allocation and concentration ----------

    private fun allocationInputs() = inputs(
        deposit("2026-01-05", "2500"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.BUY, "2026-01-05", enb, "10", "50"),
        tx(TransactionType.BUY, "2026-01-05", xiu, "10", "50"),
        closes = emptyMap(), current = mapOf("TD.TO" to "100", "ENB.TO" to "50", "XIU.TO" to "50"),
        metadata = mapOf("TD.TO" to SecurityMetadata("TD", "Financial Services", AssetClass.STOCK), "ENB.TO" to SecurityMetadata("Enbridge", null, AssetClass.STOCK),
            "XIU.TO" to SecurityMetadata("iShares", null, AssetClass.ETF))
    )

    @Test fun allocationPercentagesIncludeCashAndSumToOneHundred() {
        val allocation = PortfolioAnalyticsEngine.allocation(allocationInputs())
        assertEquals("2500", allocation.longAssets)
        assertEquals(mapOf("TD.TO" to "40", "cash" to "20", "ENB.TO" to "20", "XIU.TO" to "20"), allocation.byHolding.associate { it.key to it.percent })
        for (breakdown in listOf(allocation.byHolding, allocation.bySector, allocation.byAssetClass, allocation.byCurrency)) {
            assertClose("100", breakdown.fold(Decimal.ZERO) { sum, slice -> sum + Decimal.parse(slice.percent) }.toString(), "0.000001")
        }
    }

    @Test fun sectorsEtfsAndMissingMetadataAreNeverGuessed() {
        val allocation = PortfolioAnalyticsEngine.allocation(allocationInputs())
        val sectors = allocation.bySector.associate { it.key to it.percent }
        assertEquals("40", sectors["Financial Services"])
        assertEquals("20", sectors["etf"])
        assertEquals("20", sectors["unclassified"])
        assertEquals(setOf("stock", "etf", "cash"), allocation.byAssetClass.map { it.key }.toSet())
        assertEquals("50", allocation.classifiedShare) // TD only, of 2000 in holdings
    }

    @Test fun borrowedCashIsSeparateFromPercentages() {
        val data = inputs(deposit("2026-01-05", "500"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), closes = emptyMap(), current = mapOf("TD.TO" to "100"))
        val allocation = PortfolioAnalyticsEngine.allocation(data)
        assertEquals("-500", allocation.borrowedCash)
        assertEquals("100", allocation.byHolding.single().percent)
    }

    @Test fun missingQuoteHidesPercentagesInsteadOfRenormalizing() {
        val data = allocationInputs().let { it.copy(current = it.current.copy(prices = it.current.prices - "ENB.TO")) }
        val allocation = PortfolioAnalyticsEngine.allocation(data)
        assertEquals(Availability.PARTIAL, allocation.availability)
        assertTrue(allocation.byHolding.isEmpty())
        assertEquals(Availability.PARTIAL, PortfolioAnalyticsEngine.concentration(data).availability)
    }

    @Test fun currencyExposureIsDenominationBased() {
        val data = usdInputs(mapOf("2026-01-05" to "1.3"), "1.5")
        val exposure = PortfolioAnalyticsEngine.currency(data, null)
        assertEquals("100", exposure.foreignShare)
        assertEquals("USD-holdings", exposure.slices.single().key)
        assertTrue(exposure.notes.first().contains("denomination"))
    }

    @Test fun concentrationMetrics() {
        val concentration = PortfolioAnalyticsEngine.concentration(allocationInputs())
        assertEquals(WeightedName("TD.TO", "40"), concentration.largestHolding)
        assertNull(concentration.topThree) // only three holdings: "top three" would be everything
        assertEquals("0.375", concentration.hhi) // 0.5² + 0.25² + 0.25²
        assertEquals("2.66666667", concentration.effectiveHoldings)
        assertEquals(WeightedName("Financial Services", "40"), concentration.largestSector)

        val diversified = AnalyticsFixtureCatalog.get("diversified").analytics().concentration
        assertEquals(8, diversified.holdingsCount)
        assertTrue(d(diversified.topThree) < d(diversified.topFive))
        assertTrue(d(diversified.largestHolding!!.percent) <= d(diversified.topThree))
        assertClose((Decimal.ONE / d(diversified.hhi)).toString(), diversified.effectiveHoldings)
    }

    @Test fun emptyPortfolioIsUnavailableEverywhere() {
        val analytics = AnalyticsFixtureCatalog.get("new-portfolio").analytics()
        assertEquals(Availability.UNAVAILABLE, analytics.concentration.availability)
        assertEquals(Availability.UNAVAILABLE, analytics.allocation.availability)
        assertEquals(Availability.UNAVAILABLE, analytics.performance!!.availability)
        assertTrue(analytics.periods.isEmpty())
        assertTrue(analytics.insights.isEmpty())
    }

    // ---------- Attribution ----------

    @Test fun contributorsRankAndReconcileWithGain() {
        for (id in listOf("multiple-holdings", "mixed-currency", "partial-sales", "dividends", "withdrawals")) {
            val analytics = AnalyticsFixtureCatalog.get(id).analytics()
            val contributors = analytics.contributors!!
            val rows = contributors.positive + contributors.negative
            val total = rows.fold(d(contributors.other)) { sum, row -> sum + d(row.contribution) }
            assertClose(analytics.performance!!.investmentGain!!, total.toString(), "0.0001")
            rows.forEach { assertClose(it.contribution!!, (d(it.localPart) + d(it.fxPart)).toString(), "0") }
            assertEquals(contributors.positive, contributors.positive.sortedByDescending { d(it.contribution) })
        }
        val concentrated = AnalyticsFixtureCatalog.get("concentrated").analytics().contributors!!
        assertEquals("SHOP.TO", concentrated.negative.first().symbol)
        assertEquals("TD.TO", concentrated.positive.first().symbol)
    }

    @Test fun contributionCountsOnlyThePeriodNotLifetimeGains() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"))
        val day = PortfolioAnalyticsEngine.contributors(data, AnalyticsPeriod.ONE_DAY).positive.single()
        assertEquals("100", day.contribution) // 1100 → 1200, not the 200 lifetime gain
    }

    @Test fun transactionDuringPeriodIsInvestedNotGain() {
        val data = inputs(deposit("2026-01-05", "1550"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), tx(TransactionType.BUY, "2026-01-06", td, "5", "110"))
        assertEquals("250", PortfolioAnalyticsEngine.contributors(data, all).positive.single().contribution)
    }

    @Test fun missingAttributionDataIsListedNotEstimated() {
        val data = inputs(deposit("2026-01-05", "1000"), tx(TransactionType.BUY, "2026-01-05", td, "10", "100"), closes = emptyMap())
        val contributors = PortfolioAnalyticsEngine.contributors(data, all)
        assertEquals(Availability.UNAVAILABLE, contributors.availability)
        assertTrue(contributors.positive.isEmpty())
    }

    // ---------- Fixtures ----------

    @Test fun everyFixtureIsInternallyConsistent() {
        assertEquals(20, AnalyticsFixtureCatalog.ids.size)
        for (id in AnalyticsFixtureCatalog.ids) {
            val fixture = AnalyticsFixtureCatalog.get(id)
            for (period in fixture.analytics().periods) {
                val analytics = fixture.analytics(period)
                val perf = analytics.performance ?: continue
                if (perf.availability != Availability.AVAILABLE) continue
                assertEquals(d(perf.investmentGain), d(perf.endValue) - d(perf.startValue) - d(perf.netExternalFlows), "$id $period gain")
                assertEquals(d(perf.netExternalFlows), d(perf.deposits) - d(perf.withdrawals), "$id $period flows")
                assertEquals(perf.indexDates.size, perf.portfolioIndex.size)
                assertEquals(perf.indexDates, perf.indexDates.sorted())
                assertTrue(perf.indexDates.size <= PortfolioAnalyticsEngine.MAX_POINTS + 20)
            }
            assertEquals(fixture.analytics(), fixture.analytics(), "$id is deterministic")
        }
    }

    @Test fun benchmarkFixturesOutperformAndUnderperform() {
        assertTrue(d(AnalyticsFixtureCatalog.get("outperforming").analytics().benchmark!!.difference) > Decimal.ZERO)
        assertTrue(d(AnalyticsFixtureCatalog.get("underperforming").analytics().benchmark!!.difference) < Decimal.ZERO)
    }

    @Test fun partialFailureAndMissingFxFixturesStayHonest() {
        val failure = AnalyticsFixtureCatalog.get("partial-failure").analytics()
        assertEquals(Availability.PARTIAL, failure.allocation.availability)
        assertEquals(Availability.UNAVAILABLE, failure.performance!!.availability)
        assertEquals("data.missing", failure.insights.first().id)
        val fx = AnalyticsFixtureCatalog.get("missing-fx").analytics()
        assertEquals(Availability.UNAVAILABLE, fx.performance!!.availability)
        assertEquals(Availability.AVAILABLE, fx.allocation.availability) // current FX is published
    }

    // ---------- Entitlement shaping ----------

    @Test fun freeTierWithholdsPlusSectionsButKeepsOwnRecords() {
        for (id in listOf("free", "expired")) {
            val analytics = AnalyticsFixtureCatalog.get(id).analytics()
            assertEquals(SubscriptionTier.FREE, analytics.tier)
            assertNull(analytics.performance); assertNull(analytics.benchmark); assertNull(analytics.contributors)
            assertTrue(analytics.allocation.bySector.isEmpty())
            assertNull(analytics.concentration.hhi)
            assertNull(analytics.currency.fxEffect)
            assertTrue("performance" in analytics.locked)
            // Basic holdings view remains.
            assertTrue(analytics.allocation.byHolding.isNotEmpty())
            assertNotNull(analytics.concentration.largestHolding)
            assertNotNull(analytics.dividends.total)
        }
        val plus = AnalyticsFixtureCatalog.get("plus").analytics()
        assertTrue(plus.locked.isEmpty())
        assertNotNull(plus.performance?.timeWeightedReturn)
        assertNotNull(plus.contributors)
    }

    // ---------- Insights ----------

    @Test fun insightsAreDeterministicPrioritizedAndUnique() {
        for (id in AnalyticsFixtureCatalog.ids) {
            val analytics = AnalyticsFixtureCatalog.get(id).analytics()
            val insights = analytics.insights
            assertTrue(insights.size <= PortfolioInsightsEngine.MAX_SHOWN)
            assertEquals(insights.map { it.category }.distinct(), insights.map { it.category }, "$id: one per category")
            assertEquals(insights.map { it.id }.distinct(), insights.map { it.id })
            assertEquals(insights, PortfolioInsightsEngine.generate(analytics.copy(insights = emptyList())))
            insights.forEach { insight ->
                assertNotNull(AnalyticsEducation.entry(insight.methodology), "${insight.id} methodology")
                assertFalse(Regex("(?i)\\b(buy|sell|should|recommend|will rise|will fall)\\b").containsMatchIn(insight.title + insight.explanation), insight.explanation)
            }
        }
        val single = AnalyticsFixtureCatalog.get("single-holding").analytics().insights
        assertEquals("concentration.single", single.first().id)
    }

    @Test fun insightMetricsComeFromComputedValues() {
        val analytics = AnalyticsFixtureCatalog.get("concentrated").analytics()
        val concentration = analytics.insights.first { it.category == InsightCategory.CONCENTRATION }
        assertEquals(analytics.concentration.largestHolding!!.percent, concentration.metrics["share"])
        val performance = analytics.insights.first { it.category == InsightCategory.PERFORMANCE }
        assertEquals(analytics.performance!!.timeWeightedReturn, performance.metrics["timeWeightedReturn"])
        assertEquals(analytics.performance!!.investmentGain, performance.metrics["investmentGain"])
    }

    @Test fun insufficientDataProducesNoPerformanceInsight() {
        val analytics = AnalyticsFixtureCatalog.get("insufficient-history").analytics()
        assertTrue(analytics.insights.none { it.category == InsightCategory.PERFORMANCE || it.category == InsightCategory.BENCHMARK })
    }

    @Test fun educationCoversEveryMetric() {
        listOf("twr", "xirr", "gain", "benchmark", "concentration", "sectors", "contribution", "dividends", "currency", "data").forEach {
            assertTrue(AnalyticsEducation.entry(it)!!.body.isNotBlank())
        }
    }
}

class AiExplanationsTest {
    private val insight = AnalyticsFixtureCatalog.get("concentrated").analytics().insights.first { it.category == InsightCategory.CONCENTRATION }

    @Test fun nothingLeavesTheDeviceWithoutConsent() {
        assertNull(ExplanationRequest.from(insight, ExplanationConsent.NONE))
    }

    @Test fun metricsOnlyConsentStripsHoldings() {
        val request = assertNotNull(ExplanationRequest.from(insight, ExplanationConsent.METRICS_ONLY))
        assertFalse(request.metrics.containsKey("largestHolding"))
        assertFalse(request.title.contains("SHOP"))
        assertFalse(request.insightId.contains("SHOP"))
        assertEquals(insight.metrics["share"], request.metrics["share"])
    }

    @Test fun shippedProviderNeverCallsAnAiService(): Unit = kotlinx.coroutines.runBlocking {
        val request = ExplanationRequest.from(insight, ExplanationConsent.INCLUDE_HOLDINGS)!!
        assertIs<ExplanationResult.Unavailable>(NoAiExplanations.explain(request))
    }
}
