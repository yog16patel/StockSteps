package org.example.stocksteps.repositoryImpl

import org.example.stocksteps.repository.models.*
import java.time.LocalDate
import kotlin.test.*

class FinancialHistoryTest {
    private val today = LocalDate.parse("2026-10-07")

    @Test fun mergesStatementsPerFiscalPeriodWithNormalizedCapex() {
        // Fiscal year ends October 31 (not December), as for many Canadian banks.
        val income = listOf(
            FmpIncomeStatement("TD", "2025-10-31", "2025", "FY", "CAD", revenue = 60.0, netIncome = 12.0, epsDiluted = 6.5, grossProfit = 40.0, operatingIncome = 18.0),
            FmpIncomeStatement("TD", "2024-10-31", "2024", "FY", "CAD", revenue = 55.0, netIncome = -2.0),
            FmpIncomeStatement("TD", "2025-07-31", "2025", "Q3", "CAD", revenue = 15.0),
            FmpIncomeStatement("TD", "2027-10-31", "2027", "FY", "CAD", revenue = 99.0)
        )
        val cash = listOf(
            FmpCashFlowStatement("TD", "2025-10-31", "2025", "FY", "CAD", operatingCashFlow = 20.0, capitalExpenditure = -5.0, commonDividendsPaid = -3.0),
            FmpCashFlowStatement("TD", "2024-10-31", "2024", "FY", "CAD", operatingCashFlow = 4.0, capitalExpenditure = -6.0)
        )
        val balance = listOf(
            FmpBalanceSheetStatement("TD", "2025-10-31", "2025", "FY", "CAD", cashAndCashEquivalents = 30.0, totalDebt = 25.0, totalStockholdersEquity = 50.0),
            FmpBalanceSheetStatement("TD", "2024-10-31", "2024", "FY", "USD", cashAndCashEquivalents = 1.0)
        )
        val rows = FmpFundamentalsMapper.history("annual", income, balance, cash, today)
        assertEquals(listOf(2025, 2024), rows.map { it.fiscalYear }, "future and quarterly rows excluded, newest first")
        val latest = rows.first()
        assertEquals("CAD", latest.currency)
        assertEquals("2025-10-31", latest.date)
        assertEquals(5.0, latest.capitalExpenditure, "provider outflow shown as positive spending")
        assertEquals(15.0, latest.freeCashFlow, "FCF = operating cash flow − capital expenditure, subtracted once")
        assertEquals(3.0, latest.dividendsPaid)
        assertEquals(30.0, latest.cash)
        val prior = rows[1]
        assertEquals(-2.0, prior.netIncome, "losses stay negative")
        assertEquals(-2.0, prior.freeCashFlow, "negative free cash flow is preserved")
        assertNull(prior.cash, "a balance sheet in another currency is not merged")
        assertNull(prior.grossProfit, "unreported values stay null, never zero")
    }

    @Test fun quarterlyHistoryKeepsQuartersSeparate() {
        val income = listOf(
            FmpIncomeStatement("AAPL", "2025-06-28", "2025", "Q3", "USD", revenue = 94.0),
            FmpIncomeStatement("AAPL", "2025-09-27", "2025", "FY", "USD", revenue = 416.0),
            FmpIncomeStatement("AAPL", "2024-06-29", "2024", "Q3", "USD", revenue = 86.0)
        )
        val rows = FmpFundamentalsMapper.history("quarter", income, emptyList(), emptyList(), today)
        assertEquals(listOf("Q3", "Q3"), rows.map { it.period })
        assertEquals(listOf(2025, 2024), rows.map { it.fiscalYear })
        assertNull(rows.first().cash)
    }
}
