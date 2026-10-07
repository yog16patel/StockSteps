package org.example.stocksteps.presentation.companydetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.*
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.settings.ThemeMode

// Preview-only sample values (fictional "Sample Corp"); production renders backend state.
private fun fact(value: Double? = null, amount: Long? = null, availability: FinancialAvailability = FinancialAvailability.AVAILABLE) =
    FinancialFact(value = value, amount = amount, availability = availability, source = FinancialSource.PROVIDER_DIRECT,
        basis = FinancialBasis("annual", "2025-12-31", 2025, "USD"))

private fun previewDetails(
    name: String = "Sample Corp",
    change: Double = 5.26,
    dividend: FinancialFact = fact(0.8),
    pe: FinancialFact = fact(31.2),
    history: HistoricalComparison? = HistoricalComparison(average = 27.4, validCount = 5, differencePercent = 13.9, reliable = true),
    logo: String? = null
) = CompanyDetails(
    symbol = "SMPL",
    profile = CompanyProfile("SMPL", name, description = "Sample Corp develops software, cloud services and productivity applications used by people and businesses. " +
        "This preview text is fictional and only demonstrates how a longer description is shortened.", sector = "Technology",
        industry = "Software - Infrastructure", country = "US", exchange = "NASDAQ", currency = "USD", logoUrl = logo),
    quote = StockQuote("SMPL", name, 425.18, change, change / 4.2, 426.18, 419.85, previousClose = 425.18 - change, volume = 18_400_000,
        timestamp = 1_791_403_200, marketCap = 3_160_000_000_000, open = 420.32, yearHigh = 467.56, yearLow = 309.45),
    marketStatus = MarketStatus.OPEN,
    fundamentals = CompanyFundamentals(
        "SMPL",
        financials = CompanyFinancials(
            growth = mapOf("revenue" to fact(amount = 245_000_000_000), "revenueGrowth" to fact(15.3), "netIncome" to fact(amount = 88_000_000_000), "netIncomeGrowth" to fact(12.4), "eps" to fact(11.8)),
            profitability = mapOf("netMargin" to fact(35.1), "grossMargin" to fact(69.4)),
            financialHealth = mapOf("cash" to fact(amount = 80_000_000_000), "debt" to fact(amount = 76_000_000_000), "debtEquity" to fact(0.3)),
            cashFlow = mapOf("freeCashFlow" to fact(amount = 74_000_000_000)),
            shareholderReturns = mapOf("dividendYield" to dividend)
        ),
        valuation = CompanyValuation(metrics = mapOf("pe" to pe, "priceSales" to fact(12.9)), historical = history?.let { mapOf("pe" to it) }.orEmpty())
    )
)

private val previewChart = PriceChart("SMPL", ChartRange.ONE_DAY,
    (0 until 78).map { i -> PricePoint("2026-10-07 ${9 + (30 + i * 5) / 60}:${((30 + i * 5) % 60).toString().padStart(2, '0')}:00", 420.0 + i * 0.07 + (i % 7) * 0.4) }, "5min")

private val previewWhy = WhyMoving("SMPL", "Sample explanation: Sample Corp rose after reporting stronger cloud revenue.",
    "Cloud is one of the company's largest businesses, so changes there can shift expectations for future earnings.", 1.25,
    listOf(WhyMovingSource("Sample Corp reports results", "https://example.com/1", "Sample Wire"), WhyMovingSource("Analysts react", "https://example.com/2", "Sample Daily")))

private val previewNews = listOf(
    NewsArticle(title = "Sample Corp announces new AI tools for businesses", url = "https://example.com/a", source = "Sample Wire", id = "a"),
    NewsArticle(title = "Sample Corp expands its cloud infrastructure", url = "https://example.com/b", source = "Sample Daily", id = "b")
).map(NewsPresentation::model)

private fun previewState(details: CompanyDetails = previewDetails()) = CompanyDetailsState(
    symbol = "SMPL",
    overview = Section.Content(CompanyOverviewPresenter.build(details)),
    range = ChartRange.ONE_DAY,
    chart = Section.Content(previewChart),
    whyMoving = Section.Content(previewWhy),
    news = Section.Content(previewNews)
)

@Composable
private fun DetailsPreview(state: CompanyDetailsState, mode: ThemeMode) = StockStepsTheme(mode) {
    CompanyDetailsScreen(state, watched = true, watchlistEnabled = true, hinge = null,
        backIcon = {}, onAction = {})
}

@Preview(name = "Company Details — Light, positive day", heightDp = 3200)
@Composable
private fun DetailsLightPreview() = DetailsPreview(previewState(), ThemeMode.LIGHT)

@Preview(name = "Company Details — Dark", heightDp = 3200)
@Composable
private fun DetailsDarkPreview() = DetailsPreview(previewState(), ThemeMode.DARK)

@Preview(name = "Company Details — Negative day, no dividend, negative earnings", heightDp = 3200)
@Composable
private fun DetailsNegativePreview() = DetailsPreview(
    previewState(previewDetails(change = -7.76, dividend = fact(availability = FinancialAvailability.NO_DIVIDEND),
        pe = fact(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR), history = null)).copy(whyMoving = Section.Content(null)),
    ThemeMode.LIGHT
)

@Preview(name = "Company Details — Long name, missing history, partial failure", heightDp = 2600)
@Composable
private fun DetailsPartialPreview() = DetailsPreview(
    previewState(previewDetails(name = "Longname Sample Advanced Materials & Renewable Infrastructure Holdings International Corporation", history = null))
        .copy(chart = Section.Unavailable, whyMoving = Section.Unavailable, news = Section.Unavailable),
    ThemeMode.LIGHT
)

@Preview(name = "Company Details — Loading", heightDp = 1000)
@Composable
private fun DetailsLoadingPreview() = DetailsPreview(CompanyDetailsState("SMPL"), ThemeMode.LIGHT)

@Preview(name = "Company Details — Large text", widthDp = 320, heightDp = 3600, fontScale = 1.5f)
@Composable
private fun DetailsLargeTextPreview() = DetailsPreview(previewState(), ThemeMode.LIGHT)
