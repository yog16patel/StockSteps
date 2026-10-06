package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.presentation.companydetail.CompanyDetailAction
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.stocksteps.domain.SearchStocks
import org.example.stocksteps.domain.GetCompanyProfile
import org.example.stocksteps.domain.GetStockQuote
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.model.StockSearchResult

internal class StockSearchViewModel(
    private val searchStocks: SearchStocks,
    private val getQuote: GetStockQuote,
    private val getProfile: GetCompanyProfile,
    initialQuery: String = "",
    initialSelection: StockSearchResult? = null,
    private val closeResources: () -> Unit = {},
    private val companyNews: org.example.stocksteps.domain.GetCompanyNews? = null,
    private val getFundamentals: org.example.stocksteps.domain.GetCompanyFundamentals? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(StockSearchState(query = initialQuery, selected = initialSelection))
    val state = mutableState.map { current ->
        current.copy(
            financials = org.example.stocksteps.companydetail.FinancialsPresenter.build(
                fundamentals = current.fundamentals,
                loading = current.fundamentalsLoading,
                failed = current.fundamentalsError != null
            ),
            detail = current.selected?.let {
                CompanyDetailPresenter.build(it, current.quote, current.profile, current.fundamentals)
            } ?: CompanyDetailUiState()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, mutableState.value)
    private val queries = MutableStateFlow(initialQuery.trim())
    private var searchJob: Job? = null
    private var profileJob: Job? = null
    private var newsJob: Job? = null
    private var fundamentalsJob: Job? = null
    private var quoteJob: Job? = null

    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            queries.debounce { if (it.isBlank()) 0L else 300L }.collectLatest { text ->
                if (text.isNotBlank()) {
                    searchJob = launch { search(text) }
                    searchJob?.join()
                }
            }
        }
    }

    init {
        mutableState.update { it.copy(searching = initialQuery.isNotBlank()) }
        observeQueries()
        initialSelection?.let { selectStock(it) }
    }

    fun changeQuery(query: String) {
        fundamentalsJob?.cancel()
        mutableState.update { it.copy(fundamentals = null, fundamentalsLoading = false, fundamentalsError = null) }
        newsJob?.cancel()
        mutableState.update { it.copy(news = emptyList(), newsLoading = false, newsError = null, metricInfo = null) }
        quoteJob?.cancel()
        profileJob?.cancel()
        mutableState.update { it.copy(profile = null, profileError = null, profileLoading = false, query = query, selected = null, quote = null, quoteError = null, quoteLoading = false) }
        val text = query.trim()
        if (queries.value == text) return
        searchJob?.cancel()
        mutableState.update { it.copy(results = emptyList(), searchError = null, searching = text.isNotBlank()) }
        queries.value = text
    }

    private suspend fun search(query: String) {
        try {
            val results = searchStocks(query)
            currentCoroutineContext().ensureActive()
            if (queries.value == query) mutableState.update { it.copy(results = results, searching = false) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            if (queries.value == query) mutableState.update { it.copy(searchError = cause.userMessage(), searching = false) }
        }
    }

    fun selectStock(stock: StockSearchResult) {
        mutableState.update { it.copy(selected = stock, detailTab = CompanyDetailTab.OVERVIEW, metricInfo = null, chart = FinancialChartState()) }
        loadFundamentals(stock.symbol)
        loadNews(stock.symbol)
        loadProfile(stock)
        loadQuote(stock)
    }

    private fun loadQuote(stock: StockSearchResult) {
        quoteJob?.cancel()
        mutableState.update { it.copy(selected = stock, quote = null, quoteError = null, quoteLoading = true) }
        quoteJob = viewModelScope.launch {
            try {
                val quote = getQuote(stock.symbol)
                ensureActive()
                mutableState.update { it.copy(quote = quote, quoteLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(quoteError = cause.userMessage(), quoteLoading = false) }
            }
        }
    }

    private fun loadProfile(stock: StockSearchResult) {
        profileJob?.cancel()
        mutableState.update { it.copy(profile = null, profileError = null, profileLoading = true) }
        profileJob = viewModelScope.launch {
            try {
                val profile = getProfile(stock.symbol)
                ensureActive()
                mutableState.update { it.copy(profile = profile, profileLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(profileError = cause.userMessage(), profileLoading = false) }
            }
        }
    }

    fun detailAction(action: CompanyDetailAction) {
        when (action) {
            is CompanyDetailAction.SelectTab -> mutableState.update { it.copy(detailTab = action.tab) }
            is CompanyDetailAction.SelectChartPeriod -> mutableState.update { it.copy(chart = it.chart.copy(period = action.period)) }
            is CompanyDetailAction.SelectFinancialPeriod -> {
                mutableState.update { it.copy(financialPeriod = action.period) }
                mutableState.value.selected?.let { loadFundamentals(it.symbol) }
            }
            CompanyDetailAction.RefreshFundamentals -> mutableState.value.selected?.let { loadFundamentals(it.symbol, preserveValues = true) }
            is CompanyDetailAction.OpenMetricInfo -> mutableState.update { it.copy(metricInfo = action.id) }
            CompanyDetailAction.CloseMetricInfo -> mutableState.update { it.copy(metricInfo = null) }
            CompanyDetailAction.RefreshNews -> mutableState.value.selected?.let { loadNews(it.symbol) }
        }
    }
    private fun loadFundamentals(symbol: String, preserveValues: Boolean = false) {
        fundamentalsJob?.cancel()
        val fetch = getFundamentals ?: return
        val period = if (mutableState.value.financialPeriod == FinancialPeriod.ANNUAL) "annual" else "quarter"
        mutableState.update { it.copy(fundamentals = if (preserveValues) it.fundamentals else null, fundamentalsLoading = true, fundamentalsError = null) }
        fundamentalsJob = viewModelScope.launch {
            try {
                val values = fetch(symbol, period)
                ensureActive()
                mutableState.update { it.copy(fundamentals = values, fundamentalsLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(fundamentalsLoading = false, fundamentalsError = "Financial data is temporarily unavailable. Try again.") }
            }
        }
    }

    private fun loadNews(symbol: String) {
        newsJob?.cancel()
        val fetch = companyNews ?: return
        mutableState.update { it.copy(news = emptyList(), newsLoading = true, newsError = null) }
        newsJob = viewModelScope.launch {
            try {
                val articles = fetch(symbol)
                ensureActive()
                mutableState.update { it.copy(news = articles, newsLoading = false) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutableState.update { it.copy(newsLoading = false, newsError = "Company news is temporarily unavailable.") }
            }
        }
    }
    fun retryQuote() { state.value.selected?.let(::loadQuote) }
    fun retryProfile() { state.value.selected?.let(::loadProfile) }
    override fun onCleared() { closeResources() }
}

private fun Exception.userMessage() = if (this is StockDataException) message
    ?: "Could not load stock data." else "Could not load stock data. Try again."
