package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.model.ChartRange

internal sealed interface CompanyDetailsAction {
    data object Back : CompanyDetailsAction
    data object AddPortfolio : CompanyDetailsAction
    data object Compare : CompanyDetailsAction
    data object ToggleWatchlist : CompanyDetailsAction
    data object RetryCore : CompanyDetailsAction
    data object RetryChart : CompanyDetailsAction
    data object RetryWhyMoving : CompanyDetailsAction
    data object RetryNews : CompanyDetailsAction
    data class SelectRange(val range: ChartRange) : CompanyDetailsAction
    data object OpenFinancials : CompanyDetailsAction
    data object OpenValuation : CompanyDetailsAction
    data object OpenNews : CompanyDetailsAction
    data object OpenMovement : CompanyDetailsAction
    data class OpenArticle(val url: String) : CompanyDetailsAction
}
