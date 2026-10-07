package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.test.*

class CompanyOverviewPresenterTest {
    private fun fact(value: Double? = null, amount: Long? = null, availability: FinancialAvailability = FinancialAvailability.AVAILABLE) =
        FinancialFact(value = value, amount = amount, availability = availability, source = FinancialSource.PROVIDER_DIRECT)

    private fun details(
        pe: FinancialFact = fact(31.2),
        dividend: FinancialFact = fact(0.8),
        history: HistoricalComparison? = HistoricalComparison(average = 27.4, validCount = 5, differencePercent = 13.9, reliable = true),
        errors: List<SnapshotSectionError> = emptyList()
    ) = CompanyDetails(
        symbol = "MSFT",
        profile = CompanyProfile("MSFT", "Microsoft Corporation", description = "Microsoft develops software. ".repeat(20), sector = "Technology", industry = "Software", exchange = "NASDAQ", currency = "USD"),
        quote = StockQuote("MSFT", "Microsoft Corporation", 425.18, 5.26, 1.25, null, null, marketCap = 3_160_000_000_000),
        marketStatus = MarketStatus.OPEN,
        fundamentals = CompanyFundamentals(
            "MSFT",
            financials = CompanyFinancials(
                growth = mapOf("revenue" to fact(amount = 245_000_000_000), "revenueGrowth" to fact(15.3), "netIncome" to fact(amount = 88_000_000_000)),
                profitability = mapOf("netMargin" to fact(35.1)),
                financialHealth = mapOf("cash" to fact(amount = 80_000_000_000), "debt" to fact(amount = 76_000_000_000)),
                cashFlow = mapOf("freeCashFlow" to fact(amount = 74_000_000_000)),
                shareholderReturns = mapOf("dividendYield" to dividend)
            ),
            valuation = CompanyValuation(metrics = mapOf("pe" to pe), historical = history?.let { mapOf("pe" to it) }.orEmpty())
        ),
        errors = errors
    )

    @Test fun headerAndGlanceUseNumbersWithContext() {
        val overview = CompanyOverviewPresenter.build(details())
        assertEquals("Microsoft Corporation", overview.name)
        assertEquals("MSFT • NASDAQ", overview.listing)
        assertEquals("$425.18", overview.price)
        assertEquals("+$5.26", overview.changeAmount)
        assertEquals("+1.25%", overview.changePercent)
        assertEquals(listOf("$3.16T", "31.2", "+15.3%", "0.8%"), overview.glance.map { it.value })
        assertEquals(listOf("Mega-cap company", "31x earnings", "vs last year", "per year"), overview.glance.map { it.helper })
    }

    @Test fun assessmentIsFactualWithoutRatings() {
        val rows = CompanyOverviewPresenter.build(details()).assessment
        assertEquals(listOf("Growth", "Profitability", "Financial health", "Valuation"), rows.map { it.title })
        assertEquals("Cash $80B • Debt $76B", rows[2].detail)
        assertEquals("Above its history", rows[3].comparison)
        val text = rows.joinToString { it.detail + it.comparison.orEmpty() }.lowercase()
        listOf("strong", "excellent", "/10", "buy", "sell").forEach { assertFalse(text.contains(it), it) }
    }

    @Test fun insightStatesFactsAndNeverRecommends() {
        val insight = CompanyOverviewPresenter.build(details()).insight!!
        assertTrue(insight.contains("grew 15.3%") && insight.contains("$35 of every $100") && insight.contains("14% above"))
        assertTrue(insight.endsWith("This is context, not a recommendation."))
        listOf("buy", "sell", "will rise", "good investment").forEach { assertFalse(insight.lowercase().contains(it), it) }
    }

    @Test fun negativeEarningsShowNotApplicablePe() {
        val overview = CompanyOverviewPresenter.build(details(pe = fact(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR)))
        assertEquals("N/A", overview.glance.first { it.id == "pe" }.value)
        assertNull(overview.valuation!!.currentPe)
        assertTrue(overview.valuation!!.explanation.contains("negative"))
    }

    @Test fun noDividendDiffersFromMissingDividend() {
        assertEquals("No dividend", CompanyOverviewPresenter.build(details(dividend = fact(availability = FinancialAvailability.NO_DIVIDEND))).glance.last().value)
        assertEquals("—", CompanyOverviewPresenter.build(details(dividend = fact(availability = FinancialAvailability.MISSING))).glance.last().value)
    }

    @Test fun missingHistoryStillShowsCurrentValuation() {
        val valuation = CompanyOverviewPresenter.build(details(history = null)).valuation!!
        assertEquals("31.2", valuation.currentPe)
        assertNull(valuation.difference)
        assertTrue(valuation.explanation.contains("isn't available"))
    }

    @Test fun aboutIsTrimmedAtASentenceAndFailuresAreReported() {
        val overview = CompanyOverviewPresenter.build(details(errors = listOf(SnapshotSectionError("fundamentals", ApiError("X", "Y")))))
        assertTrue(overview.about!!.length <= 280 && overview.about!!.endsWith("."))
        assertEquals(setOf("fundamentals"), overview.unavailable)
        assertEquals("$245B", CompanyOverviewPresenter.money(245e9))
    }
}
