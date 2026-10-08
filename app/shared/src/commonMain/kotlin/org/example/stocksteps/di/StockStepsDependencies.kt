package org.example.stocksteps.di

import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.core.parameter.parametersOf
import org.example.stocksteps.createBackendClient
import org.example.stocksteps.data.RemoteMarketSnapshotRepository
import org.example.stocksteps.data.RemoteSparklineRepository
import org.example.stocksteps.data.RemoteBackendInfoRepository
import org.example.stocksteps.data.RemoteCompanyDetailsRepository
import org.example.stocksteps.data.RemoteMarketRepository
import org.example.stocksteps.data.RemoteCompanyNewsRepository
import org.example.stocksteps.data.RemoteMarketsRepository
import org.example.stocksteps.presentation.discovery.DiscoveryViewModel
import org.example.stocksteps.data.RemoteStockRepository
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.presentation.stocksearch.StockSearchViewModel

internal data class SearchInitialState(val query: String, val selection: StockSearchResult?)

/** Isolated graph: each platform owner closes its own dependencies, without global Koin state. */
internal class StockStepsDependencies(baseUrl: () -> String, clientFactory: () -> HttpClient = ::createBackendClient) {
    constructor(baseUrl: String, clientFactory: () -> HttpClient = ::createBackendClient) : this({ baseUrl }, clientFactory)

    private val application = koinApplication {
        modules(module {
            single<HttpClient> { clientFactory() } onClose { it?.close() }
            single { StockStepsApi(get(), baseUrl) }
            single<StockRepository> { RemoteStockRepository(get()) }
            single<MarketSnapshotRepository> { RemoteMarketSnapshotRepository(get()) }
            factory { GetMarketSnapshot(get()) }
            single<SparklineRepository> { RemoteSparklineRepository(get()) }
            factory { GetSparkline(get()) }
            single<BackendInfoRepository> { RemoteBackendInfoRepository(get()) }
            factory { GetBackendInfo(get()) }
            single<CompanyDetailsRepository> { RemoteCompanyDetailsRepository(get()) }
            factory { GetCompanyDetails(get()) }
            factory { GetPriceChart(get()) }
            factory { GetWhyMoving(get()) }
            factory { GetValuationHistory(get()) }
            single<MarketRepository> { RemoteMarketRepository(get()) }
            factory { GetMarketGainers(get()) }
            factory { GetMarketLosers(get()) }
            factory { GetMarketNews(get()) }
            factory { GetCompanyNews(get()) }
            single<CompanyNewsRepository> { RemoteCompanyNewsRepository(get()) }
            factory { GetCompanyNewsFeed(get()) }
            factory { GetArticleInsight(get()) }
            factory { GetMovementExplanation(get()) }
            single<MarketsRepository> { RemoteMarketsRepository(get()) }
            factory { GetMarketsOverview(get()) }
            factory { DiscoveryViewModel(get(), get(), get(), ::close) }
            factory { SearchStocks(get()) }
            factory { GetStockQuote(get()) }
            factory { GetCompanyProfile(get()) }
            factory { GetCompanyFundamentals(get()) }
            factory { parameters ->
                val initial: SearchInitialState = parameters.get()
                StockSearchViewModel(get(), get(), get(), initial.query, initial.selection, ::close, get(), get())
            }
        })
    }

    fun discoveryViewModel(): DiscoveryViewModel = application.koin.get()
    fun marketGainers(): GetMarketGainers = application.koin.get()
    fun marketLosers(): GetMarketLosers = application.koin.get()
    fun companyNews(): GetCompanyNews = application.koin.get()
    fun marketNews(): GetMarketNews = application.koin.get()
    fun searchStocks(): SearchStocks = application.koin.get()
    fun getCompanyFundamentals(): GetCompanyFundamentals = application.koin.get()
    fun getCompanyProfile(): GetCompanyProfile = application.koin.get()
    fun getMarketSnapshot(): GetMarketSnapshot = application.koin.get()
    fun getSparkline(): GetSparkline = application.koin.get()
    fun getBackendInfo(): GetBackendInfo = application.koin.get()
    fun getCompanyDetails(): GetCompanyDetails = application.koin.get()
    fun getPriceChart(): GetPriceChart = application.koin.get()
    fun getWhyMoving(): GetWhyMoving = application.koin.get()
    fun getValuationHistory(): GetValuationHistory = application.koin.get()
    fun getStockQuote(): GetStockQuote = application.koin.get()
    fun getCompanyNewsFeed(): GetCompanyNewsFeed = application.koin.get()
    fun getArticleInsight(): GetArticleInsight = application.koin.get()
    fun getMovementExplanation(): GetMovementExplanation = application.koin.get()
    fun getMarketsOverview(): GetMarketsOverview = application.koin.get()
    fun searchViewModel(query: String, selection: StockSearchResult?): StockSearchViewModel =
        application.koin.get { parametersOf(SearchInitialState(query, selection)) }

    fun close() { application.close() }
}
