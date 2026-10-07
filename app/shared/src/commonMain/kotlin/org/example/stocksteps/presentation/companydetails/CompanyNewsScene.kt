package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.domain.GetCompanyNews
import org.example.stocksteps.news.NewsPresentation
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource

/** All recent news for one company (full cards with summaries). */
internal class CompanyNewsViewModel(private val symbol: String, private val getNews: GetCompanyNews, private val closeResources: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow<Section<List<NewsUiModel>>>(Section.Loading)
    val state = mutableState.asStateFlow()
    init { load() }
    fun load() {
        mutableState.value = Section.Loading
        viewModelScope.launch {
            mutableState.value = try {
                Section.Content(getNews(symbol).map(NewsPresentation::model).distinctBy { it.id })
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                Section.Unavailable
            }
        }
    }
    override fun onCleared() = closeResources()
}

@Composable
internal fun CompanyNewsScene(route: CompanyNewsRoute, backend: BackendRouter, environment: BackendEnvironment, hinge: WindowHinge?) {
    val model = viewModel(key = "company-news:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyNewsViewModel(route.symbol, data.companyNews(), data::close)
    }
    val news by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val spacing = StockStepsTheme.spacing
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally) {
                when (val section = news) {
                    Section.Loading -> items(3) { StockNewsCardSkeleton(content) }
                    Section.Unavailable -> item { StockCard(content) { StockErrorState(stringResource(Res.string.home_news_unavailable), model::load) } }
                    is Section.Content -> if (section.value.isEmpty()) {
                        item { StockCard(content) { StockEmptyState(stringResource(Res.string.details_news_empty)) } }
                    } else items(section.value, key = { it.id }) { article ->
                        StockNewsCard(article, onClick = article.url?.let { url -> { runCatching { uriHandler.openUri(url) } } }, modifier = content, compact = false)
                    }
                }
            }
        }
    }
}
