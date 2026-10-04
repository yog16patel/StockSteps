package org.example.stocksteps.di

import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.core.parameter.parametersOf
import org.example.stocksteps.createBackendClient
import org.example.stocksteps.data.RemoteMarketRepository
import org.example.stocksteps.presentation.discovery.DiscoveryViewModel
import org.example.stocksteps.data.RemoteStockRepository
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.presentation.stocksearch.StockSearchViewModel

internal data class SearchInitialState(val query: String, val selection: StockSearchResult?)

/** Isolated graph: each platform owner closes its own dependencies, without global Koin state. */
internal class StockStepsDependencies(baseUrl: String, clientFactory: () -> HttpClient = ::createBackendClient) {
    private val application = koinApplication {
        modules(module {
            single<HttpClient> { clientFactory() } onClose { it?.close() }
            single { StockStepsApi(get(), baseUrl) }
            single<StockRepository> { RemoteStockRepository(get()) }
            single<MarketRepository> { RemoteMarketRepository(get()) }
            factory { GetMarketGainers(get()) }
            factory { GetMarketLosers(get()) }
            factory { GetMarketNews(get()) }
            factory { DiscoveryViewModel(get(), get(), get(), ::close) }
            factory { SearchStocks(get()) }
            factory { GetStockQuote(get()) }
            factory { GetCompanyProfile(get()) }
            factory { parameters ->
                val initial: SearchInitialState = parameters.get()
                StockSearchViewModel(get(), get(), get(), initial.query, initial.selection, ::close)
            }
        })
    }

    fun discoveryViewModel(): DiscoveryViewModel = application.koin.get()
    fun marketGainers(): GetMarketGainers = application.koin.get()
    fun marketLosers(): GetMarketLosers = application.koin.get()
    fun marketNews(): GetMarketNews = application.koin.get()
    fun searchStocks(): SearchStocks = application.koin.get()
    fun getCompanyProfile(): GetCompanyProfile = application.koin.get()
    fun getStockQuote(): GetStockQuote = application.koin.get()
    fun searchViewModel(query: String, selection: StockSearchResult?): StockSearchViewModel =
        application.koin.get { parametersOf(SearchInitialState(query, selection)) }

    fun close() { application.close() }
}
