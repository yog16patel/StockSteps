package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.domain.GetArticleInsight
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.model.ArticleInsight
import org.example.stocksteps.news.ArticleInsightModel
import org.example.stocksteps.news.ArticleInsightPresenter
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class NewsInsightState(
    val symbol: String,
    val articleId: String,
    /** Content(null) when the article left the company's recent feed. */
    val insight: Section<ArticleInsight?> = Section.Loading,
    val loadedAt: Long = 0
) {
    val model: ArticleInsightModel? get() = (insight as? Section.Content)?.value?.let { ArticleInsightPresenter.model(it, loadedAt) }
}

/** Requests one explanation when opened; the backend generates it once and caches it for everyone. */
@OptIn(ExperimentalTime::class)
internal class NewsInsightViewModel(
    private val symbol: String,
    private val articleId: String,
    private val getInsight: GetArticleInsight,
    private val closeResources: () -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val mutableState = MutableStateFlow(NewsInsightState(symbol, articleId))
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        mutableState.update { it.copy(insight = Section.Loading) }
        viewModelScope.launch {
            val result = try { Section.Content(getInsight(symbol, articleId)) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                Section.Unavailable
            }
            mutableState.update { it.copy(insight = result, loadedAt = now()) }
        }
    }

    override fun onCleared() = closeResources()
}

@Composable
internal fun NewsInsightScene(
    route: NewsInsightRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit
) {
    val model = viewModel(key = "news-insight:${route.symbol}:${route.articleId}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        NewsInsightViewModel(route.symbol, route.articleId, data.getArticleInsight(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    NewsInsightScreen(state, hinge, backIcon, onBack, onRetry = model::load, onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } })
}

/** "Explained simply": what happened, why it could matter, what to watch, terms, source and limits. */
@Composable
internal fun NewsInsightScreen(
    state: NewsInsightState,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val model = remember(state.insight, state.loadedAt) { state.model }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.lg)
    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = stringResource(Res.string.insight_title), backButton = AppBarBackButton.BACK),
            onBack = onBack,
            backIcon = backIcon
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally) {
                when (val insight = state.insight) {
                    Section.Loading -> item(key = "loading") {
                        StockLoadingState(content.padding(top = spacing.md)) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                StockSkeleton(Modifier.fillMaxWidth(0.9f))
                                repeat(3) { StockSkeleton(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.touchTarget * 2), shape = StockStepsTheme.shapes.card) }
                            }
                        }
                    }
                    Section.Unavailable -> item(key = "error") {
                        StockCard(content.padding(top = spacing.md), bordered = false) { StockErrorState(stringResource(Res.string.insight_failed), onRetry) }
                    }
                    is Section.Content -> if (insight.value == null || model == null) {
                        item(key = "missing") { StockCard(content.padding(top = spacing.md), bordered = false) { StockEmptyState(stringResource(Res.string.insight_missing)) } }
                    } else {
                        item(key = "headline") {
                            Column(content.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                model.sources.firstOrNull()?.meta?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                                model.headline?.let { Text(it, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary) }
                            }
                        }
                        if (model.available) {
                            item(key = "provenance") { ProvenanceNote(model.provenance, content.padding(top = spacing.sm)) }
                            item(key = "what") { InsightSection(stringResource(Res.string.insight_what_happened), listOf(model.summary!!), bullets = false, modifier = section) }
                            if (model.whyItMatters.isNotEmpty()) item(key = "why") { InsightSection(stringResource(Res.string.insight_why_matters), model.whyItMatters, modifier = section) }
                            if (model.watchNext.isNotEmpty()) item(key = "next") { InsightSection(stringResource(Res.string.insight_watch_next), model.watchNext, modifier = section) }
                        } else {
                            item(key = "unavailable") {
                                StockCard(section, bordered = false, contentPadding = PaddingValues(spacing.md)) {
                                    Text(stringResource(Res.string.insight_unavailable_title), Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                                    Text(model.unavailableMessage.orEmpty(), Modifier.padding(top = spacing.xs), style = typography.body, color = colors.textBody)
                                }
                            }
                        }
                        if (model.terms.isNotEmpty()) item(key = "terms") { LearnTermsCard(model.terms, stringResource(Res.string.insight_terms), section) }
                        item(key = "source") {
                            Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                StockSectionHeader(stringResource(Res.string.insight_source))
                                model.sources.forEach { source ->
                                    StockCard(bordered = false, contentPadding = PaddingValues(spacing.md)) {
                                        Text(source.title, style = typography.bodyMedium, color = colors.textPrimary)
                                        source.meta?.let { Text(it, Modifier.padding(top = spacing.xxs), style = typography.caption, color = colors.textSecondary) }
                                        source.url?.let { url ->
                                            StockButton(stringResource(Res.string.insight_read_original), onClick = { onOpenUrl(url) },
                                                modifier = Modifier.padding(top = spacing.sm), variant = StockButtonVariant.SECONDARY)
                                        }
                                    }
                                }
                            }
                        }
                        if (model.available && model.limitations.isNotEmpty()) {
                            item(key = "limits") { InsightSection(stringResource(Res.string.insight_limitations), model.limitations, modifier = section, subdued = true) }
                        }
                    }
                }
                item(key = "disclaimer") {
                    Text(stringResource(Res.string.details_disclaimer), content.padding(top = spacing.lg), style = typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

/** Says where the text came from (AI or template) and what it was based on. */
@Composable
internal fun ProvenanceNote(text: String, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Row(
        modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.primaryContainer).padding(spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Icon(StockIcons.Info, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        Text(text, style = StockStepsTheme.typography.small, color = colors.textBody)
    }
}

/** Titled card with a paragraph or bullet list. */
@Composable
internal fun InsightSection(title: String, lines: List<String>, modifier: Modifier = Modifier, bullets: Boolean = true, subdued: Boolean = false) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(title)
        StockCard(bordered = false, contentPadding = PaddingValues(spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                lines.forEach { line ->
                    Text(if (bullets) "• $line" else line,
                        style = if (subdued) StockStepsTheme.typography.small else StockStepsTheme.typography.body,
                        color = if (subdued) colors.textSecondary else colors.textBody)
                }
            }
        }
    }
}
