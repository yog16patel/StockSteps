package org.example.stocksteps.presentation.companydetail

import org.example.stocksteps.companydetail.*

internal sealed interface CompanyDetailAction {
    data class SelectTab(val tab: CompanyDetailTab) : CompanyDetailAction
    data class SelectChartPeriod(val period: ChartPeriod) : CompanyDetailAction
    data class SelectFinancialPeriod(val period: FinancialPeriod) : CompanyDetailAction
    data class OpenMetricInfo(val id: String) : CompanyDetailAction
    data object CloseMetricInfo : CompanyDetailAction
    data object RefreshFundamentals : CompanyDetailAction
    data object RefreshNews : CompanyDetailAction
}
