package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.model.ChartRange

internal sealed interface CompanyDetailsAction {
    data object Back : CompanyDetailsAction
    data object ToggleWatchlist : CompanyDetailsAction
    data object RetryCore : CompanyDetailsAction
    data object RetryChart : CompanyDetailsAction
    data object RetryWhyMoving : CompanyDetailsAction
    data object RetryNews : CompanyDetailsAction
    data class SelectRange(val range: ChartRange) : CompanyDetailsAction
    data object OpenFinancials : CompanyDetailsAction
    data object OpenNews : CompanyDetailsAction
    data class OpenArticle(val url: String) : CompanyDetailsAction
}
