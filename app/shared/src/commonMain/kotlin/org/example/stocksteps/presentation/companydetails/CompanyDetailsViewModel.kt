package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.domain.GetCompanyDetails
import org.example.stocksteps.domain.GetCompanyNews
import org.example.stocksteps.domain.GetPriceChart
import org.example.stocksteps.domain.GetWhyMoving
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.WhyMoving
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel

/** Section state without boolean soup: each section loads, succeeds, is empty or fails on its own. */
internal sealed interface Section<out T> {
    data object Loading : Section<Nothing>
    data class Content<T>(val value: T) : Section<T>
    data object Unavailable : Section<Nothing>
}

internal data class CompanyDetailsState(
    val symbol: String,
    val overview: Section<CompanyOverview> = Section.Loading,
    val range: ChartRange = ChartRange.ONE_MONTH,
    val chart: Section<PriceChart> = Section.Loading,
    /** Content(null) means no source-backed explanation exists, so the section is hidden. */
    val whyMoving: Section<WhyMoving?> = Section.Loading,
    val news: Section<List<NewsUiModel>> = Section.Loading
)

/**
 * Initial load: one aggregated core request plus independent chart (1M), why-moving and news
 * requests. Charts are cached per range for the screen's lifetime. The repository's base URL
 * decides mock vs real; this ViewModel never checks the environment.
 */
internal class CompanyDetailsViewModel(
    private val symbol: String,
    private val getDetails: GetCompanyDetails,
    private val getChart: GetPriceChart,
    private val getWhyMoving: GetWhyMoving,
    private val getNews: GetCompanyNews,
    private val closeResources: () -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyDetailsState(symbol))
    val state = mutableState.asStateFlow()
    private val charts = mutableMapOf<ChartRange, PriceChart>()
    private var chartJob: Job? = null

    init {
        loadCore()
        loadChart(ChartRange.ONE_MONTH)
        loadWhyMoving()
        loadNews()
    }

    fun loadCore() = load({ copy(overview = it) }) { CompanyOverviewPresenter.build(getDetails(symbol)) }

    fun selectRange(range: ChartRange) {
        mutableState.update { it.copy(range = range) }
        loadChart(range)
    }

    fun retryChart() = loadChart(state.value.range, force = true)

    private fun loadChart(range: ChartRange, force: Boolean = false) {
        charts[range]?.takeUnless { force }?.let { cached ->
            mutableState.update { it.copy(chart = Section.Content(cached)) }
            return
        }
        chartJob?.cancel()
        chartJob = load({ copy(chart = it) }) { getChart(symbol, range).also { charts[range] = it } }
    }

    fun loadWhyMoving() = load({ copy(whyMoving = it) }) { getWhyMoving(symbol) }

    fun loadNews() = load({ copy(news = it) }) {
        getNews(symbol).map(NewsPresentation::model).distinctBy { it.id }.take(NEWS_ON_PAGE)
    }

    /** Runs one section load; any failure (including "not available") becomes a compact unavailable state. */
    private fun <T> load(apply: CompanyDetailsState.(Section<T>) -> CompanyDetailsState, block: suspend () -> T): Job {
        mutableState.update { it.apply(Section.Loading) }
        return viewModelScope.launch {
            val result = try {
                Section.Content(block())
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                Section.Unavailable
            }
            mutableState.update { it.apply(result) }
        }
    }

    override fun onCleared() = closeResources()

    private companion object { const val NEWS_ON_PAGE = 3 }
}
