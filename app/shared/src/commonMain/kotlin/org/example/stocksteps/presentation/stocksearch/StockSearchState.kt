package org.example.stocksteps.presentation.stocksearch

import org.example.stocksteps.*

import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

internal data class StockSearchState(
    val financials: org.example.stocksteps.companydetail.FinancialsUiState = org.example.stocksteps.companydetail.FinancialsUiState(),
    val fundamentals: org.example.stocksteps.model.CompanyFundamentals? = null,
    val fundamentalsLoading: Boolean = false,
    val fundamentalsError: String? = null,
    val detail: org.example.stocksteps.companydetail.CompanyDetailUiState = org.example.stocksteps.companydetail.CompanyDetailUiState(),
    val detailTab: org.example.stocksteps.companydetail.CompanyDetailTab = org.example.stocksteps.companydetail.CompanyDetailTab.OVERVIEW,
    val chart: org.example.stocksteps.companydetail.FinancialChartState = org.example.stocksteps.companydetail.FinancialChartState(),
    val financialPeriod: org.example.stocksteps.companydetail.FinancialPeriod = org.example.stocksteps.companydetail.FinancialPeriod.ANNUAL,
    val metricInfo: String? = null,
    val news: List<org.example.stocksteps.model.NewsArticle> = emptyList(),
    val newsLoading: Boolean = false,
    val newsError: String? = null,
    val query: String = "",
    val results: List<StockSearchResult> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val selected: StockSearchResult? = null,
    val quote: StockQuote? = null,
    val quoteLoading: Boolean = false,
    val quoteError: String? = null,
    val profile: org.example.stocksteps.model.CompanyProfile? = null,
    val profileLoading: Boolean = false,
    val profileError: String? = null
)
