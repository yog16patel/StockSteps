package org.example.stocksteps

import org.example.stocksteps.companydetail.CompanyDetailPresenter
import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.FinancialMetric
import org.example.stocksteps.companydetail.FinancialPeriod
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.WhyMoving
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel

/**
 * Company Details for SwiftUI. Uses the same backend contracts and shared presenter as Android;
 * `baseUrl` is read per request so the Mock/Real setting applies immediately.
 */
class IosCompanyDetailsClient(baseUrl: () -> String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val details = dependencies.getCompanyDetails()
    private val chart = dependencies.getPriceChart()
    private val whyMoving = dependencies.getWhyMoving()
    private val news = dependencies.companyNews()
    private val fundamentals = dependencies.getCompanyFundamentals()

    @Throws(Exception::class)
    suspend fun getOverview(symbol: String): CompanyOverview = CompanyOverviewPresenter.build(details(symbol))

    @Throws(Exception::class)
    suspend fun getChart(symbol: String, range: ChartRange): PriceChart = chart(symbol, range)

    /** Null when no source-backed explanation exists (the section is hidden, not an error). */
    @Throws(Exception::class)
    suspend fun getWhyMoving(symbol: String): WhyMoving? = whyMoving(symbol)

    @Throws(Exception::class)
    suspend fun getNews(symbol: String): List<NewsUiModel> = getAllNews(symbol).take(NEWS_ON_PAGE)

    /** Every recent company story, for the Company News destination. */
    @Throws(Exception::class)
    suspend fun getAllNews(symbol: String): List<NewsUiModel> =
        news(symbol).map(NewsPresentation::model).distinctBy { it.id }

    /** Annual or quarterly fundamentals for the Financials destination. */
    @Throws(Exception::class)
    suspend fun getFundamentals(symbol: String, period: FinancialPeriod): CompanyFundamentals =
        fundamentals(symbol, if (period == FinancialPeriod.ANNUAL) "annual" else "quarter")

    /** Valuation metrics (P/E, P/S with history) for the Financials destination. */
    fun valuation(symbol: String, fundamentals: CompanyFundamentals): List<FinancialMetric> =
        CompanyDetailPresenter.build(StockSearchResult(symbol, symbol), null, null, fundamentals).valuation

    fun close() = dependencies.close()

    private companion object { const val NEWS_ON_PAGE = 3 }
}
