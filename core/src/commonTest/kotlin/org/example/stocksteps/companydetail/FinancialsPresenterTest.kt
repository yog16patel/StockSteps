package org.example.stocksteps.companydetail

import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import kotlin.test.*

class FinancialsPresenterTest {
    private val basis = FinancialBasis("FY", "2025-09-30", 2025, "USD")
    private fun fact(value: Double) = FinancialFact(value = value, basis = basis, availability = FinancialAvailability.AVAILABLE)
    private fun state(data: CompanyFinancials) = FinancialsPresenter.build(CompanyFundamentals("AAPL", financials = data), false, false)

    @Test fun centralFormattingPreservesUnitsAndDirection() {
        assertEquals("$391B", FinancialsFormatter.value("revenue", "money", 391e9, "USD"))
        assertEquals("$94B", FinancialsFormatter.value("netIncome", "money", 94e9, "USD"))
        assertEquals("$7.81", FinancialsFormatter.value("eps", "ratio", 7.8123, "USD"))
        assertEquals("26.9%", FinancialsFormatter.value("netMargin", "percent", 26.9, null))
        assertEquals("0.80", FinancialsFormatter.value("debtEquity", "ratio", .8, null))
        assertEquals("—", FinancialsFormatter.value("revenue", "money", Double.NaN, "USD"))
        assertEquals("↓ 4.0% YoY", FinancialsFormatter.movement(-4.0))
    }
    @Test fun absentSectionIsOneCompactStateWithoutDiagnosticRows() {
        val ui = state(CompanyFinancials(growth = mapOf("revenue" to FinancialFact(
            availability = FinancialAvailability.TEMPORARILY_UNAVAILABLE, note = "annual FMP access denied HTTP 403"))))
        val growth = ui.sections.first()
        assertEquals(SectionStatus.EMPTY, growth.status)
        assertTrue(growth.metrics.isEmpty())
        assertTrue(growth.secondary.isEmpty())
        assertFalse(ui.toString().contains("FMP"))
        assertFalse(ui.toString().contains("403"))
    }
    @Test fun partialGrowthKeepsPrimaryValuesAndOmitsMissingHistory() {
        val growth = state(CompanyFinancials(growth = mapOf(
            "revenue" to fact(391e9), "netIncome" to fact(94e9), "eps" to fact(7.81),
            "revenueGrowth" to fact(9.4), "netIncomeGrowth" to fact(8.1), "epsGrowth" to fact(12.8)
        ))).sections.first()
        assertEquals(3, growth.metrics.size)
        assertTrue(growth.secondary.isEmpty())
        assertEquals(2, growth.insights.size)
        assertEquals("↑ 9.4% YoY", growth.metrics.first().movement)
    }
    @Test fun mismatchedPeriodAndLossesDoNotProduceGrowthConclusions() {
        val growth = state(CompanyFinancials(growth = mapOf(
            "revenue" to fact(100.0), "netIncome" to fact(-10.0), "eps" to fact(-1.0),
            "revenueGrowth" to fact(20.0), "netIncomeGrowth" to fact(10.0),
            "epsGrowth" to fact(30.0).copy(basis = basis.copy(currency = "EUR"))
        ))).sections.first()
        assertTrue(growth.insights.isEmpty())
        assertNull(growth.metrics.last().movement)
    }
    @Test fun noDividendAndMissingDividendAreDistinctAndProfitLossCopyIsHonest() {
        val noDividend = state(CompanyFinancials(shareholderReturns = mapOf("dividendYield" to FinancialFact(availability = FinancialAvailability.NO_DIVIDEND)))).sections.last()
        assertEquals(SectionStatus.SUCCESS, noDividend.status)
        assertTrue(noDividend.insights.single().contains("does not pay"))
        assertEquals(SectionStatus.EMPTY, state(CompanyFinancials()).sections.last().status)
        assertContains(FinancialsFormatter.profitContext(-12.0), "loses about 12")
        assertContains(FinancialsFormatter.profitContext(26.9), "keeps about 27")
    }
    @Test fun cashEquationNeedsMatchingUnderlyingAmountsAndLoadingIsSectionScoped() {
        val cash = mapOf("operatingCashFlow" to fact(125.0), "capex" to fact(12.0), "freeCashFlow" to fact(113.0))
        assertTrue(state(CompanyFinancials(cashFlow = cash)).sections[3].cashRelationship)
        assertFalse(state(CompanyFinancials(cashFlow = cash + ("freeCashFlow" to fact(500.0)))).sections[3].cashRelationship)
        assertFalse(state(CompanyFinancials(cashFlow = cash - "capex")).sections[3].cashRelationship)
        assertTrue(FinancialsPresenter.build(null, true, false).sections.all { it.status == SectionStatus.LOADING })
        assertTrue(FinancialsPresenter.build(null, false, true).sections.all { it.status == SectionStatus.ERROR })
    }
    @Test fun refreshPreservesAvailableSectionsAndLabelsStaleValuesOnFailure() {
        val data = CompanyFundamentals("AAPL", financials = CompanyFinancials(profitability = mapOf("netMargin" to fact(26.9))))
        val refreshing = FinancialsPresenter.build(data, true, false)
        assertEquals(SectionStatus.LOADING, refreshing.sections.first().status)
        assertEquals(SectionStatus.SUCCESS, refreshing.sections[1].status)
        val failed = FinancialsPresenter.build(data, false, true)
        assertEquals(SectionStatus.SUCCESS, failed.sections[1].status)
        assertContains(failed.refreshMessage!!, "previously loaded")
    }
    @Test fun olderAvailabilityCodesDecodeToNeutralPublicValues() {
        for (code in listOf("ACCESS_RESTRICTED", "PROVIDER_UNAVAILABLE")) {
            val value = Json.decodeFromString<FinancialFact>("""{"availability":"$code"}""")
            assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, value.availability)
            assertFalse(Json.encodeToString(value).contains(code))
        }
    }
}
