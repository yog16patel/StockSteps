package org.example.stocksteps

import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.markets.MarketEducation
import org.example.stocksteps.markets.MarketLesson
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.markets.MarketsUiModel
import org.example.stocksteps.markets.MoversTab
import org.example.stocksteps.model.MarketsOverview
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Markets dashboard for SwiftUI: the same backend contract and shared presenter as Android.
 * `baseUrl` is read per request so the Mock/Real setting applies immediately.
 */
class IosMarketsClient(baseUrl: () -> String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val overview = dependencies.getMarketsOverview()

    @Throws(Exception::class)
    suspend fun getOverview(): MarketsOverview = overview()

    @OptIn(ExperimentalTime::class)
    fun model(overview: MarketsOverview, tab: MoversTab, expanded: Boolean): MarketsUiModel =
        MarketsPresenter.build(overview, tab, expanded, Clock.System.now().toEpochMilliseconds())

    fun indexLesson(id: String): MarketLesson? = MarketEducation.index(id)

    fun sectorLesson(sector: String, symbol: String, methodology: String): MarketLesson = MarketEducation.sector(sector, symbol, methodology)

    fun close() = dependencies.close()
}
