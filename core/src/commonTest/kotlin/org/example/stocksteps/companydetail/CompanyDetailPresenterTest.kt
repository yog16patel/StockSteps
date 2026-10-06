package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.test.*

class CompanyDetailPresenterTest {
    private val stock = StockSearchResult("AAPL", "Apple Inc.", "USD", "NASDAQ")
    @Test fun financialFactsPopulateBothTabsAndEvidenceWithoutScores() {
        val result = CompanyFundamentals("AAPL", financials = CompanyFinancials(
            growth = mapOf("revenue" to FinancialFact(amount = 600, basis = FinancialBasis("annual", "2025-09-30", 2025, "USD")),
                "revenueGrowth" to FinancialFact(value = 20.0)),
            cashFlow = mapOf("freeCashFlow" to FinancialFact(amount = 80))
        ), valuation = CompanyValuation(
            metrics = mapOf("pe" to FinancialFact(value = 30.0)),
            historical = mapOf("pe" to HistoricalComparison(average = 20.0, minimum = 18.0, maximum = 22.0, validCount = 3, reliable = true, differencePercent = 50.0))
        ))
        val detail = CompanyDetailPresenter.build(stock, null, null, result)
        val revenue = detail.financials.flatMap { it.metrics }.first { it.id == "revenue" }
        assertEquals(600.0, revenue.value)
        assertContains(revenue.context!!, "USD")
        assertEquals(30.0, detail.keyMetrics.first { it.id == "pe" }.value)
        val pe = detail.valuation.first { it.id == "pe" }
        assertEquals("3 valid-year average", pe.historicalLabel)
        assertEquals(20.0, pe.historicalAverage)
        assertContains(pe.context!!, "50.00% above")
        assertTrue(detail.snapshot.scores.any { it.context?.contains("20.00%") == true })
        assertTrue(detail.snapshot.scores.all { it.score == null })
    }

    @Test fun missingInputsNeverProduceScoresOrRiskConclusions() {
        val state = CompanyDetailPresenter.build(stock, null, null)
        assertEquals("Unavailable", state.price)
        assertNull(state.snapshot.overall)
        assertTrue(state.snapshot.scores.all { it.score == null && it.context == null })
        assertTrue(state.keyMetrics.all { it.value == null })
        assertTrue(state.risks.isEmpty())
        assertEquals(PriceDirection.UNAVAILABLE, state.direction)
    }
    @Test fun quoteAndProfileMapIndependentlyAndMarketCapIsReal() {
        val quote = StockQuote("AAPL", "Apple", 100.0, -2.0, -1.96, 102.0, 99.0, marketCap = 2_500_000_000_000)
        val state = CompanyDetailPresenter.build(stock, quote, null)
        assertEquals("100.00 USD", state.price)
        assertEquals("↓ −2.00 USD (−1.96%)", state.priceChange)
        assertEquals(PriceDirection.DOWN, state.direction)
        assertEquals("2.50T", CompanyDetailPresenter.metricValue(state.keyMetrics.first()))
        val profileOnly = CompanyDetailPresenter.build(stock, null, CompanyProfile("AAPL", description = "Makes devices", sector = "Technology"))
        assertEquals("Makes devices", profileOnly.description)
        assertEquals("Unavailable", profileOnly.price)
        assertEquals(PriceDirection.UNAVAILABLE, CompanyDetailPresenter.direction(Double.NaN))
    }
    @Test fun percentageAndPriceFormattingHandleMissingAndLosses() {
        assertEquals("Unavailable", CompanyDetailPresenter.percentage(null))
        assertEquals("Unavailable", CompanyDetailPresenter.number(Double.POSITIVE_INFINITY))
        assertEquals("−12.35", CompanyDetailPresenter.number(-12.3451))
        assertEquals("+12.35%", CompanyDetailPresenter.percentage(12.3451))
        assertEquals("0.00%", CompanyDetailPresenter.percentage(0.0))
        assertEquals(PriceDirection.UNCHANGED, CompanyDetailPresenter.direction(0.0))
        assertEquals(PriceDirection.UP, CompanyDetailPresenter.direction(0.1))
    }
    @Test fun historicalComparisonRequiresMeaningfulComparableInputs() {
        val metric = MetricEducation.metric("pe")
        assertNull(CompanyDetailPresenter.historicalContext(metric))
        assertNull(CompanyDetailPresenter.historicalContext(metric.copy(value = 10.0, historicalAverage = 0.0)))
        assertNull(CompanyDetailPresenter.historicalContext(metric.copy(value = -10.0, historicalAverage = 20.0)))
        assertContains(CompanyDetailPresenter.historicalContext(metric.copy(value = 34.8, historicalAverage = 30.0))!!, "16.00% above")
        assertContains(CompanyDetailPresenter.historicalContext(metric.copy(value = 15.0, historicalAverage = 30.0))!!, "50.00% below")
    }
    @Test fun statementSectionsExplainCashFlowAndShareCountWithoutInventingHistory() {
        val state = CompanyDetailPresenter.build(stock, null, null)
        assertEquals(listOf("Growth", "Profitability", "Financial health", "Cash flow", "Shareholders"), state.financials.map { it.title })
        assertTrue(state.financials.flatMap { it.metrics }.all { it.value == null })
        assertTrue(state.financials.flatMap { it.metrics }.all { it.explanation.isNotBlank() })
        assertTrue(FinancialChartState().points.isEmpty())
        assertEquals(SectionStatus.EMPTY, FinancialChartState().status)
    }
}
