package org.example.stocksteps.repositoryImpl

import kotlinx.serialization.json.Json
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.repository.models.*
import java.time.LocalDate
import kotlin.test.*

/**
 * Company Comparison Phase 1: the REAL FMP adapter with stubbed provider JSON (no network). Loss-making
 * companies have no P/E and zero/negative equity has no debt-to-equity: both "not meaningful", never a
 * negative ratio or a fabricated value.
 */
class ComparisonMetricsAdapterTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val today = LocalDate.parse("2026-10-07")

    private fun map(ratiosJson: String, equity: Double, netIncome: Double) = FmpFundamentalsMapper.map(
        "TEST", "annual",
        income = json.decodeFromString<List<FmpIncomeStatement>>("""[{"symbol":"TEST","date":"2025-12-31","fiscalYear":"2025","period":"FY","reportedCurrency":"USD","revenue":1000,"netIncome":$netIncome},
            {"symbol":"TEST","date":"2024-12-31","fiscalYear":"2024","period":"FY","reportedCurrency":"USD","revenue":900,"netIncome":50}]"""),
        annualIncome = emptyList(),
        balance = json.decodeFromString<List<FmpBalanceSheetStatement>>("""[{"symbol":"TEST","date":"2025-12-31","fiscalYear":"2025","period":"FY","reportedCurrency":"USD","totalDebt":500,"totalStockholdersEquity":$equity}]"""),
        cash = emptyList(), ratios = json.decodeFromString<FmpRatiosTtm>(ratiosJson), keys = null, history = emptyList(),
        trailingIncome = json.decodeFromString<FmpIncomeStatement>("""{"symbol":"TEST","netIncome":$netIncome,"revenue":1000}"""), trailingCash = null,
        estimates = emptyList(), dividends = emptyList(), shares = null, price = 10.0, quoteCurrency = "USD", today = today)

    @Test fun negativeEquityAndLossesAreNotMeaningfulNotNegativeRatios() {
        val f = map("""{"symbol":"TEST","debtToEquityRatioTTM":-2.5,"priceToEarningsRatioTTM":-12.0,"priceToSalesRatioTTM":1.5}""", equity = -200.0, netIncome = -80.0)
        val de = f.financials.metrics().getValue("debtEquity")
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, de.availability); assertNull(de.value)
        assertTrue(de.note!!.contains("equity is zero or negative"))
        val pe = f.valuation.metrics.getValue("pe")
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, pe.availability); assertNull(pe.value)
        assertEquals(1.5, f.valuation.metrics.getValue("priceSales").value)
    }

    @Test fun positiveEquityAndEarningsKeepTheProviderRatios() {
        val f = map("""{"symbol":"TEST","debtToEquityRatioTTM":0.8,"priceToEarningsRatioTTM":21.0}""", equity = 625.0, netIncome = 80.0)
        assertEquals(0.8, f.financials.metrics().getValue("debtEquity").value)
        assertEquals(21.0, f.valuation.metrics.getValue("pe").value)
        // Without a provider ratio: total debt ÷ equity from the balance sheet.
        val calc = map("""{"symbol":"TEST"}""", equity = 625.0, netIncome = 80.0)
        assertEquals(0.8, calc.financials.metrics().getValue("debtEquity").value!!, 1e-9)
        assertEquals(FinancialAvailability.MISSING, calc.valuation.metrics.getValue("pe").availability)  // not reported ≠ not meaningful
    }
}
