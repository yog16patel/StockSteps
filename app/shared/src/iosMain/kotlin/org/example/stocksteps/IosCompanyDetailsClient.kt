package org.example.stocksteps

import org.example.stocksteps.companydetail.ChartIssue
import org.example.stocksteps.companydetail.ChartPresentation
import org.example.stocksteps.companydetail.CompanyDetailPresenter
import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.FinancialMetric
import org.example.stocksteps.companydetail.FinancialPeriod
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.ValuationHistory
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.WhyMoving
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.model.MovementPeriod
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.news.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    private val profile = dependencies.getCompanyProfile()
    private val valuationHistory = dependencies.getValuationHistory()
    private val newsFeed = dependencies.getCompanyNewsFeed()
    private val articleInsight = dependencies.getArticleInsight()
    private val movement = dependencies.getMovementExplanation()

    @Throws(Exception::class)
    suspend fun getOverview(symbol: String): CompanyOverview = CompanyOverviewPresenter.build(details(symbol))

    /**
     * The chart, or why it can't be drawn (Phase 4C.2): a failed request becomes a [ChartIssue] (connection / temporary / no history) and a
     * response with fewer than two prices is "no history", so Swift needs no `NSError` parsing. Cancellation still propagates.
     */
    @Throws(CancellationException::class)
    suspend fun loadChart(symbol: String, range: ChartRange): ChartLoad = try {
        chart(symbol, range).let { ChartLoad(it, ChartPresentation.issue(it)) }
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        ChartLoad(null, ChartPresentation.issue(cause))
    }

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

    /** Full historical P/E series for the Valuation screen; ranges are sliced locally. */
    @Throws(Exception::class)
    suspend fun getValuationHistory(symbol: String): ValuationHistory = valuationHistory(symbol)

    /** Company profile, for the Financials header (name and exchange). */
    @Throws(Exception::class)
    suspend fun getProfile(symbol: String): CompanyProfile = profile(symbol)

    /** Valuation metrics (P/E, P/S with history) for the Financials destination. */
    fun valuation(symbol: String, fundamentals: CompanyFundamentals): List<FinancialMetric> =
        CompanyDetailPresenter.build(StockSearchResult(symbol, symbol), null, null, fundamentals).valuation

    /** The company's whole news feed (one request); [newsFeedModel] slices it per filter. */
    @Throws(Exception::class)
    suspend fun getNewsFeed(symbol: String): List<NewsArticle> = newsFeed(symbol)

    @OptIn(ExperimentalTime::class)
    fun newsFeedModel(articles: List<NewsArticle>, filter: NewsFilter): CompanyNewsFeedModel =
        CompanyNewsPresenter.feed(articles, filter, Clock.System.now().toEpochMilliseconds())

    /** Null when the article is no longer in the company's recent feed. */
    @OptIn(ExperimentalTime::class)
    @Throws(Exception::class)
    suspend fun getArticleInsight(symbol: String, articleId: String): ArticleInsightModel? =
        articleInsight(symbol, articleId)?.let { ArticleInsightPresenter.model(it, Clock.System.now().toEpochMilliseconds()) }

    /** Null when there isn't enough price data for the period. */
    @OptIn(ExperimentalTime::class)
    @Throws(Exception::class)
    suspend fun getMovement(symbol: String, period: MovementPeriod): MovementModel? =
        movement(symbol, period)?.let { MovementPresenter.model(it, Clock.System.now().toEpochMilliseconds()) }

    @Throws(Exception::class)
    suspend fun getMovementPreview(symbol: String): MovementPreview? = movement(symbol, MovementPeriod.ONE_DAY)?.let(MovementPresenter::preview)

    fun close() = dependencies.close()

    private companion object { const val NEWS_ON_PAGE = 3 }
}

/** A chart load for SwiftUI: [chart] when the request succeeded, [issue] when it can't be drawn (both set for a too-short response). */
data class ChartLoad(val chart: PriceChart?, val issue: ChartIssue?)
