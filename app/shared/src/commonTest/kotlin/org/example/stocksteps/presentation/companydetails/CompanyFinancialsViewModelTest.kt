package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.companydetail.FinancialPeriod
import org.example.stocksteps.companydetail.FinancialRange
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyFinancialsViewModelTest {
    private fun fy(year: Int) = FinancialPeriodStatement("FY", year, "$year-06-30", "USD", revenue = 100.0 + year)
    private fun q(n: Int, year: Int) = FinancialPeriodStatement("Q$n", year, "$year-0${n * 3}-28", "USD", revenue = 25.0 + n)

    private class Repo(val quarterly: CompletableDeferred<CompanyFundamentals>? = null, val failAnnual: Boolean = false) : StockRepository {
        val requests = mutableListOf<String>()
        override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
        override suspend fun getQuote(symbol: String): StockQuote = throw StockDataException("unused")
        override suspend fun getProfile(symbol: String) = CompanyProfile(symbol, "Sample Corp", exchange = "NASDAQ")
        override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals {
            requests += period
            if (period == "annual" && failAnnual) throw StockDataException("HTTP 503")
            if (period == "quarter") quarterly?.let { return it.await() }
            return CompanyFundamentals(symbol, history = if (period == "annual") (2025 downTo 2020).map { year -> FinancialPeriodStatement("FY", year, "$year-06-30", "USD", revenue = 100.0 + year) }
                else (1..4).flatMap { n -> listOf(2025, 2024).map { year -> FinancialPeriodStatement("Q$n", year, "$year-0${n * 3}-28", "USD", revenue = 25.0 + n) } })
        }
    }

    @Test fun frequenciesLoadOnceAndRangesOnlyReslice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = Repo()
        val store = ViewModelStore()
        try {
            val model = CompanyFinancialsViewModel("SMPL", GetCompanyFundamentals(repo), GetCompanyProfile(repo)) {}
            store.put("financials", model)
            advanceUntilIdle()
            assertEquals("Sample Corp", model.state.value.name)
            assertEquals("SMPL · NASDAQ", model.state.value.listing)
            assertEquals(FinancialRange.FIVE_YEARS, model.state.value.range, "5Y when six fiscal years exist")
            assertEquals(5, model.state.value.model!!.table!!.columns.size)
            model.selectRange(FinancialRange.THREE_YEARS)
            assertEquals(3, model.state.value.model!!.table!!.columns.size)
            model.selectFrequency(FinancialPeriod.QUARTERLY); advanceUntilIdle()
            assertEquals(FinancialRange.THREE_YEARS, model.state.value.range, "8 quarters is less than 5Y, so the default is 3Y")
            assertTrue(model.state.value.model!!.table!!.columns.all { it.startsWith("Q") }, "quarterly data, never annual")
            model.selectFrequency(FinancialPeriod.ANNUAL); advanceUntilIdle()
            assertEquals(listOf("annual", "quarter"), repo.requests, "switching back reuses loaded statements")
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun staleResponsesDoNotOverwriteTheCurrentSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<CompanyFundamentals>()
        val repo = Repo(quarterly = pending)
        val store = ViewModelStore()
        try {
            val model = CompanyFinancialsViewModel("SMPL", GetCompanyFundamentals(repo), GetCompanyProfile(repo)) {}
            store.put("financials", model)
            advanceUntilIdle()
            model.selectFrequency(FinancialPeriod.QUARTERLY); runCurrent()
            assertTrue(model.state.value.loading)
            model.selectFrequency(FinancialPeriod.ANNUAL); runCurrent()
            pending.complete(CompanyFundamentals("SMPL", history = listOf(q(1, 2025))))
            advanceUntilIdle()
            assertEquals(FinancialPeriod.ANNUAL, model.state.value.frequency)
            assertTrue(model.state.value.model!!.table!!.columns.all { it.startsWith("FY") })
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failureShowsRetryableErrorWithoutCrashing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val model = CompanyFinancialsViewModel("SMPL", GetCompanyFundamentals(Repo(failAnnual = true)), GetCompanyProfile(Repo())) {}
            store.put("financials", model)
            advanceUntilIdle()
            assertTrue(model.state.value.failed)
            assertNull(model.state.value.model)
            assertEquals("Sample Corp", model.state.value.name, "the header still loads")
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
