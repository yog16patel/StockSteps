package org.example.stocksteps

import org.example.stocksteps.companydetail.CompanyOverview
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

    @Throws(Exception::class)
    suspend fun getOverview(symbol: String): CompanyOverview = CompanyOverviewPresenter.build(details(symbol))

    @Throws(Exception::class)
    suspend fun getChart(symbol: String, range: ChartRange): PriceChart = chart(symbol, range)

    @Throws(Exception::class)
    suspend fun getWhyMoving(symbol: String): WhyMoving = whyMoving(symbol)

    @Throws(Exception::class)
    suspend fun getNews(symbol: String): List<NewsUiModel> =
        news(symbol).map(NewsPresentation::model).distinctBy { it.id }.take(3)

    fun close() = dependencies.close()
}
