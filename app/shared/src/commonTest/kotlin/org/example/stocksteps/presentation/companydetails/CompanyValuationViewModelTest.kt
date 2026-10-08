package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.companydetail.ValuationRange
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyValuationViewModelTest {
    private val history = ValuationHistory("SMPL", ValuationMethod.MONTHLY_TTM,
        (0 until 60).map { i -> ValuationObservation("${2021 + i / 12}-${(i % 12 + 1).toString().padStart(2, '0')}-28", 20.0 + i * 0.2) },
        currentPe = 30.0, currentPeAvailability = FinancialAvailability.AVAILABLE)

    private class Repos(val history: ValuationHistory?, val fundamentalsFail: Boolean = false) : CompanyDetailsRepository, StockRepository {
        var historyRequests = 0
        override suspend fun getDetails(symbol: String) = CompanyDetails(symbol)
        override suspend fun getChart(symbol: String, range: ChartRange) = PriceChart(symbol, range, emptyList(), "1day")
        override suspend fun getWhyMoving(symbol: String): WhyMoving? = null
        override suspend fun getValuationHistory(symbol: String): ValuationHistory { historyRequests++; return history ?: throw StockDataException("HTTP 503") }
        override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
        override suspend fun getQuote(symbol: String): StockQuote = throw StockDataException("unused")
        override suspend fun getProfile(symbol: String) = CompanyProfile(symbol, "Sample Corporation", exchange = "NASDAQ", currency = "USD")
        override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals =
            if (fundamentalsFail) throw StockDataException("HTTP 429") else CompanyFundamentals(symbol)
    }

    private fun TestScope.model(repos: Repos, store: ViewModelStore) =
        CompanyValuationViewModel("SMPL", GetValuationHistory(repos), GetCompanyFundamentals(repos), GetCompanyProfile(repos)) {}.also { store.put("valuation", it) }

    @Test fun rangesReuseTheSingleHistoryRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repos = Repos(history)
        val store = ViewModelStore()
        try {
            val model = model(repos, store)
            advanceUntilIdle()
            assertEquals("Sample", model.state.value.shortName)
            assertEquals("SMPL · NASDAQ", model.state.value.listing)
            assertEquals(ValuationRange.FIVE_YEARS, model.state.value.range)
            val fiveYear = model.state.value.model!!.average
            model.selectRange(ValuationRange.ONE_YEAR)
            assertNotEquals(fiveYear, model.state.value.model!!.average)
            model.selectRange(ValuationRange.TEN_YEARS)
            assertNull(model.state.value.model!!.average, "10Y isn't covered by 5 years of data")
            assertEquals(1, repos.historyRequests, "range switches never refetch")
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun sectionsFailIndependently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val ratiosDown = model(Repos(history, fundamentalsFail = true), store)
            advanceUntilIdle()
            assertEquals(Section.Unavailable, ratiosDown.state.value.fundamentals)
            assertEquals("30.0x", ratiosDown.state.value.model!!.currentPe, "history still renders")
            store.clear()
            val historyDown = model(Repos(null), store)
            advanceUntilIdle()
            assertEquals("We couldn't load the valuation history right now.", historyDown.state.value.model!!.historyMessage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
