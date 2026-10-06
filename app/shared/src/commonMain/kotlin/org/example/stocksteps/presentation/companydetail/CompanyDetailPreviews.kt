package org.example.stocksteps.presentation.companydetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.model.*
import org.example.stocksteps.presentation.stocksearch.StockSearchState
import org.example.stocksteps.theme.StockStepsTheme

private fun previewState(price: Double? = 100.0, change: Double? = 2.0): StockSearchState {
    val stock = StockSearchResult("DEMO", "Sample company · preview only", "USD", "NASDAQ")
    val quote = StockQuote("DEMO", stock.name, price, change, change, null, null, marketCap = 1_000_000_000)
    val profile = CompanyProfile("DEMO", description = "Preview data only. This company makes everyday products.", sector = "Consumer products")
    return StockSearchState(selected = stock, quote = quote, profile = profile, detail = CompanyDetailPresenter.build(stock, quote, profile))
}

@Composable
private fun DetailPreview(state: StockSearchState = previewState(), dark: Boolean = false) {
    StockStepsTheme(darkTheme = dark) {
        CompanyDetailScreen(state, Modifier.fillMaxSize(), false, true, null, {}, {}, {}, {})
    }
}
@Preview(name = "Normal company", widthDp = 390, heightDp = 844)
@Composable private fun NormalPreview() = DetailPreview()
@Preview(name = "Missing metrics", widthDp = 390, heightDp = 844)
@Composable private fun MissingPreview() = DetailPreview(previewState(null, null))
@Preview(name = "Loading", widthDp = 390, heightDp = 844)
@Composable private fun LoadingPreview() = DetailPreview(previewState(null, null).copy(quoteLoading = true, profileLoading = true))
@Preview(name = "Partial error", widthDp = 390, heightDp = 844)
@Composable private fun ErrorPreview() = DetailPreview(previewState().copy(profileError = "Company information unavailable."))
@Preview(name = "Dark mode", widthDp = 390, heightDp = 844)
@Composable private fun DarkPreview() = DetailPreview(dark = true)
@Preview(name = "Large detail pane", widthDp = 760, heightDp = 900)
@Composable private fun LargePreview() = DetailPreview()
private fun financialPreview(margin: Double? = 26.9, loading: Boolean = false): StockSearchState {
    val values = CompanyFundamentals("DEMO", financials = CompanyFinancials(
        profitability = margin?.let { mapOf("netMargin" to FinancialFact(value = it, basis = FinancialBasis("TTM"))) }.orEmpty()
    ))
    return previewState().copy(detailTab = CompanyDetailTab.FINANCIALS,
        financials = FinancialsPresenter.build(values, loading, false))
}
@Preview(name = "Partial financials · preview only", widthDp = 390, heightDp = 844)
@Composable private fun ProfitablePreview() = DetailPreview(financialPreview())
@Preview(name = "Loss-making · preview only", widthDp = 390, heightDp = 844)
@Composable private fun LossPreview() = DetailPreview(financialPreview(-12.0))
@Preview(name = "Financial skeletons", widthDp = 390, heightDp = 844)
@Composable private fun FinancialLoadingPreview() = DetailPreview(financialPreview(loading = true))
@Preview(name = "Financial empty state", widthDp = 320, heightDp = 844)
@Composable private fun FinancialEmptyPreview() = DetailPreview(financialPreview(null))
@Preview(name = "Financial dark mode", widthDp = 390, heightDp = 844)
@Composable private fun FinancialDarkPreview() = DetailPreview(financialPreview(), dark = true)
@Preview(name = "Financial tablet", widthDp = 760, heightDp = 900)
@Composable private fun FinancialTabletPreview() = DetailPreview(financialPreview())
